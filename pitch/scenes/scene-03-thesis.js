/*
 * Scene 3 · Why now and product thesis.
 * Beat stat:     46,5 % daily / 81 % weekly AI use (CHE DatenCHECK 9/2026); the four fragments from
 *                scene 2 still float apart on the right (same positions, so the crossfade holds them).
 * Beat converge: the AI plan becomes the Mission, the calendar deadline the goal waypoint, the to-do
 *                the next Focus step; the timer ring grows into the orbit that connects them, and a
 *                compact Focus Core begins forming at its centre.
 * Beat thesis:   "Focus Mode+ verbindet Planung und Umsetzung."
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, C = Pitch.content.data;

  const ORB = { cx: 1388, cy: 560, rx: 372, ry: 206, rot: -8 };
  const NODES = [
    { id: 'ziel', label: 'Ziel', sub: '27. Nov · Klausur', deg: 170, side: 'ri' },
    { id: 'mission', label: 'Mission', sub: '4 Schritte', deg: 240, side: 't' },
    { id: 'fokus', label: 'Fokus', sub: 'nächster Schritt', deg: 300, side: 't' },
    { id: 'feedback', label: 'Feedback', sub: 'Ergebnis', deg: 20, side: 'li' },
    { id: 'anpassung', label: 'Anpassung', sub: 'nächste Schritte', deg: 100, side: 'b' }
  ];
  const FRAG_TARGET = { '.th-ai': 'mission', '.th-cal': 'ziel', '.th-todo': 'fokus' };

  function nodePos(deg) {
    const t = deg * Math.PI / 180, r = ORB.rot * Math.PI / 180;
    const x = ORB.rx * Math.cos(t), y = ORB.ry * Math.sin(t);
    return { x: ORB.cx + x * Math.cos(r) - y * Math.sin(r), y: ORB.cy + x * Math.sin(r) + y * Math.cos(r), nx: Math.cos(t), ny: Math.sin(t) };
  }

  function build(ctx) {
    const nodes = NODES.map(function (n, i) {
      const p = nodePos(n.deg);
      return '<div class="th-node side-' + n.side + '" style="left:' + p.x.toFixed(0) + 'px;top:' + p.y.toFixed(0) + 'px;--i:' + i + '"><i></i>' +
        '<span><b>' + esc(n.label) + '</b><small>' + esc(n.sub) + '</small></span></div>';
    }).join('');
    const arrows = NODES.map(function (n, i) {
      const next = NODES[(i + 1) % NODES.length];
      let span = next.deg - n.deg; if (span <= 0) span += 360;
      const mid = n.deg + span / 2;
      const p = nodePos(mid), q = nodePos(mid + 2);
      const ang = Math.atan2(q.y - p.y, q.x - p.x) * 180 / Math.PI;
      return '<path class="th-chev" d="M-7 -7 L3 0 L-7 7" transform="translate(' + p.x.toFixed(1) + ' ' + p.y.toFixed(1) + ') rotate(' + ang.toFixed(1) + ')"/>';
    }).join('');
    Pitch.dom.mount(ctx.root, [
      '<div class="th-bg cream-bg"></div>',
      '<div class="th-left">',
      ui.kicker('Warum jetzt'),
      '<p class="stat-num th-num">' + Pitch.statMarkup(C.aiDailyStatistic) + '</p>',
      '<p class="lead th-label">' + esc(C.aiDailyLabel) + '</p>',
      '<p class="th-weekly"><b>' + esc(C.aiWeeklyStatistic) + '</b><span>' + esc(C.aiWeeklyLabel) + '</span></p>',
      ui.source(C.aiStatisticSource, C.aiStatisticQualifier, 'th-source'),
      '</div>',
      '<h2 class="display th-thesis"><span class="th-brand">Focus Mode+</span> verbindet Planung und Umsetzung.</h2>',
      '<div class="th-compact"><p><b>' + esc(C.aiDailyStatistic) + '</b> ' + esc(C.aiDailyLabel) + ' · <b>' + esc(C.aiWeeklyStatistic) + '</b> mindestens wöchentlich</p>' +
        ui.source(C.aiStatisticSource, C.aiStatisticQualifier) + '</div>',
      '<canvas class="th-core" width="520" height="520"></canvas>',
      '<svg class="th-orbit" viewBox="0 0 1920 1080" aria-hidden="true"><ellipse class="th-ell" cx="0" cy="0" rx="0" ry="0"/><g class="th-chevs">' + arrows + '</g></svg>',
      nodes,
      '<span class="astro-shadow th-shadow"></span>',
      ui.astronaut('h146 th-astro'),
      '<svg class="th-links frag-links" viewBox="0 0 1920 1080" aria-hidden="true"></svg>',
      ui.fragAi('th-ai th-frag'), ui.fragCal('th-cal th-frag'), ui.fragTodo('th-todo th-frag'), ui.fragTimer('th-timer th-frag')
    ].join(''));
    ctx.core = Pitch.FocusCore.create(ctx.q('.th-core'), null, { seed: 3, density: 0.2 });
  }

  function boxOf(el) { return { x: el.offsetLeft, y: el.offsetTop, w: el.offsetWidth, h: el.offsetHeight }; }

  function ringStart(ctx) {
    const frag = ctx.q('.th-timer'), ring = ctx.q('.th-timer .ft-ring');
    const half = ring.offsetWidth / 2;
    return { cx: frag.offsetLeft + ring.offsetLeft + half, cy: frag.offsetTop + ring.offsetTop + half, r: half * 50 / 60 };
  }

  function setEllipse(ctx, g) {
    const el = ctx.q('.th-ell');
    el.setAttribute('cx', g.cx.toFixed(1)); el.setAttribute('cy', g.cy.toFixed(1));
    el.setAttribute('rx', g.rx.toFixed(1)); el.setAttribute('ry', g.ry.toFixed(1));
    el.setAttribute('transform', 'rotate(' + g.rot.toFixed(2) + ' ' + g.cx.toFixed(1) + ' ' + g.cy.toFixed(1) + ')');
  }

  function resize(ctx) {
    const boxes = ['.th-ai', '.th-cal', '.th-todo', '.th-timer'].map(function (s) { return boxOf(ctx.q(s)); });
    Pitch.brokenLinks(ctx.q('.th-links'), boxes, [[0, 1], [0, 2], [1, 3], [2, 3]]);
    Object.keys(FRAG_TARGET).forEach(function (sel) {
      const el = ctx.q(sel), b = boxOf(el), n = NODES.filter(function (x) { return x.id === FRAG_TARGET[sel]; })[0], p = nodePos(n.deg);
      el.style.setProperty('--tx', (p.x - (b.x + b.w / 2)).toFixed(0) + 'px');
      el.style.setProperty('--ty', (p.y - (b.y + b.h / 2)).toFixed(0) + 'px');
    });
    const scale = ctx.stageScale() * (global.devicePixelRatio || 1);
    ctx.core.layout({ w: 520, h: 520, dpr: Math.min(2, Math.max(1, scale)), cx: 260, cy: 260, R: 66 });
    ctx.core.render(Pitch.timeline.now() / 1000);
    if (ctx.ellipseState) setEllipse(ctx, ctx.ellipseState);
  }

  function coreLoop(ctx) {
    ctx.stopLoops();
    ctx.loop(function (time) { ctx.core.render(time / 1000); });
  }

  function apply(ctx, beat, opts) {
    const instant = opts.instant || ctx.reduced();
    if (beat === 0) {
      Pitch.countUp(ctx, ctx.q('.th-num .num'), C.aiDailyStatistic.replace(/\s?%$/, ''), 1300, opts.instant);
    }
    if (beat >= 1) {
      const start = ringStart(ctx), end = { cx: ORB.cx, cy: ORB.cy, rx: ORB.rx, ry: ORB.ry, rot: ORB.rot };
      const from = { cx: start.cx, cy: start.cy, rx: start.r, ry: start.r, rot: 0 };
      if (instant || beat > 1) {
        ctx.ellipseState = end; setEllipse(ctx, end);
        ctx.core.set({ seed: 0.35, core: 1, g: 1, detail: 0.85 });
        ctx.core.render(Pitch.timeline.now() / 1000);
        if (!ctx.reduced()) coreLoop(ctx);
      } else {
        ctx.ellipseState = from; setEllipse(ctx, from);
        ctx.tween({ delay: 350, dur: 1700, ease: 'inOut', update: function (e) {
          const g = { cx: from.cx + (end.cx - from.cx) * e, cy: from.cy + (end.cy - from.cy) * e, rx: from.rx + (end.rx - from.rx) * e, ry: from.ry + (end.ry - from.ry) * e, rot: end.rot * e };
          ctx.ellipseState = g; setEllipse(ctx, g);
        } });
        coreLoop(ctx);
        ctx.tween({ delay: 1200, dur: 900, ease: 'out', update: function (e) { ctx.core.set({ seed: e }); } });
        ctx.tween({ delay: 1500, dur: 1400, ease: 'out', update: function (e) { ctx.core.set({ core: 1, g: 0.2 + 0.8 * e, detail: e * 0.85, seed: 1 - e * 0.65 }); } });
      }
    }
  }

  function reset(ctx) {
    ctx.ellipseState = null;
    setEllipse(ctx, { cx: 0, cy: 0, rx: 0, ry: 0, rot: 0 });
    ctx.core.set({ seed: 0, core: 0, g: 0, detail: 0 });
    ctx.core.render(0);
    const n = ctx.q('.th-num .num');
    if (n) n.textContent = C.aiDailyStatistic.replace(/\s?%$/, '');
  }

  Pitch.registerScene({
    id: 'thesis', number: 3, title: 'Warum jetzt · These', beats: ['stat', 'converge', 'thesis'],
    assets: ['./assets/operator/orbit-standing.png'],
    blendIn: true, // the four fragments sit exactly where scene 2 left them
    tone: function () { return 'light'; },
    build: build, apply: apply, reset: reset, resize: resize,
    enter: function (ctx) { Pitch.phaseLock(ctx.qa('.th-frag')); },
    exit: function (ctx) { ctx.stopLoops(); }
  });
})(window);
