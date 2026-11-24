/*
 * Scene 1 · Hook — "Ein Plan ist noch kein Fortschritt."
 * Standby: cream stage, wordmark, the astronaut on a faint route.
 * Beat plan:   an AI-generated 18-step plan assembles (structured, impressive).
 * Beat drift:  the steps separate and fall out of sequence, a calendar fragment fails to connect,
 *              a notification crosses the route and the route breaks.
 * Beat reveal: the statement; PLANUNG and UMSETZUNG with the gap between them.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon;

  const TASKS = [
    ['Mo', 'Grundlagen wiederholen', 45], ['Di', 'Termumformungen üben', 30], ['Mi', 'Gleichungen lösen', 40],
    ['Do', 'Lineare Funktionen', 45], ['Fr', 'Übungsblatt 1', 30], ['Sa', 'Fehleranalyse', 20],
    ['Mo', 'Funktionsgraphen zeichnen', 40], ['Di', 'Schnittpunkte berechnen', 35], ['Mi', 'Textaufgaben', 45],
    ['Do', 'Übungsblatt 2', 30], ['Fr', 'Formelsammlung anlegen', 25], ['Sa', 'Wiederholung', 30],
    ['Mo', 'Quadratische Funktionen', 45], ['Di', 'Scheitelpunktform', 40], ['Mi', 'Übungsblatt 3', 30],
    ['Do', 'Probeklausur 1', 90], ['Fr', 'Lücken schließen', 40], ['Sa', 'Probeklausur 2', 90]
  ];

  // Plan surface geometry (stage px).
  const PLAN = { x: 290, y: 128, w: 1300, pad: 36, head: 72, colHead: 42, cardH: 76, gapY: 12, gapX: 24 };
  const CARD_W = Math.round((PLAN.w - PLAN.pad * 2 - PLAN.gapX * 2) / 3);

  // Final resting centres around the statement (clear of the headline, route labels and camera area).
  const REST = [
    [215, 150, -4], [560, 104, 3], [962, 84, -2], [1360, 116, 4], [1742, 300, -5], [190, 330, 5],
    [150, 505, -3], [205, 690, 4], [1765, 470, 3], [1722, 650, -4], [520, 742, 3], [600, 1010, -2],
    [1200, 748, 2], [-96, 905, -9], [2016, 896, 8], [60, -44, 6], [1290, -60, -7], [2070, 560, 10]
  ];
  // Out-of-sequence order: which REST slot each task drifts to.
  const ORDER = [5, 1, 15, 2, 6, 10, 0, 3, 11, 16, 7, 12, 4, 8, 13, 9, 14, 17];

  function rnd(seed) { let a = seed >>> 0; return function () { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

  function build(ctx) {
    const r = rnd(0x51a7);
    const cards = TASKS.map(function (t, i) {
      const col = Math.floor(i / 6), row = i % 6;
      const x = PLAN.x + PLAN.pad + col * (CARD_W + PLAN.gapX);
      const y = PLAN.y + PLAN.pad + PLAN.head + PLAN.colHead + row * (PLAN.cardH + PLAN.gapY);
      const cx = x + CARD_W / 2, cy = y + PLAN.cardH / 2;
      const rest = REST[ORDER[i]];
      const mid = 0.52 + r() * 0.12;
      const dx1 = (rest[0] - cx) * mid + (r() - 0.5) * 120, dy1 = (rest[1] - cy) * mid + (r() - 0.5) * 90;
      const style = [
        'left:' + x + 'px', 'top:' + y + 'px', 'width:' + CARD_W + 'px',
        '--d0:' + (260 + (col * 6 + row) * 34) + 'ms',
        '--d1:' + Math.round(r() * 520) + 'ms',
        '--dx1:' + dx1.toFixed(0) + 'px', '--dy1:' + dy1.toFixed(0) + 'px', '--r1:' + ((r() - 0.5) * 14).toFixed(1) + 'deg',
        '--s1:' + (0.9 + r() * 0.06).toFixed(2), '--o1:' + (0.55 + r() * 0.35).toFixed(2),
        '--dx2:' + (rest[0] - cx).toFixed(0) + 'px', '--dy2:' + (rest[1] - cy).toFixed(0) + 'px', '--r2:' + rest[2] + 'deg',
        '--o2:' + (0.2 + r() * 0.2).toFixed(2), '--d2:' + Math.round(r() * 260) + 'ms'
      ].join(';');
      return '<div class="task" style="' + style + '"><span class="task-check"></span><span class="task-txt"><b>' + esc(t[1]) + '</b>' +
        '<small><em>' + t[0] + '</em> · ' + t[2] + ' Min.</small></span></div>';
    }).join('');

    const planH = PLAN.pad * 2 + PLAN.head + PLAN.colHead + 6 * PLAN.cardH + 5 * PLAN.gapY;
    Pitch.dom.mount(ctx.root, [
      '<div class="hk-bg"></div>',
      '<p class="brandmark hk-brand">Focus Mode+</p>',
      '<div class="hk-plan" style="left:' + PLAN.x + 'px;top:' + PLAN.y + 'px;width:' + PLAN.w + 'px;height:' + planH + 'px">',
      '  <div class="hk-plan-head"><span class="hk-ai">' + icon('sparkles') + '</span><b>KI-Lernplan · Mathe-Klausur</b><span class="hk-meta">18 Schritte · 3 Wochen</span></div>',
      '  <div class="hk-cols"><span>Woche 1</span><span>Woche 2</span><span>Woche 3</span></div>',
      '</div>',
      cards,
      '<div class="hk-cal"><span class="hk-cal-ic">' + icon('calendar') + '</span><span><small>Do · 27. Nov</small><b>Klausur</b></span></div>',
      '<svg class="hk-cal-link" viewBox="0 0 300 120" aria-hidden="true"><path d="M296 60 C 220 60, 170 20, 86 26"/></svg>',
      '<div class="hk-route"><i class="hk-seg hk-seg-l"></i><i class="hk-seg hk-seg-r"></i>',
      '  <i class="hk-chip c1"></i><i class="hk-chip c2"></i><i class="hk-chip c3"></i>',
      '  <span class="hk-end hk-end-l">Planung</span><span class="hk-end hk-end-r">Umsetzung</span></div>',
      '<div class="hk-notif"><span class="hk-notif-ic">' + icon('message-circle') + '</span><span><small>Messenger · jetzt</small><b>Kommst du heute noch mit?</b></span></div>',
      '<span class="astro-shadow hk-shadow"></span>',
      ui.astronaut('h146 hk-astro'),
      '<h1 class="display hk-title"><span>Ein Plan ist noch</span><span>kein Fortschritt.</span></h1>'
    ].join(''));
  }

  const STEPS = ['n-in', 'n-out', 'r-break', 'c-in'];

  function apply(ctx, beat, opts) {
    const root = ctx.root;
    if (beat >= 1) {
      Pitch.seq.run(ctx, root, [[350, 'c-in'], [700, 'n-in'], [1900, 'r-break'], [2500, 'n-out']], opts.instant);
    }
  }

  function reset(ctx) { Pitch.seq.clear(ctx.root, STEPS); }

  Pitch.registerScene({
    id: 'hook', number: 1, title: 'Hook', beats: ['plan', 'drift', 'reveal'],
    assets: ['./assets/operator/orbit-standing.png'],
    tone: function () { return 'light'; },
    build: build, apply: apply, reset: reset
  });
})(window);
