/**
 * Physical module: src/features/history/focus-insights.js
 * Shared real-data Focus aggregation and the single Focus chart renderer used by
 * Verlauf (history) and Fortschritt (stats). Reads Session History through
 * sessionRepository only; never writes, never synthesizes sessions.
 */
(function (global) {
  'use strict';

  const WEEKDAY_SHORT = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
  const WEEKDAY_LONG = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
  const MONTH_SHORT = ['Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni', 'Juli', 'Aug.', 'Sept.', 'Okt.', 'Nov.', 'Dez.'];
  const RANGES = Object.freeze({
    '7d': { label: '7 Tage', buckets: 7, unit: 'day', periodLabel: 'Letzte 7 Tage', prevLabel: 'vorherigen 7 Tagen' },
    '4w': { label: '4 Wochen', buckets: 4, unit: 'week', periodLabel: 'Letzte 4 Wochen', prevLabel: 'vorherigen 4 Wochen' }
  });
  const SVG_NS = 'http://www.w3.org/2000/svg';

  /* ---------- date helpers (local time, DST-safe) ---------- */
  function startOfDay(ts) {
    const d = new Date(ts);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }
  function addDays(ts, n) {
    const d = new Date(ts);
    d.setDate(d.getDate() + n);
    return d.getTime();
  }
  function startOfWeek(ts) {
    const d = new Date(startOfDay(ts));
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    return d.getTime();
  }
  function isoWeekNumber(ts) {
    const d = new Date(startOfDay(ts));
    d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7));
    const firstThursday = new Date(d.getFullYear(), 0, 4);
    return 1 + Math.round(((d - firstThursday) / 86400000 - 3 + ((firstThursday.getDay() + 6) % 7)) / 7);
  }
  function formatDayShort(ts) {
    const d = new Date(ts);
    return WEEKDAY_SHORT[d.getDay()] + '., ' + d.getDate() + '. ' + MONTH_SHORT[d.getMonth()];
  }
  function formatDayLong(ts) {
    const d = new Date(ts);
    return WEEKDAY_LONG[d.getDay()] + ', ' + d.getDate() + '. ' + MONTH_SHORT[d.getMonth()];
  }
  function formatWeekRange(start) {
    const end = new Date(addDays(start, 6));
    const s = new Date(start);
    return 'KW ' + isoWeekNumber(start) + ' · ' + s.getDate() + '.' + (s.getMonth() + 1) + '.–' + end.getDate() + '.' + (end.getMonth() + 1) + '.';
  }

  /* ---------- data access (single source: sessionRepository) ---------- */
  function loadSessions() {
    try {
      return typeof sessionRepository !== 'undefined' ? sessionRepository.getAll() : [];
    } catch (_) {
      return [];
    }
  }
  function isCounted(s) {
    return typeof countedFocus === 'function' ? countedFocus(s) : Number(s.actualMinutes) >= 1 && s.status === 'completed';
  }
  function minutesOf(s) {
    return Math.max(0, Math.round(Number(s.actualMinutes) || 0));
  }
  function sessionTime(s) {
    return Number(s.finishedAt) || Number(s.startedAt) || 0;
  }
  function formatDuration(m) {
    if (typeof formatHistoryDuration === 'function') return formatHistoryDuration(m);
    return Math.round(m) + ' Min.';
  }
  function outcomeKey(s) {
    if (typeof classifySessionOutcomeV173 === 'function') return classifySessionOutcomeV173(s).key;
    return s.status === 'completed' ? 'completed' : 'aborted';
  }

  function emptyBucket(start, end) {
    return { start: start, end: end, total: 0, quick: 0, mission: 0, count: 0, quickCount: 0, missionCount: 0 };
  }
  function addToBucket(bucket, s) {
    const m = minutesOf(s);
    bucket.total += m;
    bucket.count += 1;
    if (s.type === 'mission') {
      bucket.mission += m;
      bucket.missionCount += 1;
    } else {
      bucket.quick += m;
      bucket.quickCount += 1;
    }
  }

  /* Buckets for the chart: 7 days ending today, or 4 calendar weeks ending this week. */
  function buildSeries(rangeKey, sessions, nowTs) {
    const range = RANGES[rangeKey] ? rangeKey : '7d';
    const cfg = RANGES[range];
    const now = nowTs || Date.now();
    const counted = (sessions || loadSessions()).filter(isCounted);
    const buckets = [];
    const step = cfg.unit === 'week' ? 7 : 1;
    const lastStart = cfg.unit === 'week' ? startOfWeek(now) : startOfDay(now);
    const firstStart = addDays(lastStart, -step * (cfg.buckets - 1));
    for (let i = 0; i < cfg.buckets; i++) {
      const start = addDays(firstStart, step * i);
      buckets.push(emptyBucket(start, addDays(start, step)));
    }
    const prevStart = addDays(firstStart, -step * cfg.buckets);
    const rangeEnd = buckets[buckets.length - 1].end;
    let prevTotal = 0;
    counted.forEach(function (s) {
      const t = sessionTime(s);
      if (t >= firstStart && t < rangeEnd) {
        for (let i = 0; i < buckets.length; i++) {
          if (t >= buckets[i].start && t < buckets[i].end) {
            addToBucket(buckets[i], s);
            break;
          }
        }
      } else if (t >= prevStart && t < firstStart) {
        prevTotal += minutesOf(s);
      }
    });
    let bestIndex = -1;
    buckets.forEach(function (b, i) {
      if (b.total > 0 && (bestIndex === -1 || b.total > buckets[bestIndex].total)) bestIndex = i;
    });
    const sum = function (key) {
      return buckets.reduce(function (n, b) { return n + b[key]; }, 0);
    };
    return {
      range: range,
      config: cfg,
      buckets: buckets,
      total: sum('total'),
      quick: sum('quick'),
      mission: sum('mission'),
      count: sum('count'),
      prevTotal: prevTotal,
      bestIndex: bestIndex,
      activeBuckets: buckets.filter(function (b) { return b.total > 0; }).length
    };
  }

  /* Long-term interpretation for Fortschritt. Every value is derived from stored sessions. */
  function computeInsights(sessions, nowTs) {
    const all = sessions || loadSessions();
    const now = nowTs || Date.now();
    const counted = all.filter(isCounted);
    const dayTotals = new Map();
    const weekTotals = new Map();
    let totalMinutes = 0;
    let longest = null;
    const quick = { count: 0, minutes: 0 };
    const mission = { count: 0, minutes: 0 };
    counted.forEach(function (s) {
      const m = minutesOf(s);
      const t = sessionTime(s);
      totalMinutes += m;
      const day = startOfDay(t);
      dayTotals.set(day, (dayTotals.get(day) || 0) + m);
      const week = startOfWeek(t);
      weekTotals.set(week, (weekTotals.get(week) || 0) + m);
      if (!longest || m > longest.minutes) longest = { minutes: m, at: t, type: s.type };
      const bucket = s.type === 'mission' ? mission : quick;
      bucket.count += 1;
      bucket.minutes += m;
    });

    const pickMax = function (map) {
      let best = null;
      map.forEach(function (value, key) {
        if (!best || value > best.minutes) best = { start: key, minutes: value };
      });
      return best;
    };

    const today = startOfDay(now);
    let current = 0;
    let cursor = dayTotals.has(today) ? today : addDays(today, -1);
    while (dayTotals.has(cursor)) {
      current += 1;
      cursor = addDays(cursor, -1);
    }
    let bestStreak = 0;
    const sortedDays = Array.from(dayTotals.keys()).sort(function (a, b) { return a - b; });
    let run = 0;
    let prev = null;
    let hasComeback = false;
    sortedDays.forEach(function (d) {
      run = prev !== null && addDays(prev, 1) === d ? run + 1 : 1;
      bestStreak = Math.max(bestStreak, run);
      if (prev !== null && d >= addDays(prev, 8)) hasComeback = true;
      prev = d;
    });

    const recentDays = [];
    for (let i = 27; i >= 0; i--) {
      const start = addDays(today, -i);
      recentDays.push({ start: start, minutes: dayTotals.get(start) || 0, isToday: i === 0 });
    }

    const completedOutcomes = all.filter(function (s) { return outcomeKey(s) === 'completed'; }).length;

    return {
      sessionCount: all.length,
      countedCount: counted.length,
      totalMinutes: totalMinutes,
      averageMinutes: counted.length ? Math.round(totalMinutes / counted.length) : 0,
      completionRate: all.length ? Math.round((completedOutcomes / all.length) * 100) : null,
      completedOutcomes: completedOutcomes,
      todayMinutes: dayTotals.get(today) || 0,
      bestDay: pickMax(dayTotals),
      bestWeek: pickMax(weekTotals),
      longestSession: longest,
      streak: { current: current, best: bestStreak, todayActive: dayTotals.has(today) },
      recentDays: recentDays,
      activeDays28: recentDays.filter(function (d) { return d.minutes > 0; }).length,
      hasComeback: hasComeback,
      quick: quick,
      mission: mission
    };
  }

  /* ---------- shared chart renderer ---------- */
  const mountedCharts = new Set();
  const chartStates = new WeakMap();

  function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function svgEl(tag, attrs) {
    const node = document.createElementNS(SVG_NS, tag);
    Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, attrs[k]); });
    return node;
  }
  function roundedTopPath(x, y, w, h, r) {
    const rr = Math.max(0, Math.min(r, w / 2, h));
    return 'M' + x + ',' + (y + h) + 'V' + (y + rr) + 'Q' + x + ',' + y + ' ' + (x + rr) + ',' + y +
      'H' + (x + w - rr) + 'Q' + (x + w) + ',' + y + ' ' + (x + w) + ',' + (y + rr) + 'V' + (y + h) + 'Z';
  }
  function bucketLabel(series, b, i) {
    if (series.config.unit === 'week') return 'KW ' + isoWeekNumber(b.start);
    const isToday = i === series.buckets.length - 1;
    return isToday ? 'Heute' : WEEKDAY_SHORT[new Date(b.start).getDay()];
  }
  function bucketTitle(series, b) {
    return series.config.unit === 'week' ? formatWeekRange(b.start) : formatDayShort(b.start);
  }
  function sessionsLabel(n) {
    return n + (n === 1 ? ' Sitzung' : ' Sitzungen');
  }
  function describeBucket(series, b) {
    return bucketTitle(series, b) + ': ' + formatDuration(b.total) + ', ' + sessionsLabel(b.count);
  }

  function buildShell(host, state, options) {
    host.replaceChildren();
    host.classList.add('fm-focus-chart');
    const head = el('div', 'fm-chart-head');
    const titles = el('div', 'fm-chart-titles');
    titles.append(el('span', 'fm-chart-kicker', options.kicker || 'FOKUS-RHYTHMUS'));
    const sub = el('div', 'fm-chart-sub');
    titles.append(sub);
    head.append(titles);
    const toggle = el('div', 'fm-chart-range');
    toggle.setAttribute('role', 'group');
    toggle.setAttribute('aria-label', 'Zeitraum');
    Object.keys(RANGES).forEach(function (key) {
      const btn = el('button', 'fm-chart-range-btn', RANGES[key].label);
      btn.type = 'button';
      btn.dataset.range = key;
      btn.addEventListener('click', function () {
        if (state.range === key) return;
        state.range = key;
        state.selected = -1;
        renderChart(host, { animate: true });
      });
      toggle.append(btn);
    });
    head.append(toggle);

    const plot = el('div', 'fm-chart-plot');
    const hits = el('div', 'fm-chart-hits');
    const tip = el('div', 'fm-chart-tip');
    tip.setAttribute('role', 'status');
    tip.setAttribute('aria-live', 'polite');
    tip.hidden = true;
    const empty = el('div', 'fm-chart-empty');
    empty.hidden = true;
    plot.append(hits, tip, empty);

    const foot = el('div', 'fm-chart-foot');
    const summary = el('p', 'fm-sr-only');
    host.append(head, plot, foot, summary);
    state.nodes = { sub: sub, toggle: toggle, plot: plot, hits: hits, tip: tip, empty: empty, foot: foot, summary: summary };
  }

  function showTip(host, state, index) {
    const n = state.nodes;
    const series = state.series;
    if (!series || index < 0 || index >= series.buckets.length) {
      state.selected = -1;
      n.tip.hidden = true;
      n.plot.classList.remove('has-selection');
      n.plot.querySelectorAll('.fm-chart-bar.is-selected, .fm-chart-hit.is-selected').forEach(function (x) { x.classList.remove('is-selected'); });
      return;
    }
    state.selected = index;
    const b = series.buckets[index];
    n.tip.replaceChildren();
    n.tip.append(el('div', 'fm-chart-tip-date', bucketTitle(series, b)));
    n.tip.append(el('div', 'fm-chart-tip-total', b.total ? formatDuration(b.total) : 'Keine Fokuszeit'));
    if (b.count) {
      n.tip.append(el('div', 'fm-chart-tip-meta', sessionsLabel(b.count)));
      const split = el('div', 'fm-chart-tip-split');
      if (b.mission) {
        const row = el('span', 'is-mission');
        row.append(el('i'), document.createTextNode('Mission ' + formatDuration(b.mission)));
        split.append(row);
      }
      if (b.quick) {
        const row = el('span', 'is-quick');
        row.append(el('i'), document.createTextNode('Quick ' + formatDuration(b.quick)));
        split.append(row);
      }
      n.tip.append(split);
    }
    n.plot.classList.add('has-selection');
    n.plot.querySelectorAll('.fm-chart-bar, .fm-chart-hit').forEach(function (x) {
      x.classList.toggle('is-selected', Number(x.dataset.index) === index);
    });
    n.tip.hidden = false;
    const geo = state.geometry;
    const tipW = n.tip.offsetWidth;
    const tipH = n.tip.offsetHeight;
    const cx = geo.slotW * index + geo.slotW / 2;
    const barTop = geo.barTops[index];
    let left = cx - tipW / 2;
    let top = barTop - tipH - 10;
    if (top < 0) {
      // Not enough room above a tall bar: place the tip beside it instead of over it.
      top = 0;
      left = cx > geo.width / 2 ? cx - geo.slotW / 2 - tipW - 2 : cx + geo.slotW / 2 + 2;
    }
    left = Math.max(4, Math.min(geo.width - tipW - 4, left));
    n.tip.style.transform = 'translate3d(' + Math.round(left) + 'px, ' + Math.round(top) + 'px, 0)';
  }

  function renderChart(host, options) {
    if (!host || typeof document === 'undefined') return;
    const opts = options || {};
    let state = chartStates.get(host);
    if (!state) {
      state = { range: host.dataset.defaultRange === '4w' ? '4w' : '7d', sig: null, selected: -1, rendered: false };
      chartStates.set(host, state);
      buildShell(host, state, { kicker: host.dataset.chartKicker });
      mountedCharts.add(host);
    }
    const width = Math.round(state.nodes.plot.clientWidth || host.clientWidth || 0);
    if (!width) {
      state.sig = null;
      return;
    }
    const series = buildSeries(state.range);
    const sig = state.range + '|' + width + '|' + series.buckets.map(function (b) { return b.quick + ':' + b.mission + ':' + b.count; }).join(',');
    if (sig === state.sig && !opts.force) return;
    const animate = Boolean(opts.animate) || !state.rendered;
    state.sig = sig;
    state.series = series;
    state.rendered = true;
    drawChart(host, state, series, width, animate);
  }

  function drawChart(host, state, series, width, animate) {
    const n = state.nodes;
    const cfg = series.config;
    n.toggle.querySelectorAll('.fm-chart-range-btn').forEach(function (btn) {
      const on = btn.dataset.range === series.range;
      btn.classList.toggle('active', on);
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    n.sub.replaceChildren();
    const subStrong = el('strong', '', formatDuration(series.total));
    n.sub.append(subStrong, document.createTextNode(cfg.periodLabel + ' · ' + sessionsLabel(series.count)));

    const height = 150;
    const padTop = 24;
    const padBottom = 24;
    const plotH = height - padTop - padBottom;
    const count = series.buckets.length;
    const slotW = width / count;
    const barW = Math.max(10, Math.min(cfg.unit === 'week' ? 46 : 30, slotW * 0.56));
    const radius = Math.min(9, barW / 2);
    const max = Math.max(1, ...series.buckets.map(function (b) { return b.total; }));
    const scale = function (m) { return (m / max) * plotH; };
    const baseY = padTop + plotH;
    const barTops = [];

    const svg = svgEl('svg', {
      class: 'fm-chart-svg',
      width: String(width),
      height: String(height),
      viewBox: '0 0 ' + width + ' ' + height,
      'aria-hidden': 'true',
      focusable: 'false'
    });
    svg.append(svgEl('line', { class: 'fm-chart-baseline', x1: '0', x2: String(width), y1: String(baseY + 0.5), y2: String(baseY + 0.5) }));

    const active = series.buckets.filter(function (b) { return b.total > 0; });
    if (active.length >= 2) {
      const avg = series.total / count;
      const avgY = baseY - scale(avg);
      svg.append(svgEl('line', { class: 'fm-chart-avg', x1: '0', x2: String(width), y1: String(avgY), y2: String(avgY) }));
    }

    series.buckets.forEach(function (b, i) {
      const x = slotW * i + (slotW - barW) / 2;
      const g = svgEl('g', { class: 'fm-chart-bar' + (i === series.bestIndex ? ' is-best' : ''), 'data-index': String(i), style: '--i:' + i });
      if (b.total <= 0) {
        g.classList.add('is-empty');
        g.append(svgEl('rect', { class: 'fm-chart-placeholder', x: String(x), y: String(baseY - 4), width: String(barW), height: '4', rx: '2' }));
        barTops.push(baseY - 4);
      } else {
        const totalH = Math.max(6, scale(b.total));
        const missionH = b.total ? totalH * (b.mission / b.total) : 0;
        const quickH = totalH - missionH;
        const top = baseY - totalH;
        barTops.push(top);
        if (quickH > 0.5 && missionH > 0.5) {
          g.append(svgEl('path', { class: 'fm-chart-seg is-quick', d: roundedTopPath(x, top, barW, quickH + radius, radius) }));
          g.append(svgEl('rect', { class: 'fm-chart-seg is-mission', x: String(x), y: String(top + quickH), width: String(barW), height: String(missionH) }));
        } else {
          g.append(svgEl('path', { class: 'fm-chart-seg ' + (missionH > quickH ? 'is-mission' : 'is-quick'), d: roundedTopPath(x, top, barW, totalH, radius) }));
        }
        if (i === series.bestIndex) {
          const label = svgEl('text', { class: 'fm-chart-best-label', x: String(slotW * i + slotW / 2), y: String(Math.max(11, top - 7)), 'text-anchor': 'middle' });
          label.textContent = formatDuration(b.total);
          svg.append(label);
        }
      }
      const xl = svgEl('text', {
        class: 'fm-chart-x' + (i === count - 1 ? ' is-current' : ''),
        x: String(slotW * i + slotW / 2),
        y: String(height - 6),
        'text-anchor': 'middle'
      });
      xl.textContent = bucketLabel(series, b, i);
      svg.append(g, xl);
    });

    const oldSvg = n.plot.querySelector('svg');
    if (oldSvg) oldSvg.replaceWith(svg);
    else n.plot.prepend(svg);

    n.hits.replaceChildren();
    series.buckets.forEach(function (b, i) {
      const hit = el('button', 'fm-chart-hit');
      hit.type = 'button';
      hit.dataset.index = String(i);
      hit.style.left = (slotW * i).toFixed(2) + 'px';
      hit.style.width = slotW.toFixed(2) + 'px';
      hit.setAttribute('aria-label', describeBucket(series, b));
      hit.addEventListener('click', function (ev) {
        ev.stopPropagation();
        showTip(host, state, state.selected === i ? -1 : i);
      });
      n.hits.append(hit);
    });

    state.geometry = { width: width, slotW: slotW, barTops: barTops };
    n.empty.hidden = series.total > 0;
    if (!series.total) {
      n.empty.textContent = series.range === '4w' ? 'In den letzten 4 Wochen noch keine Fokuszeit.' : 'In den letzten 7 Tagen noch keine Fokuszeit.';
    }

    drawFooter(state, series);
    n.summary.textContent = cfg.periodLabel + ': ' + formatDuration(series.total) + ' Fokuszeit in ' + sessionsLabel(series.count) + '. ' +
      series.buckets.map(function (b) { return describeBucket(series, b); }).join('; ') + '.';

    if (state.selected >= 0) showTip(host, state, state.selected);
    else showTip(host, state, -1);

    host.classList.remove('is-revealing');
    if (animate && series.total > 0) {
      void host.offsetWidth;
      host.classList.add('is-revealing');
      clearTimeout(state.revealTimer);
      state.revealTimer = setTimeout(function () { host.classList.remove('is-revealing'); }, 900);
    }
  }

  function drawFooter(state, series) {
    const foot = state.nodes.foot;
    foot.replaceChildren();
    const legend = el('div', 'fm-chart-legend');
    const mission = el('span', 'fm-chart-legend-item is-mission');
    mission.append(el('i'), document.createTextNode('Mission ' + formatDuration(series.mission)));
    const quick = el('span', 'fm-chart-legend-item is-quick');
    quick.append(el('i'), document.createTextNode('Quick ' + formatDuration(series.quick)));
    legend.append(mission, quick);
    foot.append(legend);

    if (series.bestIndex >= 0) {
      const best = series.buckets[series.bestIndex];
      const row = el('div', 'fm-chart-insight');
      row.append(el('span', 'fm-chart-insight-label', series.config.unit === 'week' ? 'Stärkste Woche' : 'Stärkster Tag'));
      row.append(el('strong', '', bucketTitle(series, best) + ' · ' + formatDuration(best.total)));
      foot.append(row);
    }
    if (series.prevTotal > 0 && series.total > 0) {
      const pct = Math.round(((series.total - series.prevTotal) / series.prevTotal) * 100);
      const row = el('div', 'fm-chart-insight');
      row.append(el('span', 'fm-chart-insight-label', 'Trend'));
      const val = el('strong', 'fm-chart-trend ' + (pct > 0 ? 'is-up' : pct < 0 ? 'is-down' : 'is-flat'),
        (pct > 0 ? '+' : '') + pct + ' % ggü. ' + series.config.prevLabel);
      row.append(val);
      foot.append(row);
    }
  }

  function renderVisibleCharts() {
    mountedCharts.forEach(function (host) {
      if (!host.isConnected) {
        mountedCharts.delete(host);
        return;
      }
      if (host.offsetParent !== null) renderChart(host);
    });
  }

  let resizeTimer = null;
  function onResize() {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      resizeTimer = null;
      if (global.MotionController && typeof global.MotionController.isLocked === 'function' && global.MotionController.isLocked()) {
        onResize();
        return;
      }
      renderVisibleCharts();
    }, 180);
  }

  if (typeof document !== 'undefined') {
    global.addEventListener('resize', onResize, { passive: true });
    document.addEventListener('click', function (ev) {
      mountedCharts.forEach(function (host) {
        const state = chartStates.get(host);
        if (state && state.selected >= 0 && !host.contains(ev.target)) showTip(host, state, -1);
      });
    });
  }

  const FocusInsights = {
    RANGES: RANGES,
    buildSeries: buildSeries,
    computeInsights: computeInsights,
    renderChart: renderChart,
    formatDayLong: formatDayLong,
    formatDayShort: formatDayShort,
    formatWeekRange: formatWeekRange,
    formatDuration: formatDuration
  };
  global.FocusInsights = FocusInsights;
  if (global.FocusModeApp && global.FocusModeApp.features) {
    global.FocusModeApp.features.focusInsights = FocusInsights;
  }
})(window);
