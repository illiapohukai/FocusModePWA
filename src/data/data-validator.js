/**
 * Physical module: src/data/data-validator.js
 * Version: v16 Data Stability
 *
 * Non-destructive Data Validator for Missions, MissionSteps, FocusSessions,
 * Session History, Recovery State, and full Export/Import Bundles.
 */
(function initializeDataValidatorV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const schema = App?.data?.schema;

  if (!App?.core?.registerModule || !schema) {
    throw new Error('Schema Versions module is required before Data Validator.');
  }

  const MODULE_NAME = 'data.validator';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  const MISSION_STATUSES = new Set(['active', 'completed', 'archived']);
  const STEP_STATUSES = new Set(['pending', 'active', 'completed']);
  const SESSION_TYPES = new Set(['quick', 'mission']);
  const ACTIVE_SESSION_STATES = new Set(['created', 'running', 'paused', 'awaiting_resume', 'awaiting_review']);
  const TERMINAL_SESSION_STATES = new Set(['completed', 'cancelled', 'interrupted']);
  const ALL_SESSION_STATES = new Set([...ACTIVE_SESSION_STATES, ...TERMINAL_SESSION_STATES]);
  const MAX_ACTIVE_MISSIONS = 2;

  function createReport() {
    return {
      valid: true,
      warnings: [],
      blockingErrors: [],
      repairedFields: [],
      rejectedRecords: []
    };
  }

  function addBlockingError(report, code, path, message, detail = null) {
    report.blockingErrors.push({ code, path, message, detail });
    report.valid = false;
  }

  function addWarning(report, code, path, message, detail = null) {
    report.warnings.push({ code, path, message, detail });
  }

  function addRepairedField(report, path, field, from, to, reason) {
    report.repairedFields.push({ path, field, from, to, reason });
  }

  function addRejectedRecord(report, domain, id, reason, errors = []) {
    report.rejectedRecords.push({ domain, id: id || null, reason, errors });
  }

  function mergeReports(target, source) {
    if (!source) return target;
    target.warnings.push(...(source.warnings || []));
    target.blockingErrors.push(...(source.blockingErrors || []));
    target.repairedFields.push(...(source.repairedFields || []));
    target.rejectedRecords.push(...(source.rejectedRecords || []));
    target.valid = target.blockingErrors.length === 0;
    return target;
  }

  function finalizeReport(report) {
    report.valid = report.blockingErrors.length === 0;
    return report;
  }

  function validateMissionStep(step, parentMissionId = null, index = 0) {
    const report = createReport();
    const path = `missions[${parentMissionId || '?'}].steps[${index}]`;

    if (!step || typeof step !== 'object' || Array.isArray(step)) {
      addBlockingError(report, 'step_not_object', path, 'MissionStep must be an object.');
      return finalizeReport(report);
    }

    if (!schema.isValidEntityId(step.id)) {
      addBlockingError(report, 'step_id_invalid', `${path}.id`, 'MissionStep id is missing or invalid.', step.id);
    }

    const stepMissionId = step.missionId ?? parentMissionId;
    if (!schema.isValidEntityId(stepMissionId)) {
      addBlockingError(report, 'step_mission_id_invalid', `${path}.missionId`, 'MissionStep missionId is missing or invalid.', step.missionId);
    } else if (parentMissionId && step.missionId && step.missionId !== parentMissionId) {
      addBlockingError(report, 'step_mission_id_mismatch', `${path}.missionId`, 'MissionStep missionId does not match parent Mission id.', {
        stepMissionId: step.missionId,
        parentMissionId
      });
    } else if (parentMissionId && !step.missionId) {
      addWarning(report, 'step_mission_id_inferred', `${path}.missionId`, 'MissionStep missionId inferred from parent Mission.', parentMissionId);
    }

    const title = String(step.title ?? '').trim();
    if (!title) {
      addBlockingError(report, 'step_title_missing', `${path}.title`, 'MissionStep title is required.');
    }

    const duration = Number(step.durationMinutes ?? step.duration);
    if (!Number.isFinite(duration) || duration <= 0) {
      addBlockingError(report, 'step_duration_invalid', `${path}.durationMinutes`, 'MissionStep duration must be a positive number.', step.durationMinutes ?? step.duration);
    }

    const status = String(step.status ?? '').trim();
    if (!STEP_STATUSES.has(status)) {
      addBlockingError(report, 'step_status_invalid', `${path}.status`, 'MissionStep status must be pending, active, or completed.', step.status);
    }

    return finalizeReport(report);
  }

  function validateMission(mission, index = 0) {
    const report = createReport();
    const path = `missions[${index}]`;

    if (!mission || typeof mission !== 'object' || Array.isArray(mission)) {
      addBlockingError(report, 'mission_not_object', path, 'Mission must be an object.');
      return finalizeReport(report);
    }

    if (!schema.isValidEntityId(mission.id)) {
      addBlockingError(report, 'mission_id_invalid', `${path}.id`, 'Mission id is missing or invalid.', mission.id);
    }

    const title = String(mission.title ?? '').trim();
    if (!title) {
      addBlockingError(report, 'mission_title_missing', `${path}.title`, 'Mission title is required.');
    }

    const status = String(mission.status ?? '').trim();
    if (!MISSION_STATUSES.has(status)) {
      addBlockingError(report, 'mission_status_invalid', `${path}.status`, 'Mission status must be active, completed, or archived.', mission.status);
    }

    if (!Array.isArray(mission.steps)) {
      addBlockingError(report, 'mission_steps_not_array', `${path}.steps`, 'Mission steps must be an array.');
      return finalizeReport(report);
    }

    if (mission.steps.length === 0) {
      addBlockingError(report, 'mission_steps_empty', `${path}.steps`, 'Mission must contain at least one step.');
    }

    const stepIds = new Set();
    mission.steps.forEach((step, sIdx) => {
      const stepReport = validateMissionStep(step, mission.id, sIdx);
      mergeReports(report, stepReport);
      if (schema.isValidEntityId(step?.id)) {
        if (stepIds.has(step.id)) {
          addBlockingError(report, 'duplicate_step_id', `${path}.steps[${sIdx}].id`, 'Duplicate MissionStep id within Mission.', step.id);
        }
        stepIds.add(step.id);
      }
    });

    if (status === 'active') {
      const hasIncomplete = mission.steps.some(s => s && s.status !== 'completed');
      if (hasIncomplete && (!mission.currentStepId || !stepIds.has(mission.currentStepId))) {
        addBlockingError(report, 'mission_current_step_invalid', `${path}.currentStepId`, 'Active mission currentStepId must reference an existing step.', mission.currentStepId);
      }
    } else if (mission.currentStepId != null && !stepIds.has(mission.currentStepId)) {
      addBlockingError(report, 'mission_current_step_missing', `${path}.currentStepId`, 'Mission currentStepId does not exist in steps.', mission.currentStepId);
    }

    const completedStepIds = mission.progress?.completedStepIds;
    if (completedStepIds != null && !Array.isArray(completedStepIds)) {
      addBlockingError(report, 'mission_completed_steps_invalid', `${path}.progress.completedStepIds`, 'completedStepIds must be an array.');
    } else if (Array.isArray(completedStepIds)) {
      const completedFromSteps = mission.steps.filter(s => s?.status === 'completed').map(s => s.id);
      for (const cid of completedFromSteps) {
        if (!completedStepIds.includes(cid)) {
          addWarning(report, 'mission_completed_step_unlisted', `${path}.progress.completedStepIds`, 'Completed step missing from progress.completedStepIds.', cid);
        }
      }
    }

    return finalizeReport(report);
  }

  function validateMissionStore(store) {
    const report = createReport();
    const path = 'missionsStore';

    if (!store || typeof store !== 'object' || Array.isArray(store)) {
      addBlockingError(report, 'mission_store_invalid', path, 'Mission store must be an object.');
      return finalizeReport(report);
    }

    if (!Array.isArray(store.missions)) {
      addBlockingError(report, 'missions_not_array', `${path}.missions`, 'missionsStore.missions must be an array.');
      return finalizeReport(report);
    }

    const missionIds = new Set();
    let activeCount = 0;

    store.missions.forEach((mission, idx) => {
      const mReport = validateMission(mission, idx);
      mergeReports(report, mReport);
      if (!mReport.valid) {
        addRejectedRecord(report, 'missions', mission?.id, 'mission_validation_failed', mReport.blockingErrors.map(e => e.code));
      }
      if (schema.isValidEntityId(mission?.id)) {
        if (missionIds.has(mission.id)) {
          addBlockingError(report, 'duplicate_mission_id', `${path}.missions[${idx}].id`, 'Duplicate Mission id detected.', mission.id);
        }
        missionIds.add(mission.id);
      }
      if (mission?.status === 'active') {
        activeCount += 1;
      }
    });

    if (activeCount > MAX_ACTIVE_MISSIONS) {
      addBlockingError(report, 'active_mission_limit_exceeded', `${path}.missions`, `Maximum ${MAX_ACTIVE_MISSIONS} active missions allowed, found ${activeCount}.`, activeCount);
    }

    if (store.selectedMissionId != null && !missionIds.has(store.selectedMissionId)) {
      addBlockingError(report, 'selected_mission_missing', `${path}.selectedMissionId`, 'selectedMissionId does not point to an existing mission.', store.selectedMissionId);
    }

    if (store.primaryMissionId != null) {
      const primaryMission = store.missions.find(m => m && m.id === store.primaryMissionId);
      if (!primaryMission) {
        addBlockingError(report, 'primary_mission_missing', `${path}.primaryMissionId`, 'primaryMissionId does not point to an existing mission.', store.primaryMissionId);
      } else if (primaryMission.status !== 'active') {
        addBlockingError(report, 'primary_mission_not_active', `${path}.primaryMissionId`, 'primaryMissionId must reference an active mission.', store.primaryMissionId);
      }
    }

    return finalizeReport(report);
  }

  function validateFocusSession(session, options = {}) {
    const report = createReport();
    const path = options.path || 'session';

    if (!session || typeof session !== 'object' || Array.isArray(session)) {
      addBlockingError(report, 'session_not_object', path, 'FocusSession must be an object.');
      return finalizeReport(report);
    }

    if (!schema.isValidEntityId(session.id)) {
      addBlockingError(report, 'session_id_invalid', `${path}.id`, 'FocusSession id is missing or invalid.', session.id);
    }

    if (!SESSION_TYPES.has(session.type)) {
      addBlockingError(report, 'session_type_invalid', `${path}.type`, 'FocusSession type must be quick or mission.', session.type);
    }

    const title = String(session.title ?? '').trim();
    if (!title) {
      addBlockingError(report, 'session_title_missing', `${path}.title`, 'FocusSession title is required.');
    }

    const plannedMinutes = Number(session.plannedMinutes);
    if (!Number.isFinite(plannedMinutes) || plannedMinutes <= 0) {
      addBlockingError(report, 'session_planned_minutes_invalid', `${path}.plannedMinutes`, 'plannedMinutes must be a positive number.', session.plannedMinutes);
    }

    const actualMinutes = session.actualMinutes != null ? Number(session.actualMinutes) : 0;
    if (!Number.isFinite(actualMinutes) || actualMinutes < 0) {
      addBlockingError(report, 'session_actual_minutes_negative', `${path}.actualMinutes`, 'actualMinutes cannot be negative.', session.actualMinutes);
    }

    if (session.elapsedMs != null) {
      const elapsedMs = Number(session.elapsedMs);
      if (!Number.isFinite(elapsedMs) || elapsedMs < 0) {
        addBlockingError(report, 'session_elapsed_ms_invalid', `${path}.elapsedMs`, 'elapsedMs must be a non-negative number.', session.elapsedMs);
      }
    }

    const status = String(session.status ?? '').trim();
    const lifecycleState = String(session.lifecycleState ?? '').trim();
    const effectiveState = lifecycleState || status;

    if (!status || !ALL_SESSION_STATES.has(status)) {
      addBlockingError(report, 'session_status_invalid', `${path}.status`, 'FocusSession status is invalid.', session.status);
    }

    if (options.terminal === true && !TERMINAL_SESSION_STATES.has(status)) {
      addBlockingError(report, 'session_terminal_status_required', `${path}.status`, 'History session must have a terminal status.', session.status);
    }

    if (options.active === true && TERMINAL_SESSION_STATES.has(effectiveState)) {
      addBlockingError(report, 'session_active_status_required', `${path}.lifecycleState`, 'Active session cannot have a terminal state.', effectiveState);
    }

    if (status !== 'created' || options.terminal === true || options.active === true) {
      const startedAt = Number(session.startedAt);
      if (!Number.isFinite(startedAt) || startedAt <= 0) {
        addBlockingError(report, 'session_started_at_invalid', `${path}.startedAt`, 'startedAt must be a positive timestamp.', session.startedAt);
      }
    }

    if (options.terminal === true) {
      const finishedAt = Number(session.finishedAt);
      if (!Number.isFinite(finishedAt) || finishedAt <= 0) {
        addBlockingError(report, 'session_finished_at_invalid', `${path}.finishedAt`, 'finishedAt must be a positive timestamp.', session.finishedAt);
      } else if (Number.isFinite(Number(session.startedAt)) && finishedAt < Number(session.startedAt)) {
        addBlockingError(report, 'session_finished_before_started', `${path}.finishedAt`, 'finishedAt cannot be earlier than startedAt.');
      }
    }

    if (session.type === 'quick') {
      if (session.missionId != null || session.missionStepId != null) {
        addBlockingError(report, 'quick_mission_link_forbidden', `${path}.missionId`, 'Quick Focus session must not have missionId or missionStepId.', {
          missionId: session.missionId,
          missionStepId: session.missionStepId
        });
      }
    } else if (session.type === 'mission') {
      if (!schema.isValidEntityId(session.missionId) || !schema.isValidEntityId(session.missionStepId)) {
        addBlockingError(report, 'mission_link_required', `${path}.missionId`, 'Mission Session requires valid missionId and missionStepId.', {
          missionId: session.missionId,
          missionStepId: session.missionStepId
        });
      }
    }

    return finalizeReport(report);
  }

  function validateHistory(historyStore) {
    const report = createReport();
    const path = 'sessionHistory';

    const sessions = Array.isArray(historyStore)
      ? historyStore
      : (historyStore && typeof historyStore === 'object' ? historyStore.sessions : null);

    if (!Array.isArray(sessions)) {
      addBlockingError(report, 'history_not_array', `${path}.sessions`, 'Session history must contain a sessions array.');
      return finalizeReport(report);
    }

    const seenIds = new Set();
    sessions.forEach((session, idx) => {
      const sReport = validateFocusSession(session, {
        terminal: true,
        path: `${path}.sessions[${idx}]`
      });
      mergeReports(report, sReport);
      if (!sReport.valid) {
        addRejectedRecord(report, 'sessionHistory', session?.id, 'history_session_invalid', sReport.blockingErrors.map(e => e.code));
      }
      if (schema.isValidEntityId(session?.id)) {
        if (seenIds.has(session.id)) {
          addBlockingError(report, 'duplicate_session_id', `${path}.sessions[${idx}].id`, 'Duplicate session.id in Session History.', session.id);
          addRejectedRecord(report, 'sessionHistory', session.id, 'duplicate_session_id', ['duplicate_session_id']);
        }
        seenIds.add(session.id);
      }
    });

    return finalizeReport(report);
  }

  function validateRecovery(activeSession, historyStore = null, missionStore = null) {
    const report = createReport();
    const path = 'recovery';

    if (activeSession == null) {
      return finalizeReport(report);
    }

    const sReport = validateFocusSession(activeSession, {
      active: true,
      path: `${path}.activeSession`
    });
    mergeReports(report, sReport);

    const historySessions = Array.isArray(historyStore)
      ? historyStore
      : (Array.isArray(historyStore?.sessions) ? historyStore.sessions : []);

    if (schema.isValidEntityId(activeSession.id) && activeSession.lifecycleState !== 'awaiting_review') {
      const alreadyFinalized = historySessions.some(h => h && h.id === activeSession.id && TERMINAL_SESSION_STATES.has(h.status));
      if (alreadyFinalized || activeSession.finalizationState === 'saved' || TERMINAL_SESSION_STATES.has(activeSession.status)) {
        addBlockingError(report, 'recovery_session_already_finalized', `${path}.activeSession.id`, 'Completed/finalized session cannot remain in active recovery.', activeSession.id);
      }
    }

    if (activeSession.type === 'mission' && missionStore && Array.isArray(missionStore.missions)) {
      const mission = missionStore.missions.find(m => m && m.id === activeSession.missionId);
      if (!mission) {
        addWarning(report, 'recovery_mission_reference_missing', `${path}.activeSession.missionId`, 'Recovery session references a Mission that does not exist in Mission Store.', activeSession.missionId);
      } else {
        const step = Array.isArray(mission.steps) ? mission.steps.find(s => s && s.id === activeSession.missionStepId) : null;
        if (!step) {
          addWarning(report, 'recovery_step_reference_missing', `${path}.activeSession.missionStepId`, 'Recovery session references a MissionStep that does not exist in Mission.', activeSession.missionStepId);
        }
      }
    }

    return finalizeReport(report);
  }

  function validateBundle(bundle, options = {}) {
    const report = createReport();

    if (!bundle || typeof bundle !== 'object' || Array.isArray(bundle)) {
      addBlockingError(report, 'bundle_not_object', 'bundle', 'Bundle must be a JSON object.');
      return finalizeReport(report);
    }

    const detectedVersions = schema.detectDataVersions(bundle);
    const compatibility = schema.checkSchemaCompatibility(detectedVersions);
    if (!compatibility.supported) {
      addBlockingError(
        report,
        'unsupported_future_schema',
        'bundle.schemaVersions',
        'Bundle uses a newer unsupported schema version.',
        compatibility.unsupportedDomains
      );
      return finalizeReport(report);
    }

    const dataRoot = (bundle.data && typeof bundle.data === 'object' && !Array.isArray(bundle.data))
      ? bundle.data
      : bundle;

    const missionsStore = dataRoot.missionsStore ?? dataRoot.missions;
    const sessionHistoryStore = dataRoot.sessionHistoryStore ?? dataRoot.sessionHistory;
    const activeSession = dataRoot.activeSession ?? null;
    const lastSession = dataRoot.lastSession ?? null;
    const preferences = dataRoot.preferences ?? null;

    if (options.requireSections === true) {
      if (missionsStore == null) {
        addBlockingError(report, 'missing_missions_section', 'bundle.missions', 'Required missions section is missing.');
      }
      if (sessionHistoryStore == null) {
        addBlockingError(report, 'missing_history_section', 'bundle.sessionHistory', 'Required sessionHistory section is missing.');
      }
      if (preferences == null || typeof preferences !== 'object' || Array.isArray(preferences)) {
        addBlockingError(report, 'missing_preferences_section', 'bundle.preferences', 'Required preferences section is missing.');
      }
    }

    if (missionsStore != null) {
      mergeReports(report, validateMissionStore(missionsStore));
    }

    if (sessionHistoryStore != null) {
      mergeReports(report, validateHistory(sessionHistoryStore));
    }

    if (activeSession != null) {
      mergeReports(report, validateRecovery(activeSession, sessionHistoryStore, missionsStore));
    }

    if (lastSession != null) {
      mergeReports(report, validateFocusSession(lastSession, { path: 'lastSession' }));
    }

    if (preferences != null) {
      if (typeof preferences !== 'object' || Array.isArray(preferences)) {
        addBlockingError(report, 'preferences_invalid', 'preferences', 'Preferences must be an object.');
      } else if (preferences.language != null && !['de', 'en', 'ru'].includes(String(preferences.language))) {
        addWarning(report, 'preferences_language_unknown', 'preferences.language', 'Unsupported language code will default to de.', preferences.language);
      }
    }

    return finalizeReport(report);
  }

  const validatorApi = Object.freeze({
    createReport,
    validateMissionStep,
    validateMission,
    validateMissionStore,
    validateFocusSession,
    validateHistory,
    validateRecovery,
    validateBundle
  });

  App.data = App.data || {};
  App.data.validator = App.core.registerModule(MODULE_NAME, validatorApi, {
    virtualPath: 'src/data/data-validator.js',
    version: '16.0',
    status: 'canonical-validator'
  });
})(window);
