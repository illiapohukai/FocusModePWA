/**
 * Concise German Speaker Notes & Rehearsal Timing Metadata
 * Target spoken duration: 4:40–4:50 (285s target marker at 4:45).
 * Stored separately from scene DOM; rendered only in ?mode=rehearsal.
 */

export const SCENE_TIMING_MARKERS = [
  { sceneIndex: 0, id: "scene-01-hook", title: "1 · Hook", startSec: 0, endSec: 35, markerLabel: "0:35" },
  { sceneIndex: 1, id: "scene-02-problem", title: "2 · Problem", startSec: 35, endSec: 65, markerLabel: "1:05" },
  { sceneIndex: 2, id: "scene-03-thesis", title: "3 · Warum jetzt & These", startSec: 65, endSec: 90, markerLabel: "1:30" },
  { sceneIndex: 3, id: "scene-04-demo", title: "4 · Produkt-Demo", startSec: 90, endSec: 160, markerLabel: "2:40" },
  { sceneIndex: 4, id: "scene-05-progress", title: "5 · Fortschritt & Validierung", startSec: 160, endSec: 210, markerLabel: "3:30" },
  { sceneIndex: 5, id: "scene-06-market", title: "6 · Markt & Geschäftsmodell", startSec: 210, endSec: 250, markerLabel: "4:10" },
  { sceneIndex: 6, id: "scene-07-pilot", title: "7 · Pilot & Finaler CTA", startSec: 250, endSec: 285, markerLabel: "4:45" }
];

export const speakerNotes = {
  "scene-01-hook": {
    sceneTitle: "Szene 1 — Hook: Der Bruch zwischen Plan und Tat",
    targetRange: "0:00 – 0:35 (35 Sek.)",
    targetEndSec: 35,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · Der perfekte KI-Lernplan",
        keySentence: "Wer heute für eine Prüfung lernt, bekommt in Sekunden einen beeindruckenden 20-Schritte-Plan.",
        transition: "Leertaste drücken, um das Auseinanderdriften der Aufgaben zu starten.",
        limitation: "Noch keine Statistik nennen — rein visuell über die Plan-Oberfläche einsteigen.",
        sourceReminder: "Keine externe Quelle in Szene 1.",
        pronunciation: "Focus Mode Plus [ˈfəʊkəs məʊd plʌs]",
        targetTime: "0:12"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · Fragmentierung & Unterbrechung",
        keySentence: "Doch im Alltag fällt dieser Plan auseinander: Der Kalender ist getrennt, eine Push-Nachricht kreuzt den Weg — und die Verbindung bricht ab.",
        transition: "Leertaste für das zentrale Leitmotiv.",
        limitation: "Nicht von ‚Smartphone-Sucht‘ sprechen, sondern vom strukturellen Bruch im Ablauf.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "0:24"
      },
      {
        beatIndex: 2,
        label: "Beat 3 · Kernaussage (PLANUNG | UMSETZUNG)",
        keySentence: "Denn unsere zentrale Erkenntnis lautet: Ein Plan ist noch kein Fortschritt. Zwischen Planung und Umsetzung klafft eine Lücke.",
        transition: "Pfeil rechts / Leertaste zu Szene 2 (Problem & Evidenz).",
        limitation: "Kurze Pause nach ‚Ein Plan ist noch kein Fortschritt‘ lassen.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "0:35"
      }
    ]
  },

  "scene-02-problem": {
    sceneTitle: "Szene 2 — Problem: Ablenkung & Werkzeug-Inselwelt",
    targetRange: "0:35 – 1:05 (30 Sek.)",
    targetEndSec: 65,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · 44 % Ablenkung bei Hausaufgaben",
        keySentence: "Laut JIM-Studie 2025 geben 44 Prozent der 12- bis 19-Jährigen selbst an, bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt zu werden.",
        transition: "Leertaste für die vier isolierten Funktions-Fragmente.",
        limitation: "WICHTIG: Selbstauskunft (12–19 J., n = 1.200). Nicht als Sucht oder direkten Noten-Kausalbeweis formulieren.",
        sourceReminder: "JIM-Studie 2025 · 12–19 Jahre · n = 1.200",
        pronunciation: "JIM-Studie [jɪm ˈʃtuːdi̯ə]",
        targetTime: "0:50"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · Vier getrennte Funktions-Inseln",
        keySentence: "Aber das tiefere Problem liegt im Workflow: KI-Chat, Kalender, To-do-Liste und Fokus-Timer sind vier getrennte Inseln ohne Rückkopplung.",
        transition: "Pfeil rechts / Leertaste zu Szene 3 (Warum jetzt & Produktthese).",
        limitation: "Keine Firmen-Vorwürfe; sachlich die fehlende Brücke zwischen Plan und Timer zeigen.",
        sourceReminder: "JIM-Studie 2025 bleibt unten links lesbar.",
        pronunciation: "Workflow [ˈwɜːkfləʊ]",
        targetTime: "1:05"
      }
    ]
  },

  "scene-03-thesis": {
    sceneTitle: "Szene 3 — Warum jetzt & Produktthese",
    targetRange: "1:05 – 1:30 (25 Sek.)",
    targetEndSec: 90,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · KI-Adoption im Studium (46,5 % / 81 %)",
        keySentence: "Warum genau jetzt? Im aktuellen CHE DatenCHECK nutzen 46,5 Prozent der befragten Studierenden KI bereits täglich — 81 Prozent mindestens wöchentlich.",
        transition: "Leertaste, um die vier Fragmente gravitativ zur Mission-Route zu verbinden.",
        limitation: "Hinweis: CHE-Stichprobe (n = 28.584) deckt vor allem Wirtschafts-, Rechts- und verwandte Fächer ab.",
        sourceReminder: "CHE DatenCHECK 9/2026 · 28.584 gültige Angaben",
        pronunciation: "CHE [tseː-haː-eː]",
        targetTime: "1:18"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · Gravitative Verbindung & Positionierung",
        keySentence: "Planung ist also gelöst — die Umsetzung nicht. Focus Mode+ verbindet Planung und Umsetzung in einem Kreislauf: Ziel, Mission, Fokus, Feedback und Anpassung.",
        transition: "Pfeil rechts / Leertaste zur Produkt-Demonstration (Szene 4).",
        limitation: "Positionierung klar als Verbindung von Planung und Umsetzung sprechen.",
        sourceReminder: "CHE DatenCHECK 9/2026",
        pronunciation: "—",
        targetTime: "1:30"
      }
    ]
  },

  "scene-04-demo": {
    sceneTitle: "Szene 4 — Produkt-Demonstration (8 deterministische Beats)",
    targetRange: "1:30 – 2:40 (70 Sek.)",
    targetEndSec: 160,
    beats: [
      {
        beatIndex: 0,
        label: "Demo Beat 1 · Mission Creation (Ziel, Termin, Material)",
        keySentence: "Sehen wir uns das im Produkt an: Ich gebe mein Ziel ‚Mathe-Klausur vorbereiten‘, den Termin am 27. November und mein Übungsblatt ein.",
        transition: "Leertaste für Beat 2 (AI Mission Generation mit Operator).",
        limitation: "Deterministische Demo-Daten; kein Echtzeit-Tippen nötig.",
        sourceReminder: "PWA-native Komponenten",
        pronunciation: "—",
        targetTime: "1:39"
      },
      {
        beatIndex: 1,
        label: "Demo Beat 2 · AI Mission Generation",
        keySentence: "Während der Operator arbeitet, wird das Ziel analysiert und in ausführbare Fokus-Einheiten strukturiert — bis die Mission bereitsteht.",
        transition: "Leertaste für Beat 3 (Mission Route im Raum).",
        limitation: "Keine internen Backend-Versprechen erfinden; reale Produkt-Zustände nennen.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "1:47"
      },
      {
        beatIndex: 2,
        label: "Demo Beat 3 · Mission Route",
        keySentence: "Statt einer endlosen Checkliste entsteht eine klare Mission-Route: von Grundlagen prüfen über Lineare Funktionen bis zur Probeklausur.",
        transition: "Leertaste für Beat 4 (Nächster konkreter Schritt).",
        limitation: "—",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "1:55"
      },
      {
        beatIndex: 3,
        label: "Demo Beat 4 · Nächster konkreter Schritt (25:00)",
        keySentence: "Der Rest tritt in den Hintergrund. Übrig bleibt nur der nächste konkrete Schritt: ‚Lineare Funktionen üben‘ für 25 Minuten — ohne neues Grübeln.",
        transition: "Leertaste für Beat 5 (Fokus-Schutz vorbereiten).",
        limitation: "Nicht behaupten, jeder KI-Schritt sei automatisch perfekt.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "2:03"
      },
      {
        beatIndex: 4,
        label: "Demo Beat 5 · Fokus-Schutz vorbereiten",
        keySentence: "Vor dem Start wähle ich ablenkende Apps aus, um meinen Fokus-Schutz für diese Sitzung gezielt vorzubereiten.",
        transition: "Leertaste für Beat 6 (Quick Focus Launch → Cosmic Core Transformation).",
        limitation: "WICHTIG: ‚Ablenkende Apps auswählen / Fokus-Schutz vorbereiten / Geschützter Fokus‘ nutzen — niemals ‚100 % unumgehbare Sperre‘.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "2:12"
      },
      {
        beatIndex: 5,
        label: "Demo Beat 6 · Quick Focus Launch → Cosmic Core",
        keySentence: "Beim Start zieht sich die helle Dauer-Umlaufbahn zusammen: Die Fokus-Materie verdichtet sich unter dem verankerten Timer zur dunklen Sphäre und öffnet den Cosmic Core.",
        transition: "Leertaste für Beat 7 (Aktive Fokus-Session).",
        limitation: "Transformation kurz wirken lassen (1,2 Sek.) — der Timer bleibt exakt an Ort und Stelle.",
        sourceReminder: "—",
        pronunciation: "Cosmic Core [ˈkɒzmɪk kɔː]",
        targetTime: "2:23"
      },
      {
        beatIndex: 6,
        label: "Demo Beat 7 · Aktiver Geschützter Fokus",
        keySentence: "In der aktiven Session bleibt die Umgebung ruhig: dunkler Cosmic Core, klarer Timer, aktuelle Aufgabe und aktiver Fokus-Schutz.",
        transition: "Leertaste für Beat 8 (Ergebnis & Rückfluss in den Plan).",
        limitation: "Nur kurz auf dem aktiven Timer verweilen.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "2:31"
      },
      {
        beatIndex: 7,
        label: "Demo Beat 8 · Ergebnis & Anpassung",
        keySentence: "Nach der Einheit zieht sich der Core zum Fortschrittsknoten ‚Abgeschlossen‘ zusammen, das Ergebnis fließt in den weiteren Plan ein und der nächste Schritt wird aktiv.",
        transition: "Pfeil rechts / Leertaste zu Szene 5 (Fortschritt & Validierung).",
        limitation: "Formulierung: ‚Ergebnis fließt in den weiteren Plan ein‘.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "2:40"
      }
    ]
  },

  "scene-05-progress": {
    sceneTitle: "Szene 5 — Produktfortschritt & Validierung",
    targetRange: "2:40 – 3:30 (50 Sek.)",
    targetEndSec: 210,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · Entwicklungsroute & GEBAUT",
        keySentence: "Was während des Programms entstanden ist: Vom Schulprojekt zur funktionierenden PWA mit AI Missions, Quick Focus, Session Recovery und einer nativen Android-Foundation inklusive echter App-Auswahl.",
        transition: "Leertaste für Beat 2 (NOCH ZU BEWEISEN & offene Hypothesen).",
        limitation: "Keinen Play-Store-Release und kein produktionsreifes Hardware-Blocking behaupten.",
        sourceReminder: "Repository-gestützter Stand (v17/v18 Foundation)",
        pronunciation: "—",
        targetTime: "3:05"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · NOCH ZU BEWEISEN (Ehrliche Hypothesen)",
        keySentence: "Genauso transparent trennen wir, was noch zu beweisen ist: wiederkehrende Nutzung, mehr erledigte Fokus-Schritte, der reale Nutzen des Fokus-Schutzes im Alltag und die Day-7-Retention.",
        transition: "Pfeil rechts / Leertaste zu Szene 6 (Markt & Geschäftsmodell).",
        limitation: "Keine erfundenen Interview- oder Retention-Zahlen nennen; der Pilot ist vorbereitet.",
        sourceReminder: "—",
        pronunciation: "Day-Seven-Retention [deɪ ˈsɛvn rɪˈtɛnʃn]",
        targetTime: "3:30"
      }
    ]
  },

  "scene-06-market": {
    sceneTitle: "Szene 6 — Markt, Positionierung & Geschäftsmodell",
    targetRange: "3:30 – 4:10 (40 Sek.)",
    targetEndSec: 250,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · Adresssegment (2,9 Mio.) & Kategorien",
        keySentence: "In Deutschland gibt es laut Destatis rund 2,9 Millionen Studierende als breiten Kontext — wir starten bewusst spitz bei Studierenden ab 18 Jahren auf Android.",
        transition: "Leertaste für Differenzierung & Free/Pro-Hypothesen.",
        limitation: "2,9 Mio. NUR als breiter Kontext, niemals als erreichbare Kunden oder Umsatzprognose!",
        sourceReminder: "Destatis · Wintersemester 2024/25",
        pronunciation: "Destatis [deˈstaːtɪs]",
        targetTime: "3:50"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · Vergleich & Preishypothesen (3,99 € / 5,99 € / 7,99 €)",
        keySentence: "Unter den verglichenen Lösungen verbindet keine KI-Zielplanung direkt mit geschützten Fokus-Sessions und Feedback. Unser Modell kombiniert einen kostenlosen Einstieg mit Pro-Preishypothesen zwischen 3,99 und 7,99 Euro.",
        transition: "Pfeil rechts / Leertaste zur finalen Pilot-Szene (Szene 7).",
        limitation: "Kein MRR-Szenario zeigen; Preise klar als unvalidierte Preishypothesen benennen.",
        sourceReminder: "Destatis · WS 2024/25",
        pronunciation: "—",
        targetTime: "4:10"
      }
    ]
  },

  "scene-07-pilot": {
    sceneTitle: "Szene 7 — 14-Tage Android-Pilot & Finaler CTA",
    targetRange: "4:10 – 4:45 (35 Sek.)",
    targetEndSec: 285,
    beats: [
      {
        beatIndex: 0,
        label: "Beat 1 · Pilot-Design (20 Android-Testnutzer · 14 Tage)",
        keySentence: "Unser nächster Schritt für messbaren Fortschritt ist ein fokussierter 14-Tage-Pilot mit 20 Android-Testnutzern entlang einer klaren Messroute.",
        transition: "Leertaste für die internen Pilotziele und den finalen Abschluss.",
        limitation: "Interne Pilotziele ausdrücklich als eigene Zielwerte, nicht als Branchenbenchmarks einordnen.",
        sourceReminder: "Interne Pilotziele, keine Branchenbenchmarks",
        pronunciation: "—",
        targetTime: "4:27"
      },
      {
        beatIndex: 1,
        label: "Beat 2 · Messziele, QR-Portal & Schlusssatz",
        keySentence: "Wir prüfen 60 Prozent erste Mission, 40 Prozent drei Sessions, 30 Prozent Rückkehr an Tag 7 und 5 bis 8 Interviews. Denn was jetzt zählt, sind nicht die nächsten Features — sondern die ersten echten Nutzer.",
        transition: "Ende des Pitches (4:45). Ruhig in die Kamera blicken.",
        limitation: "QR-Platzhalter steht bereit für den finalen Pilot-Link.",
        sourceReminder: "—",
        pronunciation: "—",
        targetTime: "4:45"
      }
    ]
  }
};
