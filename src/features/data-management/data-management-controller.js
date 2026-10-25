/**
 * Physical module: src/features/data-management/data-management-controller.js
 * Version: v16 Data Stability
 *
 * UI Controller for the "Data & Backup" (Daten & Backup) screen.
 * Provides Data Summary, Export Data, Import Data with preview and explicit
 * confirmation, Last Backup inspection + Restore Last Backup, and Safe Reset.
 */
(function initializeDataManagementControllerV16(global) {
  'use strict';

  const App = global.FocusModeApp;
  const storage = App?.services?.storage;
  const schema = App?.data?.schema;
  const validator = App?.data?.validator;
  const runner = App?.data?.migrationRunner;
  const exporter = App?.data?.exporter;
  const importer = App?.data?.importer;
  const resetService = App?.data?.reset;

  if (!App?.core?.registerModule || !storage || !schema || !validator || !runner || !exporter || !importer || !resetService) {
    throw new Error('Data Stability modules are required before Data Management Controller.');
  }

  const MODULE_NAME = 'features.dataManagement';
  if (App.core.hasModule(MODULE_NAME)) {
    return;
  }

  function escapeHTML(value) {
    return String(value ?? '').replace(/[&<>'"]/g, ch => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[ch]));
  }

  function currentLang() {
    const lang = storage.getText(schema.keys.language, 'de') || global.document?.documentElement?.lang || 'de';
    return lang === 'en' ? 'en' : 'de';
  }

  function t(deText, enText) {
    return currentLang() === 'en' ? enText : deText;
  }

  function formatTimestamp(isoOrMs) {
    if (!isoOrMs) return t('Kein Backup', 'No backup');
    const date = new Date(isoOrMs);
    if (Number.isNaN(date.getTime())) return String(isoOrMs);
    const locale = currentLang() === 'en' ? 'en-GB' : 'de-DE';
    return date.toLocaleString(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function formatBackupReason(reason) {
    const map = {
      pre_migration: t('Vor Migration', 'Before migration'),
      post_legacy_migration: t('Nach Migration', 'After migration'),
      pre_import: t('Vor Import', 'Before import'),
      pre_reset: t('Vor Zurücksetzen', 'Before reset'),
      user_export: t('Manueller Export', 'Manual export'),
      manual_backup: t('Manuelles Backup', 'Manual backup'),
      auto_backup: t('Automatisches Backup', 'Automatic backup')
    };
    return map[reason] || String(reason || t('Automatisches Backup', 'Automatic backup'));
  }

  function getSummaryData() {
    const missionsRepo = App.repositories?.missions || global.missionRepository;
    const sessionsRepo = App.repositories?.sessions;

    const missions = missionsRepo?.getAll ? missionsRepo.getAll() : [];
    const activeMissions = missions.filter(m => m?.status === 'active');
    const sessions = sessionsRepo?.listHistory ? sessionsRepo.listHistory() : [];
    const activeSession = sessionsRepo?.loadActive ? sessionsRepo.loadActive() : null;
    const storageOk = storage.isAvailable();
    const lastBackup = resetService.inspectLastBackup();

    return {
      missionCount: missions.length,
      activeMissionCount: activeMissions.length,
      sessionCount: sessions.length,
      hasActiveSession: Boolean(activeSession),
      activeSessionTitle: activeSession?.title || null,
      storageAvailable: storageOk,
      schemaVersion: schema.versions.bundle,
      applicationVersion: schema.applicationVersion,
      lastBackup
    };
  }

  function setFeedbackBanner(type, title, detail = '') {
    const banner = global.document?.getElementById('dataFeedbackBanner');
    if (!banner) return;
    banner.className = 'data-feedback-banner dm-feedback-banner dm-feedback-' + type;
    banner.innerHTML =
      '<div class="dm-feedback-title">' + escapeHTML(title) + '</div>' +
      (detail ? '<div class="dm-feedback-detail">' + escapeHTML(detail) + '</div>' : '');
    banner.classList.remove('hidden');
  }

  function clearFeedbackBanner() {
    const banner = global.document?.getElementById('dataFeedbackBanner');
    if (!banner) return;
    banner.classList.add('hidden');
    banner.innerHTML = '';
  }

  function refreshDataManagementUI() {
    const screen = global.document?.getElementById('app-screen-data');
    if (!screen) return;

    const summary = getSummaryData();

    const activeMissionsEl = global.document.getElementById('dataActiveMissionsCount');
    const savedSessionsEl = global.document.getElementById('dataSavedSessionsCount');
    const activeSessionEl = global.document.getElementById('dataActiveSessionStatus');
    const healthBadgeEl = global.document.getElementById('dataMigrationHealthBadge');
    const lastBackupEl = global.document.getElementById('dataLastBackupTime');
    const restoreBtn = global.document.getElementById('restoreLastBackupBtn');
    const versionBadge = global.document.getElementById('dataAppVersionBadge');

    if (versionBadge) {
      versionBadge.textContent = schema.applicationVersion;
    }

    if (activeMissionsEl) {
      activeMissionsEl.textContent = `${summary.activeMissionCount} / 2 (${summary.missionCount})`;
    }
    if (savedSessionsEl) {
      savedSessionsEl.textContent = String(summary.sessionCount);
    }
    if (activeSessionEl) {
      activeSessionEl.textContent = summary.hasActiveSession
        ? t('Aktiv', 'Active')
        : t('Keine', 'None');
    }
    if (healthBadgeEl) {
      healthBadgeEl.textContent = summary.storageAvailable
        ? t(`Stabil (v${summary.schemaVersion})`, `Stable (v${summary.schemaVersion})`)
        : t('Eingeschränkt', 'Restricted');
    }

    if (summary.lastBackup.available) {
      if (lastBackupEl) {
        lastBackupEl.textContent =
          formatTimestamp(summary.lastBackup.exportedAt || summary.lastBackup.timestamp) +
          ' · ' + formatBackupReason(summary.lastBackup.reason);
      }
      if (restoreBtn) {
        restoreBtn.disabled = false;
        restoreBtn.classList.remove('opacity-40', 'cursor-not-allowed');
      }
    } else {
      if (lastBackupEl) {
        lastBackupEl.textContent = t('Kein Backup', 'No backup');
      }
      if (restoreBtn) {
        restoreBtn.disabled = true;
        restoreBtn.classList.add('opacity-40', 'cursor-not-allowed');
      }
    }

    if (global.lucide?.createIcons) {
      global.lucide.createIcons();
    }
  }

  function openDataManagementScreen() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    hideResetConfirmCard();
    refreshDataManagementUI();
    global.setAppScreen('data', { immediate: true });
  }

  function handleDataExportClick() {
    clearFeedbackBanner();
    const res = exporter.exportUserData({ download: true });

    if (!res.ok) {
      setFeedbackBanner(
        'error',
        t('Export fehlgeschlagen', 'Export failed'),
        String(res.error || 'export_validation_failed')
      );
      return res;
    }

    setFeedbackBanner(
      'success',
      t('Daten erfolgreich exportiert', 'Data exported successfully'),
      res.filename + ' · ' + res.missionCount + ' ' + t('Missionen', 'Missions') + ', ' + res.sessionCount + ' ' + t('Sitzungen', 'Sessions')
    );
    refreshDataManagementUI();
    return res;
  }

  function triggerDataImportPicker() {
    clearFeedbackBanner();
    hideResetConfirmCard();
    const input = global.document?.getElementById('dataImportFileInput');
    if (!input) return;
    input.value = '';
    input.click();
  }

  function showImportPreviewCard(preview) {
    const card = global.document?.getElementById('dataImportPreviewCard');
    if (!card) return;
    const warningEl = global.document?.getElementById('dataImportWarningText');
    if (warningEl) {
      warningEl.textContent = t(
        'Dies ersetzt die aktuellen lokalen App-Daten. Ein Backup der aktuellen Daten wird automatisch erstellt.',
        'This will replace local app data. A backup of the current data will be created automatically.'
      );
    }
    const details = global.document?.getElementById('dataImportPreviewDetails');
    if (details) {
      details.innerHTML =
        '<div><span>' + escapeHTML(t('Missionen', 'Missions')) + '</span><strong id="importPreviewMissions">' + escapeHTML(preview.missionCount) + '</strong></div>' +
        '<div><span>' + escapeHTML(t('Sitzungen', 'Sessions')) + '</span><strong id="importPreviewSessions">' + escapeHTML(preview.sessionCount) + '</strong></div>' +
        '<div><span>' + escapeHTML(t('Exportdatum', 'Export date')) + '</span><strong id="importPreviewDate">' + escapeHTML(formatTimestamp(preview.exportedAt)) + '</strong></div>' +
        '<div><span>' + escapeHTML(t('Bundle-Version', 'Bundle version')) + '</span><strong id="importPreviewVersion">v' + escapeHTML(preview.bundleVersion) + ' (Schema v' + escapeHTML(preview.schemaVersion) + ')</strong></div>';
    }
    card.classList.remove('hidden');
  }

  function hideImportPreviewCard() {
    const card = global.document?.getElementById('dataImportPreviewCard');
    if (card) card.classList.add('hidden');
  }

  async function handleDataImportFileChange(event) {
    clearFeedbackBanner();
    const file = event?.target?.files?.[0];
    if (!file) return null;

    const prepared = await importer.prepareImportFromFile(file);
    if (!prepared.ok) {
      hideImportPreviewCard();
      const errorMap = {
        invalid_json: t('Ungültige JSON-Datei. Die aktuellen Daten wurden nicht verändert.', 'Invalid JSON file. Current data was not modified.'),
        missing_required_sections: t('Pflichtbereiche fehlen im Backup. Die aktuellen Daten bleiben unverändert.', 'Required sections are missing in backup. Current data was not modified.'),
        unsupported_future_schema: t('Nicht unterstützte neuere Schema-Version. Import abgelehnt.', 'Unsupported future schema version. Import rejected.'),
        duplicate_session_id: t('Doppelte Sitzungs-IDs im Backup gefunden. Import abgelehnt.', 'Duplicate Session IDs found in backup. Import rejected.'),
        mission_link_required: t('Mission-Sitzung ohne gültige missionId/missionStepId. Import abgelehnt.', 'Mission Session missing missionId/missionStepId. Import rejected.'),
        quick_mission_link_forbidden: t('Quick Focus enthält unzulässige missionId. Import abgelehnt.', 'Quick Focus contains forbidden missionId. Import rejected.')
      };
      setFeedbackBanner(
        'error',
        t('Import abgelehnt', 'Import rejected'),
        errorMap[prepared.code] || (t('Validierungsfehler: ', 'Validation error: ') + (prepared.errors || []).join(', '))
      );
      return prepared;
    }

    showImportPreviewCard(prepared.preview);
    return prepared;
  }

  function confirmPendingDataImport() {
    const res = importer.confirmPreparedImport();
    hideImportPreviewCard();

    if (!res.ok) {
      setFeedbackBanner(
        'error',
        t('Import fehlgeschlagen', 'Import failed'),
        (res.errors || [res.code]).join(', ')
      );
      return res;
    }

    refreshDataManagementUI();
    setFeedbackBanner(
      'success',
      t('Daten erfolgreich importiert', 'Data imported successfully'),
      res.missionCount + ' ' + t('Missionen', 'Missions') + ' · ' + res.sessionCount + ' ' + t('Sitzungen wiederhergestellt', 'Sessions restored')
    );
    return res;
  }

  function cancelPendingDataImport() {
    importer.cancelPreparedImport();
    hideImportPreviewCard();
  }

  function promptSafeDataReset() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    const card = global.document?.getElementById('dataResetConfirmCard');
    if (card) card.classList.remove('hidden');
  }

  function hideResetConfirmCard() {
    const card = global.document?.getElementById('dataResetConfirmCard');
    if (card) card.classList.add('hidden');
  }

  function confirmSafeDataReset() {
    const res = resetService.safeResetLocalData({ keepLanguage: true });
    hideResetConfirmCard();
    refreshDataManagementUI();

    if (res.ok) {
      setFeedbackBanner(
        'success',
        t('Lokale Daten zurückgesetzt', 'Local data reset completed'),
        t('Ein automatisches Backup wurde gespeichert. Das Profil ist jetzt leer.', 'An automatic backup was saved. The profile is now clean.')
      );
    }
    return res;
  }

  function handleRestoreLastBackupClick() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    hideResetConfirmCard();

    const res = resetService.restoreLastBackup();
    refreshDataManagementUI();

    if (!res.ok) {
      setFeedbackBanner(
        'error',
        t('Wiederherstellung fehlgeschlagen', 'Restore failed'),
        t('Kein gültiges Backup zum Wiederherstellen gefunden.', 'No valid backup found to restore.')
      );
      return res;
    }

    setFeedbackBanner(
      'success',
      t('Letztes Backup wiederhergestellt', 'Last backup restored'),
      res.missionCount + ' ' + t('Missionen', 'Missions') + ' · ' + res.sessionCount + ' ' + t('Sitzungen aktiv', 'Sessions active')
    );
    return res;
  }

  // Hook into applyAppScreenImmediately so opening 'data' or 'settings' refreshes summary counts
  const prevApplyScreen = global.applyAppScreenImmediately;
  if (typeof prevApplyScreen === 'function') {
    global.applyAppScreenImmediately = function applyAppScreenWithDataManagementV16(screenName) {
      const result = prevApplyScreen.apply(this, arguments);
      if (screenName === 'data' || screenName === 'settings') {
        refreshDataManagementUI();
      }
      return result;
    };
  }

  // Expose handlers matching index.html and programmatic callers
  global.openDataManagementScreen = openDataManagementScreen;
  global.refreshDataManagementUI = refreshDataManagementUI;
  global.handleDataExportClick = handleDataExportClick;
  global.handleExportDataClick = handleDataExportClick;
  global.triggerDataImportPicker = triggerDataImportPicker;
  global.triggerImportFilePicker = triggerDataImportPicker;
  global.handleDataImportFileChange = handleDataImportFileChange;
  global.handleImportFileSelected = handleDataImportFileChange;
  global.confirmPendingDataImport = confirmPendingDataImport;
  global.confirmImportDataAction = confirmPendingDataImport;
  global.cancelPendingDataImport = cancelPendingDataImport;
  global.cancelImportDataAction = cancelPendingDataImport;
  global.promptSafeDataReset = promptSafeDataReset;
  global.showResetConfirmCard = promptSafeDataReset;
  global.cancelSafeDataReset = hideResetConfirmCard;
  global.hideResetConfirmCard = hideResetConfirmCard;
  global.confirmSafeDataReset = confirmSafeDataReset;
  global.confirmResetLocalDataAction = confirmSafeDataReset;
  global.handleRestoreLastBackupClick = handleRestoreLastBackupClick;
  global.handleRestoreLastBackupAction = handleRestoreLastBackupClick;

  global.document?.addEventListener('DOMContentLoaded', () => {
    refreshDataManagementUI();
  }, { once: true });

  const controllerApi = Object.freeze({
    open: openDataManagementScreen,
    refreshUI: refreshDataManagementUI,
    getSummaryData,
    exportData: handleDataExportClick,
    triggerImport: triggerDataImportPicker,
    confirmImport: confirmPendingDataImport,
    cancelImport: cancelPendingDataImport,
    requestReset: promptSafeDataReset,
    confirmReset: confirmSafeDataReset,
    cancelReset: hideResetConfirmCard,
    restoreLastBackup: handleRestoreLastBackupClick
  });

  App.features = App.features || {};
  App.features.dataManagement = App.core.registerModule(MODULE_NAME, controllerApi, {
    virtualPath: 'src/features/data-management/data-management-controller.js',
    version: '16.0',
    status: 'active-controller'
  });
})(window);
