/*
 * Focus Mode+ Pitch · keyboard and pointer input.
 *
 * Space next beat · Shift+Space previous beat · → / ← next / previous scene · R restart scene ·
 * F fullscreen · P pause · T rehearsal timer (Shift+T reset) · N notes · H controls · E evidence
 * appendix · Esc close overlays. Auto-repeat is ignored; the controller adds a short input lock.
 * Touch (review on a phone or tablet): tap the right 60 % for the next beat, the left 40 % for the
 * previous beat.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};

  function toggleFullscreen() {
    const d = document;
    if (d.fullscreenElement) { if (d.exitFullscreen) d.exitFullscreen().catch(function () {}); }
    else if (d.documentElement.requestFullscreen) d.documentElement.requestFullscreen().catch(function () {});
  }

  function onKey(e) {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const C = Pitch.controller, R = Pitch.rehearsal;
    const key = e.key;
    const handled = function () { e.preventDefault(); e.stopPropagation(); };

    if (key === ' ' || e.code === 'Space') {
      handled();
      if (e.repeat) return;
      if (e.shiftKey) C.prev(); else C.next();
      return;
    }
    if (e.repeat) return;
    switch (key) {
      case 'ArrowRight': case 'PageDown': handled(); C.nextScene(); break;
      case 'ArrowLeft': case 'PageUp': handled(); C.prevScene(); break;
      case 'r': case 'R': handled(); C.restart(); break;
      case 'f': case 'F': handled(); toggleFullscreen(); break;
      case 'p': case 'P': handled(); C.togglePause(); break;
      case 't': handled(); R.togglePresenter(); break;
      case 'T': handled(); R.resetClock(); break;
      case 'n': case 'N': handled(); R.toggleNotes(); break;
      case 'h': case 'H': handled(); R.toggleHelp(); break;
      case 'e': case 'E': handled(); R.toggleAppendix(); break;
      case 'Escape':
        if (!R.closeAll() && document.fullscreenElement) toggleFullscreen();
        break;
      default: break;
    }
  }

  function onPointer(e) {
    if (e.pointerType !== 'touch') return;
    if (e.target.closest && e.target.closest('.ov')) return;
    const x = e.clientX / global.innerWidth;
    if (x > 0.4) Pitch.controller.next(); else Pitch.controller.prev();
  }

  Pitch.keyboard = Object.freeze({
    init: function () {
      global.addEventListener('keydown', onKey);
      global.addEventListener('pointerup', onPointer, { passive: true });
    }
  });
})(window);
