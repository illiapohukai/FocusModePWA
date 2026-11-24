/*
 * Focus Mode+ Pitch · source registry.
 *
 * Compact source lines appear on stage next to each claim (see pitch-content.js). This registry
 * holds the full meaning and limitations of each approved figure. It feeds the evidence appendix
 * (key E), which exists only in rehearsal and review mode and is never part of the recording.
 */
(function (global) {
  'use strict';

  global.PITCH_SOURCES = Object.freeze([
    {
      id: 'jim-2025',
      title: 'JIM-Studie 2025',
      scene: 2,
      figures: ['44 % werden bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt'],
      population: 'Jugendliche von 12 bis 19 Jahren',
      sample: 'n = 1.200',
      meaning: 'Selbstauskunft: regelmäßige Ablenkung durch das Smartphone bei den Hausaufgaben.',
      limitations: [
        'Selbstauskunft, keine Messung des Verhaltens',
        'Altersgruppe 12–19, nicht die Pilot-Zielgruppe (Studierende 18+)',
        'kein Nachweis von Sucht, schlechteren Noten oder kausalem Schaden'
      ]
    },
    {
      id: 'che-datencheck-9-2026',
      title: 'CHE DatenCHECK 9/2026',
      scene: 3,
      figures: ['46,5 % nutzen KI im Studium täglich', '81 % nutzen KI mindestens wöchentlich'],
      population: 'Studierende',
      sample: '28.584 gültige Angaben',
      meaning: 'Häufigkeit der KI-Nutzung im Studium.',
      limitations: [
        'Stichprobe vor allem aus Wirtschafts-, Rechts- und verwandten Fächern',
        'nicht für alle Studierenden in Deutschland repräsentativ'
      ]
    },
    {
      id: 'destatis-ws-2024-25',
      title: 'Destatis · Wintersemester 2024/25',
      scene: 6,
      figures: ['rund 2,9 Mio. Studierende in Deutschland'],
      population: 'Studierende an deutschen Hochschulen',
      sample: 'amtliche Statistik',
      meaning: 'Breite Segmentgröße als Kontext für die erste Zielgruppe.',
      limitations: [
        'keine erreichbaren Nutzer und keine Kundenprognose',
        'keine Umsatz- oder Zahlungsbereitschaftsschätzung',
        'erste Zielgruppe ist enger: Studierende · 18+ · Android'
      ]
    }
  ]);
})(window);
