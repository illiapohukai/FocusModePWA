/**
 * Physical module: src/services/ai-service.js
 * v17.4.2 Gemini 3.8 Flash Integration
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

/* v17.17 input/output bounds. Header-declared image dimensions are checked before decoding so a
   small file cannot expand into a huge bitmap (decompression bomb). */
const MISSION_IMAGE_MAX_SOURCE_SIDE = 12000;
const MISSION_IMAGE_MAX_SOURCE_PIXELS = 40 * 1000 * 1000;
const MISSION_GOAL_TEXT_MAX_CHARS = 6000;
const AI_TEXT_LIMITS = Object.freeze({ title: 200, short: 120, text: 2000, item: 500, listItems: 20, tools: 12 });

let missionImageFile = null;
let missionImagePreviewUrl = null;
let pendingAIPlan = null;
let activeAIPlan = null;
let aiRequestController = null;
let aiRequestCancelledByUser = false;
let isMissionAnalyzeInFlight = false;
/* v17.17: one active request owner. Only the request whose token matches may touch shared state,
   so a late-settling aborted request can never release the lock of a newer one. */
let aiRequestToken = 0;

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

/* v17.17: identify the real format from the file signature (magic bytes) and read the
   header-declared dimensions without decoding pixels. Returns { mime, width, height } or null. */
function readU16BE(b, i) { return (b[i] << 8) | b[i + 1]; }
function readU32BE(b, i) { return ((b[i] << 24) >>> 0) + (b[i + 1] << 16) + (b[i + 2] << 8) + b[i + 3]; }
function readU16LE(b, i) { return b[i] | (b[i + 1] << 8); }
function readU24LE(b, i) { return b[i] | (b[i + 1] << 8) | (b[i + 2] << 16); }

function sniffImageHeader(bytes) {
  const b = bytes;
  if (!b || b.length < 12) return null;
  // PNG: 89 50 4E 47 0D 0A 1A 0A, IHDR width/height at 16/20
  if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47 && b[4] === 0x0D && b[5] === 0x0A && b[6] === 0x1A && b[7] === 0x0A) {
    if (b.length < 24) return { mime: 'image/png', width: 0, height: 0 };
    return { mime: 'image/png', width: readU32BE(b, 16), height: readU32BE(b, 20) };
  }
  // WebP: "RIFF" .... "WEBP"
  if (b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50) {
    let width = 0, height = 0;
    const chunk = String.fromCharCode(b[12], b[13], b[14], b[15]);
    if (chunk === 'VP8X' && b.length >= 30) { width = readU24LE(b, 24) + 1; height = readU24LE(b, 27) + 1; }
    else if (chunk === 'VP8 ' && b.length >= 30) { width = readU16LE(b, 26) & 0x3fff; height = readU16LE(b, 28) & 0x3fff; }
    else if (chunk === 'VP8L' && b.length >= 25) {
      const bits = b[21] | (b[22] << 8) | (b[23] << 16) | (b[24] << 24);
      width = (bits & 0x3fff) + 1; height = ((bits >> 14) & 0x3fff) + 1;
    }
    return { mime: 'image/webp', width, height };
  }
  // JPEG: FF D8 FF, then scan segments for a SOFn marker
  if (b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF) {
    let i = 2;
    while (i + 9 < b.length) {
      if (b[i] !== 0xFF) { i += 1; continue; }
      const marker = b[i + 1];
      if (marker === 0xFF) { i += 1; continue; }
      if (marker === 0xD8 || marker === 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { i += 2; continue; }
      const len = readU16BE(b, i + 2);
      if (len < 2) break;
      if (marker >= 0xC0 && marker <= 0xCF && marker !== 0xC4 && marker !== 0xC8 && marker !== 0xCC) {
        return { mime: 'image/jpeg', width: readU16BE(b, i + 7), height: readU16BE(b, i + 5) };
      }
      i += 2 + len;
    }
    return { mime: 'image/jpeg', width: 0, height: 0 };
  }
  return null;
}

/* Header-based checks before any decoding. JPEG SOF may sit after large EXIF blocks, so up to
   256 KB of the file head is inspected; unknown dimensions are re-checked after decoding. */
async function inspectMissionImageFile(file, declaredMime) {
  const en = isEnglishLanguage();
  let head;
  try {
    head = new Uint8Array(await file.slice(0, 262144).arrayBuffer());
  } catch (_) {
    return { ok: false, message: en ? 'The image could not be read.' : 'Das Bild konnte nicht gelesen werden.' };
  }
  const info = sniffImageHeader(head);
  if (!info || info.mime !== declaredMime) {
    return {
      ok: false,
      message: en
        ? 'The file content does not match a JPEG, PNG, or WEBP image.'
        : 'Der Dateiinhalt passt nicht zu einem JPEG-, PNG- oder WEBP-Bild. Bitte wähle ein anderes Foto.'
    };
  }
  if (!isMissionImageSizeAcceptable(info.width, info.height, true)) {
    return {
      ok: false,
      message: en
        ? 'The image resolution is too large. Please choose a smaller photo or a screenshot.'
        : 'Die Bildauflösung ist zu groß. Bitte wähle ein kleineres Foto oder einen Ausschnitt.'
    };
  }
  return { ok: true, mime: info.mime, width: info.width, height: info.height };
}

function isMissionImageSizeAcceptable(width, height, allowUnknown) {
  const w = Number(width) || 0;
  const h = Number(height) || 0;
  if (!w || !h) return Boolean(allowUnknown);
  return w <= MISSION_IMAGE_MAX_SOURCE_SIDE && h <= MISSION_IMAGE_MAX_SOURCE_SIDE && (w * h) <= MISSION_IMAGE_MAX_SOURCE_PIXELS;
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

  const header = await inspectMissionImageFile(original, check.mime);
  if (!header.ok) {
    showMissionError(header.message, { retryable: false });
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
    if (nameEl) nameEl.textContent = String(original.name || 'Foto.jpg').replace(/[\u0000-\u001f\u007f]/g, '').slice(0, 80);
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
    if (!isMissionImageSizeAcceptable(srcW, srcH, false)) throw new Error('IMAGE_TOO_LARGE');

    const longSide = Math.max(srcW, srcH);
    const needsResize = longSide > MISSION_IMAGE_MAX_DIMENSION;
    const needsCompress = file.size > MISSION_IMAGE_OPTIMIZE_THRESHOLD_BYTES;

    /* v17.17: every image is re-encoded through a canvas, which drops embedded metadata
       (EXIF incl. GPS location, camera serials, comments). Small images keep full size and
       high quality; PNG screenshots stay lossless PNG. */
    const attempts = (!needsResize && !needsCompress)
      ? [
          { maxSide: longSide, quality: 0.92, type: mime === 'image/png' ? 'image/png' : 'image/jpeg' },
          { maxSide: longSide, quality: 0.84 }
        ]
      : [
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

      const outType = cfg.type || 'image/jpeg';
      const blob = await new Promise((resolve, reject) => {
        canvas.toBlob(b => (b && b.size > 0) ? resolve(b) : reject(new Error('compression failed')), outType, cfg.quality);
      });
      bestBlob = blob;
      if (blob.size <= MISSION_IMAGE_MAX_PROCESSED_BYTES) break;
    }

    if (!bestBlob || bestBlob.size <= 0) throw new Error('IMAGE_PROCESSING_FAILED');
    // Generic name: the original filename (which may contain personal names) is not uploaded.
    const outMime = bestBlob.type === 'image/png' ? 'image/png' : 'image/jpeg';
    return new File([bestBlob], outMime === 'image/png' ? 'material.png' : 'material.jpg', { type: outMime, lastModified: Date.now() });
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

function parseMissionErrorResponseCore(status, body, fallbackError) {
  const en = isEnglishLanguage();
  const rawErr = body && typeof body === 'object' ? body.error : null;
  const code = (rawErr && typeof rawErr === 'object' && typeof rawErr.code === 'string')
    ? rawErr.code
    : (body && typeof body.code === 'string' ? body.code : '');
  const serverMessage = ((rawErr && typeof rawErr === 'object' && typeof rawErr.message === 'string')
    ? rawErr.message
    : (typeof rawErr === 'string' ? rawErr : '')).replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, 300);

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

  // v17.17: internal exception text (e.g. JSON parser messages) is never shown to the user.
  return {
    code: code || 'INTERNAL_ERROR',
    retryable: retryableFlag !== null ? retryableFlag : true,
    message: serverMessage ||
      (en ? 'AI analysis unavailable. Please try again.' : 'AI-Analyse momentan nicht verfügbar. Bitte versuche es erneut.')
  };
}

/* The Worker's error contract may include a request ID; it is shown only as a support reference. */
function parseMissionErrorResponse(status, body, fallbackError) {
  const parsed = parseMissionErrorResponseCore(status, body, fallbackError);
  const rawErr = body && typeof body === 'object' ? body.error : null;
  const rawRequestId = rawErr && typeof rawErr === 'object' ? (rawErr.requestId ?? body?.requestId) : body?.requestId;
  parsed.requestId = typeof rawRequestId === 'string' && /^[A-Za-z0-9_-]{4,64}$/.test(rawRequestId) ? rawRequestId : null;
  return parsed;
}

function cancelMissionAnalysis() {
  window.MissionOperator?.cancel();
  if (!isMissionAnalyzeInFlight && !aiRequestController) {
    setAppScreen('goal', { immediate: true });
    return;
  }
  aiRequestCancelledByUser = true;
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
  if (goalText.length > MISSION_GOAL_TEXT_MAX_CHARS) {
    showMissionError(en
      ? 'Please shorten your description (max. ' + MISSION_GOAL_TEXT_MAX_CHARS + ' characters).'
      : 'Bitte kürze deine Beschreibung (max. ' + MISSION_GOAL_TEXT_MAX_CHARS + ' Zeichen). Deine Eingabe bleibt erhalten.', { retryable: false });
    return false;
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
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

  isMissionAnalyzeInFlight = true;
  aiRequestCancelledByUser = false;
  const requestToken = ++aiRequestToken;
  const isCurrentRequest = () => requestToken === aiRequestToken;

  const button = document.getElementById('missionPlanButton');
  if (button) {
    button.classList.add('ai-loading');
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
  }

  // v17.14: the operator animation only observes this request (src/features/missions/mission-operator.js).
  const operatorRun = window.MissionOperator ? window.MissionOperator.begin() : 0;
  setAppScreen('analyzing');

  if (aiRequestController) {
    try { aiRequestController.abort(); } catch (_) {}
  }
  const requestController = new AbortController();
  aiRequestController = requestController;

  const hasImage = Boolean(missionImageFile && missionImageFile instanceof Blob && missionImageFile.size > 0);
  const timeoutMs = hasImage ? MISSION_REQUEST_TIMEOUT_IMAGE_MS : MISSION_REQUEST_TIMEOUT_TEXT_MS;
  const timeout = setTimeout(() => {
    try { requestController.abort(); } catch (_) {}
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
      // Generic upload name only (no user filename leaves the device).
      const safeName = missionImageFile.type === 'image/png' ? 'material.png' : 'material.jpg';
      form.append('image', missionImageFile, safeName);
    }

    const response = await fetch(FOCUS_AI_ENDPOINT, {
      method: 'POST',
      body: form,
      signal: requestController.signal,
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'strict-origin-when-cross-origin'
    });

    let data = {};
    try { data = await response.json(); } catch (_) { data = {}; }
    if (!isCurrentRequest()) return false;

    if (!response.ok || !data || data.ok !== true || !data.plan) {
      const parsedErr = parseMissionErrorResponse(response.status, data, null);
      const err = new Error(parsedErr.message);
      err.code = parsedErr.code;
      err.retryable = parsedErr.retryable;
      err.requestId = parsedErr.requestId || null;
      throw err;
    }

    pendingAIPlan = normalizeAIPlan(data.plan, schedulePayload);
    renderAIConfirmation(pendingAIPlan);
    window.FocusModeApp?.services?.storage?.setJSON?.('focus-mode-last-ai-plan', pendingAIPlan);
    // The planet segment plays only now: response received, parsed, validated and rendered.
    const revealed = window.MissionOperator ? window.MissionOperator.succeed(operatorRun) : Promise.resolve(true);
    revealed.then((shown) => {
      if (shown !== false && !aiRequestCancelledByUser) setAppScreen('confirm');
    });
    return true;
  } catch (error) {
    if (aiRequestCancelledByUser || !isCurrentRequest()) {
      return false;
    }
    window.MissionOperator?.fail(operatorRun, error?.name === 'AbortError' ? 'timeout' : 'error');
    // Operational log: codes only, never the goal text, photo or full server message.
    console.error('Focus AI request failed', {
      name: error?.name,
      code: error?.code || null,
      requestId: error?.requestId || null
    });
    if (isOfflineOrNetworkFailure(error)) {
      showMissionOfflineError();
      return false;
    }
    const resolved = error?.code
      ? { message: error.message, retryable: error.retryable !== false }
      : parseMissionErrorResponse(0, null, error);
    const supportRef = error?.requestId ? ((en ? ' (Request ID: ' : ' (Anfrage-ID: ') + error.requestId + ')') : '';
    showMissionError(resolved.message + supportRef, { retryable: resolved.retryable });
    return false;
  } finally {
    clearTimeout(timeout);
    // A superseded request must not release the lock or UI state of the newer request.
    if (isCurrentRequest()) {
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
}

/* v17.17 AI output boundary. Model output is untrusted data (prompt injection via user material
   is possible): strings are length-capped, lists are bounded, and step IDs must satisfy the same
   safe-ID policy as imported data, because IDs are later placed into inline handler strings. */
function aiText(value, max) {
  if (value === null || value === undefined) return '';
  const text = typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  return text.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').trim().slice(0, max);
}

function aiList(value, maxItems, maxLen) {
  if (!Array.isArray(value)) return [];
  return value.slice(0, maxItems).map(item => {
    if (item && typeof item === 'object' && !Array.isArray(item)) {
      const out = {};
      for (const key of ['label', 'name', 'title', 'value', 'text', 'description', 'detail']) {
        if (typeof item[key] === 'string' || typeof item[key] === 'number') out[key] = aiText(item[key], maxLen);
      }
      return out;
    }
    return aiText(item, maxLen);
  }).filter(item => (typeof item === 'string' ? item : Object.keys(item).length));
}

function isSafeEntityId(value) {
  const schema = window.FocusModeApp?.data?.schema;
  if (schema?.isValidEntityId) return schema.isValidEntityId(value);
  return typeof value === 'string' && /^[a-zA-Z0-9_.:-]{2,160}$/.test(value);
}

function safeAiStepId(rawId, index, used) {
  const candidate = typeof rawId === 'string' || typeof rawId === 'number' ? String(rawId).trim() : '';
  let id = candidate && isSafeEntityId(candidate) && !used.has(candidate) ? candidate : 'session-' + (index + 1);
  let n = 2;
  while (used.has(id)) id = 'session-' + (index + 1) + '-' + n++;
  used.add(id);
  return id;
}

function normalizeAIPlan(plan, activeSchedule) {
  if (!plan || typeof plan !== 'object' || Array.isArray(plan)) {
    const err = new Error('Die AI-Antwort hatte kein gültiges Format.');
    err.code = 'INVALID_AI_RESPONSE';
    throw err;
  }
  const goalObj = plan.goal && typeof plan.goal === 'object' ? plan.goal : null;
  const rawTitle = goalObj && typeof goalObj.title === 'string' ? aiText(goalObj.title, AI_TEXT_LIMITS.title) : '';
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

  const usedStepIds = new Set();
  const sessions = plan.sessions.slice(0, 6).map((s, i) => {
    if (!s || typeof s !== 'object') {
      const err = new Error('Ein Missionsschritt ist ungültig.');
      err.code = 'INVALID_AI_RESPONSE';
      throw err;
    }
    const title = aiText(s.title, AI_TEXT_LIMITS.title);
    const task = aiText(s.task || s.reason, AI_TEXT_LIMITS.text);
    const durationNum = Number(s.durationMinutes);
    if (!title || !task || !Number.isFinite(durationNum) || durationNum < 5 || durationNum > 60) {
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
      id: safeAiStepId(s.id, i, usedStepIds),
      title,
      task,
      durationMinutes: Math.max(5, Math.min(60, Math.round(durationNum))),
      reason: aiText(s.reason, AI_TEXT_LIMITS.text),
      requiredTools: aiList(s.requiredTools, AI_TEXT_LIMITS.tools, AI_TEXT_LIMITS.short).map(String).filter(x => x && x !== '[object Object]'),
      recommendedApps: aiList(s.recommendedApps, AI_TEXT_LIMITS.tools, AI_TEXT_LIMITS.short).map(String).filter(x => x && x !== '[object Object]')
    };
    if (stepDate) out.scheduledDate = stepDate;
    return out;
  });

  const rawDeadlineText = aiText(goalObj.deadlineText, AI_TEXT_LIMITS.short);
  const resolvedDeadlineText = rawDeadlineText && rawDeadlineText !== 'Nicht angegeben'
    ? rawDeadlineText
    : (deadlineISO || 'Nicht angegeben');

  const scheduleWarning = aiText(
    typeof plan.scheduleWarning === 'string' && plan.scheduleWarning.trim()
      ? plan.scheduleWarning
      : (typeof plan.estimate?.scheduleWarning === 'string' && plan.estimate.scheduleWarning.trim()
        ? plan.estimate.scheduleWarning
        : (typeof plan.warning === 'string' ? plan.warning : '')),
    AI_TEXT_LIMITS.item
  );

  return {
    goal: {
      title: rawTitle,
      subject: aiText(goalObj.subject, AI_TEXT_LIMITS.short) || 'Allgemein',
      type: normalizedType,
      deadlineText: resolvedDeadlineText
    },
    sourceAnalysis: {
      detectedTasks: aiText(plan.sourceAnalysis?.detectedTasks, AI_TEXT_LIMITS.item),
      detectedPages: aiText(plan.sourceAnalysis?.detectedPages, AI_TEXT_LIMITS.item),
      confidence: Math.max(0, Math.min(100, Number(plan.sourceAnalysis?.confidence) || 0)),
      missingInformation: aiList(plan.sourceAnalysis?.missingInformation, AI_TEXT_LIMITS.listItems, AI_TEXT_LIMITS.item),
      facts: aiList(plan.sourceAnalysis?.facts, AI_TEXT_LIMITS.listItems, AI_TEXT_LIMITS.item),
      estimates: aiList(plan.sourceAnalysis?.estimates, AI_TEXT_LIMITS.listItems, AI_TEXT_LIMITS.item)
    },
    estimate: {
      totalMinutes: Math.max(0, Math.min(100000, Number(plan.estimate?.totalMinutes) || 0)) || sessions.reduce((a, s) => a + s.durationMinutes, 0),
      difficulty: aiText(plan.estimate?.difficulty, 40) || 'unknown',
      explanation: aiText(plan.estimate?.explanation, AI_TEXT_LIMITS.text) || 'Die Dauer ist eine erste Schätzung und wird nach deinen Sitzungen angepasst.',
      ...(scheduleWarning ? { scheduleWarning } : {})
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

/* v17.7 Mission-Briefing: presents the normalized plan without changing it.
   Facts, estimates and open questions stay visibly separate; unavailable fields are omitted. */
let pendingMissionDraftId = null;

function newMissionDraftId() {
  const schema = window.FocusModeApp?.data?.schema;
  if (schema?.generateUUID) return schema.generateUUID();
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID();
  return 'mission_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
}

function consumePendingMissionDraftId() {
  const id = pendingMissionDraftId;
  pendingMissionDraftId = null;
  return id;
}

function briefText(item) {
  if (item == null) return '';
  if (typeof item === 'string' || typeof item === 'number') return String(item).trim();
  if (typeof item === 'object') {
    const label = item.label || item.name || item.title || '';
    const value = item.value ?? item.text ?? item.description ?? item.detail ?? '';
    return [label, value].map(v => String(v || '').trim()).filter(Boolean).join(': ');
  }
  return '';
}

function briefList(arr) {
  return (Array.isArray(arr) ? arr : []).map(briefText).filter(Boolean);
}

function briefDate(iso, withWeekday) {
  if (!isValidISODateString(iso)) return '';
  const [y, m, d] = iso.split('-').map(Number);
  try {
    return new Intl.DateTimeFormat(isEnglishLanguage() ? 'en-GB' : 'de-DE', withWeekday
      ? { weekday: 'short', day: 'numeric', month: 'short' }
      : { day: 'numeric', month: 'short' }).format(new Date(y, m - 1, d));
  } catch (_) {
    return iso;
  }
}

function briefConfidence(raw) {
  let n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return null;
  if (n > 1) n = n / 100;
  n = Math.max(0, Math.min(1, n));
  if (n >= 0.75) return { level: 3, label: 'hoch' };
  if (n >= 0.45) return { level: 2, label: 'mittel' };
  return { level: 1, label: 'niedrig' };
}

function briefDifficulty(raw) {
  const key = String(raw || '').toLowerCase();
  return ({ easy: 'Leicht', low: 'Leicht', medium: 'Mittel', moderate: 'Mittel', hard: 'Anspruchsvoll', high: 'Anspruchsvoll', difficult: 'Anspruchsvoll' })[key] || '';
}

function briefWeekdays(days) {
  const wk = [...new Set((days || []).map(Number).filter(n => n >= 1 && n <= 7))].sort((a, b) => a - b);
  if (!wk.length) return '';
  const joined = wk.join(',');
  if (joined === '1,2,3,4,5') return 'Unter der Woche';
  if (joined === '6,7') return 'Wochenende';
  return wk.map(d => ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][d - 1]).join(', ');
}

function briefGroup(icon, title, items, tone) {
  if (!items.length) return '';
  return '<section class="mb-source-group is-' + tone + '">'
    + '<h5><i data-lucide="' + icon + '"></i>' + escapeHTML(title) + '</h5>'
    + '<ul>' + items.map(t => '<li>' + escapeHTML(t) + '</li>').join('') + '</ul>'
    + '</section>';
}

function toggleBriefSource(btn) {
  const panel = document.getElementById('mbSourcePanel');
  if (!btn || !panel) return;
  const open = btn.getAttribute('aria-expanded') !== 'true';
  btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  panel.hidden = !open;
  btn.closest('.mb-source')?.classList.toggle('is-open', open);
}

function toggleBriefStep(btn) {
  const panel = btn?.nextElementSibling;
  if (!panel) return;
  const open = btn.getAttribute('aria-expanded') !== 'true';
  btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  panel.hidden = !open;
}

function renderAIConfirmation(plan) {
  const fieldsEl = document.getElementById('aiConfirmFields');
  const missing = document.getElementById('aiMissingInfo');
  if (!plan || !fieldsEl) return;

  pendingMissionDraftId = newMissionDraftId();
  const theme = typeof window.previewMissionThemeV177 === 'function' ? window.previewMissionThemeV177(pendingMissionDraftId) : 'aubergine';
  const screen = document.getElementById('app-screen-confirm');
  if (screen) {
    screen.className = screen.className.replace(/\btheme-[a-z]+\b/g, '').trim() + ' theme-' + theme;
    screen.dataset.missionTheme = theme;
  }

  const goal = plan.goal || {};
  const src = plan.sourceAnalysis || {};
  const est = plan.estimate || {};
  const sched = plan.schedule || null;
  const sessions = Array.isArray(plan.sessions) ? plan.sessions : [];
  const totalMinutes = Number(est.totalMinutes) || sessions.reduce((a, x) => a + (Number(x.durationMinutes) || 0), 0);
  const subject = goal.subject && goal.subject !== 'Allgemein' ? goal.subject : '';
  const deadlineText = goal.deadlineText && goal.deadlineText !== 'Nicht angegeben' ? goal.deadlineText : '';
  const deadlineLabel = sched?.deadlineDate ? briefDate(sched.deadlineDate, true) : (isValidISODateString(deadlineText) ? briefDate(deadlineText, true) : deadlineText);
  const difficulty = briefDifficulty(est.difficulty);
  const confidence = briefConfidence(src.confidence);
  const hadImage = typeof window.hasMissionImageAttached === 'function' && window.hasMissionImageAttached();

  /* A. Hero + B. key summary */
  const summary = [
    { icon: 'layers', value: String(sessions.length), label: sessions.length === 1 ? 'Sitzung' : 'Sitzungen' },
    totalMinutes ? { icon: 'clock-3', value: formatMinutes(totalMinutes), label: 'geschätzt' } : null,
    difficulty ? { icon: 'gauge', value: difficulty, label: 'Niveau' } : null
  ].filter(Boolean);
  const hero = '<article class="mb-hero mission-theme theme-' + theme + '">'
    + '<div class="mb-hero-grid" aria-hidden="true"></div>'
    + '<div class="mb-hero-top"><span class="mb-kicker"><i></i>Neue Mission · ' + escapeHTML(missionTypeLabel(goal.type)) + '</span>'
    + (deadlineLabel ? '<span class="mb-deadline"><i data-lucide="flag"></i>' + escapeHTML(deadlineLabel) + '</span>' : '') + '</div>'
    + '<h4 class="mb-title">' + escapeHTML(goal.title || 'Focus Mission') + '</h4>'
    + (subject ? '<p class="mb-subject">' + escapeHTML(subject) + '</p>' : '')
    + '<div class="mb-summary" role="list">' + summary.map(x =>
      '<div class="mb-summary-item" role="listitem"><i data-lucide="' + x.icon + '"></i><span><strong>' + escapeHTML(x.value) + '</strong><small>' + escapeHTML(x.label) + '</small></span></div>'
    ).join('') + '</div>'
    + '</article>';

  /* C. Preparation route (only with a real selected schedule) */
  let route = '';
  if (sched && isValidISODateString(sched.deadlineDate)) {
    const startISO = isValidISODateString(sched.preparationStartDate) ? sched.preparationStartDate : (sched.currentLocalDate || '');
    const days = Math.max(0, Number(sched.preparationDays) || 0);
    const rhythm = [briefWeekdays(sched.preferredWeekdays), sched.preferredSessionMinutes ? sched.preferredSessionMinutes + ' Min. pro Sitzung' : ''].filter(Boolean);
    route = '<section class="mb-card mb-route" aria-label="Vorbereitung">'
      + '<div class="mb-route-line">'
      + '<div class="mb-route-end"><small>Start</small><strong>' + escapeHTML(briefDate(startISO) || 'Heute') + '</strong></div>'
      + '<div class="mb-route-mid" aria-hidden="true"><span></span><em>' + (days === 1 ? '1 Tag' : days + ' Tage') + '</em><span></span></div>'
      + '<div class="mb-route-end is-goal"><small>' + (goal.type === 'exam' ? 'Prüfung' : 'Ziel') + '</small><strong>' + escapeHTML(briefDate(sched.deadlineDate)) + '</strong></div>'
      + '</div>'
      + '<p class="mb-route-meta"><i data-lucide="calendar-range"></i>' + escapeHTML(rhythm.length ? rhythm.join(' · ') : 'Flexibler Lernrhythmus') + '</p>'
      + '</section>';
  }

  /* E. Session plan */
  const steps = '<section class="mb-steps" aria-label="Sitzungsplan">'
    + '<div class="mb-section-head"><h5>Deine Sitzungen</h5><span>' + sessions.length + ' Schritte</span></div>'
    + '<ol class="mb-step-list">' + sessions.map((x, i) => {
      const first = i === 0;
      const tools = briefList(x.requiredTools), apps = briefList(x.recommendedApps);
      const hasMore = Boolean(String(x.reason || '').trim() || tools.length || apps.length);
      const date = isValidISODateString(x.scheduledDate) ? briefDate(x.scheduledDate, true) : '';
      return '<li class="mb-step' + (first ? ' is-first' : '') + '">'
        + '<span class="mb-step-num" aria-hidden="true">' + (i + 1) + '</span>'
        + '<div class="mb-step-body">'
        + (first ? '<span class="mb-step-badge"><i data-lucide="play"></i>Erster Schritt</span>' : '')
        + '<div class="mb-step-head"><strong>' + escapeHTML(x.title) + '</strong><span class="mb-step-time">' + (Number(x.durationMinutes) || 0) + ' Min.</span></div>'
        + '<p class="mb-step-task">' + escapeHTML(x.task || '') + '</p>'
        + (date ? '<span class="mb-step-date"><i data-lucide="calendar"></i>' + escapeHTML(date) + '</span>' : '')
        + (hasMore ? '<button type="button" class="mb-step-why" aria-expanded="false" onclick="toggleBriefStep(this)"><span>Warum so?</span><i data-lucide="chevron-down"></i></button>'
          + '<div class="mb-step-more" hidden>'
          + (String(x.reason || '').trim() ? '<p>' + escapeHTML(x.reason) + '</p>' : '')
          + (tools.length ? '<div class="mb-step-tags"><small>Brauchst du</small>' + tools.map(t => '<span>' + escapeHTML(t) + '</span>').join('') + '</div>' : '')
          + (apps.length ? '<div class="mb-step-tags"><small>Empfohlene Apps</small>' + apps.map(t => '<span>' + escapeHTML(t) + '</span>').join('') + '</div>' : '')
          + '</div>' : '')
        + '</div></li>';
    }).join('')
    + '<li class="mb-step is-finish"><span class="mb-step-num" aria-hidden="true"><i data-lucide="flag"></i></span><div class="mb-step-body"><strong>'
    + escapeHTML(deadlineLabel ? 'Bereit bis ' + deadlineLabel : 'Mission abgeschlossen') + '</strong></div></li>'
    + '</ol></section>';

  /* D. How the plan was built: user input / recognized / estimated */
  const userItems = [];
  const chosenType = typeof selectedMissionTypeValue === 'string' ? selectedMissionTypeValue.replace(/^[^\p{L}]+/u, '').trim() : '';
  if (chosenType) userItems.push('Mission-Typ: ' + chosenType);
  if (typeof selectedMissionConfidenceValue === 'string' && selectedMissionConfidenceValue) {
    userItems.push('Deine Sicherheit: ' + ({ uncertain: 'Unsicher', partial: 'Teilsicher', confident: 'Sicher' })[confidenceForAPI()]);
  }
  if (sched?.deadlineDate) userItems.push('Termin: ' + briefDate(sched.deadlineDate, true));
  if (sched && Array.isArray(sched.preferredWeekdays) && sched.preferredWeekdays.length) userItems.push('Lerntage: ' + briefWeekdays(sched.preferredWeekdays));
  if (sched?.preferredSessionMinutes) userItems.push('Sitzungsdauer: ' + sched.preferredSessionMinutes + ' Min.');
  const recognized = [];
  if (String(src.detectedTasks || '').trim()) recognized.push('Aufgaben: ' + src.detectedTasks);
  if (String(src.detectedPages || '').trim()) recognized.push('Seiten: ' + src.detectedPages);
  recognized.push(...briefList(src.facts));
  const estimated = briefList(src.estimates);
  if (totalMinutes) estimated.push('Gesamtaufwand ca. ' + formatMinutes(totalMinutes));
  if (String(est.explanation || '').trim()) estimated.push(est.explanation);
  const unreadable = hadImage && !recognized.length;
  const sourceBody = briefGroup('user-round', 'Aus deinen Angaben', userItems, 'user')
    + (unreadable
      ? '<section class="mb-source-group is-unclear"><h5><i data-lucide="image-off"></i>Material</h5><ul><li>Im Foto wurde nichts eindeutig erkannt. Der Plan stützt sich auf deine Beschreibung.</li></ul></section>'
      : briefGroup('scan-text', hadImage ? 'Aus dem Material erkannt' : 'Aus deinem Ziel erkannt', recognized, 'fact'))
    + briefGroup('calculator', 'Geschätzt', estimated, 'estimate');
  const source = sourceBody ? '<section class="mb-card mb-source">'
    + '<button type="button" class="mb-source-toggle" aria-expanded="false" aria-controls="mbSourcePanel" onclick="toggleBriefSource(this)">'
    + '<span class="mb-source-icon"><i data-lucide="brain-circuit"></i></span>'
    + '<span class="mb-source-copy"><strong>So wurde der Plan erstellt</strong>'
    + '<small>' + (confidence
      ? '<span class="mb-meter is-' + confidence.level + '" aria-hidden="true"><i></i><i></i><i></i></span>AI-Erkennung: ' + confidence.label
      : 'Angaben, Erkanntes und Schätzungen') + '</small></span>'
    + '<i data-lucide="chevron-down" class="mb-source-chevron"></i></button>'
    + '<div class="mb-source-panel" id="mbSourcePanel" hidden>' + sourceBody
    + '<p class="mb-source-note">Zeiten und Umfang sind Schätzungen und werden nach deinen Sitzungen genauer.</p></div>'
    + '</section>' : '';

  fieldsEl.innerHTML = hero + route;
  const planEl = document.getElementById('aiConfirmPlan');
  if (planEl) planEl.innerHTML = steps + source;
  else fieldsEl.insertAdjacentHTML('beforeend', steps + source);

  if (missing) {
    const warnings = [];
    if (plan.scheduleWarning) warnings.push(plan.scheduleWarning);
    warnings.push(...briefList(src.missingInformation));
    if (warnings.length) {
      missing.innerHTML = '<span class="mb-attention-icon"><i data-lucide="circle-help"></i></span><div><strong>Noch unklar</strong>'
        + '<ul>' + warnings.map(w => '<li>' + escapeHTML(w) + '</li>').join('') + '</ul>'
        + '<small>Ergänze es über „Angaben ändern“ oder passe den Plan später an.</small></div>';
      missing.classList.remove('hidden');
    } else {
      missing.innerHTML = '';
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
  global.toggleBriefSource = toggleBriefSource;
  global.toggleBriefStep = toggleBriefStep;
  global.consumePendingMissionDraftId = consumePendingMissionDraftId;
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
    }), { virtualPath: 'src/services/ai-service.js', version: '17.4.2', status: 'ai-boundary' });
  }
})(typeof window !== 'undefined' ? window : globalThis);
