/**
 * Focus Mode+ Five-Minute Pitch Experience — Bootstrap Entry (pitch/pitch.js)
 * Standalone, zero-framework, offline-capable ES module entrypoint.
 */

import { pitchData } from "./data/pitch-content.js";
import { PresentationController } from "./engine/presentation-controller.js";
import { createScene01Hook } from "./scenes/scene-01-hook.js";
import { createScene02Problem } from "./scenes/scene-02-problem.js";
import { createScene03Thesis } from "./scenes/scene-03-thesis.js";
import { createScene04Demo } from "./scenes/scene-04-demo.js";
import { createScene05Progress } from "./scenes/scene-05-progress.js";
import { createScene06Market } from "./scenes/scene-06-market.js";
import { createScene07Pilot } from "./scenes/scene-07-pilot.js";

function resolveMode() {
  const params = new URLSearchParams(window.location.search);
  const raw = (params.get("mode") || "").toLowerCase();
  if (raw === "recording") return "recording";
  if (raw === "rehearsal") return "rehearsal";
  return "standard";
}

function resolveReducedMotion() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("reducedMotion") === "1" || params.get("reduced") === "1") return true;
  try {
    return Boolean(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  } catch (_) {
    return false;
  }
}

function bootPitchExperience() {
  const stageEl = document.getElementById("pitchStage");
  const hudRootEl = document.getElementById("pitchHudRoot");
  if (!stageEl) return;

  const mode = resolveMode();
  const reducedMotion = resolveReducedMotion();
  const sceneOptions = { mode, reducedMotion };

  const scenes = [
    createScene01Hook(pitchData, sceneOptions),
    createScene02Problem(pitchData, sceneOptions),
    createScene03Thesis(pitchData, sceneOptions),
    createScene04Demo(pitchData, sceneOptions),
    createScene05Progress(pitchData, sceneOptions),
    createScene06Market(pitchData, sceneOptions),
    createScene07Pilot(pitchData, sceneOptions)
  ];

  const controller = new PresentationController({
    stageEl,
    hudRootEl,
    scenes,
    mode,
    reducedMotion
  });

  controller.init();
  window.FocusPitchController = controller;
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", bootPitchExperience, { once: true });
} else {
  bootPitchExperience();
}
