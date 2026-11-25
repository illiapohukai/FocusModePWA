/*
 * Focus Mode+ Pitch · presentation-native product components.
 *
 * Visual replicas of the PWA (Mission hero, Mission route nodes, Quick Focus duration dial,
 * app selection, result options, workflow fragments) built for the stage. They copy the PWA's
 * geometry and tokens but are independent markup: they hold deterministic demo data only and never
 * touch PWA code, storage or AI.
 *
 * Also: Pitch.seq, the in-beat sub-step helper. A beat may add classes on a schedule
 * (e.g. 'n-in' after 600 ms); settling the beat adds them all at once, so every beat has one exact
 * final state.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const esc = Pitch.dom.esc, icon = Pitch.icon;

  /* ------------------------------------------------------------ sub-steps inside a beat */
  const seq = {
    /* steps: [[ms, className, optionalFn], ...] applied to root. */
    run: function (ctx, root, steps, instant) {
      steps.forEach(function (s) {
        const apply = function () { if (s[1]) root.classList.add(s[1]); if (s[2]) s[2](true); };
        if (instant) {
          if (s[1]) root.classList.add(s[1]);
          if (s[2]) s[2](false);
        } else ctx.after(s[0], apply);
      });
    },
    clear: function (root, names) { names.forEach(function (n) { root.classList.remove(n); }); }
  };

  /* ------------------------------------------------------------ text pieces */
  function kicker(text, opts) {
    opts = opts || {};
    return '<p class="kicker' + (opts.cls ? ' ' + opts.cls : '') + '">' + (opts.dot ? '<i class="kdot"></i>' : '') + esc(text) + '</p>';
  }

  function source(text, qualifier, cls) {
    return '<p class="source' + (cls ? ' ' + cls : '') + '"><span class="source-tag">Quelle</span><span>' + esc(text) +
      (qualifier ? '<span class="source-q"> · ' + esc(qualifier) + '</span>' : '') + '</span></p>';
  }

  function astronaut(cls) {
    return '<img class="astro ' + (cls || '') + '" src="./assets/operator/orbit-standing.png" alt="" draggable="false" decoding="async">';
  }

  /* ------------------------------------------------------------ Mission hero (PWA .mission-hero, aubergine) */
  function missionHero(o) {
    return '<article class="mhero theme-aubergine' + (o.cls ? ' ' + o.cls : '') + '">' +
      '<div class="mh-top"><span class="mh-kicker"><i></i>' + esc(o.kicker || 'Focus Mission') + '</span>' +
      (o.counter ? '<span class="mh-counter">' + esc(o.counter) + '</span>' : '') + '</div>' +
      '<h3 class="mh-title">' + esc(o.title) + '</h3>' +
      (o.meta ? '<p class="mh-meta">' + o.meta + '</p>' : '') +
      (o.progress != null ? '<div class="mh-progress"><i style="--p:' + o.progress + '"></i></div>' : '') +
      (o.step ? '<div class="mh-step"><span class="mh-label">' + esc(o.stepLabel || 'Nächster Schritt') + '</span>' +
        '<strong>' + esc(o.step) + '</strong>' + (o.stepMeta ? '<small>' + o.stepMeta + '</small>' : '') + '</div>' : '') +
      (o.button ? '<span class="mh-btn">' + icon('play') + esc(o.button) + '</span>' : '') +
      '</article>';
  }

  /* ------------------------------------------------------------ Quick Focus duration dial (PWA SVG geometry) */
  function dial(minutes) {
    const ticks = [];
    for (let i = 0; i < 12; i++) {
      const a = i / 12 * Math.PI * 2 - Math.PI / 2, major = i % 3 === 0;
      ticks.push('<circle class="qd-tick' + (major ? ' is-major' : '') + '" cx="' + (120 + Math.cos(a) * 111.5).toFixed(1) + '" cy="' + (120 + Math.sin(a) * 111.5).toFixed(1) + '" r="' + (major ? 2.2 : 1.5) + '"/>');
    }
    const offset = 60 - minutes, rot = minutes / 60 * 360;
    return '<div class="qdial">' +
      '<div class="qd-orbit" aria-hidden="true"><span class="qd-line"></span><span class="qd-tilt"></span>' +
      '<span class="qd-o qd-a"><i></i><i></i></span><span class="qd-o qd-b"><i></i><i></i></span>' +
      '<span class="qd-o qd-c"><i></i><i></i></span><span class="qd-o qd-d"><i></i><i></i><i></i><i></i></span></div>' +
      '<div class="qd-face"><svg viewBox="0 0 240 240" aria-hidden="true" focusable="false"><defs>' +
      '<linearGradient id="qdTrack" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#DCD2C2"/><stop offset="1" stop-color="#F1EBE0"/></linearGradient>' +
      '<linearGradient id="qdArc" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#B69CFB"/><stop offset=".5" stop-color="#7C3AED"/><stop offset="1" stop-color="#5B21B6"/></linearGradient></defs>' +
      '<g class="qd-ticks">' + ticks.join('') + '</g>' +
      '<circle class="qd-track" cx="120" cy="120" r="96"/><circle class="qd-shade" cx="120" cy="120" r="88"/>' +
      '<circle class="qd-arc" cx="120" cy="120" r="96" pathLength="60" transform="rotate(-90 120 120)" style="stroke-dashoffset:' + offset + '"/>' +
      '<g class="qd-thumb" style="transform:rotate(' + rot + 'deg)"><circle class="qd-halo" cx="120" cy="24" r="16"/><circle class="qd-knob" cx="120" cy="24" r="10.5"/><circle class="qd-kcore" cx="120" cy="24" r="3.2"/></g>' +
      '</svg><span class="qd-core"></span></div></div>';
  }

  /* ------------------------------------------------------------ app selection tile (generic categories only) */
  function appTile(app, i) {
    return '<div class="app-tile tone-' + app.tone + '" data-app="' + i + '"><span class="app-ic">' + icon(app.icon) + '</span>' +
      '<b>' + esc(app.name) + '</b><span class="app-check">' + icon('check') + '</span></div>';
  }

  /* ------------------------------------------------------------ fragmented-workflow replicas (scenes 2 and 3) */
  function fragAi(cls) {
    return '<div class="frag frag-ai ' + (cls || '') + '"><p class="frag-label">' + icon('sparkles') + 'KI-Chat</p>' +
      '<div class="frag-bubble"><p>Hier ist dein Lernplan:</p><ol><li>Grundlagen wiederholen</li><li>Lineare Funktionen</li><li>Übungsblatt 3</li><li>Probeklausur</li></ol><p class="frag-more">… 14 weitere Schritte</p></div></div>';
  }
  function fragCal(cls) {
    let cells = '';
    for (let d = 1; d <= 30; d++) cells += '<i' + (d === 27 ? ' class="is-mark"' : '') + '>' + d + '</i>';
    return '<div class="frag frag-cal ' + (cls || '') + '"><p class="frag-label">' + icon('calendar') + 'Kalender</p>' +
      '<div class="cal-head"><b>November</b><span>Klausur</span></div><div class="cal-grid"><i></i><i></i><i></i><i></i><i></i>' + cells + '</div></div>';
  }
  function fragTodo(cls) {
    return '<div class="frag frag-todo ' + (cls || '') + '"><p class="frag-label">' + icon('list-checks') + 'To-do</p>' +
      '<ul><li class="is-done"><span>' + icon('check') + '</span>Kapitel 3 lesen</li><li><span></span>Aufgaben S. 42</li><li><span></span>Formeln lernen</li></ul></div>';
  }
  function fragTimer(cls) {
    return '<div class="frag frag-timer ' + (cls || '') + '"><p class="frag-label">' + icon('timer') + 'Fokus-Timer</p>' +
      '<div class="ft-ring"><svg viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="50" class="ft-track"/><circle cx="60" cy="60" r="50" class="ft-arc" pathLength="60" transform="rotate(-90 60 60)"/></svg><b>25:00</b></div></div>';
  }

  /* ------------------------------------------------------------ result options (PWA Session review) */
  function resultOptions() {
    return '<div class="result-opts">' +
      '<div class="ro is-pick" data-r="completed"><span>' + icon('check') + '</span><strong>Abgeschlossen</strong><small>Schritt erledigt</small></div>' +
      '<div class="ro" data-r="partial"><span>' + icon('circle-dashed') + '</span><strong>Teilweise</strong><small>Später weiter</small></div>' +
      '<div class="ro" data-r="aborted"><span>' + icon('clock') + '</span><strong>Abgebrochen</strong><small>Neu ansetzen</small></div></div>';
  }

  /* Phase-lock ambient CSS loops to absolute time, so the same fragment floats identically in two
     scenes that blend into each other (scene 2 → 3). */
  function phaseLock(els) {
    const now = performance.now();
    els.forEach(function (el, i) {
      const dur = parseFloat(getComputedStyle(el).animationDuration) * 1000;
      if (dur > 0) el.style.animationDelay = (-((now + i * 1700) % dur)).toFixed(0) + 'ms';
    });
  }

  Pitch.seq = seq;
  Pitch.phaseLock = phaseLock;
  Pitch.ui = Object.freeze({
    kicker: kicker, source: source, astronaut: astronaut, missionHero: missionHero, dial: dial, appTile: appTile,
    fragAi: fragAi, fragCal: fragCal, fragTodo: fragTodo, fragTimer: fragTimer, resultOptions: resultOptions
  });
})(window);
