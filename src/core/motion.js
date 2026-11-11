/**
 * Physical module: src/core/motion.js
 * Version: v17.3.1 Motion System
 *
 * Coordinates startup splash lifecycle, directional screen transitions,
 * Focus Session radial launch & staged reveal, bottom sheets/menus,
 * carousel card emphasis, list crossfades, and reduced-motion accessibility
 * without altering business logic, storage keys, or recovery authority.
 */

(function installMotionSystemV1731(global) {
  'use strict';

  const ROOT_SCREENS = new Set(['home', 'missions', 'history', 'settings']);
  const OVERLAY_SCREENS = new Set(['distractions', 'filepicker', 'confirm', 'update']);
  const SCREEN_DEPTH = {
    'home': 0,
    'missions': 0,
    'history': 0,
    'settings': 0,
    'session-recovery': 1,
    'quicksetup': 1,
    'goal': 1,
    'plan': 1,
    'stats': 1,
    'score': 1,
    'data': 1,
    'premium': 1,
    'chat': 1,
    'distractions': 2,
    'filepicker': 2,
    'analyzing': 2,
    'confirm': 2,
    'timer': 3,
    'result': 3,
    'feedbackprocessing': 4,
    'update': 4
  };

  const state = {
    version: '17.3.1',
    splashVisible: true,
    splashDismissing: false,
    splashMountedAt: Date.now(),
    initialHomeActivityAnimated: false,
    lastKnownTopActivityKey: null,
    pendingDirectionHint: null,
    activeOutgoingEl: null,
    activeIncomingEl: null,
    transitionCleanupTimer: null,
    transitionFailSafeTimer: null,
    timerStageCleanupTimer: null,
    portalCleanupTimer: null,
    lastTransitionTarget: null,
    lastTransitionAt: 0,
    lastStartActionOrigin: null,
    lastMissionMenuTrigger: null,
    missionSheetCloseTimer: null,
    focusChoiceCloseTimer: null,
    ucTaskCloseTimer: null
  };

  function prefersReducedMotion() {
    try {
      return Boolean(global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch {
      return false;
    }
  }

  function getActiveScreenElement() {
    return document.querySelector('#appBody > .app-screen:not(.hidden):not(.fm-screen-outgoing)');
  }

  function getActiveScreenName() {
    const el = getActiveScreenElement();
    if (el && el.id && el.id.startsWith('app-screen-')) {
      return el.id.replace('app-screen-', '');
    }
    return typeof global.currentAppScreen === 'string' ? global.currentAppScreen : 'home';
  }

  /* ==========================================================================
     1. STARTUP SPLASH CONTROLLER
     ========================================================================== */

  function hasPendingRecoveryCandidate() {
    try {
      if (global.sessionRecoveryCandidate) return true;
      const sessions = global.FocusModeApp?.repositories?.sessions;
      const active = sessions?.loadActive?.();
      if (active && (active.status === 'running' || active.lifecycleState === 'running' || active.lifecycleState === 'interrupted' || active.lifecycleState === 'awaiting_review')) {
        return true;
      }
    } catch {}
    return false;
  }

  function triggerInitialHomeActivityMotion() {
    if (state.initialHomeActivityAnimated) return;
    const activeScreen = getActiveScreenName();
    if (activeScreen !== 'home') return;
    state.initialHomeActivityAnimated = true;
    const list = document.getElementById('homeActivityList');
    if (!list || prefersReducedMotion()) return;
    list.classList.add('fm-activity-initial-enter');
    setTimeout(() => {
      list.classList.remove('fm-activity-initial-enter');
    }, 520);
  }

  function dismissStartupSplash(forceImmediate = false) {
    const splash = document.getElementById('fmStartupSplash');
    if (!splash) {
      state.splashVisible = false;
      return;
    }
    if (!state.splashVisible && ! state.splashDismissing) return;

    if (forceImmediate || prefersReducedMotion()) {
      state.splashVisible = false;
      state.splashDismissing = false;
      splash.classList.remove('fm-splash-exiting');
      splash.classList.add('fm-splash-hidden');
      splash.setAttribute('aria-hidden', 'true');
      triggerInitialHomeActivityMotion();
      return;
    }

    if (state.splashDismissing) return;
    state.splashDismissing = true;
    state.splashVisible = false;
    splash.classList.add('fm-splash-exiting');
    splash.setAttribute('aria-hidden', 'true');

    setTimeout(() => {
      splash.classList.add('fm-splash-hidden');
      splash.classList.remove('fm-splash-exiting');
      state.splashDismissing = false;
      triggerInitialHomeActivityMotion();
    }, 330);
  }

  function scheduleSplashExitWhenReady() {
    const minVisualMs = prefersReducedMotion() ? 140 : 720;
    const maxFailSafeMs = 1380;

    // Hard fail-safe so splash can never remain stuck
    setTimeout(() => {
      if (state.splashVisible || state.splashDismissing) {
        dismissStartupSplash(true);
      }
    }, maxFailSafeMs);

    const checkReady = () => {
      if (!state.splashVisible) return;
      const elapsed = Date.now() - state.splashMountedAt;
      const pendingRecovery = hasPendingRecoveryCandidate();
      const activeScreen = getActiveScreenName();

      // If recovery candidate exists but screen hasn't switched off 'home' yet,
      // allow resolveInitialAppStateV112 (runs at 120ms/180ms/220ms) to finish first.
      if (pendingRecovery && activeScreen === 'home' && elapsed < 420) {
        try {
          global.resolveInitialAppStateV112?.('splash_guard');
        } catch {}
        setTimeout(checkReady, 60);
        return;
      }

      const remaining = Math.max(0, minVisualMs - elapsed);
      setTimeout(() => {
        if (hasPendingRecoveryCandidate() && getActiveScreenName() === 'home') {
          try {
            global.resolveInitialAppStateV112?.('splash_final_check');
          } catch {}
        }
        dismissStartupSplash(false);
      }, remaining);
    };

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => setTimeout(checkReady, 240), { once: true });
    } else {
      setTimeout(checkReady, 240);
    }
  }

  global.addEventListener('error', () => {
    setTimeout(() => dismissStartupSplash(true), 120);
  });
  global.addEventListener('unhandledrejection', () => {
    setTimeout(() => dismissStartupSplash(true), 120);
  });

  /* ==========================================================================
     2. FOCUS SESSION PORTAL & STAGED TIMER REVEAL
     ========================================================================== */

  function recordPointerActionOrigin(event) {
    const btn = event.target?.closest?.(
      '#homeMissionStartStepBtn, .mh-start-btn, .mission-active-start, #app-screen-quicksetup .qb-bottom button, #app-screen-session-recovery .recovery-primary, .mmc-action-btn.primary'
    );
    if (!btn) return;
    try {
      const wrapper = document.getElementById('screenWrapper') || document.body;
      const wrapRect = wrapper.getBoundingClientRect();
      const btnRect = btn.getBoundingClientRect();
      const cx = Math.round(btnRect.left - wrapRect.left + btnRect.width / 2);
      const cy = Math.round(btnRect.top - wrapRect.top + btnRect.height / 2);
      state.lastStartActionOrigin = {
        x: `${cx}px`,
        y: `${cy}px`,
        ts: Date.now()
      };
    } catch {}
  }

  document.addEventListener('pointerdown', recordPointerActionOrigin, { capture: true, passive: true });
  document.addEventListener('click', recordPointerActionOrigin, { capture: true, passive: true });

  function triggerFocusLaunchPortal() {
    if (prefersReducedMotion()) return;
    const portal = document.getElementById('fmFocusLaunchPortal');
    if (!portal) return;

    const origin = state.lastStartActionOrigin && (Date.now() - state.lastStartActionOrigin.ts < 1800)
      ? state.lastStartActionOrigin
      : { x: '50%', y: '78%' };

    portal.style.setProperty('--fm-portal-x', origin.x);
    portal.style.setProperty('--fm-portal-y', origin.y);
    portal.classList.remove('fm-portal-active');
    void portal.offsetWidth;
    portal.classList.add('fm-portal-active');

    if (state.portalCleanupTimer) clearTimeout(state.portalCleanupTimer);
    state.portalCleanupTimer = setTimeout(() => {
      portal.classList.remove('fm-portal-active');
      state.portalCleanupTimer = null;
    }, 540);
  }

  function triggerTimerStagedReveal() {
    const timerScreen = document.getElementById('app-screen-timer');
    if (!timerScreen || prefersReducedMotion()) return;
    timerScreen.classList.remove('fm-timer-staged-enter');
    void timerScreen.offsetWidth;
    timerScreen.classList.add('fm-timer-staged-enter');

    if (state.timerStageCleanupTimer) clearTimeout(state.timerStageCleanupTimer);
    state.timerStageCleanupTimer = setTimeout(() => {
      timerScreen.classList.remove('fm-timer-staged-enter');
      state.timerStageCleanupTimer = null;
    }, 660);
  }

  /* ==========================================================================
     3. GLOBAL SCREEN TRANSITION COORDINATOR
     ========================================================================== */

  const ALL_DIRECTION_CLASSES = [
    'fm-dir-forward',
    'fm-dir-back',
    'fm-dir-root',
    'fm-dir-overlay',
    'fm-dir-session-start',
    'fm-dir-session-exit'
  ];

  function cleanupScreenTransition() {
    if (state.transitionCleanupTimer) {
      clearTimeout(state.transitionCleanupTimer);
      state.transitionCleanupTimer = null;
    }
    if (state.transitionFailSafeTimer) {
      clearTimeout(state.transitionFailSafeTimer);
      state.transitionFailSafeTimer = null;
    }

    const appBody = document.getElementById('appBody');
    if (appBody) {
      appBody.classList.remove('fm-body-transitioning');
    }

    document.querySelectorAll('.app-screen.fm-screen-outgoing, .app-screen.fm-screen-incoming').forEach(el => {
      el.classList.remove('fm-screen-outgoing', 'fm-screen-incoming', ...ALL_DIRECTION_CLASSES);
      el.style.removeProperty('--fm-outgoing-display');
      el.style.removeProperty('--fm-outgoing-top');
      if (el.classList.contains('hidden')) {
        el.setAttribute('aria-hidden', 'true');
      } else {
        el.removeAttribute('aria-hidden');
      }
    });

    state.activeOutgoingEl = null;
    state.activeIncomingEl = null;
  }

  function classifyTransitionDirection(fromScreen, toScreen) {
    const explicitHint = state.pendingDirectionHint;
    state.pendingDirectionHint = null;

    if (toScreen === 'timer') return 'session-start';
    if (fromScreen === 'timer') return 'session-exit';

    if (explicitHint) return explicitHint;

    if (ROOT_SCREENS.has(fromScreen) && ROOT_SCREENS.has(toScreen)) {
      return 'root';
    }

    if (OVERLAY_SCREENS.has(toScreen) || OVERLAY_SCREENS.has(fromScreen)) {
      const fromDepth = SCREEN_DEPTH[fromScreen] ?? 1;
      const toDepth = SCREEN_DEPTH[toScreen] ?? 1;
      return toDepth >= fromDepth ? 'overlay' : 'back';
    }

    if (ROOT_SCREENS.has(toScreen) && !ROOT_SCREENS.has(fromScreen)) {
      return 'back';
    }

    const fromDepth = SCREEN_DEPTH[fromScreen] ?? 1;
    const toDepth = SCREEN_DEPTH[toScreen] ?? 1;
    if (toDepth < fromDepth) return 'back';
    return 'forward';
  }

  function getDurationForDirection(dir) {
    if (prefersReducedMotion()) return 95;
    switch (dir) {
      case 'root': return 205;
      case 'back': return 245;
      case 'overlay': return 235;
      case 'session-start': return 430;
      case 'session-exit': return 305;
      default: return 255;
    }
  }

  function closeTransientSheetsOnNavigation(nextScreen) {
    // Ensure Plus action sheet & mission action sheet never remain stuck on screen transition
    const choiceOverlay = document.getElementById('focusChoiceOverlay');
    if (choiceOverlay && !choiceOverlay.classList.contains('hidden')) {
      choiceOverlay.classList.remove('fm-sheet-closing', 'focus-choice-enter', 'focus-choice-leave');
      choiceOverlay.classList.add('hidden');
      document.getElementById('screenWrapper')?.classList.remove('focus-choice-open');
      document.body?.classList.remove('fm-choice-sheet-open');
    }

    const missionSheet = document.getElementById('missionSheetV116');
    if (missionSheet && (missionSheet.classList.contains('open') || missionSheet.classList.contains('fm-sheet-closing'))) {
      missionSheet.classList.remove('open', 'fm-sheet-closing');
    }

    if (nextScreen !== 'timer') {
      const ucPopup = document.getElementById('ucTaskPopup');
      if (ucPopup) ucPopup.classList.remove('show', 'fm-sheet-closing');
    }
  }

  function installScreenTransitionHook() {
    const previousApplyScreen = global.applyAppScreenImmediately;
    if (typeof previousApplyScreen !== 'function') return;

    global.applyAppScreenImmediately = function applyAppScreenWithMotionV1731(requestedScreen) {
      const prevEl = getActiveScreenElement();
      const prevScreen = prevEl?.id?.replace('app-screen-', '') || null;
      let prevDisplay = 'flex';
      const appBody = document.getElementById('appBody');
      const prevScrollTop = appBody ? appBody.scrollTop : 0;

      if (prevEl) {
        try {
          const computed = global.getComputedStyle(prevEl).display;
          if (computed && computed !== 'none') prevDisplay = computed;
        } catch {}
      }

      // Clean up any in-flight visual transition before applying the new authoritative screen
      cleanupScreenTransition();

      // Run authoritative synchronous screen change immediately (preserves Recovery router & all state)
      const result = previousApplyScreen.apply(this, arguments);

      const nextEl = getActiveScreenElement();
      const nextScreen = nextEl?.id?.replace('app-screen-', '') || requestedScreen;
      global.currentAppScreen = nextScreen;

      closeTransientSheetsOnNavigation(nextScreen);

      // Reset scroll top when changing screens
      if (appBody && prevScreen && prevScreen !== nextScreen) {
        appBody.scrollTop = 0;
      }

      // Do not animate while startup splash is covering the viewport or if screen did not change
      if (state.splashVisible || !prevEl || !nextEl || prevEl === nextEl || prevScreen === nextScreen) {
        state.pendingDirectionHint = null;
        return result;
      }

      const direction = classifyTransitionDirection(prevScreen, nextScreen);
      const dirClass = `fm-dir-${direction}`;
      const durationMs = getDurationForDirection(direction);

      state.lastTransitionTarget = nextScreen;
      state.lastTransitionAt = Date.now();

      if (direction === 'session-start') {
        triggerFocusLaunchPortal();
        triggerTimerStagedReveal();
      }

      if (appBody) {
        appBody.classList.add('fm-body-transitioning');
      }

      prevEl.style.setProperty('--fm-outgoing-display', prevDisplay);
      prevEl.style.setProperty('--fm-outgoing-top', `${-prevScrollTop}px`);
      prevEl.setAttribute('aria-hidden', 'true');
      prevEl.classList.add('fm-screen-outgoing', dirClass);

      nextEl.removeAttribute('aria-hidden');
      nextEl.classList.add('fm-screen-incoming', dirClass);

      state.activeOutgoingEl = prevEl;
      state.activeIncomingEl = nextEl;

      state.transitionCleanupTimer = setTimeout(cleanupScreenTransition, durationMs);
      state.transitionFailSafeTimer = setTimeout(cleanupScreenTransition, durationMs + 140);

      return result;
    };

    // Direction hint wrappers around navigation helpers (authority remains 100% unchanged)
    if (typeof global.navigateToRoot === 'function') {
      const origNavigateToRoot = global.navigateToRoot;
      global.navigateToRoot = function navigateToRootMotionV1731(rootName, options = {}) {
        const current = getActiveScreenName();
        if (current === rootName && (Date.now() - state.lastTransitionAt < 180)) {
          return;
        }
        state.pendingDirectionHint = ROOT_SCREENS.has(current) ? 'root' : 'back';
        return origNavigateToRoot.apply(this, arguments);
      };
    }

    if (typeof global.navigateForward === 'function') {
      const origNavigateForward = global.navigateForward;
      global.navigateForward = function navigateForwardMotionV1731(screenName, options = {}) {
        if (state.lastTransitionTarget === screenName && (Date.now() - state.lastTransitionAt < 180)) {
          return;
        }
        state.pendingDirectionHint = OVERLAY_SCREENS.has(screenName) ? 'overlay' : 'forward';
        return origNavigateForward.apply(this, arguments);
      };
    }

    if (typeof global.navigateBack === 'function') {
      const origNavigateBack = global.navigateBack;
      global.navigateBack = function navigateBackMotionV1731(fallbackRoot = 'home') {
        state.pendingDirectionHint = 'back';
        return origNavigateBack.apply(this, arguments);
      };
    }

    if (typeof global.replaceScreen === 'function') {
      const origReplaceScreen = global.replaceScreen;
      global.replaceScreen = function replaceScreenMotionV1731(screenName, options = {}) {
        const current = getActiveScreenName();
        if (current === 'timer') {
          state.pendingDirectionHint = 'session-exit';
        } else if (screenName === 'timer') {
          state.pendingDirectionHint = 'session-start';
        } else if (ROOT_SCREENS.has(screenName)) {
          state.pendingDirectionHint = 'back';
        } else {
          state.pendingDirectionHint = 'forward';
        }
        return origReplaceScreen.apply(this, arguments);
      };
    }
  }

  // Ensure backgrounding/returning never leaves navigation stuck in a transitioning state
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      cleanupScreenTransition();
      if (state.splashVisible || state.splashDismissing) {
        dismissStartupSplash(true);
      }
    }
  });

  /* ==========================================================================
     4. CENTRAL PLUS ACTION SHEET & TASK POPUP MOTION
     ========================================================================== */

  function installPlusActionMotion() {
    if (typeof global.openFocusChoice === 'function') {
      const origOpenFocusChoice = global.openFocusChoice;
      global.openFocusChoice = function openFocusChoiceMotionV1731() {
        if (state.focusChoiceCloseTimer) {
          clearTimeout(state.focusChoiceCloseTimer);
          state.focusChoiceCloseTimer = null;
        }
        const overlay = document.getElementById('focusChoiceOverlay');
        if (overlay) overlay.classList.remove('fm-sheet-closing');
        document.body?.classList.add('fm-choice-sheet-open');
        const res = origOpenFocusChoice.apply(this, arguments);
        return res;
      };
    }

    if (typeof global.closeFocusChoice === 'function') {
      const origCloseFocusChoice = global.closeFocusChoice;
      global.closeFocusChoice = function closeFocusChoiceMotionV1731(immediate = false) {
        const overlay = document.getElementById('focusChoiceOverlay');
        const wrapper = document.getElementById('screenWrapper');
        document.body?.classList.remove('fm-choice-sheet-open');
        if (wrapper) wrapper.classList.remove('focus-choice-open');

        if (!overlay || overlay.classList.contains('hidden')) {
          return origCloseFocusChoice.apply(this, arguments);
        }

        if (immediate || prefersReducedMotion()) {
          overlay.classList.remove('fm-sheet-closing', 'focus-choice-enter', 'focus-choice-leave');
          const res = origCloseFocusChoice.apply(this, arguments);
          overlay.classList.add('hidden');
          return res;
        }

        overlay.classList.add('fm-sheet-closing');
        const res = origCloseFocusChoice.apply(this, arguments);
        if (state.focusChoiceCloseTimer) clearTimeout(state.focusChoiceCloseTimer);
        state.focusChoiceCloseTimer = setTimeout(() => {
          state.focusChoiceCloseTimer = null;
          overlay.classList.remove('fm-sheet-closing', 'focus-choice-leave');
          overlay.classList.add('hidden');
        }, 195);
        return res;
      };
    }

    if (typeof global.hideUcTask === 'function') {
      const origHideUcTask = global.hideUcTask;
      global.hideUcTask = function hideUcTaskMotionV1731() {
        const popup = document.getElementById('ucTaskPopup');
        if (!popup || !popup.classList.contains('show') || prefersReducedMotion()) {
          return origHideUcTask.apply(this, arguments);
        }
        if (popup.classList.contains('fm-sheet-closing')) return;
        popup.classList.add('fm-sheet-closing');
        if (state.ucTaskCloseTimer) clearTimeout(state.ucTaskCloseTimer);
        state.ucTaskCloseTimer = setTimeout(() => {
          state.ucTaskCloseTimer = null;
          popup.classList.remove('fm-sheet-closing');
          origHideUcTask.apply(this, arguments);
        }, 180);
      };
    }
  }

  /* ==========================================================================
     5. THREE-DOT MISSION MENU MOTION & ACCESSIBILITY
     ========================================================================== */

  function installMissionMenuMotion() {
    document.addEventListener('click', event => {
      const trigger = event.target?.closest?.('.mmc-menu');
      if (!trigger) return;
      if (state.lastMissionMenuTrigger && state.lastMissionMenuTrigger !== trigger) {
        state.lastMissionMenuTrigger.classList.remove('fm-trigger-active');
      }
      state.lastMissionMenuTrigger = trigger;
      trigger.classList.add('fm-trigger-active');
    }, true);

    if (typeof global.openMissionSheetV116 === 'function') {
      const origOpenSheet = global.openMissionSheetV116;
      global.openMissionSheetV116 = function openMissionSheetMotionV1731(content) {
        if (state.missionSheetCloseTimer) {
          clearTimeout(state.missionSheetCloseTimer);
          state.missionSheetCloseTimer = null;
        }
        const sheet = document.getElementById('missionSheetV116');
        if (sheet) sheet.classList.remove('fm-sheet-closing');

        const res = origOpenSheet.apply(this, arguments);

        const activeSheet = document.getElementById('missionSheetV116');
        if (activeSheet) {
          activeSheet.setAttribute('role', 'dialog');
          activeSheet.setAttribute('aria-modal', 'true');
          requestAnimationFrame(() => {
            const firstInputOrBtn = activeSheet.querySelector('input, button.mission-sheet-action-v116');
            firstInputOrBtn?.focus?.({ preventScroll: true });
          });
        }
        return res;
      };
    }

    if (typeof global.closeMissionSheetV116 === 'function') {
      const origCloseSheet = global.closeMissionSheetV116;
      global.closeMissionSheetV116 = function closeMissionSheetMotionV1731(immediate = false) {
        const sheet = document.getElementById('missionSheetV116');
        const triggerToFocus = state.lastMissionMenuTrigger;
        if (triggerToFocus) {
          triggerToFocus.classList.remove('fm-trigger-active');
          state.lastMissionMenuTrigger = null;
        }

        const restoreFocus = () => {
          if (triggerToFocus && document.body.contains(triggerToFocus)) {
            try {
              triggerToFocus.focus({ preventScroll: true });
            } catch {}
          }
        };

        if (!sheet || !sheet.classList.contains('open')) {
          const r = origCloseSheet.apply(this, arguments);
          restoreFocus();
          return r;
        }

        if (immediate || prefersReducedMotion()) {
          sheet.classList.remove('fm-sheet-closing');
          const r = origCloseSheet.apply(this, arguments);
          restoreFocus();
          return r;
        }

        if (sheet.classList.contains('fm-sheet-closing')) return;
        sheet.classList.add('fm-sheet-closing');
        if (state.missionSheetCloseTimer) clearTimeout(state.missionSheetCloseTimer);
        state.missionSheetCloseTimer = setTimeout(() => {
          state.missionSheetCloseTimer = null;
          sheet.classList.remove('fm-sheet-closing');
          origCloseSheet.apply(this, arguments);
          restoreFocus();
        }, 180);
      };
    }

    // Keyboard Escape support for all sheets and dialogs
    document.addEventListener('keydown', event => {
      if (event.key !== 'Escape') return;

      const missionSheet = document.getElementById('missionSheetV116');
      if (missionSheet && missionSheet.classList.contains('open') && !missionSheet.classList.contains('fm-sheet-closing')) {
        event.preventDefault();
        global.closeMissionSheetV116?.();
        return;
      }

      const choiceOverlay = document.getElementById('focusChoiceOverlay');
      if (choiceOverlay && !choiceOverlay.classList.contains('hidden') && !choiceOverlay.classList.contains('fm-sheet-closing')) {
        event.preventDefault();
        global.closeFocusChoice?.();
        return;
      }

      const ucPopup = document.getElementById('ucTaskPopup');
      if (ucPopup && ucPopup.classList.contains('show')) {
        event.preventDefault();
        global.hideUcTask?.();
        return;
      }

      const blockedPopup = document.getElementById('blockedAppsPopup');
      if (blockedPopup && !blockedPopup.classList.contains('hidden')) {
        event.preventDefault();
        global.closeBlockedAppsPopup?.();
      }
    });
  }

  /* ==========================================================================
     6. MISSION CAROUSEL CARD EMPHASIS & MISSION PLAN SCROLL
     ========================================================================== */

  function syncMissionCarouselSlideEmphasis(host) {
    const root = host || document.getElementById('missionCarouselV1181');
    if (!root) return;
    const slides = [...root.children].filter(el => el.classList.contains('mission-hero-v116') || el.classList.contains('mission-carousel-slide'));
    if (!slides.length) return;

    if (slides.length === 1) {
      slides[0].classList.add('is-centered-card');
      slides[0].classList.remove('is-adjacent-card');
      return;
    }

    const center = root.scrollLeft + Math.max(root.clientWidth, 1) / 2;
    let closestIdx = 0;
    let minDistance = Infinity;
    slides.forEach((el, idx) => {
      const dist = Math.abs((el.offsetLeft + el.offsetWidth / 2) - center);
      if (dist < minDistance) {
        minDistance = dist;
        closestIdx = idx;
      }
    });

    slides.forEach((slide, idx) => {
      const centered = idx === closestIdx;
      slide.classList.toggle('is-centered-card', centered);
      slide.classList.toggle('is-adjacent-card', !centered);
    });

    if (!root.dataset.fmMotionBound) {
      root.dataset.fmMotionBound = '1';
      let rafId = 0;
      root.addEventListener('scroll', () => {
        if (rafId) cancelAnimationFrame(rafId);
        rafId = requestAnimationFrame(() => syncMissionCarouselSlideEmphasis(root));
      }, { passive: true });
    }
  }

  function installMissionCarouselAndPlanMotion() {
    if (typeof global.renderHomeCarouselV116 === 'function') {
      const origRenderCarousel = global.renderHomeCarouselV116;
      global.renderHomeCarouselV116 = function renderHomeCarouselMotionV1731() {
        const res = origRenderCarousel.apply(this, arguments);
        syncMissionCarouselSlideEmphasis();
        return res;
      };
    }

    if (typeof global.toggleFutureStepV81 === 'function') {
      const origToggleFutureStep = global.toggleFutureStepV81;
      global.toggleFutureStepV81 = function toggleFutureStepMotionV1731(index) {
        const numericIndex = Number(index);
        const res = origToggleFutureStep.apply(this, arguments);

        // For step 1 (index 0), never scroll the page down unnecessarily.
        // For later steps (index > 0), smoothly scroll selected step into comfortable view.
        if (numericIndex > 0) {
          requestAnimationFrame(() => {
            const container = document.getElementById('aiPlanSteps') || document.getElementById('planStepsContainer');
            if (!container) return;
            const targetCard = container.querySelector('.flow-step-line.mission-step-future.expanded, .mission-future-step .mission-future-detail')?.closest('.flow-step-line, .mission-future-step');
            const scrollHost = document.getElementById('app-screen-plan') || document.getElementById('appBody');
            if (!targetCard || !scrollHost) return;

            const hostRect = scrollHost.getBoundingClientRect();
            const cardRect = targetCard.getBoundingClientRect();
            const topSafeOffset = 68;
            const bottomSafeOffset = 88;

            if (cardRect.bottom > hostRect.bottom - bottomSafeOffset || cardRect.top < hostRect.top + topSafeOffset) {
              const targetScrollTop = Math.max(
                0,
                scrollHost.scrollTop + (cardRect.top - hostRect.top) - topSafeOffset
              );
              scrollHost.scrollTo({
                top: targetScrollTop,
                left: 0,
                behavior: prefersReducedMotion() ? 'auto' : 'smooth'
              });
            }
          });
        }
        return res;
      };
    }
  }

  /* ==========================================================================
     7. ACTIVITY INSERTION, HISTORY FILTER CROSSFADE & EXPANDABLE DETAILS
     ========================================================================== */

  function triggerListCrossfade(listId) {
    if (prefersReducedMotion()) return;
    const list = document.getElementById(listId);
    if (!list) return;
    list.classList.remove('fm-list-crossfade');
    void list.offsetWidth;
    list.classList.add('fm-list-crossfade');
    setTimeout(() => {
      list.classList.remove('fm-list-crossfade');
    }, 210);
  }

  function getSessionHistoryListSafe() {
    try {
      if (typeof global.sessionRepository?.getAll === 'function') {
        return global.sessionRepository.getAll();
      }
      if (typeof global.FocusModeApp?.repositories?.sessions?.listHistory === 'function') {
        return global.FocusModeApp.repositories.sessions.listHistory();
      }
    } catch {}
    return [];
  }

  function enhanceHistoryCardsWithDetails() {
    const list = document.getElementById('sessionHistoryList');
    if (!list) return;
    const cards = [...list.querySelectorAll('.history-card')];
    if (!cards.length) return;

    const historyItems = getSessionHistoryListSafe();

    cards.forEach((card, index) => {
      if (card.dataset.fmExpandableBound === '1') return;
      card.dataset.fmExpandableBound = '1';
      const item = historyItems[index] || {};

      const planned = Math.max(1, Number(item.plannedMinutes) || Number(item.actualMinutes) || 25);
      const actual = Math.max(0, Number(item.actualMinutes) || 0);
      const statusText = item.status === 'completed' ? 'Vollständig abgeschlossen' : 'Vorzeitig beendet';
      const blockedCount = Array.isArray(item.blockedApps) ? item.blockedApps.length : 0;

      const detailBox = document.createElement('div');
      detailBox.className = 'fm-history-extra-details';
      detailBox.innerHTML =
        `<div class="fm-history-detail-chip"><strong>Geplant / Erreicht</strong><span>${planned} Min. / ${actual} Min.</span></div>` +
        `<div class="fm-history-detail-chip"><strong>Status</strong><span>${statusText}</span></div>` +
        (item.missionTitle ? `<div class="fm-history-detail-chip"><strong>Mission</strong><span>${String(item.missionTitle).replace(/[<>&"]/g, '')}</span></div>` : '') +
        (blockedCount > 0 ? `<div class="fm-history-detail-chip"><strong>Fokus-Schutz</strong><span>${blockedCount} Apps blockiert</span></div>` : '');

      card.appendChild(detailBox);
      card.setAttribute('role', 'button');
      card.setAttribute('tabindex', '0');
      card.setAttribute('aria-expanded', 'false');

      const toggleExpand = () => {
        const expanded = card.classList.toggle('fm-history-expanded');
        card.setAttribute('aria-expanded', expanded ? 'true' : 'false');
      };

      card.addEventListener('click', toggleExpand);
      card.addEventListener('keydown', e => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggleExpand();
        }
      });
    });
  }

  function installActivityAndHistoryMotion() {
    if (typeof global.renderHomeActivityTimelineV173 === 'function') {
      const origRenderActivity = global.renderHomeActivityTimelineV173;
      global.renderHomeActivityTimelineV173 = function renderHomeActivityMotionV1731() {
        const activityList = document.getElementById('homeActivityList');
        const prevScrollTop = activityList ? activityList.scrollTop : 0;
        const topIdBefore = state.lastKnownTopActivityKey;

        const res = origRenderActivity.apply(this, arguments);

        const all = getSessionHistoryListSafe();
        const topItemNow = all[0];
        const topKeyNow = topItemNow ? (topItemNow.id || `${topItemNow.finishedAt}_${topItemNow.actualMinutes}`) : null;

        if (activityList && topKeyNow && topIdBefore && topKeyNow !== topIdBefore && !prefersReducedMotion()) {
          const firstRow = activityList.querySelector('.home-activity-row');
          if (firstRow) {
            firstRow.classList.add('fm-activity-new-item');
            setTimeout(() => firstRow.classList.remove('fm-activity-new-item'), 350);
          }
          activityList.scrollTop = prevScrollTop;
        }
        if (topKeyNow) {
          state.lastKnownTopActivityKey = topKeyNow;
        }
        return res;
      };
    }

    if (typeof global.renderSessionHistory === 'function') {
      const origRenderSessionHistory = global.renderSessionHistory;
      global.renderSessionHistory = function renderSessionHistoryMotionV1731() {
        const res = origRenderSessionHistory.apply(this, arguments);
        enhanceHistoryCardsWithDetails();
        return res;
      };
    }

    if (typeof global.setHistoryFilter === 'function') {
      const origSetHistoryFilter = global.setHistoryFilter;
      global.setHistoryFilter = function setHistoryFilterMotionV1731(filter) {
        const res = origSetHistoryFilter.apply(this, arguments);
        triggerListCrossfade('sessionHistoryList');
        return res;
      };
    }

    if (typeof global.setMissionFilterV116 === 'function') {
      const origSetMissionFilter = global.setMissionFilterV116;
      global.setMissionFilterV116 = function setMissionFilterMotionV1731(filter, button) {
        const res = origSetMissionFilter.apply(this, arguments);
        triggerListCrossfade('missionManagementListV116');
        return res;
      };
    }
  }

  /* ==========================================================================
     8.INITIALIZATION & EXPORT
     ========================================================================== */

  function initMotionSystem() {
    global.currentAppScreen = getActiveScreenName();
    installScreenTransitionHook();
    installPlusActionMotion();
    installMissionMenuMotion();
    installMissionCarouselAndPlanMotion();
    installActivityAndHistoryMotion();
    scheduleSplashExitWhenReady();

    setTimeout(() => {
      syncMissionCarouselSlideEmphasis();
      enhanceHistoryCardsWithDetails();
    }, 320);
  }

  initMotionSystem();

  if (global.FocusModeApp) {
    global.FocusModeApp.core = global.FocusModeApp.core || {};
    global.FocusModeApp.core.motion = Object.freeze({
      version: '17.3.1',
      dismissSplash: dismissStartupSplash,
      cleanupTransitions: cleanupScreenTransition,
      syncCarouselEmphasis: syncMissionCarouselSlideEmphasis
    });
  }
})(window);
