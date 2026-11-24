/*
 * Focus Mode+ Pitch · modes.
 *
 *   ?mode=recording  Loom / Meet / OBS capture: no overlays, no notes, no timer, no beat or scene
 *                    labels, no placeholder warnings; the cursor hides after inactivity. Overlay
 *                    keys (T, N, H, E) do nothing, so nothing can be revealed by accident.
 *   ?mode=rehearsal  Presenter overlay (elapsed time, scene and beat, target, cues, warnings),
 *                    speaker notes (N) and the camera-safe outline.
 *   (none)/review    Clean stage; overlays can be toggled (T, N, H, E).
 *
 * Other parameters: ?scene=4&beat=2 (start state, 1-based scene, 0-based beat),
 * ?motion=reduced (force the reduced-motion path), ?safe=show (outline the camera-safe area).
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const params = new URLSearchParams(global.location.search);
  const raw = (params.get('mode') || 'review').toLowerCase();
  const mode = raw === 'recording' || raw === 'rehearsal' ? raw : 'review';
  const html = document.documentElement;

  const mq = global.matchMedia ? global.matchMedia('(prefers-reduced-motion: reduce)') : null;
  const forced = (params.get('motion') || '').toLowerCase() === 'reduced';

  const modes = {
    mode: mode,
    isRecording: mode === 'recording',
    isRehearsal: mode === 'rehearsal',
    reducedMotion: forced || Boolean(mq && mq.matches),
    startScene: params.has('scene') ? parseInt(params.get('scene'), 10) || 1 : null,
    startBeat: params.has('beat') ? parseInt(params.get('beat'), 10) : null,
    showSafe: params.get('safe') === 'show'
  };

  html.classList.add('mode-' + mode);
  html.classList.toggle('reduced-motion', modes.reducedMotion);
  if (mq && !forced && mq.addEventListener) {
    mq.addEventListener('change', function (e) {
      modes.reducedMotion = e.matches;
      html.classList.toggle('reduced-motion', e.matches);
    });
  }

  /* Camera-safe area from the content file (stage pixels). */
  function applySafeArea() {
    const safe = (global.PITCH_CONTENT && global.PITCH_CONTENT.cameraSafeArea) || { width: 320, height: 220 };
    html.style.setProperty('--safe-w', safe.width + 'px');
    html.style.setProperty('--safe-h', safe.height + 'px');
    html.classList.toggle('show-safe', modes.isRehearsal || modes.showSafe);
  }

  /* Recording: hide the cursor after 1.5 s without pointer movement; any movement shows it again. */
  function cursorHiding() {
    if (!modes.isRecording) return;
    let timer = 0;
    function arm() {
      html.classList.remove('cursor-hidden');
      global.clearTimeout(timer);
      timer = global.setTimeout(function () { html.classList.add('cursor-hidden'); }, 1500);
    }
    global.addEventListener('pointermove', arm, { passive: true });
    global.addEventListener('pointerdown', arm, { passive: true });
    arm();
  }

  modes.init = function () {
    applySafeArea();
    cursorHiding();
  };

  Pitch.modes = modes;
})(window);
