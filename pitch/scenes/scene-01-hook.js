/**
 * Scene 1 — Hook: "Ein Plan ist noch kein Fortschritt."
 * 3 manually controlled beats:
 *   Beat 0: Impressive structured 18-card AI study plan on warm cream + silent pixel astronaut
 *   Beat 1: Cards drift out of sequence, calendar fragment fails to dock, distraction vector slices route
 *   Beat 2: Tectonic gap opens between PLANUNG and UMSETZUNG; primary statement reveals
 */

import { renderIcon, renderPixelAstronaut } from "../components/product-replicas.js";

const AI_PLAN_TASKS = [
  { num: "01", title: "Kapitel 1–3 zusammenfassen", meta: "Theorie · 45 Min." },
  { num: "02", title: "Definitionen Grenzwerte lernen", meta: "Karteikarten · 30 Min." },
  { num: "03", title: "Ableitungsregeln wiederholen", meta: "Skript S. 14–28" },
  { num: "04", title: "Lineare Gleichungssysteme", meta: "Übungsblatt 2" },
  { num: "05", title: "Matrix-Rang & Determinanten", meta: "Vorlesung 4" },
  { num: "06", title: "Eigenwerte berechnen", meta: "12 Teilaufgaben" },
  { num: "07", title: "Kurvendiskussion komplett", meta: "Aufgabenblock A" },
  { num: "08", title: "Integralrechnung Grundlagen", meta: "Partielle Integration" },
  { num: "09", title: "Substitution & Uneigentliche Integrale", meta: "Skript S. 62" },
  { num: "10", title: "Taylorreihen & Konvergenz", meta: "Übungsblatt 5" },
  { num: "11", title: "Vektorräume & Basiswechsel", meta: "Theorie-Check" },
  { num: "12", title: "Lineare Abbildungen prüfen", meta: "Kern & Bild" },
  { num: "13", title: "Differentialgleichungen 1. Ordnung", meta: "Übungsblatt 7" },
  { num: "14", title: "Altklausur WS 2023 durchrechnen", meta: "90 Min. Simulation" },
  { num: "15", title: "Fehleranalyse Altklausur", meta: "Schwachstellen-Liste" },
  { num: "16", title: "Formelsammlung komprimieren", meta: "2 Seiten A4" },
  { num: "17", title: "Altklausur SS 2024 lösen", meta: "90 Min. Simulation" },
  { num: "18", title: "Generalprobe vor der Klausur", meta: "Letzter Check" }
];

// Deterministic drift vectors for Beat 1 & Beat 2 (x, y, deg, dropIntoRift)
const DRIFT_VECTORS = [
  [-28, -18, -3.5, false], [18, -32, 4.2, false], [54, -22, 6.5, false],
  [-42, 12, -5.0, false], [24, 38, 11.0, true], [86, -14, -7.5, false],
  [-18, 44, -8.2, true], [46, 52, 14.0, true], [112, 18, 8.4, false],
  [-64, 28, -6.1, false], [12, 74, -15.0, true], [92, 48, 12.2, true],
  [-36, 62, 5.4, false], [34, 96, -18.0, true], [128, 36, 9.5, false],
  [-52, 88, -9.0, false], [64, 110, 16.5, true], [140, 74, -11.0, false]
];

export function createScene01Hook(data) {
  const cardsHtml = AI_PLAN_TASKS.map((t, idx) => {
    const [dx, dy, rot, inRift] = DRIFT_VECTORS[idx];
    return `
      <div
        class="s1-task-card ${inRift ? "is-rift-card" : ""}"
        style="--dx:${dx}px; --dy:${dy}px; --rot:${rot}deg; --stagger:${idx * 24}ms;"
      >
        <span class="s1-task-num">${t.num}</span>
        <div class="s1-task-copy">
          <strong>${t.title}</strong>
          <small>${t.meta}</small>
        </div>
        <span class="s1-task-status"></span>
      </div>
    `;
  }).join("");

  return {
    id: "scene-01-hook",
    title: "1 · Hook",
    beatCount: 3,
    theme: "cream",
    render() {
      return `
        <section class="pitch-scene scene-01" id="scene-01-hook" data-beat="0" aria-label="Szene 1: Hook">
          <div class="s1-stage">
            <!-- Top Editorial Kicker (Left-aligned, clear of top-right 320x220 camera safe zone) -->
            <header class="s1-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>FOCUS MODE+ · AUSGANGSPUNKT</span>
              </div>
              <div class="s1-plan-banner" id="s1PlanBanner">
                <div class="s1-plan-banner-left">
                  ${renderIcon("sparkles")}
                  <span>KI-generierter Lernplan · 18 Lernschritte erstellt</span>
                </div>
                <span class="s1-plan-banner-tag">Strukturiert in 4 Sekunden</span>
              </div>
            </header>

            <!-- Large AI-Generated Planning Surface (18 task fragments) -->
            <div class="s1-plan-field" id="s1PlanField">
              <!-- Tectonic Rift Bridge: PLANUNG | UMSETZUNG -->
              <div class="s1-rift-zone" aria-hidden="true">
                <div class="s1-rift-side s1-rift-left">
                  <span class="s1-rift-tag">PLANUNG</span>
                  <small>18 Schritte generiert · 0 gestartet</small>
                </div>
                <div class="s1-rift-chasm">
                  <div class="s1-rift-line"></div>
                  <span class="s1-rift-label">KEINE VERBINDUNG</span>
                </div>
                <div class="s1-rift-side s1-rift-right">
                  <span class="s1-rift-tag">UMSETZUNG</span>
                  <small>Nächster konkreter Fokus-Schritt fehlt</small>
                </div>
              </div>

              <!-- 18 AI Task Cards Grid -->
              <div class="s1-task-grid">
                ${cardsHtml}
              </div>

              <!-- Disconnected Calendar Fragment (Beat 1) -->
              <div class="s1-floating-calendar" aria-hidden="true">
                <div class="s1-cal-icon">${renderIcon("calendar")}</div>
                <div class="s1-cal-body">
                  <strong>Klausur · 27. Nov</strong>
                  <small>Kalender-Eintrag ohne Rückkopplung</small>
                </div>
                <span class="s1-cal-warn">Getrennt</span>
              </div>

              <!-- Distracting Smartphone Interruption Vector (Beat 1) -->
              <div class="s1-interruption-vector" aria-hidden="true">
                <div class="s1-interrupt-slash"></div>
                <div class="s1-interrupt-card">
                  <span class="s1-interrupt-icon">${renderIcon("smartphone")}</span>
                  <div class="s1-interrupt-text">
                    <strong>Push-Mitteilung · Kurzvideo-Feed</strong>
                    <small>„Nur kurz 2 Minuten Pause …“ → Fokus-Abbruch</small>
                  </div>
                </div>
              </div>

              <!-- Silent Supporting Pixel Astronaut at the edge of the broken route -->
              <div class="s1-astronaut-anchor">
                ${renderPixelAstronaut({
                  src: data.astronautAsset,
                  id: "s1Astronaut",
                  variant: "hook-edge"
                })}
                <div class="s1-route-stub" aria-hidden="true">
                  <span class="s1-route-node is-broken"></span>
                  <span class="s1-route-dash"></span>
                </div>
              </div>
            </div>

            <!-- Beat 2 Reveal: Primary Statement -->
            <footer class="s1-statement-reveal" id="s1StatementReveal">
              <h1 class="s1-headline">Ein Plan ist noch kein Fortschritt.</h1>
              <div class="s1-subbar">
                <span class="s1-pill-side">PLANUNG</span>
                <span class="s1-gap-indicator" aria-hidden="true">
                  <i></i><span>DER BRUCH IM ALLTAG</span><i></i>
                </span>
                <span class="s1-pill-side is-target">UMSETZUNG</span>
              </div>
            </footer>
          </div>
        </section>
      `;
    },
    onBeat(el, beatIndex) {
      el.dataset.beat = String(beatIndex);
    }
  };
}
