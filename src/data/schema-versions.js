/**
 * Physical module: src/data/schema-versions.js
 * Version: v16 Data Stability
 *
 * Centralized registry of data schema versions, canonical storage keys,
 * and stable UUID helpers for Mission, MissionStep, and FocusSession.
 */
(function initializeSchemaVersionsV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  if (!App?.core?.registerModule) {
    throw new Error('Architecture Shell must be initialized before Schema Versions.');
  }

  const MODULE_NAME = 'data.schemaVersions';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  const APPLICATION_VERSION = 'v16 Data Stability';
  const EXPORT_VERSION = 1;

  const DATA_SCHEMA_VERSIONS = Object.freeze({
    bundle: 1,
    missions: 1,
    missionSteps: 1,
    sessions: 1,
    sessionHistory: 1,
    preferences: 1,
    recovery: 1
  });

  const STORAGE_KEYS = Object.freeze({
    // Canonical keys (v14/v15 preserved)
    language: 'focus-mode-language',
    pwaHelp: 'focus-pwa-help-v11',
    lastAiPlan: 'focus-mode-last-ai-plan',
    legacyActiveMission: 'focus-mode-active-mission',
    legacyMissionProgress: 'focus-mode-mission-progress-v7',
    missions: 'focus-mode-missions-v11-5',
    missionsMigrated: 'focus-mode-missions-v11-5-migrated',
    activeSession: 'focusmode-active-session-v11',
    lastSession: 'focusmode-last-session-v11',
    sessionHistory: 'focusmode-session-history-v11-1',
    legacySessionHistory: 'focusmode-session-history-v11',

    // Existing quarantine/backup keys
    missionsCorruptBackup: 'focus-mode-missions-v11-5-corrupt-backup',
    activeSessionRecoveryBackup: 'focusmode-active-session-v11-recovery-backup',
    activeSessionRepoBackup: 'focusmode-active-session-v11-repository-backup',
    lastSessionRepoBackup: 'focusmode-last-session-v11-repository-backup',
    sessionHistoryRepoBackup: 'focusmode-session-history-repository-backup',
    sessionHistoryCorruptBackup: 'focusmode-session-history-corrupt-backup',

    // New v16 Data Stability namespace keys
    schemaState: 'focus-mode-data-schema-v16',
    migrationState: 'focus-mode-migration-state-v16',
    backupLatest: 'focus-mode-backup-latest-v16',
    backupImport: 'focus-mode-backup-import-v16',
    backupReset: 'focus-mode-backup-reset-v16'
  });

  const FORBIDDEN_ID_LITERALS = new Set([
    'null',
    'undefined',
    'nan',
    'true',
    'false',
    '[object object]'
  ]);

  const UUID_REGEX = /^(?:(?:mission|step|session)_)?[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const SAFE_ID_REGEX = /^[a-zA-Z0-9_.:-]+$/;

  function fallbackRandomUUID() {
    const bytes = new Uint8Array(16);
    if (global.crypto && typeof global.crypto.getRandomValues === 'function') {
      global.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < 16; i += 1) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    return (
      hex.slice(0, 8) + '-' +
      hex.slice(8, 12) + '-' +
      hex.slice(12, 16) + '-' +
      hex.slice(16, 20) + '-' +
      hex.slice(20, 32)
    );
  }

  function generateUUID(prefix = '') {
    const raw = (global.crypto && typeof global.crypto.randomUUID === 'function')
      ? global.crypto.randomUUID()
      : fallbackRandomUUID();
    const cleanPrefix = String(prefix || '').trim();
    return cleanPrefix ? cleanPrefix + '_' + raw : raw;
  }

  function isUUID(value) {
    if (typeof value !== 'string') return false;
    return UUID_REGEX.test(value.trim());
  }

  function isValidEntityId(value) {
    if (typeof value !== 'string') return false;
    const trimmed = value.trim();
    if (trimmed.length < 2 || trimmed.length > 160) return false;
    if (FORBIDDEN_ID_LITERALS.has(trimmed.toLowerCase())) return false;
    return SAFE_ID_REGEX.test(trimmed);
  }

  function ensureEntityId(value, prefix = '') {
    if (isValidEntityId(value)) {
      return String(value).trim();
    }
    return generateUUID(prefix);
  }

  function parsePositiveVersion(value) {
    const num = Number(value);
    return Number.isInteger(num) && num >= 0 ? num : null;
  }

  function detectDataVersions(source = {}) {
    const schemaHeader = source.schemaVersions || source.schemaState?.versions || null;
    const missionsStore = source.missionsStore ?? source.data?.missions ?? null;
    const historyStore = source.sessionHistoryStore ?? source.data?.sessionHistory ?? null;
    const activeSession = source.activeSession ?? source.data?.activeSession ?? null;
    const recovery = source.recovery ?? source.data?.recovery ?? null;
    const preferences = source.preferences ?? source.data?.preferences ?? null;

    const detected = {
      bundle: parsePositiveVersion(schemaHeader?.bundle ?? source.exportVersion) ?? 0,
      missions: parsePositiveVersion(schemaHeader?.missions ?? missionsStore?.schemaVersion) ?? 0,
      missionSteps: parsePositiveVersion(schemaHeader?.missionSteps ?? missionsStore?.stepSchemaVersion) ?? 0,
      sessions: parsePositiveVersion(schemaHeader?.sessions ?? activeSession?.schemaVersion) ?? 0,
      sessionHistory: parsePositiveVersion(schemaHeader?.sessionHistory ?? historyStore?.schemaVersion) ?? 0,
      preferences: parsePositiveVersion(schemaHeader?.preferences ?? preferences?.schemaVersion) ?? 0,
      recovery: parsePositiveVersion(schemaHeader?.recovery ?? recovery?.schemaVersion) ?? 0
    };

    // If missions store is v1 and all steps have missionId and valid IDs, missionSteps is v1
    if (detected.missions === 1 && detected.missionSteps === 0 && Array.isArray(missionsStore?.missions)) {
      const allStepsV1 = missionsStore.missions.every(m =>
        Array.isArray(m?.steps) && m.steps.every(s => isValidEntityId(s?.id) && s?.missionId === m.id)
      );
      if (allStepsV1 && schemaHeader) {
        detected.missionSteps = 1;
      }
    }

    return detected;
  }

  function checkSchemaCompatibility(detectedVersions = {}) {
    const unsupportedDomains = [];
    const legacyDomains = [];

    for (const [domain, targetVersion] of Object.entries(DATA_SCHEMA_VERSIONS)) {
      const current = parsePositiveVersion(detectedVersions[domain]);
      if (current !== null && current > targetVersion) {
        unsupportedDomains.push({
          domain,
          detectedVersion: current,
          supportedVersion: targetVersion
        });
      } else if (current === null || current < targetVersion) {
        legacyDomains.push({
          domain,
          detectedVersion: current ?? 0,
          targetVersion
        });
      }
    }

    if (unsupportedDomains.length > 0) {
      return Object.freeze({
        supported: false,
        needsMigration: false,
        status: 'unsupported_future_schema',
        unsupportedDomains,
        legacyDomains
      });
    }

    return Object.freeze({
      supported: true,
      needsMigration: legacyDomains.length > 0,
      status: legacyDomains.length > 0 ? 'migration_needed' : 'up_to_date',
      unsupportedDomains: [],
      legacyDomains
    });
  }

  function readStoredSchemaState() {
    const storage = App.services?.storage;
    if (!storage) return null;
    return storage.getJSON(STORAGE_KEYS.schemaState, null);
  }

  function writeStoredSchemaState(extra = {}) {
    const storage = App.services?.storage;
    if (!storage) return false;
    const payload = {
      applicationVersion: APPLICATION_VERSION,
      exportVersion: EXPORT_VERSION,
      versions: { ...DATA_SCHEMA_VERSIONS },
      updatedAt: Date.now(),
      ...extra
    };
    return storage.setJSON(STORAGE_KEYS.schemaState, payload);
  }

  const schemaApi = Object.freeze({
    applicationVersion: APPLICATION_VERSION,
    exportVersion: EXPORT_VERSION,
    versions: DATA_SCHEMA_VERSIONS,
    keys: STORAGE_KEYS,
    generateUUID,
    isUUID,
    isValidEntityId,
    ensureEntityId,
    detectDataVersions,
    checkSchemaCompatibility,
    readStoredSchemaState,
    writeStoredSchemaState
  });

  App.data = App.data || {};
  App.data.schema = App.core.registerModule(MODULE_NAME, schemaApi, {
    virtualPath: 'src/data/schema-versions.js',
    version: '16.0',
    status: 'canonical-schema-registry'
  });
  App.data.schemaVersions = DATA_SCHEMA_VERSIONS;
  global.DATA_SCHEMA_VERSIONS = DATA_SCHEMA_VERSIONS;
})(window);
