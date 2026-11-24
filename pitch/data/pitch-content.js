/*
 * Focus Mode+ Pitch · central content configuration.
 *
 * Every research value, validation value, business-model value and the QR asset path lives here.
 * Scenes read from window.PITCH_CONTENT only; no scene file hardcodes these values.
 *
 * Placeholders
 * - A value written as "[SOME_TOKEN]" counts as unresolved. Unresolved values are never rendered on
 *   stage in any mode: the scene hides the element or shows the approved neutral fallback copy below.
 * - Rehearsal mode lists every unresolved value in the presenter overlay.
 *
 * To fill in real validation data, replace the bracket token with the real value, e.g.
 *   interviewCount: "12",
 *   pilotSignups: "31",
 *   validationQuote: "Ich wusste endlich, womit ich anfangen soll.",
 *   validationQuoteAuthor: "Studentin, 2. Semester",
 * Only use values that were actually collected.
 */
(function (global) {
  'use strict';

  global.PITCH_CONTENT = Object.freeze({
    /* ------------------------------------------------------------ Scene 2 · Problem */
    problemStatistic: '44 %',
    problemStatisticValue: 0.44, // Drives the ten learner markers (4 full, the fifth 40 % filled).
    problemStatisticLabel: 'werden bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt',
    problemStatisticSource: 'JIM-Studie 2025 · 12–19 Jahre · n = 1.200',
    problemStatisticQualifier: 'Selbstauskunft',

    /* ------------------------------------------------------------ Scene 3 · Why now */
    aiDailyStatistic: '46,5 %',
    aiDailyLabel: 'nutzen KI im Studium täglich',
    aiWeeklyStatistic: '81 %',
    aiWeeklyLabel: 'nutzen KI mindestens wöchentlich',
    aiStatisticSource: 'CHE DatenCHECK 9/2026 · 28.584 gültige Angaben',
    aiStatisticQualifier: 'v. a. Wirtschafts-, Rechts- und verwandte Fächer',

    /* ------------------------------------------------------------ Scene 6 · Market */
    targetGroup: 'Studierende · 18+ · Android',
    addressableSegment: '2,9 Mio.',
    addressableSegmentLabel: 'Studierende in Deutschland',
    addressableSegmentSource: 'Destatis · Wintersemester 2024/25',
    addressableSegmentQualifier: 'Breites Segment, keine Nutzerprognose',

    comparison: [
      { tools: 'ChatGPT / Gemini', category: 'Planung', icon: 'spark' },
      { tools: 'Todoist / Notion', category: 'Organisation', icon: 'check' },
      { tools: 'Forest / Opal', category: 'Fokus', icon: 'timer' }
    ],
    comparisonSelf: { tools: 'Focus Mode+', category: 'durchgängiger Workflow' },
    comparisonStatement:
      'Unter den verglichenen Lösungen verbindet keine KI-gestützte Zielplanung direkt mit geschützten Fokus-Sessions und Feedback in einem durchgängigen Workflow.',

    freeDefinition: ['Quick Focus', 'einfache Missions', 'lokaler Fortschritt'],
    proDefinition: ['komplexe Projekte', 'mehr Material', 'langfristige Planung', 'erweiterte Anpassung'],
    priceHypotheses: ['3,99 €', '5,99 €', '7,99 €'],
    priceHypothesesLabel: 'Preishypothesen',
    priceHypothesesUnit: 'pro Monat · noch nicht validiert',

    /* ------------------------------------------------------------ Scene 5 · Progress */
    progressRoute: [
      { label: 'Schulprojekt', note: 'Ausgangspunkt', state: 'done' },
      { label: 'PWA', note: 'offline-fähig', state: 'done' },
      { label: 'AI Missions', note: 'Ziel wird zu Schritten', state: 'done' },
      { label: 'Android Foundation', note: 'Kotlin · Compose', state: 'done' },
      { label: 'Pilot', note: 'als Nächstes', state: 'next' }
    ],
    // Only repository-supported progress (PWA runtime and android/ module, v18.1).
    built: {
      pwa: ['funktionierende PWA', 'KI-generierte Missions', 'Quick Focus', 'Fokus-Sessions', 'Session-Verlauf', 'Session-Wiederherstellung'],
      android: ['native Android-Basis', 'Auswahl installierter Apps', 'Benachrichtigungen & Berechtigungen']
    },
    // Verified from the repository history (git log): first commit 8 Oct 2026, v18.1 on 21 Nov 2026.
    buildFact: '81 Commits · 8. Okt. – 21. Nov. 2026',
    toProve: [
      'wiederkehrende Nutzung',
      'mehr abgeschlossene Fokus-Schritte',
      'Nutzen des Fokus-Schutzes',
      'Nutzen der KI-Missionspläne',
      'Rückkehr an Tag 7',
      'Zahlungsbereitschaft'
    ],

    /* ------------------------------------------------------------ Validation placeholders */
    interviewCount: '[INTERVIEW_COUNT]',
    pilotSignups: '[PILOT_SIGNUPS]',
    validationQuote: '[KEY_VALIDATION_QUOTE]',
    validationQuoteAuthor: '', // Optional, e.g. "Studentin, 2. Semester". Empty = no attribution line.
    // Neutral copy shown while the values above are unresolved.
    validationFallback: ['Pilot vorbereitet', 'Messbare Hypothesen definiert'],

    /* ------------------------------------------------------------ Scene 7 · Pilot */
    pilotSize: '20',
    pilotSizeLabel: 'Android-Testnutzer',
    pilotDuration: '14 Tage',
    pilotRoute: ['Mission erstellt', 'erste Session', '3 Sessions', 'Rückkehr Tag 7', 'Interview'],
    activationTarget: '60 %',
    activationLabel: 'erste Mission',
    threeSessionTarget: '40 %',
    threeSessionLabel: 'mindestens 3 Sessions',
    daySevenTarget: '30 %',
    daySevenLabel: 'Rückkehr an Tag 7',
    followUpInterviews: '5–8',
    followUpLabel: 'Interviews',
    pilotTargetsLabel: 'Interne Pilotziele, keine Branchenbenchmarks',

    /*
     * QR slot. The stage always shows this one file at 300 × 300 px (1920 × 1080 stage).
     * The repository ships pilot-qr.svg as a copy of pilot-qr-placeholder.svg (not scannable).
     * To go live, overwrite pitch/assets/pilot-qr.svg with the final square QR; nothing else changes.
     */
    qrAsset: './assets/pilot-qr.svg',
    qrPlaceholderAsset: './assets/pilot-qr-placeholder.svg',
    qrCaption: 'Pilot-Link per QR',

    /* ------------------------------------------------------------ Camera-safe area (stage px) */
    cameraSafeArea: { corner: 'top-right', width: 320, height: 220 }
  });
})(window);
