/*
 * Scene 4 · Product demonstration (deterministic, local fixtures only; no AI, no storage, no network).
 *
 *  create    Goal, deadline and material float in as separate components, then assemble into the
 *            Mission creation screen inside a temporary phone outline.
 *  generate  The approved pixel operator works at the terminal; the success (planet) plays once.
 *            Copy is the PWA's own generation copy.
 *  route     The phone dissolves; the four generation dots detach and become the Mission route.
 *  next      The route recedes; the next concrete step comes forward (Lineare Funktionen üben, 25:00).
 *  protect   Distracting apps are selected (generic categories); protected focus is prepared.
 *  launch    Quick Focus setup, then the central transformation: orbit charge, inflow, the light
 *            track dissolves, a compact dark sphere forms, the cream stage darkens from the centre,
 *            the sphere grows into the Cosmic Core; the timer digits never move.
 *  active    Focus Session: task, protected-focus state, running timer, restrained particles.
 *  result    "Abgeschlossen"; the Core contracts into the route node, the next step becomes active
 *            and the result returns to the plan.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon;

  const CENTER = { x: 960, y: 520 };
  const CORE_R = 230;
  const NODES = [
    { x: 300, y: 822, title: 'Grundlagen prüfen' },
    { x: 690, y: 704, title: 'Lineare Funktionen verstehen' },
    { x: 1080, y: 612, title: 'Übungsaufgaben lösen' },
    { x: 1470, y: 556, title: 'Probeklausur bearbeiten' }
  ];
  const FLAG = { x: 1752, y: 538 };
  // Generation route dots inside the phone (stage px), which detach to become the route nodes.
  const GDOTS = [{ x: 912, y: 874 }, { x: 944, y: 874 }, { x: 976, y: 874 }, { x: 1008, y: 874 }];
  const APPS = [
    { name: 'Kurzvideos', icon: 'clapperboard', tone: 'burgundy', pick: true },
    { name: 'Social Media', icon: 'camera', tone: 'ember', pick: true },
    { name: 'Messenger', icon: 'message-circle', tone: 'forest', pick: false },
    { name: 'Spiele', icon: 'gamepad2', tone: 'violet', pick: true },
    { name: 'Shopping', icon: 'shopping-bag', tone: 'graphite', pick: true },
    { name: 'Musik', icon: 'music', tone: 'ink', pick: false }
  ];
  const CAPTIONS = [
    ['Mission erstellen', 'Ziel, Deadline, Material.'],
    ['Mission-Erstellung', 'Aus dem Ziel werden konkrete Schritte.'],
    ['Mission-Route', 'Schritt für Schritt bis zur Klausur.'],
    ['Nächster Schritt', 'Was jetzt dran ist, steht schon fest.'],
    ['Fokus-Schutz', 'Ablenkende Apps vorher auswählen.'],
    ['Fokus starten', 'Aus dem Schritt wird Fokus.'],
    ['Aktiver Fokus', 'Eine Aufgabe. 25 Minuten.'],
    ['Ergebnis', 'Ergebnis fließt in den weiteren Plan ein.']
  ];

  // In-beat sub-steps: [ms after the beat starts, class].
  const SEQ = [
    [[150, 'c-float'], [1750, 'c-asm']],
    [[80, 'g-in'], [2700, 'g-ok']],
    [[60, 'rt-out'], [700, 'rt-in']],
    [],
    [[650, 'p-0'], [880, 'p-1'], [1110, 'p-3'], [1340, 'p-4'], [1750, 'p-ready']],
    [[60, 'l-setup'], [1650, 'l-press'], [1850, 'l-charge'], [2150, 'l-dissolve'], [2650, 'l-dark'], [3350, 'l-core']],
    [[60, 'a-in']],
    [[60, 'x-pick'], [520, 'x-on'], [1350, 'x-contract'], [2300, 'x-route'], [2750, 'x-done'], [3250, 'x-next'], [3700, 'x-feedback']]
  ];
  const ALL_STEPS = SEQ.reduce(function (a, s) { return a.concat(s.map(function (x) { return x[1]; })); }, []);

  /* Smooth route through the nodes (Catmull-Rom as cubic Béziers). */
  function routePath(points) {
    let d = 'M' + points[0].x + ' ' + points[0].y;
    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[Math.max(0, i - 1)], p1 = points[i], p2 = points[i + 1], p3 = points[Math.min(points.length - 1, i + 2)];
      const c1 = { x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6 }, c2 = { x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6 };
      d += ' C' + c1.x.toFixed(1) + ' ' + c1.y.toFixed(1) + ' ' + c2.x.toFixed(1) + ' ' + c2.y.toFixed(1) + ' ' + p2.x + ' ' + p2.y;
    }
    return d;
  }

  function worksheet() {
    let lines = '';
    for (let i = 0; i < 7; i++) lines += '<i style="width:' + [88, 72, 94, 64, 82, 58, 76][i] + '%"></i>';
    return '<div class="dm-sheet"><p class="ws-h">Arbeitsblatt 3 · Lineare Funktionen</p>' +
      '<svg class="ws-graph" viewBox="0 0 200 120" aria-hidden="true"><path d="M10 110 H190 M20 118 V6" class="ws-axis"/><path d="M20 100 L180 20" class="ws-line"/><path d="M20 70 L180 50" class="ws-line ws-line-b"/></svg>' +
      '<div class="ws-lines">' + lines + '</div><p class="ws-task"><b>1.</b> f(x) = 2x + 1 · Steigung?</p><p class="ws-task"><b>2.</b> Schnittpunkt von f und g</p></div>';
  }

  function build(ctx) {
    const nodes = NODES.map(function (n, i) {
      return '<div class="dm-node n' + i + '" style="left:' + n.x + 'px;top:' + n.y + 'px">' +
        '<span class="dn-dot"><b>' + (i + 1) + '</b>' + icon('check') + '</span>' +
        '<div class="dn-card"><p class="dn-k"><span class="k-done">Abgeschlossen</span><span class="k-next">Nächster Schritt</span><span class="k-plain">Schritt ' + (i + 1) + '</span></p><b>' + esc(n.title) + '</b></div></div>';
    }).join('');
    const gdots = GDOTS.map(function (d, i) {
      const n = NODES[i];
      return '<i class="dm-gdot g' + i + '" style="left:' + d.x + 'px;top:' + d.y + 'px;--tx:' + (n.x - d.x) + 'px;--ty:' + (n.y - d.y) + 'px;--i:' + i + '"></i>';
    }).join('');
    const captions = CAPTIONS.map(function (c, i) {
      return '<div class="dm-cap" data-cap="' + i + '"><p class="kicker">' + esc(c[0]) + '</p><p class="dm-cap-line">' + esc(c[1]) + '</p></div>';
    }).join('');
    const pts = NODES.concat([FLAG]);

    Pitch.dom.mount(ctx.root, [
      '<div class="dm-bg cream-bg"></div>',
      '<div class="dm-dark"></div>',
      '<canvas class="dm-backdrop"></canvas>',
      // ---------------------------------------------------------------- create / generate (phone)
      '<div class="dm-phone"><span class="dm-notch"></span></div>',
      '<div class="dm-screen dm-create"><p class="dm-topbar">Focus Mission</p><h3>Was ist dein Ziel heute?</h3>' +
        '<span class="dm-plan-btn">Mission planen' + icon('arrow-up') + '</span></div>',
      worksheet(),
      '<div class="dm-goal"><p class="dm-flabel">' + icon('target') + 'Ziel</p><p class="dm-goal-text">Mathe-Klausur vorbereiten</p>' +
        '<div class="dm-goal-tools"><span>' + icon('paperclip') + '</span><span>' + icon('mic') + '</span><span>' + icon('camera') + '</span><small>Text oder Foto hinzufügen</small></div></div>',
      '<div class="dm-file"><span class="dm-file-ic">' + icon('file-text') + '</span><span><b>Arbeitsblatt_3.pdf</b><small>Material · 2 Seiten</small></span></div>',
      '<div class="dm-deadline"><span class="dm-dl-ic">' + icon('calendar') + '</span><span><small>Deadline</small><b>Do, 27. November</b></span></div>',
      '<div class="dm-screen dm-gen"><div class="dm-op"></div><h3><span class="t-work">Deine Mission entsteht</span><span class="t-ok">Deine Mission ist bereit</span></h3>' +
        '<p class="dm-gen-copy">Wir strukturieren dein Ziel und bereiten die nächsten Schritte vor.</p>' +
        '<p class="dm-gen-status"><span class="t-work">Mission wird erstellt …</span><span class="t-ok">Mission bereit</span></p></div>',
      gdots,
      // ---------------------------------------------------------------- route
      '<div class="dm-rg">',
      ui.missionHero({ cls: 'dm-mission', kicker: 'Mission · aktiv', counter: '4 Schritte', title: 'Mathe-Klausur vorbereiten',
        meta: icon('flag') + 'bis Do, 27. Nov · <span class="dm-prog-a">1 von 4 erledigt</span><span class="dm-prog-b">2 von 4 erledigt</span>', progress: 0.25 }),
      '<svg class="dm-route" viewBox="0 0 1920 1080" aria-hidden="true"><path class="dr-base" d="' + routePath(pts) + '"/>' +
        '<path class="dr-line" pathLength="100" d="' + routePath(pts) + '"/>' +
        '<path class="dr-feedback" pathLength="100" d="M640 560 C 622 500, 584 462, 548 430"/><path class="dr-fb-head" d="M546 452 L546 428 L570 430"/></svg>',
      nodes,
      '<div class="dm-flag" style="left:' + FLAG.x + 'px;top:' + FLAG.y + 'px"><span class="df-ic">' + icon('flag') + '</span><div class="df-card"><b>Klausur</b><small>Do, 27. Nov</small></div></div>',
      '</div>',
      // ---------------------------------------------------------------- next step
      '<div class="dm-next-wrap">' + ui.missionHero({ cls: 'dm-next', kicker: 'Focus Mission', counter: 'Schritt 2 von 4', title: 'Mathe-Klausur vorbereiten',
        step: 'Lineare Funktionen üben', stepMeta: icon('timer') + '<span class="dm-next-min">25:00</span> Fokus-Sitzung', button: 'Fokus starten' }) + '</div>',
      // ---------------------------------------------------------------- protection
      '<div class="dm-protect card"><p class="kicker">Fokus-Schutz vorbereiten</p><h3>Was lenkt dich ab?</h3><p class="dm-pr-sub">Ablenkende Apps auswählen</p>' +
        '<div class="dm-apps">' + APPS.map(ui.appTile).join('') + '</div>' +
        '<div class="dm-pr-sum"><span class="dm-shield">' + icon('shield') + '</span><span><b>Geschützter Fokus</b><small><span class="dm-cnt">0</span> Apps ausgewählt</small></span><span class="dm-ready-pill"><i></i>vorbereitet</span></div></div>',
      '<p class="dm-pr-note">Android: Auswahl aus den installierten Apps · Schutzwirkung wird im Pilot getestet</p>',
      // ---------------------------------------------------------------- launch: Quick Focus setup
      '<div class="dm-setup"><h3 class="dm-setup-h">Richte deine Sitzung ein.</h3>' +
        '<p class="dm-setup-k">Woran arbeitest du?</p><div class="dm-task">' + icon('target') + '<b>Lineare Funktionen üben</b>' + icon('pencil') + '</div>' +
        '<div class="dm-prow"><span class="dm-shield is-ready">' + icon('shield') + '</span><span><b>Fokus-Schutz</b><small>4 Apps ausgewählt</small></span>' + icon('chevron-right') + '</div>' +
        '<span class="dm-start">' + icon('play') + 'Fokus starten</span></div>',
      '<div class="dm-dial-wrap">' + ui.dial(25) + '</div>',
      '<canvas class="dm-core"></canvas>',
      '<p class="timer-digits dm-digits">25:00</p>',
      // ---------------------------------------------------------------- active focus
      '<div class="dm-fhead on-dark"><p class="kicker">Fokus-Sitzung</p><h3>Lineare Funktionen üben</h3>' +
        '<p class="dm-chips"><span>' + icon('book-open') + '25 Min.</span><span class="is-accent">Mission · Schritt 2 von 4</span></p></div>',
      '<div class="dm-pill"><i></i>Fokus-Schutz aktiv' + icon('chevron-right') + '</div>',
      // ---------------------------------------------------------------- result
      '<div class="dm-result"><p class="kicker is-dark">Wie weit bist du gekommen?</p>' + ui.resultOptions() + '</div>',
      '<div class="dm-caps">' + captions + '</div>',
      '<p class="dm-tag">Produkt-Demo · feste Beispieldaten</p>'
    ].join(''));

    ctx.op = Pitch.Operator.create(ctx.q('.dm-op'));
    ctx.core = Pitch.FocusCore.create(ctx.q('.dm-core'), ctx.q('.dm-backdrop'), { seed: 4, dialRatio: 1.22, inflowCount: 220 });
    ctx.digits = ctx.q('.dm-digits');
  }

  /* ------------------------------------------------------------------ core helpers */
  function digitsZone(ctx) {
    // Stage pixels (offset* ignore the stage scale). The digits are centred on CENTER.x.
    const el = ctx.digits, h = el.offsetHeight, textW = h * 2.56;
    return { x0: CENTER.x - textW / 2, x1: CENTER.x + textW / 2, y0: el.offsetTop + h * 0.16, y1: el.offsetTop + h * 0.86 };
  }

  function resize(ctx) {
    const dpr = Math.min(2, Math.max(1, ctx.stageScale() * (global.devicePixelRatio || 1)));
    ctx.core.layout({ w: 1920, h: 1080, dpr: dpr, cx: CENTER.x, cy: CENTER.y, R: CORE_R, zone: digitsZone(ctx) });
    ctx.core.render(Pitch.timeline.now() / 1000);
  }

  const CORE_OFF = { inflow: 0, seed: 0, core: 0, g: 0, detail: 0, orbit: 0, dust: 0, stars: 0, alpha: 1, shiftX: 0, shiftY: 0, shiftS: 1, tempo: 1 };
  const CORE_ON = { inflow: 1, seed: 0, core: 1, g: 1, detail: 1, orbit: 1, dust: 1, stars: 1, alpha: 1, shiftX: 0, shiftY: 0, shiftS: 1, tempo: 1 };

  function runCore(ctx) {
    if (ctx.coreLoop) return;
    if (ctx.reduced()) { ctx.core.render(7.4); return; }
    ctx.coreLoop = ctx.loop(function (time) { ctx.core.render(time / 1000); });
  }
  function stopCore(ctx) {
    if (ctx.coreLoop) { Pitch.timeline.cancelId(ctx.coreLoop); ctx.coreLoop = 0; }
  }
  function coreTween(ctx, delay, dur, ease, fn) {
    ctx.tween({ delay: delay, dur: dur, ease: ease, update: fn });
  }

  /* ------------------------------------------------------------------ timer */
  function startCountdown(ctx) {
    stopCountdown(ctx);
    const t0 = Pitch.timeline.now();
    let last = -1;
    ctx.digits.textContent = '25:00';
    ctx.countdown = ctx.loop(function (time) {
      const s = Math.floor((time - t0) / 1000);
      if (s === last) return;
      last = s;
      const left = Math.max(0, 25 * 60 - s);
      ctx.digits.textContent = String(Math.floor(left / 60)).padStart(2, '0') + ':' + String(left % 60).padStart(2, '0');
    });
  }
  function stopCountdown(ctx) {
    if (ctx.countdown) { Pitch.timeline.cancelId(ctx.countdown); ctx.countdown = 0; }
  }

  /* ------------------------------------------------------------------ beats */
  function addClasses(root, beat) { SEQ[beat].forEach(function (s) { root.classList.add(s[1]); }); }

  function setCount(ctx, n) { ctx.q('.dm-cnt').textContent = String(n); }

  function apply(ctx, beat, opts) {
    const root = ctx.root, instant = opts.instant;
    for (let b = 0; b < beat; b++) addClasses(root, b);
    if (instant) addClasses(root, beat);
    else SEQ[beat].forEach(function (s) { ctx.after(s[0], function () { root.classList.add(s[1]); }); });

    // Operator (generate).
    if (beat === 1 && !instant) {
      if (ctx.reduced()) ctx.op.final();
      else { ctx.op.work(ctx); ctx.after(2700, function () { ctx.op.succeed(); }); }
    } else if (beat >= 1) ctx.op.final();
    else ctx.op.first();

    // App selection count.
    if (beat === 4 && !instant) {
      setCount(ctx, 0);
      [880, 1110, 1340, 1570].forEach(function (ms, i) { ctx.after(ms, function () { setCount(ctx, i + 1); }); });
    } else setCount(ctx, beat >= 4 ? 4 : 0);

    // Core and timer.
    if (beat <= 4) { stopCore(ctx); stopCountdown(ctx); ctx.core.set(CORE_OFF); ctx.core.render(0); ctx.digits.textContent = '25:00'; }
    if (beat === 5) {
      stopCountdown(ctx);
      ctx.digits.textContent = '25:00';
      if (instant || ctx.reduced()) { ctx.core.set(CORE_ON); stopCore(ctx); runCore(ctx); }
      else {
        ctx.core.set(CORE_OFF);
        runCore(ctx);
        coreTween(ctx, 1900, 1500, 'inQuad', function (e) { ctx.core.set({ inflow: e }); });
        coreTween(ctx, 2600, 700, 'out', function (e) { ctx.core.set({ seed: e, core: e > 0.3 ? 1 : 0, g: 0.16 * e }); });
        coreTween(ctx, 3300, 1100, 'out', function (e) { ctx.core.set({ g: 0.16 + 0.84 * e, detail: e, seed: 1 - e }); });
        coreTween(ctx, 3600, 1700, 'out', function (e) { ctx.core.set({ orbit: e, dust: e, stars: e }); });
      }
    }
    if (beat === 6) {
      ctx.core.set(CORE_ON); stopCore(ctx); runCore(ctx);
      startCountdown(ctx);
    }
    if (beat === 7) {
      stopCountdown(ctx);
      const target = NODES[1];
      if (instant || ctx.reduced()) {
        ctx.core.set({ alpha: 0 }); stopCore(ctx); ctx.core.render(0);
      } else {
        ctx.core.set(CORE_ON); runCore(ctx);
        coreTween(ctx, 1350, 700, 'out', function (e) { ctx.core.set({ orbit: 1 - e, dust: 1 - e, stars: 1 - e }); });
        coreTween(ctx, 1500, 1200, 'inOut', function (e) {
          ctx.core.set({ shiftX: (target.x - CENTER.x) * e, shiftY: (target.y - CENTER.y) * e, shiftS: 1 - 0.905 * e, detail: 1 - 0.3 * e });
        });
        coreTween(ctx, 2550, 450, 'out', function (e) { ctx.core.set({ alpha: 1 - e }); });
        ctx.after(3050, function () { stopCore(ctx); ctx.core.render(0); });
      }
    }
  }

  function reset(ctx) {
    Pitch.seq.clear(ctx.root, ALL_STEPS);
    stopCore(ctx); stopCountdown(ctx);
    ctx.core.set(CORE_OFF); ctx.core.render(0);
    ctx.op.first();
    ctx.digits.textContent = '25:00';
    setCount(ctx, 0);
  }

  function exit(ctx) { stopCore(ctx); stopCountdown(ctx); ctx.op.stop(); }

  Pitch.registerScene({
    id: 'demo', number: 4, title: 'Produkt-Demo', beats: ['create', 'generate', 'route', 'next', 'protect', 'launch', 'active', 'result'],
    assets: ['./assets/operator/mission-operator-work.png', './assets/operator/mission-operator-success.png'],
    tone: function (beat) { return beat === 5 || beat === 6 ? 'dark' : 'light'; },
    build: build, apply: apply, reset: reset, resize: resize, exit: exit
  });
})(window);
