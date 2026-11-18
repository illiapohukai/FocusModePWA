/* v17.14 Mission Generation Operator.
   Single visual owner of the AI Mission-generation waiting state (#app-screen-analyzing).
   It shows the approved pixel-art operator: the terminal "work" segment loops while the real
   create-mission request is pending, and the one-shot "success" segment (the planet in the
   thought cloud) plays exactly once after a validated Mission response.

   Business logic stays in src/services/ai-service.js (analyzeGoal): request, timeout, abort,
   parsing, validation, error and retry. This module never starts a request; it only observes
   the lifecycle through begin(), succeed(), fail() and cancel(), and reports back when the
   success animation has finished so the existing route (setAppScreen('confirm')) can follow.

   States: idle → request-starting → working-loop → success-pending → success-transition →
   completed → cleanup → idle. error, timeout and canceled always go to cleanup without the
   planet. Leaving the screen (any route change) also cleans up and turns a pending success
   into `false`, so a late response can never animate or navigate on another screen.

   Assets: assets/operator/mission-operator.json (frame metadata) and two sprite sheets. One
   canvas is redrawn at the sheet's frame rate with requestAnimationFrame; no DOM nodes are
   created per frame. Idle cost: no frame loop, timers or listeners besides one class observer
   on the screen element. */
(function (global) {
  'use strict';
  const doc = global.document;
  if (!doc || global.MissionOperator) return;

  const META_URL = './assets/operator/mission-operator.json';
  const SLOW_NOTICE_MS = 9000;
  const REDUCED_FADE_MS = 200;

  const STATES = Object.freeze({
    IDLE: 'idle',
    STARTING: 'request-starting',
    WORKING: 'working-loop',
    SUCCESS_PENDING: 'success-pending',
    SUCCESS: 'success-transition',
    COMPLETED: 'completed',
    ERROR: 'error',
    CANCELED: 'canceled',
    TIMEOUT: 'timeout',
    CLEANUP: 'cleanup'
  });

  const COPY = Object.freeze({
    de: Object.freeze({
      title: 'Deine Mission entsteht',
      copy: 'Wir strukturieren dein Ziel und bereiten die nächsten Schritte vor.',
      working: 'Mission wird erstellt …',
      slow: 'Das dauert einen Moment länger. Deine Eingaben bleiben erhalten.',
      readyTitle: 'Deine Mission ist bereit',
      ready: 'Mission bereit',
      failed: 'Die Mission konnte nicht erstellt werden.'
    }),
    en: Object.freeze({
      title: 'Your Mission is taking shape',
      copy: 'We are structuring your goal and preparing the next steps.',
      working: 'Creating your Mission …',
      slow: 'This is taking a little longer. Your input is kept.',
      readyTitle: 'Your Mission is ready',
      ready: 'Mission ready',
      failed: 'The Mission could not be created.'
    })
  });

  /* Static markup is German; the app-wide translator (src/core/bootstrap.js) swaps it. */
  if (typeof I18N !== 'undefined' && I18N && I18N.en) {
    Object.assign(I18N.en, {
      [COPY.de.title]: COPY.en.title,
      [COPY.de.copy]: COPY.en.copy,
      [COPY.de.working]: COPY.en.working
    });
  }

  let state = STATES.IDLE;
  let run = null;        // the current visual run, one per analyzeGoal() request
  let runCounter = 0;
  let ui = null;
  let assets = null;     // Promise<{ meta, work, success }>, loaded on first use and reused
  let observer = null;

  /* ---------- helpers ---------- */
  function now() {
    return global.performance && typeof global.performance.now === 'function' ? global.performance.now() : Date.now();
  }

  function isEnglish() {
    return (doc.documentElement.lang || 'de') === 'en';
  }

  function copy() {
    return isEnglish() ? COPY.en : COPY.de;
  }

  function reducedMotion() {
    try {
      return Boolean(global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch (_) {
      return false;
    }
  }

  function getUI() {
    if (ui && ui.screen.isConnected) return ui;
    const screen = doc.getElementById('app-screen-analyzing');
    const canvas = doc.getElementById('missionOperatorCanvas');
    if (!screen || !canvas) return null;
    ui = {
      screen,
      canvas,
      ctx: canvas.getContext('2d'),
      finalCanvas: doc.getElementById('missionOperatorFinal'),
      title: doc.getElementById('missionGenTitle'),
      text: doc.getElementById('missionGenCopy'),
      status: doc.getElementById('missionGenStatus')
    };
    return ui;
  }

  function setState(next) {
    state = next;
    const u = getUI();
    if (u) u.screen.dataset.mgenState = next;
  }

  function setText(el, value) {
    if (el && el.textContent !== value) el.textContent = value;
  }

  function loadImage(url) {
    return new Promise(function (resolve, reject) {
      const img = new global.Image();
      img.decoding = 'async';
      img.onload = function () {
        if (typeof img.decode === 'function') img.decode().then(function () { resolve(img); }, function () { resolve(img); });
        else resolve(img);
      };
      img.onerror = function () { reject(new Error('mission operator image failed: ' + url)); };
      img.src = url;
    });
  }

  function loadAssets() {
    if (assets) return assets;
    assets = global.fetch(META_URL, { cache: 'default' })
      .then(function (res) {
        if (!res.ok) throw new Error('mission operator metadata ' + res.status);
        return res.json();
      })
      .then(function (meta) {
        const base = new URL(META_URL, doc.baseURI);
        return Promise.all([
          loadImage(new URL(meta.work.image, base).href),
          loadImage(new URL(meta.success.image, base).href)
        ]).then(function (images) {
          return { meta: meta, work: images[0], success: images[1] };
        });
      });
    /* A failed load is not cached, so a later run can try again; the screen still works
       without the animation (static copy, status and cancel). */
    assets.catch(function () { assets = null; });
    return assets;
  }

  /* ---------- drawing ---------- */
  function drawFrame(target, ctx, sheet, segMeta, meta, index) {
    if (!ctx || !sheet) return;
    const fw = meta.frameWidth;
    const fh = meta.frameHeight;
    const col = index % segMeta.columns;
    const row = Math.floor(index / segMeta.columns);
    ctx.clearRect(0, 0, target.width, target.height);
    ctx.drawImage(sheet, col * fw, row * fh, fw, fh, 0, 0, fw, fh);
  }

  function prepareCanvas(canvas, meta) {
    if (!canvas) return null;
    if (canvas.width !== meta.frameWidth) canvas.width = meta.frameWidth;
    if (canvas.height !== meta.frameHeight) canvas.height = meta.frameHeight;
    const ctx = canvas.getContext('2d');
    if (ctx) ctx.imageSmoothingEnabled = false;
    return ctx;
  }

  function clearCanvases() {
    const u = getUI();
    if (!u) return;
    [u.canvas, u.finalCanvas].forEach(function (c) {
      if (!c) return;
      const ctx = c.getContext('2d');
      if (ctx) ctx.clearRect(0, 0, c.width, c.height);
    });
    if (u.finalCanvas) u.finalCanvas.classList.remove('is-visible');
    u.screen.classList.remove('mgen-has-art');
  }

  /* ---------- run lifecycle ---------- */
  function stopFrames(r) {
    if (!r) return;
    if (r.frameId) {
      global.cancelAnimationFrame(r.frameId);
      r.frameId = 0;
    }
    r.timers.forEach(function (id) { global.clearTimeout(id); });
    r.timers.clear();
  }

  function later(r, ms, fn) {
    const id = global.setTimeout(function () {
      r.timers.delete(id);
      if (run === r) fn();
    }, ms);
    r.timers.add(id);
    return id;
  }

  function settle(r, value) {
    if (r && r.resolveSuccess) {
      const resolve = r.resolveSuccess;
      r.resolveSuccess = null;
      resolve(value);
    }
  }

  function cleanup(reason) {
    const r = run;
    if (!r) {
      if (state !== STATES.IDLE) setState(STATES.IDLE);
      return;
    }
    if (reason) setState(reason);
    stopFrames(r);
    settle(r, false);
    run = null;
    setState(STATES.CLEANUP);
    clearCanvases();
    const u = getUI();
    if (u) u.screen.removeAttribute('aria-busy');
    setState(STATES.IDLE);
  }

  function schedule(r) {
    if (r.frameId || run !== r) return;
    r.frameId = global.requestAnimationFrame(function (ts) {
      r.frameId = 0;
      if (run !== r) return;
      tick(r, ts);
    });
  }

  function tick(r, ts) {
    const seg = r.segment === 'success' ? r.assets.meta.success : r.assets.meta.work;
    const frameMs = 1000 / seg.fps;
    if (!r.nextAt) r.nextAt = ts + frameMs;
    if (ts >= r.nextAt) {
      advance(r);
      if (run !== r) return;
      r.nextAt += frameMs;
      /* After a hidden tab or a long task, continue from now instead of racing to catch up. */
      if (ts - r.nextAt > frameMs * 3) r.nextAt = ts + frameMs;
    }
    if (r.segment === 'work' || r.segment === 'success') schedule(r);
  }

  /* The minimum working moment counts from the first drawn work frame, so a very fast
     response never flashes the planet straight after the screen appears. */
  function canExit(r) {
    if (!r.successRequested) return false;
    return now() - r.workShownAt >= r.assets.meta.minWorkMs;
  }

  function enterSuccess(r, index) {
    r.segment = 'success';
    r.index = index;
    setState(STATES.SUCCESS);
    announceReady(r);
    drawFrame(ui.canvas, ui.ctx, r.assets.success, r.assets.meta.success, r.assets.meta, index);
  }

  function advance(r) {
    const meta = r.assets.meta;
    if (r.segment === 'work') {
      const next = r.index >= meta.work.loopEnd ? meta.work.loopStart : r.index + 1;
      if (canExit(r)) {
        if (!r.exitWaitSince) r.exitWaitSince = now();
        const entry = meta.work.exitToSuccess[next];
        const waitedTooLong = now() - r.exitWaitSince >= meta.maxExitWaitMs;
        if (entry !== null && entry !== undefined) {
          enterSuccess(r, entry);
          return;
        }
        if (waitedTooLong) {
          enterSuccess(r, meta.success.successStart);
          return;
        }
      }
      r.index = next;
      drawFrame(ui.canvas, ui.ctx, r.assets.work, meta.work, meta, next);
      return;
    }
    if (r.segment === 'success') {
      if (r.index >= meta.success.successEnd) {
        finish(r, meta.success.finalHoldMs);
        return;
      }
      r.index += 1;
      drawFrame(ui.canvas, ui.ctx, r.assets.success, meta.success, meta, r.index);
    }
  }

  function finish(r, holdMs) {
    r.segment = 'hold';
    setState(STATES.COMPLETED);
    stopFrames(r);
    later(r, holdMs, function () { settle(r, true); });
  }

  function announceReady(r) {
    const u = getUI();
    if (!u || r.announcedReady) return;
    r.announcedReady = true;
    const c = copy();
    setText(u.title, c.readyTitle);
    setText(u.status, c.ready);
    u.screen.setAttribute('aria-busy', 'false');
  }

  function startVisuals(r) {
    const u = getUI();
    if (!u || run !== r) return;
    const meta = r.assets.meta;
    u.ctx = prepareCanvas(u.canvas, meta);
    prepareCanvas(u.finalCanvas, meta);
    u.screen.classList.add('mgen-has-art');
    r.index = meta.work.loopStart;
    drawFrame(u.canvas, u.ctx, r.assets.work, meta.work, meta, r.index);
    r.workShownAt = now();
    if (state === STATES.STARTING) setState(STATES.WORKING);
    if (r.reduced) {
      if (r.successRequested) reducedSuccess(r);
      return;
    }
    r.segment = 'work';
    if (r.successRequested) setState(STATES.SUCCESS_PENDING);
    schedule(r);
  }

  /* Reduced motion: one still work frame, then a short crossfade to the final success frame. */
  function reducedSuccess(r) {
    const u = getUI();
    if (!u || run !== r || r.reducedSuccessStarted) return;
    const meta = r.assets.meta;
    const wait = Math.max(0, meta.minWorkMs - (now() - r.workShownAt));
    r.reducedSuccessStarted = true;
    later(r, wait, function () {
      setState(STATES.SUCCESS);
      announceReady(r);
      const fctx = u.finalCanvas ? u.finalCanvas.getContext('2d') : null;
      drawFrame(u.finalCanvas, fctx, r.assets.success, meta.success, meta, meta.success.successEnd);
      if (u.finalCanvas) u.finalCanvas.classList.add('is-visible');
      later(r, REDUCED_FADE_MS, function () { finish(r, meta.success.finalHoldMs); });
    });
  }

  /* ---------- public API (called by ai-service.js analyzeGoal) ---------- */
  function begin() {
    cleanup();
    const u = getUI();
    if (!u) return 0;
    runCounter += 1;
    const r = {
      id: runCounter,
      startedAt: now(),
      reduced: reducedMotion(),
      segment: 'none',
      index: 0,
      nextAt: 0,
      frameId: 0,
      timers: new Set(),
      successRequested: false,
      exitWaitSince: 0,
      workShownAt: 0,
      announcedReady: false,
      assets: null,
      resolveSuccess: null,
      successPromise: null
    };
    run = r;
    setState(STATES.STARTING);
    const c = copy();
    setText(u.title, c.title);
    setText(u.text, c.copy);
    /* Written after the screen is shown, so the live region announces the start once. */
    setText(u.status, '');
    later(r, 120, function () { setText(u.status, copy().working); });
    u.screen.setAttribute('aria-busy', 'true');
    later(r, SLOW_NOTICE_MS, function () {
      if (state === STATES.STARTING || state === STATES.WORKING) setText(u.status, copy().slow);
    });
    watchRoute();
    loadAssets().then(function (a) {
      if (run !== r) return;
      r.assets = a;
      startVisuals(r);
    }, function () {
      if (run !== r) return;
      r.assetsFailed = true;
      if (state === STATES.STARTING) setState(STATES.WORKING);
      if (r.successRequested) completeWithoutArt(r);
    });
    return r.id;
  }

  /* Without art (assets unavailable) success is still shown truthfully in text, briefly. */
  function completeWithoutArt(r) {
    const wait = Math.max(0, 500 - (now() - r.startedAt));
    later(r, wait, function () {
      setState(STATES.SUCCESS);
      announceReady(r);
      finish(r, 450);
    });
  }

  /* Resolves true once the success segment and its final hold have played, or false when the
     run was replaced, canceled or left (the caller must then not navigate). */
  function succeed(runId) {
    const r = run;
    if (!r || r.id !== runId) return Promise.resolve(false);
    if (r.successPromise) return r.successPromise;
    r.successPromise = new Promise(function (resolve) { r.resolveSuccess = resolve; });
    r.successRequested = true;
    if (r.assets) {
      if (r.reduced) {
        reducedSuccess(r);
      } else if (state === STATES.WORKING) {
        setState(STATES.SUCCESS_PENDING);
      }
    } else {
      setState(STATES.SUCCESS_PENDING);
      if (r.assetsFailed) completeWithoutArt(r);
    }
    return r.successPromise;
  }

  /* kind: 'error' | 'timeout' | 'offline'. The planet is never shown. */
  function fail(runId, kind) {
    const r = run;
    if (!r || (runId && r.id !== runId)) return;
    const u = getUI();
    if (u) setText(u.status, copy().failed);
    cleanup(kind === 'timeout' ? STATES.TIMEOUT : STATES.ERROR);
  }

  function cancel(runId) {
    const r = run;
    if (!r || (runId && r.id !== runId)) return;
    cleanup(STATES.CANCELED);
  }

  /* Route ownership: once the screen has been shown, hiding it (any navigation) ends the run. */
  function watchRoute() {
    const u = getUI();
    if (!u || observer || typeof global.MutationObserver !== 'function') return;
    observer = new global.MutationObserver(function () {
      const r = run;
      if (!r) return;
      const hidden = u.screen.classList.contains('hidden');
      if (!hidden) {
        r.seenVisible = true;
        return;
      }
      if (r.seenVisible) cleanup(state === STATES.COMPLETED ? null : STATES.CANCELED);
    });
    observer.observe(u.screen, { attributes: true, attributeFilter: ['class'] });
  }

  global.MissionOperator = Object.freeze({
    begin: begin,
    succeed: succeed,
    fail: fail,
    cancel: cancel,
    getState: function () { return state; },
    STATES: STATES
  });

  const App = global.FocusModeApp;
  if (App && App.core && typeof App.core.registerModule === 'function' && !App.core.hasModule('features.missionOperator')) {
    App.features = App.features || {};
    App.features.missionOperator = App.core.registerModule('features.missionOperator', global.MissionOperator, {
      virtualPath: 'src/features/missions/mission-operator.js',
      version: '17.14',
      status: 'visual-owner'
    });
  }
})(typeof window !== 'undefined' ? window : globalThis);
