/* v17.13 Cosmic Operator Pull, v17.15 Cosmic Operator Pull Refresh.
   Single owner of the hidden pull interaction above Home: pulling Home down at its top edge
   reveals a small cosmic operator scene; releasing past the threshold refreshes Home through
   the existing Home renderers (no reload, no network, no data writes).

   v17.15: the operator is the approved floating-coffee animation, cut frame by frame into one
   sprite sheet (assets/operator/operator-coffee-pull-sheet.png, metadata in
   operator-coffee-pull.json). Pull distance selects frames of the reveal and reach ranges and
   holds the last frame before the cup is touched; only a release past the threshold plays the
   cup push and settle range, once, on the source timeline. The terminal operator of Mission
   generation (mission-operator.js, mission-operator-*) has its own assets and state; nothing
   is shared.

   Footprint on Home: one hidden scene and one visually hidden live region appended to
   #app-screen-home at start-up, the state class `fm-op-active` and a transform on
   .home-scroll-content while a pull is active. Removing this file, styles/home-pull.css and
   the operator-coffee-pull assets removes the feature.

   Gesture ownership: starts only on Home at scroll top, while idle, with no overlay, no
   navigation transition and no running Focus Session. Form controls, horizontally scrollable
   elements (the Mission carousel) and the Mission calendar keep their own gestures. Lock slop
   and dominance match the navigation gesture owner (motion-controller.js), so a vertical pull and
   a horizontal root swipe can never both lock on one touch.

   Idle cost: no animation frames, timers, observers or window listeners; the scene is
   display:none and the sprite sheet is only loaded and decoded once, on idle. */
(function (global) {
  'use strict';
  const doc = global.document;
  if (!doc || global.HomePullOperator) return;

  /* The operator frames come from the approved source video and are never redrawn; this owner
     only chooses which frame is shown and where the 120 px frame sits. Values are CSS px. */
  const SCENE = Object.freeze({
    meta: './assets/operator/operator-coffee-pull.json',
    base: './assets/operator/',
    size: 120,         // displayed frame; 180 px sheet cells, 2 device px per cell px at DPR 3
    clearLeft: 33,     // transparent frame margin at the height of the status line
    pullTop: -16,      // frame top at the threshold (the tether runs off the top of Home)
    pullFollow: 0.12,  // frame drift per px of pull below the threshold
    overFollow: 0.3,   // and beyond it
    holdTop: 2         // frame top while the cup floats away (cup stays inside the pocket)
  });

  const PULL = Object.freeze({
    lockPx: 10,
    dominance: 1.2,
    knee: 60,
    linear: 0.8,
    max: 140,
    threshold: 104,
    disarm: 98,
    hold: 122,
    sceneHeight: 176,
    clickGuardMs: 350
  });

  /* A pull never starts on a control or on an element that owns its own gestures: buttons and
     links, form and slider controls, anything marked data-block-swipe-back (Home calendar,
     Mission carousel) and the Mission block. The header and the gaps between cards stay free. */
  const GESTURE_OWNERS =
    'input, textarea, select, button, a[href], label, [role="button"], [role="slider"], ' +
    '[contenteditable="true"], [data-block-swipe-back="true"], .multi-mission, #homeMissionCalendar';

  const COPY = Object.freeze({
    de: Object.freeze({ pull: 'Zum Aktualisieren ziehen', armed: 'Loslassen', refreshing: 'Wird aktualisiert', done: 'Aktualisiert' }),
    en: Object.freeze({ pull: 'Pull to refresh', armed: 'Release to refresh', refreshing: 'Refreshing', done: 'Updated' })
  });

  const STATES = Object.freeze({
    IDLE: 'idle',
    TRACKING: 'tracking',
    PULLING: 'pulling',
    ARMED: 'armed',
    REFRESHING: 'refreshing',
    SETTLING: 'settling',
    CANCELED: 'canceled'
  });

  let ui = null;
  let state = STATES.IDLE;
  let g = null; // the current interaction, from touch-down until it has settled
  let frameId = null;
  let routeObserver = null;
  let animId = null; // release playback or cancel return; never runs while idle
  let clickGuardUntil = 0;
  let spriteRequested = false;
  let sprite = null; // { meta, img, cells } once the sheet is decoded
  let shown = -1;    // sheet cell currently drawn
  const timers = new Set();

  /* ---------- helpers ---------- */
  function now() {
    return global.performance && typeof global.performance.now === 'function' ? global.performance.now() : Date.now();
  }

  function later(ms, fn) {
    const id = global.setTimeout(function () {
      timers.delete(id);
      fn();
    }, Math.max(0, ms));
    timers.add(id);
  }

  function clearTimers() {
    timers.forEach(function (id) { global.clearTimeout(id); });
    timers.clear();
  }

  function easeOut(t) {
    return 1 - Math.pow(1 - t, 4);
  }

  function smooth(edge0, edge1, x) {
    const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
    return t * t * (3 - 2 * t);
  }

  function isEn() {
    return (doc.documentElement.lang || 'de') === 'en';
  }

  function copy(key) {
    return (isEn() ? COPY.en : COPY.de)[key];
  }

  function reducedMotion() {
    const mc = global.MotionController;
    if (mc && typeof mc.prefersReducedMotion === 'function') return mc.prefersReducedMotion();
    try {
      return Boolean(global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch (_) {
      return false;
    }
  }

  function msVar(styles, name, fallback) {
    const v = parseFloat(styles.getPropertyValue(name));
    return Number.isFinite(v) ? v : fallback;
  }

  function hasSelection() {
    try {
      const sel = global.getSelection && global.getSelection();
      return Boolean(sel && !sel.isCollapsed && String(sel).trim());
    } catch (_) {
      return false;
    }
  }

  function clearSelection() {
    try {
      const sel = global.getSelection && global.getSelection();
      if (sel && typeof sel.removeAllRanges === 'function') sel.removeAllRanges();
    } catch (_) {}
  }

  function setState(next) {
    state = next;
    if (ui) ui.scene.dataset.state = next;
  }

  function isLive(input) {
    return Boolean(g && g.input === input && (state === STATES.TRACKING || state === STATES.PULLING || state === STATES.ARMED));
  }

  /* Restrained rubber band: close to direct movement up to the knee, then an exponential approach
     to the maximum reveal. Continuous in value and slope. */
  function resist(raw) {
    if (raw <= 0) return 0;
    const kneeRaw = PULL.knee / PULL.linear;
    if (raw <= kneeRaw) return raw * PULL.linear;
    const span = PULL.max - PULL.knee;
    return PULL.knee + span * (1 - Math.exp(-((raw - kneeRaw) * PULL.linear) / span));
  }

  /* ---------- DOM ---------- */
  function build() {
    const home = doc.getElementById('app-screen-home');
    const content = home && home.querySelector('.home-scroll-content');
    if (!home || !content) return false;

    const scene = doc.createElement('div');
    scene.id = 'homePullOperator';
    scene.className = 'fm-op';
    scene.hidden = true;
    scene.setAttribute('aria-hidden', 'true');
    scene.dataset.state = STATES.IDLE;
    scene.innerHTML =
      '<div class="fm-op-space">' +
        '<i class="fm-op-stars fm-op-stars-far"></i>' +
        '<i class="fm-op-stars fm-op-stars-near"></i>' +
        '<div class="fm-op-actor">' +
          '<i class="fm-op-glow"></i>' +
          '<canvas class="fm-op-sprite" width="' + SCENE.size + '" height="' + SCENE.size + '"></canvas>' +
        '</div>' +
      '</div>' +
      '<div class="fm-op-edge"></div>' +
      '<div class="fm-op-status"><i class="fm-op-dot"></i><span class="fm-op-label"></span></div>';

    const live = doc.createElement('span');
    live.className = 'fm-op-live';
    live.setAttribute('role', 'status');
    live.setAttribute('aria-live', 'polite');
    live.setAttribute('aria-atomic', 'true');

    home.append(scene, live);
    const canvas = scene.querySelector('.fm-op-sprite');
    ui = {
      home: home,
      content: content,
      scene: scene,
      space: scene.querySelector('.fm-op-space'),
      actor: scene.querySelector('.fm-op-actor'),
      canvas: canvas,
      ctx: canvas.getContext ? canvas.getContext('2d') : null,
      status: scene.querySelector('.fm-op-status'),
      label: scene.querySelector('.fm-op-label'),
      live: live
    };
    return true;
  }

  /* ---------- operator frames (one sheet, one canvas) ---------- */
  function validMeta(meta) {
    return Boolean(
      meta && typeof meta.image === 'string' && Array.isArray(meta.frames) && meta.frames.length > 0 &&
      meta.frameWidth > 0 && meta.frameHeight > 0 && meta.columns > 0 && meta.fps > 0 &&
      meta.frames.length <= meta.columns * meta.rows &&
      meta.pull && Array.isArray(meta.pull.map) && meta.pull.map.length >= 2 &&
      meta.release && meta.release.to > meta.release.from &&
      meta.release.to === meta.frames[meta.frames.length - 1]
    );
  }

  /* Source frame number -> sheet cell: the last stored frame at or before it (the settle range
     keeps every second source frame). */
  function cellTable(frames) {
    const table = [];
    let cell = 0;
    for (let n = 0; n <= frames[frames.length - 1]; n += 1) {
      while (cell + 1 < frames.length && frames[cell + 1] <= n) cell += 1;
      table.push(cell);
    }
    return table;
  }

  function loadSheet(meta) {
    return new Promise(function (resolve, reject) {
      const img = new global.Image();
      img.decoding = 'async';
      img.onload = function () {
        const done = function () { resolve({ meta: meta, img: img, cells: cellTable(meta.frames) }); };
        if (typeof img.decode === 'function') img.decode().then(done, done);
        else done();
      };
      img.onerror = function () { reject(new Error('sheet')); };
      img.src = SCENE.base + meta.image;
    });
  }

  /* Loaded once; a failure is retried on the next pull. The pull and the refresh work without it. */
  function requestSprite() {
    if (spriteRequested || sprite || !ui || !ui.ctx || typeof global.fetch !== 'function') return;
    spriteRequested = true;
    global.fetch(SCENE.meta)
      .then(function (res) {
        if (!res.ok) throw new Error('meta');
        return res.json();
      })
      .then(function (meta) {
        if (!validMeta(meta)) throw new Error('meta');
        return loadSheet(meta);
      })
      .then(function (loaded) {
        sprite = loaded;
        // A pull that is already open shows its frame as soon as the sheet is ready.
        if (g && g.locked && (state === STATES.PULLING || state === STATES.ARMED)) render(g.d);
      })
      .catch(function () {
        spriteRequested = false;
      });
  }

  function sizeCanvas(dpr) {
    const px = Math.round(SCENE.size * dpr);
    if (ui.canvas.width !== px || ui.canvas.height !== px) {
      ui.canvas.width = px;
      ui.canvas.height = px;
      shown = -1;
    }
    ui.ctx.imageSmoothingEnabled = false;
  }

  function clearFrame() {
    if (ui && ui.ctx && shown !== -1) ui.ctx.clearRect(0, 0, ui.canvas.width, ui.canvas.height);
    shown = -1;
  }

  /* Draws source frame `n` (clamped to the clip); redraws only when the sheet cell changes. */
  function showFrame(n) {
    if (!sprite) return;
    const m = sprite.meta;
    const cells = sprite.cells;
    const cell = cells[Math.max(0, Math.min(cells.length - 1, Math.floor(n + 1e-6)))];
    if (cell === shown) return;
    shown = cell;
    const w = ui.canvas.width;
    ui.ctx.clearRect(0, 0, w, w);
    ui.ctx.drawImage(
      sprite.img,
      (cell % m.columns) * m.frameWidth,
      Math.floor(cell / m.columns) * m.frameHeight,
      m.frameWidth,
      m.frameHeight,
      0,
      0,
      w,
      w
    );
  }

  /* Pull progress (0 at lock, 1 at the threshold) to a source frame of the reveal and reach
     ranges. Piecewise linear and stateless, so pulling back walks the same frames in reverse;
     past the threshold it stays on the last frame before the glove touches the cup. */
  function pullFrame(p) {
    const map = sprite.meta.pull.map;
    if (p <= map[0][0]) return map[0][1];
    for (let i = 1; i < map.length; i += 1) {
      if (p <= map[i][0]) {
        const a = map[i - 1];
        const b = map[i];
        return a[1] + (b[1] - a[1]) * ((p - a[0]) / (b[0] - a[0]));
      }
    }
    return map[map.length - 1][1];
  }

  function frameForPull(d) {
    const m = sprite.meta;
    if (g.m.reduced) return m.reducedMotionFrame;
    if (state === STATES.ARMED) return m.pull.armedFrame;
    return Math.min(m.pull.armedFrame, pullFrame(d / PULL.threshold));
  }

  function setStatus(key) {
    const text = copy(key);
    if (ui.label.textContent !== text) ui.label.textContent = text;
  }

  function announce(text) {
    ui.live.textContent = '';
    later(40, function () { ui.live.textContent = text; });
  }

  /* ---------- gesture ownership ---------- */
  function activeScreen() {
    if (typeof global.getActiveScreenV17 === 'function') {
      try {
        return global.getActiveScreenV17();
      } catch (_) {}
    }
    const visible = doc.querySelector('#appBody > .app-screen:not(.hidden)');
    return visible && visible.id ? visible.id.replace('app-screen-', '') : '';
  }

  function homeAvailable() {
    const home = ui.home;
    if (home.classList.contains('hidden') || home.classList.contains('fm-motion-active')) return false;
    const appBody = doc.getElementById('appBody');
    if (appBody && appBody.classList.contains('fm-dual-transition')) return false;
    return activeScreen() === 'home';
  }

  function ownedByOtherGesture(target) {
    if (!target || typeof target.closest !== 'function') return true;
    if (target.closest(GESTURE_OWNERS)) return true;
    for (let el = target; el && el !== ui.home; el = el.parentElement) {
      if (el.scrollWidth > el.clientWidth + 2) {
        const overflowX = global.getComputedStyle(el).overflowX;
        if (overflowX === 'auto' || overflowX === 'scroll') return true;
      }
    }
    return false;
  }

  function canBegin(target) {
    if (!ui || state !== STATES.IDLE || g) return false;
    if (!homeAvailable() || ui.home.scrollTop > 0) return false;
    const mc = global.MotionController;
    if (!mc) return false;
    if (typeof mc.isLocked === 'function' && mc.isLocked()) return false;
    if (typeof mc.isGestureBlockingOverlayOpen === 'function' && mc.isGestureBlockingOverlayOpen()) return false;
    if (global.activeSession && global.activeSession.status === 'running') return false;
    if (hasSelection() || ownedByOtherGesture(target)) return false;
    return true;
  }

  /* ---------- interaction ---------- */
  function begin(x, y, input, pointerId) {
    requestSprite();
    g = {
      input: input, pointerId: pointerId, startX: x, startY: y, raw: 0, d: 0,
      locked: false, captured: false, refreshed: false, played: false, ok: false, m: null
    };
    setState(STATES.TRACKING);
    if (input === 'pointer') setPointerListeners(true);
  }

  function dropTracking() {
    setPointerListeners(false);
    g = null;
    setState(STATES.IDLE);
  }

  function track(x, y, ev) {
    const dx = x - g.startX;
    const dy = y - g.startY;
    if (!g.locked) {
      const adx = Math.abs(dx);
      const ady = Math.abs(dy);
      /* At the top edge a downward move has no native scroll to preserve. Consuming it before the
         lock keeps the browser from starting an overscroll, which would make later moves
         uncancelable. */
      if (g.input === 'touch' && dy > 0 && ady >= adx && ev.cancelable) ev.preventDefault();
      if (Math.max(adx, ady) < PULL.lockPx) return;
      if (
        dy <= 0 ||
        ady < adx * PULL.dominance ||
        ui.home.scrollTop > 0 ||
        (g.input === 'touch' && !ev.cancelable) ||
        !homeAvailable()
      ) {
        dropTracking();
        return;
      }
      lock();
    }
    if (ev.cancelable) ev.preventDefault();
    g.raw = Math.max(0, dy - PULL.lockPx);
    if (frameId === null) frameId = global.requestAnimationFrame(frame);
  }

  /* Geometry and timing are read once per pull. */
  function measure() {
    const styles = global.getComputedStyle(ui.scene);
    const width = ui.home.clientWidth;
    let labelWidth = 0;
    ['pull', 'armed', 'refreshing', 'done'].forEach(function (key) {
      setStatus(key);
      labelWidth = Math.max(labelWidth, ui.status.offsetWidth);
    });
    const minLeft = ui.status.offsetLeft + labelWidth + 8 - SCENE.clearLeft;
    const maxLeft = width - SCENE.size - 12;
    const preferred = width * 0.58 - SCENE.size / 2;
    const reduced = reducedMotion();
    ui.scene.toggleAttribute('data-reduced', reduced);
    return {
      dpr: Math.max(1, Math.min(4, global.devicePixelRatio || 1)),
      edge: ui.content.offsetTop,
      left: Math.max(0, Math.min(maxLeft, Math.max(preferred, minLeft))),
      reduced: reduced,
      cancel: msVar(styles, '--fm-op-cancel', 280),
      toHold: msVar(styles, '--fm-op-to-hold', 220),
      holdMin: msVar(styles, '--fm-op-hold-min', 400),
      confirm: msVar(styles, '--fm-op-confirm', 340),
      finalHold: msVar(styles, '--fm-op-final-hold', 380),
      settle: msVar(styles, '--fm-op-settle', 340)
    };
  }

  function lock() {
    g.locked = true;
    setState(STATES.PULLING);
    if (g.input === 'pointer') {
      try {
        ui.home.setPointerCapture(g.pointerId);
        g.captured = true;
      } catch (_) {}
      clearSelection();
    }
    ui.home.classList.add('fm-op-active');
    ui.scene.hidden = false;
    g.m = measure();
    if (ui.ctx) sizeCanvas(g.m.dpr);
    setStatus('pull');
    ui.content.style.setProperty('will-change', 'transform');
    watchLifecycle(true);
    render(0);
  }

  function updateArmed(d) {
    const armed = state === STATES.ARMED ? d >= PULL.disarm : d >= PULL.threshold;
    if (armed === (state === STATES.ARMED)) return;
    setState(armed ? STATES.ARMED : STATES.PULLING);
    setStatus(armed ? 'armed' : 'pull');
  }

  function frame() {
    frameId = null;
    if (!g || !g.locked || (state !== STATES.PULLING && state !== STATES.ARMED)) return;
    if (!homeAvailable()) {
      finish();
      return;
    }
    const d = resist(g.raw);
    updateArmed(d);
    render(d);
  }

  function snap(v) {
    return Math.round(v * g.m.dpr) / g.m.dpr;
  }

  /* The page (content and the scene window) follows the reveal; the space stays anchored to the
     top of the screen. */
  function placePage(d) {
    const D = snap(d);
    const offset = D + g.m.edge - PULL.sceneHeight;
    ui.content.style.transform = 'translate3d(0,' + D + 'px,0)';
    ui.scene.style.transform = 'translate3d(0,' + offset + 'px,0)';
    ui.space.style.transform = 'translate3d(0,' + -offset + 'px,0)';
    return D;
  }

  /* Frame top for a pull: the frame hangs in the pocket and drifts down a little with the pull,
     so the operator is uncovered from the tether down; with reduced motion it stays still. */
  function pullTop(d) {
    if (g.m.reduced) return SCENE.pullTop;
    const over = d - PULL.threshold;
    return SCENE.pullTop + over * (over < 0 ? SCENE.pullFollow : SCENE.overFollow);
  }

  /* The frame is only translated as a whole, on whole device pixels; never scaled or rotated. */
  function placeOperator(top) {
    ui.actor.style.transform = 'translate3d(' + snap(g.m.left) + 'px,' + snap(top) + 'px,0)';
  }

  function render(d) {
    g.d = placePage(d);
    placeOperator(pullTop(g.d));
    const p = Math.min(1, g.d / PULL.threshold);
    ui.scene.style.setProperty('--fm-op-p', p.toFixed(3));
    ui.scene.style.setProperty('--fm-op-a', smooth(0.82, 1, p).toFixed(3));
    if (sprite) showFrame(frameForPull(g.d));
  }

  function setTransition(ms, withActor) {
    const value = ms > 0 ? 'transform ' + ms + 'ms var(--ease-standard, cubic-bezier(0.22, 1, 0.36, 1))' : 'none';
    ui.content.style.transition = value;
    ui.scene.style.transition = value;
    ui.space.style.transition = value;
    ui.actor.style.transition = withActor ? value : 'none';
  }

  function stopAnim() {
    if (animId !== null) {
      global.cancelAnimationFrame(animId);
      animId = null;
    }
  }

  function release(canceled) {
    setPointerListeners(false);
    if (!g) return;
    if (!g.locked) {
      dropTracking();
      return;
    }
    if (g.captured) {
      try {
        ui.home.releasePointerCapture(g.pointerId);
      } catch (_) {}
      g.captured = false;
    }
    if (frameId !== null) {
      global.cancelAnimationFrame(frameId);
      frameId = null;
    }
    clickGuardUntil = now() + PULL.clickGuardMs;
    if (!homeAvailable()) {
      finish();
      return;
    }
    // Apply the last move so the release decision matches what the user saw.
    const d = resist(g.raw);
    updateArmed(d);
    render(d);
    if (!canceled && state === STATES.ARMED) refresh();
    else cancelPull();
  }

  /* Below the threshold: no refresh and no cup push. One short return in which the page, the
     frame position and the reach frames all follow the same eased distance back to rest, so
     the operator lowers its arm while the page covers the scene again. */
  function cancelPull() {
    setState(STATES.CANCELED);
    setTransition(0, false);
    const from = g.d;
    const duration = g.m.cancel;
    const started = now();
    const step = function () {
      animId = null;
      if (state !== STATES.CANCELED || !g) return;
      const t = duration > 0 ? Math.min(1, (now() - started) / duration) : 1;
      render(from * (1 - easeOut(t)));
      if (t >= 1) finish();
      else animId = global.requestAnimationFrame(step);
    };
    animId = global.requestAnimationFrame(step);
  }

  /* Past the threshold: the page holds the pocket open, the remaining frames (cup push, then the
     settle) play once on the source timeline, starting with the frame after the armed one, and
     Home is refreshed exactly once while they play. */
  function refresh() {
    const m = g.m;
    const started = now();
    const animate = Boolean(sprite) && !m.reduced;
    setState(STATES.REFRESHING);
    setStatus('refreshing');
    ui.live.textContent = '';
    setTransition(m.toHold, true);
    placePage(animate ? PULL.hold : PULL.threshold);
    placeOperator(animate ? SCENE.holdTop : pullTop(PULL.threshold));
    ui.scene.style.setProperty('--fm-op-p', '1');
    ui.scene.style.setProperty('--fm-op-a', '1');
    if (animate) playRelease();
    else g.played = true;
    // Run the Home renderers once the hold movement is under way on the compositor.
    later(34, function () {
      if (state !== STATES.REFRESHING || !g || g.refreshed) return;
      g.refreshed = true;
      g.ok = refreshHome();
      later(animate ? 0 : m.holdMin - (now() - started), confirmRefresh);
    });
  }

  function playRelease() {
    const rel = sprite.meta.release;
    const fps = sprite.meta.fps;
    const started = now();
    const step = function () {
      animId = null;
      if (state !== STATES.REFRESHING || !g) return;
      const n = Math.min(rel.to, rel.from + ((now() - started) / 1000) * fps);
      showFrame(n);
      if (n >= rel.to) {
        g.played = true;
        confirmRefresh();
        return;
      }
      animId = global.requestAnimationFrame(step);
    };
    showFrame(rel.from);
    animId = global.requestAnimationFrame(step);
  }

  /* Runs once the refresh has executed and the release frames have reached the final pose. */
  function confirmRefresh() {
    if (state !== STATES.REFRESHING || !g || !g.refreshed || !g.played) return;
    setState(STATES.SETTLING);
    let wait = 0;
    if (g.ok) {
      ui.scene.dataset.done = 'true';
      setStatus('done');
      announce(copy('done'));
      wait = g.m.confirm;
    }
    if (sprite && !g.m.reduced) wait = Math.max(wait, g.m.finalHold);
    later(wait, function () {
      if (state !== STATES.SETTLING || !g) return;
      setTransition(g.m.settle, false);
      placePage(0);
      later(g.m.settle + 40, finish);
    });
  }

  /* Returns Home to its exact resting state and releases everything this owner holds. Also the
     immediate path for route changes, backgrounding, resize and errors. */
  function finish() {
    clearTimers();
    if (frameId !== null) {
      global.cancelAnimationFrame(frameId);
      frameId = null;
    }
    stopAnim();
    setPointerListeners(false);
    watchLifecycle(false);
    if (g && g.captured) {
      try {
        ui.home.releasePointerCapture(g.pointerId);
      } catch (_) {}
    }
    if (ui) {
      ['transform', 'transition', 'will-change'].forEach(function (prop) {
        ui.content.style.removeProperty(prop);
      });
      [ui.scene, ui.space, ui.actor].forEach(function (el) {
        el.style.removeProperty('transform');
        el.style.removeProperty('transition');
      });
      ui.scene.style.removeProperty('--fm-op-p');
      ui.scene.style.removeProperty('--fm-op-a');
      [ui.content, ui.scene, ui.space, ui.actor].forEach(function (el) {
        if (el.getAttribute('style') === '') el.removeAttribute('style');
      });
      delete ui.scene.dataset.done;
      ui.scene.hidden = true;
      ui.home.classList.remove('fm-op-active');
      clearFrame();
    }
    g = null;
    setState(STATES.IDLE);
  }

  /* ---------- Home refresh (existing owners only) ---------- */
  /* The same renderers that run when Home is entered, plus the League preview, the Quick Focus
     label and the connection state. They read current local state; nothing is written. */
  function refreshHome() {
    const carousel = doc.getElementById('missionCarouselV1181');
    const carouselLeft = carousel ? carousel.scrollLeft : 0;
    const steps = [
      ['missions', function () { if (typeof global.renderHomeCarouselV116 === 'function') global.renderHomeCarouselV116(); }],
      ['today', function () { if (typeof global.refreshHomeStats === 'function') global.refreshHomeStats(); }],
      ['quickFocus', function () { if (typeof global.updateQuickRing === 'function') global.updateQuickRing(); }],
      ['league', function () {
        const league = global.FocusLeague;
        if (league && typeof league.renderHomePreview === 'function') league.renderHomePreview();
      }],
      ['connection', function () { if (typeof global.syncOfflineStatusBanner === 'function') global.syncOfflineStatusBanner(); }]
    ];
    let ok = true;
    steps.forEach(function (step) {
      try {
        step[1]();
      } catch (error) {
        ok = false;
        console.warn('[HomePullOperator]', { event: 'refresh_step_failed', step: step[0], message: error && error.message });
      }
    });
    // Keep the Mission the user was looking at in a multi-Mission carousel.
    const nextCarousel = doc.getElementById('missionCarouselV1181');
    if (nextCarousel && carouselLeft > 0) nextCarousel.scrollLeft = carouselLeft;
    return ok;
  }

  /* ---------- listeners ---------- */
  function onTouchStart(ev) {
    if (!ev.touches) return;
    if (ev.touches.length !== 1) {
      if (isLive('touch')) release(true);
      return;
    }
    if (!canBegin(ev.target)) return;
    const touch = ev.touches[0];
    begin(touch.clientX, touch.clientY, 'touch', null);
  }

  function onTouchMove(ev) {
    if (isLive('touch')) {
      if (!ev.touches || ev.touches.length !== 1) {
        release(true);
        return;
      }
      const touch = ev.touches[0];
      track(touch.clientX, touch.clientY, ev);
      return;
    }
    // While a refresh holds or the page settles, Home stays put under a new finger.
    if (g && state !== STATES.IDLE && state !== STATES.TRACKING && ev.cancelable) ev.preventDefault();
  }

  function onTouchEnd(ev) {
    if (!isLive('touch') || (ev.touches && ev.touches.length > 0)) return;
    release(false);
  }

  function onTouchCancel() {
    if (isLive('touch')) release(true);
  }

  function onPointerDown(ev) {
    // Touch input is handled by touch events only, so one physical gesture has one owner.
    if (ev.pointerType === 'touch' || (ev.button !== undefined && ev.button !== 0)) return;
    if (!canBegin(ev.target)) return;
    begin(ev.clientX, ev.clientY, 'pointer', ev.pointerId);
  }

  function onPointerMove(ev) {
    if (isLive('pointer') && ev.pointerId === g.pointerId) track(ev.clientX, ev.clientY, ev);
  }

  function onPointerUp(ev) {
    if (isLive('pointer') && ev.pointerId === g.pointerId) release(false);
  }

  function onPointerCancel(ev) {
    if (isLive('pointer') && ev.pointerId === g.pointerId) release(true);
  }

  function onClickCapture(ev) {
    if (clickGuardUntil && now() < clickGuardUntil) {
      clickGuardUntil = 0;
      ev.preventDefault();
      ev.stopPropagation();
    }
  }

  function onSelectStart(ev) {
    // Only a locked mouse pull suppresses selection; ordinary text selection keeps working.
    if (g && g.input === 'pointer' && g.locked) ev.preventDefault();
  }

  function onVisibility() {
    if (doc.visibilityState === 'hidden') finish();
  }

  let pointerListening = false;
  function setPointerListeners(on) {
    if (on === pointerListening) return;
    pointerListening = on;
    const method = on ? 'addEventListener' : 'removeEventListener';
    global[method]('pointermove', onPointerMove, { passive: false });
    global[method]('pointerup', onPointerUp, { passive: true });
    global[method]('pointercancel', onPointerCancel, { passive: true });
  }

  let lifecycleWatching = false;
  function watchLifecycle(on) {
    if (on === lifecycleWatching) return;
    lifecycleWatching = on;
    if (on) {
      if (!routeObserver && typeof global.MutationObserver === 'function') {
        routeObserver = new global.MutationObserver(function () {
          if (state !== STATES.IDLE && !homeAvailable()) finish();
        });
      }
      if (routeObserver) routeObserver.observe(ui.home, { attributes: true, attributeFilter: ['class'] });
    } else if (routeObserver) {
      routeObserver.disconnect();
    }
    const method = on ? 'addEventListener' : 'removeEventListener';
    global[method]('resize', finish, { passive: true });
    global[method]('orientationchange', finish, { passive: true });
    global[method]('pagehide', finish);
    doc[method]('visibilitychange', onVisibility);
  }

  function registerStrings() {
    if (typeof I18N === 'object' && I18N && I18N.en) {
      Object.keys(COPY.de).forEach(function (key) {
        I18N.en[COPY.de[key]] = COPY.en[key];
      });
    }
  }

  function install() {
    if (ui || !build()) return;
    const home = ui.home;
    home.addEventListener('touchstart', onTouchStart, { passive: true });
    home.addEventListener('touchmove', onTouchMove, { passive: false });
    home.addEventListener('touchend', onTouchEnd, { passive: true });
    home.addEventListener('touchcancel', onTouchCancel, { passive: true });
    home.addEventListener('pointerdown', onPointerDown, { passive: true });
    home.addEventListener('click', onClickCapture, true);
    home.addEventListener('selectstart', onSelectStart);
    registerStrings();
    // Load and decode the frames once, off the critical path, so the first pull is ready.
    if (typeof global.requestIdleCallback === 'function') global.requestIdleCallback(requestSprite, { timeout: 4000 });
    else global.setTimeout(requestSprite, 1500);
  }

  const HomePullOperator = Object.freeze({
    version: 'v17.15 Cosmic Operator Pull Refresh',
    STATES: STATES,
    getState: function () { return state; },
    refreshHome: refreshHome,
    cancel: function () { if (state !== STATES.IDLE) finish(); }
  });
  global.HomePullOperator = HomePullOperator;
  if (global.FocusModeApp && global.FocusModeApp.features) {
    global.FocusModeApp.features.homePull = HomePullOperator;
  }

  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', install, { once: true });
  else install();
})(window);
