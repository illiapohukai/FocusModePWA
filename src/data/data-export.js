/**
 * Physical module: src/data/data-export.js
 * Version: v16 Data Stability
 *
 * Offline-first User Data Export into a validated single JSON file
 * without mutating local application state or exposing secrets.
 */
(function initializeDataExportV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const schema = App?.data?.schema;
  const validator = App?.data?.validator;
  const runner = App?.data?.migrationRunner;

  if (!App?.core?.registerModule || !schema || !validator || !runner) {
    throw new Error('Schema, Validator, and Migration Runner are required before Data Export.');
  }

  const MODULE_NAME = 'data.export';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  let lastExportedBundle = null;
  let lastExportSummary = null;

  function formatExportDate(date = new Date()) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  const EXPORT_SCOPES = Object.freeze({ full: 'full', missions: 'missions' });

  /* Filenames never contain user content (no Mission titles, no names): fixed prefix + date. */
  function formatExportFilename(date = new Date(), scope = EXPORT_SCOPES.full) {
    const suffix = scope === EXPORT_SCOPES.missions ? 'missionen-' : '';
    return `focus-mode-backup-${suffix}${formatExportDate(date)}.json`.replace(/[^a-z0-9.-]/g, '');
  }

  function sanitizeExportBundle(rawBundle) {
    const clone = JSON.parse(JSON.stringify(rawBundle));
    // Never include raw localStorage snapshot or any secret/credential keys in external export file
    delete clone.rawSnapshot;
    // v17.17 data minimisation: internal migration bookkeeping (id maps, fingerprints) is not user data.
    if (clone.data) delete clone.data.migrationMetadata;
    if (clone.data?.preferences) {
      delete clone.data.preferences.apiKey;
      delete clone.data.preferences.token;
      delete clone.data.preferences.secret;
    }
    return clone;
  }

  function normalizeScope(scope) {
    return scope === EXPORT_SCOPES.missions ? EXPORT_SCOPES.missions : EXPORT_SCOPES.full;
  }

  /* v17.17 "Nur Missionen & Einstellungen": no Session History, no active/last Session. */
  function applyExportScope(bundle, scope) {
    if (scope !== EXPORT_SCOPES.missions) return bundle;
    bundle.data.sessionHistory = { schemaVersion: schema.versions.sessionHistory, sessions: [] };
    bundle.data.activeSession = null;
    bundle.data.lastSession = null;
    bundle.data.recovery = { schemaVersion: 1, hasRecoverableSession: false, activeSessionId: null, lifecycleState: null };
    bundle.integrity.sessionCount = 0;
    bundle.integrity.hasActiveSession = false;
    return bundle;
  }

  function buildExportBundle(options = {}) {
    const now = new Date();
    const scope = normalizeScope(options.scope);
    const rawBundle = runner.collectCurrentDataBundle('user_export');
    const exportBundle = applyExportScope(sanitizeExportBundle(rawBundle), scope);
    exportBundle.format = schema.backupFormat;
    exportBundle.exportedAt = now.toISOString();
    exportBundle.exportVersion = schema.exportVersion;
    exportBundle.applicationVersion = schema.applicationVersion;
    exportBundle.appVersion = schema.appRelease;
    exportBundle.scope = scope;
    exportBundle.trust = 'local_backup_unverified';
    exportBundle.schemaVersions = { ...schema.versions };
    // Digest is added asynchronously by exportUserData (Web Crypto); until then it is explicitly pending.
    // The legacy FNV-1a checksum was never verified and did not match the exported payload; it is replaced
    // by the SHA-256 digest below. Internal local backups keep their own fields unchanged.
    delete exportBundle.integrity.checksum;
    exportBundle.integrity = {
      ...exportBundle.integrity,
      algorithm: validator.integrity.algorithm,
      canonicalization: validator.integrity.canonicalization,
      covers: ['format', 'exportVersion', 'schemaVersions', 'scope', 'data'],
      digest: null,
      status: 'pending'
    };

    const validation = validator.validateBundle(exportBundle, { requireSections: true });

    const filename = formatExportFilename(now, scope);
    const summary = {
      ok: validation.valid,
      filename,
      scope,
      exportedAt: exportBundle.exportedAt,
      missionCount: exportBundle.integrity.missionCount,
      activeMissionCount: exportBundle.integrity.activeMissionCount,
      sessionCount: exportBundle.integrity.sessionCount,
      hasActiveSession: exportBundle.integrity.hasActiveSession,
      bundleVersion: exportBundle.exportVersion,
      applicationVersion: exportBundle.applicationVersion,
      schemaVersions: exportBundle.schemaVersions,
      validation,
      bundle: exportBundle,
      json: JSON.stringify(exportBundle, null, 2)
    };

    return summary;
  }

  /* Adds the SHA-256 corruption-detection digest. If Web Crypto is unavailable the export still
     works and says so ("unavailable") instead of claiming a verification that never happened. */
  async function attachIntegrity(summary) {
    const bundle = summary.bundle;
    let digest = null;
    try {
      digest = await validator.integrity.computeBundleDigest(bundle);
    } catch (error) {
      digest = null;
    }
    bundle.integrity.digest = digest;
    bundle.integrity.status = digest ? 'present' : 'unavailable';
    summary.integrityStatus = bundle.integrity.status;
    summary.json = JSON.stringify(bundle, null, 2);
    return summary;
  }

  function downloadJSON(json, filename) {
    const blob = new global.Blob([json], { type: 'application/json;charset=utf-8' });
    const url = global.URL.createObjectURL(blob);
    const link = global.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.rel = 'noopener';
    link.style.display = 'none';
    global.document.body.appendChild(link);
    link.click();
    setTimeout(() => {
      link.remove();
      global.URL.revokeObjectURL(url);
    }, 150);
  }

  async function exportUserData(options = {}) {
    const result = buildExportBundle(options);
    if (!result.ok) {
      return {
        ...result,
        error: 'export_validation_failed',
        validationCodes: result.validation.blockingErrors.map(e => e.code).slice(0, 5)
      };
    }
    await attachIntegrity(result);
    lastExportedBundle = result.bundle;
    lastExportSummary = result;

    if (options.download !== false && global.document && typeof global.Blob === 'function') {
      try {
        downloadJSON(result.json, result.filename);
      } catch (error) {
        return { ...result, ok: false, error: 'export_download_failed' };
      }
    }

    return result;
  }

  const exportApi = Object.freeze({
    scopes: EXPORT_SCOPES,
    formatExportDate,
    formatExportFilename,
    buildExportBundle,
    attachIntegrity,
    exportUserData,
    get lastExportedBundle() {
      return lastExportedBundle;
    },
    get lastExportSummary() {
      return lastExportSummary;
    }
  });

  App.data = App.data || {};
  App.data.exporter = App.core.registerModule(MODULE_NAME, exportApi, {
    virtualPath: 'src/data/data-export.js',
    version: '16.0',
    status: 'canonical-exporter'
  });
})(window);
