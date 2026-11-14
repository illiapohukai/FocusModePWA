/**
 * Physical module: src/services/ai-service.js
 * v17.4.1 Mission Generation Repair
 */
const FOCUS_AI_ADAPT_ENDPOINT = 'https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission';
const FOCUS_AI_ENDPOINT = 'https://focusmodedemoai.illia-pohukai.workers.dev/create-mission';

const MISSION_IMAGE_ALLOWED_MIMES = Object.freeze(['image/jpeg', 'image/png', 'image/webp']);
const MISSION_IMAGE_MAX_ORIGINAL_BYTES = 20 * 1024 * 1024; // 20 MB
const MISSION_IMAGE_MAX_PROCESSED_BYTES = 1500000; // ~1.5 MB
const MISSION_IMAGE_OPTIMIZE_THRESHOLD_BYTES = 350000; // ~350 KB
const MISSION_IMAGE_MAX_DIMENSION = 1800;
const MISSION_IMAGE_MAX_ATTACHMENTS = 1;

const MISSION_REQUEST_TIMEOUT_TEXT_MS = 38000;
const MISSION_REQUEST_TIMEOUT_IMAGE_MS = 52000;

let missionImageFile = null;
let missionImagePreviewUrl = null;
let pendingAIPlan = null;
let activeAIPlan = null;
let aiRequestController = null;
let aiRequestCancelledByUser = false;
let isMissionAnalyzeInFlight = false;
let analysisRowTimers = [];

function isEnglishLanguage() {
  try {
    if (typeof document !== 'undefined' && document.documentElement.lang === 'en') return true;
    const saved = window.FocusModeApp?.services?.storage?.getText?.('focus-mode-language') ||
      localStorage.getItem('focus-mode-language') ||
      localStorage.getItem('focus_lang');
    return saved === 'en';
  } catch (_) {
    return false;
  }
}

function clearAnalysisRowTimers() {
  while (analysisRowTimers.length) {
    clearTimeout(analysisRowTimers.pop());
  }
}

function isOfflineOrNetworkFailure(error) {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return true;
  if (!error) return false;
  if (error.name === 'AbortError') return false;
  const msg = String(error?.message || error || '');
  return error instanceof TypeError || /failed to fetch|networkerror|network request failed|load failed|offline|internet/i.test(msg);
}

function getMissionImageExtensionMime(filename) {
  const name = String(filename || '').toLowerCase();
  if (/\.(jpe?g)$/.test(name)) return 'image/jpeg';
  if (/\.png$/.test(name)) return 'image/png';
  if (/\.webp$/.test(name)) return 'image/webp';
  return '';
}

function validateMissionImageInput(file) {
  const en = isEnglishLanguage();
  if (!file || typeof file !== 'object') {
    return {
      ok: false,
      code: 'INVALID_REQUEST',
      message: en ? 'No image file selected.' : 'Keine Bilddatei ausgewählt.'
    };
  }
  const rawMime = String(file.type || '').toLowerCase().trim();
  let normalizedMime = rawMime === 'image/jpg' ? 'image/jpeg' : rawMime;
  if (!normalizedMime) {
    normalizedMime = getMissionImageExtensionMime(file.name);
  }
  if (!MISSION_IMAGE_ALLOWED_MIMES.includes(normalizedMime)) {
    return {
      ok: false,
      code: 'UNSUPPORTED_FILE_TYPE',
      message: en
        ? 'Unsupported file format. Please choose a JPEG, PNG, or WEBP image.'
        : 'Nicht unterstütztes Dateiformat. Bitte wähle ein JPEG-, PNG- oder WEBP-Bild aus.'
    };
  }
  const size = Number(file.size) || 0;
  if (size <= 0) {
    return {
      ok: false,
      code: 'IMAGE_PROCESSING_FAILED',
      message: en
        ? 'The selected image file is empty. Please choose another photo.'
        : 'Die ausgewählte Bilddatei ist leer. Bitte wähle ein anderes Foto aus.'
    };
  }
  if (size > MISSION_IMAGE_MAX_ORIGINAL_BYTES) {
    return {
      ok: false,
      code: 'FILE_TOO_LARGE',
      message: en
        ? 'The image is too large (max. 20 MB). Please choose a smaller photo.'
        : 'Das Bild ist zu groß (maximal 20 MB). Bitte wähle ein kleineres Foto aus.'
    };
  }
  return { ok: true, mime: normalizedMime };
}

function openRealFilePicker(cameraPreferred) {
  const input = document.getElementById('missionFileInput');
  if (!input) return;
  if (cameraPreferred) input.setAttribute('capture', 'environment');
  else input.removeAttribute('capture');
  input.value = '';
  input.click();
}

function clearMissionImage(ev) {
  if (ev && typeof ev.stopPropagation === 'function') ev.stopPropagation();
  if (ev && typeof ev.preventDefault === 'function') ev.preventDefault();
  missionImageFile = null;
  if (missionImagePreviewUrl) {
    try { URL.revokeObjectURL(missionImagePreviewUrl); } catch (_) {}
    missionImagePreviewUrl = null;
  }
  const input = document.getElementById('missionFileInput');
  if (input) input.value = '';
  const card = document.getElementById('selectedFileCard');
  if (card) {
    card.classList.add('hidden');
    card.classList.remove('selected');
  }
  const previewBox = document.getElementById('selectedFilePreviewBox') || card?.querySelector('.w-11.h-11');
  if (previewBox) {
    previewBox.innerHTML = '<i class="w-5 h-5" data-lucide="image"></i>';
  }
  const hint = document.getElementById('goalInputHint');
  const promptEl = document.getElementById('goalPrompt');
  if (hint) {
    const hasText = Boolean(promptEl?.value?.trim());
    hint.textContent = hasText ? 'Text bereit' : 'Text oder Foto hinzufügen';
  }
  if (typeof window.lucide?.createIcons === 'function') {
    window.lucide.createIcons();
  }
  if (typeof window.updateMissionPlanButton === 'function') {
    window.updateMissionPlanButton();
  }
}

async function handleMissionFile(event) {
  clearMissionError();
  const files = event?.target?.files;
  const original = files && files[0];
  if (!original) return;
  if (files.length > MISSION_IMAGE_MAX_ATTACHMENTS) {
    showMissionError('Es kann maximal 1 Bild pro Mission angehängt werden.', { retryable: false });
    return;
  }

  const check = validateMissionImageInput(original);
  if (!check.ok) {
    showMissionError(check.message, { retryable: false });
    return;
  }

  try {
    const optimized = await compressMissionImage(original, check.mime);
    if (!optimized || !(optimized instanceof Blob) || optimized.size <= 0) {
      throw new Error('IMAGE_PROCESSING_FAILED');
    }
    if (optimized.size > MISSION_IMAGE_MAX_PROCESSED_BYTES) {
      showMissionError('Das Bild ist auch nach der Komprimierung zu groß (max. 1,5 MB). Bitte wähle einen engeren Ausschnitt.', { retryable: false });
      return;
    }

    missionImageFile = optimized;
    if (missionImagePreviewUrl) {
      try { URL.revokeObjectURL(missionImagePreviewUrl); } catch (_) {}
    }
    missionImagePreviewUrl = URL.createObjectURL(missionImageFile);

    const card = document.getElementById('selectedFileCard');
    const nameEl = document.getElementById('selectedFileName');
    const metaEl = document.getElementById('selectedFileMeta');
    const iconBox = document.getElementById('selectedFilePreviewBox') || card?.querySelector('.w-11.h-11');
    const hint = document.getElementById('goalInputHint');

    if (card) {
      card.classList.remove('hidden');
      card.classList.add('selected');
    }
    if (nameEl) nameEl.textContent = original.name || missionImageFile.name || 'Foto.jpg';
    if (metaEl) metaEl.textContent = formatBytes(missionImageFile.size) + ' · bereit für AI-Analyse';
    if (iconBox) {
      iconBox.innerHTML = '';
      const img = document.createElement('img');
      img.src = missionImagePreviewUrl;
      img.alt = 'Ausgewähltes Lernmaterial';
      iconBox.appendChild(img);
    }
    if (hint) hint.textContent = isEnglishLanguage() ? 'Photo attached' : 'Foto hinzugefügt';
    if (typeof window.lucide?.createIcons === 'function') {
      window.lucide.createIcons();
    }
    if (typeof window.updateMissionPlanButton === 'function') {
      window.updateMissionPlanButton();
    }
  } catch (err) {
    console.error('Mission image preparation failed', err?.message || err);
    showMissionError('Das Foto konnte nicht vorbereitet werden. Bitte versuche ein anderes Bild.', { retryable: false });
  }
}

async function loadDrawableImage(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
      return {
        source: bitmap,
        width: bitmap.width,
        height: bitmap.height,
        cleanup() { bitmap.close?.(); }
      };
    } catch (_) {
      // Fall back to HTMLImageElement below
    }
  }
  return await new Promise((resolve, reject) => {
    const tempUrl = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      resolve({
        source: img,
        width: img.naturalWidth || img.width,
        height: img.naturalHeight || img.height,
        cleanup() { try { URL.revokeObjectURL(tempUrl); } catch (_) {} }
      });
    };
    img.onerror = () => {
      try { URL.revokeObjectURL(tempUrl); } catch (_) {}
      reject(new Error('IMAGE_PROCESSING_FAILED'));
    };
    img.src = tempUrl;
  });
}

async function compressMissionImage(file, explicitMime) {
  const mime = explicitMime || (file.type === 'image/jpg' ? 'image/jpeg' : file.type) || getMissionImageExtensionMime(file.name) || 'image/jpeg';
  const drawable = await loadDrawableImage(file);
  try {
    const srcW = drawable.width;
    const srcH = drawable.height;
    if (!srcW || !srcH) throw new Error('IMAGE_PROCESSING_FAILED');

    const longSide = Math.max(srcW, srcH);
    const needsResize = longSide > MISSION_IMAGE_MAX_DIMENSION;
    const needsCompress = file.size > MISSION_IMAGE_OPTIMIZE_THRESHOLD_BYTES;

    if (!needsResize && !needsCompress) {
      if (file instanceof File && file.type === mime) return file;
      return new File([file], file.name || 'material.jpg', { type: mime, lastModified: Date.now() });
    }

    const attempts = [
      { maxSide: MISSION_IMAGE_MAX_DIMENSION, quality: 0.84 },
      { maxSide: 1600, quality: 0.76 }
    ];

    let bestBlob = null;
    for (const cfg of attempts) {
      const scale = Math.min(1, cfg.maxSide / longSide);
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(srcW * scale));
      canvas.height = Math.max(1, Math.round(srcH * scale));
      const ctx = canvas.getContext('2d', { alpha: false });
      if (!ctx) throw new Error('IMAGE_PROCESSING_FAILED');
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(drawable.source, 0, 0, canvas.width, canvas.height);

      const blob = await new Promise((resolve, reject) => {
        canvas.toBlob(b => (b && b.size > 0) ? resolve(b) : reject(new Error('compression failed')), 'image/jpeg', cfg.quality);
      });
      bestBlob = blob;
      if (blob.size <= MISSION_IMAGE_MAX_PROCESSED_BYTES) break;
    }

    if (!bestBlob || bestBlob.size <= 0) throw new Error('IMAGE_PROCESSING_FAILED');
    const baseName = (file.name || 'material').replace(/\.[^.]+$/, '') || 'material';
    return new File([bestBlob], baseName + '.jpg', { type: 'image/jpeg', lastModified: Date.now() });
  } finally {
    drawable.cleanup?.();
  }
}

function formatBytes(bytes) {
  return bytes < 1048576
    ? Math.max(1, Math.round(bytes / 1024)) + ' KB'
    : (bytes / 1048576).toFixed(1).replace('.', ',') + ' MB';
}

function showMissionError(message, options = {}) {
  const box = document.getElementById('missionError');
  if (!box) return;
  delete box.dataset.offlineState;
  const en = isEnglishLanguage();
  const retryable = options.retryable !== false;
  const safeMsg = String(message || (en ? 'AI analysis failed.' : 'AI-Analyse fehlgeschlagen.'));

  if (!retryable) {
    box.innerHTML =
      '<div class="mg-error-card">' +
        '<div class="mg-error-head"><span>' + escapeHTML(safeMsg) + '</span></div>' +
      '</div>';
  } else {
    const retryLabel = en ? 'Try Again' : 'Erneut versuchen';
    const dismissLabel = en ? 'Dismiss' : 'Schließen';
    box.innerHTML =
      '<div class="mg-error-card">' +
        '<div class="mg-error-head"><span>' + escapeHTML(safeMsg) + '</span></div>' +
        '<div class="mg-error-actions">' +
          '<button type="button" class="mg-error-btn primary" id="missionErrorRetryBtn" onclick="analyzeGoal()">' + escapeHTML(retryLabel) + '</button>' +
          '<button type="button" class="mg-error-btn secondary" onclick="clearMissionError()">' + escapeHTML(dismissLabel) + '</button>' +
        '</div>' +
      '</div>';
  }
  box.classList.remove('hidden');
  setAppScreen('goal', { immediate: true });
  try { box.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }); } catch (_) {}
}

function showMissionOfflineError() {
  const box = document.getElementById('missionError');
  if (!box) return;
  const en = isEnglishLanguage();
  box.dataset.offlineState = '1';
  const title = en ? 'No internet connection' : 'Keine Internetverbindung';
  const line1 = en ? 'Your AI Mission can be created when you are back online.' : 'Die AI-Mission kann erstellt werden, sobald du wieder online bist.';
  const line2 = en ? 'Your input has been preserved.' : 'Deine Eingaben bleiben erhalten.';
  const retryLabel = en ? 'Try Again' : 'Erneut versuchen';
  const backLabel = en ? 'Back' : 'Zurück';
  box.innerHTML =
    '<div class="mg-offline-state">' +
      '<div class="mg-offline-title">' + escapeHTML(title) + '</div>' +
      '<div class="mg-offline-copy"><span>' + escapeHTML(line1) + '</span><br><span>' + escapeHTML(line2) + '</span></div>' +
      '<div class="mg-offline-actions">' +
        '<button type="button" class="mg-offline-btn primary" id="missionOfflineRetryBtn" onclick="analyzeGoal()">' + escapeHTML(retryLabel) + '</button>' +
        '<button type="button" class="mg-offline-btn secondary" id="missionOfflineBackBtn" onclick="clearMissionError();navigateBack()">' + escapeHTML(backLabel) + '</button>' +
      '</div>' +
    '</div>';
  box.classList.remove('hidden');
  setAppScreen('goal', { immediate: true });
  try { box.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }); } catch (_) {}
}

function clearMissionError() {
  const box = document.getElementById('missionError');
  if (box) {
    delete box.dataset.offlineState;
    box.textContent = '';
    box.classList.add('hidden');
  }
}
const hideMissionError = clearMissionError;

function missionTypeForAPI() {
  const map = {
    'Prüfung': 'exam',
    'Aufgabe': 'assignment',
    'Projekt': 'project',
    'Eigene Mission': 'custom',
    'Exam': 'exam',
    'Task': 'assignment',
    'Project': 'project',
    'Custom mission': 'custom'
  };
  return map[selectedMissionTypeValue] || 'custom';
}

function confidenceForAPI() {
  const v = (selectedMissionConfidenceValue || '').toLowerCase();
  if (v.includes('unsicher') || v.includes('uncertain')) return 'uncertain';
  if (v === 'sicher' || v.includes('confident')) return 'confident';
  return 'partial';
}

function resetAnalysisRows() {
  document.querySelectorAll('#analysisRows .analysis-row').forEach(row => {
    row.classList.add('opacity-40');
    row.classList.remove('done');
    const icon = row.querySelector('.analysis-icon');
    if (icon) icon.innerHTML = '<span class="flow-loader-dot w-1.5 h-1.5 bg-indigo-300 rounded-full"></span>';
  });
}

function completeAnalysisRow(index) {
  const row = document.querySelectorAll('#analysisRows .analysis-row')[index];
  if (!row) return;
  row.classList.remove('opacity-40');
  row.classList.add('done');
  const icon = row.querySelector('.analysis-icon');
  if (icon) icon.innerHTML = '<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i>';
  if (typeof lucide?.createIcons === 'function') lucide.createIcons();
}

function getLocalTodayISO() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return y + '-' + m + '-' + d;
}

function isValidISODateString(value) {
  if (typeof value !== 'string') return false;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!m) return false;
  const y = Number(m[1]), mo = Number(m[2]) - 1, d = Number(m[3]);
  const dt = new Date(y, mo, d, 0, 0, 0, 0);
  return dt.getFullYear() === y && dt.getMonth() === mo && dt.getDate() === d;
}

function diffISOCalendarDays(startISO, endISO) {
  if (!isValidISODateString(startISO) || !isValidISODateString(endISO)) return 0;
  const [sy, sm, sd] = startISO.split('-').map(Number);
  const [ey, em, ed] = endISO.split('-').map(Number);
  const utcA = Date.UTC(sy, sm - 1, sd);
  const utcB = Date.UTC(ey, em - 1, ed);
  return Math.round((utcB - utcA) / 86400000);
}

function getValidatedTimezone(overrideTz) {
  const candidate = overrideTz || (() => {
    try { return Intl.DateTimeFormat().resolvedOptions().timeZone; } catch (_) { return ''; }
  })();
  if (candidate && typeof candidate === 'string') {
    try {
      new Intl.DateTimeFormat('de-DE', { timeZone: candidate });
      return candidate;
    } catch (_) {}
  }
  return 'Europe/Berlin';
}

function buildMissionSchedulePayload(scheduleState, options = {}) {
  if (!scheduleState || typeof scheduleState !== 'object' || !isValidISODateString(scheduleState.deadlineDate)) {
    return null;
  }
  const currentLocalDate = isValidISODateString(options.currentLocalDate)
    ? options.currentLocalDate
    : getLocalTodayISO();
  const deadlineDate = scheduleState.deadlineDate;
  const preparationStartDate = isValidISODateString(scheduleState.preparationStartDate)
    ? scheduleState.preparationStartDate
    : currentLocalDate;
  const preparationDays = Math.max(0, diffISOCalendarDays(preparationStartDate, deadlineDate));
  const startMode = ['immediate', 'one_week_before', 'two_weeks_before', 'custom'].includes(scheduleState.startMode)
    ? scheduleState.startMode
    : 'immediate';

  const payload = {
    currentLocalDate,
    deadlineDate,
    preparationStartDate,
    preparationDays,
    startMode,
    timezone: getValidatedTimezone(options.timezone)
  };

  if (Array.isArray(scheduleState.preferredWeekdays) && scheduleState.preferredWeekdays.length > 0) {
    const weekdays = [...new Set(scheduleState.preferredWeekdays.map(Number).filter(n => n >= 1 && n <= 7))].sort((a, b) => a - b);
    if (weekdays.length > 0) {
      payload.preferredWeekdays = weekdays;
    }
  }

  if (typeof scheduleState.preferredSessionMinutes === 'number' && [20, 30, 45].includes(scheduleState.preferredSessionMinutes)) {
    payload.preferredSessionMinutes = scheduleState.preferredSessionMinutes;
  }

  return payload;
}

function parseMissionErrorResponse(status, body, fallbackError) {
  const en = isEnglishLanguage();
  const rawErr = body && typeof body === 'object' ? body.error : null;
  const code = (rawErr && typeof rawErr === 'object' && typeof rawErr.code === 'string')
    ? rawErr.code
    : (body && typeof body.code === 'string' ? body.code : '');
  const serverMessage = (rawErr && typeof rawErr === 'object' && typeof rawErr.message === 'string')
    ? rawErr.message
    : (typeof rawErr === 'string' ? rawErr : '');
  const retryableFlag = (rawErr && typeof rawErr === 'object' && typeof rawErr.retryable === 'boolean')
    ? rawErr.retryable
    : null;

  if (fallbackError && fallbackError.name === 'AbortError') {
    return {
      code: 'AI_TIMEOUT',
      retryable: true,
      message: en
        ? 'The analysis took too long. Your input and photo were kept — please try again.'
        : 'Die Analyse hat zu lange gedauert. Deine Eingaben und dein Foto bleiben erhalten – bitte versuche es erneut.'
    };
  }

  if (code === 'UNSUPPORTED_FILE_TYPE' || status === 415) {
    return {
      code: 'UNSUPPORTED_FILE_TYPE',
      retryable: false,
      message: serverMessage || (en
        ? 'Unsupported image format. Please use JPEG, PNG, or WEBP.'
        : 'Nicht unterstütztes Dateiformat. Bitte verwende JPEG, PNG oder WEBP.')
    };
  }
  if (code === 'FILE_TOO_LARGE' || status === 413) {
    return {
      code: 'FILE_TOO_LARGE',
      retryable: false,
      message: serverMessage || (en
        ? 'The image file is too large. Please select a smaller photo.'
        : 'Das Bild ist zu groß. Bitte wähle ein kleineres Foto aus.')
    };
  }
  if (code === 'IMAGE_PROCESSING_FAILED') {
    return {
      code: 'IMAGE_PROCESSING_FAILED',
      retryable: false,
      message: serverMessage || (en
        ? 'The uploaded image could not be processed. Please try another photo.'
        : 'Das Bild konnte nicht verarbeitet werden. Bitte wähle ein anderes Foto.')
    };
  }
  if (code === 'INVALID_REQUEST' || status === 400) {
    return {
      code: 'INVALID_REQUEST',
      retryable: false,
      message: serverMessage || (en
        ? 'Please check your goal description and schedule selection.'
        : 'Bitte prüfe deine Eingabe und den gewählten Zeitplan.')
    };
  }
  if (code === 'AI_RATE_LIMITED' || status === 429) {
    return {
      code: 'AI_RATE_LIMITED',
      retryable: true,
      message: serverMessage || (en
        ? 'Too many requests right now. Please wait a moment and try again.'
        : 'Der AI-Dienst ist kurzzeitig ausgelastet. Bitte warte einen Moment und versuche es erneut.')
    };
  }
  if (code === 'AI_TIMEOUT' || status === 504 || status === 524) {
    return {
      code: 'AI_TIMEOUT',
      retryable: true,
      message: serverMessage || (en
        ? 'The AI service timed out before finishing. Your input is saved — please try again.'
        : 'Die AI-Analyse hat das Zeitlimit überschritten. Deine Eingaben bleiben erhalten – bitte versuche es erneut.')
    };
  }
  if (code === 'AI_UNAVAILABLE' || status === 502 || status === 503) {
    return {
      code: 'AI_UNAVAILABLE',
      retryable: true,
      message: serverMessage || (en
        ? 'The AI service is temporarily unavailable. Please try again shortly.'
        : 'Der AI-Dienst ist momentan nicht erreichbar. Bitte versuche es gleich erneut.')
    };
  }
  if (code === 'INVALID_AI_RESPONSE') {
    return {
      code: 'INVALID_AI_RESPONSE',
      retryable: true,
      message: serverMessage || (en
        ? 'The generated plan was incomplete. Please try again.'
        : 'Der erzeugte Plan war unvollständig und wurde nicht gespeichert. Bitte versuche es erneut.')
    };
  }

  return {
    code: code || 'INTERNAL_ERROR',
    retryable: retryableFlag !== null ? retryableFlag : true,
    message: serverMessage || (fallbackError?.message && fallbackError.message !== '[object Object]' ? fallbackError.message : '') ||
      (en ? 'AI analysis unavailable. Please try again.' : 'AI-Analyse momentan nicht verfügbar. Bitte versuche es erneut.')
  };
}

function cancelMissionAnalysis() {
  if (!isMissionAnalyzeInFlight && !aiRequestController) {
    setAppScreen('goal', { immediate: true });
    return;
  }
  aiRequestCancelledByUser = true;
  clearAnalysisRowTimers();
  try { aiRequestController?.abort(); } catch (_) {}
  aiRequestController = null;
  isMissionAnalyzeInFlight = false;
  const button = document.getElementById('missionPlanButton');
  button?.classList.remove('ai-loading');
  button?.removeAttribute('aria-busy');
  if (typeof window.updateMissionPlanButton === 'function') window.updateMissionPlanButton();
  setAppScreen('goal', { immediate: true });
}

async function analyzeGoal() {
  if (isMissionAnalyzeInFlight) {
    return false;
  }
  const goalText = (document.getElementById('goalPrompt')?.value || '').trim();
  const en = isEnglishLanguage();
  if (!goalText && !missionImageFile) {
    showMissionError(en ? 'Please describe your goal or add a photo.' : 'Beschreibe dein Ziel oder füge ein Foto hinzu.', { retryable: false });
    return false;
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    clearAnalysisRowTimers();
    showMissionOfflineError();
    return false;
  }

  const scheduleState = typeof window.getMissionScheduleState === 'function' ? window.getMissionScheduleState() : null;
  if (typeof window.validateMissionScheduleState === 'function') {
    const schedCheck = window.validateMissionScheduleState();
    if (!schedCheck.valid) {
      if (typeof window.syncMissionScheduleUI === 'function') window.syncMissionScheduleUI();
      const noticeEl = document.getElementById('mgScheduleNotice');
      noticeEl?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      return false;
    }
  }

  if (!selectedMissionTypeValue) selectedMissionTypeValue = en ? 'Custom mission' : 'Eigene Mission';
  if (!selectedMissionConfidenceValue) selectedMissionConfidenceValue = en ? '50/50' : 'Teilsicher';

  clearMissionError();
  clearAnalysisRowTimers();
  resetAnalysisRows();

  isMissionAnalyzeInFlight = true;
  aiRequestCancelledByUser = false;

  const button = document.getElementById('missionPlanButton');
  if (button) {
    button.classList.add('ai-loading');
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
  }

  setAppScreen('analyzing');
  [450, 950].forEach((t, i) => analysisRowTimers.push(setTimeout(() => completeAnalysisRow(i), t)));

  if (aiRequestController) {
    try { aiRequestController.abort(); } catch (_) {}
  }
  aiRequestController = new AbortController();

  const hasImage = Boolean(missionImageFile && missionImageFile instanceof Blob && missionImageFile.size > 0);
  const timeoutMs = hasImage ? MISSION_REQUEST_TIMEOUT_IMAGE_MS : MISSION_REQUEST_TIMEOUT_TEXT_MS;
  const timeout = setTimeout(() => {
    try { aiRequestController?.abort(); } catch (_) {}
  }, timeoutMs);

  const schedulePayload = buildMissionSchedulePayload(scheduleState);

  try {
    const form = new FormData();
    form.append('goalText', goalText);
    form.append('missionType', missionTypeForAPI());
    form.append('confidence', confidenceForAPI());
    if (schedulePayload) {
      form.append('schedule', JSON.stringify(schedulePayload));
    }
    if (hasImage) {
      const safeName = (missionImageFile.name && String(missionImageFile.name).trim()) || 'material.jpg';
      form.append('image', missionImageFile, safeName);
    }

    const response = await fetch(FOCUS_AI_ENDPOINT, {
      method: 'POST',
      body: form,
      signal: aiRequestController.signal
    });

    let data = {};
    try { data = await response.json(); } catch (_) { data = {}; }

    if (!response.ok || !data || data.ok !== true || !data.plan) {
      const parsedErr = parseMissionErrorResponse(response.status, data, null);
      const err = new Error(parsedErr.message);
      err.code = parsedErr.code;
      err.retryable = parsedErr.retryable;
      throw err;
    }

    pendingAIPlan = normalizeAIPlan(data.plan, schedulePayload);
    completeAnalysisRow(2);
    renderAIConfirmation(pendingAIPlan);
    window.FocusModeApp?.services?.storage?.setJSON?.('focus-mode-last-ai-plan', pendingAIPlan);
    setTimeout(() => {
      if (!aiRequestCancelledByUser) setAppScreen('confirm');
    }, 420);
    return true;
  } catch (error) {
    clearAnalysisRowTimers();
    if (aiRequestCancelledByUser) {
      return false;
    }
    console.error('Focus AI request failed', {
      name: error?.name,
      code: error?.code,
      message: error?.message
    });
    if (isOfflineOrNetworkFailure(error)) {
      showMissionOfflineError();
      return false;
    }
    const resolved = error?.code
      ? { message: error.message, retryable: error.retryable !== false }
      : parseMissionErrorResponse(0, null, error);
    showMissionError(resolved.message, { retryable: resolved.retryable });
    return false;
  } finally {
    clearTimeout(timeout);
    aiRequestController = null;
    isMissionAnalyzeInFlight = false;
    if (button) {
      button.classList.remove('ai-loading');
      button.removeAttribute('aria-busy');
    }
    if (typeof window.updateMissionPlanButton === 'function') {
      window.updateMissionPlanButton();
    }
  }
}

function normalizeAIPlan(plan, activeSchedule) {
  if (!plan || typeof plan !== 'object' || Array.isArray(plan)) {
    const err = new Error('Die AI-Antwort hatte kein gültiges Format.');
    err.code = 'INVALID_AI_RESPONSE';
    throw err;
  }
  const goalObj = plan.goal && typeof plan.goal === 'object' ? plan.goal : null;
  const rawTitle = goalObj && typeof goalObj.title === 'string' ? goalObj.title.trim() : '';
  if (!rawTitle) {
    const err = new Error('Der AI-Plan enthält keinen gültigen Titel.');
    err.code = 'INVALID_AI_RESPONSE';
    throw err;
  }
  const allowedTypes = ['exam', 'assignment', 'project', 'custom'];
  const rawType = goalObj && typeof goalObj.type === 'string' ? goalObj.type.trim().toLowerCase() : 'custom';
  const normalizedType = allowedTypes.includes(rawType) ? rawType : 'custom';

  if (!Array.isArray(plan.sessions) || plan.sessions.length === 0) {
    const err = new Error('Der AI-Plan enthält keine Sitzungen.');
    err.code = 'INVALID_AI_RESPONSE';
    throw err;
  }

  const deadlineISO = activeSchedule && isValidISODateString(activeSchedule.deadlineDate)
    ? activeSchedule.deadlineDate
    : null;

  const sessions = plan.sessions.slice(0, 6).map((s, i) => {
    if (!s || typeof s !== 'object') {
      const err = new Error('Ein Missionsschritt ist ungültig.');
      err.code = 'INVALID_AI_RESPONSE';
      throw err;
    }
    const title = String(s.title || '').trim();
    const task = String(s.task || s.reason || '').trim();
    const durationNum = Number(s.durationMinutes);
    if (!title || !task || !Number.isFinite(durationNum) || durationNum <= 0) {
      const err = new Error('Ein Missionsschritt ist unvollständig.');
      err.code = 'INVALID_AI_RESPONSE';
      throw err;
    }
    const stepDate = typeof s.scheduledDate === 'string' && isValidISODateString(s.scheduledDate)
      ? s.scheduledDate.trim()
      : (typeof s.date === 'string' && isValidISODateString(s.date) ? s.date.trim() : null);
    if (deadlineISO && stepDate && diffISOCalendarDays(deadlineISO, stepDate) > 0) {
      const err = new Error('Ein geplanter Schritt liegt nach dem gewählten Prüfungs- oder Abgabedatum.');
      err.code = 'INVALID_AI_RESPONSE';
      throw err;
    }
    const out = {
      id: String(s.id || ('session-' + (i + 1))),
      title,
      task,
      durationMinutes: Math.max(5, Math.min(60, Math.round(durationNum))),
      reason: String(s.reason || ''),
      requiredTools: Array.isArray(s.requiredTools) ? s.requiredTools.map(String) : [],
      recommendedApps: Array.isArray(s.recommendedApps) ? s.recommendedApps.map(String) : []
    };
    if (stepDate) out.scheduledDate = stepDate;
    return out;
  });

  const rawDeadlineText = String(goalObj.deadlineText || '').trim();
  const resolvedDeadlineText = rawDeadlineText && rawDeadlineText !== 'Nicht angegeben'
    ? rawDeadlineText
    : (deadlineISO || 'Nicht angegeben');

  const scheduleWarning = typeof plan.scheduleWarning === 'string' && plan.scheduleWarning.trim()
    ? plan.scheduleWarning.trim()
    : (typeof plan.warning === 'string' && plan.warning.trim() ? plan.warning.trim() : '');

  return {
    goal: {
      title: rawTitle,
      subject: String(goalObj.subject || 'Allgemein').trim() || 'Allgemein',
      type: normalizedType,
      deadlineText: resolvedDeadlineText
    },
    sourceAnalysis: {
      detectedTasks: String(plan.sourceAnalysis?.detectedTasks || ''),
      detectedPages: String(plan.sourceAnalysis?.detectedPages || ''),
      confidence: Number(plan.sourceAnalysis?.confidence) || 0,
      missingInformation: Array.isArray(plan.sourceAnalysis?.missingInformation) ? plan.sourceAnalysis.missingInformation : [],
      facts: Array.isArray(plan.sourceAnalysis?.facts) ? plan.sourceAnalysis.facts : [],
      estimates: Array.isArray(plan.sourceAnalysis?.estimates) ? plan.sourceAnalysis.estimates : []
    },
    estimate: {
      totalMinutes: Number(plan.estimate?.totalMinutes) || sessions.reduce((a, s) => a + s.durationMinutes, 0),
      difficulty: String(plan.estimate?.difficulty || 'unknown'),
      explanation: String(plan.estimate?.explanation || 'Die Dauer ist eine erste Schätzung und wird nach deinen Sitzungen angepasst.')
    },
    ...(scheduleWarning ? { scheduleWarning } : {}),
    ...(activeSchedule ? { schedule: activeSchedule } : {}),
    sessions
  };
}

function escapeHTML(value) {
  return String(value).replace(/[&<>'"]/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;'
  }[c]));
}

function renderAIConfirmation(plan) {
  const rows = [
    ['Fach', plan.goal.subject],
    ['Thema', plan.goal.title],
    ['Frist', plan.goal.deadlineText || 'Nicht angegeben'],
    ['Erkannte Aufgaben', plan.sourceAnalysis.detectedTasks || 'Nicht eindeutig erkannt'],
    ['Erkannte Seiten', plan.sourceAnalysis.detectedPages || 'Nicht eindeutig erkannt'],
    ['Geschätzter Aufwand', formatMinutes(plan.estimate.totalMinutes) + ' · ' + plan.sessions.length + ' Sitzungen']
  ];
  const fieldsEl = document.getElementById('aiConfirmFields');
  if (fieldsEl) {
    fieldsEl.innerHTML = rows.map(([l, v]) =>
      '<div class="ai-confirm-row"><div class="ai-confirm-label">' + escapeHTML(l) + '</div><div class="ai-confirm-value">' + escapeHTML(v) + '</div></div>'
    ).join('');
  }
  const missing = document.getElementById('aiMissingInfo');
  if (missing) {
    const warnings = [];
    if (plan.scheduleWarning) warnings.push(plan.scheduleWarning);
    if (Array.isArray(plan.sourceAnalysis?.missingInformation)) {
      warnings.push(...plan.sourceAnalysis.missingInformation);
    }
    if (warnings.length) {
      missing.innerHTML = '<strong>Bitte prüfen:</strong><br>' + warnings.map(escapeHTML).join('<br>');
      missing.classList.remove('hidden');
    } else {
      missing.classList.add('hidden');
    }
  }
  if (typeof lucide?.createIcons === 'function') lucide.createIcons();
}

function formatMinutes(total) {
  const m = Math.max(0, Math.round(Number(total) || 0));
  const h = Math.floor(m / 60);
  const r = m % 60;
  return h ? (h + 'h' + (r ? ' ' + r + 'm' : '')) : (r + ' Min.');
}

function missionTypeLabel(type) {
  return ({ exam: 'Prüfung', assignment: 'Aufgabe', project: 'Projekt', custom: 'Mission' })[type] || 'Mission';
}

if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('pagehide', () => {
    if (isMissionAnalyzeInFlight && aiRequestController) {
      aiRequestCancelledByUser = true;
      try { aiRequestController.abort(); } catch (_) {}
      aiRequestController = null;
      isMissionAnalyzeInFlight = false;
    }
  });
}

(function registerAIService(global) {
  const App = global.FocusModeApp;
  global.isMissionAnalyzeInFlight = () => isMissionAnalyzeInFlight;
  global.hasMissionImageAttached = () => Boolean(missionImageFile && missionImageFile instanceof Blob && missionImageFile.size > 0);
  if (App?.core?.registerModule && !App.core.hasModule('services.ai')) {
    App.services.ai = App.core.registerModule('services.ai', Object.freeze({
      endpoint: FOCUS_AI_ENDPOINT,
      adaptEndpoint: FOCUS_AI_ADAPT_ENDPOINT,
      timeoutMs: MISSION_REQUEST_TIMEOUT_TEXT_MS,
      imageTimeoutMs: MISSION_REQUEST_TIMEOUT_IMAGE_MS,
      analyzeGoal,
      cancelMissionAnalysis,
      normalizeAIPlan,
      missionTypeForAPI,
      confidenceForAPI,
      validateMissionImageInput,
      compressMissionImage,
      clearMissionImage,
      buildMissionSchedulePayload,
      parseMissionErrorResponse,
      showMissionOfflineError
    }), { virtualPath: 'src/services/ai-service.js', version: '17.4.1', status: 'ai-boundary' });
  }
})(typeof window !== 'undefined' ? window : globalThis);
