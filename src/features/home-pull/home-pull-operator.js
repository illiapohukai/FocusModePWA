/* v17.13 Cosmic Operator Pull (experimental).
   Single owner of the hidden pull interaction above Home: pulling Home down at its top edge
   reveals a small cosmic operator scene; releasing past the threshold refreshes Home through
   the existing Home renderers (no reload, no network, no data writes).

   Footprint on Home: one hidden scene and one visually hidden live region appended to
   #app-screen-home at start-up, the state class `fm-op-active` and a transform on
   .home-scroll-content while a pull is active. Removing this file, styles/home-pull.css and
   assets/operator/ removes the feature.

   Gesture ownership: starts only on Home at scroll top, while idle, with no overlay, no
   navigation transition and no running Focus Session. Form controls, horizontally scrollable
   elements (the Mission carousel) and the Mission calendar keep their own gestures. Lock slop
   and dominance match the navigation gesture owner (motion-controller.js), so a vertical pull and
   a horizontal root swipe can never both lock on one touch.

   Idle cost: no animation frames, timers, observers or window listeners; the scene is
   display:none and the sprite image is only decoded once, on idle. */
(function (global) {
  'use strict';
  const doc = global.document;
  if (!doc || global.HomePullOperator) return;

  /* The approved sprite is one locked image (astronaut, tether, cup and coffee), shown at a fixed
     width and only moved as a whole (translation, slight rotation, opacity). Values are CSS px of
     the displayed image. Replacing the art means updating this block and the sprite-relative
     positions documented in styles/home-pull.css; the gesture logic does not change. */
  const SPRITE = Object.freeze({
    src: './assets/operator/operator-floating-coffee.png',
    width: 90,
    astronautTop: 49,
    astronautLeft: 7,
    groupRight: 87
  });

  const PULL = Object.freeze({
    lockPx: 10,
    dominance: 1.2,
    knee: 60,
    linear: 0.8,
    max: 136,
    threshold: 96,
    disarm: 90,
    hold: 94,
    sceneHeight: 176,
    clickGuardMs: 350
  });

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
  let clickGuardUntil = 0;
  let spriteRequested = false;
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
          '<i class="fm-op-pulse"></i>' +
          '<div class="fm-op-float"><img class="fm-op-sprite" alt="" draggable="false" decoding="async"></div>' +
          '<div class="fm-op-drops"><i></i><i></i><i></i><i></i></div>' +
          '<div class="fm-op-sparks"><i></i><i></i><i></i></div>' +
          '<i class="fm-op-check"></i>' +
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
    ui = {
      home: home,
      content: content,
      scene: scene,
      space: scene.querySelector('.fm-op-space'),
      actor: scene.querySelector('.fm-op-actor'),
      sprite: scene.querySelector('.fm-op-sprite'),
      status: scene.querySelector('.fm-op-status'),
      label: scene.querySelector('.fm-op-label'),
      live: live
    };
    return true;
  }

  function requestSprite() {
    if (spriteRequested || !ui) return;
    spriteRequested = true;
    ui.sprite.src = SPRITE.src;
    if (typeof ui.sprite.decode === 'function') ui.sprite.decode().catch(function () {});
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
    if (target.closest('input, textarea, select, [contenteditable="true"], .multi-mission, #homeMissionCalendar')) return true;
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
    g = { input: input, pointerId: pointerId, startX: x, startY: y, raw: 0, d: 0, locked: false, captured: false, ok: false, m: null };
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
    const minLeft = ui.status.offsetLeft + labelWidth + 10 - SPRITE.astronautLeft;
    const maxLeft = width - SPRITE.groupRight - 12;
    const preferred = width * 0.58 - SPRITE.width / 2;
    return {
      dpr: Math.max(1, Math.min(4, global.devicePixelRatio || 1)),
      edge: ui.content.offsetTop,
      left: Math.max(0, Math.min(maxLeft, Math.max(preferred, minLeft))),
      reduced: reducedMotion(),
      cancel: msVar(styles, '--fm-op-cancel', 280),
      toHold: msVar(styles, '--fm-op-to-hold', 220),
      holdMin: msVar(styles, '--fm-op-hold-min', 400),
      confirm: msVar(styles, '--fm-op-confirm', 340),
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

  /* The operator drifts down slightly with the pull and tilts its helmet toward the cup near the
     threshold; with reduced motion it stays still. */
  function placeOperator(d, upright) {
    const m = g.m;
    const p = Math.min(1, d / PULL.threshold);
    let top = 12;
    let x = m.left;
    let rot = 0;
    if (!m.reduced) {
      top = 8 + 0.08 * d + 0.3 * Math.max(0, d - PULL.threshold);
      x = m.left + 3 * p;
      rot = upright ? 0 : -2 + 6 * smooth(0.2, 1, p);
    }
    ui.actor.style.transform =
      'translate3d(' + snap(x) + 'px,' + snap(top - SPRITE.astronautTop) + 'px,0) rotate(' + rot.toFixed(2) + 'deg)';
  }

  function render(d) {
    g.d = placePage(d);
    placeOperator(g.d, false);
    const p = Math.min(1, g.d / PULL.threshold);
    ui.scene.style.setProperty('--fm-op-p', p.toFixed(3));
    ui.scene.style.setProperty('--fm-op-a', smooth(0.82, 1, p).toFixed(3));
  }

  function setTransition(ms, withActor) {
    const value = ms > 0 ? 'transform ' + ms + 'ms var(--ease-standard, cubic-bezier(0.22, 1, 0.36, 1))' : 'none';
    ui.content.style.transition = value;
    ui.scene.style.transition = value;
    ui.space.style.transition = value;
    ui.actor.style.transition = withActor ? value : 'none';
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

  /* Below the threshold: no refresh; the page covers the scene again. */
  function cancelPull() {
    setState(STATES.CANCELED);
    setTransition(g.m.cancel, false);
    placePage(0);
    later(g.m.cancel + 40, finish);
  }

  function refresh() {
    const m = g.m;
    const started = now();
    setState(STATES.REFRESHING);
    setStatus('refreshing');
    ui.live.textContent = '';
    setTransition(m.toHold, true);
    placePage(PULL.hold);
    placeOperator(PULL.hold, true);
    ui.scene.style.setProperty('--fm-op-p', '1');
    ui.scene.style.setProperty('--fm-op-a', '1');
    // Run the Home renderers once the hold movement is under way on the compositor.
    later(34, function () {
      if (state !== STATES.REFRESHING || !g) return;
      g.ok = refreshHome();
      later(m.holdMin - (now() - started), confirmRefresh);
    });
  }

  function confirmRefresh() {
    if (state !== STATES.REFRESHING || !g) return;
    setState(STATES.SETTLING);
    let wait = 0;
    if (g.ok) {
      ui.scene.dataset.done = 'true';
      setStatus('done');
      announce(copy('done'));
      wait = g.m.confirm;
    }
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
    // Decode the sprite once, off the critical path, so the first pull is ready.
    if (typeof global.requestIdleCallback === 'function') global.requestIdleCallback(requestSprite, { timeout: 4000 });
    else global.setTimeout(requestSprite, 1500);
  }

  const HomePullOperator = Object.freeze({
    version: 'v17.13 Cosmic Operator Pull',
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
