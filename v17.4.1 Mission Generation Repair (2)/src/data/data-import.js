/**
 * Physical module: src/data/data-import.js
 * Version: v16 Data Stability
 *
 * User Data Import with JSON parsing, schema version verification,
 * strict validation, preview generation, automatic pre-import backup,
 * and atomic replacement of local application data.
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

  function normalizeIncomingBundleStructure(parsed) {
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { ok: false, code: 'invalid_bundle_format', errors: ['bundle_not_object'] };
    }

    const detectedVersions = schema.detectDataVersions(parsed);
    const compatibility = schema.checkSchemaCompatibility(detectedVersions);
    if (!compatibility.supported) {
      return {
        ok: false,
        code: 'unsupported_future_schema',
        errors: ['unsupported_future_schema'],
        compatibility
      };
    }

    const dataRoot = (parsed.data && typeof parsed.data === 'object' && !Array.isArray(parsed.data))
      ? parsed.data
      : parsed;

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

    const missionsStore = Array.isArray(rawMissions)
      ? {
          schemaVersion: 1,
          missions: rawMissions,
          selectedMissionId: dataRoot.selectedMissionId ?? null,
          primaryMissionId: dataRoot.primaryMissionId ?? null,
          updatedAt: Date.now()
        }
      : {
          ...rawMissions,
          selectedMissionId: dataRoot.selectedMissionId !== undefined
            ? dataRoot.selectedMissionId
            : (rawMissions.selectedMissionId ?? null),
          primaryMissionId: dataRoot.primaryMissionId !== undefined
            ? dataRoot.primaryMissionId
            : (rawMissions.primaryMissionId ?? null)
        };

    const sessionHistoryStore = Array.isArray(rawHistory)
      ? { schemaVersion: 1, sessions: rawHistory }
      : rawHistory;

    const structuredBundle = {
      exportVersion: Number(parsed.exportVersion) || 1,
      applicationVersion: String(parsed.applicationVersion || 'v15 Modular Baseline'),
      exportedAt: parsed.exportedAt || null,
      schemaVersions: parsed.schemaVersions || detectedVersions,
      data: {
        missions: missionsStore,
        selectedMissionId: missionsStore.selectedMissionId ?? null,
        primaryMissionId: missionsStore.primaryMissionId ?? null,
        sessionHistory: sessionHistoryStore,
        activeSession: dataRoot.activeSession ?? null,
        lastSession: dataRoot.lastSession ?? null,
        recovery: dataRoot.recovery ?? null,
        preferences: rawPreferences,
        lastAiPlan: dataRoot.lastAiPlan ?? null,
        legacyCompatibility: dataRoot.legacyCompatibility ?? null
      }
    };

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
    const sessionsArr = Array.isArray(sessionHistoryStore.sessions) ? sessionHistoryStore.sessions : [];

    const preview = {
      exportedAt: structuredBundle.exportedAt,
      bundleVersion: structuredBundle.exportVersion,
      applicationVersion: structuredBundle.applicationVersion,
      schemaVersion: structuredBundle.schemaVersions?.bundle || 1,
      missionCount: missionsArr.length,
      activeMissionCount: missionsArr.filter(m => m?.status === 'active').length,
      sessionCount: sessionsArr.length,
      hasActiveSession: Boolean(structuredBundle.data.activeSession),
      language: structuredBundle.data.preferences?.language || 'de',
      policy: 'replace_current_data'
    };

    return {
      ok: true,
      preview,
      bundle: structuredBundle,
      validation
    };
  }

  function prepareImportFromText(jsonText, filename = 'backup.json') {
    if (typeof jsonText !== 'string' || !jsonText.trim()) {
      return {
        ok: false,
        code: 'invalid_json',
        errors: ['empty_json_input']
      };
    }

    let parsed = null;
    try {
      parsed = JSON.parse(jsonText);
    } catch (error) {
      return {
        ok: false,
        code: 'invalid_json',
        errors: ['json_parse_error'],
        message: error?.message || 'Invalid JSON syntax'
      };
    }

    const check = normalizeIncomingBundleStructure(parsed);
    if (!check.ok) {
      pendingImportState = null;
      return check;
    }

    check.preview.filename = filename;
    pendingImportState = {
      preparedAt: Date.now(),
      filename,
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

  async function prepareImportFromFile(file) {
    if (!file || typeof file.text !== 'function') {
      return {
        ok: false,
        code: 'file_missing',
        errors: ['file_missing']
      };
    }
    const text = await file.text();
    return prepareImportFromText(text, file.name || 'backup.json');
  }

  function confirmPreparedImport(explicitBundle = null) {
    const targetBundle = explicitBundle || pendingImportState?.bundle;
    if (!targetBundle) {
      return {
        ok: false,
        code: 'no_prepared_import',
        errors: ['no_prepared_import']
      };
    }

    // Re-validate before creating backup or applying
    const preCheck = normalizeIncomingBundleStructure(targetBundle);
    if (!preCheck.ok) {
      return preCheck;
    }

    // Create automatic backup of current data before import
    const backup = runner.createBackup('pre_import', schema.keys.backupImport, {
      updateLatest: true
    });

    // Apply atomically with strict validation and migration runner normalization
    const applyResult = runner.applyBundleAtomically(preCheck.bundle, {
      strictValidation: true,
      requireSections: true
    });

    if (!applyResult.ok) {
      return {
        ok: false,
        code: applyResult.status || 'import_apply_failed',
        errors: applyResult.errors || ['import_apply_failed'],
        validation: applyResult.validation || null,
        backupCreated: true
      };
    }

    const summary = {
      ok: true,
      policy: 'replace_current_data',
      missionCount: applyResult.missionCount,
      sessionCount: applyResult.sessionCount,
      exportedAt: preCheck.preview.exportedAt,
      bundleVersion: preCheck.preview.bundleVersion,
      backupKey: schema.keys.backupImport,
      backupTimestamp: backup.timestamp,
      validation: applyResult.validation
    };

    pendingImportState = null;
    return summary;
  }

  function cancelPreparedImport() {
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
