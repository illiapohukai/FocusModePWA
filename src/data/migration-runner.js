/**
 * Physical module: src/data/migration-runner.js
 * Version: v16 Data Stability
 *
 * Single idempotent Migration Runner with pre-migration backup,
 * sequential execution, oldId -> newId atomic link mapping, post-migration
 * validation, and automatic backup rollback on failure.
 */
(function initializeMigrationRunnerV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const storage = App?.services?.storage;
  const schema = App?.data?.schema;
  const validator = App?.data?.validator;

  if (!App?.core?.registerModule || !storage || !schema || !validator) {
    throw new Error('Storage, Schema Versions, and Data Validator are required before Migration Runner.');
  }

  const MODULE_NAME = 'data.migrationRunner';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  const KEYS = schema.keys;
  const MISSION_STATUSES = ['active', 'completed', 'archived'];
  const STEP_STATUSES = ['pending', 'active', 'completed'];
  const ACTIVE_STATES = new Set(['created', 'running', 'paused', 'awaiting_resume', 'awaiting_review']);
  const TERMINAL_STATES = new Set(['completed', 'cancelled', 'interrupted']);

  function clone(value) {
    if (value === undefined || value === null) return value;
    if (typeof global.structuredClone === 'function') {
      return global.structuredClone(value);
    }
    return JSON.parse(JSON.stringify(value));
  }

  function computeChecksum(payload) {
    const str = typeof payload === 'string' ? payload : JSON.stringify(payload);
    let hash = 2166136261;
    for (let i = 0; i < str.length; i += 1) {
      hash ^= str.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return 'fnv1a_' + (hash >>> 0).toString(16).padStart(8, '0');
  }

  function safeParseJSON(raw, fallback = null) {
    if (raw == null || raw === '') return clone(fallback);
    if (typeof raw === 'object') return clone(raw);
    try {
      return JSON.parse(String(raw));
    } catch {
      return clone(fallback);
    }
  }

  function captureRawLocalStorageSnapshot() {
    const trackedKeys = [
      KEYS.missions,
      KEYS.missionsMigrated,
      KEYS.legacyActiveMission,
      KEYS.legacyMissionProgress,
      KEYS.activeSession,
      KEYS.lastSession,
      KEYS.sessionHistory,
      KEYS.legacySessionHistory,
      KEYS.language,
      KEYS.pwaHelp,
      KEYS.lastAiPlan,
      KEYS.schemaState,
      KEYS.migrationState
    ];
    const rawEntries = {};
    for (const key of trackedKeys) {
      const val = storage.getText(key, null);
      if (val !== null) {
        rawEntries[key] = val;
      }
    }
    return rawEntries;
  }

  function restoreRawLocalStorageSnapshot(rawEntries) {
    if (!rawEntries || typeof rawEntries !== 'object') return false;
    const trackedKeys = [
      KEYS.missions,
      KEYS.missionsMigrated,
      KEYS.legacyActiveMission,
      KEYS.legacyMissionProgress,
      KEYS.activeSession,
      KEYS.lastSession,
      KEYS.sessionHistory,
      KEYS.legacySessionHistory,
      KEYS.language,
      KEYS.pwaHelp,
      KEYS.lastAiPlan,
      KEYS.schemaState,
      KEYS.migrationState
    ];
    try {
      for (const key of trackedKeys) {
        if (Object.prototype.hasOwnProperty.call(rawEntries, key) && rawEntries[key] !== null) {
          storage.setText(key, rawEntries[key]);
        } else {
          storage.remove(key);
        }
      }
      return true;
    } catch (error) {
      console.error('[MigrationRunner]', { event: 'raw_restore_failed', message: error?.message });
      return false;
    }
  }

  function collectCurrentDataBundle(reason = 'auto_backup') {
    const rawSnapshot = captureRawLocalStorageSnapshot();
    const missionsStore = safeParseJSON(rawSnapshot[KEYS.missions], null) || {
      schemaVersion: 1,
      missions: [],
      selectedMissionId: null,
      primaryMissionId: null,
      updatedAt: Date.now()
    };
    const legacyHistory = safeParseJSON(rawSnapshot[KEYS.legacySessionHistory], null);
    const sessionHistoryStore = safeParseJSON(rawSnapshot[KEYS.sessionHistory], null) || (
      Array.isArray(legacyHistory)
        ? { schemaVersion: 1, sessions: legacyHistory }
        : (legacyHistory && Array.isArray(legacyHistory.sessions) ? legacyHistory : { schemaVersion: 1, sessions: [] })
    );
    const activeSession = safeParseJSON(rawSnapshot[KEYS.activeSession], null);
    const lastSession = safeParseJSON(rawSnapshot[KEYS.lastSession], null);
    const lastAiPlan = safeParseJSON(rawSnapshot[KEYS.lastAiPlan], null);
    const legacyActiveMission = safeParseJSON(rawSnapshot[KEYS.legacyActiveMission], null);
    const legacyMissionProgress = safeParseJSON(rawSnapshot[KEYS.legacyMissionProgress], null);
    const migrationMetadata = safeParseJSON(rawSnapshot[KEYS.migrationState], null);
    const storedSchemaState = safeParseJSON(rawSnapshot[KEYS.schemaState], null);
    const rawLang = rawSnapshot[KEYS.language] || 'de';
    const language = ['de', 'en'].includes(rawLang) ? rawLang : 'de';
    const pwaHelp = rawSnapshot[KEYS.pwaHelp] ?? null;

    const missionsList = Array.isArray(missionsStore?.missions) ? missionsStore.missions : [];
    const sessionsList = Array.isArray(sessionHistoryStore?.sessions) ? sessionHistoryStore.sessions : [];

    const dataPayload = {
      missions: clone(missionsStore),
      selectedMissionId: missionsStore?.selectedMissionId ?? null,
      primaryMissionId: missionsStore?.primaryMissionId ?? null,
      sessionHistory: clone(sessionHistoryStore),
      lastSession: clone(lastSession),
      activeSession: clone(activeSession),
      recovery: {
        schemaVersion: 1,
        hasRecoverableSession: Boolean(activeSession && activeSession.status === 'running'),
        activeSessionId: activeSession?.id ?? null,
        lifecycleState: activeSession?.lifecycleState ?? null
      },
      preferences: {
        schemaVersion: 1,
        language,
        pwaInstallHelpDismissed: pwaHelp
      },
      lastAiPlan: clone(lastAiPlan),
      legacyCompatibility: {
        missionsMigrated: rawSnapshot[KEYS.missionsMigrated] ?? null,
        activeMission: clone(legacyActiveMission),
        missionProgress: clone(legacyMissionProgress)
      },
      migrationMetadata: clone(migrationMetadata)
    };

    const exportedAt = new Date().toISOString();
    const checksum = computeChecksum({
      missions: dataPayload.missions,
      sessionHistory: dataPayload.sessionHistory,
      activeSession: dataPayload.activeSession,
      preferences: dataPayload.preferences
    });

    return {
      exportVersion: schema.exportVersion,
      applicationVersion: schema.applicationVersion,
      exportedAt,
      timestamp: Date.now(),
      reason,
      schemaVersions: storedSchemaState?.versions
        ? { ...schema.versions, ...storedSchemaState.versions }
        : { ...schema.versions },
      integrity: {
        checksum,
        missionCount: missionsList.length,
        activeMissionCount: missionsList.filter(m => m?.status === 'active').length,
        sessionCount: sessionsList.length,
        hasActiveSession: Boolean(activeSession)
      },
      data: dataPayload,
      rawSnapshot
    };
  }

  function createBackup(reason = 'auto_migration', targetKey = KEYS.backupLatest, options = {}) {
    const bundle = collectCurrentDataBundle(reason);
    const hasMeaningfulData = (
      bundle.integrity.missionCount > 0 ||
      bundle.integrity.sessionCount > 0 ||
      bundle.integrity.hasActiveSession ||
      Boolean(bundle.data.legacyCompatibility?.activeMission)
    );

    if (targetKey) {
      storage.setJSON(targetKey, bundle);
    }

    if (options.updateLatest !== false) {
      const existingLatest = storage.getJSON(KEYS.backupLatest, null);
      const existingHasData = Boolean(
        existingLatest?.integrity?.missionCount > 0 ||
        existingLatest?.integrity?.sessionCount > 0 ||
        existingLatest?.integrity?.hasActiveSession
      );
      if (hasMeaningfulData || !existingLatest || !existingHasData || options.forceLatest === true) {
        storage.setJSON(KEYS.backupLatest, bundle);
      }
    }

    return bundle;
  }

  function getLastBackup(preferredKey = null) {
    const candidateKeys = preferredKey
      ? [preferredKey, KEYS.backupLatest, KEYS.backupReset, KEYS.backupImport]
      : [KEYS.backupLatest, KEYS.backupReset, KEYS.backupImport];

    const candidates = [];
    for (const key of candidateKeys) {
      const parsed = storage.getJSON(key, null);
      if (parsed && typeof parsed === 'object' && parsed.data) {
        candidates.push({ key, bundle: parsed });
      }
    }
    if (candidates.length === 0) return null;

    // Prefer a backup with user data if the top one is empty after a repeat action
    const withData = candidates.find(c =>
      (c.bundle.integrity?.missionCount || 0) > 0 ||
      (c.bundle.integrity?.sessionCount || 0) > 0 ||
      Boolean(c.bundle.integrity?.hasActiveSession)
    );
    return (withData || candidates[0]).bundle;
  }

  function normalizeDatasetAndBuildMappings(rawInput, report) {
    const idMap = {
      missions: {},
      steps: {},
      sessions: {}
    };

    const now = Date.now();
    let missionsStore = clone(rawInput.missionsStore);
    const legacyMission = clone(rawInput.legacyActiveMission);
    const legacyProgress = clone(rawInput.legacyMissionProgress);

    // Step 1: Legacy v7/v10 mission migration if v11.5 store is empty
    if ((!missionsStore || !Array.isArray(missionsStore.missions) || missionsStore.missions.length === 0) &&
        legacyMission && Array.isArray(legacyMission.sessions) && legacyMission.sessions.length > 0) {
      const rawSteps = legacyMission.sessions;
      const idx = Math.max(0, Math.min(Number(legacyProgress?.currentSessionIndex) || 0, rawSteps.length));
      const mId = schema.ensureEntityId(legacyMission.id);
      if (legacyMission.id && legacyMission.id !== mId) {
        idMap.missions[String(legacyMission.id)] = mId;
      }
      const steps = rawSteps.map((s, i) => {
        const sId = schema.ensureEntityId(s?.id);
        if (s?.id && s.id !== sId) {
          idMap.steps[String(s.id)] = sId;
        }
        return {
          ...clone(s),
          id: sId,
          missionId: mId,
          title: String(s?.title || ('Schritt ' + (i + 1))).trim(),
          task: String(s?.task || ''),
          durationMinutes: Math.max(1, Math.round(Number(s?.durationMinutes ?? s?.duration) || 20)),
          status: i < idx ? 'completed' : (i === idx ? 'active' : 'pending'),
          reason: String(s?.reason || ''),
          requiredTools: Array.isArray(s?.requiredTools) ? s.requiredTools : [],
          recommendedApps: Array.isArray(s?.recommendedApps) ? s.recommendedApps : [],
          createdAt: Number(s?.createdAt) || now,
          updatedAt: Number(s?.updatedAt) || now,
          completedAt: i < idx ? (Number(s?.completedAt) || now) : null
        };
      });
      const currentStepId = steps[idx]?.id || null;
      const status = currentStepId ? 'active' : 'completed';
      const migratedMission = {
        id: mId,
        title: String(legacyMission.title || legacyMission.goal?.title || 'Focus Mission').trim(),
        subject: String(legacyMission.subject || legacyMission.goal?.subject || 'Allgemein'),
        type: String(legacyMission.type || legacyMission.goal?.type || 'custom'),
        deadlineText: String(legacyMission.deadlineText || legacyMission.goal?.deadlineText || 'Nicht angegeben'),
        status,
        createdAt: Number(legacyMission.createdAt) || now,
        updatedAt: Number(legacyMission.updatedAt) || now,
        completedAt: status === 'completed' ? now : null,
        archivedAt: null,
        currentStepId,
        estimate: clone(legacyMission.estimate || {}),
        sourceAnalysis: clone(legacyMission.sourceAnalysis || {}),
        steps,
        progress: {
          completedStepIds: steps.slice(0, idx).map(x => x.id),
          history: clone(legacyProgress?.history || []),
          initialStepCount: Number(legacyProgress?.initialSessionCount) || steps.length
        }
      };
      missionsStore = {
        schemaVersion: 1,
        missions: [migratedMission],
        selectedMissionId: mId,
        primaryMissionId: status === 'active' ? mId : null,
        updatedAt: now
      };
    }

    if (!missionsStore || typeof missionsStore !== 'object' || Array.isArray(missionsStore)) {
      missionsStore = {
        schemaVersion: 1,
        missions: [],
        selectedMissionId: null,
        primaryMissionId: null,
        updatedAt: now
      };
    }

    const rawMissions = Array.isArray(missionsStore.missions) ? missionsStore.missions : [];
    const usedMissionIds = new Set();
    const usedGlobalStepIds = new Set();
    const normalizedMissions = [];

    for (let mIndex = 0; mIndex < rawMissions.length; mIndex += 1) {
      const m = rawMissions[mIndex];
      if (!m || typeof m !== 'object') continue;

      const oldMissionId = m.id != null ? String(m.id).trim() : '';
      let nextMissionId = schema.isValidEntityId(oldMissionId) && !usedMissionIds.has(oldMissionId)
        ? oldMissionId
        : schema.generateUUID();

      if (oldMissionId !== nextMissionId) {
        if (oldMissionId) {
          idMap.missions[oldMissionId] = nextMissionId;
        }
        if (report) {
          report.repairedFields.push({
            path: `missions[${mIndex}].id`,
            field: 'id',
            from: m.id ?? null,
            to: nextMissionId,
            reason: 'missing_or_invalid_uuid'
          });
        }
      }
      usedMissionIds.add(nextMissionId);

      const rawSteps = Array.isArray(m.steps) ? m.steps : (Array.isArray(m.sessions) ? m.sessions : []);
      const localStepMap = {};
      const usedStepIdsInMission = new Set();

      const normalizedSteps = rawSteps.map((s, sIndex) => {
        const oldStepId = s?.id != null ? String(s.id).trim() : '';
        const stepIdValid = schema.isValidEntityId(oldStepId) && !usedStepIdsInMission.has(oldStepId);
        const nextStepId = stepIdValid ? oldStepId : schema.generateUUID();

        if (oldStepId !== nextStepId) {
          if (oldStepId) {
            localStepMap[oldStepId] = nextStepId;
            if (!usedGlobalStepIds.has(oldStepId)) {
              idMap.steps[oldStepId] = nextStepId;
            }
            idMap.steps[`${oldMissionId || nextMissionId}::${oldStepId}`] = nextStepId;
          }
          if (report) {
            report.repairedFields.push({
              path: `missions[${mIndex}].steps[${sIndex}].id`,
              field: 'id',
              from: s?.id ?? null,
              to: nextStepId,
              reason: 'missing_or_invalid_step_uuid'
            });
          }
        }

        usedStepIdsInMission.add(nextStepId);
        usedGlobalStepIds.add(nextStepId);

        const durationMinutes = Math.max(1, Math.round(Number(s?.durationMinutes ?? s?.duration) || 20));
        const status = STEP_STATUSES.includes(s?.status)
          ? s.status
          : (sIndex === 0 ? 'active' : 'pending');

        if (s?.missionId !== nextMissionId && report) {
          report.repairedFields.push({
            path: `missions[${mIndex}].steps[${sIndex}].missionId`,
            field: 'missionId',
            from: s?.missionId ?? null,
            to: nextMissionId,
            reason: 'step_mission_link_normalized'
          });
        }

        return {
          ...clone(s || {}),
          id: nextStepId,
          missionId: nextMissionId,
          title: String(s?.title || ('Schritt ' + (sIndex + 1))).trim(),
          task: String(s?.task ?? ''),
          durationMinutes,
          status,
          reason: String(s?.reason ?? ''),
          requiredTools: Array.isArray(s?.requiredTools) ? clone(s.requiredTools) : [],
          recommendedApps: Array.isArray(s?.recommendedApps) ? clone(s.recommendedApps) : [],
          createdAt: Number(s?.createdAt) || now,
          updatedAt: Number(s?.updatedAt) || now,
          completedAt: status === 'completed' ? (Number(s?.completedAt) || now) : null
        };
      });

      const resolveStepRef = refId => {
        if (!refId) return null;
        const key = String(refId).trim();
        const mapped = localStepMap[key] || idMap.steps[`${oldMissionId}::${key}`] || idMap.steps[key] || key;
        return normalizedSteps.some(st => st.id === mapped) ? mapped : null;
      };

      const status = MISSION_STATUSES.includes(m.status) ? m.status : 'active';
      let currentStepId = resolveStepRef(m.currentStepId);
      const currentStepObj = normalizedSteps.find(st => st.id === currentStepId);
      if (status === 'active' && (!currentStepId || currentStepObj?.status === 'completed')) {
        currentStepId = normalizedSteps.find(st => st.status === 'active')?.id ||
          normalizedSteps.find(st => st.status !== 'completed')?.id ||
          currentStepId ||
          null;
      } else if (status !== 'active') {
        currentStepId = null;
      }

      const rawCompletedIds = Array.isArray(m.progress?.completedStepIds)
        ? m.progress.completedStepIds
        : normalizedSteps.filter(st => st.status === 'completed').map(st => st.id);

      const completedStepIds = Array.from(new Set([
        ...rawCompletedIds.map(resolveStepRef).filter(Boolean),
        ...normalizedSteps.filter(st => st.status === 'completed').map(st => st.id)
      ]));

      const progressHistory = Array.isArray(m.progress?.history)
        ? m.progress.history.map(entry => {
            if (!entry || typeof entry !== 'object') return entry;
            const mappedStepId = entry.stepId ? (resolveStepRef(entry.stepId) || entry.stepId) : null;
            return { ...clone(entry), ...(mappedStepId ? { stepId: mappedStepId } : {}) };
          })
        : [];

      const archivedCurrentStepId = m.archivedCurrentStepId
        ? (resolveStepRef(m.archivedCurrentStepId) || null)
        : undefined;

      const normalizedMission = {
        ...clone(m),
        id: nextMissionId,
        title: String(m.title || m.goal?.title || 'Focus Mission').trim(),
        subject: String(m.subject || m.goal?.subject || 'Allgemein'),
        type: String(m.type || m.goal?.type || 'custom'),
        deadlineText: String(m.deadlineText || m.goal?.deadlineText || 'Nicht angegeben'),
        status,
        createdAt: Number(m.createdAt) || now,
        updatedAt: Number(m.updatedAt) || now,
        completedAt: m.completedAt || (status === 'completed' ? now : null),
        archivedAt: m.archivedAt || (status === 'archived' ? now : null),
        currentStepId,
        ...(archivedCurrentStepId !== undefined ? { archivedCurrentStepId } : {}),
        estimate: clone(m.estimate || {}),
        sourceAnalysis: clone(m.sourceAnalysis || {}),
        steps: normalizedSteps,
        progress: {
          ...(m.progress && typeof m.progress === 'object' ? clone(m.progress) : {}),
          completedStepIds,
          history: progressHistory,
          initialStepCount: Number(m.progress?.initialStepCount) || normalizedSteps.length
        }
      };

      normalizedMissions.push(normalizedMission);
    }

    // Preserve separate selectedMissionId and primaryMissionId
    const mapMissionRef = id => {
      if (id == null) return null;
      const key = String(id).trim();
      const mapped = idMap.missions[key] || key;
      return normalizedMissions.some(m => m.id === mapped) ? mapped : null;
    };

    const activeMissions = normalizedMissions.filter(m => m.status === 'active');
    let primaryMissionId = mapMissionRef(missionsStore.primaryMissionId);
    if (!primaryMissionId || !activeMissions.some(m => m.id === primaryMissionId)) {
      primaryMissionId = activeMissions[0]?.id || null;
    }

    let selectedMissionId = mapMissionRef(missionsStore.selectedMissionId);
    if (missionsStore.selectedMissionId != null && !selectedMissionId) {
      selectedMissionId = primaryMissionId || normalizedMissions[0]?.id || null;
    } else if (missionsStore.selectedMissionId == null && primaryMissionId && normalizedMissions.length > 0 && rawInput.preserveNullSelected !== true) {
      selectedMissionId = missionsStore.selectedMissionId ?? null;
    }

    const normalizedMissionStore = {
      schemaVersion: schema.versions.missions,
      stepSchemaVersion: schema.versions.missionSteps,
      missions: normalizedMissions,
      selectedMissionId,
      primaryMissionId,
      updatedAt: Number(missionsStore.updatedAt) || now
    };

    // Helper to map session missionId & missionStepId atomically
    function normalizeSessionRecord(rawSession, isTerminal = false, usedSessionIds = null) {
      if (!rawSession || typeof rawSession !== 'object' || Array.isArray(rawSession)) return null;
      const s = clone(rawSession);

      const oldId = s.id != null ? String(s.id).trim() : '';
      const isUnique = !usedSessionIds || !usedSessionIds.has(oldId);
      const nextId = schema.isValidEntityId(oldId) && isUnique ? oldId : schema.generateUUID();

      if (oldId !== nextId) {
        if (oldId) {
          idMap.sessions[oldId] = nextId;
        }
        if (report) {
          report.repairedFields.push({
            path: `session[${oldId || 'new'}].id`,
            field: 'id',
            from: s.id ?? null,
            to: nextId,
            reason: 'missing_or_invalid_session_uuid'
          });
        }
      }
      if (usedSessionIds) {
        usedSessionIds.add(nextId);
      }
      s.id = nextId;

      if (s.type === 'quick') {
        s.missionId = null;
        s.missionStepId = null;
      } else if (s.type === 'mission') {
        const oldMid = s.missionId != null ? String(s.missionId).trim() : '';
        if (oldMid && !idMap.missions[oldMid] && !schema.isValidEntityId(oldMid)) {
          idMap.missions[oldMid] = schema.generateUUID();
        }
        const mappedMid = idMap.missions[oldMid] || (schema.isValidEntityId(oldMid) ? oldMid : schema.generateUUID());
        s.missionId = mappedMid;

        const oldSid = s.missionStepId != null ? String(s.missionStepId).trim() : '';
        let mappedSid = idMap.steps[`${oldMid}::${oldSid}`] || idMap.steps[`${mappedMid}::${oldSid}`] || idMap.steps[oldSid] || oldSid;
        if (!schema.isValidEntityId(mappedSid)) {
          mappedSid = schema.generateUUID();
          if (oldSid) {
            idMap.steps[oldSid] = mappedSid;
          }
        }
        s.missionStepId = mappedSid;
      }

      s.plannedMinutes = Math.max(1, Math.round(Number(s.plannedMinutes) || 25));
      s.actualMinutes = Math.max(0, Math.round(Number(s.actualMinutes) || 0));
      if (s.elapsedMs != null) {
        s.elapsedMs = Math.max(0, Number(s.elapsedMs) || 0);
      }
      if (isTerminal) {
        s.schemaVersion = schema.versions.sessionHistory;
      } else {
        s.schemaVersion = schema.versions.sessions;
      }
      return s;
    }

    // Step 2: Normalize and deduplicate Session History
    const rawHistoryStore = rawInput.sessionHistoryStore;
    const rawHistoryArray = Array.isArray(rawHistoryStore)
      ? rawHistoryStore
      : (Array.isArray(rawHistoryStore?.sessions) ? rawHistoryStore.sessions : []);

    const deduplicatedById = new Map();
    for (const item of rawHistoryArray) {
      if (!item || typeof item !== 'object') continue;
      const normalized = normalizeSessionRecord(item, true, null);
      if (!normalized) continue;
      if (deduplicatedById.has(normalized.id)) {
        const prev = deduplicatedById.get(normalized.id);
        const merged = {
          ...prev,
          ...normalized,
          id: normalized.id,
          result: { ...(prev.result || {}), ...(normalized.result || {}) },
          ui: { ...(prev.ui || {}), ...(normalized.ui || {}) }
        };
        deduplicatedById.set(normalized.id, merged);
      } else {
        deduplicatedById.set(normalized.id, normalized);
      }
    }

    const normalizedHistorySessions = Array.from(deduplicatedById.values())
      .sort((a, b) => (Number(b.finishedAt) || 0) - (Number(a.finishedAt) || 0))
      .slice(0, 500);

    const normalizedHistoryStore = {
      schemaVersion: schema.versions.sessionHistory,
      sessions: normalizedHistorySessions
    };

    // Step 3: Normalize Active Session & Last Session
    let normalizedActiveSession = null;
    if (rawInput.activeSession && typeof rawInput.activeSession === 'object') {
      const candidateActive = normalizeSessionRecord(rawInput.activeSession, false, null);
      if (candidateActive) {
        const isAlreadyFinalized = candidateActive.lifecycleState !== 'awaiting_review' && (
          TERMINAL_STATES.has(candidateActive.status) ||
          candidateActive.finalizationState === 'saved' ||
          normalizedHistorySessions.some(h => h.id === candidateActive.id && TERMINAL_STATES.has(h.status))
        );
        if (!isAlreadyFinalized) {
          if (candidateActive.status === 'running' && !ACTIVE_STATES.has(candidateActive.lifecycleState)) {
            candidateActive.lifecycleState = 'awaiting_resume';
          }
          normalizedActiveSession = candidateActive;
        } else if (report) {
          report.repairedFields.push({
            path: 'activeSession',
            field: 'activeSession',
            from: candidateActive.id,
            to: null,
            reason: 'cleared_finalized_session_residue'
          });
        }
      }
    }

    let normalizedLastSession = null;
    if (rawInput.lastSession && typeof rawInput.lastSession === 'object') {
      const rawLastId = rawInput.lastSession.id != null ? String(rawInput.lastSession.id).trim() : '';
      const mappedLastId = idMap.sessions[rawLastId] || rawLastId;
      normalizedLastSession = normalizeSessionRecord({
        ...clone(rawInput.lastSession),
        ...(mappedLastId ? { id: mappedLastId } : {})
      }, TERMINAL_STATES.has(rawInput.lastSession.status), null);
    }

    const rawLang = String(rawInput.preferences?.language || 'de').trim();
    const normalizedLanguage = ['de', 'en'].includes(rawLang) ? rawLang : 'de';

    return {
      idMap,
      missionsStore: normalizedMissionStore,
      sessionHistoryStore: normalizedHistoryStore,
      activeSession: normalizedActiveSession,
      lastSession: normalizedLastSession,
      preferences: {
        schemaVersion: schema.versions.preferences,
        language: normalizedLanguage,
        pwaInstallHelpDismissed: rawInput.preferences?.pwaInstallHelpDismissed ?? null
      },
      lastAiPlan: rawInput.lastAiPlan ? clone(rawInput.lastAiPlan) : null
    };
  }

  function computeStateFingerprint(missionsStore, sessionHistoryStore, activeSession) {
    return computeChecksum({
      m: (missionsStore?.missions || []).map(m => ({
        id: m?.id,
        u: m?.updatedAt,
        s: (m?.steps || []).map(s => ({ id: s?.id, mid: s?.missionId, st: s?.status }))
      })),
      sel: missionsStore?.selectedMissionId ?? null,
      pri: missionsStore?.primaryMissionId ?? null,
      h: (sessionHistoryStore?.sessions || []).map(s => ({ id: s?.id, st: s?.status, mid: s?.missionId, sid: s?.missionStepId })),
      a: activeSession ? { id: activeSession.id, st: activeSession.lifecycleState || activeSession.status, el: activeSession.elapsedMs } : null
    });
  }

  function inspectCurrentStateNeedsMigration() {
    const rawMissions = safeParseJSON(storage.getText(KEYS.missions, null), null);
    const rawLegacyMission = safeParseJSON(storage.getText(KEYS.legacyActiveMission, null), null);
    const rawHistory = safeParseJSON(storage.getText(KEYS.sessionHistory, null), null);
    const rawLegacyHistory = safeParseJSON(storage.getText(KEYS.legacySessionHistory, null), null);
    const rawActive = safeParseJSON(storage.getText(KEYS.activeSession, null), null);
    const schemaState = storage.getJSON(KEYS.schemaState, null);
    const migrationState = storage.getJSON(KEYS.migrationState, null);

    const detectedVersions = schema.detectDataVersions({
      schemaState,
      missionsStore: rawMissions,
      sessionHistoryStore: rawHistory,
      activeSession: rawActive
    });

    const compatibility = schema.checkSchemaCompatibility(detectedVersions);
    if (!compatibility.supported) {
      return {
        supported: false,
        needsMigration: false,
        compatibility
      };
    }

    // Legacy stores present without canonical stores
    if ((!rawMissions || !Array.isArray(rawMissions.missions) || rawMissions.missions.length === 0) &&
        rawLegacyMission && Array.isArray(rawLegacyMission.sessions) && rawLegacyMission.sessions.length > 0) {
      return { supported: true, needsMigration: true, reason: 'legacy_v7_mission_pending', compatibility };
    }

    if (!rawHistory && rawLegacyHistory) {
      return { supported: true, needsMigration: true, reason: 'legacy_v11_history_pending', compatibility };
    }

    // Check if any mission or step lacks valid ID or step.missionId
    if (rawMissions && Array.isArray(rawMissions.missions)) {
      const mIds = new Set();
      for (const m of rawMissions.missions) {
        if (!schema.isValidEntityId(m?.id) || mIds.has(m.id)) {
          return { supported: true, needsMigration: true, reason: 'mission_uuid_repair_needed', compatibility };
        }
        mIds.add(m.id);
        if (!Array.isArray(m.steps)) {
          return { supported: true, needsMigration: true, reason: 'mission_steps_repair_needed', compatibility };
        }
        const sIds = new Set();
        for (const s of m.steps) {
          if (!schema.isValidEntityId(s?.id) || sIds.has(s.id) || s.missionId !== m.id) {
            return { supported: true, needsMigration: true, reason: 'step_uuid_or_mission_link_needed', compatibility };
          }
          sIds.add(s.id);
        }
      }
    }

    // Check if history has duplicate IDs or missing session IDs
    if (rawHistory && Array.isArray(rawHistory.sessions)) {
      const hIds = new Set();
      for (const s of rawHistory.sessions) {
        if (!schema.isValidEntityId(s?.id) || hIds.has(s.id)) {
          return { supported: true, needsMigration: true, reason: 'history_dedup_or_uuid_needed', compatibility };
        }
        hIds.add(s.id);
      }
    }

    const fingerprint = computeStateFingerprint(rawMissions, rawHistory, rawActive);
    if (!schemaState || !migrationState || migrationState.schemaVersion !== schema.versions.bundle) {
      return { supported: true, needsMigration: true, reason: 'v16_schema_initialization_needed', compatibility };
    }

    if (migrationState.dataFingerprint !== fingerprint && compatibility.needsMigration) {
      return { supported: true, needsMigration: true, reason: 'schema_version_upgrade_needed', compatibility };
    }

    return {
      supported: true,
      needsMigration: false,
      reason: 'already_migrated',
      compatibility,
      fingerprint
    };
  }

  function runStartupMigration(options = {}) {
    const inspection = inspectCurrentStateNeedsMigration();

    if (!inspection.supported) {
      const unsupportedResult = Object.freeze({
        status: 'unsupported_future_schema',
        migrated: false,
        compatibility: inspection.compatibility,
        timestamp: Date.now()
      });
      console.warn('[MigrationRunner]', unsupportedResult);
      return unsupportedResult;
    }

    if (!inspection.needsMigration && options.force !== true) {
      const noOpResult = Object.freeze({
        status: 'no_migration_needed',
        migrated: false,
        reason: inspection.reason,
        schemaVersions: { ...schema.versions },
        timestamp: Date.now()
      });
      return noOpResult;
    }

    // 1. Full backup before any mutation
    const preMigrationBackup = createBackup('pre_migration', KEYS.backupLatest);
    const rawRollbackSnapshot = preMigrationBackup.rawSnapshot;

    try {
      const report = validator.createReport();
      const rawInput = {
        missionsStore: safeParseJSON(storage.getText(KEYS.missions, null), null),
        legacyActiveMission: safeParseJSON(storage.getText(KEYS.legacyActiveMission, null), null),
        legacyMissionProgress: safeParseJSON(storage.getText(KEYS.legacyMissionProgress, null), null),
        sessionHistoryStore: safeParseJSON(storage.getText(KEYS.sessionHistory, null), null) ||
          safeParseJSON(storage.getText(KEYS.legacySessionHistory, null), null),
        activeSession: safeParseJSON(storage.getText(KEYS.activeSession, null), null),
        lastSession: safeParseJSON(storage.getText(KEYS.lastSession, null), null),
        preferences: {
          language: storage.getText(KEYS.language, 'de') || 'de',
          pwaInstallHelpDismissed: storage.getText(KEYS.pwaHelp, null)
        },
        lastAiPlan: safeParseJSON(storage.getText(KEYS.lastAiPlan, null), null)
      };

      // 2. Run sequential normalization & UUID link migration
      const migrated = normalizeDatasetAndBuildMappings(rawInput, report);

      // 3. Validate migrated result before writing
      const candidateBundle = {
        exportVersion: schema.exportVersion,
        schemaVersions: { ...schema.versions },
        data: {
          missions: migrated.missionsStore,
          sessionHistory: migrated.sessionHistoryStore,
          activeSession: migrated.activeSession,
          lastSession: migrated.lastSession,
          preferences: migrated.preferences
        }
      };

      const validation = validator.validateBundle(candidateBundle);
      validation.repairedFields.push(...report.repairedFields);

      if (!validation.valid) {
        restoreRawLocalStorageSnapshot(rawRollbackSnapshot);
        const failResult = Object.freeze({
          status: 'migration_failed_and_restored',
          migrated: false,
          reason: 'post_migration_validation_failed',
          validation,
          timestamp: Date.now()
        });
        console.error('[MigrationRunner]', failResult);
        return failResult;
      }

      // 4. Persist validated data atomically
      if (migrated.missionsStore.missions.length > 0 || storage.has(KEYS.missions) || rawInput.legacyActiveMission) {
        storage.setJSON(KEYS.missions, migrated.missionsStore);
        storage.setText(KEYS.missionsMigrated, '1');
      }

      if (migrated.sessionHistoryStore.sessions.length > 0 || storage.has(KEYS.sessionHistory) || rawInput.sessionHistoryStore) {
        storage.setJSON(KEYS.sessionHistory, migrated.sessionHistoryStore);
      }

      if (migrated.activeSession) {
        storage.setJSON(KEYS.activeSession, migrated.activeSession);
      } else if (storage.has(KEYS.activeSession)) {
        storage.remove(KEYS.activeSession);
      }

      if (migrated.lastSession) {
        storage.setJSON(KEYS.lastSession, migrated.lastSession);
      }

      if (storage.getText(KEYS.language, null) === 'ru') {
        storage.setText(KEYS.language, 'de');
      }

      const fingerprint = computeStateFingerprint(
        migrated.missionsStore,
        migrated.sessionHistoryStore,
        migrated.activeSession
      );

      schema.writeStoredSchemaState({
        lastMigrationAt: Date.now(),
        dataFingerprint: fingerprint
      });

      const prevMigrationState = storage.getJSON(KEYS.migrationState, null) || {};
      const migrationMeta = {
        schemaVersion: schema.versions.bundle,
        applicationVersion: schema.applicationVersion,
        lastStatus: 'migration_completed',
        lastRunAt: Date.now(),
        runCount: (Number(prevMigrationState.runCount) || 0) + 1,
        dataFingerprint: fingerprint,
        appliedSteps: ['v14_legacy_stores_to_v15', 'v15_to_v16_uuid_and_links', 'v16_schema_stamp'],
        idMap: migrated.idMap,
        repairedCount: validation.repairedFields.length
      };
      storage.setJSON(KEYS.migrationState, migrationMeta);

      // Update latest backup with the clean migrated state if previous backup had no data
      if (preMigrationBackup.integrity.missionCount === 0 && migrated.missionsStore.missions.length > 0) {
        createBackup('post_legacy_migration', KEYS.backupLatest, { forceLatest: true });
      }

      const completedResult = Object.freeze({
        status: 'migration_completed',
        migrated: true,
        idMap: migrated.idMap,
        validation,
        migrationMeta,
        timestamp: Date.now()
      });
      console.info('[MigrationRunner]', {
        event: 'migration_completed',
        repairedCount: validation.repairedFields.length,
        missions: migrated.missionsStore.missions.length,
        sessions: migrated.sessionHistoryStore.sessions.length
      });
      return completedResult;
    } catch (error) {
      restoreRawLocalStorageSnapshot(rawRollbackSnapshot);
      const errorResult = Object.freeze({
        status: 'migration_failed_and_restored',
        migrated: false,
        reason: error?.message || 'unexpected_migration_exception',
        timestamp: Date.now()
      });
      console.error('[MigrationRunner]', errorResult);
      return errorResult;
    }
  }

  function syncRuntimeStateAfterDataChange() {
    try {
      const missionsRepo = App.repositories?.missions || global.missionRepository;
      const sessionsRepo = App.repositories?.sessions;

      // Sync active session & recovery state
      const active = sessionsRepo ? sessionsRepo.loadActive() : safeParseJSON(storage.getText(KEYS.activeSession, null), null);
      if (active) {
        const migratedActive = typeof global.migrateActiveSessionV112 === 'function'
          ? (global.migrateActiveSessionV112(active) || active)
          : active;
        global.activeSession = clone(migratedActive);
        if (migratedActive.lifecycleState === 'awaiting_resume') {
          global.sessionRecoveryCandidate = clone(migratedActive);
        } else {
          global.sessionRecoveryCandidate = null;
        }
      } else {
        if (typeof global.stopSessionRuntime === 'function') {
          try { global.stopSessionRuntime(); } catch {}
        }
        if (typeof global.releaseRecoveryNavigationV112 === 'function') {
          try { global.releaseRecoveryNavigationV112(); } catch {}
        }
        global.activeSession = null;
        global.sessionRecoveryCandidate = null;
        const indicator = global.document?.getElementById('islandIndicator');
        indicator?.classList.add('hidden');
      }

      // Sync selected/primary mission & legacy compatibility globals
      if (missionsRepo && typeof missionsRepo.loadStore === 'function') {
        const st = missionsRepo.loadStore();
        const selected = missionsRepo.getById(st.selectedMissionId) || missionsRepo.getPrimary() || null;
        if (typeof global.bindMissionCompatibilityV115 === 'function') {
          global.bindMissionCompatibilityV115(selected);
        }
      }

      // Sync language if changed
      const savedLang = storage.getText(KEYS.language, null);
      if (savedLang && ['de', 'en'].includes(savedLang) && typeof global.setLanguage === 'function') {
        global.setLanguage(savedLang, false);
      }

      // Refresh UI views
      try { global.refreshMissionUIV116?.(); } catch {}
      try { global.renderMultiMissionUIV115?.(); } catch {}
      try { global.renderHomeCarouselV116?.(); } catch {}
      try { global.refreshSessionHistoryUI?.(); } catch {}
      try { global.refreshHomeStats?.(); } catch {}
      try { global.refreshStatsScreen?.(); } catch {}
      try { App.features?.dataManagement?.refreshUI?.(); } catch {}
    } catch (error) {
      console.warn('[MigrationRunner]', { event: 'runtime_sync_warning', message: error?.message });
    }
  }

  function applyBundleAtomically(bundleInput, options = {}) {
    const preApplySnapshot = captureRawLocalStorageSnapshot();

    try {
      const detected = schema.detectDataVersions(bundleInput);
      const compat = schema.checkSchemaCompatibility(detected);
      if (!compat.supported) {
        return {
          ok: false,
          status: 'unsupported_future_schema',
          errors: ['unsupported_future_schema'],
          compatibility: compat
        };
      }

      const dataRoot = (bundleInput?.data && typeof bundleInput.data === 'object' && !Array.isArray(bundleInput.data))
        ? bundleInput.data
        : bundleInput;

      const rawMissionsStore = dataRoot?.missionsStore ?? dataRoot?.missions ?? {
        schemaVersion: 1,
        missions: [],
        selectedMissionId: dataRoot?.selectedMissionId ?? null,
        primaryMissionId: dataRoot?.primaryMissionId ?? null,
        updatedAt: Date.now()
      };

      const normalizedMissionsInput = Array.isArray(rawMissionsStore)
        ? {
            schemaVersion: 1,
            missions: rawMissionsStore,
            selectedMissionId: dataRoot?.selectedMissionId ?? null,
            primaryMissionId: dataRoot?.primaryMissionId ?? null,
            updatedAt: Date.now()
          }
        : {
            ...clone(rawMissionsStore),
            selectedMissionId: dataRoot?.selectedMissionId !== undefined
              ? dataRoot.selectedMissionId
              : (rawMissionsStore.selectedMissionId ?? null),
            primaryMissionId: dataRoot?.primaryMissionId !== undefined
              ? dataRoot.primaryMissionId
              : (rawMissionsStore.primaryMissionId ?? null)
          };

      const rawHistoryStore = dataRoot?.sessionHistoryStore ?? dataRoot?.sessionHistory ?? {
        schemaVersion: 1,
        sessions: []
      };

      // Validate input strictly before normalization if requested
      if (options.strictValidation === true) {
        const strictReport = validator.validateBundle({
          exportVersion: bundleInput.exportVersion ?? 1,
          schemaVersions: bundleInput.schemaVersions,
          data: {
            missions: normalizedMissionsInput,
            sessionHistory: rawHistoryStore,
            activeSession: dataRoot?.activeSession ?? null,
            lastSession: dataRoot?.lastSession ?? null,
            preferences: dataRoot?.preferences ?? { language: 'de' }
          }
        }, { requireSections: options.requireSections === true });

        if (!strictReport.valid) {
          return {
            ok: false,
            status: 'validation_failed',
            validation: strictReport,
            errors: strictReport.blockingErrors.map(e => e.code)
          };
        }
      }

      const normReport = validator.createReport();
      const normalized = normalizeDatasetAndBuildMappings({
        missionsStore: normalizedMissionsInput,
        legacyActiveMission: dataRoot?.legacyCompatibility?.activeMission ?? null,
        legacyMissionProgress: dataRoot?.legacyCompatibility?.missionProgress ?? null,
        sessionHistoryStore: rawHistoryStore,
        activeSession: dataRoot?.activeSession ?? null,
        lastSession: dataRoot?.lastSession ?? null,
        preferences: dataRoot?.preferences ?? {
          language: storage.getText(KEYS.language, 'de') || 'de',
          pwaInstallHelpDismissed: storage.getText(KEYS.pwaHelp, null)
        },
        lastAiPlan: dataRoot?.lastAiPlan ?? null,
        preserveNullSelected: true
      }, normReport);

      const postValidation = validator.validateBundle({
        exportVersion: schema.exportVersion,
        schemaVersions: { ...schema.versions },
        data: {
          missions: normalized.missionsStore,
          sessionHistory: normalized.sessionHistoryStore,
          activeSession: normalized.activeSession,
          lastSession: normalized.lastSession,
          preferences: normalized.preferences
        }
      });

      if (!postValidation.valid) {
        restoreRawLocalStorageSnapshot(preApplySnapshot);
        return {
          ok: false,
          status: 'validation_failed',
          validation: postValidation,
          errors: postValidation.blockingErrors.map(e => e.code)
        };
      }

      // Write atomically to localStorage
      storage.setJSON(KEYS.missions, normalized.missionsStore);
      storage.setText(KEYS.missionsMigrated, '1');
      storage.setJSON(KEYS.sessionHistory, normalized.sessionHistoryStore);

      if (normalized.activeSession) {
        storage.setJSON(KEYS.activeSession, normalized.activeSession);
      } else {
        storage.remove(KEYS.activeSession);
      }

      if (normalized.lastSession) {
        storage.setJSON(KEYS.lastSession, normalized.lastSession);
      } else {
        storage.remove(KEYS.lastSession);
      }

      if (normalized.lastAiPlan) {
        storage.setJSON(KEYS.lastAiPlan, normalized.lastAiPlan);
      } else {
        storage.remove(KEYS.lastAiPlan);
      }

      if (normalized.preferences?.language) {
        storage.setText(KEYS.language, normalized.preferences.language);
      }
      if (normalized.preferences?.pwaInstallHelpDismissed != null) {
        storage.setText(KEYS.pwaHelp, String(normalized.preferences.pwaInstallHelpDismissed));
      }

      const fingerprint = computeStateFingerprint(
        normalized.missionsStore,
        normalized.sessionHistoryStore,
        normalized.activeSession
      );

      schema.writeStoredSchemaState({
        lastAppliedAt: Date.now(),
        dataFingerprint: fingerprint
      });

      storage.setJSON(KEYS.migrationState, {
        schemaVersion: schema.versions.bundle,
        applicationVersion: schema.applicationVersion,
        lastStatus: 'migration_completed',
        lastRunAt: Date.now(),
        dataFingerprint: fingerprint,
        appliedSteps: ['bundle_atomic_apply', 'v16_schema_stamp'],
        idMap: normalized.idMap
      });

      syncRuntimeStateAfterDataChange();

      return {
        ok: true,
        status: 'migration_completed',
        validation: postValidation,
        normalized,
        missionCount: normalized.missionsStore.missions.length,
        sessionCount: normalized.sessionHistoryStore.sessions.length
      };
    } catch (error) {
      restoreRawLocalStorageSnapshot(preApplySnapshot);
      return {
        ok: false,
        status: 'migration_failed_and_restored',
        errors: [error?.message || 'apply_bundle_failed']
      };
    }
  }

  // Run startup migration immediately so repositories see clean v16 data
  const bootMigrationResult = runStartupMigration();

  const runnerApi = Object.freeze({
    runStartupMigration,
    inspectCurrentStateNeedsMigration,
    collectCurrentDataBundle,
    createBackup,
    getLastBackup,
    normalizeDatasetAndBuildMappings,
    applyBundleAtomically,
    syncRuntimeStateAfterDataChange,
    computeChecksum,
    get lastBootResult() {
      return bootMigrationResult;
    }
  });

  App.data = App.data || {};
  App.data.migrationRunner = App.core.registerModule(MODULE_NAME, runnerApi, {
    virtualPath: 'src/data/migration-runner.js',
    version: '16.0',
    status: 'canonical-migration-runner'
  });
})(window);
