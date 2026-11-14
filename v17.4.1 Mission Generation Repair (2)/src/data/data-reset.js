/**
 * Physical module: src/data/data-reset.js
 * Version: v16 Data Stability
 *
 * Safe Local Data Reset and Last Backup Restore without deleting
 * the last usable backup or breaking language/PWA settings.
 */
(function initializeDataResetV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const storage = App?.services?.storage;
  const schema = App?.data?.schema;
  const validator = App?.data?.validator;
  const runner = App?.data?.migrationRunner;

  if (!App?.core?.registerModule || !storage || !schema || !validator || !runner) {
    throw new Error('Storage, Schema, Validator, and Migration Runner are required before Data Reset.');
  }

  const MODULE_NAME = 'data.reset';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  const KEYS = schema.keys;

  function safeResetLocalData(options = {}) {
    const keepLanguage = options.keepLanguage !== false;
    const savedLanguage = storage.getText(KEYS.language, 'de') || 'de';
    const savedPwaHelp = storage.getText(KEYS.pwaHelp, null);

    // 1. Always create a backup before reset without deleting the last usable backup
    const currentBundle = runner.collectCurrentDataBundle('pre_reset');
    const currentHasData = (
      currentBundle.integrity.missionCount > 0 ||
      currentBundle.integrity.sessionCount > 0 ||
      currentBundle.integrity.hasActiveSession
    );

    const existingResetBackup = storage.getJSON(KEYS.backupReset, null);
    const existingResetHasData = Boolean(
      existingResetBackup?.integrity?.missionCount > 0 ||
      existingResetBackup?.integrity?.sessionCount > 0 ||
      existingResetBackup?.integrity?.hasActiveSession
    );

    if (currentHasData || !existingResetBackup || !existingResetHasData) {
      storage.setJSON(KEYS.backupReset, currentBundle);
    }

    const existingLatestBackup = storage.getJSON(KEYS.backupLatest, null);
    const existingLatestHasData = Boolean(
      existingLatestBackup?.integrity?.missionCount > 0 ||
      existingLatestBackup?.integrity?.sessionCount > 0 ||
      existingLatestBackup?.integrity?.hasActiveSession
    );

    if (currentHasData || !existingLatestBackup || !existingLatestHasData) {
      storage.setJSON(KEYS.backupLatest, currentBundle);
    }

    // 2. Stop active timer / session runtime cleanly
    try { global.stopSessionRuntime?.(); } catch {}
    try { global.stopUc?.(); } catch {}
    try { global.releaseRecoveryNavigationV112?.(); } catch {}
    if (global.timerInterval) {
      clearInterval(global.timerInterval);
      global.timerInterval = null;
    }

    // 3. Reset user data stores to clean profile while keeping backups intact
    const emptyMissionStore = {
      schemaVersion: schema.versions.missions,
      stepSchemaVersion: schema.versions.missionSteps,
      missions: [],
      selectedMissionId: null,
      primaryMissionId: null,
      updatedAt: Date.now()
    };

    const emptyHistoryStore = {
      schemaVersion: schema.versions.sessionHistory,
      sessions: []
    };

    storage.setJSON(KEYS.missions, emptyMissionStore);
    storage.setText(KEYS.missionsMigrated, '1');
    storage.setJSON(KEYS.sessionHistory, emptyHistoryStore);

    storage.remove(KEYS.activeSession);
    storage.remove(KEYS.lastSession);
    storage.remove(KEYS.lastAiPlan);
    storage.remove(KEYS.legacyActiveMission);
    storage.remove(KEYS.legacyMissionProgress);
    storage.remove(KEYS.legacySessionHistory);

    if (keepLanguage) {
      storage.setText(KEYS.language, ['de', 'en'].includes(savedLanguage) ? savedLanguage : 'de');
    } else {
      storage.remove(KEYS.language);
    }

    if (savedPwaHelp !== null) {
      storage.setText(KEYS.pwaHelp, savedPwaHelp);
    }

    schema.writeStoredSchemaState({
      lastResetAt: Date.now()
    });

    storage.setJSON(KEYS.migrationState, {
      schemaVersion: schema.versions.bundle,
      applicationVersion: schema.applicationVersion,
      lastStatus: 'migration_completed',
      lastRunAt: Date.now(),
      appliedSteps: ['safe_reset_clean_profile'],
      idMap: { missions: {}, steps: {}, sessions: {} }
    });

    // 4. Reset runtime memory globals and sync UI
    global.activeSession = null;
    global.sessionRecoveryCandidate = null;
    global.activeAIPlan = null;
    global.pendingAIPlan = null;

    runner.syncRuntimeStateAfterDataChange();

    const usableBackup = runner.getLastBackup(KEYS.backupReset);
    return {
      ok: true,
      resetAt: new Date().toISOString(),
      languagePreserved: keepLanguage ? savedLanguage : null,
      backupPreserved: Boolean(usableBackup),
      lastBackupExportedAt: usableBackup?.exportedAt || null,
      lastBackupReason: usableBackup?.reason || null
    };
  }

  function inspectLastBackup(preferredKey = null) {
    const backup = runner.getLastBackup(preferredKey);
    if (!backup) {
      return {
        available: false,
        backup: null
      };
    }

    const validation = validator.validateBundle(backup, { requireSections: true });
    return {
      available: true,
      valid: validation.valid,
      exportedAt: backup.exportedAt || null,
      timestamp: backup.timestamp || null,
      reason: backup.reason || 'auto_backup',
      applicationVersion: backup.applicationVersion || schema.applicationVersion,
      bundleVersion: backup.exportVersion || 1,
      missionCount: backup.integrity?.missionCount ?? (backup.data?.missions?.missions?.length || 0),
      sessionCount: backup.integrity?.sessionCount ?? (backup.data?.sessionHistory?.sessions?.length || 0),
      hasActiveSession: Boolean(backup.data?.activeSession),
      validation,
      backup
    };
  }

  function restoreLastBackup(preferredKey = null) {
    const info = inspectLastBackup(preferredKey);
    if (!info.available || !info.backup) {
      return {
        ok: false,
        code: 'no_backup_available',
        errors: ['no_backup_available']
      };
    }

    if (!info.valid) {
      return {
        ok: false,
        code: 'backup_invalid',
        errors: info.validation.blockingErrors.map(e => e.code),
        validation: info.validation
      };
    }

    const applyResult = runner.applyBundleAtomically(info.backup, {
      strictValidation: true,
      requireSections: true
    });

    if (!applyResult.ok) {
      return {
        ok: false,
        code: applyResult.status || 'restore_failed',
        errors: applyResult.errors || ['restore_failed'],
        validation: applyResult.validation
      };
    }

    return {
      ok: true,
      restoredAt: new Date().toISOString(),
      backupExportedAt: info.exportedAt,
      backupReason: info.reason,
      missionCount: applyResult.missionCount,
      sessionCount: applyResult.sessionCount
    };
  }

  const resetApi = Object.freeze({
    safeResetLocalData,
    inspectLastBackup,
    restoreLastBackup
  });

  App.data = App.data || {};
  App.data.reset = App.core.registerModule(MODULE_NAME, resetApi, {
    virtualPath: 'src/data/data-reset.js',
    version: '16.0',
    status: 'canonical-reset-restore'
  });
})(window);
