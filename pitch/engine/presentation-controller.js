/**
 * Focus Mode+ Pitch Presentation Engine
 * Manages scene lifecycle, manual beat sequencing, keyboard input locking,
 * ?mode=recording camera-safe & cursor-hide discipline, and ?mode=rehearsal presenter HUD.
 */

import { getUnresolvedPlaceholders, pitchData, sourceRegistry } from "../data/pitch-content.js";
import { SCENE_TIMING_MARKERS, speakerNotes } from "../data/speaker-notes.js";

const INPUT_LOCK_MS = 220;
const CURSOR_IDLE_MS = 1800;

function formatTime(sec) {
  const s = Math.max(0, Math.round(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${r < 10 ? "0" : ""}${r}`;
}

function formatDelta(diffSec) {
  const rounded = Math.round(diffSec);
  if (Math.abs(rounded) <= 2) return "im Zeitplan (0:00)";
  const sign = rounded > 0 ? "+" : "−";
  const abs = Math.abs(rounded);
  const m = Math.floor(abs / 60);
  const r = abs % 60;
  return `${sign}${m}:${r < 10 ? "0" : ""}${r}`;
}

export class PresentationController {
  constructor({ stageEl, hudRootEl, scenes, mode = "standard", reducedMotion = false }) {
    this.stageEl = stageEl;
    this.hudRootEl = hudRootEl;
    this.scenes = scenes;
    this.mode = mode; // 'recording' | 'rehearsal' | 'standard'
    this.reducedMotion = reducedMotion;

    this.sceneIndex = 0;
    this.beatIndex = 0;
    this.paused = false;
    this.inputLockedUntil = 0;

    // Rehearsal overlay toggles (never visible in recording mode)
    this.showTimer = mode === "rehearsal";
    this.showNotes = mode === "rehearsal";
    this.showControls = mode === "rehearsal";
    this.showAppendix = false;

    // Rehearsal elapsed timer
    this.elapsedSec = 0;
    this.lastTickAt = performance.now();
    this.timerInterval = null;

    // Cursor inactivity tracker
    this.cursorTimer = null;
    this.sceneEls = [];
  }

  init() {
    document.documentElement.dataset.pitchMode = this.mode;
    document.documentElement.dataset.reducedMotion = String(this.reducedMotion);

    // Mount all 7 scene DOM trees once; only active scene runs animations
    this.stageEl.innerHTML = this.scenes.map((s) => s.render()).join("");
    this.sceneEls = this.scenes.map((s) => document.getElementById(s.id));

    this.scenes.forEach((s, idx) => {
      const el = this.sceneEls[idx];
      if (el && typeof s.onMount === "function") {
        s.onMount(el);
      }
    });

    this._buildHud();
    this._bindKeyboard();
    this._bindPointerActivity();
    this._startRehearsalClock();

    // Check URL params for deterministic jump (?scene=1..7&beat=0..7)
    const params = new URLSearchParams(window.location.search);
    const initScene = Math.max(0, Math.min(this.scenes.length - 1, (parseInt(params.get("scene"), 10) || 1) - 1));
    const maxBeat = this.scenes[initScene].beatCount - 1;
    const initBeat = Math.max(0, Math.min(maxBeat, parseInt(params.get("beat"), 10) || 0));

    this.goTo(initScene, initBeat, { skipLock: true });
  }

  _isInputLocked() {
    const now = performance.now();
    if (now < this.inputLockedUntil) return true;
    this.inputLockedUntil = now + INPUT_LOCK_MS;
    return false;
  }

  goTo(nextSceneIdx, nextBeatIdx = 0, opts = {}) {
    if (!opts.skipLock && this._isInputLocked()) return false;

    const clampedScene = Math.max(0, Math.min(this.scenes.length - 1, nextSceneIdx));
    const targetScene = this.scenes[clampedScene];
    const clampedBeat = Math.max(0, Math.min(targetScene.beatCount - 1, nextBeatIdx));

    const prevSceneIdx = this.sceneIndex;
    const prevScene = this.scenes[prevSceneIdx];
    const prevEl = this.sceneEls[prevSceneIdx];

    if (prevSceneIdx !== clampedScene && prevScene && typeof prevScene.onExit === "function") {
      prevScene.onExit(prevEl);
    }

    this.sceneIndex = clampedScene;
    this.beatIndex = clampedBeat;

    this.sceneEls.forEach((el, idx) => {
      if (!el) return;
      const isActive = idx === clampedScene;
      el.classList.toggle("is-active-scene", isActive);
      el.setAttribute("aria-hidden", String(!isActive));
    });

    const activeEl = this.sceneEls[clampedScene];
    if (activeEl && typeof targetScene.onBeat === "function") {
      targetScene.onBeat(activeEl, clampedBeat);
    }

    // Update top-level cosmic chrome flag so ambient frame matches active scene
    const isCosmicScene =
      targetScene.theme === "cosmic" ||
      (targetScene.id === "scene-04-demo" && clampedBeat === 6);
    document.documentElement.dataset.cosmicStage = String(isCosmicScene);

    this._syncHud();
    return true;
  }

  nextBeat() {
    const currentScene = this.scenes[this.sceneIndex];
    if (this.beatIndex < currentScene.beatCount - 1) {
      return this.goTo(this.sceneIndex, this.beatIndex + 1);
    }
    if (this.sceneIndex < this.scenes.length - 1) {
      return this.goTo(this.sceneIndex + 1, 0);
    }
    return false;
  }

  prevBeat() {
    if (this.beatIndex > 0) {
      return this.goTo(this.sceneIndex, this.beatIndex - 1);
    }
    if (this.sceneIndex > 0) {
      const prevSceneIdx = this.sceneIndex - 1;
      const prevScene = this.scenes[prevSceneIdx];
      return this.goTo(prevSceneIdx, prevScene.beatCount - 1);
    }
    return false;
  }

  nextScene() {
    if (this.sceneIndex < this.scenes.length - 1) {
      return this.goTo(this.sceneIndex + 1, 0);
    }
    return false;
  }

  prevScene() {
    if (this.sceneIndex > 0) {
      return this.goTo(this.sceneIndex - 1, 0);
    }
    return false;
  }

  restartScene() {
    return this.goTo(this.sceneIndex, 0, { skipLock: true });
  }

  togglePause() {
    this.paused = !this.paused;
    document.documentElement.dataset.pitchPaused = String(this.paused);
    const currentScene = this.scenes[this.sceneIndex];
    const activeEl = this.sceneEls[this.sceneIndex];
    if (this.paused) {
      if (typeof currentScene.onPause === "function") currentScene.onPause(activeEl, this.beatIndex);
    } else {
      this.lastTickAt = performance.now();
      if (typeof currentScene.onResume === "function") currentScene.onResume(activeEl, this.beatIndex);
    }
    this._syncHud();
  }

  toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().catch(() => {});
    } else {
      document.exitFullscreen?.().catch(() => {});
    }
  }

  toggleTimer() {
    if (this.mode === "recording") return;
    this.showTimer = !this.showTimer;
    this._syncHud();
  }

  toggleNotes() {
    if (this.mode === "recording") return;
    this.showNotes = !this.showNotes;
    this._syncHud();
  }

  toggleControls() {
    if (this.mode === "recording") return;
    this.showControls = !this.showControls;
    this._syncHud();
  }

  toggleAppendix() {
    if (this.mode === "recording") return;
    this.showAppendix = !this.showAppendix;
    this._syncHud();
  }

  closeOverlays() {
    if (this.showAppendix) {
      this.showAppendix = false;
      this._syncHud();
      return;
    }
    if (document.fullscreenElement) {
      document.exitFullscreen?.().catch(() => {});
    }
  }

  _bindKeyboard() {
    window.addEventListener("keydown", (e) => {
      if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA")) return;

      switch (e.code) {
        case "ArrowRight":
          e.preventDefault();
          this.nextScene();
          break;
        case "ArrowLeft":
          e.preventDefault();
          this.prevScene();
          break;
        case "Space":
          e.preventDefault();
          if (e.shiftKey) this.prevBeat();
          else this.nextBeat();
          break;
        case "KeyR":
          if (!e.metaKey && !e.ctrlKey) {
            e.preventDefault();
            this.restartScene();
          }
          break;
        case "KeyF":
          if (!e.metaKey && !e.ctrlKey) {
            e.preventDefault();
            this.toggleFullscreen();
          }
          break;
        case "KeyP":
          e.preventDefault();
          this.togglePause();
          break;
        case "KeyT":
          e.preventDefault();
          this.toggleTimer();
          break;
        case "KeyN":
          e.preventDefault();
          this.toggleNotes();
          break;
        case "KeyH":
          e.preventDefault();
          this.toggleControls();
          break;
        case "KeyE":
          e.preventDefault();
          this.toggleAppendix();
          break;
        case "Escape":
          e.preventDefault();
          this.closeOverlays();
          break;
        default:
          break;
      }
    });
  }

  _bindPointerActivity() {
    const resetIdle = () => {
      document.documentElement.dataset.cursorIdle = "false";
      if (this.cursorTimer) clearTimeout(this.cursorTimer);
      if (this.mode === "recording") {
        this.cursorTimer = setTimeout(() => {
          document.documentElement.dataset.cursorIdle = "true";
        }, CURSOR_IDLE_MS);
      }
    };
    window.addEventListener("mousemove", resetIdle, { passive: true });
    window.addEventListener("pointerdown", resetIdle, { passive: true });
    resetIdle();
  }

  _startRehearsalClock() {
    this.lastTickAt = performance.now();
    this.timerInterval = setInterval(() => {
      const now = performance.now();
      if (!this.paused) {
        this.elapsedSec += (now - this.lastTickAt) / 1000;
      }
      this.lastTickAt = now;
      if (this.mode !== "recording" && (this.showTimer || this.showNotes)) {
        this._syncTimerDisplay();
      }
    }, 500);
  }

  _buildHud() {
    if (!this.hudRootEl) return;
    const status = getUnresolvedPlaceholders(pitchData);
    const markersHtml = SCENE_TIMING_MARKERS.map(
      (m) => `<span class="hud-marker-chip" data-marker-scene="${m.sceneIndex}">${m.title} · <b>${m.markerLabel}</b></span>`
    ).join("");

    const appendixRowsHtml = sourceRegistry
      .map(
        (src) => `
        <div class="hud-appendix-card">
          <div class="hud-app-top">
            <span class="hud-app-scene">${src.scene}</span>
            <strong class="hud-app-val">${src.value}</strong>
          </div>
          <h4>${src.title}</h4>
          <p class="hud-app-claim">„${src.claim}“</p>
          <div class="hud-app-meta">
            <span>Herausgeber: ${src.publisher}</span>
            <span>Grundgesamtheit: ${src.population}</span>
            <span>Stichprobe: <strong>${src.sample}</strong></span>
          </div>
          <p class="hud-app-limit"><strong>Methodische Einordnung:</strong> ${src.limitation}</p>
        </div>
      `
      )
      .join("");

    this.hudRootEl.innerHTML = `
      <!-- Top-Right 320x220 Camera-Safe Zone Guide (Visible ONLY in Rehearsal Mode) -->
      <div class="hud-camera-safe-guide" id="hudCameraSafeGuide" aria-hidden="true">
        <span>KAMERA-SAFE-ZONE (320 × 220)</span>
        <small>Loom / Meet Kamera-Bubble · Frei von Inhalten</small>
      </div>

      <!-- Compact Controls & Progress Bar (Hidden in ?mode=recording) -->
      <div class="hud-controls-bar" id="hudControlsBar">
        <div class="hud-ctrl-left">
          <span class="hud-mode-pill" id="hudModePill">${this.mode.toUpperCase()}</span>
          <span class="hud-scene-counter" id="hudSceneCounter">Szene 1/7 · Beat 1/3</span>
        </div>
        <div class="hud-ctrl-actions">
          <button type="button" data-action="prev-scene" title="Vorherige Szene (←)">← Szene</button>
          <button type="button" data-action="prev-beat" title="Vorheriger Beat (Shift+Space)">‹ Beat</button>
          <button type="button" data-action="next-beat" class="is-primary" title="Nächster Beat (Space)">Beat ›</button>
          <button type="button" data-action="next-scene" title="Nächste Szene (→)">Szene →</button>
          <button type="button" data-action="restart" title="Szene neu starten (R)">↺ R</button>
          <button type="button" data-action="pause" id="hudPauseBtn" title="Animation pausieren (P)">⏸ P</button>
          <button type="button" data-action="notes" title="Sprechernotizen (N)">Notizen (N)</button>
          <button type="button" data-action="appendix" title="Quellen-Appendix (E)">Quellen (E)</button>
        </div>
      </div>

      <!-- Rehearsal Presenter Panel (Elapsed time, Target time, Delta, Speaker Notes, Placeholder Warnings) -->
      <aside class="hud-rehearsal-panel" id="hudRehearsalPanel" aria-label="Rehearsal Presenter Overlay">
        <div class="hud-reh-top">
          <div class="hud-clock-block">
            <span class="hud-clock-label">GESAMTZEIT</span>
            <strong class="hud-clock-elapsed" id="hudClockElapsed">0:00</strong>
          </div>
          <div class="hud-clock-block">
            <span class="hud-clock-label">SZENEN-ZIEL</span>
            <strong class="hud-clock-target" id="hudClockTarget">0:35</strong>
          </div>
          <div class="hud-clock-block">
            <span class="hud-clock-label">DIFFERENZ</span>
            <strong class="hud-clock-delta" id="hudClockDelta">im Zeitplan (0:00)</strong>
          </div>
          <div class="hud-status-badges">
            <span class="hud-status-chip ${status.hasUnresolvedValidation ? "is-warn" : "is-ok"}">
              ${
                status.hasUnresolvedValidation
                  ? `Validierungs-Tokens offen (${status.validationTokens.length}) → Neutral-Fallback aktiv`
                  : "Validierungs-Tokens gesetzt"
              }
            </span>
            <span class="hud-status-chip ${status.isQrPlaceholder ? "is-warn" : "is-ok"}">
              ${status.isQrPlaceholder ? "QR: Platzhalter-SVG aktiv" : "QR: Produktiv-SVG aktiv"}
            </span>
          </div>
        </div>

        <div class="hud-markers-strip">
          ${markersHtml}
        </div>

        <div class="hud-notes-body" id="hudNotesBody">
          <div class="hud-note-main">
            <span class="hud-note-beat" id="hudNoteBeat">Beat 1</span>
            <p class="hud-note-sentence" id="hudNoteSentence"></p>
          </div>
          <div class="hud-note-meta">
            <div><strong>Nächster Impuls:</strong> <span id="hudNoteTransition"></span></div>
            <div><strong>Abgrenzung / Hinweis:</strong> <span id="hudNoteLimitation"></span></div>
            <div><strong>Quelle / Aussprache:</strong> <span id="hudNoteSource"></span></div>
          </div>
        </div>
      </aside>

      <!-- Hidden Evidence & Source Registry Appendix (Reachable only in Rehearsal/Review Mode via E) -->
      <div class="hud-evidence-appendix" id="hudEvidenceAppendix" hidden>
        <div class="hud-appendix-dialog">
          <div class="hud-appendix-head">
            <div>
              <span class="fm-eyebrow">EVIDENZ- &amp; QUELLENREGISTER (NICHT TEIL DER 5-MIN-AUFNAHME)</span>
              <h3>Autorisierte quantitative Quellen &amp; Methodische Abgrenzung</h3>
            </div>
            <button type="button" data-action="close-appendix" class="hud-appendix-close">Schließen (Esc)</button>
          </div>
          <div class="hud-appendix-grid">
            ${appendixRowsHtml}
          </div>
        </div>
      </div>
    `;

    this.hudRootEl.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-action]");
      if (!btn) return;
      const action = btn.dataset.action;
      if (action === "prev-scene") this.prevScene();
      else if (action === "next-scene") this.nextScene();
      else if (action === "prev-beat") this.prevBeat();
      else if (action === "next-beat") this.nextBeat();
      else if (action === "restart") this.restartScene();
      else if (action === "pause") this.togglePause();
      else if (action === "notes") this.toggleNotes();
      else if (action === "appendix" || action === "close-appendix") this.toggleAppendix();
    });
  }

  _syncTimerDisplay() {
    const elapsedEl = document.getElementById("hudClockElapsed");
    const targetEl = document.getElementById("hudClockTarget");
    const deltaEl = document.getElementById("hudClockDelta");
    if (!elapsedEl || !targetEl || !deltaEl) return;

    const marker = SCENE_TIMING_MARKERS[this.sceneIndex] || SCENE_TIMING_MARKERS[0];
    const currentScene = this.scenes[this.sceneIndex];
    const beatFraction = (this.beatIndex + 1) / currentScene.beatCount;
    const expectedSec = marker.startSec + (marker.endSec - marker.startSec) * beatFraction;
    const diff = this.elapsedSec - expectedSec;

    elapsedEl.textContent = formatTime(this.elapsedSec);
    targetEl.textContent = `${marker.markerLabel} (Beat ${formatTime(expectedSec)})`;
    deltaEl.textContent = formatDelta(diff);
    deltaEl.dataset.drift = Math.abs(diff) <= 5 ? "on-time" : diff > 5 ? "behind" : "ahead";
  }

  _syncHud() {
    if (!this.hudRootEl) return;
    const isRec = this.mode === "recording";
    this.hudRootEl.hidden = isRec;
    if (isRec) return;

    const currentScene = this.scenes[this.sceneIndex];
    const counterEl = document.getElementById("hudSceneCounter");
    if (counterEl) {
      counterEl.textContent = `Szene ${this.sceneIndex + 1}/${this.scenes.length} (${currentScene.title}) · Beat ${this.beatIndex + 1}/${currentScene.beatCount}`;
    }

    const pauseBtn = document.getElementById("hudPauseBtn");
    if (pauseBtn) {
      pauseBtn.textContent = this.paused ? "▶ P" : "⏸ P";
    }

    const controlsBar = document.getElementById("hudControlsBar");
    if (controlsBar) controlsBar.hidden = !this.showControls;

    const rehPanel = document.getElementById("hudRehearsalPanel");
    if (rehPanel) rehPanel.hidden = !(this.showTimer || this.showNotes);

    const notesBody = document.getElementById("hudNotesBody");
    if (notesBody) notesBody.hidden = !this.showNotes;

    const appendix = document.getElementById("hudEvidenceAppendix");
    if (appendix) appendix.hidden = !this.showAppendix;

    // Highlight current scene timing chip
    this.hudRootEl.querySelectorAll(".hud-marker-chip").forEach((chip) => {
      const idx = Number(chip.dataset.markerScene);
      chip.classList.toggle("is-current", idx === this.sceneIndex);
      chip.classList.toggle("is-past", idx < this.sceneIndex);
    });

    // Populate current beat speaker note
    const sceneNote = speakerNotes[currentScene.id];
    const beatNote = sceneNote?.beats?.[this.beatIndex] || sceneNote?.beats?.[0];
    if (beatNote) {
      const beatEl = document.getElementById("hudNoteBeat");
      const sentEl = document.getElementById("hudNoteSentence");
      const transEl = document.getElementById("hudNoteTransition");
      const limEl = document.getElementById("hudNoteLimitation");
      const srcEl = document.getElementById("hudNoteSource");
      if (beatEl) beatEl.textContent = `${beatNote.label} · Ziel: ${beatNote.targetTime}`;
      if (sentEl) sentEl.textContent = beatNote.keySentence;
      if (transEl) transEl.textContent = beatNote.transition;
      if (limEl) limEl.textContent = beatNote.limitation;
      if (srcEl) {
        srcEl.textContent = `${beatNote.sourceReminder}${
          beatNote.pronunciation && beatNote.pronunciation !== "—" ? ` · ${beatNote.pronunciation}` : ""
        }`;
      }
    }

    this._syncTimerDisplay();
  }
}
