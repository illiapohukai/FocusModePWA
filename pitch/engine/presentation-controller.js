/*
 * Focus Mode+ Pitch · presentation controller.
 *
 * Owns the seven scenes, their manually advanced beats, scene crossfades, the input lock, pause and
 * the 1920 x 1080 stage fit. A scene route change has exactly one motion owner: the scene
 * crossfade here. Everything inside a scene is driven by beat classes (CSS transitions) and the
 * scene's own timeline scope.
 *
 * Scene contract (see scenes/*.js):
 *   { id, number, title, beats: [ids], tone(beat) -> 'light'|'dark',
 *     build(ctx), enter(ctx), apply(ctx, beat, { instant }), reset(ctx), exit(ctx), resize(ctx) }
 * apply() must bring the scene to the exact final state of `beat`. When it animates, it starts
 * from the settled state of beat - 1: the controller settles a running beat before advancing.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const T = Pitch.timeline;
  const STAGE_W = 1920, STAGE_H = 1080;
  const BEAT_LOCK_MS = 420;
  const SCENE_LOCK_MS = 700;
  const LEAVE_MS = 940;
  const TONES = { light: '#F6F1E7', dark: '#07091B', aubergine: '#160D1B' };

  const defs = [];
  const ctxs = [];
  const listeners = [];
  const state = { index: -1, beat: -1, lockUntil: 0, paused: false, started: false, pausedAnims: [] };
  const leaving = new Map(); // scene index -> timeline step id
  let stageEl = null;
  let scale = 1;

  function registerScene(def) { defs.push(def); }

  function emit(type) {
    for (let i = 0; i < listeners.length; i++) listeners[i](type, snapshot());
  }
  function on(fn) { listeners.push(fn); }

  function snapshot() {
    const def = defs[state.index];
    return {
      index: state.index,
      beat: state.beat,
      sceneId: def ? def.id : null,
      sceneNumber: def ? def.number : 0,
      sceneTitle: def ? def.title : '',
      beatId: def && state.beat >= 0 ? def.beats[state.beat] : null,
      beatCount: def ? def.beats.length : 0,
      sceneCount: defs.length,
      paused: state.paused,
      started: state.started
    };
  }

  function reflow(el) { return el.offsetWidth; }

  /* Cumulative beat classes: beat-0 … beat-k. Instant changes never start CSS transitions. */
  function setBeatClasses(ctx, beat, instant) {
    const root = ctx.root, n = ctx.def.beats.length;
    if (instant) { root.classList.add('is-instant'); reflow(root); }
    root.dataset.beat = String(beat);
    for (let i = 0; i < n; i++) root.classList.toggle('beat-' + i, i <= beat);
    if (instant) {
      reflow(root);
      root.classList.remove('is-instant');
      reflow(root);
    }
  }

  function makeCtx(def, root) {
    const scope = def.id, loopScope = def.id + ':loop';
    return {
      def: def,
      root: root,
      scope: scope,
      data: global.PITCH_CONTENT,
      after: function (ms, fn) { return T.after(scope, reducedScale(ms), fn); },
      tween: function (o) { o.dur = reducedScale(o.dur || 0); o.delay = reducedScale(o.delay || 0); return T.tween(scope, o); },
      loop: function (fn) { return T.frame(loopScope, fn); },
      stopLoops: function () { T.cancel(loopScope); },
      reduced: function () { return Boolean(Pitch.modes && Pitch.modes.reducedMotion); },
      stageScale: function () { return scale; },
      q: function (sel) { return root.querySelector(sel); },
      qa: function (sel) { return Array.prototype.slice.call(root.querySelectorAll(sel)); },
      settle: function (beat) { setBeatClasses(this, beat, true); }
    };
  }

  /* Reduced motion keeps sequences but compresses their timing. */
  function reducedScale(ms) {
    return Pitch.modes && Pitch.modes.reducedMotion ? Math.min(ms, ms * 0.25) : ms;
  }

  /* Preload and decode only the current and the next scene's assets. */
  const preloaded = new Map();
  function preload(index) {
    const def = defs[index];
    if (!def || !def.assets) return;
    def.assets.forEach(function (src) {
      if (preloaded.has(src)) return;
      const img = new Image();
      img.decoding = 'async';
      img.src = src;
      preloaded.set(src, img);
      if (img.decode) img.decode().catch(function () {});
    });
  }

  function lock(ms) { state.lockUntil = performance.now() + ms; }
  function locked() { return performance.now() < state.lockUntil; }

  function updateTone() {
    const def = defs[state.index];
    if (!def) return;
    const tone = typeof def.tone === 'function' ? def.tone(state.beat) : (def.tone || 'light');
    document.documentElement.style.setProperty('--letterbox', TONES[tone] || tone);
    document.documentElement.dataset.tone = tone === 'light' ? 'light' : 'dark';
  }

  function mount(index) {
    const ctx = ctxs[index];
    const pendingLeave = leaving.get(index);
    if (pendingLeave) { T.cancelId(pendingLeave); leaving.delete(index); finishLeave(index); }
    ctx.root.classList.add('is-mounted');
    ctx.root.removeAttribute('aria-hidden');
    reflow(ctx.root);
    ctx.root.classList.add('is-active');
    if (ctx.def.resize) ctx.def.resize(ctx);
    if (ctx.def.enter) ctx.def.enter(ctx);
  }

  function leave(index) {
    const ctx = ctxs[index];
    T.cancel(ctx.scope);
    ctx.root.classList.remove('is-active');
    ctx.root.classList.add('is-leaving');
    ctx.root.setAttribute('aria-hidden', 'true');
    leaving.set(index, T.after('controller', LEAVE_MS, function () {
      leaving.delete(index);
      finishLeave(index);
    }));
  }

  /* After the crossfade: stop everything, reset to the pre-state, unmount (display: none). */
  function finishLeave(index) {
    const ctx = ctxs[index];
    T.cancel(ctx.scope);
    ctx.stopLoops();
    if (ctx.def.exit) ctx.def.exit(ctx);
    if (ctx.def.reset) ctx.def.reset(ctx);
    setBeatClasses(ctx, -1, true);
    delete ctx.root.dataset.beat;
    ctx.root.classList.remove('is-leaving', 'is-mounted', 'is-active', 'blend-in', 'blend-out');
  }

  /* An instant beat keeps transitions off while the scene's own apply() adds its classes too, so
     every class that belongs to the settled state lands without animating. */
  function applyBeat(ctx, beat, instant) {
    const root = ctx.root;
    T.cancel(ctx.scope);
    if (instant) { root.classList.add('is-instant'); reflow(root); }
    setBeatClasses(ctx, beat, false);
    if (ctx.def.apply) ctx.def.apply(ctx, beat, { instant: Boolean(instant) });
    if (instant) {
      reflow(root);
      root.classList.remove('is-instant');
      reflow(root);
    }
  }

  /* Bring the running beat to its final state so the next beat animates from a clean state. */
  function settleCurrent() {
    const ctx = ctxs[state.index];
    if (!ctx || state.beat < 0) return;
    if (T.pending(ctx.scope) > 0) applyBeat(ctx, state.beat, true);
  }

  function goTo(index, beat, opts) {
    opts = opts || {};
    if (index < 0 || index >= defs.length) return false;
    const def = defs[index];
    beat = Math.max(opts.allowStandby ? -1 : 0, Math.min(def.beats.length - 1, beat));
    resume();
    if (index !== state.index) {
      const blend = Boolean(defs[index].blendIn) && state.index === index - 1;
      if (state.index >= 0) { ctxs[state.index].root.classList.toggle('blend-out', blend); leave(state.index); }
      const ctx = ctxs[index];
      ctx.root.classList.toggle('blend-in', blend);
      if (ctx.def.reset) ctx.def.reset(ctx);
      setBeatClasses(ctx, -1, true);
      state.index = index;
      state.beat = -1;
      preload(index);
      preload(index + 1);
      mount(index);
      if (beat >= 0) {
        if (opts.instant) applyBeat(ctx, beat, true);
        else {
          // Settle every beat before the target, then animate the target beat.
          if (beat > 0) applyBeat(ctx, beat - 1, true);
          applyBeat(ctx, beat, false);
        }
      }
      state.beat = beat;
      lock(SCENE_LOCK_MS);
    } else {
      const ctx = ctxs[index];
      if (beat === state.beat && !opts.replay) return false;
      if (opts.instant || beat < state.beat) applyBeat(ctx, beat, true);
      else {
        settleCurrent();
        if (beat > state.beat + 1) applyBeat(ctx, beat - 1, true);
        applyBeat(ctx, beat, false);
      }
      state.beat = beat;
      lock(BEAT_LOCK_MS);
    }
    updateTone();
    emit('change');
    return true;
  }

  function markStarted() {
    if (!state.started) { state.started = true; emit('start'); }
  }

  /* Space: next beat; after the last beat, the next scene. */
  function next() {
    if (locked()) return;
    markStarted();
    const def = defs[state.index];
    if (state.beat < def.beats.length - 1) goTo(state.index, state.beat + 1);
    else if (state.index < defs.length - 1) goTo(state.index + 1, 0);
  }

  /* Shift + Space: previous beat (instant); before the first beat, the previous scene's last beat. */
  function prev() {
    if (locked()) return;
    if (state.beat > 0) goTo(state.index, state.beat - 1, { instant: true });
    else if (state.index > 0) goTo(state.index - 1, defs[state.index - 1].beats.length - 1, { instant: true });
  }

  function nextScene() {
    if (locked() || state.index >= defs.length - 1) return;
    markStarted();
    goTo(state.index + 1, 0);
  }

  /* Left arrow: back to the settled final state of the previous scene. */
  function prevScene() {
    if (locked() || state.index <= 0) return;
    goTo(state.index - 1, defs[state.index - 1].beats.length - 1, { instant: true });
  }

  /* R: replay the current scene from its pre-state. */
  function restart() {
    if (locked()) return;
    const ctx = ctxs[state.index];
    resume();
    T.cancel(ctx.scope);
    ctx.stopLoops();
    if (ctx.def.exit) ctx.def.exit(ctx);
    if (ctx.def.reset) ctx.def.reset(ctx);
    setBeatClasses(ctx, -1, true);
    state.beat = -1;
    if (ctx.def.enter) ctx.def.enter(ctx);
    applyBeat(ctx, 0, false);
    state.beat = 0;
    lock(SCENE_LOCK_MS);
    updateTone();
    emit('change');
  }

  /* P: pause every timeline job and every running CSS transition/animation. */
  function pause() {
    if (state.paused) return;
    state.paused = true;
    T.pause();
    // Flush pending style changes first, so transitions triggered in this frame exist and pause too.
    reflow(stageEl);
    state.pausedAnims = document.getAnimations().filter(function (a) { return a.playState === 'running' || a.pending; });
    state.pausedAnims.forEach(function (a) { a.pause(); });
    document.documentElement.classList.add('is-paused');
    emit('pause');
  }
  function resume() {
    if (!state.paused) return;
    state.paused = false;
    state.pausedAnims.forEach(function (a) { try { a.play(); } catch (_) {} });
    state.pausedAnims = [];
    T.resume();
    document.documentElement.classList.remove('is-paused');
    emit('pause');
  }
  function togglePause() { if (state.paused) resume(); else pause(); }

  /* Fit the fixed 1920 x 1080 stage into the viewport (letterboxed, centred). */
  function fit() {
    const vw = global.innerWidth, vh = global.innerHeight;
    scale = Math.min(vw / STAGE_W, vh / STAGE_H);
    stageEl.style.setProperty('--stage-scale', String(scale));
    stageEl.style.transform = 'translate(-50%, -50%) scale(' + scale + ')';
    for (let i = 0; i < ctxs.length; i++) {
      const ctx = ctxs[i];
      if (ctx.root.classList.contains('is-mounted') && ctx.def.resize) ctx.def.resize(ctx);
    }
    emit('resize');
  }

  function init(opts) {
    opts = opts || {};
    stageEl = document.getElementById('stage');
    defs.sort(function (a, b) { return a.number - b.number; });
    defs.forEach(function (def) {
      const root = document.getElementById('scene-' + def.id);
      const ctx = makeCtx(def, root);
      ctxs.push(ctx);
      root.setAttribute('aria-hidden', 'true');
      if (def.build) def.build(ctx);
    });
    fit();
    let resizeQueued = false;
    global.addEventListener('resize', function () {
      if (resizeQueued) return;
      resizeQueued = true;
      global.requestAnimationFrame(function () { resizeQueued = false; fit(); });
    });
    const startIndex = Math.max(0, Math.min(defs.length - 1, (opts.scene || 1) - 1));
    if (opts.beat != null) goTo(startIndex, opts.beat, { instant: true, allowStandby: true });
    else goTo(startIndex, startIndex === 0 ? -1 : 0, { instant: true, allowStandby: true });
    state.lockUntil = 0;
  }

  Pitch.registerScene = registerScene;
  Pitch.controller = Object.freeze({
    init: init,
    on: on,
    snapshot: snapshot,
    goTo: goTo,
    next: next,
    prev: prev,
    nextScene: nextScene,
    prevScene: prevScene,
    restart: restart,
    togglePause: togglePause,
    pause: pause,
    resume: resume,
    scenes: function () { return defs.slice(); },
    scale: function () { return scale; }
  });
})(window);
