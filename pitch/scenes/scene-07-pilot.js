/**
 * Scene 7 — Pilot & Final CTA: "Nicht die nächsten Features. Die ersten echten Nutzer."
 * 2 manually controlled beats:
 *   Beat 0: 20 Android-Testnutzer · 14 Tage + 5-Stage Measurement Route (Mission erstellt → erste Session → 3 Sessions → Rückkehr Tag 7 → Interview)
 *   Beat 1: Internal Pilot Targets (60% / 40% / 30% / 5–8 Interviews) + Cosmic Core Portal framing the exact-size QR placeholder + Final Statement
 */

import { PitchCosmicCore } from "../components/focus-core.js";
import { renderIcon, renderPixelAstronaut } from "../components/product-replicas.js";

export function createScene07Pilot(data, options = {}) {
  let portalCore = null;

  const measurementSteps = [
    { step: "01", title: "Mission erstellt", metric: `${data.activationTarget} erste Mission` },
    { step: "02", title: "erste Session", metric: "Start der Umsetzung" },
    { step: "03", title: "3 Sessions", metric: `${data.threeSessionTarget} mindestens 3 Sessions` },
    { step: "04", title: "Rückkehr Tag 7", metric: `${data.daySevenTarget} Rückkehr an Tag 7` },
    { step: "05", title: "Interview", metric: `${data.followUpInterviews} Interviews` }
  ];

  const routeHtml = measurementSteps
    .map(
      (m, idx) => `
      <div class="s7-mroute-step" style="--m-idx:${idx}">
        <div class="s7-mroute-node">
          <span class="s7-mroute-num">${m.step}</span>
        </div>
        <div class="s7-mroute-copy">
          <strong class="s7-mroute-title">${m.title}</strong>
          <span class="s7-mroute-target">${m.metric}</span>
        </div>
        ${idx < measurementSteps.length - 1 ? `<span class="s7-mroute-arrow" aria-hidden="true">→</span>` : ""}
      </div>
    `
    )
    .join("");

  return {
    id: "scene-07-pilot",
    title: "7 · Pilot & Finaler CTA",
    beatCount: 2,
    theme: "cosmic",
    render() {
      return `
        <section class="pitch-scene scene-07" id="scene-07-pilot" data-beat="0" aria-label="Szene 7: Pilot und Finaler CTA">
          <div class="s7-stage">
            <!-- Header (Left-aligned, strictly clear of top-right 320x220 camera bubble zone) -->
            <header class="s7-header">
              <div class="fm-eyebrow is-cosmic">
                <span class="fm-eyebrow-dot"></span>
                <span>NÄCHSTER SCHRITT FÜR MESSBAREN FORTSCHRITT · ANDROID-PILOT</span>
              </div>
              <div class="s7-pilot-hero-pills">
                <div class="s7-hero-pill is-primary">
                  <strong>${data.pilotUserLabel}</strong>
                </div>
                <div class="s7-hero-pill is-secondary">
                  <strong>${data.pilotDuration}</strong>
                </div>
                <span class="s7-hero-context">Fokussierter Praxistest im echten Studienalltag</span>
              </div>
            </header>

            <!-- 5-Stage Measurement Route -->
            <div class="s7-measurement-band" aria-label="Messroute des Piloten">
              <div class="s7-mroute-track">
                ${routeHtml}
              </div>
            </div>

            <!-- Lower Main Split: Internal Pilot Targets (Left) + Cosmic Core QR Portal & Astronaut (Right) -->
            <div class="s7-main-grid">
              <!-- Left Side: Internal Pilot Targets & Final Statement -->
              <div class="s7-left-panel">
                <div class="s7-targets-card" id="s7TargetsCard">
                  <div class="s7-targets-head">
                    <span class="s7-targets-kicker">${renderIcon("target")} MESSBARE HYPOTHESEN</span>
                    <span class="s7-targets-disclaimer">${data.pilotBenchmarkDisclaimer}</span>
                  </div>

                  <div class="s7-targets-grid">
                    <div class="s7-target-box">
                      <strong>${data.activationTarget}</strong>
                      <span>erste Mission</span>
                    </div>
                    <div class="s7-target-box">
                      <strong>${data.threeSessionTarget}</strong>
                      <span>mindestens 3 Sessions</span>
                    </div>
                    <div class="s7-target-box">
                      <strong>${data.daySevenTarget}</strong>
                      <span>Rückkehr an Tag 7</span>
                    </div>
                    <div class="s7-target-box">
                      <strong>${data.followUpInterviews}</strong>
                      <span>Interviews</span>
                    </div>
                  </div>
                </div>

                <!-- Final Closing Thesis -->
                <div class="s7-closing-statement" id="s7ClosingStatement">
                  <p class="s7-closing-line-1">Nicht die nächsten Features.</p>
                  <h2 class="s7-closing-line-2">Die ersten echten Nutzer.</h2>
                </div>
              </div>

              <!-- Right Side: Cosmic Core Portal framing Exact-Size QR Placeholder + Supporting Astronaut -->
              <div class="s7-portal-stage" id="s7PortalStage">
                <canvas id="s7PortalCanvas" class="s7-portal-canvas" aria-hidden="true"></canvas>

                <!-- Exact-Size Replaceable QR Slot (240x240px 1:1) -->
                <div class="s7-qr-portal-frame" id="s7QrPortalFrame" data-qr-slot="pilot-qr">
                  <img
                    src="${data.qrAsset}"
                    alt="QR — Pilot-Link folgt"
                    width="240"
                    height="240"
                    class="s7-qr-image"
                    id="s7QrImage"
                  />
                </div>

                <!-- Secondary Supporting Pixel Astronaut beside the Portal -->
                <div class="s7-astronaut-anchor">
                  ${renderPixelAstronaut({
                    src: data.astronautAsset,
                    id: "s7Astronaut",
                    variant: "portal-observer"
                  })}
                </div>
              </div>
            </div>
          </div>
        </section>
      `;
    },

    onMount(el) {
      const canvas = el.querySelector("#s7PortalCanvas");
      if (canvas) {
        portalCore = new PitchCosmicCore(canvas, {
          mode: "portal",
          reducedMotion: options.reducedMotion
        });
      }
    },

    onBeat(el, beatIndex) {
      el.dataset.beat = String(beatIndex);
      if (portalCore) {
        portalCore.measure();
        portalCore.start();
      }
    },

    onPause() {
      if (portalCore) portalCore.pause();
    },

    onResume() {
      if (portalCore) portalCore.start();
    },

    onExit() {
      if (portalCore) portalCore.pause();
    }
  };
}
