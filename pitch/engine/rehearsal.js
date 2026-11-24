/*
 * Focus Mode+ Pitch · rehearsal overlays.
 *
 * Presenter overlay (T), speaker notes (N), controls help (H) and the evidence appendix (E).
 * All overlays sit outside the stage in #overlay-root (position: fixed), so they never change the
 * stage layout. None of them can open in recording mode.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const esc = function (s) { return Pitch.dom.esc(s); };
  const NOTES = global.PITCH_NOTES || {};
  const SOURCES = global.PITCH_SOURCES || [];
  const MARKERS = [35, 65, 90, 160, 210, 250, 285]; // 0:35 1:05 1:30 2:40 3:30 4:10 4:45
  const LIMIT = 300;

  const ui = {};
  const clock = { startedAt: 0, pausedAt: 0, pausedTotal: 0, running: false, sceneStart: 0 };
  let tickId = 0;
  let qrStatus = 'wird geprüft …';

  function fmt(sec) {
    const s = Math.max(0, Math.round(sec));
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  }
  function fmtDiff(sec) {
    const sign = sec > 0 ? '+' : (sec < 0 ? '−' : '±');
    return sign + fmt(Math.abs(sec));
  }

  function elapsed() {
    if (!clock.running) return 0;
    const end = clock.pausedAt || performance.now();
    return (end - clock.startedAt - clock.pausedTotal) / 1000;
  }

  function startClock() {
    if (clock.running) return;
    clock.running = true;
    clock.startedAt = performance.now();
    clock.pausedTotal = 0;
    clock.pausedAt = 0;
    clock.sceneStart = 0;
  }
  function resetClock() {
    clock.running = false;
    clock.startedAt = 0;
    clock.pausedAt = 0;
    clock.pausedTotal = 0;
    clock.sceneStart = 0;
    render();
  }

  function build() {
    const root = document.getElementById('overlay-root');
    root.innerHTML = [
      '<aside class="ov ov-presenter" id="ovPresenter" hidden aria-label="Presenter">',
      '  <div class="ovp-top"><b class="ovp-time" data-k="time">0:00</b><span class="ovp-limit">/ 4:45 Ziel · 5:00 Limit</span><span class="ovp-paused" data-k="paused">Pausiert</span></div>',
      '  <div class="ovp-bar" data-k="bar"><i data-k="fill"></i></div>',
      '  <div class="ovp-row"><span data-k="scene"></span><span data-k="beat"></span></div>',
      '  <div class="ovp-row"><span>Szenenziel <b data-k="target"></b></span><span data-k="diff"></span></div>',
      '  <p class="ovp-cue" data-k="cue"></p>',
      '  <p class="ovp-next"><span>Als Nächstes</span> <b data-k="next"></b></p>',
      '  <ul class="ovp-warn" data-k="warn"></ul>',
      '</aside>',
      '<aside class="ov ov-notes" id="ovNotes" hidden aria-label="Sprechernotizen">',
      '  <p class="ovn-kicker" data-k="nkicker"></p><p class="ovn-say" data-k="say"></p><p class="ovn-note" data-k="note"></p>',
      '</aside>',
      '<aside class="ov ov-help" id="ovHelp" hidden aria-label="Steuerung">',
      '  <h2>Steuerung</h2>',
      '  <dl>',
      '   <dt>Leertaste</dt><dd>nächster Beat (danach nächste Szene)</dd>',
      '   <dt>Umschalt + Leertaste</dt><dd>vorheriger Beat</dd>',
      '   <dt>→ / ←</dt><dd>nächste / vorherige Szene</dd>',
      '   <dt>R</dt><dd>Szene neu starten</dd>',
      '   <dt>F</dt><dd>Vollbild</dd>',
      '   <dt>P</dt><dd>Animation pausieren / fortsetzen</dd>',
      '   <dt>T</dt><dd>Probe-Timer (Umschalt + T: zurücksetzen)</dd>',
      '   <dt>N</dt><dd>Sprechernotizen</dd>',
      '   <dt>E</dt><dd>Quellen-Anhang (nicht Teil der Aufnahme)</dd>',
      '   <dt>H</dt><dd>diese Hilfe</dd>',
      '   <dt>Esc</dt><dd>Overlays schließen</dd>',
      '  </dl>',
      '  <p>Aufnahme: <code>?mode=recording</code> · Probe: <code>?mode=rehearsal</code></p>',
      '</aside>',
      '<section class="ov ov-appendix" id="ovAppendix" hidden aria-label="Quellen-Anhang">',
      '  <h2>Quellen-Anhang <small>nur Probe/Review · nicht Teil der Aufnahme</small></h2>',
      '  <div class="ova-grid">' + SOURCES.map(function (s) {
        return '<article><h3>' + esc(s.title) + '</h3><p class="ova-fig">' + s.figures.map(esc).join('<br>') + '</p>' +
          '<p><b>Population:</b> ' + esc(s.population) + ' · <b>Stichprobe:</b> ' + esc(s.sample) + '</p>' +
          '<p>' + esc(s.meaning) + '</p><ul>' + s.limitations.map(function (l) { return '<li>' + esc(l) + '</li>'; }).join('') + '</ul></article>';
      }).join('') + '</div>',
      '</section>'
    ].join('');
    ['ovPresenter', 'ovNotes', 'ovHelp', 'ovAppendix'].forEach(function (id) { ui[id] = document.getElementById(id); });
    root.querySelectorAll('[data-k]').forEach(function (el) { ui[el.dataset.k] = el; });
    const bar = ui.bar;
    MARKERS.forEach(function (m, i) {
      const tick = document.createElement('b');
      tick.style.left = (m / LIMIT * 100) + '%';
      tick.title = fmt(m);
      tick.textContent = i === MARKERS.length - 1 ? fmt(m) : '';
      bar.appendChild(tick);
    });
  }

  function noteFor(snap, offset) {
    const scenes = Pitch.controller.scenes();
    let index = snap.index, beat = snap.beat + (offset || 0);
    if (beat >= scenes[index].beats.length) { index += 1; beat = 0; }
    if (index >= scenes.length) return null;
    if (beat < 0) beat = 0;
    const def = scenes[index];
    const sn = NOTES[def.id] || {};
    const bn = (sn.beats || {})[def.beats[beat]] || {};
    return { def: def, beat: beat, sceneNote: sn, note: bn };
  }

  function render() {
    if (!ui.time) return;
    const snap = Pitch.controller.snapshot();
    const sec = elapsed();
    ui.time.textContent = fmt(sec);
    ui.time.classList.toggle('is-over', sec > 285);
    ui.fill.style.width = Math.min(100, sec / LIMIT * 100) + '%';
    ui.paused.hidden = !snap.paused;
    const cur = noteFor(snap, 0);
    const nxt = noteFor(snap, 1);
    if (!cur) return;
    const end = cur.sceneNote.end || 0;
    ui.scene.textContent = 'Szene ' + snap.sceneNumber + '/' + snap.sceneCount + ' · ' + snap.sceneTitle;
    ui.beat.textContent = snap.beat < 0 ? 'Bereit (Leertaste startet)' : 'Beat ' + (snap.beat + 1) + '/' + snap.beatCount + ' · ' + snap.beatId;
    ui.target.textContent = fmt(end);
    const diff = clock.running ? sec - end : 0;
    ui.diff.textContent = clock.running ? fmtDiff(diff) + (diff > 0 ? ' über Ziel' : ' Reserve') : 'Timer startet mit der Leertaste';
    ui.diff.className = clock.running && diff > 0 ? 'is-over' : 'is-ok';
    ui.cue.textContent = snap.beat < 0 ? 'Aufnahme starten, kurz vorstellen, dann Leertaste.' : (cur.note.cue || '');
    ui.next.textContent = nxt ? (nxt.note.cue || nxt.def.title) : 'Ende';
    ui.nkicker.textContent = (snap.beat < 0 ? 'Vor dem Start' : snap.sceneTitle + ' · ' + snap.beatId) + ' · Ziel ' + fmt(end);
    ui.say.textContent = snap.beat < 0 ? 'Begrüßung, Name, dann Szene 1.' : (cur.note.say || '');
    ui.note.textContent = snap.beat < 0 ? '' : (cur.note.note || '');
    const warns = Pitch.content.unresolved().map(function (w) { return 'Platzhalter offen: ' + w; });
    warns.push('QR: ' + qrStatus);
    ui.warn.innerHTML = warns.map(function (w) { return '<li>' + esc(w) + '</li>'; }).join('');
  }

  function loop() {
    render();
    tickId = global.setTimeout(loop, 250);
  }

  /* Reads the live QR slot and reports whether it is still the placeholder (marker attribute). */
  function checkQr() {
    const src = global.PITCH_CONTENT && global.PITCH_CONTENT.qrAsset;
    if (!src || global.location.protocol === 'file:') { qrStatus = 'Status unbekannt (file://)'; return; }
    fetch(src, { cache: 'no-store' }).then(function (r) { return r.ok ? r.text() : ''; }).then(function (txt) {
      if (!txt) qrStatus = 'Datei fehlt: ' + src;
      else qrStatus = /data-pitch-placeholder="true"/.test(txt) ? 'Platzhalter aktiv (nicht scannbar)' : 'eigener QR eingesetzt – vor der Aufnahme scannen';
      render();
    }, function () { qrStatus = 'Status unbekannt'; render(); });
  }

  function toggle(id, force) {
    const el = ui[id];
    if (!el) return;
    el.hidden = force == null ? !el.hidden : !force;
  }

  function closeAll() {
    let closed = false;
    ['ovNotes', 'ovHelp', 'ovAppendix'].forEach(function (id) { if (ui[id] && !ui[id].hidden) { ui[id].hidden = true; closed = true; } });
    return closed;
  }

  function init() {
    if (Pitch.modes.isRecording) return;
    build();
    if (Pitch.modes.isRehearsal) {
      toggle('ovPresenter', true);
      checkQr();
      loop();
    }
    Pitch.controller.on(function (type, snap) {
      if (type === 'start') startClock();
      if (type === 'pause') {
        if (snap.paused && clock.running && !clock.pausedAt) clock.pausedAt = performance.now();
        if (!snap.paused && clock.pausedAt) { clock.pausedTotal += performance.now() - clock.pausedAt; clock.pausedAt = 0; }
      }
      render();
    });
  }

  Pitch.rehearsal = Object.freeze({
    init: init,
    togglePresenter: function () {
      if (Pitch.modes.isRecording) return;
      toggle('ovPresenter');
      if (!tickId) { checkQr(); loop(); }
    },
    toggleNotes: function () { if (!Pitch.modes.isRecording) { toggle('ovNotes'); render(); } },
    toggleHelp: function () { if (!Pitch.modes.isRecording) toggle('ovHelp'); },
    toggleAppendix: function () { if (!Pitch.modes.isRecording) toggle('ovAppendix'); },
    resetClock: function () { if (!Pitch.modes.isRecording) resetClock(); },
    closeAll: closeAll
  });
})(window);
