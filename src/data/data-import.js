/**
 * Physical module: src/data/data-import.js
 * Version: v16 Data Stability
 *
 * User Data Import with JSON parsing, schema version verification,
 * strict validation, preview generation, automatic pre-import backup,
 * and atomic replacement of local application data.
 *
 * v17.17: every file is treated as hostile — byte limit before reading,
 * structural scan (depth, size, dangerous and reserved keys), explicit
 * format/scope check, Mission field whitelist, domain limits, SHA-256
 * corruption check, required pre-import backup and transactional apply.
 */
(function initializeDataImportV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const schema = App?.data?.schema;
  const validator = App?.data?.validator;
  const runner = App?.data?.migrationRunner;

  if (!App?.core?.registerModule || !schema || !validator || !runner) {
    throw new Error('Schema, Validator, and Migration Runner are required before Data Import.');
  }

  const MODULE_NAME = 'data.import';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  let pendingImportState = null;
  let prepareSequence = 0;

  const LIMITS = validator.limits;
  const BACKUP_FORMAT = schema.backupFormat;
  const SUPPORTED_SCOPES = new Set(['full', 'missions']);
  const HISTORY_KEEP_LIMIT = 500; // the app keeps at most 500 History records (session-repository)

  /* v17.17 whitelist: only fields the Mission repository actually persists (plus legacy input
     aliases read by the migration runner). Unknown Mission/step fields are dropped, never copied
     into runtime state. Session records keep unknown fields within the structural limits for
     forward compatibility (see docs/security/security-baseline.md, "Unknown-field policy"). */
  const MISSION_FIELDS = Object.freeze([
    'id', 'missionSchemaVersion', 'title', 'subject', 'type', 'deadlineText', 'status',
    'createdAt', 'updatedAt', 'completedAt', 'archivedAt', 'currentStepId', 'archivedCurrentStepId',
    'estimate', 'sourceAnalysis', 'steps', 'progress', 'goal', 'sessions'
  ]);
  const STEP_FIELDS = Object.freeze([
    'id', 'missionId', 'stepSchemaVersion', 'title', 'task', 'durationMinutes', 'duration', 'status',
    'reason', 'requiredTools', 'recommendedApps', 'createdAt', 'updatedAt', 'completedAt', 'scheduledDate'
  ]);
  const GOAL_FIELDS = Object.freeze(['title', 'subject', 'type', 'deadlineText']);
  const ESTIMATE_FIELDS = Object.freeze(['totalMinutes', 'difficulty', 'explanation', 'scheduleWarning']);
  const SOURCE_FIELDS = Object.freeze(['detectedTasks', 'detectedPages', 'confidence', 'missingInformation', 'facts', 'estimates']);
  const PROGRESS_FIELDS = Object.freeze(['completedStepIds', 'history', 'initialStepCount']);

  function fail(code, extra = {}) {
    return { ok: false, code, errors: [code], ...extra };
  }

  function isPlainObject(value) {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
  }

  function pick(source, fields) {
    const out = {};
    for (const field of fields) {
      if (Object.prototype.hasOwnProperty.call(source, field) && source[field] !== undefined) {
        out[field] = source[field];
      }
    }
    return out;
  }

  function pickObject(value, fields) {
    return isPlainObject(value) ? pick(value, fields) : undefined;
  }

  function sanitizeStep(step) {
    return isPlainObject(step) ? pick(step, STEP_FIELDS) : step;
  }

  function sanitizeMission(mission) {
    if (!isPlainObject(mission)) return mission;
    const out = pick(mission, MISSION_FIELDS);
    if (Array.isArray(out.steps)) out.steps = out.steps.map(sanitizeStep);
    if (Array.isArray(out.sessions)) out.sessions = out.sessions.map(sanitizeStep);
    for (const [field, allowed] of [['goal', GOAL_FIELDS], ['estimate', ESTIMATE_FIELDS], ['sourceAnalysis', SOURCE_FIELDS], ['progress', PROGRESS_FIELDS]]) {
      if (field in out) {
        const cleaned = pickObject(out[field], allowed);
        if (cleaned === undefined) delete out[field]; else out[field] = cleaned;
      }
    }
    return out;
  }

  function safeLabel(value, maxLength = 60) {
    const text = typeof value === 'string' ? value.trim() : '';
    return /^[\p{L}\p{N} .,&+()_:/-]{1,200}$/u.test(text) ? text.slice(0, maxLength) : null;
  }

  function safeIsoTimestamp(value) {
    if (typeof value !== 'string' || value.length > 40) return null;
    return Number.isFinite(Date.parse(value)) ? value : null;
  }

  /* Display-only filename: control characters removed, length capped. Never used as a path. */
  function safeFilename(name) {
    const cleaned = String(name || '').replace(/[\u0000-\u001f\u007f<>"'`\\/]/g, '').trim();
    return (cleaned || 'backup.json').slice(0, 80);
  }

  function readCurrentState() {
    const collected = runner.collectCurrentDataBundle('import_preview');
    const sessionsRepo = App.repositories?.sessions;
    const history = sessionsRepo?.listHistory
      ? { schemaVersion: schema.versions.sessionHistory, sessions: sessionsRepo.listHistory() }
      : collected.data.sessionHistory;
    const lastSession = sessionsRepo?.loadLast ? sessionsRepo.loadLast() : collected.data.lastSession;
    const missions = Array.isArray(collected.data.missions?.missions) ? collected.data.missions.missions : [];
    return {
      history,
      lastSession: lastSession || null,
      missionIds: new Set(missions.map(m => m && m.id).filter(Boolean)),
      summary: {
        missionCount: missions.length,
        sessionCount: Array.isArray(history?.sessions) ? history.sessions.length : 0,
        hasActiveSession: Boolean(collected.data.activeSession)
      }
    };
  }

  function computeDateRange(sessions) {
    let min = Infinity;
    let max = -Infinity;
    for (const s of sessions) {
      const t = Number(s?.finishedAt || s?.startedAt);
      if (Number.isFinite(t) && t > 0) {
        if (t < min) min = t;
        if (t > max) max = t;
      }
    }
    return Number.isFinite(min) ? { from: min, to: max } : null;
  }

  function normalizeIncomingBundleStructure(parsed, options = {}) {
    if (!isPlainObject(parsed)) {
      return fail('invalid_bundle_format', { errors: ['bundle_not_object'] });
    }

    // v17.17: explicit format check. Unknown formats are rejected instead of reinterpreted;
    // files without a format marker are accepted only as older Focus Mode+ exports below.
    if (parsed.format !== undefined && parsed.format !== BACKUP_FORMAT) {
      return fail('unsupported_format');
    }
    const scope = parsed.scope === undefined ? 'full' : parsed.scope;
    if (!SUPPORTED_SCOPES.has(scope)) {
      return fail('unsupported_format');
    }

    const detectedVersions = schema.detectDataVersions(parsed);
    const compatibility = schema.checkSchemaCompatibility(detectedVersions);
    if (!compatibility.supported) {
      return fail('unsupported_future_schema', { compatibility });
    }

    const dataRoot = isPlainObject(parsed.data) ? parsed.data : parsed;

    const rawMissions = dataRoot.missionsStore ?? dataRoot.missions;
    const rawHistory = dataRoot.sessionHistoryStore ?? dataRoot.sessionHistory;
    const rawPreferences = dataRoot.preferences;

    if (rawMissions == null || rawHistory == null || rawPreferences == null) {
      const missing = [];
      if (rawMissions == null) missing.push('missions');
      if (rawHistory == null) missing.push('sessionHistory');
      if (rawPreferences == null) missing.push('preferences');
      return {
        ok: false,
        code: 'missing_required_sections',
        errors: missing.map(s => 'missing_section:' + s),
        missingSections: missing
      };
    }
    if (!Array.isArray(rawMissions) && !isPlainObject(rawMissions)) {
      return fail('invalid_bundle_format');
    }

    const sourceMissions = Array.isArray(rawMissions) ? rawMissions : rawMissions.missions;
    const missionsStore = Array.isArray(rawMissions)
      ? {
          schemaVersion: 1,
          missions: rawMissions.map(sanitizeMission),
          selectedMissionId: dataRoot.selectedMissionId ?? null,
          primaryMissionId: dataRoot.primaryMissionId ?? null,
          updatedAt: Date.now()
        }
      : {
          schemaVersion: rawMissions.schemaVersion,
          stepSchemaVersion: rawMissions.stepSchemaVersion,
          missions: Array.isArray(sourceMissions) ? sourceMissions.map(sanitizeMission) : sourceMissions,
          updatedAt: rawMissions.updatedAt,
          selectedMissionId: dataRoot.selectedMissionId !== undefined
            ? dataRoot.selectedMissionId
            : (rawMissions.selectedMissionId ?? null),
          primaryMissionId: dataRoot.primaryMissionId !== undefined
            ? dataRoot.primaryMissionId
            : (rawMissions.primaryMissionId ?? null)
        };

    let sessionHistoryStore = Array.isArray(rawHistory)
      ? { schemaVersion: 1, sessions: rawHistory }
      : rawHistory;
    let lastSession = dataRoot.lastSession ?? null;

    // A "Nur Missionen" backup carries no History: the current local History is kept.
    const current = options.current || null;
    if (scope === 'missions' && current) {
      sessionHistoryStore = current.history;
      lastSession = current.lastSession;
    }

    const structuredBundle = {
      format: BACKUP_FORMAT,
      scope,
      exportVersion: Number(parsed.exportVersion) || 1,
      applicationVersion: safeLabel(parsed.applicationVersion) || 'v15 Modular Baseline',
      exportedAt: safeIsoTimestamp(parsed.exportedAt),
      schemaVersions: isPlainObject(parsed.schemaVersions) ? parsed.schemaVersions : detectedVersions,
      data: {
        missions: missionsStore,
        selectedMissionId: missionsStore.selectedMissionId ?? null,
        primaryMissionId: missionsStore.primaryMissionId ?? null,
        sessionHistory: sessionHistoryStore,
        activeSession: dataRoot.activeSession ?? null,
        lastSession,
        recovery: dataRoot.recovery ?? null,
        preferences: rawPreferences,
        lastAiPlan: dataRoot.lastAiPlan ?? null,
        legacyCompatibility: dataRoot.legacyCompatibility ?? null
      }
    };

    const limitReport = validator.validateImportLimits(structuredBundle);
    if (!limitReport.valid) {
      return fail(limitReport.blockingErrors[0]?.code || 'import_limits_exceeded', {
        errors: limitReport.blockingErrors.map(e => e.code),
        validation: limitReport
      });
    }

    const validation = validator.validateBundle(structuredBundle, { requireSections: true });
    if (!validation.valid) {
      return {
        ok: false,
        code: validation.blockingErrors[0]?.code || 'validation_failed',
        errors: validation.blockingErrors.map(e => e.code),
        validation
      };
    }

    const missionsArr = Array.isArray(missionsStore.missions) ? missionsStore.missions : [];
    const sessionsArr = Array.isArray(sessionHistoryStore?.sessions) ? sessionHistoryStore.sessions : [];
    const overlapping = current ? missionsArr.filter(m => current.missionIds.has(m?.id)).length : 0;

    const preview = {
      format: parsed.format === BACKUP_FORMAT ? 'focusmode-backup' : 'legacy',
      exportedAt: structuredBundle.exportedAt,
      bundleVersion: structuredBundle.exportVersion,
      applicationVersion: structuredBundle.applicationVersion,
      appVersion: safeLabel(parsed.appVersion, 20),
      schemaVersion: structuredBundle.schemaVersions?.bundle || 1,
      scope,
      missionCount: missionsArr.length,
      activeMissionCount: missionsArr.filter(m => m?.status === 'active').length,
      sessionCount: scope === 'missions' ? 0 : sessionsArr.length,
      sessionsKept: scope === 'missions' ? 0 : Math.min(sessionsArr.length, HISTORY_KEEP_LIMIT),
      dateRange: scope === 'missions' ? null : computeDateRange(sessionsArr),
      hasActiveSession: Boolean(structuredBundle.data.activeSession),
      language: ['de', 'en'].includes(rawPreferences?.language) ? rawPreferences.language : 'de',
      settingsIncluded: isPlainObject(rawPreferences),
      current: current ? current.summary : null,
      overlappingMissionCount: overlapping,
      policy: scope === 'missions' ? 'replace_missions_keep_history' : 'replace_current_data',
      trust: 'local_backup_unverified'
    };

    return {
      ok: true,
      preview,
      bundle: structuredBundle,
      validation
    };
  }

  /* Synchronous part of the pipeline: size, parse, structure scan, envelope, limits, schema. */
  function analyzeImportText(jsonText, filename) {
    if (typeof jsonText !== 'string' || !jsonText.trim()) {
      return fail('empty_file');
    }
    if (jsonText.length > LIMITS.maxFileBytes) {
      return fail('file_too_large');
    }

    let parsed = null;
    try {
      parsed = JSON.parse(jsonText);
    } catch (error) {
      return fail('invalid_json');
    }
    if (!isPlainObject(parsed)) {
      return fail('invalid_bundle_format');
    }

    const structure = validator.inspectUntrustedStructure(parsed, LIMITS);
    if (!structure.valid) {
      return fail(structure.blockingErrors[0]?.code || 'import_structure_invalid', {
        errors: structure.blockingErrors.map(e => e.code)
      });
    }

    const check = normalizeIncomingBundleStructure(parsed, { current: readCurrentState() });
    if (!check.ok) return check;
    check.preview.filename = safeFilename(filename);
    check.parsed = parsed;
    return check;
  }

  function finalizePrepared(check, integrityStatus, sequence) {
    if (sequence !== prepareSequence) {
      return fail('import_superseded');
    }
    check.preview.integrity = integrityStatus;
    pendingImportState = {
      preparedAt: Date.now(),
      filename: check.preview.filename,
      preview: check.preview,
      bundle: check.bundle,
      validation: check.validation
    };
    return {
      ok: true,
      preview: check.preview,
      bundle: check.bundle,
      validation: check.validation
    };
  }

  /* Programmatic synchronous variant (no Web Crypto): a present digest is reported as not checked. */
  function prepareImportFromText(jsonText, filename = 'backup.json') {
    const sequence = ++prepareSequence;
    pendingImportState = null;
    const check = analyzeImportText(jsonText, filename);
    if (!check.ok) return check;
    const hasDigest = typeof check.parsed?.integrity?.digest === 'string';
    return finalizePrepared(check, hasDigest ? 'not_checked' : 'absent', sequence);
  }

  async function prepareImportFromTextAsync(jsonText, filename = 'backup.json', sequence = ++prepareSequence) {
    pendingImportState = null;
    const check = analyzeImportText(jsonText, filename);
    if (!check.ok) return check;
    let integrity;
    try {
      integrity = await validator.integrity.verifyBundleIntegrity(check.parsed);
    } catch (error) {
      integrity = { status: 'not_checked' };
    }
    if (integrity.status === 'mismatch') return fail('integrity_mismatch');
    if (integrity.status === 'invalid') return fail('integrity_invalid');
    return finalizePrepared(check, integrity.status, sequence);
  }

  async function prepareImportFromFile(file) {
    const sequence = ++prepareSequence;
    pendingImportState = null;
    if (!file || typeof file.text !== 'function') {
      return fail('file_missing');
    }
    // Enforce the byte limit before reading the file into memory.
    const size = Number(file.size);
    if (Number.isFinite(size) && size <= 0) return fail('empty_file');
    if (Number.isFinite(size) && size > LIMITS.maxFileBytes) return fail('file_too_large');
    let text;
    try {
      text = await file.text();
    } catch (error) {
      return fail('file_read_failed');
    }
    return prepareImportFromTextAsync(text, file.name || 'backup.json', sequence);
  }

  function confirmPreparedImport(explicitBundle = null) {
    const targetBundle = explicitBundle || pendingImportState?.bundle;
    if (!targetBundle) {
      return fail('no_prepared_import');
    }

    // Re-validate before creating a backup or applying anything.
    const structure = validator.inspectUntrustedStructure(targetBundle, LIMITS);
    if (!structure.valid) {
      return fail(structure.blockingErrors[0]?.code || 'import_structure_invalid');
    }
    const preCheck = normalizeIncomingBundleStructure(targetBundle, { current: readCurrentState() });
    if (!preCheck.ok) {
      return preCheck;
    }

    // A restorable copy of the current data must exist before anything is replaced.
    const backup = runner.createBackupChecked('pre_import', schema.keys.backupImport, { updateLatest: true });
    if (!backup.ok) {
      return fail('backup_failed', { detail: backup.code, backupCreated: false });
    }

    // Transactional apply: checked writes, read-back verification, rollback on any failure.
    const applyResult = runner.applyBundleAtomically(preCheck.bundle, {
      strictValidation: true,
      requireSections: true,
      provenance: 'local_backup'
    });

    if (!applyResult.ok) {
      return {
        ok: false,
        code: applyResult.status || 'import_apply_failed',
        errors: applyResult.errors || ['import_apply_failed'],
        validation: applyResult.validation || null,
        rolledBack: applyResult.rolledBack !== false,
        backupCreated: true
      };
    }

    const summary = {
      ok: true,
      policy: preCheck.preview.policy,
      scope: preCheck.preview.scope,
      missionCount: applyResult.missionCount,
      sessionCount: applyResult.sessionCount,
      exportedAt: preCheck.preview.exportedAt,
      bundleVersion: preCheck.preview.bundleVersion,
      backupKey: schema.keys.backupImport,
      backupTimestamp: backup.bundle.timestamp,
      validation: applyResult.validation
    };

    pendingImportState = null;
    return summary;
  }

  function cancelPreparedImport() {
    prepareSequence += 1;
    pendingImportState = null;
    return { ok: true };
  }

  function importFromJSONTextDirect(jsonText, filename = 'backup.json') {
    const prepared = prepareImportFromText(jsonText, filename);
    if (!prepared.ok) {
      return prepared;
    }
    return confirmPreparedImport(prepared.bundle);
  }

  const importApi = Object.freeze({
    policy: 'replace_current_data',
    prepareImportFromText,
    prepareImportFromTextAsync,
    prepareImportFromFile,
    confirmPreparedImport,
    cancelPreparedImport,
    importFromJSONTextDirect,
    get pendingImport() {
      return pendingImportState;
    }
  });

  App.data = App.data || {};
  App.data.importer = App.core.registerModule(MODULE_NAME, importApi, {
    virtualPath: 'src/data/data-import.js',
    version: '16.0',
    status: 'canonical-importer'
  });
})(window);
