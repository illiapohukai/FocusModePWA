/**
 * Central Content & Research Configuration — Focus Mode+ Five-Minute Pitch Experience
 * Edit all statistics, validation tokens, business model hypotheses, and QR path here.
 */

export const pitchData = {
  // Scene 2 — Problem Evidence
  problemStatistic: "44 %",
  problemStatisticLabel:
    "werden bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt",
  problemStatisticSource:
    "JIM-Studie 2025 · 12–19 Jahre · n = 1.200",
  problemLimitationNote:
    "Selbstauskunft (12–19 J.) zu regelmäßiger Ablenkung bei Hausaufgaben; kein Kausalnachweis für Noten oder Sucht.",

  // Scene 3 — Why-Now Evidence
  aiDailyStatistic: "46,5 %",
  aiDailyLabel:
    "nutzen KI im Studium täglich",
  aiWeeklyStatistic: "81 %",
  aiWeeklyLabel:
    "mindestens wöchentlich",
  aiStatisticSource:
    "CHE DatenCHECK 9/2026 · 28.584 gültige Angaben",
  aiLimitationNote:
    "Stichprobe schwerpunktmäßig Wirtschafts-, Rechts- und verwandte Fächer; nicht vollrepräsentativ für alle Studiengänge.",

  // Scene 6 — Market Context & Target Segment
  targetGroup:
    "Studierende · 18+ · Android",
  addressableSegment:
    "2,9 Mio.",
  addressableSegmentLabel:
    "Studierende in Deutschland",
  addressableSegmentSource:
    "Destatis · Wintersemester 2024/25",
  addressableSegmentContext:
    "Breites Adresssegment zur Einordnung der ersten Zielgruppe — keine Nutzer- oder Umsatzprognose.",

  // Scene 5 — Validation Placeholders (auto-collapse to approved neutral copy if unresolved)
  interviewCount:
    "[INTERVIEW_COUNT]",
  pilotSignups:
    "[PILOT_SIGNUPS]",
  validationQuote:
    "[KEY_VALIDATION_QUOTE]",
  validationFallbackPrimary:
    "Pilot vorbereitet",
  validationFallbackSecondary:
    "Messbare Hypothesen definiert",

  // Scene 6 — Competitor Category Comparison & Defensible Positioning
  competitorCategories: [
    {
      tools: "ChatGPT / Gemini",
      domain: "Planung",
      detail: "Generiert Lernpläne, endet vor der konkreten Ausführung"
    },
    {
      tools: "Todoist / Notion",
      domain: "Organisation",
      detail: "Verwaltet Aufgabenlisten ohne geschützte Fokus-Ausführung"
    },
    {
      tools: "Forest / Opal",
      domain: "Fokus",
      detail: "Schützt einzelne Sessions ohne Anbindung an den Lernplan"
    },
    {
      tools: "Focus Mode+",
      domain: "durchgängiger Workflow",
      detail: "Ziel → Mission → nächster Schritt → geschützter Fokus → Ergebnis → Anpassung",
      isPrimary: true
    }
  ],
  defensiblePositioning:
    "Unter den verglichenen Lösungen verbindet keine KI-gestützte Zielplanung direkt mit geschützten Fokus-Sessions und Feedback in einem durchgängigen Workflow.",

  // Scene 6 — Business Model Surfaces
  freeDefinition: [
    "Quick Focus",
    "einfache Missions",
    "lokaler Fortschritt"
  ],

  proDefinition: [
    "komplexe Projekte",
    "mehr Material",
    "langfristige Planung",
    "erweiterte Anpassung"
  ],

  priceHypothesesLabel: "Preishypothesen",
  priceHypotheses: [
    "3,99 €",
    "5,99 €",
    "7,99 €"
  ],

  // Scene 7 — Pilot & Final CTA
  pilotSize:
    "20",
  pilotUserLabel:
    "20 Android-Testnutzer",
  pilotDuration:
    "14 Tage",
  activationTarget:
    "60 %",
  threeSessionTarget:
    "40 %",
  daySevenTarget:
    "30 %",
  followUpInterviews:
    "5–8",
  pilotBenchmarkDisclaimer:
    "Interne Pilotziele, keine Branchenbenchmarks",

  // QR Placeholder & Replacement Contract
  qrPlaceholderToken: "[QR_PLACEHOLDER]",
  qrAsset:
    "./assets/pilot-qr-placeholder.svg",
  qrProductionAsset:
    "./assets/pilot-qr.svg",

  // Approved Astronaut & Operator Assets
  astronautAsset:
    "./assets/operator/orbit-standing.png",
  operatorMetaAsset:
    "./assets/operator/mission-operator.json",

  // Deterministic Product Demo Fixtures (Scene 4)
  demoFixture: {
    goalTitle: "Mathe-Klausur vorbereiten",
    goalSubject: "Mathematik · Prüfung",
    deadlineText: "27. November",
    prepWindow: "12 Tage Vorbereitung · Mo–Fr · 25 Min.",
    materialName: "Übungsblatt_Lineare_Funktionen.pdf",
    materialMeta: "PDF-Arbeitsblatt · 4 Seiten · Gleichungssysteme & Graphen",
    generationStages: [
      "Ziel wird analysiert",
      "Schritte werden strukturiert",
      "Mission ist bereit"
    ],
    routeSteps: [
      {
        index: 1,
        title: "Grundlagen prüfen",
        detail: "Steigung, Achsenabschnitt & Definitionen wiederholen",
        duration: "20 Min.",
        status: "completed"
      },
      {
        index: 2,
        title: "Lineare Funktionen verstehen",
        focusTitle: "Lineare Funktionen üben",
        detail: "Funktionsgleichungen aufstellen & Schnittpunkte berechnen",
        duration: "25:00",
        durationLabel: "25 Min.",
        status: "next"
      },
      {
        index: 3,
        title: "Übungsaufgaben lösen",
        detail: "Aufgabenblock A–C vom Übungsblatt selbstständig rechnen",
        duration: "30 Min.",
        status: "upcoming"
      },
      {
        index: 4,
        title: "Probeklausur bearbeiten",
        detail: "Zeitgesteuerter Durchlauf unter Prüfungsbedingungen",
        duration: "45 Min.",
        status: "upcoming"
      }
    ],
    distractionApps: [
      { id: "social", name: "Kurzvideo-Feed", category: "Social Media", color: "#E11D48", selected: true },
      { id: "chat", name: "Gruppen-Messenger", category: "Nachrichten", color: "#0284C7", selected: true },
      { id: "stream", name: "Video-Streaming", category: "Entertainment", color: "#D97706", selected: true },
      { id: "music", name: "Lern-Playlist", category: "Audio (Erlaubt)", color: "#16A34A", selected: false }
    ],
    protectionCopy: {
      selectHeading: "Ablenkende Apps auswählen",
      prepareStatus: "Fokus-Schutz vorbereiten",
      activeBadge: "Geschützter Fokus"
    },
    resultCopy: {
      completed: "Abgeschlossen",
      partial: "Teilweise abgeschlossen",
      feedbackLine: "Ergebnis fließt in den weiteren Plan ein"
    }
  },

  // Scene 5 — Repository-Supported Progress vs. Open Hypotheses
  progressRoute: [
    { id: "school", label: "Schulprojekt", sub: "Ursprung & Problemfokus", completed: true },
    { id: "pwa", label: "PWA", sub: "Warm-Cream System & Cosmic Core", completed: true },
    { id: "missions", label: "AI Missions", sub: "Ziel → strukturierte Schritte", completed: true },
    { id: "android", label: "Android Foundation", sub: "Native Basis & App-Auswahl", completed: true },
    { id: "pilot", label: "Pilot", sub: "20 Testnutzer · 14 Tage", completed: false, isNext: true }
  ],
  builtItems: [
    { title: "Funktionierende PWA", detail: "Kompletter lokaler Lern- & Fokus-Workflow im Browser" },
    { title: "AI-generierte Missions", detail: "Ziel-, Termin- & Material-Analyse in konkrete Schritte" },
    { title: "Quick Focus & Focus Sessions", detail: "Nahtloser Übergang vom Zeit-Dial in den Cosmic Core" },
    { title: "Session History & Recovery", detail: "Verlauf, Statistiken und exakte Wiederaufnahme nach Reload" },
    { title: "Native Android Foundation", detail: "Echte Auswahl installierter Apps & Berechtigungs-Architektur" }
  ],
  toProveItems: [
    { title: "Wiederkehrende Nutzung", detail: "Kehren Studierende nach dem ersten Plan ohne äußeren Zwang zurück?" },
    { title: "Mehr abgeschlossene Fokus-Schritte", detail: "Steigt die tatsächliche Umsetzungsrate gegenüber offenen To-do-Listen?" },
    { title: "Nutzen des Fokus-Schutzes", detail: "Reduziert die App-Auswahl Unterbrechungen im echten Studienalltag?" },
    { title: "Qualität der AI-Mission-Pläne", detail: "Passen Schrittgrößen und Zeitschätzungen zu realen Klausurphasen?" },
    { title: "Day-7-Retention & Zahlungsbereitschaft", detail: "Erreichen wir ≥ 30 % Rückkehr an Tag 7 und reale Pro-Relevanz?" }
  ]
};

/**
 * Authoritative Source Registry (Section 30)
 * Visible only next to their claims or in the rehearsal/review evidence appendix.
 */
export const sourceRegistry = [
  {
    id: "jim-2025",
    scene: "Szene 2 — Problem",
    title: "JIM-Studie 2025 (Jugend, Information, Medien)",
    publisher: "Medienpädagogischer Forschungsverbund Südwest (mpfs)",
    value: "44 %",
    claim: "werden bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt",
    population: "Jugendliche in Deutschland, 12–19 Jahre",
    sample: "n = 1.200",
    limitation:
      "Selbstauskunft zu regelmäßiger Ablenkung während der Hausaufgaben. Kein Beleg für klinische Sucht, kein Kausalbeweis für schlechtere Noten oder kognitive Schäden."
  },
  {
    id: "che-2026",
    scene: "Szene 3 — Warum jetzt & Produktthese",
    title: "CHE DatenCHECK 9/2026 — KI-Nutzung im Studium",
    publisher: "Centrum für Hochschulentwicklung (CHE)",
    value: "46,5 % täglich · 81 % mindestens wöchentlich",
    claim: "nutzen KI im Studium täglich (81 % mindestens wöchentlich)",
    population: "Studierende an deutschen Hochschulen",
    sample: "28.584 gültige Angaben",
    limitation:
      "Die Befragung umfasst schwerpunktmäßig Wirtschafts-, Rechts- und verwandte Studiengänge und ist nicht exakt repräsentativ für jeden einzelnen Studiengang in Deutschland."
  },
  {
    id: "destatis-2024-25",
    scene: "Szene 6 — Markt & Geschäftsmodell",
    title: "Studierendenstatistik Wintersemester 2024/25",
    publisher: "Statistisches Bundesamt (Destatis)",
    value: "2,9 Mio.",
    claim: "Studierende in Deutschland",
    population: "Immatrikulierte Studierende an deutschen Hochschulen (WS 2024/25)",
    sample: "Amtliche Gesamterhebung (~2,87–2,9 Mio.)",
    limitation:
      "Dient ausschließlich als breiter Adressrahmen zur Einordnung des ersten Zielsegments (Studierende · 18+ · Android). Keine erreichbare Nutzerprognose, kein Marktanteil und keine Umsatz- oder MRR-Hochrechnung."
  }
];

/**
 * Helper to determine if a configuration value is still an unreplaced bracket token.
 */
export function isUnresolvedPlaceholder(value) {
  if (typeof value !== "string") return true;
  const trimmed = value.trim();
  return trimmed.length === 0 || /^\[[A-Z0-9_]+\]$/.test(trimmed);
}

/**
 * Returns a list of unresolved validation/QR placeholders for the rehearsal HUD.
 */
export function getUnresolvedPlaceholders(data = pitchData) {
  const unresolved = [];
  if (isUnresolvedPlaceholder(data.interviewCount)) unresolved.push("INTERVIEW_COUNT");
  if (isUnresolvedPlaceholder(data.pilotSignups)) unresolved.push("PILOT_SIGNUPS");
  if (isUnresolvedPlaceholder(data.validationQuote)) unresolved.push("KEY_VALIDATION_QUOTE");
  const isQrPlaceholder = !data.qrAsset || data.qrAsset.includes("placeholder");
  return {
    validationTokens: unresolved,
    hasUnresolvedValidation: unresolved.length > 0,
    isQrPlaceholder
  };
}
