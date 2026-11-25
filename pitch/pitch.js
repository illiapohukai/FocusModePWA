/*
 * Focus Mode+ Pitch · bootstrap.
 * Waits for the local font so the first frame never re-flows, then starts the controller.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch;

  function start() {
    Pitch.modes.init();
    Pitch.controller.init({ scene: Pitch.modes.startScene, beat: Pitch.modes.startBeat });
    Pitch.rehearsal.init();
    Pitch.keyboard.init();
    document.documentElement.classList.add('is-ready');
    global.__pitchReady = true;
  }

  const fonts = document.fonts;
  if (fonts && fonts.load) {
    Promise.race([
      Promise.all([fonts.load('900 100px "Inter Pitch"'), fonts.load('600 30px "Inter Pitch"')]),
      new Promise(function (resolve) { global.setTimeout(resolve, 1500); })
    ]).then(start, start);
  } else {
    start();
  }
})(window);
