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
 *
 * v17.16: the same owner also renders the Mission Creation planning calendar (#mgPlannerCalendar).
 * It reuses this file's month generation, date keys, locale and event derivation, shows the
 * existing active Missions' deadlines and starts next to the new Mission's chosen dates, and writes
 * a chosen date only through the Zeitplan owner in mission-renderer.js
 * (selectMissionDeadlineDate / selectMissionPreparationStartDate). It stores nothing itself.
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
    if (e.kind === 'start') return { chip: t('Start', 'Start date'), sub: t('Mission erstellt', 'Mission created') };
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

  /* Shared month model (Monday first): whole weeks, leading and trailing days marked outside. */
  function monthCells(year, month) {
    const first = new Date(year, month, 1, 12);
    const lead = (first.getDay() + 6) % 7;
    const days = new Date(year, month + 1, 0, 12).getDate();
    const prevDays = new Date(year, month, 0, 12).getDate();
    const total = Math.ceil((lead + days) / 7) * 7;
    const cells = [];
    for (let i = 0; i < total; i++) {
      const day = i - lead + 1;
      if (day < 1 || day > days) cells.push({ outside: true, day: day < 1 ? prevDays + day : day - days });
      else cells.push({ outside: false, day: day, key: keyOf(year, month, day) });
    }
    return cells;
  }
  function outsideCell(day) { return '<span class="hc-day is-outside" aria-hidden="true"><span class="hc-num">' + day + '</span></span>'; }
  function weekdayLabels() { return isEn() ? ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']; }
  function navHTML(prevDisabled) {
    return '<div class="hc-nav"><button type="button" class="hc-nav-btn" data-step="-1" aria-label="' + t('Vorheriger Monat', 'Previous month') + '"' + (prevDisabled ? ' disabled' : '') + '><i data-lucide="chevron-left"></i></button>'
      + '<button type="button" class="hc-nav-btn" data-step="1" aria-label="' + t('Nächster Monat', 'Next month') + '"><i data-lucide="chevron-right"></i></button></div>';
  }

  function renderGrid(data) {
    const today = todayKey();
    let html = '';
    monthCells(view.year, view.month).forEach(cell => {
      if (cell.outside) { html += outsideCell(cell.day); return; }
      const day = cell.day, key = cell.key;
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
    });
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

  /* v17.16: a quiet, centred note. Home's Mission card already owns the create action. */
  function renderEmpty(host) {
    host.innerHTML = '<div class="hc-empty" role="note"><span class="hc-empty-icon" aria-hidden="true"><i data-lucide="calendar-days"></i></span>'
      + '<strong class="hc-empty-title">' + t('Noch keine Missionstermine', 'No Mission dates yet') + '</strong>'
      + '<span class="hc-empty-text">' + t('Erstelle eine Mission mit Deadline, um Termine hier zu sehen.', 'Create a Mission with a deadline to see its dates here.') + '</span></div>';
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
    const wd = weekdayLabels();
    const motion = view.motion;
    view.motion = '';
    host.innerHTML = '<div class="hc-head">'
      + '<div class="hc-head-copy"><h4 class="hc-month" id="homeCalendarMonth" aria-live="polite">' + escapeText(monthTitle) + '</h4>'
      + '<span class="hc-next">' + (next ? escapeText(deadlineLabel(next.missionType) + ' · ' + next.title + ' · ' + relative(next.date)) : escapeText(t('Keine anstehende Frist', 'No upcoming deadline'))) + '</span></div>'
      + navHTML(false) + '</div>'
      + '<div class="hc-weekdays" aria-hidden="true">' + wd.map(d => '<span>' + d + '</span>').join('') + '</div>'
      + '<div class="hc-grid' + (motion ? ' is-enter-' + motion : '') + '" role="grid" aria-labelledby="homeCalendarMonth">' + renderGrid(data) + '</div>'
      + '<div class="hc-legend" aria-hidden="true"><span><i class="hc-key is-deadline"></i><i class="hc-key hc-key-diamond"></i>' + t('Frist', 'Deadline') + '</span><span><i class="hc-key is-start"></i>' + t('Start', 'Start date') + '</span><span><i class="hc-key is-focus"></i>' + t('Fokus-Sitzung', 'Focus session') + '</span></div>'
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
    bindMonthSwipe(host, shiftMonth);
    doc.getElementById('homeCalendarTodayBtn')?.addEventListener('click', goToday);
  }

  /* Horizontal swipe on a grid changes the month. Both calendars are marked
     data-block-swipe-back, so root and edge swipe never start here; vertical scroll stays native. */
  function bindMonthSwipe(host, onShift) {
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
      if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.6) { host.__hcSwiped = true; setTimeout(() => { host.__hcSwiped = false; }, 350); onShift(dx < 0 ? 1 : -1); }
    };
    host.addEventListener('pointerup', finish, { passive: true });
    host.addEventListener('pointercancel', finish, { passive: true });
  }

  /* ------------------------------------------------------------------ Mission Creation planner */
  /* View state only. The chosen dates live in the Zeitplan state (mission-renderer.js). */
  const planner = { year: 0, month: 0, target: 'deadline', expanded: true, inspect: null, motion: '', focusAfter: false };

  function schedule() {
    try { return typeof global.getMissionScheduleState === 'function' ? global.getMissionScheduleState() : null; } catch (_) { return null; }
  }
  /* Existing commitments: active Missions' deadlines and starts, plus completion days for context. */
  function plannerEvents() {
    const data = collectEvents();
    const byDate = new Map();
    data.events.forEach(e => {
      const keep = (e.status === 'active' && (e.kind === 'deadline' || e.kind === 'start')) || e.kind === 'done';
      if (!keep) return;
      if (!byDate.has(e.date)) byDate.set(e.date, []);
      byDate.get(e.date).push(e);
    });
    const order = { deadline: 0, start: 1, done: 2 };
    byDate.forEach(list => list.sort((a, b) => order[a.kind] - order[b.kind] || String(a.title).localeCompare(String(b.title))));
    return { byDate, deadlines: data.events.filter(e => e.kind === 'deadline' && e.status === 'active') };
  }
  function plannerCopy(e) {
    if (e.kind === 'deadline') return { chip: deadlineLabel(e.missionType), sub: relative(e.date) };
    if (e.kind === 'start') return { chip: t('Start', 'Start date'), sub: t('Mission erstellt', 'Mission created') };
    return { chip: t('Abgeschlossen', 'Completed'), sub: t('Mission abgeschlossen', 'Mission completed') };
  }
  function selectableFor(key, sch) {
    const today = todayKey();
    if (!key || key < today) return false;
    if (planner.target === 'start') return Boolean(sch && sch.deadlineDate && key <= sch.deadlineDate);
    return true;
  }
  function plannerCellLabel(key, list, isNewDeadline, isNewStart, selectable) {
    const parts = [fmt(key, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })];
    if (key === todayKey()) parts.push(t('Heute', 'Today'));
    if (isNewDeadline) parts.push(t('Frist der neuen Mission', 'New Mission deadline'));
    if (isNewStart) parts.push(t('Vorbereitungsstart der neuen Mission', 'New Mission preparation start'));
    (list || []).forEach(e => { const c = plannerCopy(e); parts.push(c.chip + ': ' + e.title); });
    if (!selectable && !(list && list.length)) parts.push(t('nicht wählbar', 'not available'));
    return parts.join(', ');
  }
  function plannerCursor(sch, ev) {
    const inMonth = k => { const d = parseKey(k); return d && d.getFullYear() === planner.year && d.getMonth() === planner.month; };
    const pref = [planner.inspect, planner.target === 'start' ? sch?.preparationStartDate : sch?.deadlineDate, todayKey()];
    for (const k of pref) if (k && inMonth(k)) return k;
    const cells = monthCells(planner.year, planner.month).filter(c => !c.outside);
    const first = cells.find(c => selectableFor(c.key, sch) || ev.byDate.has(c.key));
    return (first || cells[0]).key;
  }
  function renderPlannerGrid(sch, ev, cursor) {
    const today = todayKey();
    const end = sch?.deadlineDate || null;
    const start = end ? (sch.preparationStartDate || today) : null;
    let html = '';
    monthCells(planner.year, planner.month).forEach(cell => {
      if (cell.outside) { html += outsideCell(cell.day); return; }
      const key = cell.key;
      const list = ev.byDate.get(key) || [];
      const deadlines = list.filter(e => e.kind === 'deadline');
      const isNewDeadline = key === end;
      const isNewStart = Boolean(start && key === start && start !== end);
      const selectable = selectableFor(key, sch);
      const cls = ['hc-day'];
      if (key === today) cls.push('is-today');
      if (key < today) cls.push('is-past');
      if (!selectable && !list.length) cls.push('is-blocked');
      if (deadlines.length) cls.push('has-deadline', 'theme-' + deadlines[0].theme);
      if (isNewDeadline) cls.push('is-new-deadline');
      if (isNewStart) cls.push('is-new-start');
      if (start && end && key > start && key < end) cls.push('is-in-range');
      if (start && end && start !== end && key === start) cls.push('is-range-start');
      if (start && end && start !== end && key === end) cls.push('is-range-end');
      if (key === planner.inspect) cls.push('is-inspected');
      const chosen = planner.target === 'start' ? isNewStart || (isNewDeadline && start === end) : isNewDeadline;
      html += '<button type="button" class="' + cls.join(' ') + '" role="gridcell" data-date="' + key + '"'
        + ' aria-selected="' + (chosen ? 'true' : 'false') + '"' + (key === today ? ' aria-current="date"' : '')
        + (!selectable && !list.length ? ' aria-disabled="true"' : '')
        + ' tabindex="' + (key === cursor ? '0' : '-1') + '" aria-label="' + escapeText(plannerCellLabel(key, list, isNewDeadline, isNewStart, selectable)) + '">'
        + '<span class="hc-num">' + cell.day + '</span>' + markersFor(list) + '</button>'; // existing Mission dates; the new Mission's dates are drawn on the number
    });
    return html;
  }
  function plannerRow(kind, theme, title, chip, sub) {
    return '<li class="hc-event hcp-row is-' + kind + (theme ? ' theme-' + theme : '') + '">'
      + '<span class="hc-event-mark" aria-hidden="true"></span>'
      + '<span class="hc-event-copy"><span class="hc-event-title">' + escapeText(title) + '</span>'
      + '<span class="hc-event-sub"><b>' + escapeText(chip) + '</b> · ' + escapeText(sub) + '</span></span></li>';
  }
  function renderPlannerDetail(sch, ev) {
    const end = sch?.deadlineDate || null;
    const start = end ? (sch.preparationStartDate || todayKey()) : null;
    const key = planner.inspect || (planner.target === 'start' ? start : end);
    if (!key) {
      return '<p class="hcp-hint">' + escapeText(t('Tippe auf einen Tag, um die Frist festzulegen.', 'Tap a day to set the deadline.')) + '</p>';
    }
    const list = ev.byDate.get(key) || [];
    const rows = [];
    const newTitle = t('Neue Mission', 'New Mission');
    if (key === end) rows.push(plannerRow('new-deadline', '', newTitle, t('Frist', 'Deadline'), relative(key)));
    if (start && key === start && start !== end) rows.push(plannerRow('new-start', '', newTitle, t('Vorbereitung', 'Preparation'), t('Start', 'Start date')));
    list.forEach(e => { const c = plannerCopy(e); rows.push(plannerRow(e.kind, e.theme, e.title, c.chip, c.sub)); });
    const head = '<div class="hcp-detail-head"><span class="hc-detail-title">' + escapeText(key === todayKey() ? t('Heute', 'Today') + ' · ' + fmt(key, { day: 'numeric', month: 'long' }) : fmt(key, { weekday: 'long', day: 'numeric', month: 'long' })) + '</span></div>';
    const body = rows.length ? '<ul class="hc-events">' + rows.join('') + '</ul>' : '<p class="hc-none">' + escapeText(t('Keine Missionstermine an diesem Tag.', 'No Mission dates on this day.')) + '</p>';
    const current = planner.target === 'start' ? start : end;
    const pick = selectableFor(key, sch) && key !== current
      ? '<button type="button" class="hcp-pick" data-pick="' + key + '"><i data-lucide="' + (planner.target === 'start' ? 'calendar-plus' : 'flag') + '" aria-hidden="true"></i>'
        + escapeText(planner.target === 'start' ? t('Als Vorbereitungsstart wählen', 'Set as preparation start') : t('Als Frist wählen', 'Set as deadline')) + '</button>'
      : '';
    return head + body + pick;
  }
  /* The disclosure row above the calendar: the deadline summary and whether it meets existing work. */
  function syncPlannerSummary(sch, ev) {
    const trigger = doc.getElementById('mgDeadlineTrigger');
    if (trigger) trigger.setAttribute('aria-expanded', planner.expanded ? 'true' : 'false');
    const sub = doc.getElementById('mgDeadlineSub');
    if (!sub) return;
    const end = sch?.deadlineDate || null;
    let text = '';
    if (end) {
      const start = sch.preparationStartDate || todayKey();
      const same = ev.deadlines.filter(e => e.date === end);
      const within = ev.deadlines.filter(e => e.date >= start && e.date < end);
      text = relative(end);
      if (same.length) text += ' · ' + t('Gleicher Tag wie „', 'Same day as “') + same[0].title + t('“', '”');
      else if (within.length === 1) text += ' · ' + t('1 weitere Frist im Zeitraum', '1 other deadline in this period');
      else if (within.length > 1) text += ' · ' + within.length + t(' weitere Fristen im Zeitraum', ' other deadlines in this period');
    } else if (!planner.expanded) {
      text = t('Kalender öffnen', 'Open calendar');
    }
    if (sub.textContent !== text) sub.textContent = text;
    sub.classList.toggle('hidden', !text);
  }
  function renderPlanner() {
    const host = doc.getElementById('mgPlannerCalendar');
    if (!host) return;
    if (!planner.year) { const now = new Date(); planner.year = now.getFullYear(); planner.month = now.getMonth(); }
    const sch = schedule();
    if (planner.target === 'start' && !sch?.deadlineDate) planner.target = 'deadline';
    const ev = plannerEvents();
    syncPlannerSummary(sch, ev);
    host.hidden = !planner.expanded;
    host.dataset.target = planner.target;
    if (!planner.expanded) { host.replaceChildren(); return; }
    const now = new Date();
    const prevDisabled = planner.year < now.getFullYear() || (planner.year === now.getFullYear() && planner.month <= now.getMonth());
    const cursor = plannerCursor(sch, ev);
    const motion = planner.motion;
    planner.motion = '';
    const mode = planner.target === 'start'
      ? '<div class="hcp-mode"><i data-lucide="calendar-plus" aria-hidden="true"></i><span>' + escapeText(t('Wähle den Start deiner Vorbereitung.', 'Choose when your preparation starts.')) + '</span>'
        + '<button type="button" class="hcp-mode-done" data-action="done">' + escapeText(t('Fertig', 'Done')) + '</button></div>'
      : '';
    host.innerHTML = mode
      + '<div class="hc-head"><div class="hc-head-copy"><h5 class="hc-month" id="mgPlannerMonth" aria-live="polite">' + escapeText(fmt(keyOf(planner.year, planner.month, 1), { month: 'long', year: 'numeric' })) + '</h5></div>'
      + navHTML(prevDisabled) + '</div>'
      + '<div class="hc-weekdays" aria-hidden="true">' + weekdayLabels().map(d => '<span>' + d + '</span>').join('') + '</div>'
      + '<div class="hc-grid' + (motion ? ' is-enter-' + motion : '') + '" role="grid" aria-labelledby="mgPlannerMonth">' + renderPlannerGrid(sch, ev, cursor) + '</div>'
      + '<div class="hcp-legend" aria-hidden="true"><span><i class="hc-key is-new-deadline"></i>' + t('Neue Frist', 'New deadline') + '</span>'
      + '<span><i class="hc-key is-deadline"></i><i class="hc-key hc-key-diamond"></i>' + t('Mission-Frist', 'Mission deadline') + '</span>'
      + '<span><i class="hc-key is-start"></i>' + t('Start', 'Start date') + '</span></div>'
      + '<div class="hc-detail hcp-detail" aria-live="polite">' + renderPlannerDetail(sch, ev) + '</div>';
    global.lucide?.createIcons?.();
    if (planner.focusAfter) {
      planner.focusAfter = false;
      host.querySelector('.hc-day[tabindex="0"]')?.focus({ preventScroll: true });
    }
  }
  function showPlannerMonth(key) {
    const dt = parseKey(key);
    if (!dt) return;
    const next = dt.getFullYear() * 12 + dt.getMonth(), cur = planner.year * 12 + planner.month;
    if (next !== cur) planner.motion = next > cur ? 'next' : 'prev';
    planner.year = dt.getFullYear();
    planner.month = dt.getMonth();
  }
  function shiftPlannerMonth(step) {
    const dt = new Date(planner.year, planner.month + step, 1, 12);
    const now = new Date();
    if (dt.getFullYear() * 12 + dt.getMonth() < now.getFullYear() * 12 + now.getMonth()) return;
    planner.year = dt.getFullYear();
    planner.month = dt.getMonth();
    planner.inspect = null;
    planner.motion = step > 0 ? 'next' : 'prev';
    renderPlanner();
  }
  /* Writes go through the Zeitplan owner, which re-renders this view via syncMissionScheduleUI. */
  function pickPlannerDate(key) {
    const sch = schedule();
    if (!selectableFor(key, sch)) return false;
    const target = planner.target;
    planner.inspect = null;
    planner.target = 'deadline';
    planner.expanded = false;
    const fn = target === 'start' ? global.selectMissionPreparationStartDate : global.selectMissionDeadlineDate;
    const ok = typeof fn === 'function' ? fn(key) !== false : false;
    if (!ok) { planner.expanded = true; planner.target = target; }
    renderPlanner();
    if (ok) doc.getElementById('mgDeadlineTrigger')?.focus({ preventScroll: true });
    return ok;
  }
  function bindPlanner() {
    const host = doc.getElementById('mgPlannerCalendar');
    if (!host || host.__hcBound) return;
    host.__hcBound = true;
    host.addEventListener('click', e => {
      if (host.__hcSwiped) { host.__hcSwiped = false; return; }
      const nav = e.target.closest('.hc-nav-btn');
      if (nav) { if (!nav.disabled) shiftPlannerMonth(Number(nav.dataset.step) || 0); return; }
      if (e.target.closest('[data-action="done"]')) { planner.target = 'deadline'; planner.inspect = null; planner.expanded = false; renderPlanner(); doc.getElementById('mgDeadlineTrigger')?.focus({ preventScroll: true }); return; }
      const pick = e.target.closest('.hcp-pick[data-pick]');
      if (pick) { pickPlannerDate(pick.dataset.pick); return; }
      const day = e.target.closest('.hc-day[data-date]');
      if (!day) return;
      const key = day.dataset.date;
      const sch = schedule();
      const hasEvents = plannerEvents().byDate.has(key);
      if (!hasEvents && !selectableFor(key, sch)) return;
      /* A day that already holds Mission dates opens its detail first; it is never chosen by accident. */
      if (hasEvents) {
        planner.inspect = key;
        renderPlanner();
        host.querySelector('.hc-day[data-date="' + key + '"]')?.focus({ preventScroll: true });
        return;
      }
      pickPlannerDate(key);
    });
    host.addEventListener('keydown', e => {
      const day = e.target.closest('.hc-day[data-date]');
      if (!day) return;
      const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key];
      if (!delta) return;
      e.preventDefault();
      const dt = parseKey(day.dataset.date);
      dt.setDate(dt.getDate() + delta);
      const key = keyOfDate(dt);
      if (key < keyOf(new Date().getFullYear(), new Date().getMonth(), 1)) return;
      showPlannerMonth(key);
      planner.inspect = key;
      planner.focusAfter = true;
      renderPlanner();
    });
    bindMonthSwipe(host, shiftPlannerMonth);
  }
  function togglePlanner(force) {
    planner.expanded = typeof force === 'boolean' ? force : !planner.expanded;
    if (planner.expanded) {
      const sch = schedule();
      showPlannerMonth((planner.target === 'start' ? sch?.preparationStartDate : sch?.deadlineDate) || todayKey());
      planner.motion = '';
    } else {
      planner.inspect = null;
      planner.target = 'deadline';
    }
    renderPlanner();
  }
  /* Opens the planner to choose the preparation start (Zeitplan "Eigene Wahl"). */
  function openPlanner(target) {
    planner.target = target === 'start' ? 'start' : 'deadline';
    planner.inspect = null;
    planner.expanded = true;
    const sch = schedule();
    showPlannerMonth((planner.target === 'start' ? sch?.preparationStartDate : sch?.deadlineDate) || todayKey());
    planner.motion = '';
    renderPlanner();
    doc.getElementById('mgPlannerCalendar')?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }
  function resetPlanner() {
    const now = new Date();
    planner.year = now.getFullYear();
    planner.month = now.getMonth();
    planner.target = 'deadline';
    planner.expanded = true;
    planner.inspect = null;
    planner.motion = '';
    renderPlanner();
  }
  function refreshPlanner() { bindPlanner(); renderPlanner(); }

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

  global.MissionCalendar = Object.freeze({
    refresh, parseDeadlineText, collectEvents, selectDate, shiftMonth, getView: () => ({ ...view }),
    refreshPlanner, togglePlanner, openPlanner, resetPlanner, shiftPlannerMonth, getPlannerView: () => ({ ...planner })
  });
  global.toggleMissionPlannerCalendar = function () { togglePlanner(); };
})(window);
