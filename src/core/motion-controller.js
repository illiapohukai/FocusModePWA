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
        durationMs: 0,
        intent: 'same-tab'
      };
    }
    const delta = toIdx - fromIdx;
    const direction = delta > 0 ? 1 : -1;
    const distance = Math.abs(delta);
    let travelPx = 24;
    let durationMs = 260;
    if (distance === 2) {
      travelPx = 44;
      durationMs = 340;
    } else if (distance >= 3) {
      travelPx = 64;
      durationMs = 420;
    }
    return {
      fromIndex: fromIdx,
      toIndex: toIdx,
      direction,
      distance,
      travelPx,
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
    if (ctx.type === 'root') {
      const fromRoot = ctx.fromRoot || fromScreen;
      const toRoot = ctx.toRoot || toScreen;
      const metrics = computeRootTabMetrics(fromRoot, toRoot);
      if (metrics.intent !== 'same-tab') return metrics.intent;
      if (fromScreen !== toScreen) return 'pop';
    }
    if (ctx.type === 'pop' || ctx.direction === 'back') {
      return 'pop';
    }
    if (ctx.type === 'push' || ctx.direction === 'forward') {
      return 'push';
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
  let homeVisitedOnce = false;
  let historyVisitedOnce = false;
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
      appBody?.classList.remove('fm-dual-transition');
      document.querySelectorAll('.app-screen').forEach(function (scr) {
        cancelElementAnimations(scr);
        scr.classList.remove(
          'fm-layer-outgoing',
          'fm-layer-incoming',
          'fm-layer-top',
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

    let durationMs = reduced ? 100 : 260;
    let fromKeyframes = [
      { transform: 'translateX(0px)', opacity: 1 },
      { transform: 'translateX(-16px)', opacity: 0 }
    ];
    let toKeyframes = [
      { transform: 'translateX(16px)', opacity: 0.35 },
      { transform: 'translateX(0px)', opacity: 1 }
    ];
    let easingIn = 'cubic-bezier(0.16, 1, 0.3, 1)';
    let easingOut = 'cubic-bezier(0.4, 0, 1, 1)';
    let outgoingOnTop = false;

    if (intent === 'root-tab-right' || intent === 'root-tab-left') {
      const fromRoot = (context && context.fromRoot) || fromScreen;
      const toRoot = (context && context.toRoot) || toScreen;
      const metrics = computeRootTabMetrics(fromRoot, toRoot);
      const dir = metrics.direction || (intent === 'root-tab-right' ? 1 : -1);
      const px = reduced ? 8 : metrics.travelPx || 24;
      durationMs = reduced ? 100 : metrics.durationMs || 260;
      easingIn = 'cubic-bezier(0.22, 1, 0.36, 1)';
      easingOut = 'cubic-bezier(0.4, 0, 1, 1)';
      toKeyframes = [
        { transform: 'translate3d(' + dir * px + 'px, 0, 0)', opacity: 0.25 },
        { transform: 'translate3d(0px, 0, 0)', opacity: 1 }
      ];
      fromKeyframes = [
        { transform: 'translate3d(0px, 0, 0)', opacity: 1 },
        { transform: 'translate3d(' + -dir * px + 'px, 0, 0)', opacity: 0 }
      ];
      animateDockActiveTab(toRoot, reduced);
    } else if (intent === 'push' || intent === 'focus-exit-result') {
      durationMs = reduced ? 100 : 260;
      toKeyframes = [
        { transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(12%, 0, 0)', opacity: 0.45 },
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 }
      ];
      fromKeyframes = [
        { transform: 'translate3d(0%, 0, 0)', opacity: 1, filter: 'brightness(1)' },
        {
          transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(-4%, 0, 0)',
          opacity: 0.82,
          filter: 'brightness(0.92)'
        }
      ];
    } else if (intent === 'pop') {
      durationMs = reduced ? 100 : 250;
      outgoingOnTop = true;
      fromKeyframes = [
        { transform: 'translate3d(0%, 0, 0)', opacity: 1 },
        { transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(12%, 0, 0)', opacity: 0 }
      ];
      toKeyframes = [
        {
          transform: reduced ? 'translate3d(0, 0, 0)' : 'translate3d(-4%, 0, 0)',
          opacity: 0.84,
          filter: 'brightness(0.94)'
        },
        { transform: 'translate3d(0%, 0, 0)', opacity: 1, filter: 'brightness(1)' }
      ];
    }

    stateMachine.acquireLock(intent, durationMs + 180, forceCleanupScreenLayers);
    stateMachine.setState('animating', intent);

    appBody.classList.add('fm-dual-transition');
    fromEl.classList.add('fm-layer-outgoing', 'fm-motion-active');
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
      toEl.classList.remove('fm-layer-incoming', 'fm-motion-active');
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
        duration: intent.startsWith('root-tab') ? Math.round(durationMs * 0.88) : durationMs,
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
    const durationMs = reduced ? 120 : 620;
    stateMachine.acquireLock('focus-enter', durationMs + 220, forceCleanupScreenLayers);
    stateMachine.setState('animating', 'focus-enter');

    appBody.classList.add('fm-dual-transition');
    fromEl.classList.add('fm-layer-outgoing', 'fm-motion-active');
    toEl.classList.add('fm-layer-incoming', 'fm-motion-active');

    const planetEl = toEl.querySelector('.uc-planet');
    const protectBtn = toEl.querySelector('.uc-protect');
    const headerEl = toEl.querySelector('.uc-header');
    const readoutEl = toEl.querySelector('.uc-readout');
    const focusCardEl = toEl.querySelector('#ucFocusCard');
    const actionsEl = toEl.querySelector('.uc-actions');
    const rings = toEl.querySelectorAll('.uc-main-ring, .uc-far-orbit');

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
      const startSize = 44;
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
        'radial-gradient(circle at 38% 30%, rgba(196,181,253,0.88) 0%, rgba(124,58,237,0.75) 38%, rgba(30,27,75,0.96) 78%, rgba(5,7,20,1) 100%)';
      seedEl.style.boxShadow =
        '0 0 42px rgba(139,92,246,0.55), inset 0 0 24px rgba(192,132,252,0.35)';
      document.body.appendChild(seedEl);

      const dx = startCx - endCx;
      const dy = startCy - endCy;
      const scale0 = startSize / endSize;

      seedEl.animate(
        [
          {
            transform: 'translate3d(' + dx + 'px, ' + dy + 'px, 0) scale(' + scale0 + ')',
            opacity: 0.92
          },
          {
            transform: 'translate3d(0px, 0px, 0) scale(1.02)',
            opacity: 0.85,
            offset: 0.72
          },
          {
            transform: 'translate3d(0px, 0px, 0) scale(1)',
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
      appBody.classList.remove('fm-dual-transition');
      fromEl.classList.remove('fm-layer-outgoing', 'fm-motion-active');
      toEl.classList.remove('fm-layer-incoming', 'fm-motion-active');
      clearInlineMotionStyles(fromEl);
      clearInlineMotionStyles(toEl);
      if (protectBtn) {
        protectBtn.classList.remove('fm-protect-activating');
      }
      if (activeScreenCleanup === cleanup) {
        activeScreenCleanup = null;
      }
      stateMachine.releaseLock();
    }
    activeScreenCleanup = cleanup;

    if (typeof fromEl.animate === 'function' && typeof toEl.animate === 'function') {
      fromEl.animate(
        [
          { transform: 'scale(1)', opacity: 1 },
          { transform: reduced ? 'scale(1)' : 'scale(0.96)', opacity: 0 }
        ],
        {
          duration: Math.round(durationMs * 0.55),
          easing: 'cubic-bezier(0.4, 0, 1, 1)',
          fill: 'forwards'
        }
      );

      const inAnim = toEl.animate(
        [
          { opacity: 0 },
          { opacity: 1 }
        ],
        {
          duration: Math.round(durationMs * 0.65),
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );

      if (!reduced && planetEl && typeof planetEl.animate === 'function') {
        planetEl.animate(
          [
            { transform: 'translate(-50%, -50%) scale(0.42)', opacity: 0.2 },
            { transform: 'translate(-50%, -50%) scale(1.03)', opacity: 1, offset: 0.78 },
            { transform: 'translate(-50%, -50%) scale(1)', opacity: 1 }
          ],
          {
            duration: durationMs,
            easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
          }
        );
      }

      if (!reduced) {
        rings.forEach(function (ring) {
          if (typeof ring.animate === 'function') {
            ring.animate(
              [
                { transform: 'scale(0.78)', opacity: 0 },
                { transform: 'scale(1)', opacity: 1 }
              ],
              {
                duration: durationMs,
                easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
              }
            );
          }
        });

        [headerEl, readoutEl].forEach(function (el) {
          if (el && typeof el.animate === 'function') {
            el.animate(
              [
                { transform: 'translateY(10px)', opacity: 0 },
                { transform: 'translateY(0px)', opacity: 1 }
              ],
              {
                duration: 360,
                delay: 95,
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
                fill: 'backwards'
              }
            );
          }
        });

        [focusCardEl, actionsEl].forEach(function (el) {
          if (el && typeof el.animate === 'function') {
            el.animate(
              [
                { transform: 'translateY(14px)', opacity: 0 },
                { transform: 'translateY(0px)', opacity: 1 }
              ],
              {
                duration: 380,
                delay: 145,
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
                fill: 'backwards'
              }
            );
          }
        });

        if (protectBtn && typeof protectBtn.animate === 'function') {
          protectBtn.classList.add('fm-protect-activating');
          protectBtn.animate(
            [
              { transform: 'scale(0.92)', opacity: 0.35 },
              { transform: 'scale(1.04)', opacity: 1, offset: 0.7 },
              { transform: 'scale(1)', opacity: 1 }
            ],
            {
              duration: 460,
              delay: 130,
              easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)',
              fill: 'backwards'
            }
          );
        }
      }

      setTimeout(cleanup, durationMs + 30);
      inAnim.finished.catch(function () {});
    } else {
      cleanup();
    }
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
     BOTTOM NAVIGATION DOCK ACTIVE FEEDBACK & SAME-TAB BOUNCE
     ======================================================================== */
  function animateDockActiveTab(section, reduced) {
    if (typeof document === 'undefined' || reduced) return;
    const nav = document.getElementById('bottomNavigationV17');
    if (!nav) return;
    const btn = nav.querySelector('[data-nav-section="' + section + '"]');
    if (!btn) return;
    const icon = btn.querySelector('.fm-nav-icon');
    const indicator = btn.querySelector('.fm-nav-indicator');
    if (icon && typeof icon.animate === 'function') {
      cancelElementAnimations(icon);
      icon.animate(
        [
          { transform: 'scale(0.92)' },
          { transform: 'scale(1.08)', offset: 0.55 },
          { transform: 'scale(1.06)' }
        ],
        {
          duration: 240,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
        }
      );
    }
    if (indicator && typeof indicator.animate === 'function') {
      cancelElementAnimations(indicator);
      indicator.animate(
        [
          { transform: 'translateX(-50%) scaleX(0.4)', opacity: 0.4 },
          { transform: 'translateX(-50%) scaleX(1.12)', opacity: 1, offset: 0.65 },
          { transform: 'translateX(-50%) scaleX(1)', opacity: 1 }
        ],
        {
          duration: 260,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
        }
      );
    }
  }

  function handleSameRootTabTap(section) {
    if (typeof document === 'undefined') return;
    const reduced = prefersReducedMotion();
    const screenEl = document.getElementById('app-screen-' + section);
    const scrollContainer =
      (section === 'missions' && document.getElementById('missionManagementListV116')) ||
      (section === 'history' && document.getElementById('sessionHistoryList')) ||
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
          { transform: 'scale(0.88)', offset: 0.45 },
          { transform: 'scale(0.92)' }
        ],
        {
          duration: 220,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
        }
      );
    }

    if (backdrop && typeof backdrop.animate === 'function') {
      backdrop.animate(
        [{ opacity: 0 }, { opacity: 1 }],
        {
          duration: reduced ? 80 : 220,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
          fill: 'forwards'
        }
      );
    }

    if (sheet && typeof sheet.animate === 'function') {
      sheet.animate(
        [
          {
            transform: reduced ? 'translateY(0px) scale(1)' : 'translateY(28px) scale(0.91)',
            opacity: 0
          },
          {
            transform: 'translateY(0px) scale(1)',
            opacity: 1
          }
        ],
        {
          duration: reduced ? 90 : 300,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)',
          fill: 'forwards'
        }
      );

      if (!reduced) {
        const items = sheet.querySelectorAll(
          '.focus-mission-v116, .focus-new-v116, .focus-manage-v116, .space-y-2\\.5 > button'
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
                duration: 240,
                delay: Math.min(idx * 38, 160),
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
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
          duration: 185,
          easing: 'cubic-bezier(0.4, 0, 1, 1)',
          fill: 'forwards'
        }
      );
    }

    const sheetOut = sheet.animate(
      [
        { transform: 'translateY(0px) scale(1)', opacity: 1 },
        { transform: 'translateY(24px) scale(0.91)', opacity: 0 }
      ],
      {
        duration: 195,
        easing: 'cubic-bezier(0.4, 0, 1, 1)',
        fill: 'forwards'
      }
    );

    plusCloseTimer = setTimeout(finalizeClose, 220);
    sheetOut.finished.catch(function () {}).finally(finalizeClose);
  }

  /* ========================================================================
     MISSION THREE-DOT MENU ACTION SHEET (#missionSheetV116)
     ======================================================================== */
  function openMissionSheet(contentHtml) {
    if (typeof document === 'undefined') return;
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
    card.innerHTML = contentHtml;

    sheet.classList.add('open');
    clearInlineMotionStyles(sheet);
    clearInlineMotionStyles(card);

    if (typeof root.hideBottomNavigation === 'function') {
      try {
        root.hideBottomNavigation();
      } catch (_) {}
    }

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
          duration: reduced ? 80 : 180,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
        }
      );
    } else {
      sheet.animate(
        [{ opacity: 0 }, { opacity: 1 }],
        {
          duration: reduced ? 80 : 180,
          easing: 'cubic-bezier(0.16, 1, 0.3, 1)'
        }
      );
      card.animate(
        [
          {
            transform: reduced ? 'scale(1)' : 'scale(0.95) translateY(10px)',
            opacity: 0
          },
          {
            transform: 'scale(1) translateY(0px)',
            opacity: 1
          }
        ],
        {
          duration: reduced ? 80 : 230,
          easing: 'cubic-bezier(0.34, 1.16, 0.64, 1)'
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
                duration: 190,
                delay: Math.min(idx * 26, 130),
                easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
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
        easing: 'cubic-bezier(0.4, 0, 1, 1)',
        fill: 'forwards'
      }
    );
    const outAnim = card.animate(
      [
        { transform: 'scale(1) translateY(0px)', opacity: 1 },
        { transform: 'scale(0.95) translateY(8px)', opacity: 0 }
      ],
      {
        duration: 170,
        easing: 'cubic-bezier(0.4, 0, 1, 1)',
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
    } else if (screenName === 'history' && !historyVisitedOnce) {
      historyVisitedOnce = true;
      const rows = document.querySelectorAll('#sessionHistoryList > *');
      rows.forEach(function (row, idx) {
        if (idx < 6 && typeof row.animate === 'function') {
          row.animate(
            [
              { transform: 'translateY(8px)', opacity: 0 },
              { transform: 'translateY(0px)', opacity: 1 }
            ],
            {
              duration: 220,
              delay: Math.min(idx * 28, 140),
              easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
              fill: 'backwards'
            }
          );
        }
      });
    } else if (screenName === 'home' && !homeVisitedOnce) {
      homeVisitedOnce = true;
      const items = document.querySelectorAll('#homeActivityList > .home-activity-row');
      items.forEach(function (item, idx) {
        if (idx < 5 && typeof item.animate === 'function') {
          item.animate(
            [
              { transform: 'translateY(6px)', opacity: 0 },
              { transform: 'translateY(0px)', opacity: 1 }
            ],
            {
              duration: 210,
              delay: Math.min(idx * 28, 120),
              easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
              fill: 'backwards'
            }
          );
        }
      });
    }
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
    version: 'v17.3.2 Fluid Motion System',
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
    openPlusSheet,
    closePlusSheet,
    openMissionSheet,
    closeMissionSheet,
    forceCleanupScreenLayers,
    dismissStartupSplash,
    init: function () {
      installGlobalTriggerTracking();
      installLifecycleSafetyHandlers();
      initStartupSplash();
      if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', installFeatureMotionEnhancements);
        } else {
          installFeatureMotionEnhancements();
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
