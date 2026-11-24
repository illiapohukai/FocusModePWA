/*
 * Scene 7 · Pilot and final CTA.
 * Beat pilot:   dark stage; the Cosmic Core forms as a portal around the QR slot; 20 Android-Testnutzer,
 *               14 Tage.
 * Beat measure: measurement route and internal pilot targets (labelled as internal, not benchmarks).
 * Beat close:   "Nicht die nächsten Features. Die ersten echten Nutzer." The astronaut floats in near
 *               the portal.
 * The QR is one file (PITCH_CONTENT.qrAsset) shown at 300 x 300; the placeholder is not scannable.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon, C = Pitch.content.data;

  const PORTAL = { x: 1400, y: 572, R: 236 };

  function build(ctx) {
    const route = (C.pilotRoute || []).map(function (s, i) {
      return '<li style="--i:' + i + '"><i></i><span>' + esc(s) + '</span></li>';
    }).join('');
    const targets = [
      [C.activationTarget, C.activationLabel], [C.threeSessionTarget, C.threeSessionLabel],
      [C.daySevenTarget, C.daySevenLabel], [C.followUpInterviews, C.followUpLabel]
    ].map(function (t, i) { return '<div class="pl-t" style="--i:' + i + '"><b>' + esc(t[0]) + '</b><span>' + esc(t[1]) + '</span></div>'; }).join('');

    Pitch.dom.mount(ctx.root, [
      '<div class="pl-bg"></div>',
      '<canvas class="pl-backdrop"></canvas>',
      '<canvas class="pl-core"></canvas>',
      '<div class="pl-qr"><img src="' + esc(C.qrAsset) + '" width="300" height="300" alt="QR-Code zum Pilot" draggable="false"></div>',
      '<span class="pl-astro-wrap">' + ui.astronaut('h146 pl-astro') + '</span>',
      '<div class="pl-left on-dark">',
      '  <div class="pl-a">' + ui.kicker('Nächster Schritt · Android-Pilot') +
      '    <p class="pl-big"><b>' + esc(C.pilotSize) + '</b><span>' + esc(C.pilotSizeLabel) + '</span></p>' +
      '    <p class="pl-big pl-dur"><b>' + esc(C.pilotDuration) + '</b></p></div>',
      '  <div class="pl-m"><ol class="pl-route">' + route + '</ol><div class="pl-targets">' + targets + '</div>' +
      '    <p class="pl-note">' + icon('flag') + esc(C.pilotTargetsLabel) + '</p></div>',
      '</div>',
      '<div class="pl-close on-dark"><h2 class="display pl-final"><span>Nicht die nächsten Features.</span><span class="pl-final-b">Die ersten echten Nutzer.</span></h2>' +
        '<p class="pl-recap">' + esc(C.pilotSize + ' ' + C.pilotSizeLabel + ' · ' + C.pilotDuration + ' · ' + (C.qrCaption || '')) + '</p></div>'
    ].join(''));
    ctx.core = Pitch.FocusCore.create(ctx.q('.pl-core'), ctx.q('.pl-backdrop'), { seed: 7, density: 0.85 });
  }

  function resize(ctx) {
    const dpr = Math.min(2, Math.max(1, ctx.stageScale() * (global.devicePixelRatio || 1)));
    ctx.core.layout({ w: 1920, h: 1080, dpr: dpr, cx: PORTAL.x, cy: PORTAL.y, R: PORTAL.R });
    ctx.core.render(Pitch.timeline.now() / 1000);
  }

  function run(ctx) {
    ctx.stopLoops();
    if (ctx.reduced()) { ctx.core.render(7.4); return; }
    ctx.loop(function (time) { ctx.core.render(time / 1000); });
  }

  const ON = { core: 1, g: 1, detail: 1, orbit: 1, dust: 1, stars: 1, seed: 0, alpha: 1, inflow: 0, tempo: 0.8 };

  function apply(ctx, beat, opts) {
    if (beat === 0 && !opts.instant && !ctx.reduced()) {
      ctx.core.set({ core: 1, g: 0, detail: 0, orbit: 0, dust: 0, stars: 0, seed: 0, alpha: 1, inflow: 0, tempo: 0.8 });
      run(ctx);
      ctx.tween({ delay: 250, dur: 700, ease: 'out', update: function (e) { ctx.core.set({ seed: e, g: 0.2 * e }); } });
      ctx.tween({ delay: 800, dur: 1300, ease: 'out', update: function (e) { ctx.core.set({ g: 0.2 + 0.8 * e, detail: e, seed: 1 - e }); } });
      ctx.tween({ delay: 1200, dur: 1700, ease: 'out', update: function (e) { ctx.core.set({ orbit: e, dust: e, stars: e }); } });
    } else {
      ctx.core.set(ON);
      run(ctx);
    }
  }

  function reset(ctx) {
    ctx.stopLoops();
    ctx.core.set({ core: 0, g: 0, detail: 0, orbit: 0, dust: 0, stars: 0, seed: 0 });
    ctx.core.render(0);
  }

  Pitch.registerScene({
    id: 'pilot', number: 7, title: 'Pilot · CTA', beats: ['pilot', 'measure', 'close'],
    assets: ['./assets/operator/orbit-standing.png', C.qrAsset],
    tone: function () { return 'aubergine'; },
    build: build, apply: apply, reset: reset, resize: resize,
    exit: function (ctx) { ctx.stopLoops(); }
  });
})(window);
