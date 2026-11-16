(function (root) {
  'use strict';

  const ROOT_TAB_ORDER = Object.freeze(['home', 'missions', 'history', 'settings']);
  const VALID_STATES = Object.freeze(['idle', 'preparing', 'animating', 'cleaning']);

  function getRootTabIndex(section) {
    if (!section || typeof section !== 'string') return -1;
    return ROOT_TAB_ORDER.indexOf(section.trim().toLowerCase());
  }

  function computeRootTabMetrics(fromSection, toSection) {
    const fromIdx = getRootTabIndex(fromSection);
    const toIdx = getRootTabIndex(toSection);
    if (fromIdx === -1 || toIdx === -1 || fromIdx === toIdx) {
      return {
        fromIndex: fromIdx,
        toIndex: toIdx,
        direction: 0,
        distance: 0,
        travelPx: 0,
        travelPct: 0,
        durationMs: 0,
        intent: 'same-tab'
      };
    }
    const delta = toIdx - fromIdx;
    const direction = delta > 0 ? 1 : -1;
    const distance = Math.abs(delta);
    let travelPx = 56;
    let travelPct = 14;
    let durationMs = 360;
    if (distance === 2) {
      travelPx = 64;
      travelPct = 16;
      durationMs = 400;
    } else if (distance >= 3) {
      travelPx = 72;
      travelPct = 18;
      durationMs = 430;
    }
    return {
      fromIndex: fromIdx,
      toIndex: toIdx,
      direction,
      distance,
      travelPx,
      travelPct,
      durationMs,
      intent: direction > 0 ? 'root-tab-right' : 'root-tab-left'
    };
  }

  function resolveTransitionIntent(fromScreen, toScreen, context) {
    const ctx = context && typeof context === 'object' ? context : {};
    if (!fromScreen || !toScreen || fromScreen === toScreen) {
      return 'none';
    }
    if (ctx.intent && typeof ctx.intent === 'string') {
      return ctx.intent;
    }
    if (toScreen === 'timer') {
      return 'focus-enter';
    }
    if (fromScreen === 'timer') {
      if (toScreen === 'home') return 'focus-exit-home';
      if (toScreen === 'result') return 'focus-exit-result';
      if (toScreen === 'distractions') return 'push';
      return 'pop';
    }
    if (ctx.type === 'pop' || ctx.direction === 'back') {
      return 'pop';
    }
    if (ctx.type === 'push' || ctx.direction === 'forward') {
      return 'push';
    }
    if (ctx.type === 'root') {
      const fromRoot = ctx.fromRoot || fromScreen;
      const toRoot = ctx.toRoot || toScreen;
      const metrics = computeRootTabMetrics(fromRoot, toRoot);
      if (metrics.intent !== 'same-tab') return metrics.intent;
      if (fromScreen !== toScreen) return 'pop';
    }
    const fromTabIdx = getRootTabIndex(fromScreen);
    const toTabIdx = getRootTabIndex(toScreen);
    if (fromTabIdx !== -1 && toTabIdx !== -1) {
      return computeRootTabMetrics(fromScreen, toScreen).intent;
    }
    if (fromTabIdx === -1 && toTabIdx !== -1) {
      return 'pop';
    }
    return 'push';
  }

  function createMotionStateMachine() {
    let currentState = 'idle';
    let lockReason = null;
    let safetyTimer = null;

    function clearSafetyTimer() {
      if (safetyTimer !== null) {
        clearTimeout(safetyTimer);
        safetyTimer = null;
      }
    }

    function setState(nextState, reason) {
      if (!VALID_STATES.includes(nextState)) {
        throw new Error('Invalid motion state: ' + nextState);
      }
      currentState = nextState;
      if (nextState === 'idle') {
        lockReason = null;
        clearSafetyTimer();
      } else if (reason) {
        lockReason = reason;
      }
      return currentState;
    }

    function acquireLock(reason, timeoutMs, onTimeout) {
      clearSafetyTimer();
      lockReason = reason || 'transition';
      currentState = 'preparing';
      const maxMs = typeof timeoutMs === 'number' && timeoutMs > 0 ? timeoutMs : 720;
      safetyTimer = setTimeout(function () {
        safetyTimer = null;
        currentState = 'idle';
        lockReason = null;
        if (typeof onTimeout === 'function') {
          try {
            onTimeout();
          } catch (_) {}
        }
      }, maxMs);
      return true;
    }

    function releaseLock() {
      clearSafetyTimer();
      currentState = 'idle';
      lockReason = null;
    }

    return {
      getState: function () {
        return currentState;
      },
      getLockReason: function () {
        return lockReason;
      },
      isLocked: function () {
        return currentState !== 'idle';
      },
      setState,
      acquireLock,
      releaseLock
    };
  }

  const stateMachine = createMotionStateMachine();

  let activeScreenCleanup = null;
  let lastTriggerRect = null;
  let lastMenuTriggerEl = null;
  let plusCloseTimer = null;
  let missionSheetCloseTimer = null;
  let missionSheetAbortController = null;
  let splashDismissed = false;
  let splashStartTime = Date.now();
  let knownActivityTopKey = null;

  function prefersReducedMotion() {
    try {
      return Boolean(
        root.matchMedia && root.matchMedia('(prefers-reduced-motion: reduce)').matches
      );
    } catch (_) {
      return false;
    }
  }

  function cancelElementAnimations(el) {
    if (!el || typeof el.getAnimations !== 'function') return;
    try {
      const anims = el.getAnimations();
      for (let i = 0; i < anims.length; i++) {
        anims[i].cancel();
      }
    } catch (_) {}
  }

  function clearInlineMotionStyles(el) {
    if (!el || !el.style) return;
    el.style.removeProperty('transform');
    el.style.removeProperty('opacity');
    el.style.removeProperty('filter');
    el.style.removeProperty('transition');
    el.style.removeProperty('will-change');
  }

  function removeSharedProxies() {
    if (typeof document === 'undefined') return;
    document.querySelectorAll('.fm-shared-morph-proxy').forEach(function (node) {
      cancelElementAnimations(node);
      node.remove();
    });
  }

  function forceCleanupScreenLayers() {
    if (typeof activeScreenCleanup === 'function') {
      const fn = activeScreenCleanup;
      activeScreenCleanup = null;
      try {
        fn();
      } catch (_) {}
    }
    if (typeof document !== 'undefined') {
      const appBody = document.getElementById('appBody');
      appBody?.classList.remove('fm-dual-transition', 'fm-swipe-back-active', 'fm-root-swipe-active');
      document.querySelectorAll('.app-screen').forEach(function (scr) {
        cancelElementAnimations(scr);
        scr.classList.remove(
          'fm-layer-outgoing',
          'fm-layer-incoming',
          'fm-layer-top',
          'fm-layer-flat',
          'fm-swipe-back-underlay',
          'fm-motion-active'
        );
        clearInlineMotionStyles(scr);
      });
    }
    removeSharedProxies();
    stateMachine.releaseLock();
  }

  function recordTriggerElement(el) {
    if (!el || typeof el.getBoundingClientRect !== 'function') return;
    try {
      const rect = el.getBoundingClientRect();
      if (rect && rect.width > 0 && rect.height > 0) {
        lastTriggerRect = {
          left: rect.left,
          top: rect.top,
          width: rect.width,
          height: rect.height,
          cx: rect.left + rect.width / 2,
          cy: rect.top + rect.height / 2,
          timestamp: Date.now()
        };
      }
    } catch (_) {}
  }

  function installGlobalTriggerTracking() {
    if (typeof document === 'undefined') return;
    document.addEventListener(
      'pointerdown',
      function (e) {
        const target = e.target && typeof e.target.closest === 'function'
          ? e.target.closest(
              'button, [role="button"], .mh-start-btn, .mh-next-box, .mmc-menu'
            )
          : null;
        if (!target) return;
        recordTriggerElement(target);
        if (target.classList.contains('mmc-menu')) {
          lastMenuTriggerEl = target;
        }
      },
      { passive: true, capture: true }
    );
  }

  /* ========================================================================
     STARTUP SPLASH LIFECYCLE (#fmStartupSplash)
     ======================================================================== */
  function initStartupSplash() {
    if (typeof document === 'undefined') return;
    const splash = document.getElementById('fmStartupSplash');
    if (!splash || splashDismissed) return;

    splashStartTime = Date.now();
    const reduced = prefersReducedMotion();
    const iconWrap = splash.querySelector('.fm-splash-icon-wrap');
    const titleEl = splash.querySelector('.fm-splash-title');
    const statusEl = splash.querySelector('.fm-splash-status');

    if (iconWrap && typeof iconWrap.animate === 'function') {
      iconWrap.animate(
        [
          { transform: 'scale(0.94)', opacity: 0 },
          { transform: 'scale(1)', opacity: 1 }
        ],
        {
          duration: reduced ? 90 : 320,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );
    } else if (iconWrap) {
      iconWrap.style.opacity = '1';
      iconWrap.style.transform = 'scale(1)';
    }

    if (titleEl && typeof titleEl.animate === 'function') {
      titleEl.animate(
        [
          { transform: 'translateY(6px)', opacity: 0 },
          { transform: 'translateY(0px)', opacity: 1 }
        ],
        {
          duration: reduced ? 90 : 280,
          delay: reduced ? 0 : 60,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );
    } else if (titleEl) {
      titleEl.style.opacity = '1';
      titleEl.style.transform = 'translateY(0)';
    }

    if (statusEl && typeof statusEl.animate === 'function') {
      statusEl.animate(
        [
          { transform: 'translateY(5px)', opacity: 0 },
          { transform: 'translateY(0px)', opacity: 1 }
        ],
        {
          duration: reduced ? 90 : 260,
          delay: reduced ? 0 : 110,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );
    } else if (statusEl) {
      statusEl.style.opacity = '1';
      statusEl.style.transform = 'translateY(0)';
    }

    const minSplashMs = reduced ? 220 : 520;
    const maxSplashMs = reduced ? 480 : 1150;

    const safetyTimeout = setTimeout(function () {
      dismissStartupSplash();
    }, maxSplashMs);

    setTimeout(function () {
      clearTimeout(safetyTimeout);
      dismissStartupSplash();
    }, minSplashMs);
  }

  function dismissStartupSplash() {
    if (splashDismissed || typeof document === 'undefined') return;
    splashDismissed = true;
    const splash = document.getElementById('fmStartupSplash');
    if (!splash) return;

    const reduced = prefersReducedMotion();
    const activeScreen = document.querySelector('#appBody > .app-screen:not(.hidden)');

    if (activeScreen && typeof activeScreen.animate === 'function') {
      activeScreen.classList.add('fm-motion-active');
      const revealAnim = activeScreen.animate(
        [
          { transform: reduced ? 'scale(1)' : 'scale(0.985)', opacity: 0.72 },
          { transform: 'scale(1)', opacity: 1 }
        ],
        {
          duration: reduced ? 90 : 250,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)'
        }
      );
      revealAnim.finished
        .catch(function () {})
        .finally(function () {
          activeScreen.classList.remove('fm-motion-active');
          clearInlineMotionStyles(activeScreen);
        });
    }

    if (typeof splash.animate === 'function') {
      const exitAnim = splash.animate(
        [
          { transform: 'translateY(0px)', opacity: 1 },
          { transform: reduced ? 'translateY(0px)' : 'translateY(-6px)', opacity: 0 }
        ],
        {
          duration: reduced ? 90 : 220,
          easing: 'cubic-bezier(0.4, 0, 1, 1)',
          fill: 'forwards'
        }
      );
      exitAnim.finished
        .catch(function () {})
        .finally(function () {
          cancelElementAnimations(splash);
          splash.classList.add('fm-splash-hidden');
          splash.setAttribute('aria-hidden', 'true');
        });
      setTimeout(function () {
        splash.classList.add('fm-splash-hidden');
        splash.setAttribute('aria-hidden', 'true');
      }, reduced ? 110 : 240);
    } else {
      splash.classList.add('fm-splash-hidden');
      splash.setAttribute('aria-hidden', 'true');
    }
  }

  /* ========================================================================
     SCREEN-TO-SCREEN TRANSITION ENGINE
     ======================================================================== */
  function animateScreenTransition(fromScreen, toScreen, context) {
    if (typeof document === 'undefined') return;
    if (!fromScreen || !toScreen || fromScreen === toScreen) return;

    const appBody = document.getElementById('appBody');
    const fromEl = document.getElementById('app-screen-' + fromScreen);
    const toEl = document.getElementById('app-screen-' + toScreen);
    if (!appBody || !fromEl || !toEl) return;

    forceCleanupScreenLayers();

    const reduced = prefersReducedMotion();
    const intent = resolveTransitionIntent(fromScreen, toScreen, context);
    if (intent === 'none') return;

    if (intent === 'focus-enter') {
      animateFocusEnter(appBody, fromEl, toEl, reduced);
      return;
    }

    if (intent === 'focus-exit-home') {
      animateFocusExitToHome(appBody, fromEl, toEl, reduced);
      return;
    }

    let durationMs = reduced ? 100 : 390;
    let fromKeyframes = [
      { transform: 'translateX(0px)', opacity: 1 },
      { transform: 'translateX(-16px)', opacity: 0 }
    ];
    let toKeyframes = [
      { transform: 'translateX(16px)', opacity: 0.35 },
      { transform: 'translateX(0px)', opacity: 1 }
    ];
    let easingIn = 'cubic-bezier(0.22, 1, 0.36, 1)';
    let easingOut = 'cubic-bezier(0.4, 0, 0.2, 1)';
    let incomingOnTop = false;
    let outgoingOnTop = false;

    let outgoingDurationMs = null;
    if (intent === 'root-tab-right' || intent === 'root-tab-left') {
      // Directional root transition: only source and destination are ever rendered,
      // distant roots use one continuous move with modestly larger travel.
      const fromRoot = (context && context.fromRoot) || fromScreen;
      const toRoot = (context && context.toRoot) || toScreen;
      const metrics = computeRootTabMetrics(fromRoot, toRoot);
      const dir = metrics.direction || (intent === 'root-tab-right' ? 1 : -1);
      const travel = reduced ? 0 : metrics.travelPct || 14;
      durationMs = reduced ? 120 : metrics.durationMs || 360;
      outgoingDurationMs = reduced ? 120 : Math.round(durationMs * 0.72);
      incomingOnTop = true;
      easingIn = 'cubic-bezier(0.22, 1, 0.36, 1)';
      easingOut = 'cubic-bezier(0.32, 0, 0.24, 1)';
      toKeyframes = [
        { transform: 'translate3d(' + dir * travel + '%, 0, 0)', opacity: 0 },
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 }
      ];
      fromKeyframes = [
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 },
        { transform: 'translate3d(' + -dir * travel * 0.55 + '%, 0, 0)', opacity: 0 }
      ];
    } else if (intent === 'push' || intent === 'focus-exit-result') {
      durationMs = reduced ? 100 : 390;
      incomingOnTop = true;
      easingIn = 'cubic-bezier(0.22, 1, 0.36, 1)';
      easingOut = 'cubic-bezier(0.22, 1, 0.36, 1)';
      toKeyframes = [
        { transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(100%, 0, 0)', opacity: 1 },
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 }
      ];
      fromKeyframes = [
        { transform: 'translate3d(0%, 0, 0) scale(1)', opacity: 1 },
        {
          transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(-8%, 0, 0) scale(0.985)',
          opacity: 0.88
        }
      ];
    } else if (intent === 'pop') {
      durationMs = reduced ? 100 : 370;
      outgoingOnTop = true;
      easingIn = 'cubic-bezier(0.22, 1, 0.36, 1)';
      easingOut = 'cubic-bezier(0.4, 0, 0.2, 1)';
      fromKeyframes = [
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 },
        { transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(100%, 0, 0)', opacity: 1 }
      ];
      toKeyframes = [
        {
          transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(-8%, 0, 0) scale(0.985)',
          opacity: 0.88
        },
        { transform: 'translate3d(0%, 0, 0) scale(1)', opacity: 1 }
      ];
    }

    stateMachine.acquireLock(intent, durationMs + 180, forceCleanupScreenLayers);
    stateMachine.setState('animating', intent);

    appBody.classList.add('fm-dual-transition');
    fromEl.classList.add('fm-layer-outgoing', 'fm-motion-active');
    if (incomingOnTop) {
      toEl.classList.add('fm-layer-top');
    }
    if (intent === 'root-tab-right' || intent === 'root-tab-left') {
      toEl.classList.add('fm-layer-flat');
    }
    if (outgoingOnTop) {
      fromEl.classList.add('fm-layer-top');
    }
    toEl.classList.add('fm-layer-incoming', 'fm-motion-active');

    let cleaned = false;
    function cleanup() {
      if (cleaned) return;
      cleaned = true;
      stateMachine.setState('cleaning', intent);
      cancelElementAnimations(fromEl);
      cancelElementAnimations(toEl);
      appBody.classList.remove('fm-dual-transition');
      fromEl.classList.remove('fm-layer-outgoing', 'fm-layer-top', 'fm-motion-active');
      toEl.classList.remove('fm-layer-incoming', 'fm-layer-top', 'fm-layer-flat', 'fm-motion-active');
      clearInlineMotionStyles(fromEl);
      clearInlineMotionStyles(toEl);
      if (activeScreenCleanup === cleanup) {
        activeScreenCleanup = null;
      }
      stateMachine.releaseLock();
    }
    activeScreenCleanup = cleanup;

    if (typeof toEl.animate === 'function' && typeof fromEl.animate === 'function') {
      const outAnim = fromEl.animate(fromKeyframes, {
        duration: outgoingDurationMs || durationMs,
        easing: easingOut,
        fill: 'forwards'
      });
      const inAnim = toEl.animate(toKeyframes, {
        duration: durationMs,
        easing: easingIn,
        fill: 'forwards'
      });
      Promise.all([
        outAnim.finished.catch(function () {}),
        inAnim.finished.catch(function () {})
      ]).finally(cleanup);
    } else {
      cleanup();
    }

    runScreenContentArrivalHooks(toScreen, reduced);
  }

  /* ========================================================================
     SHARED PLANET TRANSITION: FOCUS ENTER & FOCUS EXIT TO HOME
     ======================================================================== */
  function animateFocusEnter(appBody, fromEl, toEl, reduced) {
    const durationMs = reduced ? 120 : 720;
    stateMachine.acquireLock('focus-enter', durationMs + 240, forceCleanupScreenLayers);
    stateMachine.setState('animating', 'focus-enter');

    if (typeof root.syncTimerProtectionBadge === 'function') {
      try {
        root.syncTimerProtectionBadge();
      } catch (_) {}
    }

    appBody.classList.add('fm-dual-transition', 'fm-focus-entering');
    fromEl.classList.add('fm-layer-outgoing', 'fm-motion-active');
    toEl.classList.add('fm-layer-incoming', 'fm-layer-top', 'fm-motion-active');

    const planetEl = toEl.querySelector('.uc-planet');
    const protectBtn = toEl.querySelector('.uc-protect');
    const headerEl = toEl.querySelector('.uc-header');
    const readoutEl = toEl.querySelector('.uc-readout');
    const digitsEl = toEl.querySelector('#timerDigits');
    const focusCardEl = toEl.querySelector('#ucFocusCard');
    const actionsEl = toEl.querySelector('.uc-actions');
    const rings = toEl.querySelectorAll('.uc-main-ring, .uc-far-orbit');
    const hasProtectionActive =
      typeof root.getSelectedDistractions === 'function' &&
      Array.isArray(root.getSelectedDistractions()) &&
      root.getSelectedDistractions().length > 0;

    let seedEl = null;
    if (!reduced && planetEl) {
      const planetRect = planetEl.getBoundingClientRect();
      const wrapperRect = (
        document.getElementById('screenWrapper') || document.body
      ).getBoundingClientRect();
      const startCx =
        lastTriggerRect && Date.now() - lastTriggerRect.timestamp < 2500
          ? lastTriggerRect.cx
          : wrapperRect.left + wrapperRect.width / 2;
      const startCy =
        lastTriggerRect && Date.now() - lastTriggerRect.timestamp < 2500
          ? lastTriggerRect.cy
          : wrapperRect.top + wrapperRect.height * 0.78;
      const startSize =
        lastTriggerRect && Date.now() - lastTriggerRect.timestamp < 2500
          ? Math.max(40, Math.min(54, lastTriggerRect.height || 46))
          : 46;
      const endSize = Math.max(planetRect.width || 190, 160);
      const endCx =
        planetRect.width > 0
          ? planetRect.left + planetRect.width / 2
          : wrapperRect.left + wrapperRect.width / 2;
      const endCy =
        planetRect.height > 0
          ? planetRect.top + planetRect.height / 2
          : wrapperRect.top + wrapperRect.height * 0.42;

      seedEl = document.createElement('div');
      seedEl.className = 'fm-shared-morph-proxy';
      seedEl.style.width = endSize + 'px';
      seedEl.style.height = endSize + 'px';
      seedEl.style.left = endCx - endSize / 2 + 'px';
      seedEl.style.top = endCy - endSize / 2 + 'px';
      seedEl.style.borderRadius = '50%';
      seedEl.style.background =
        'radial-gradient(circle at 38% 30%, rgba(196,181,253,0.92) 0%, rgba(124,58,237,0.82) 38%, rgba(30,27,75,0.96) 78%, rgba(5,7,20,1) 100%)';
      seedEl.style.boxShadow =
        '0 0 46px rgba(139,92,246,0.6), inset 0 0 24px rgba(192,132,252,0.38)';
      document.body.appendChild(seedEl);

      const dx = startCx - endCx;
      const dy = startCy - endCy;
      const scale0 = startSize / endSize;

      seedEl.animate(
        [
          {
            transform: 'translate3d(' + dx + 'px, ' + dy + 'px, 0) scale(' + scale0 + ')',
            opacity: 0.96
          },
          {
            transform: 'translate3d(' + dx * 0.28 + 'px, ' + dy * 0.22 + 'px, 0) scale(' + Math.max(scale0 * 1.9, 0.54) + ')',
            opacity: 0.94,
            offset: 0.44
          },
          {
            transform: 'translate3d(0px, 0px, 0) scale(1)',
            opacity: 0.82,
            offset: 0.78
          },
          {
            transform: 'translate3d(0px, 0px, 0) scale(1)',
            opacity: 0
          }
        ],
        {
          duration: 540,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'forwards'
        }
      );
    }

    let cleaned = false;
    function cleanup() {
      if (cleaned) return;
      cleaned = true;
      stateMachine.setState('cleaning', 'focus-enter');
      if (seedEl) {
        cancelElementAnimations(seedEl);
        seedEl.remove();
        seedEl = null;
      }
      cancelElementAnimations(fromEl);
      cancelElementAnimations(toEl);
      appBody.classList.remove('fm-dual-transition', 'fm-focus-entering');
      fromEl.classList.remove('fm-layer-outgoing', 'fm-motion-active');
      toEl.classList.remove('fm-layer-incoming', 'fm-layer-top', 'fm-motion-active');
      clearInlineMotionStyles(fromEl);
      clearInlineMotionStyles(toEl);
      if (activeScreenCleanup === cleanup) {
        activeScreenCleanup = null;
      }
      stateMachine.releaseLock();
    }
    activeScreenCleanup = cleanup;

    if (typeof fromEl.animate === 'function' && typeof toEl.animate === 'function') {
      // Phase 2: Source screen recedes calmly (translateY(-6px), scale(0.985), never slides left)
      fromEl.animate(
        [
          { transform: 'scale(1) translateY(0px)', opacity: 1 },
          {
            transform: reduced ? 'scale(1)' : 'scale(0.985) translateY(-6px)',
            opacity: 0
          }
        ],
        {
          duration: reduced ? 110 : 320,
          easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
          fill: 'forwards'
        }
      );

      // Phase 3: Cosmic backdrop deepens continuously
      const inAnim = toEl.animate(
        [
          { opacity: 0 },
          { opacity: 0.92, offset: 0.56 },
          { opacity: 1 }
        ],
        {
          duration: reduced ? 120 : 520,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'forwards'
        }
      );

      // Phase 4: planet and orbit rings grow together from the real planet center.
      // Rings keep their own resolved base transform (translate/rotate) and only gain a
      // trailing scale, so they never travel in from an offset position.
      const growKeyframes = function (base, from) {
        const prefix = base && base !== 'none' ? base + ' ' : '';
        return [
          { transform: prefix + 'scale(' + from + ')', opacity: 0 },
          { transform: prefix + 'scale(' + (from + (1 - from) * 0.76) + ')', offset: 0.56 },
          { transform: prefix + 'scale(1.012)', offset: 0.84 },
          { transform: prefix + 'scale(1)' }
        ];
      };
      if (!reduced && planetEl && typeof planetEl.animate === 'function') {
        cancelElementAnimations(planetEl);
        const planetFrames = growKeyframes('translate(-50%, -50%)', 0.42);
        planetFrames[0].opacity = 0.15;
        planetFrames[planetFrames.length - 1].opacity = 1;
        planetEl.animate(planetFrames, {
          duration: 600,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'backwards'
        });
      }

      if (!reduced) {
        rings.forEach(function (ring) {
          if (typeof ring.animate !== 'function') return;
          cancelElementAnimations(ring);
          const cs = root.getComputedStyle(ring);
          const finalOpacity = Number(cs.opacity);
          const frames = growKeyframes(cs.transform, 0.62);
          frames[frames.length - 1].opacity = Number.isFinite(finalOpacity) ? finalOpacity : 1;
          ring.animate(frames, {
            duration: 560,
            delay: 60,
            easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
            fill: 'backwards'
          });
        });

        if (headerEl && typeof headerEl.animate === 'function') {
          cancelElementAnimations(headerEl);
          headerEl.animate(
            [
              { transform: 'translateY(-6px)', opacity: 0 },
              { transform: 'translateY(0px)', opacity: 1 }
            ],
            {
              duration: 380,
              delay: 140,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        }

        if (readoutEl && typeof readoutEl.animate === 'function') {
          cancelElementAnimations(readoutEl);
          readoutEl.animate(
            [
              { transform: 'translate(-50%, -50%) scale(0.93)', opacity: 0 },
              { transform: 'translate(-50%, -50%) scale(1)', opacity: 1 }
            ],
            {
              duration: 420,
              delay: 175,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        } else if (digitsEl && typeof digitsEl.animate === 'function') {
          cancelElementAnimations(digitsEl);
          digitsEl.animate(
            [
              { transform: 'scale(0.93)', opacity: 0 },
              { transform: 'scale(1)', opacity: 1 }
            ],
            {
              duration: 420,
              delay: 175,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        }

        if (focusCardEl && typeof focusCardEl.animate === 'function') {
          cancelElementAnimations(focusCardEl);
          focusCardEl.style.transform = 'none';
          focusCardEl.animate(
            [
              { opacity: 0 },
              { opacity: 1 }
            ],
            {
              duration: 320,
              delay: 140,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        }

        if (actionsEl && typeof actionsEl.animate === 'function') {
          cancelElementAnimations(actionsEl);
          actionsEl.style.transform = 'none';
          actionsEl.animate(
            [
              { opacity: 0 },
              { opacity: 1 }
            ],
            {
              duration: 320,
              delay: 165,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        }

        // Phase 5: Fokus-Schutz badge fades in; the glow belongs to its own lifecycle
        // (rise, hold, decay) and is never cut off by the screen cleanup.
        if (protectBtn && typeof protectBtn.animate === 'function') {
          cancelElementAnimations(protectBtn);
          protectBtn.animate(
            [
              { transform: 'translateX(-50%) scale(0.96)', opacity: 0 },
              { transform: 'translateX(-50%) scale(1)', opacity: 1 }
            ],
            {
              duration: 380,
              delay: 220,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
              fill: 'backwards'
            }
          );
        }
      }

      if (protectBtn) {
        runProtectionGlow(protectBtn, hasProtectionActive && !reduced, 300);
      }

      setTimeout(cleanup, durationMs + 30);
      inAnim.finished.catch(function () {});
    } else {
      cleanup();
    }
  }

  let protectGlowTimer = null;
  function runProtectionGlow(badge, active, delayMs) {
    if (!badge) return;
    if (protectGlowTimer !== null) {
      clearTimeout(protectGlowTimer);
      protectGlowTimer = null;
    }
    badge.classList.remove('fm-protect-glow');
    if (!active) return;
    badge.style.setProperty('--fm-protect-glow-delay', Math.max(0, delayMs || 0) + 'ms');
    void badge.offsetWidth;
    badge.classList.add('fm-protect-glow');
    const finish = function (ev) {
      if (ev && ev.animationName && ev.animationName !== 'fmProtectGlow') return;
      badge.removeEventListener('animationend', finish);
      if (protectGlowTimer !== null) {
        clearTimeout(protectGlowTimer);
        protectGlowTimer = null;
      }
      badge.classList.remove('fm-protect-glow');
      badge.style.removeProperty('--fm-protect-glow-delay');
    };
    badge.addEventListener('animationend', finish);
    protectGlowTimer = setTimeout(finish, (delayMs || 0) + 1400);
  }

  function animateFocusExitToHome(appBody, fromEl, toEl, reduced) {
    const durationMs = reduced ? 120 : 440;
    stateMachine.acquireLock('focus-exit-home', durationMs + 200, forceCleanupScreenLayers);
    stateMachine.setState('animating', 'focus-exit-home');

    appBody.classList.add('fm-dual-transition');
    fromEl.classList.add('fm-layer-outgoing', 'fm-layer-top', 'fm-motion-active');
    toEl.classList.add('fm-layer-incoming', 'fm-motion-active');

    const planetEl = fromEl.querySelector('.uc-planet');
    const plusBtn = document.getElementById('bottomNavPlus');

    let seedEl = null;
    if (!reduced && planetEl && plusBtn) {
      const planetRect = planetEl.getBoundingClientRect();
      const plusRect = plusBtn.getBoundingClientRect();
      if (planetRect.width > 0 && plusRect.width > 0) {
        const startSize = planetRect.width;
        const startCx = planetRect.left + planetRect.width / 2;
        const startCy = planetRect.top + planetRect.height / 2;
        const endSize = plusRect.width;
        const endCx = plusRect.left + plusRect.width / 2;
        const endCy = plusRect.top + plusRect.height / 2;

        seedEl = document.createElement('div');
        seedEl.className = 'fm-shared-morph-proxy';
        seedEl.style.width = startSize + 'px';
        seedEl.style.height = startSize + 'px';
        seedEl.style.left = startCx - startSize / 2 + 'px';
        seedEl.style.top = startCy - startSize / 2 + 'px';
        seedEl.style.borderRadius = '50%';
        seedEl.style.background =
          'radial-gradient(circle at 42% 32%, #A78BFA 0%, #7C3AED 45%, #4C1D95 100%)';
        seedEl.style.boxShadow = '0 0 34px rgba(124, 58, 237, 0.58)';
        document.body.appendChild(seedEl);

        const dx = endCx - startCx;
        const dy = endCy - startCy;
        const endScale = endSize / startSize;

        seedEl.animate(
          [
            { transform: 'translate3d(0px, 0px, 0) scale(1)', opacity: 0.95 },
            {
              transform: 'translate3d(' + dx + 'px, ' + dy + 'px, 0) scale(' + endScale + ')',
              opacity: 0.92,
              offset: 0.86
            },
            {
              transform: 'translate3d(' + dx + 'px, ' + dy + 'px, 0) scale(' + endScale * 0.85 + ')',
              opacity: 0
            }
          ],
          {
            duration: durationMs,
            easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
            fill: 'forwards'
          }
        );
      }
    }

    let cleaned = false;
    function cleanup() {
      if (cleaned) return;
      cleaned = true;
      stateMachine.setState('cleaning', 'focus-exit-home');
      if (seedEl) {
        cancelElementAnimations(seedEl);
        seedEl.remove();
        seedEl = null;
      }
      cancelElementAnimations(fromEl);
      cancelElementAnimations(toEl);
      appBody.classList.remove('fm-dual-transition');
      fromEl.classList.remove('fm-layer-outgoing', 'fm-layer-top', 'fm-motion-active');
      toEl.classList.remove('fm-layer-incoming', 'fm-motion-active');
      clearInlineMotionStyles(fromEl);
      clearInlineMotionStyles(toEl);
      if (activeScreenCleanup === cleanup) {
        activeScreenCleanup = null;
      }
      stateMachine.releaseLock();

      if (!reduced && plusBtn && typeof plusBtn.animate === 'function') {
        plusBtn.animate(
          [
            { transform: 'scale(1)' },
            { transform: 'scale(1.13)', offset: 0.42 },
            { transform: 'scale(0.96)', offset: 0.75 },
            { transform: 'scale(1)' }
          ],
          {
            duration: 280,
            easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
          }
        );
      }
    }
    activeScreenCleanup = cleanup;

    if (typeof fromEl.animate === 'function' && typeof toEl.animate === 'function') {
      fromEl.animate(
        [
          { opacity: 1, transform: 'scale(1)' },
          { opacity: 0, transform: reduced ? 'scale(1)' : 'scale(0.96)' }
        ],
        {
          duration: Math.round(durationMs * 0.78),
          easing: 'cubic-bezier(0.4, 0, 1, 1)',
          fill: 'forwards'
        }
      );
      toEl.animate(
        [
          { opacity: 0.35, transform: reduced ? 'scale(1)' : 'scale(0.985)' },
          { opacity: 1, transform: 'scale(1)' }
        ],
        {
          duration: durationMs,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );
      setTimeout(cleanup, durationMs + 25);
    } else {
      cleanup();
    }
  }

  /* ========================================================================
     BOTTOM NAVIGATION DOCK: SINGLE PERSISTENT ACTIVE INDICATOR (v17.8)
     The glider's resting position comes from nav[data-lit] in CSS. This module is
     the only writer of data-lit and the only animator of the glider. Geometry is
     expressed in the glider's own width (CSS calc), so no layout is read per frame.
     ======================================================================== */
  // [multiple of glider width, extra px] for each root (see .fm-nav-glider in navigation.css).
  const DOCK_GLIDER_POS = Object.freeze({
    home: [0, 0],
    missions: [1, 2],
    history: [2, 62],
    settings: [3, 64]
  });
  const dockState = { anim: null, tracking: null };

  function dockGliderTransform(from, to, progress) {
    const a = DOCK_GLIDER_POS[from];
    const b = DOCK_GLIDER_POS[to] || a;
    const p = Math.max(0, Math.min(1, progress || 0));
    const mul = a[0] + (b[0] - a[0]) * p;
    const px = a[1] + (b[1] - a[1]) * p;
    return 'translate3d(calc(' + (mul * 100).toFixed(3) + '% + ' + px.toFixed(2) + 'px), 0, 0)';
  }

  function getDockParts() {
    if (typeof document === 'undefined') return null;
    const nav = document.getElementById('bottomNavigationV17');
    const glider = document.getElementById('bottomNavGlider');
    return nav && glider ? { nav: nav, glider: glider } : null;
  }

  function dockCrossesPlus(from, to) {
    return getRootTabIndex(from) <= 1 !== getRootTabIndex(to) <= 1;
  }

  function stopDockAnimation(glider) {
    if (!dockState.anim) return null;
    let current = null;
    try {
      current = getComputedStyle(glider).transform;
    } catch (_) {}
    try {
      dockState.anim.cancel();
    } catch (_) {}
    dockState.anim = null;
    return current && current !== 'none' ? current : null;
  }

  function runDockAnimation(parts, frames, durationMs, easing) {
    const nav = parts.nav;
    if (typeof parts.glider.animate !== 'function' || durationMs <= 0) {
      nav.style.removeProperty('--fm-dock-dur');
      return;
    }
    const anim = parts.glider.animate(frames, { duration: durationMs, easing: easing });
    dockState.anim = anim;
    anim.finished
      .then(function () {
        if (dockState.anim === anim) {
          dockState.anim = null;
          nav.style.removeProperty('--fm-dock-dur');
        }
      })
      .catch(function () {});
  }

  function clearDockTracking(parts) {
    const t = dockState.tracking;
    dockState.tracking = null;
    if (!t) return null;
    parts.nav.classList.remove('fm-dock-tracking');
    [t.fromBtn, t.toBtn].forEach(function (btn) {
      if (!btn) return;
      btn.classList.remove('fm-nav-tracked');
      btn.style.removeProperty('--fm-emph');
    });
    const start = parts.glider.style.transform || null;
    parts.glider.style.removeProperty('transform');
    return { tracking: t, start: start };
  }

  /* Settle the dock on a root section. Called by navigation.js whenever the route's
     root changes; animates in step with the root page transition (same metrics). */
  function syncDockIndicator(section, options) {
    const parts = getDockParts();
    if (!parts || !DOCK_GLIDER_POS[section]) return;
    const opts = options && typeof options === 'object' ? options : {};
    const nav = parts.nav;
    if (dockState.tracking) clearDockTracking(parts);
    const from = nav.dataset.lit;
    if (from === section) return;

    const start = stopDockAnimation(parts.glider);
    nav.dataset.lit = section;
    if (opts.instant || !DOCK_GLIDER_POS[from]) {
      nav.style.removeProperty('--fm-dock-dur');
      return;
    }

    const reduced = prefersReducedMotion();
    const metrics = computeRootTabMetrics(from, section);
    const durationMs = reduced ? 120 : opts.durationMs || metrics.durationMs || 360;
    nav.style.setProperty('--fm-dock-dur', durationMs + 'ms');
    const end = dockGliderTransform(section, section, 0);
    let frames;
    if (reduced) {
      frames = [{ opacity: 0 }, { opacity: 1 }];
    } else if (dockCrossesPlus(from, section)) {
      frames = [
        { transform: start || dockGliderTransform(from, from, 0), opacity: 1 },
        { opacity: 0.6, offset: 0.5 },
        { transform: end, opacity: 1 }
      ];
    } else {
      frames = [{ transform: start || dockGliderTransform(from, from, 0) }, { transform: end }];
    }
    runDockAnimation(parts, frames, durationMs, opts.easing || 'cubic-bezier(0.22, 1, 0.36, 1)');
  }

  /* Interactive root swipe: the glider and both icons follow real progress. */
  function beginDockTracking(from, to) {
    const parts = getDockParts();
    if (!parts || !DOCK_GLIDER_POS[from] || !DOCK_GLIDER_POS[to] || from === to) return false;
    stopDockAnimation(parts.glider);
    if (dockState.tracking) clearDockTracking(parts);
    const nav = parts.nav;
    nav.dataset.lit = from;
    nav.style.removeProperty('--fm-dock-dur');
    const fromBtn = nav.querySelector('[data-nav-section="' + from + '"]');
    const toBtn = nav.querySelector('[data-nav-section="' + to + '"]');
    nav.classList.add('fm-dock-tracking');
    if (fromBtn) fromBtn.classList.add('fm-nav-tracked');
    if (toBtn) toBtn.classList.add('fm-nav-tracked');
    dockState.tracking = { from: from, to: to, fromBtn: fromBtn, toBtn: toBtn };
    setDockTrackingProgress(0);
    return true;
  }

  function setDockTrackingProgress(progress) {
    const t = dockState.tracking;
    const parts = t && getDockParts();
    if (!parts) return;
    const p = Math.max(0, Math.min(1, progress || 0));
    parts.glider.style.transform = dockGliderTransform(t.from, t.to, p);
    if (t.fromBtn) t.fromBtn.style.setProperty('--fm-emph', (1 - p).toFixed(3));
    if (t.toBtn) t.toBtn.style.setProperty('--fm-emph', p.toFixed(3));
  }

  /* Release: settle toward the destination (commit) or back to the source (cancel),
     with the page's own settle duration. The later route commit finds data-lit already
     correct and does nothing, so the destination settles exactly once. */
  function endDockTracking(commit, durationMs, easing) {
    const parts = getDockParts();
    if (!parts || !dockState.tracking) return;
    const nav = parts.nav;
    const ms = Math.max(0, Math.round(durationMs || 0));
    nav.style.setProperty('--fm-dock-dur', Math.max(1, ms) + 'ms');
    const cleared = clearDockTracking(parts);
    const target = commit ? cleared.tracking.to : cleared.tracking.from;
    nav.dataset.lit = target;
    if (!cleared.start || ms <= 0) {
      nav.style.removeProperty('--fm-dock-dur');
      return;
    }
    runDockAnimation(
      parts,
      [{ transform: cleared.start }, { transform: dockGliderTransform(target, target, 0) }],
      ms,
      easing || 'cubic-bezier(0.2, 0.75, 0.25, 1)'
    );
  }

  function handleSameRootTabTap(section) {
    if (typeof document === 'undefined') return;
    const reduced = prefersReducedMotion();
    const screenEl = document.getElementById('app-screen-' + section);
    const scrollContainer =
      (section === 'missions' && document.getElementById('missionManagementListV116')) ||
      screenEl;

    if (scrollContainer && scrollContainer.scrollTop > 4) {
      try {
        scrollContainer.scrollTo({
          top: 0,
          behavior: reduced ? 'auto' : 'smooth'
        });
      } catch (_) {
        scrollContainer.scrollTop = 0;
      }
    }

    if (!reduced) {
      const nav = document.getElementById('bottomNavigationV17');
      const btn = nav ? nav.querySelector('[data-nav-section="' + section + '"]') : null;
      const icon = btn ? btn.querySelector('.fm-nav-icon') : null;
      if (icon && typeof icon.animate === 'function') {
        cancelElementAnimations(icon);
        icon.animate(
          [
            { transform: 'scale(1.06)' },
            { transform: 'scale(0.94)', offset: 0.35 },
            { transform: 'scale(1.1)', offset: 0.72 },
            { transform: 'scale(1.06)' }
          ],
          {
            duration: 220,
            easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
          }
        );
      }
    }
  }

  let plusAbortController = null;

  /* ========================================================================
     CENTRAL PLUS BUTTON & FOCUS CHOICE SHEET MORPH (#focusChoiceOverlay)
     ======================================================================== */
  function openPlusSheet() {
    if (typeof document === 'undefined') return;
    const overlay = document.getElementById('focusChoiceOverlay');
    const wrapper = document.getElementById('screenWrapper');
    const plusBtn = document.getElementById('bottomNavPlus');
    const bottomNav = document.getElementById('bottomNavigationV17');
    if (!overlay) return;

    if (plusCloseTimer !== null) {
      clearTimeout(plusCloseTimer);
      plusCloseTimer = null;
    }

    // Toggle close if already open and not currently closing
    if (
      !overlay.classList.contains('hidden') &&
      wrapper?.classList.contains('focus-choice-open')
    ) {
      closePlusSheet();
      return;
    }

    if (typeof root.renderFocusChoiceV116 === 'function') {
      try {
        root.renderFocusChoiceV116();
      } catch (_) {}
    }

    const backdrop = overlay.querySelector('.focus-choice-backdrop');
    const sheet = overlay.querySelector('.focus-choice-sheet');

    cancelElementAnimations(overlay);
    cancelElementAnimations(backdrop);
    cancelElementAnimations(sheet);
    cancelElementAnimations(plusBtn);
    clearInlineMotionStyles(overlay);
    clearInlineMotionStyles(backdrop);
    clearInlineMotionStyles(sheet);

    overlay.classList.remove('hidden', 'focus-choice-leave', 'focus-choice-enter');
    wrapper?.classList.add('focus-choice-open');
    plusBtn?.classList.add('fm-plus-open');
    bottomNav?.classList.add('fm-plus-dock-active');

    // Keep the floating bottom navigation sharp and visible while Plus panel is open
    if (typeof root.showBottomNavigation === 'function') {
      try {
        root.showBottomNavigation();
      } catch (_) {}
    }

    if (plusAbortController) {
      plusAbortController.abort();
    }
    plusAbortController = new AbortController();
    document.addEventListener(
      'keydown',
      function (ev) {
        if (ev.key === 'Escape' && !overlay.classList.contains('hidden')) {
          ev.stopPropagation();
          if (typeof root.closeFocusChoice === 'function') {
            root.closeFocusChoice();
          } else {
            closePlusSheet();
          }
        }
      },
      { signal: plusAbortController.signal }
    );

    if (typeof root.syncChoiceChrome === 'function') {
      try {
        root.syncChoiceChrome();
      } catch (_) {}
    }
    if (root.lucide && typeof root.lucide.createIcons === 'function') {
      try {
        root.lucide.createIcons();
      } catch (_) {}
    }

    const reduced = prefersReducedMotion();

    if (plusBtn && typeof plusBtn.animate === 'function' && !reduced) {
      plusBtn.animate(
        [
          { transform: 'scale(1)' },
          { transform: 'scale(0.88)', offset: 0.42 },
          { transform: 'scale(0.94)' }
        ],
        {
          duration: 240,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
        }
      );
    }

    if (backdrop && typeof backdrop.animate === 'function') {
      backdrop.animate(
        [{ opacity: 0 }, { opacity: 1 }],
        {
          duration: reduced ? 80 : 230,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'forwards'
        }
      );
    }

    if (sheet && typeof sheet.animate === 'function') {
      sheet.animate(
        [
          {
            transform: reduced ? 'translateY(0px) scale(1)' : 'translateY(34px) scale(0.9)',
            opacity: 0
          },
          {
            transform: 'translateY(0px) scale(1)',
            opacity: 1
          }
        ],
        {
          duration: reduced ? 90 : 320,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'forwards'
        }
      );

      if (!reduced) {
        const items = sheet.querySelectorAll(
          '.fm-plus-header, .fm-plus-card, .fm-plus-missions-section, .fm-plus-footer-section'
        );
        items.forEach(function (item, idx) {
          cancelElementAnimations(item);
          if (typeof item.animate === 'function') {
            item.animate(
              [
                { transform: 'translateY(10px)', opacity: 0 },
                { transform: 'translateY(0px)', opacity: 1 }
              ],
              {
                duration: 260,
                delay: Math.min(idx * 35, 160),
                easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                fill: 'backwards'
              }
            );
          }
        });
      }
    }
  }

  function closePlusSheet(options) {
    if (typeof document === 'undefined') return;
    const opts = options && typeof options === 'object' ? options : {};
    const overlay = document.getElementById('focusChoiceOverlay');
    const wrapper = document.getElementById('screenWrapper');
    const plusBtn = document.getElementById('bottomNavPlus');
    const bottomNav = document.getElementById('bottomNavigationV17');
    if (!overlay) return;

    if (plusCloseTimer !== null) {
      clearTimeout(plusCloseTimer);
      plusCloseTimer = null;
    }
    if (plusAbortController) {
      plusAbortController.abort();
      plusAbortController = null;
    }

    plusBtn?.classList.remove('fm-plus-open');
    bottomNav?.classList.remove('fm-plus-dock-active');

    if (overlay.classList.contains('hidden')) {
      wrapper?.classList.remove('focus-choice-open');
      return;
    }

    const backdrop = overlay.querySelector('.focus-choice-backdrop');
    const sheet = overlay.querySelector('.focus-choice-sheet');
    const reduced = prefersReducedMotion();

    function finalizeClose() {
      if (plusCloseTimer !== null) {
        clearTimeout(plusCloseTimer);
        plusCloseTimer = null;
      }
      cancelElementAnimations(overlay);
      cancelElementAnimations(backdrop);
      cancelElementAnimations(sheet);
      clearInlineMotionStyles(overlay);
      clearInlineMotionStyles(backdrop);
      clearInlineMotionStyles(sheet);
      overlay.classList.remove('focus-choice-enter', 'focus-choice-leave');
      overlay.classList.add('hidden');
      wrapper?.classList.remove('focus-choice-open');
      if (typeof root.syncBottomNavigationForScreen === 'function') {
        try {
          const pending = root.__navigationStateV17 && root.__navigationStateV17.pendingScreen;
          if (!pending) {
            root.syncBottomNavigationForScreen();
          }
        } catch (_) {}
      }
    }

    if (opts.immediate || reduced || !sheet || typeof sheet.animate !== 'function') {
      finalizeClose();
      return;
    }

    cancelElementAnimations(backdrop);
    cancelElementAnimations(sheet);

    if (backdrop && typeof backdrop.animate === 'function') {
      backdrop.animate(
        [{ opacity: 1 }, { opacity: 0 }],
        {
          duration: 190,
          easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
          fill: 'forwards'
        }
      );
    }

    const sheetOut = sheet.animate(
      [
        { transform: 'translateY(0px) scale(1)', opacity: 1 },
        { transform: 'translateY(26px) scale(0.9)', opacity: 0 }
      ],
      {
        duration: 210,
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
        fill: 'forwards'
      }
    );

    plusCloseTimer = setTimeout(finalizeClose, 230);
    sheetOut.finished.catch(function () {}).finally(finalizeClose);
  }

  /* ========================================================================
     MISSION THREE-DOT MENU ACTION SHEET (#missionSheetV116)
     ======================================================================== */
  function openMissionSheet(contentHtml, triggerEl) {
    if (typeof document === 'undefined') return;
    if (triggerEl && typeof triggerEl.getBoundingClientRect === 'function') {
      lastMenuTriggerEl = triggerEl;
    }
    if (typeof root.ensureMissionSheetV116 === 'function') {
      try {
        root.ensureMissionSheetV116();
      } catch (_) {}
    }
    const sheet = document.getElementById('missionSheetV116');
    if (!sheet) return;

    if (missionSheetCloseTimer !== null) {
      clearTimeout(missionSheetCloseTimer);
      missionSheetCloseTimer = null;
    }

    const wasAlreadyOpen = sheet.classList.contains('open');
    cancelElementAnimations(sheet);

    let card = document.getElementById('missionSheetCardV116') || sheet.querySelector('.mission-sheet-card-v116');
    if (!card) {
      sheet.innerHTML =
        '<div class="mission-sheet-card-v116" id="missionSheetCardV116" onclick="event.stopPropagation()"></div>';
      card = document.getElementById('missionSheetCardV116');
    }
    card.setAttribute('onclick', 'event.stopPropagation()');
    const translatedHtml =
      typeof root.translateLiteralText === 'function'
        ? root.translateLiteralText(contentHtml)
        : contentHtml;
    const hasHandle = String(translatedHtml || '').includes('mission-sheet-handle-v175');
    card.innerHTML =
      (hasHandle ? '' : '<div class="mission-sheet-handle-v175" aria-hidden="true"></div>') +
      translatedHtml;

    sheet.classList.add('open');
    clearInlineMotionStyles(sheet);
    clearInlineMotionStyles(card);

    if (card && lastMenuTriggerEl && typeof lastMenuTriggerEl.getBoundingClientRect === 'function') {
      try {
        const trigRect = lastMenuTriggerEl.getBoundingClientRect();
        const sheetRect = sheet.getBoundingClientRect();
        if (trigRect.width > 0 && sheetRect.width > 0) {
          const relX = Math.round(
            ((trigRect.left + trigRect.width / 2 - sheetRect.left) / sheetRect.width) * 100
          );
          card.style.setProperty('--fm-sheet-origin-x', Math.max(15, Math.min(92, relX)) + '%');
        }
      } catch (_) {}
    }

    if (root.lucide && typeof root.lucide.createIcons === 'function') {
      try {
        root.lucide.createIcons();
      } catch (_) {}
    }

    if (missionSheetAbortController) {
      missionSheetAbortController.abort();
    }
    missionSheetAbortController = new AbortController();
    document.addEventListener(
      'keydown',
      function (ev) {
        if (ev.key === 'Escape' && sheet.classList.contains('open')) {
          ev.stopPropagation();
          closeMissionSheet();
        }
      },
      { signal: missionSheetAbortController.signal }
    );

    const reduced = prefersReducedMotion();
    if (!card || typeof card.animate !== 'function') return;

    cancelElementAnimations(card);

    if (wasAlreadyOpen) {
      card.animate(
        [
          { transform: reduced ? 'scale(1)' : 'scale(0.97) translateY(4px)', opacity: 0.65 },
          { transform: 'scale(1) translateY(0px)', opacity: 1 }
        ],
        {
          duration: reduced ? 80 : 190,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
        }
      );
    } else {
      sheet.animate(
        [{ opacity: 0 }, { opacity: 1 }],
        {
          duration: reduced ? 80 : 190,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
        }
      );
      card.animate(
        [
          {
            transform: reduced ? 'scale(1)' : 'scale(0.95) translateY(12px)',
            opacity: 0
          },
          {
            transform: 'scale(1) translateY(0px)',
            opacity: 1
          }
        ],
        {
          duration: reduced ? 80 : 240,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
        }
      );

      if (!reduced) {
        const actions = card.querySelectorAll('.mission-sheet-action-v116');
        actions.forEach(function (btn, idx) {
          if (typeof btn.animate === 'function') {
            btn.animate(
              [
                { transform: 'translateY(6px)', opacity: 0 },
                { transform: 'translateY(0px)', opacity: 1 }
              ],
              {
                duration: 200,
                delay: Math.min(idx * 25, 130),
                easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                fill: 'backwards'
              }
            );
          }
        });
      }
    }
  }

  function closeMissionSheet(options) {
    if (typeof document === 'undefined') return;
    const opts = options && typeof options === 'object' ? options : {};
    const sheet = document.getElementById('missionSheetV116');
    if (!sheet) return;

    if (missionSheetCloseTimer !== null) {
      clearTimeout(missionSheetCloseTimer);
      missionSheetCloseTimer = null;
    }
    if (missionSheetAbortController) {
      missionSheetAbortController.abort();
      missionSheetAbortController = null;
    }

    const card = document.getElementById('missionSheetCardV116') || sheet.querySelector('.mission-sheet-card-v116');

    if (!sheet.classList.contains('open')) {
      if (card) card.innerHTML = '';
      clearInlineMotionStyles(sheet);
      if (typeof root.syncBottomNavigationForScreen === 'function') {
        try {
          root.syncBottomNavigationForScreen();
        } catch (_) {}
      }
      return;
    }

    const reduced = prefersReducedMotion();

    function finalizeSheetClose() {
      if (missionSheetCloseTimer !== null) {
        clearTimeout(missionSheetCloseTimer);
        missionSheetCloseTimer = null;
      }
      cancelElementAnimations(sheet);
      if (card) {
        cancelElementAnimations(card);
        clearInlineMotionStyles(card);
        card.innerHTML = '';
      }
      clearInlineMotionStyles(sheet);
      sheet.classList.remove('open');
      if (typeof root.syncBottomNavigationForScreen === 'function') {
        try {
          root.syncBottomNavigationForScreen();
        } catch (_) {}
      }
      if (
        !opts.skipFocusRestore &&
        lastMenuTriggerEl &&
        document.body.contains(lastMenuTriggerEl) &&
        typeof lastMenuTriggerEl.focus === 'function'
      ) {
        try {
          lastMenuTriggerEl.focus({ preventScroll: true });
        } catch (_) {}
      }
    }

    if (opts.immediate || reduced || !card || typeof card.animate !== 'function') {
      finalizeSheetClose();
      return;
    }

    cancelElementAnimations(sheet);
    cancelElementAnimations(card);

    sheet.animate(
      [{ opacity: 1 }, { opacity: 0 }],
      {
        duration: 165,
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
        fill: 'forwards'
      }
    );
    const outAnim = card.animate(
      [
        { transform: 'scale(1) translateY(0px)', opacity: 1 },
        { transform: 'scale(0.95) translateY(8px)', opacity: 0 }
      ],
      {
        duration: 175,
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
        fill: 'forwards'
      }
    );

    missionSheetCloseTimer = setTimeout(finalizeSheetClose, 195);
    outAnim.finished.catch(function () {}).finally(finalizeSheetClose);
  }

  /* ========================================================================
     CONTENT ARRIVAL STAGGERS, PLAN STEP EXPANSION & ACTIVITY / HISTORY
     ======================================================================== */
  function runScreenContentArrivalHooks(screenName, reduced) {
    if (typeof document === 'undefined' || reduced) return;

    if (screenName === 'plan') {
      const steps = document.querySelectorAll('#aiPlanSteps .flow-step-line');
      steps.forEach(function (step, idx) {
        if (typeof step.animate === 'function') {
          cancelElementAnimations(step);
          step.animate(
            [
              { transform: 'translateY(8px)', opacity: 0.2 },
              { transform: 'translateY(0px)', opacity: 1 }
            ],
            {
              duration: 230,
              delay: Math.min(idx * 32, 130),
              easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
              fill: 'backwards'
            }
          );
        }
      });
    }
  }

  /* v17.8: Mission A <-> B on the Mission Plan. Content is already re-rendered in the
     new Mission's tone; this only fades it in once (opacity + small translate). */
  function animateMissionPlanSwitch() {
    if (typeof document === 'undefined') return;
    const reduced = prefersReducedMotion();
    const targets = [
      document.getElementById('aiPlanHero'),
      document.querySelector('#app-screen-plan .mp-section-head'),
      document.getElementById('aiPlanSteps')
    ].filter(Boolean);
    targets.forEach(function (el, i) {
      if (typeof el.animate !== 'function') return;
      cancelElementAnimations(el);
      el.animate(
        reduced
          ? [{ opacity: 0.6 }, { opacity: 1 }]
          : [
              { opacity: 0.35, transform: 'translate3d(0, 6px, 0)' },
              { opacity: 1, transform: 'translate3d(0, 0, 0)' }
            ],
        {
          duration: reduced ? 100 : 240,
          delay: reduced ? 0 : i * 30,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          fill: 'backwards'
        }
      );
    });
  }

  function installFeatureMotionEnhancements() {
    if (typeof document === 'undefined') return;

    // 0. Fast-path guard for lucide.createIcons to prevent full-DOM SVG rebuild jank during 60fps transitions
    if (root.lucide && typeof root.lucide.createIcons === 'function' && !root.lucide.createIcons.__fmFastWrapped) {
      const origCreateIcons = root.lucide.createIcons.bind(root.lucide);
      const fastCreateIcons = function (options) {
        if (!options && typeof document !== 'undefined') {
          if (!document.querySelector('[data-lucide]:not(svg)')) {
            return;
          }
        }
        return origCreateIcons(options);
      };
      fastCreateIcons.__fmFastWrapped = true;
      root.lucide.createIcons = fastCreateIcons;
    }

    // 1. Mission Sheet (3-dot menu) Repeatable Motion Hook
    if (typeof root.openMissionSheetV116 === 'function') {
      root.openMissionSheetV116 = function (content) {
        openMissionSheet(content);
      };
    }
    if (typeof root.closeMissionSheetV116 === 'function') {
      root.closeMissionSheetV116 = function (options) {
        closeMissionSheet(options);
      };
    }

    // 2. Mission Plan Step Expand/Collapse & Conditional Scroll (Step >= 2 only)
    if (typeof root.toggleFutureStepV81 === 'function') {
      const origToggleStep = root.toggleFutureStepV81;
      root.toggleFutureStepV81 = function (index) {
        const list = document.getElementById('aiPlanSteps');
        const beforeCards = list ? [...list.querySelectorAll('.flow-step-line')] : [];
        const beforeHeights = beforeCards.map(function (c) {
          return c.getBoundingClientRect().height;
        });

        const res = origToggleStep.apply(this, arguments);

        if (!prefersReducedMotion() && list) {
          const afterCards = [...list.querySelectorAll('.flow-step-line')];
          const targetCard = afterCards[index];
          const prevH = beforeHeights[index];
          if (targetCard && prevH && typeof targetCard.animate === 'function') {
            const nextH = targetCard.getBoundingClientRect().height;
            if (Math.abs(nextH - prevH) > 2) {
              targetCard.animate(
                [
                  { height: prevH + 'px', overflow: 'hidden' },
                  { height: nextH + 'px', overflow: 'hidden' }
                ],
                {
                  duration: 240,
                  easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
                }
              );
            }
          }
        }
        return res;
      };
    }

    if (typeof root.applyMissionDepthV105 === 'function') {
      root.applyMissionDepthV105 = function (center) {
        const list = document.getElementById('aiPlanSteps');
        if (!list) return;
        const cards = [...list.querySelectorAll('.flow-step-line')];
        const ai = cards.findIndex(function (c) {
          return c.classList.contains('mission-step-active');
        });
        cards.forEach(function (c, i) {
          c.classList.remove(
            'mission-depth-near',
            'mission-depth-far',
            'mission-depth-before',
            'mission-depth-after'
          );
          if (ai < 0 || i === ai) return;
          c.classList.add(
            Math.abs(i - ai) === 1 ? 'mission-depth-near' : 'mission-depth-far',
            i < ai ? 'mission-depth-before' : 'mission-depth-after'
          );
        });
        const active = cards[ai];
        if (active && !active.querySelector('.mission-active-start')) {
          const b = document.createElement('button');
          b.type = 'button';
          b.className = 'mission-active-start';
          b.innerHTML = '<i data-lucide="play"></i><span>Schritt ' + (ai + 1) + ' starten</span>';
          b.onclick = function (e) {
            e.stopPropagation();
            if (typeof root.prepareLearningSession === 'function') {
              root.prepareLearningSession();
            }
          };
          active.appendChild(b);
        }
        if (root.lucide && typeof root.lucide.createIcons === 'function') {
          try {
            root.lucide.createIcons();
          } catch (_) {}
        }
        // Only auto-scroll if active step is Step 2 or later (ai >= 1) and not comfortably visible
        if (center && active && ai >= 1) {
          requestAnimationFrame(function () {
            const planScreen = document.getElementById('app-screen-plan');
            if (!planScreen) return;
            const screenRect = planScreen.getBoundingClientRect();
            const cardRect = active.getBoundingClientRect();
            const comfortablyVisible =
              cardRect.top >= screenRect.top + 48 &&
              cardRect.bottom <= screenRect.bottom - 96;
            if (!comfortablyVisible && typeof active.scrollIntoView === 'function') {
              active.scrollIntoView({
                behavior: prefersReducedMotion() ? 'auto' : 'smooth',
                block: 'center'
              });
            }
          });
        }
      };
    }

    // 3. History Filter Smooth Crossfade
    if (typeof root.setHistoryFilter === 'function') {
      const origSetHistoryFilter = root.setHistoryFilter;
      root.setHistoryFilter = function (filter, button) {
        const res = origSetHistoryFilter.apply(this, arguments);
        if (!prefersReducedMotion()) {
          const list = document.getElementById('sessionHistoryList');
          if (list && typeof list.animate === 'function') {
            cancelElementAnimations(list);
            list.animate(
              [
                { opacity: 0.32, transform: 'translateY(5px)' },
                { opacity: 1, transform: 'translateY(0px)' }
              ],
              {
                duration: 190,
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)'
              }
            );
          }
        }
        return res;
      };
    }

    // 4. Mission Management Tab Switch Crossfade
    ['setMissionFilterV116', 'setMissionManagementTabV116'].forEach(function (fnName) {
      if (typeof root[fnName] === 'function' && !root[fnName].__fmWrapped) {
        const origFn = root[fnName];
        const wrappedFn = function () {
          const res = origFn.apply(this, arguments);
          if (!prefersReducedMotion()) {
            const list = document.getElementById('missionManagementListV116');
            if (list && typeof list.animate === 'function') {
              cancelElementAnimations(list);
              list.animate(
                [
                  { opacity: 0.35, transform: 'translateY(5px)' },
                  { opacity: 1, transform: 'translateY(0px)' }
                ],
                {
                  duration: 190,
                  easing: 'cubic-bezier(0.16, 1, 0.3, 1)'
                }
              );
            }
          }
          return res;
        };
        wrappedFn.__fmWrapped = true;
        root[fnName] = wrappedFn;
      }
    });

    // 5. Activity Timeline Single New Row Insertion Animation
    const activityList = document.getElementById('homeActivityList');
    if (activityList && typeof MutationObserver === 'function') {
      const captureFirstKey = function () {
        const first = activityList.querySelector('.home-activity-row');
        return first ? first.textContent.trim().slice(0, 80) : null;
      };
      knownActivityTopKey = captureFirstKey();

      const observer = new MutationObserver(function () {
        const first = activityList.querySelector('.home-activity-row');
        const nextKey = first ? first.textContent.trim().slice(0, 80) : null;
        if (nextKey && knownActivityTopKey !== nextKey) {
          if (!prefersReducedMotion() && first && typeof first.animate === 'function') {
            first.animate(
              [
                { transform: 'translateY(-10px)', opacity: 0 },
                { transform: 'translateY(0px)', opacity: 1 }
              ],
              {
                duration: 260,
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)'
              }
            );
          }
        }
        knownActivityTopKey = nextKey;
      });
      observer.observe(activityList, { childList: true });
    }
  }

  /* ========================================================================
     INTERACTIVE NAVIGATION GESTURES — one owner for stacked swipe-back and
     adjacent root-page swipe.
     Priority: 1. open overlay, menu, sheet or dialog → no navigation gesture
               2. marked or scrollable horizontal control → the control owns it
               3. left-edge swipe-back on stacked screens
               4. root-page swipe between adjacent root pages
               5. vertical scrolling (left to the browser via touch-action)
     Business navigation stays authoritative: motion only visualizes the decision
     and the route is committed exactly once through navigateBack/navigateToRoot.
     ======================================================================== */
  let navigationGesturesInstalled = false;

  const SWIPEABLE_STACK_SCREENS = new Set([
    'goal',
    'quicksetup',
    'plan',
    'stats',
    'data',
    'distractions',
    'confirm',
    'filepicker',
    'score',
    'premium',
    'chat'
  ]);

  const GESTURE_CONFIG = Object.freeze({
    edgePx: 28,
    lockPx: 10,
    dominance: 1.2,
    backCompleteRatio: 0.32,
    rootCompleteRatio: 0.3,
    completeVelocity: 0.5,
    minFlingRatio: 0.06,
    velocityWindowMs: 100,
    staleReleaseMs: 90,
    underlayOffsetPct: -6,
    rootPeekPct: 16,
    edgeResistance: 0.2,
    edgeResistMaxPx: 44
  });

  function resolveCurrentSwipeScreen() {
    if (typeof root.getActiveScreenV17 === 'function') {
      const s = root.getActiveScreenV17();
      if (s) return s;
    }
    if (root.__navigationStateV17 && root.__navigationStateV17.currentScreen) {
      return root.__navigationStateV17.currentScreen;
    }
    if (typeof document !== 'undefined') {
      const vis =
        document.querySelector('.app-screen:not(.hidden):not(.fm-motion-leave)') ||
        document.querySelector('.app-screen:not(.hidden)');
      if (vis && vis.id) return vis.id.replace('app-screen-', '');
    }
    return '';
  }

  function isGestureBlockingOverlayOpen() {
    if (typeof document === 'undefined') return false;
    if (typeof root.isCriticalModalOrSheetOpenV17 === 'function' && root.isCriticalModalOrSheetOpenV17()) {
      return true;
    }
    const isShown = function (id) {
      const el = document.getElementById(id);
      return Boolean(el && !el.classList.contains('hidden'));
    };
    if (isShown('focusChoiceOverlay') || isShown('missionCalendarSheet') || isShown('sessionConfirmModal')) {
      return true;
    }
    const missionSheet = document.getElementById('missionSheetV116');
    if (missionSheet && missionSheet.classList.contains('open')) return true;
    const installHelp = document.getElementById('pwaInstallHelp');
    if (installHelp && installHelp.classList.contains('open')) return true;
    return false;
  }

  /* One reusable ownership rule for horizontal controls: form controls, anything
     marked data-block-swipe-back="true", and any horizontally scrollable ancestor
     (carousels, selectors) keep their own horizontal gestures. */
  function ownsHorizontalGesture(targetEl) {
    if (!targetEl || typeof targetEl.closest !== 'function') return false;
    if (
      targetEl.closest(
        'input, textarea, select, [contenteditable="true"], [data-block-swipe-back="true"]'
      )
    ) {
      return true;
    }
    for (let el = targetEl; el && el.nodeType === 1; el = el.parentElement) {
      if (el.id === 'appBody' || el.classList.contains('app-screen')) break;
      if (el.scrollWidth > el.clientWidth + 2) {
        const overflowX = root.getComputedStyle(el).overflowX;
        if (overflowX === 'auto' || overflowX === 'scroll') return true;
      }
    }
    return false;
  }

  function hasActiveTextSelection() {
    try {
      const sel = root.getSelection && root.getSelection();
      return Boolean(sel && !sel.isCollapsed && String(sel).trim());
    } catch (_) {
      return false;
    }
  }

  function isFocusSessionRunning() {
    return Boolean(root.activeSession && root.activeSession.status === 'running');
  }

  function canSwipeBackFromCurrentScreen(targetEl) {
    if (typeof document === 'undefined') return false;
    if (stateMachine.isLocked() || isFocusSessionRunning()) return false;
    if (!SWIPEABLE_STACK_SCREENS.has(resolveCurrentSwipeScreen())) return false;
    if (isGestureBlockingOverlayOpen()) return false;
    if (ownsHorizontalGesture(targetEl) || hasActiveTextSelection()) return false;
    return true;
  }

  function canRootSwipeFromCurrentScreen(targetEl) {
    if (typeof document === 'undefined') return false;
    if (stateMachine.isLocked() || isFocusSessionRunning()) return false;
    if (getRootTabIndex(resolveCurrentSwipeScreen()) === -1) return false;
    const wrapper = document.getElementById('screenWrapper');
    if (wrapper && !wrapper.classList.contains('has-bottom-nav')) return false;
    if (isGestureBlockingOverlayOpen()) return false;
    if (ownsHorizontalGesture(targetEl) || hasActiveTextSelection()) return false;
    return true;
  }

  function prepareRootGestureTarget(section) {
    try {
      if (section === 'missions') {
        if (typeof root.ensureMissionManagementScreenV116 === 'function') root.ensureMissionManagementScreenV116();
        if (typeof root.renderMissionManagementV116 === 'function') root.renderMissionManagementV116();
      } else if (section === 'history') {
        if (typeof root.renderSessionHistory === 'function') root.renderSessionHistory();
      } else if (section === 'home') {
        if (typeof root.refreshHomeStats === 'function') root.refreshHomeStats();
      }
    } catch (_) {}
  }

  function installNavigationGestures() {
    if (navigationGesturesInstalled || typeof document === 'undefined') return;
    const appBody = document.getElementById('appBody');
    if (!appBody) return;
    navigationGesturesInstalled = true;

    const C = GESTURE_CONFIG;
    let gesture = null;
    let settling = null;
    let rafId = null;
    let suppressClickUntil = 0;

    function cancelFrame() {
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
    }

    function displacement(g) {
      const d = g.x - g.originX;
      if (g.mode === 'back') return Math.max(0, Math.min(g.width, d));
      if (g.resist) {
        const pull = Math.min(C.edgeResistMaxPx, Math.abs(d) * C.edgeResistance);
        return d < 0 ? -pull : pull;
      }
      return g.dir > 0 ? Math.max(-g.width, Math.min(0, d)) : Math.max(0, Math.min(g.width, d));
    }

    function underlayFrame(g, progress) {
      if (g.mode === 'back') {
        return {
          transform: 'translate3d(' + (C.underlayOffsetPct * (1 - progress)).toFixed(2) + '%, 0, 0)',
          opacity: (0.92 + 0.08 * progress).toFixed(3)
        };
      }
      return {
        transform: 'translate3d(' + (g.dir * C.rootPeekPct * (1 - progress)).toFixed(2) + '%, 0, 0)',
        opacity: (0.5 + 0.5 * progress).toFixed(3)
      };
    }

    function applyFrame() {
      rafId = null;
      const g = gesture;
      if (!g || !g.locked) return;
      if (resolveCurrentSwipeScreen() !== g.currentScreen) {
        abortGesture();
        return;
      }
      const d = displacement(g);
      g.fromEl.style.transform = 'translate3d(' + d.toFixed(1) + 'px, 0, 0)';
      if (g.toEl) {
        const frame = underlayFrame(g, Math.abs(d) / g.width);
        g.toEl.style.transform = frame.transform;
        g.toEl.style.opacity = frame.opacity;
      }
      if (g.dockTracked) setDockTrackingProgress(Math.abs(d) / g.width);
    }

    function releaseCapture(g) {
      if (g && g.captureEl && g.pointerId !== null) {
        try {
          if (g.captureEl.hasPointerCapture(g.pointerId)) g.captureEl.releasePointerCapture(g.pointerId);
        } catch (_) {}
      }
    }

    function syncDockToRoute() {
      const screen = resolveCurrentSwipeScreen();
      if (getRootTabIndex(screen) !== -1) syncDockIndicator(screen, { durationMs: 200 });
    }

    function cleanupGestureDOM(g) {
      cancelFrame();
      if (!g) return;
      releaseCapture(g);
      if (g.dockTracked) {
        g.dockTracked = false;
        endDockTracking(false, 0);
      }
      appBody.classList.remove(
        'fm-dual-transition',
        'fm-swipe-back-active',
        'fm-root-swipe-active',
        'fm-root-swipe-backward'
      );
      if (g.fromEl) {
        cancelElementAnimations(g.fromEl);
        g.fromEl.classList.remove('fm-layer-outgoing', 'fm-layer-top', 'fm-motion-active');
        clearInlineMotionStyles(g.fromEl);
      }
      if (g.toEl) {
        cancelElementAnimations(g.toEl);
        g.toEl.classList.remove('fm-layer-incoming', 'fm-swipe-back-underlay', 'fm-motion-active');
        clearInlineMotionStyles(g.toEl);
      }
    }

    function velocityOf(g) {
      const now = performance.now();
      const samples = g.samples;
      if (samples.length < 2) return 0;
      const last = samples[samples.length - 1];
      if (now - last.t > C.staleReleaseMs) return 0;
      let first = samples[0];
      for (let i = samples.length - 2; i >= 0; i--) {
        first = samples[i];
        if (last.t - samples[i].t >= C.velocityWindowMs) break;
      }
      const dt = last.t - first.t;
      return dt >= 8 ? (last.x - first.x) / dt : 0;
    }

    function beginGesture(clientX, clientY, targetEl, input, pointerId) {
      if (gesture || settling || stateMachine.isLocked()) return false;
      const wrapper = document.getElementById('screenWrapper') || appBody;
      const rect = wrapper.getBoundingClientRect();
      const relX = clientX - rect.left;
      const relY = clientY - rect.top;
      if (relY < 0 || relY > rect.height || relX < -4 || relX > rect.width + 4) return false;

      const currentScreen = resolveCurrentSwipeScreen();
      const fromEl = document.getElementById('app-screen-' + currentScreen);
      if (!fromEl) return false;

      let mode = null;
      let targetScreen = null;
      let toEl = null;
      if (SWIPEABLE_STACK_SCREENS.has(currentScreen)) {
        if (relX > C.edgePx || !canSwipeBackFromCurrentScreen(targetEl)) return false;
        targetScreen =
          typeof root.getBackTargetScreen === 'function' ? root.getBackTargetScreen(currentScreen) : 'home';
        if (!targetScreen || targetScreen === currentScreen) return false;
        if (targetScreen === 'missions' && typeof root.ensureMissionManagementScreenV116 === 'function') {
          root.ensureMissionManagementScreenV116();
        }
        toEl = document.getElementById('app-screen-' + targetScreen);
        if (!toEl) return false;
        mode = 'back';
      } else if (getRootTabIndex(currentScreen) !== -1) {
        if (!canRootSwipeFromCurrentScreen(targetEl)) return false;
        mode = 'root';
      } else {
        return false;
      }

      gesture = {
        mode: mode,
        input: input,
        pointerId: pointerId !== undefined ? pointerId : null,
        captureEl: null,
        startX: clientX,
        startY: clientY,
        originX: clientX,
        x: clientX,
        samples: [{ x: clientX, t: performance.now() }],
        width: Math.max(280, rect.width || 390),
        locked: false,
        resist: false,
        dir: 0,
        currentScreen: currentScreen,
        targetScreen: targetScreen,
        fromEl: fromEl,
        toEl: toEl,
        dockTracked: false
      };
      return true;
    }

    function lockGesture(g, dx) {
      if (g.mode === 'root') {
        g.dir = dx < 0 ? 1 : -1;
        const targetIdx = getRootTabIndex(g.currentScreen) + g.dir;
        if (targetIdx >= 0 && targetIdx < ROOT_TAB_ORDER.length) {
          g.targetScreen = ROOT_TAB_ORDER[targetIdx];
          if (g.targetScreen === 'missions' && typeof root.ensureMissionManagementScreenV116 === 'function') {
            root.ensureMissionManagementScreenV116();
          }
          g.toEl = document.getElementById('app-screen-' + g.targetScreen);
        }
        g.resist = !g.toEl;
      }
      g.locked = true;
      // Follow the finger from the lock point on: only the lock slop is absorbed.
      g.originX = g.startX + (dx < 0 ? -C.lockPx : C.lockPx);
      appBody.classList.add('fm-dual-transition', g.mode === 'back' ? 'fm-swipe-back-active' : 'fm-root-swipe-active');
      if (g.mode === 'root' && g.dir < 0) appBody.classList.add('fm-root-swipe-backward');
      g.fromEl.classList.add('fm-layer-outgoing', 'fm-layer-top', 'fm-motion-active');
      if (g.toEl) {
        g.toEl.classList.add('fm-layer-incoming', 'fm-swipe-back-underlay', 'fm-motion-active');
        // Render the destination once, while it is laid out underneath; never during drag.
        if (g.mode === 'root') {
          prepareRootGestureTarget(g.targetScreen);
          g.dockTracked = beginDockTracking(g.currentScreen, g.targetScreen);
        }
        if (typeof root.restoreScreenScrollPosition === 'function') {
          try {
            root.restoreScreenScrollPosition(g.targetScreen);
          } catch (_) {}
        }
      }
      if (g.input === 'pointer' && g.pointerId !== null) {
        try {
          appBody.setPointerCapture(g.pointerId);
          g.captureEl = appBody;
        } catch (_) {}
      }
      applyFrame();
    }

    function updateGesture(clientX, clientY, ev) {
      const g = gesture;
      if (!g) return;
      g.x = clientX;
      const now = performance.now();
      g.samples.push({ x: clientX, t: now });
      while (g.samples.length > 2 && now - g.samples[0].t > C.velocityWindowMs * 2) g.samples.shift();

      if (!g.locked) {
        const dx = clientX - g.startX;
        const dy = clientY - g.startY;
        const adx = Math.abs(dx);
        const ady = Math.abs(dy);
        if (Math.max(adx, ady) < C.lockPx) return;
        if (adx < ady * C.dominance || (g.mode === 'back' && dx <= 0)) {
          gesture = null;
          return;
        }
        lockGesture(g, dx);
      }

      if (ev && ev.cancelable) ev.preventDefault();
      if (rafId === null) rafId = requestAnimationFrame(applyFrame);
    }

    function commitRoute(g) {
      if (g.mode === 'back') {
        if (typeof root.navigateBack === 'function') root.navigateBack({ skipMotion: true });
        return;
      }
      if (typeof root.navigateToRoot === 'function') {
        root.navigateToRoot(g.targetScreen, { skipMotion: true });
        if (typeof root.restoreScreenScrollPosition === 'function') {
          try {
            root.restoreScreenScrollPosition(g.targetScreen);
          } catch (_) {}
        }
      }
    }

    function finishGesture(cancelled) {
      const g = gesture;
      gesture = null;
      if (!g) return;
      if (!g.locked) {
        cleanupGestureDOM(g);
        return;
      }
      suppressClickUntil = performance.now() + 350;
      cancelFrame();

      const d = displacement(g);
      const progress = Math.abs(d) / g.width;
      const velocity = velocityOf(g);
      let shouldComplete = false;
      if (!cancelled && !g.resist && g.toEl) {
        if (g.mode === 'back') {
          shouldComplete =
            progress >= C.backCompleteRatio ||
            (velocity >= C.completeVelocity && progress >= C.minFlingRatio);
        } else {
          shouldComplete =
            progress >= C.rootCompleteRatio ||
            (-g.dir * velocity >= C.completeVelocity && progress >= C.minFlingRatio);
        }
      }

      if (prefersReducedMotion() || typeof g.fromEl.animate !== 'function') {
        if (g.dockTracked) {
          g.dockTracked = false;
          endDockTracking(shouldComplete, 120);
        }
        cleanupGestureDOM(g);
        if (shouldComplete) commitRoute(g);
        syncDockToRoute();
        return;
      }

      const exitX = g.mode === 'back' ? g.width : -g.dir * g.width;
      const remaining = shouldComplete ? Math.abs(exitX - d) : Math.abs(d);
      const fraction = Math.min(1, remaining / g.width);
      const speed = Math.abs(velocity);
      let durationMs = shouldComplete ? 130 + 210 * fraction : 150 + 170 * fraction;
      if (shouldComplete && speed > 0.3) {
        durationMs = Math.min(durationMs, Math.max(110, (remaining / speed) * 1.7));
      }
      durationMs = Math.round(Math.max(110, Math.min(340, durationMs)));
      const easing = 'cubic-bezier(0.2, 0.75, 0.25, 1)';
      if (g.dockTracked) {
        g.dockTracked = false;
        endDockTracking(shouldComplete, durationMs, easing);
      }

      let finalized = false;
      const finalize = function (commit) {
        if (finalized) return;
        finalized = true;
        if (settling === finalize) settling = null;
        if (activeScreenCleanup === abortSettle) activeScreenCleanup = null;
        cleanupGestureDOM(g);
        stateMachine.releaseLock();
        if (commit) commitRoute(g);
        // An interrupted settle never leaves the dock on a route that was not committed.
        syncDockToRoute();
      };
      const abortSettle = function () {
        finalize(false);
      };
      settling = finalize;
      activeScreenCleanup = abortSettle;
      stateMachine.acquireLock(g.mode === 'back' ? 'swipe-back' : 'root-swipe', durationMs + 160, function () {
        finalize(shouldComplete);
      });
      stateMachine.setState('animating', g.mode === 'back' ? 'swipe-back' : 'root-swipe');

      const fromAnim = g.fromEl.animate(
        [
          { transform: 'translate3d(' + d.toFixed(1) + 'px, 0, 0)' },
          { transform: 'translate3d(' + (shouldComplete ? exitX : 0) + 'px, 0, 0)' }
        ],
        { duration: durationMs, easing: easing, fill: 'forwards' }
      );
      const anims = [fromAnim.finished];
      if (g.toEl) {
        const startFrame = underlayFrame(g, progress);
        const endFrame = underlayFrame(g, shouldComplete ? 1 : 0);
        anims.push(
          g.toEl.animate(
            [
              { transform: startFrame.transform, opacity: Number(startFrame.opacity) },
              { transform: endFrame.transform, opacity: Number(endFrame.opacity) }
            ],
            { duration: durationMs, easing: easing, fill: 'forwards' }
          ).finished
        );
      }
      Promise.all(anims)
        .then(function () {
          finalize(shouldComplete);
        })
        .catch(function () {
          finalize(false);
        });
    }

    function abortGesture() {
      const g = gesture;
      gesture = null;
      if (g) cleanupGestureDOM(g);
      if (settling) settling(false);
    }

    function onTouchStart(ev) {
      if (!ev.touches) return;
      if (ev.touches.length !== 1) {
        if (gesture) finishGesture(true);
        return;
      }
      const touch = ev.touches[0];
      beginGesture(touch.clientX, touch.clientY, ev.target, 'touch');
    }

    function onTouchMove(ev) {
      if (!gesture || gesture.input !== 'touch' || !ev.touches || ev.touches.length !== 1) return;
      const touch = ev.touches[0];
      updateGesture(touch.clientX, touch.clientY, ev);
    }

    function onTouchEnd(ev) {
      if (!gesture || gesture.input !== 'touch') return;
      if (ev.touches && ev.touches.length > 0) return;
      finishGesture(false);
    }

    function onTouchCancel() {
      if (!gesture || gesture.input !== 'touch') return;
      finishGesture(true);
    }

    // Touch input is handled exclusively through touch events (cancelable touchmove);
    // pointer events only drive mouse and pen so one physical gesture never has two owners.
    function isTouchPointer(ev) {
      return ev.pointerType === 'touch';
    }

    function onPointerDown(ev) {
      if (gesture || isTouchPointer(ev)) return;
      if (ev.button !== undefined && ev.button !== 0) return;
      beginGesture(ev.clientX, ev.clientY, ev.target, 'pointer', ev.pointerId);
    }

    function onPointerMove(ev) {
      if (!gesture || gesture.input !== 'pointer' || gesture.pointerId !== ev.pointerId) return;
      updateGesture(ev.clientX, ev.clientY, ev);
    }

    function onPointerUp(ev) {
      if (!gesture || gesture.input !== 'pointer' || gesture.pointerId !== ev.pointerId) return;
      finishGesture(false);
    }

    function onPointerCancel(ev) {
      if (!gesture || gesture.input !== 'pointer' || gesture.pointerId !== ev.pointerId) return;
      finishGesture(true);
    }

    function onClickCapture(ev) {
      if (performance.now() < suppressClickUntil) {
        suppressClickUntil = 0;
        ev.preventDefault();
        ev.stopPropagation();
      }
    }

    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('touchend', onTouchEnd, { passive: true });
    window.addEventListener('touchcancel', onTouchCancel, { passive: true });

    window.addEventListener('pointerdown', onPointerDown, { passive: true });
    window.addEventListener('pointermove', onPointerMove, { passive: false });
    window.addEventListener('pointerup', onPointerUp, { passive: true });
    window.addEventListener('pointercancel', onPointerCancel, { passive: true });
    window.addEventListener('click', onClickCapture, true);

    window.addEventListener('resize', abortGesture, { passive: true });
    window.addEventListener('orientationchange', abortGesture, { passive: true });
    window.addEventListener('pagehide', abortGesture);
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'hidden') abortGesture();
    });
  }

  function installLifecycleSafetyHandlers() {
    if (typeof root.addEventListener !== 'function') return;
    root.addEventListener('pagehide', function () {
      forceCleanupScreenLayers();
      closePlusSheet({ immediate: true });
      closeMissionSheet({ immediate: true, skipFocusRestore: true });
    });
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
          forceCleanupScreenLayers();
        }
      });
    }
    root.addEventListener('error', function () {
      forceCleanupScreenLayers();
    });
  }

  const MotionController = {
    version: 'v17.8 Mission Plan & Focus Protection Polish',
    ROOT_TAB_ORDER,
    VALID_STATES,
    getRootTabIndex,
    computeRootTabMetrics,
    resolveTransitionIntent,
    createMotionStateMachine,
    getState: stateMachine.getState,
    getLockReason: stateMachine.getLockReason,
    isLocked: stateMachine.isLocked,
    prefersReducedMotion,
    animateScreenTransition,
    handleSameRootTabTap,
    syncDockIndicator,
    animateMissionPlanSwitch,
    openPlusSheet,
    closePlusSheet,
    openMissionSheet,
    closeMissionSheet,
    forceCleanupScreenLayers,
    dismissStartupSplash,
    canSwipeBackFromCurrentScreen,
    canRootSwipeFromCurrentScreen,
    init: function () {
      installGlobalTriggerTracking();
      installLifecycleSafetyHandlers();
      initStartupSplash();
      if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', function () {
            installFeatureMotionEnhancements();
            installNavigationGestures();
          });
        } else {
          installFeatureMotionEnhancements();
          installNavigationGestures();
        }
      }
    }
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = MotionController;
  }

  if (root) {
    root.MotionController = MotionController;
    if (root.FocusModeApp && root.FocusModeApp.core) {
      root.FocusModeApp.core.motionController = MotionController;
    }
    if (typeof document !== 'undefined') {
      MotionController.init();
    }
  }
})(typeof window !== 'undefined' ? window : globalThis);
