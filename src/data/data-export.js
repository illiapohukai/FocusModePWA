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

  function formatExportFilename(date = new Date()) {
    return `focus-mode-backup-${formatExportDate(date)}.json`;
  }

  function sanitizeExportBundle(rawBundle) {
    const clone = JSON.parse(JSON.stringify(rawBundle));
    // Never include raw localStorage snapshot or any secret/credential keys in external export file
    delete clone.rawSnapshot;
    if (clone.data?.preferences) {
      delete clone.data.preferences.apiKey;
      delete clone.data.preferences.token;
      delete clone.data.preferences.secret;
    }
    return clone;
  }

  function buildExportBundle() {
    const now = new Date();
    const rawBundle = runner.collectCurrentDataBundle('user_export');
    const exportBundle = sanitizeExportBundle(rawBundle);
    exportBundle.exportedAt = now.toISOString();
    exportBundle.exportVersion = schema.exportVersion;
    exportBundle.applicationVersion = schema.applicationVersion;
    exportBundle.schemaVersions = { ...schema.versions };

    const validation = validator.validateBundle(exportBundle, { requireSections: true });

    const filename = formatExportFilename(now);
    const summary = {
      ok: validation.valid,
      filename,
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

    if (validation.valid) {
      lastExportedBundle = exportBundle;
      lastExportSummary = summary;
    }

    return summary;
  }

  function exportUserData(options = {}) {
    const result = buildExportBundle();
    if (!result.ok) {
      return {
        ...result,
        error: result.validation.blockingErrors[0]?.code || 'export_validation_failed'
      };
    }

    if (options.download !== false && global.document && typeof global.Blob === 'function') {
      try {
        const jsonString = JSON.stringify(result.bundle, null, 2);
        const blob = new global.Blob([jsonString], { type: 'application/json;charset=utf-8' });
        const url = global.URL.createObjectURL(blob);
        const link = global.document.createElement('a');
        link.href = url;
        link.download = result.filename;
        link.style.display = 'none';
        global.document.body.appendChild(link);
        link.click();
        setTimeout(() => {
          link.remove();
          global.URL.revokeObjectURL(url);
        }, 150);
      } catch (error) {
        return {
          ...result,
          ok: false,
          error: error?.message || 'export_download_failed'
        };
      }
    }

    return result;
  }

  const exportApi = Object.freeze({
    formatExportDate,
    formatExportFilename,
    buildExportBundle,
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
