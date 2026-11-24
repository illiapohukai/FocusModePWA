/*
 * Focus Mode+ Pitch · timeline (the single JS motion clock).
 *
 * One requestAnimationFrame loop drives every scheduled step, tween and per-frame renderer.
 * Time is "play time": it stops while the presentation is paused, so pausing and resuming never
 * skips or doubles a step. Every job belongs to a scope (usually a scene id), and a scope can be
 * cancelled in one call when a scene restarts or exits. The loop sleeps when nothing is scheduled.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};

  const EASE = {
    linear: function (k) { return k; },
    inOut: function (k) { return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2; },
    out: function (k) { const m = 1 - k; return 1 - m * m * m; },
    outQuint: function (k) { const m = 1 - k; return 1 - m * m * m * m * m; },
    in: function (k) { return k * k * k; },
    inQuad: function (k) { return k * k; },
    outBack: function (k) { const c = 1.35, m = k - 1; return 1 + (c + 1) * m * m * m + c * m * m; }
  };

  let playTime = 0;
  let lastReal = 0;
  let rafId = 0;
  let paused = false;
  let seq = 0;
  const jobs = []; // { id, scope, kind: 'step'|'tween'|'frame', ... }

  function now() { return playTime; }

  let inTick = false;

  /* Start the loop from idle. While it runs, frames chain on their own rAF timestamps, so the
     time spent processing a frame is never lost (play time tracks wall-clock CSS transitions). */
  function ensureLoop() {
    if (paused || rafId || inTick || !jobs.length) return;
    lastReal = performance.now();
    rafId = global.requestAnimationFrame(tick);
  }

  function tick(real) {
    rafId = 0;
    if (paused) return;
    // Only a real stall (hidden tab, debugger) is clamped, so a sequence never jumps to its end.
    const dt = Math.min(1000, Math.max(0, real - lastReal));
    lastReal = real;
    playTime += dt;
    inTick = true;
    try {
      const snapshot = jobs.slice();
      for (let i = 0; i < snapshot.length; i++) {
        const j = snapshot[i];
        if (j.dead) continue;
        if (j.kind === 'step') {
          if (playTime >= j.at) { remove(j); j.fn(); }
        } else if (j.kind === 'tween') {
          if (playTime < j.start) continue;
          const k = j.dur <= 0 ? 1 : Math.min(1, (playTime - j.start) / j.dur);
          j.update(j.ease(k), k);
          if (k >= 1) { remove(j); if (j.done) j.done(); }
        } else if (j.kind === 'frame') {
          j.fn(playTime, dt);
        }
      }
    } finally {
      inTick = false;
      if (!paused && !rafId && jobs.length) rafId = global.requestAnimationFrame(tick);
    }
  }

  function remove(j) {
    j.dead = true;
    const i = jobs.indexOf(j);
    if (i >= 0) jobs.splice(i, 1);
  }

  function add(j) {
    j.id = ++seq;
    jobs.push(j);
    ensureLoop();
    return j.id;
  }

  /* Run fn after ms of play time. */
  function after(scope, ms, fn) {
    if (ms <= 0) { fn(); return 0; }
    return add({ scope: scope, kind: 'step', at: playTime + ms, fn: fn });
  }

  /* Tween a value: opts { dur, delay, ease, update(e, k), done() }. */
  function tween(scope, opts) {
    const ease = typeof opts.ease === 'function' ? opts.ease : (EASE[opts.ease] || EASE.inOut);
    if (!(opts.dur > 0) && !(opts.delay > 0)) {
      opts.update(1, 1);
      if (opts.done) opts.done();
      return 0;
    }
    return add({ scope: scope, kind: 'tween', start: playTime + (opts.delay || 0), dur: opts.dur || 0, ease: ease, update: opts.update, done: opts.done });
  }

  /* Call fn(time, dt) every frame until the scope is cancelled. */
  function frame(scope, fn) {
    return add({ scope: scope, kind: 'frame', fn: fn });
  }

  function cancel(scope) {
    for (let i = jobs.length - 1; i >= 0; i--) {
      if (jobs[i].scope === scope) { jobs[i].dead = true; jobs.splice(i, 1); }
    }
  }

  function cancelId(id) {
    for (let i = jobs.length - 1; i >= 0; i--) {
      if (jobs[i].id === id) { jobs[i].dead = true; jobs.splice(i, 1); return; }
    }
  }

  /* Number of pending steps and tweens (frames are ambient and not counted). */
  function pending(scope) {
    let n = 0;
    for (let i = 0; i < jobs.length; i++) if (jobs[i].scope === scope && jobs[i].kind !== 'frame') n++;
    return n;
  }

  function pause() {
    if (paused) return;
    paused = true;
    if (rafId) { global.cancelAnimationFrame(rafId); rafId = 0; }
  }

  function resume() {
    if (!paused) return;
    paused = false;
    ensureLoop();
  }

  function debugCounts() {
    const counts = {};
    jobs.forEach(function (j) { counts[j.scope + ':' + j.kind] = (counts[j.scope + ':' + j.kind] || 0) + 1; });
    return counts;
  }

  Pitch.timeline = Object.freeze({
    EASE: EASE,
    now: now,
    after: after,
    tween: tween,
    frame: frame,
    cancel: cancel,
    cancelId: cancelId,
    pending: pending,
    pause: pause,
    resume: resume,
    isPaused: function () { return paused; },
    debugCounts: debugCounts
  });
})(window);
