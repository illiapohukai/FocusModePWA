/**
 * Physical module: src/features/missions/mission-calendar.js
 * v17.10 Home Mission calendar.
 *
 * Read-only view over existing data. It never writes storage and never invents dates:
 *  - deadline : the Mission's stored deadlineText, only when it holds a real calendar date
 *               (ISO, dd.mm.yyyy or "27. November 2026"). Relative text is not placed.
 *  - start    : the day the Mission was created (createdAt). The Zeitplan preparation start is
 *               not part of the stored Mission, so it is not shown as a preparation date.
 *  - focus    : days with real finished Focus sessions of that Mission (session History).
 *  - done     : the day a Mission was completed (completedAt).
 * All dates are local date-only keys (YYYY-MM-DD); no UTC conversion is involved.
 */
(function (global) {
  'use strict';

  const doc = global.document;
  const MONTHS_DE = ['januar', 'februar', 'märz', 'april', 'mai', 'juni', 'juli', 'august', 'september', 'oktober', 'november', 'dezember'];
  const MONTHS_EN = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];
  const MONTH_ALIASES = { 'maerz': 2, 'marz': 2, 'jan': 0, 'feb': 1, 'mär': 2, 'mrz': 2, 'apr': 3, 'jun': 5, 'jul': 6, 'aug': 7, 'sep': 8, 'sept': 8, 'okt': 9, 'oct': 9, 'nov': 10, 'dez': 11, 'dec': 11, 'march': 2, 'may': 4 };

  const view = { year: 0, month: 0, selected: null, signature: '', motion: '' };

  function isEn() { return (doc.documentElement.lang || 'de') === 'en'; }
  function t(de, en) { return isEn() ? en : de; }
  function pad(n) { return String(n).padStart(2, '0'); }
  function keyOf(y, m, d) { return y + '-' + pad(m + 1) + '-' + pad(d); }
  function keyOfDate(dt) { return keyOf(dt.getFullYear(), dt.getMonth(), dt.getDate()); }
  function todayKey() { return keyOfDate(new Date()); }
  function validDate(y, m, d) {
    if (!(y >= 1900 && y <= 2200 && m >= 0 && m <= 11 && d >= 1 && d <= 31)) return null;
    const dt = new Date(y, m, d, 12, 0, 0, 0);
    return dt.getFullYear() === y && dt.getMonth() === m && dt.getDate() === d ? keyOf(y, m, d) : null;
  }
  function parseKey(key) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key || '');
    return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12, 0, 0, 0) : null;
  }
  function dayDiff(fromKey, toKey) {
    const a = parseKey(fromKey), b = parseKey(toKey);
    if (!a || !b) return 0;
    return Math.round((Date.UTC(b.getFullYear(), b.getMonth(), b.getDate()) - Date.UTC(a.getFullYear(), a.getMonth(), a.getDate())) / 86400000);
  }
  function keyFromTimestamp(ms) {
    const n = Number(ms);
    if (!Number.isFinite(n) || n <= 0) return null;
    const dt = new Date(n);
    return Number.isNaN(dt.getTime()) ? null : keyOfDate(dt);
  }
  function monthIndex(word) {
    const w = String(word || '').toLowerCase().replace(/\.$/, '');
    let i = MONTHS_DE.indexOf(w);
    if (i < 0) i = MONTHS_EN.indexOf(w);
    if (i < 0 && Object.prototype.hasOwnProperty.call(MONTH_ALIASES, w)) i = MONTH_ALIASES[w];
    return i;
  }

  /* Returns a date key only when the text contains one explicit, complete calendar date. */
  function parseDeadlineText(text) {
    const s = String(text || '').trim();
    if (!s) return null;
    let m = /\b(\d{4})-(\d{2})-(\d{2})\b/.exec(s);
    if (m) return validDate(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    m = /\b(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})\b/.exec(s);
    if (m) {
      const y = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
      return validDate(y, Number(m[2]) - 1, Number(m[1]));
    }
    m = /\b(\d{1,2})\.?\s+([A-Za-zÄÖÜäöü]{3,10}\.?)\s+(\d{4})\b/.exec(s);
    if (m) {
      const mi = monthIndex(m[2]);
      if (mi >= 0) return validDate(Number(m[3]), mi, Number(m[1]));
    }
    m = /\b([A-Za-z]{3,10})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b/.exec(s);
    if (m) {
      const mi = monthIndex(m[1]);
      if (mi >= 0) return validDate(Number(m[3]), mi, Number(m[2]));
    }
    return null;
  }

  function themeOf(id) {
    return typeof global.getMissionThemeV177 === 'function' ? global.getMissionThemeV177(id) : 'aubergine';
  }

  function readMissions() {
    try {
      const repo = global.FocusModeApp?.repositories?.missions || global.missionRepository;
      return (repo?.getAll ? repo.getAll() : []).filter(m => m && m.status !== 'archived');
    } catch (_) { return []; }
  }
  function readHistory() {
    try {
      if (global.sessionRepository?.getAll) return global.sessionRepository.getAll();
      return global.FocusModeApp?.repositories?.sessions?.listHistory?.() || [];
    } catch (_) { return []; }
  }
  function counted(s) {
    if (typeof global.countedFocus === 'function') return global.countedFocus(s);
    return s && s.status === 'completed' && Number(s.actualMinutes) >= 1;
  }

  /* One event list per render, grouped by local date key. */
  function collectEvents() {
    const missions = readMissions();
    const byId = new Map(missions.map(m => [m.id, m]));
    const events = [];
    missions.forEach(m => {
      const base = { missionId: m.id, title: m.title, theme: themeOf(m.id), missionType: m.type, status: m.status };
      const deadline = parseDeadlineText(m.deadlineText);
      if (deadline) events.push({ ...base, kind: 'deadline', date: deadline });
      const start = keyFromTimestamp(m.createdAt);
      if (start) events.push({ ...base, kind: 'start', date: start });
      const done = m.status === 'completed' ? keyFromTimestamp(m.completedAt) : null;
      if (done) events.push({ ...base, kind: 'done', date: done });
    });
    const focus = new Map();
    readHistory().forEach(s => {
      if (!s || s.type !== 'mission' || !byId.has(s.missionId) || !counted(s)) return;
      const date = keyFromTimestamp(s.finishedAt || s.startedAt);
      if (!date) return;
      const k = s.missionId + '|' + date;
      const agg = focus.get(k) || { count: 0, minutes: 0, missionId: s.missionId, date };
      agg.count += 1;
      agg.minutes += Math.max(0, Math.round(Number(s.actualMinutes) || 0));
      focus.set(k, agg);
    });
    focus.forEach(agg => {
      const m = byId.get(agg.missionId);
      events.push({ missionId: m.id, title: m.title, theme: themeOf(m.id), missionType: m.type, status: m.status, kind: 'focus', date: agg.date, count: agg.count, minutes: agg.minutes });
    });
    const byDate = new Map();
    events.forEach(e => {
      if (!byDate.has(e.date)) byDate.set(e.date, []);
      byDate.get(e.date).push(e);
    });
    const order = { deadline: 0, done: 1, start: 2, focus: 3 };
    byDate.forEach(list => list.sort((a, b) => order[a.kind] - order[b.kind] || String(a.title).localeCompare(String(b.title))));
    return { missions, byDate, events };
  }

  function escapeText(v) {
    return String(v ?? '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  }
  function locale() { return isEn() ? 'en-GB' : 'de-DE'; }
  function fmt(key, opts) {
    const dt = parseKey(key);
    if (!dt) return '';
    try { return new Intl.DateTimeFormat(locale(), opts).format(dt); } catch (_) { return key; }
  }
  function relative(key) {
    const d = dayDiff(todayKey(), key);
    if (d === 0) return t('heute', 'today');
    if (d === 1) return t('morgen', 'tomorrow');
    if (d === -1) return t('gestern', 'yesterday');
    return d > 0 ? t('in ' + d + ' Tagen', 'in ' + d + ' days') : t('vor ' + (-d) + ' Tagen', (-d) + ' days ago');
  }
  function deadlineLabel(type) {
    if (type === 'exam') return t('Prüfung', 'Exam');
    if (type === 'assignment') return t('Abgabe', 'Due date');
    return t('Frist', 'Deadline');
  }
  function minutesLabel(n) {
    if (typeof global.formatHistoryDuration === 'function' && !isEn()) return global.formatHistoryDuration(n);
    return n + (isEn() ? ' min' : ' Min.');
  }
  function eventCopy(e) {
    if (e.kind === 'deadline') return { chip: deadlineLabel(e.missionType), sub: relative(e.date) };
    if (e.kind === 'start') return { chip: t('Start', 'Start'), sub: t('Mission erstellt', 'Mission created') };
    if (e.kind === 'done') return { chip: t('Abgeschlossen', 'Completed'), sub: t('Mission abgeschlossen', 'Mission completed') };
    return { chip: t('Fokus', 'Focus'), sub: (e.count === 1 ? t('1 Sitzung', '1 session') : t(e.count + ' Sitzungen', e.count + ' sessions')) + ' · ' + minutesLabel(e.minutes) };
  }

  function nextDeadline(events) {
    const today = todayKey();
    return events.filter(e => e.kind === 'deadline' && e.status === 'active' && e.date >= today).sort((a, b) => a.date.localeCompare(b.date))[0] || null;
  }

  /* ------------------------------------------------------------------ render */
  function markersFor(list) {
    if (!list || !list.length) return '';
    const dots = [];
    const seen = new Set();
    list.forEach(e => {
      const k = e.missionId + e.kind;
      if (seen.has(k)) return;
      seen.add(k);
      dots.push('<i class="hc-mark is-' + e.kind + ' theme-' + e.theme + '"></i>');
    });
    const shown = dots.slice(0, 3).join('');
    const more = dots.length > 3 ? '<i class="hc-mark-more">+' + (dots.length - 3) + '</i>' : '';
    return shown || more ? '<span class="hc-marks" aria-hidden="true">' + shown + more + '</span>' : '';
  }
  function cellLabel(key, list) {
    const parts = [fmt(key, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })];
    if (key === todayKey()) parts.push(t('Heute', 'Today'));
    (list || []).forEach(e => { const c = eventCopy(e); parts.push(c.chip + ': ' + e.title); });
    return parts.join(', ');
  }

  function renderGrid(data) {
    const today = todayKey();
    const first = new Date(view.year, view.month, 1, 12);
    const lead = (first.getDay() + 6) % 7; // Monday first
    const days = new Date(view.year, view.month + 1, 0, 12).getDate();
    const prevDays = new Date(view.year, view.month, 0, 12).getDate();
    const total = Math.ceil((lead + days) / 7) * 7;
    let html = '';
    for (let i = 0; i < total; i++) {
      const day = i - lead + 1;
      if (day < 1 || day > days) {
        const outDay = day < 1 ? prevDays + day : day - days;
        html += '<span class="hc-day is-outside" aria-hidden="true"><span class="hc-num">' + outDay + '</span></span>';
        continue;
      }
      const key = keyOf(view.year, view.month, day);
      const list = data.byDate.get(key) || [];
      const deadlines = list.filter(e => e.kind === 'deadline');
      const cls = ['hc-day'];
      if (key === today) cls.push('is-today');
      if (key < today) cls.push('is-past');
      if (key === view.selected) cls.push('is-selected');
      if (deadlines.length) cls.push('has-deadline', 'theme-' + deadlines[0].theme);
      html += '<button type="button" class="' + cls.join(' ') + '" role="gridcell" data-date="' + key + '"'
        + ' aria-selected="' + (key === view.selected ? 'true' : 'false') + '"' + (key === today ? ' aria-current="date"' : '')
        + ' tabindex="' + (key === view.selected ? '0' : '-1') + '" aria-label="' + escapeText(cellLabel(key, list)) + '">'
        + '<span class="hc-num">' + day + '</span>' + markersFor(list) + '</button>';
    }
    return html;
  }

  function renderDetail(data) {
    const key = view.selected;
    const list = data.byDate.get(key) || [];
    const title = key === todayKey() ? t('Heute', 'Today') + ' · ' + fmt(key, { day: 'numeric', month: 'long' }) : fmt(key, { weekday: 'long', day: 'numeric', month: 'long' });
    let body;
    if (list.length) {
      body = '<ul class="hc-events">' + list.map(e => {
        const c = eventCopy(e);
        return '<li><button type="button" class="hc-event is-' + e.kind + ' theme-' + e.theme + '" data-mission-id="' + escapeText(e.missionId) + '">'
          + '<span class="hc-event-mark" aria-hidden="true"></span>'
          + '<span class="hc-event-copy"><span class="hc-event-title">' + escapeText(e.title) + '</span>'
          + '<span class="hc-event-sub"><b>' + escapeText(c.chip) + '</b> · ' + escapeText(c.sub) + '</span></span>'
          + '<i data-lucide="chevron-right" aria-hidden="true"></i></button></li>';
      }).join('') + '</ul>';
    } else {
      const next = nextDeadline(data.events);
      body = '<p class="hc-none">' + t('Keine Missionstermine an diesem Tag.', 'No Mission dates on this day.') + '</p>'
        + (next && next.date !== key ? '<button type="button" class="hc-jump" data-jump="' + next.date + '"><i data-lucide="flag" aria-hidden="true"></i>'
          + escapeText(t('Nächste Frist: ', 'Next deadline: ') + fmt(next.date, { day: 'numeric', month: 'short' }) + ' · ' + next.title) + '</button>' : '');
    }
    return '<div class="hc-detail-title">' + escapeText(title) + '</div>' + body;
  }

  function renderEmpty(host) {
    host.innerHTML = '<div class="hc-empty"><span class="hc-empty-icon" aria-hidden="true"><i data-lucide="calendar-days"></i></span>'
      + '<span class="hc-empty-copy"><strong>' + t('Noch keine Missionstermine', 'No Mission dates yet') + '</strong>'
      + '<span>' + t('Erstelle eine Mission mit Prüfungs- oder Abgabedatum, um sie hier zu sehen.', 'Create a Mission with an exam or due date to see it here.') + '</span></span>'
      + '<button type="button" class="hc-empty-action" data-action="create">' + t('Mission erstellen', 'Create Mission') + '</button></div>';
  }

  function render(force) {
    const host = doc.getElementById('homeMissionCalendar');
    if (!host) return;
    if (!view.year) {
      const now = new Date();
      view.year = now.getFullYear();
      view.month = now.getMonth();
      view.selected = todayKey();
    }
    const data = collectEvents();
    const sig = [doc.documentElement.lang, todayKey(), view.year, view.month, view.selected,
      data.events.map(e => e.missionId + e.kind + e.date + e.title + e.theme + e.status + (e.minutes || '')).join('|')].join('#');
    if (!force && sig === view.signature) return;
    view.signature = sig;
    const todayBtn = doc.getElementById('homeCalendarTodayBtn');
    const isCurrent = view.selected === todayKey() && view.year === new Date().getFullYear() && view.month === new Date().getMonth();
    if (todayBtn) todayBtn.classList.toggle('hidden', isCurrent || !data.missions.length);
    if (!data.missions.length) { host.dataset.state = 'empty'; renderEmpty(host); global.lucide?.createIcons?.(); return; }
    host.dataset.state = 'ready';
    const monthTitle = fmt(keyOf(view.year, view.month, 1), { month: 'long', year: 'numeric' });
    const next = nextDeadline(data.events);
    const wd = isEn() ? ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
    const motion = view.motion;
    view.motion = '';
    host.innerHTML = '<div class="hc-head">'
      + '<div class="hc-head-copy"><h4 class="hc-month" id="homeCalendarMonth" aria-live="polite">' + escapeText(monthTitle) + '</h4>'
      + '<span class="hc-next">' + (next ? escapeText(deadlineLabel(next.missionType) + ' · ' + next.title + ' · ' + relative(next.date)) : escapeText(t('Keine anstehende Frist', 'No upcoming deadline'))) + '</span></div>'
      + '<div class="hc-nav"><button type="button" class="hc-nav-btn" data-step="-1" aria-label="' + t('Vorheriger Monat', 'Previous month') + '"><i data-lucide="chevron-left"></i></button>'
      + '<button type="button" class="hc-nav-btn" data-step="1" aria-label="' + t('Nächster Monat', 'Next month') + '"><i data-lucide="chevron-right"></i></button></div></div>'
      + '<div class="hc-weekdays" aria-hidden="true">' + wd.map(d => '<span>' + d + '</span>').join('') + '</div>'
      + '<div class="hc-grid' + (motion ? ' is-enter-' + motion : '') + '" role="grid" aria-labelledby="homeCalendarMonth">' + renderGrid(data) + '</div>'
      + '<div class="hc-legend" aria-hidden="true"><span><i class="hc-key is-deadline"></i><i class="hc-key hc-key-diamond"></i>' + t('Frist', 'Deadline') + '</span><span><i class="hc-key is-start"></i>' + t('Start', 'Start') + '</span><span><i class="hc-key is-focus"></i>' + t('Fokus-Sitzung', 'Focus session') + '</span></div>'
      + '<div class="hc-detail" aria-live="polite">' + renderDetail(data) + '</div>';
    global.lucide?.createIcons?.();
  }

  /* ------------------------------------------------------------------ interaction */
  function shiftMonth(step) {
    const dt = new Date(view.year, view.month + step, 1, 12);
    view.year = dt.getFullYear();
    view.month = dt.getMonth();
    const today = todayKey();
    const td = parseKey(today);
    view.selected = td.getFullYear() === view.year && td.getMonth() === view.month ? today : keyOf(view.year, view.month, 1);
    view.motion = step > 0 ? 'next' : 'prev';
    render(true);
  }
  function selectDate(key, focusCell) {
    const dt = parseKey(key);
    if (!dt) return;
    const monthChanged = dt.getFullYear() !== view.year || dt.getMonth() !== view.month;
    if (monthChanged) view.motion = (dt.getFullYear() * 12 + dt.getMonth()) > (view.year * 12 + view.month) ? 'next' : 'prev';
    view.year = dt.getFullYear();
    view.month = dt.getMonth();
    view.selected = key;
    render(true);
    if (focusCell) doc.querySelector('#homeMissionCalendar .hc-day[data-date="' + key + '"]')?.focus({ preventScroll: true });
  }
  function goToday() { selectDate(todayKey(), false); }

  function bind() {
    const host = doc.getElementById('homeMissionCalendar');
    if (!host || host.__hcBound) return;
    host.__hcBound = true;
    host.addEventListener('click', e => {
      if (host.__hcSwiped) { host.__hcSwiped = false; return; }
      const nav = e.target.closest('.hc-nav-btn');
      if (nav) { shiftMonth(Number(nav.dataset.step) || 0); return; }
      const day = e.target.closest('.hc-day[data-date]');
      if (day) { selectDate(day.dataset.date, false); return; }
      const ev = e.target.closest('.hc-event[data-mission-id]');
      if (ev) { if (typeof global.openMission === 'function') global.openMission(ev.dataset.missionId, 'home'); return; }
      const jump = e.target.closest('.hc-jump[data-jump]');
      if (jump) { selectDate(jump.dataset.jump, false); return; }
      if (e.target.closest('[data-action="create"]') && typeof global.openGoalIntake === 'function') global.openGoalIntake();
    });
    host.addEventListener('keydown', e => {
      const day = e.target.closest('.hc-day[data-date]');
      if (!day) return;
      const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key];
      if (!delta) return;
      e.preventDefault();
      const dt = parseKey(day.dataset.date);
      dt.setDate(dt.getDate() + delta);
      selectDate(keyOfDate(dt), true);
    });
    /* Horizontal swipe on the grid changes the month. The calendar is marked
       data-block-swipe-back, so root swipe never starts here; vertical scroll stays native. */
    let sx = 0, sy = 0, sid = null;
    host.addEventListener('pointerdown', e => {
      if (!e.target.closest('.hc-grid') || e.pointerType === 'mouse') return;
      sid = e.pointerId; sx = e.clientX; sy = e.clientY;
    }, { passive: true });
    const finish = e => {
      if (e.pointerId !== sid) return;
      sid = null;
      if (e.type !== 'pointerup') return;
      const dx = e.clientX - sx, dy = e.clientY - sy;
      if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.6) { host.__hcSwiped = true; setTimeout(() => { host.__hcSwiped = false; }, 350); shiftMonth(dx < 0 ? 1 : -1); }
    };
    host.addEventListener('pointerup', finish, { passive: true });
    host.addEventListener('pointercancel', finish, { passive: true });
    doc.getElementById('homeCalendarTodayBtn')?.addEventListener('click', goToday);
  }

  function refresh() { bind(); render(false); }

  /* I18N lives in bootstrap.js, which loads later; register the static strings on DOMContentLoaded. */
  function registerStrings() {
    if (typeof I18N === 'object' && I18N.en) Object.assign(I18N.en, { 'MISSIONSKALENDER': 'MISSION CALENDAR', 'Zum heutigen Tag': 'Back to today' });
  }

  /* Recompute only through the existing Home and Mission refresh points (setLanguage already
     calls refreshHomeStats); render() skips unchanged data, so frequent Home refreshes do not
     rebuild the grid. */
  function wrap(name) {
    const prev = global[name];
    if (typeof prev !== 'function' || prev.__hcWrapped) return;
    const next = function () { const r = prev.apply(this, arguments); try { refresh(); } catch (err) { console.warn('[MissionCalendar]', err?.message); } return r; };
    next.__hcWrapped = true;
    global[name] = next;
  }
  wrap('refreshHomeStats');
  wrap('renderMultiMissionUIV115');
  doc.addEventListener('DOMContentLoaded', () => { registerStrings(); refresh(); });

  global.MissionCalendar = Object.freeze({ refresh, parseDeadlineText, collectEvents, selectDate, shiftMonth, getView: () => ({ ...view }) });
})(window);
