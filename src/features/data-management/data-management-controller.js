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

  /* v17.10 Settings static copy (translated by the shared text-node i18n). */
  if (typeof I18N === 'object' && I18N.en) {
    Object.assign(I18N.en, {
      'Konfiguration & Profil': 'Configuration & Profile',
      'Dein Fokus-System': 'Your focus system',
      'Lokal gespeichert': 'Stored locally',
      'Sitzungen gespeichert': 'Sessions saved',
      'Apps im Fokus-Schutz': 'Apps in focus protection',
      'Fokus & Missionen': 'Focus & Missions',
      'Aktive, abgeschlossene und archivierte Missionen': 'Active, completed and archived missions',
      'Fokus-Schutz & Apps': 'Focus protection & apps',
      'Apps während Sitzungen blockiert': 'apps blocked during sessions',
      'Bewusste Barriere bei App-Wechsel': 'A conscious barrier when switching apps',
      'Darstellung & Sprache': 'Appearance & Language',
      'Konto & Coaching': 'Account & Coaching',
      'Langfristige Fokus-Muster & Empfehlungen': 'Long-term focus patterns & recommendations',
      'Über die App': 'About the app',
      'Offline-fähig · Lokale Speicherung': 'Works offline · Local storage',
      /* v17.17 Security & Privacy copy */
      'Vollständiges JSON-Backup herunterladen': 'Download full JSON backup',
      'Nur Missionen & Einstellungen': 'Missions & settings only',
      'Backup ohne Sitzungsverlauf': 'Backup without Session History',
      'Alle lokalen Daten löschen': 'Delete all local data',
      'Inklusive automatischer Backups und Einstellungen': 'Including automatic backups and settings',
      'Alle lokalen Daten endgültig löschen?': 'Permanently delete all local data?',
      'Missionen, Sitzungsverlauf, Einstellungen und alle automatischen Backups werden von diesem Gerät entfernt. Das kann nicht rückgängig gemacht werden. Exportiere vorher ein Backup, wenn du die Daten behalten möchtest.': 'Missions, Session History, settings and all automatic backups are removed from this device. This cannot be undone. Export a backup first if you want to keep the data.',
      'Alles löschen': 'Delete everything',
      'Backups enthalten deine Missionen, Aufgaben und Notizen als lesbaren Text. Teile Backup-Dateien nur mit Personen, denen du vertraust. Ein Backup ist ein lokaler Datenexport – kein Nachweis für Fortschritt oder Serien.': 'Backups contain your Missions, tasks and notes as readable text. Only share backup files with people you trust. A backup is a local data export – not proof of progress or streaks.',
      'Lokales Backup: Verlauf, Serie und Fortschritt stammen aus einer Datei und sind keine verifizierten Nachweise.': 'Local backup: History, streak and progress come from a file and are not verified records.',
      'Was wird an die AI gesendet?': 'What is sent to the AI?',
      'Für den Plan werden nur dein Ziel-Text, ein ausgewähltes Foto sowie Mission-Typ, Sicherheit und der gewählte Zeitplan an den AI-Dienst gesendet (unser Server bei Cloudflare, Modell: Google Gemini). Fotos werden vorher verkleinert und ohne Metadaten (z. B. Standort) übertragen.': 'Only your goal text, a selected photo, Mission type, confidence and the selected schedule are sent to the AI service to create the plan (our server at Cloudflare, model: Google Gemini). Photos are resized first and sent without metadata (e.g. location).',
      'Missionen, Sitzungsverlauf, Serie und Einstellungen bleiben auf diesem Gerät.': 'Missions, Session History, streak and settings stay on this device.'
    });
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
      versionBadge.textContent = App.version || schema.applicationVersion;
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

    syncSettingsSummary(summary);

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

  /* v17.10: the Settings summary and backup line show the same real local summary as the Data screen. */
  function syncSettingsSummary(summary) {
    const doc = global.document;
    const set = (id, value) => { const el = doc.getElementById(id); if (el && el.textContent !== value) el.textContent = value; };
    set('settingsActiveMissions', summary.activeMissionCount + ' / 2');
    set('settingsSavedSessions', String(summary.sessionCount));
    set('settingsStorageState', summary.storageAvailable ? t('Lokal gespeichert', 'Stored locally') : t('Speicher eingeschränkt', 'Storage restricted'));
    const card = doc.querySelector('#app-screen-settings .st-summary');
    if (card) card.dataset.storage = summary.storageAvailable ? 'ok' : 'limited';
    set('settingsLastBackup', summary.lastBackup.available
      ? formatTimestamp(summary.lastBackup.exportedAt || summary.lastBackup.timestamp)
      : t('Kein Backup', 'No backup'));
  }

  function openDataManagementScreen() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    hideResetConfirmCard();
    hideDeleteAllConfirmCard();
    refreshDataManagementUI();
    if (typeof global.navigateForward === 'function') {
      global.navigateForward('data', { parent: 'settings', rootSection: 'settings', options: { immediate: true } });
    } else {
      global.setAppScreen('data', { immediate: true });
    }
  }

  /* v17.17: one German message per failure code. Internal codes, stack traces and file paths are
     never shown; unknown codes fall back to a generic message that states data is unchanged. */
  const IMPORT_ERROR_MESSAGES = {
    file_missing: ['Keine Datei ausgewählt.', 'No file selected.'],
    empty_file: ['Die Datei ist leer. Die aktuellen Daten wurden nicht verändert.', 'The file is empty. Current data was not modified.'],
    file_too_large: ['Die Datei ist zu groß (max. 5 MB). Die aktuellen Daten wurden nicht verändert.', 'The file is too large (max. 5 MB). Current data was not modified.'],
    file_read_failed: ['Die Datei konnte nicht gelesen werden.', 'The file could not be read.'],
    invalid_json: ['Datei beschädigt: kein gültiges JSON. Die aktuellen Daten wurden nicht verändert.', 'File damaged: not valid JSON. Current data was not modified.'],
    invalid_bundle_format: ['Format nicht unterstützt. Bitte wähle ein Focus Mode+ Backup.', 'Format not supported. Please choose a Focus Mode+ backup.'],
    unsupported_format: ['Format nicht unterstützt. Bitte wähle ein Focus Mode+ Backup.', 'Format not supported. Please choose a Focus Mode+ backup.'],
    missing_required_sections: ['Pflichtbereiche fehlen im Backup. Die aktuellen Daten bleiben unverändert.', 'Required sections are missing in backup. Current data was not modified.'],
    unsupported_future_schema: ['Dieses Backup stammt aus einer neueren App-Version und kann hier nicht importiert werden.', 'This backup comes from a newer app version and cannot be imported here.'],
    integrity_mismatch: ['Datei beschädigt: Die Prüfsumme stimmt nicht. Die Datei wurde nach dem Export verändert oder unvollständig kopiert.', 'File damaged: the checksum does not match. The file was changed after export or copied incompletely.'],
    integrity_invalid: ['Datei beschädigt: Die Prüfsumme ist ungültig.', 'File damaged: the checksum is invalid.'],
    import_dangerous_key: ['Die Datei enthält unzulässige Feldnamen und wurde abgelehnt.', 'The file contains forbidden field names and was rejected.'],
    import_reserved_server_field: ['Die Datei enthält Felder, die nur ein Server ausstellen darf (z. B. verifizierte Werte). Import abgelehnt.', 'The file contains fields only a server may issue (e.g. verified values). Import rejected.'],
    duplicate_session_id: ['Doppelte Sitzungs-IDs im Backup gefunden. Import abgelehnt.', 'Duplicate Session IDs found in backup. Import rejected.'],
    duplicate_mission_id: ['Doppelte Missions-IDs im Backup gefunden. Import abgelehnt.', 'Duplicate Mission IDs found in backup. Import rejected.'],
    mission_link_required: ['Mission-Sitzung ohne gültige Mission. Import abgelehnt.', 'Mission Session without a valid Mission. Import rejected.'],
    quick_mission_link_forbidden: ['Kurzer Fokus enthält eine unzulässige Missions-Verknüpfung. Import abgelehnt.', 'Quick Focus contains a forbidden Mission link. Import rejected.'],
    backup_failed: ['Vor dem Import konnte keine Sicherung der aktuellen Daten gespeichert werden (Speicher voll?). Es wurde nichts verändert. Exportiere zuerst deine Daten.', 'A backup of the current data could not be saved before import (storage full?). Nothing was changed. Export your data first.'],
    storage_write_failed: ['Der Import konnte nicht vollständig gespeichert werden. Der vorherige Stand wurde wiederhergestellt.', 'The import could not be stored completely. The previous state was restored.'],
    migration_failed_and_restored: ['Der Import ist fehlgeschlagen. Der vorherige Stand wurde wiederhergestellt.', 'The import failed. The previous state was restored.'],
    rollback_failed: ['Der Import ist fehlgeschlagen und der vorherige Stand konnte nicht vollständig wiederhergestellt werden. Nutze „Letztes Backup wiederherstellen“.', 'The import failed and the previous state could not be fully restored. Use “Restore last backup”.'],
    no_prepared_import: ['Keine Import-Vorschau aktiv. Bitte wähle die Datei erneut.', 'No import preview active. Please choose the file again.'],
    import_superseded: ['', '']
  };

  function describeImportError(code) {
    const entry = IMPORT_ERROR_MESSAGES[code];
    if (entry) return t(entry[0], entry[1]);
    if (/^import_|_invalid$|_missing$|_mismatch$|_required$|_exceeded$|_empty$|_not_array$|_not_object$/.test(String(code || ''))) {
      return t('Die Datei enthält ungültige oder zu umfangreiche Daten. Die aktuellen Daten wurden nicht verändert.', 'The file contains invalid or excessive data. Current data was not modified.');
    }
    return t('Die Datei konnte nicht importiert werden. Die aktuellen Daten wurden nicht verändert.', 'The file could not be imported. Current data was not modified.');
  }

  let exportInFlight = false;

  async function handleDataExportClick(scope = 'full') {
    if (exportInFlight) return null;
    exportInFlight = true;
    clearFeedbackBanner();
    const buttons = ['exportDataBtn', 'exportMissionsOnlyBtn'].map(id => global.document?.getElementById(id)).filter(Boolean);
    buttons.forEach(b => { b.disabled = true; b.setAttribute('aria-busy', 'true'); });
    let res = null;
    try {
      res = await exporter.exportUserData({ download: true, scope: scope === 'missions' ? 'missions' : 'full' });
    } catch (error) {
      res = { ok: false, error: 'export_failed' };
    } finally {
      exportInFlight = false;
      buttons.forEach(b => { b.disabled = false; b.removeAttribute('aria-busy'); });
    }

    if (!res || !res.ok) {
      setFeedbackBanner(
        'error',
        t('Export fehlgeschlagen', 'Export failed'),
        t('Das Backup konnte nicht erstellt werden. Deine Daten wurden nicht verändert.', 'The backup could not be created. Your data was not modified.')
      );
      return res;
    }

    const integrityNote = res.integrityStatus === 'present'
      ? t('Mit Prüfsumme (SHA-256) gegen Beschädigung.', 'With checksum (SHA-256) against corruption.')
      : t('Prüfsumme in diesem Browser nicht verfügbar.', 'Checksum not available in this browser.');
    const scopeNote = res.scope === 'missions'
      ? t('ohne Sitzungsverlauf', 'without Session History')
      : res.sessionCount + ' ' + t('Sitzungen', 'Sessions');
    setFeedbackBanner(
      'success',
      t('Lokales Backup gespeichert', 'Local backup saved'),
      res.filename + ' · ' + res.missionCount + ' ' + t('Missionen', 'Missions') + ', ' + scopeNote + '. ' + integrityNote
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

  function formatDateOnly(ms) {
    const date = new Date(ms);
    if (Number.isNaN(date.getTime())) return '–';
    return date.toLocaleDateString(currentLang() === 'en' ? 'en-GB' : 'de-DE', { year: 'numeric', month: '2-digit', day: '2-digit' });
  }

  function previewRow(label, value, id = '') {
    return '<div><span>' + escapeHTML(label) + '</span><strong' + (id ? ' id="' + id + '"' : '') + '>' + escapeHTML(value) + '</strong></div>';
  }

  function showImportPreviewCard(preview) {
    const card = global.document?.getElementById('dataImportPreviewCard');
    if (!card) return;
    const keepsHistory = preview.policy === 'replace_missions_keep_history';
    const warningEl = global.document?.getElementById('dataImportWarningText');
    if (warningEl) {
      const current = preview.current || { missionCount: 0, sessionCount: 0, hasActiveSession: false };
      const parts = [
        keepsHistory
          ? t('Dies ersetzt deine aktuellen Missionen (' + current.missionCount + '). Dein Sitzungsverlauf bleibt erhalten.',
              'This replaces your current Missions (' + current.missionCount + '). Your Session History is kept.')
          : t('Dies ersetzt deine aktuellen lokalen Daten (' + current.missionCount + ' Missionen, ' + current.sessionCount + ' Sitzungen).',
              'This replaces your current local data (' + current.missionCount + ' Missions, ' + current.sessionCount + ' Sessions).'),
        t('Vorher wird automatisch ein Backup erstellt.', 'A backup is created automatically first.')
      ];
      if (current.hasActiveSession) {
        parts.push(t('Eine laufende Sitzung wird dabei beendet.', 'A running Session will be ended.'));
      }
      warningEl.textContent = parts.join(' ');
    }
    const integrityLabel = {
      verified: t('Backup geprüft (Datei unverändert)', 'Backup checked (file unchanged)'),
      absent: t('Älteres Backup ohne Prüfsumme', 'Older backup without checksum'),
      not_checked: t('Prüfsumme nicht prüfbar', 'Checksum not checkable')
    }[preview.integrity] || t('Nicht geprüft', 'Not checked');
    const range = preview.dateRange
      ? formatDateOnly(preview.dateRange.from) + ' – ' + formatDateOnly(preview.dateRange.to)
      : '–';
    const sessionsValue = keepsHistory
      ? t('nicht enthalten', 'not included')
      : String(preview.sessionCount) + (preview.sessionsKept < preview.sessionCount
        ? t(' (neueste ' + preview.sessionsKept + ' werden übernommen)', ' (newest ' + preview.sessionsKept + ' kept)')
        : '');
    const details = global.document?.getElementById('dataImportPreviewDetails');
    if (details) {
      details.innerHTML =
        previewRow(t('Format', 'Format'), preview.format === 'focusmode-backup' ? 'Focus Mode+ Backup' : t('Älteres Focus Mode+ Backup', 'Older Focus Mode+ backup')) +
        previewRow(t('Exportdatum', 'Export date'), preview.exportedAt ? formatTimestamp(preview.exportedAt) : '–', 'importPreviewDate') +
        previewRow(t('App-Version', 'App version'), preview.appVersion || preview.applicationVersion || '–', 'importPreviewVersion') +
        previewRow(t('Missionen', 'Missions'), preview.missionCount + ' (' + preview.activeMissionCount + ' ' + t('aktiv', 'active') + ')', 'importPreviewMissions') +
        previewRow(t('Sitzungen', 'Sessions'), sessionsValue, 'importPreviewSessions') +
        previewRow(t('Zeitraum', 'Date range'), range) +
        previewRow(t('Einstellungen', 'Settings'), preview.settingsIncluded ? t('Sprache: ', 'Language: ') + (preview.language === 'en' ? 'English' : 'Deutsch') : t('nicht enthalten', 'not included')) +
        previewRow(t('Gleiche Missionen wie aktuell', 'Same Missions as current'), String(preview.overlappingMissionCount || 0)) +
        previewRow(t('Datei-Prüfung', 'File check'), integrityLabel, 'importPreviewIntegrity');
    }
    const trustEl = global.document?.getElementById('dataImportTrustNote');
    if (trustEl) {
      trustEl.textContent = t(
        'Lokales Backup: Verlauf, Serie und Fortschritt stammen aus einer Datei und sind keine verifizierten Nachweise.',
        'Local backup: History, streak and progress come from a file and are not verified records.'
      );
    }
    card.classList.remove('hidden');
  }

  function hideImportPreviewCard() {
    const card = global.document?.getElementById('dataImportPreviewCard');
    if (card) card.classList.add('hidden');
    if (typeof global.syncBottomNavigationForScreen === 'function') {
      global.syncBottomNavigationForScreen();
    }
  }

  let importCheckInFlight = false;

  async function handleDataImportFileChange(event) {
    clearFeedbackBanner();
    hideImportPreviewCard();
    const file = event?.target?.files?.[0];
    if (!file) return null;
    if (importCheckInFlight) return null;
    importCheckInFlight = true;
    setFeedbackBanner('info', t('Backup wird geprüft …', 'Checking backup …'), '');

    let prepared;
    try {
      prepared = await importer.prepareImportFromFile(file);
    } catch (error) {
      prepared = { ok: false, code: 'file_read_failed' };
    } finally {
      importCheckInFlight = false;
      if (event?.target) event.target.value = '';
    }

    if (!prepared.ok) {
      if (prepared.code === 'import_superseded') return prepared;
      hideImportPreviewCard();
      setFeedbackBanner('error', t('Import abgelehnt', 'Import rejected'), describeImportError(prepared.code));
      return prepared;
    }

    clearFeedbackBanner();
    showImportPreviewCard(prepared.preview);
    return prepared;
  }

  function confirmPendingDataImport() {
    const res = importer.confirmPreparedImport();
    hideImportPreviewCard();

    if (!res.ok) {
      setFeedbackBanner('error', t('Import fehlgeschlagen', 'Import failed'), describeImportError(res.code));
      refreshDataManagementUI();
      return res;
    }

    refreshDataManagementUI();
    const detail = res.policy === 'replace_missions_keep_history'
      ? res.missionCount + ' ' + t('Missionen übernommen · Sitzungsverlauf beibehalten', 'Missions restored · Session History kept')
      : res.missionCount + ' ' + t('Missionen', 'Missions') + ' · ' + res.sessionCount + ' ' + t('Sitzungen wiederhergestellt', 'Sessions restored');
    setFeedbackBanner('success', t('Lokales Backup importiert', 'Local backup imported'), detail);
    return res;
  }

  function cancelPendingDataImport() {
    importer.cancelPreparedImport();
    hideImportPreviewCard();
  }

  function promptSafeDataReset() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    hideDeleteAllConfirmCard();
    const card = global.document?.getElementById('dataResetConfirmCard');
    if (card) card.classList.remove('hidden');
  }

  function hideResetConfirmCard() {
    const card = global.document?.getElementById('dataResetConfirmCard');
    if (card) card.classList.add('hidden');
    if (typeof global.syncBottomNavigationForScreen === 'function') {
      global.syncBottomNavigationForScreen();
    }
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

  function promptDeleteAllLocalData() {
    clearFeedbackBanner();
    hideImportPreviewCard();
    hideResetConfirmCard();
    const card = global.document?.getElementById('dataDeleteAllConfirmCard');
    if (card) card.classList.remove('hidden');
  }

  function hideDeleteAllConfirmCard() {
    const card = global.document?.getElementById('dataDeleteAllConfirmCard');
    if (card) card.classList.add('hidden');
  }

  function confirmDeleteAllLocalData() {
    const res = resetService.deleteAllLocalData();
    hideDeleteAllConfirmCard();
    refreshDataManagementUI();
    if (res.ok) {
      setFeedbackBanner(
        'success',
        t('Alle lokalen Daten gelöscht', 'All local data deleted'),
        t('Missionen, Verlauf, Einstellungen und automatische Backups wurden von diesem Gerät entfernt.', 'Missions, history, settings and automatic backups were removed from this device.')
      );
    } else {
      setFeedbackBanner(
        'error',
        t('Löschen unvollständig', 'Deletion incomplete'),
        t('Einige Einträge konnten nicht entfernt werden. Lösche die Website-Daten zusätzlich in den Browser-Einstellungen.', 'Some entries could not be removed. Also clear the site data in your browser settings.')
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
        ['no_backup_available', 'backup_invalid'].includes(res.code)
          ? t('Kein gültiges Backup zum Wiederherstellen gefunden.', 'No valid backup found to restore.')
          : describeImportError(res.code)
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
  global.promptDeleteAllLocalData = promptDeleteAllLocalData;
  global.cancelDeleteAllLocalData = hideDeleteAllConfirmCard;
  global.confirmDeleteAllLocalData = confirmDeleteAllLocalData;
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
    restoreLastBackup: handleRestoreLastBackupClick,
    requestDeleteAll: promptDeleteAllLocalData,
    confirmDeleteAll: confirmDeleteAllLocalData,
    describeImportError
  });

  App.features = App.features || {};
  App.features.dataManagement = App.core.registerModule(MODULE_NAME, controllerApi, {
    virtualPath: 'src/features/data-management/data-management-controller.js',
    version: '16.0',
    status: 'active-controller'
  });
})(window);
