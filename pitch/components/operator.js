/*
 * Focus Mode+ Pitch · pixel operator player.
 *
 * Plays the approved PWA operator frames (assets/operator/mission-operator-*.png, cut and keyed
 * from the approved source video without redrawing; see mission-operator.json). Frames are stepped
 * on the pitch timeline, so pause, restart and reduced motion behave like every other sequence.
 * 'work' loops while the Mission "is being prepared"; 'success' (the planet forming in the
 * thought cloud) plays exactly once and holds its last frame.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};

  const META = {
    frameWidth: 266,
    frameHeight: 358,
    work: { image: './assets/operator/mission-operator-work.png', columns: 4, rows: 4, frameCount: 16, fps: 12,
      exitToSuccess: [0, 2, 1, 0, 4, 5, 5, null, null, 5, 4, 1, 1, 0, 3, 3] },
    success: { image: './assets/operator/mission-operator-success.png', columns: 6, rows: 4, frameCount: 24, fps: 12 }
  };

  function create(host) {
    host.classList.add('operator');
    host.innerHTML = '<span class="op-sheet op-work"></span><span class="op-sheet op-success"></span>';
    const work = host.querySelector('.op-work'), success = host.querySelector('.op-success');
    const st = { mode: 'idle', frame: 0, acc: 0, wantSuccess: false, onDone: null, loopId: 0 };

    function place(el, sheet, index) {
      const col = index % sheet.columns, row = Math.floor(index / sheet.columns);
      el.style.backgroundPosition = (-col * META.frameWidth) + 'px ' + (-row * META.frameHeight) + 'px';
    }
    function show(which, index) {
      work.style.opacity = which === 'work' ? '1' : '0';
      success.style.opacity = which === 'success' ? '1' : '0';
      place(which === 'work' ? work : success, which === 'work' ? META.work : META.success, index);
    }

    function step(ctx) {
      if (st.mode === 'work') {
        if (st.wantSuccess) {
          const entry = META.work.exitToSuccess[st.frame];
          if (entry != null) { st.mode = 'success'; st.frame = entry; show('success', st.frame); return; }
        }
        st.frame = (st.frame + 1) % META.work.frameCount;
        show('work', st.frame);
      } else if (st.mode === 'success') {
        if (st.frame < META.success.frameCount - 1) { st.frame += 1; show('success', st.frame); }
        else { st.mode = 'done'; stop(); if (st.onDone) { const fn = st.onDone; st.onDone = null; fn(); } }
      }
    }

    function start(ctx) {
      stop();
      const interval = 1000 / META.work.fps;
      st.acc = 0;
      st.loopId = ctx.loop(function (time, dt) {
        st.acc += dt;
        while (st.acc >= interval && st.loopId) { st.acc -= interval; step(ctx); }
      });
    }
    function stop() {
      if (st.loopId) { Pitch.timeline.cancelId(st.loopId); st.loopId = 0; }
    }

    return {
      /* Begin the working loop (frame 0). */
      work: function (ctx) { st.mode = 'work'; st.frame = 0; st.wantSuccess = false; show('work', 0); start(ctx); },
      /* Leave the loop at the next mapped frame and play the success once. */
      succeed: function (onDone) { st.wantSuccess = true; st.onDone = onDone || null; },
      /* Static final frame (settled state, reduced motion). */
      final: function () { stop(); st.mode = 'done'; st.frame = META.success.frameCount - 1; show('success', st.frame); },
      first: function () { stop(); st.mode = 'idle'; st.frame = 0; show('work', 0); },
      stop: stop
    };
  }

  Pitch.Operator = Object.freeze({ create: create, META: META });
})(window);
