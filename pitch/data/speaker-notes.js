/*
 * Focus Mode+ Pitch · speaker notes (German).
 *
 * Shown only in rehearsal mode (key N). Never rendered in recording mode.
 * Keys match the scene ids and beat ids in scenes/*.js.
 *
 * Per scene: end = cumulative target time at the end of the scene (rehearsal markers).
 * Per beat:  say  = the spoken line (short, natural German)
 *            cue  = the key sentence to land, shown large in the overlay
 *            note = factual limitation, source reminder or pronunciation (optional)
 *
 * Total spoken text is about 520 words. At a calm pitch pace plus the animation pauses this lands
 * at roughly 4:40–4:50. Rehearse with ?mode=rehearsal and adjust the pauses, not the facts.
 */
(function (global) {
  'use strict';

  global.PITCH_NOTES = Object.freeze({
    hook: {
      end: 35,
      beats: {
        plan: {
          cue: 'Ein KI-Plan sieht sofort beeindruckend aus.',
          say: 'Das ist ein Lernplan, wie ihn eine KI in wenigen Sekunden schreibt: achtzehn Schritte, sauber gegliedert, bis zur Klausur.',
          note: 'Ruhig starten. Kein Produktname, keine Zahl.'
        },
        drift: {
          cue: 'Dann beginnt der Alltag.',
          say: 'Dann beginnt der Alltag. Ein Termin passt nicht in den Kalender, eine Nachricht kommt dazwischen – und die Schritte lösen sich voneinander.',
          note: 'Die Animation läuft ca. 4 s. Während der Bewegung sprechen.'
        },
        reveal: {
          cue: 'Ein Plan ist noch kein Fortschritt.',
          say: 'Ein Plan ist noch kein Fortschritt. Zwischen Planung und Umsetzung liegt eine Lücke. Genau dort setzt Focus Mode+ an.',
          note: 'Kernsatz langsam. Nach „Fortschritt“ eine Sekunde Pause.'
        }
      }
    },

    problem: {
      end: 65,
      beats: {
        stat: {
          cue: '44 % – regelmäßig abgelenkt bei den Hausaufgaben.',
          say: 'Laut JIM-Studie 2025 werden 44 Prozent der Zwölf- bis Neunzehnjährigen bei den Hausaufgaben regelmäßig vom Smartphone abgelenkt – nach eigener Angabe.',
          note: 'Quelle: JIM-Studie 2025, 12–19 Jahre, n = 1.200, Selbstauskunft. Nicht „süchtig“, nicht „schlechtere Noten“. Aussprache: „Dschimm-Studie“.'
        },
        fragments: {
          cue: 'Vier Werkzeuge, kein gemeinsamer Plan.',
          say: 'Und geplant wird in getrennten Werkzeugen: KI-Chat, Kalender, To-do-Liste, Fokus-Timer. Den nächsten Schritt muss man jedes Mal selbst wählen, und kein Ergebnis fließt zurück in den Plan.',
          note: 'Keine Produktnamen abwerten. Es geht um die Lücke zwischen den Werkzeugen.'
        }
      }
    },

    thesis: {
      end: 90,
      beats: {
        stat: {
          cue: 'KI-Planung ist im Studium Alltag.',
          say: 'Gleichzeitig ist KI im Studium Alltag: 46,5 Prozent nutzen sie täglich, 81 Prozent mindestens wöchentlich – so der CHE DatenCHECK, vor allem aus Wirtschaft und Recht.',
          note: 'Quelle: CHE DatenCHECK 9/2026, 28.584 gültige Angaben. Nicht repräsentativ für alle Studierenden. Aussprache: „Ce-Ha-E“.'
        },
        converge: {
          cue: 'Was fehlt, ist die Verbindung.',
          say: 'Geplant wird also schon. Was fehlt, ist die Verbindung.',
          note: 'Pause, während sich die Teile zur Route verbinden.'
        },
        thesis: {
          cue: 'Focus Mode+ verbindet Planung und Umsetzung.',
          say: 'Focus Mode+ verbindet Planung und Umsetzung: vom Ziel über die Mission zum Fokus – und das Ergebnis passt den Plan an.',
          note: 'Positionierungssatz wörtlich sagen.'
        }
      }
    },

    demo: {
      end: 160,
      beats: {
        create: {
          cue: 'Ziel, Deadline, Material.',
          say: 'So sieht das aus. Ich gebe mein Ziel ein: Mathe-Klausur vorbereiten, Deadline 27. November, dazu mein Arbeitsblatt.',
          note: 'Demo-Daten, kein echter Account.'
        },
        generate: {
          cue: 'Aus dem Ziel wird eine Mission.',
          say: 'Focus Mode+ strukturiert daraus eine Mission. In der Demo ist das ein fester Ablauf, in der App erstellt die KI den Plan.',
          note: 'Ehrlich: Die Demo simuliert die Erstellung. Keine Live-KI während der Aufnahme.'
        },
        route: {
          cue: 'Eine Route bis zur Klausur.',
          say: 'Das Ergebnis ist eine Route mit konkreten Schritten bis zur Klausur. Der erste ist erledigt, der nächste ist markiert.',
          note: 'Nicht behaupten, dass jeder KI-Schritt automatisch richtig ist.'
        },
        next: {
          cue: 'Der nächste Schritt ist schon klar.',
          say: 'Ich muss nicht überlegen, womit ich anfange: Lineare Funktionen üben, 25 Minuten.',
          note: ''
        },
        protect: {
          cue: 'Ablenkende Apps vorher auswählen.',
          say: 'Vorher wähle ich die Apps, die mich ablenken. Auf Android kommt die Auswahl aus den installierten Apps.',
          note: 'Nicht „blockiert alle Apps“ sagen. Die Schutzwirkung wird im Pilot getestet.'
        },
        launch: {
          cue: 'Aus dem Schritt wird Fokus.',
          say: 'Dann starte ich den Fokus – und aus dem Schritt wird eine Fokus-Sitzung.',
          note: 'Stärkster Übergang (ca. 5 s). Während der Verdichtung nicht weiterklicken.'
        },
        active: {
          cue: 'Eine Aufgabe. Ein Timer.',
          say: 'Eine Aufgabe, ein Timer, Fokus-Schutz aktiv.',
          note: 'Kurz halten, nicht auf dem Timer verweilen.'
        },
        result: {
          cue: 'Das Ergebnis fließt in den Plan ein.',
          say: 'Danach gebe ich das Ergebnis an: abgeschlossen. Der Schritt wird auf der Route erledigt, der nächste wird aktiv. Das Ergebnis fließt in den weiteren Plan ein.',
          note: 'Keine Aussage über vollautomatische Anpassung jedes Plans.'
        }
      }
    },

    progress: {
      end: 210,
      beats: {
        route: {
          cue: 'Vom Schulprojekt bis zum Pilot.',
          say: 'Wo stehen wir? Focus Mode+ begann als Schulprojekt. Daraus wurde eine PWA, dann KI-Missions, jetzt eine native Android-Basis. Der nächste Schritt ist der Pilot.',
          note: 'Aussprache: „Pe-We-A“.'
        },
        built: {
          cue: 'Gebaut, nicht nur geplant.',
          say: 'Gebaut ist eine funktionierende PWA mit KI-Missions, Quick Focus, Fokus-Sessions, Verlauf und Wiederherstellung. Auf Android: die native Basis, Auswahl installierter Apps, Benachrichtigungen und Berechtigungen. 81 Commits seit Oktober.',
          note: 'Kein Play-Store-Release, keine iOS-App, kein produktionsreifes Blockieren. Gerätetests auf echter Hardware stehen noch aus.'
        },
        prove: {
          cue: 'Das Wichtigste ist noch nicht bewiesen.',
          say: 'Noch nicht bewiesen ist das Wichtigste: ob Nutzer wiederkommen, mehr Fokus-Schritte abschließen, ob Fokus-Schutz und KI-Pläne wirklich helfen – und ob jemand dafür zahlt. Der Pilot ist vorbereitet, die Hypothesen sind messbar definiert.',
          note: 'Interview- und Anmeldezahlen nur nennen, wenn sie in pitch-content.js eingetragen sind.'
        }
      }
    },

    market: {
      end: 250,
      beats: {
        segment: {
          cue: '2,9 Mio. Studierende – Kontext, keine Prognose.',
          say: 'Unser Kontext: 2,9 Millionen Studierende in Deutschland, laut Destatis. Das ist keine Nutzerprognose. Wir starten enger: Studierende ab 18 mit Android.',
          note: 'Quelle: Destatis, Wintersemester 2024/25. Keine Umsatzzahlen nennen.'
        },
        compare: {
          cue: 'Einzeln gibt es alles. Verbunden nicht.',
          say: 'Planung, Organisation und Fokus gibt es einzeln, etwa mit ChatGPT, Notion oder Forest. Unter den verglichenen Lösungen verbindet aber keine die KI-Zielplanung direkt mit geschützten Fokus-Sessions und Feedback.',
          note: 'Nur „unter den verglichenen Lösungen“. Nie „kein anderes Produkt“.'
        },
        model: {
          cue: 'Free für den Einstieg, Pro für Projekte.',
          say: 'Das Modell: Free mit Quick Focus und einfachen Missions. Pro für komplexe Projekte und langfristige Planung. Die Preise testen wir als Hypothesen: 3,99, 5,99 oder 7,99 Euro.',
          note: 'Kein finaler Preis, keine bewiesene Zahlungsbereitschaft.'
        }
      }
    },

    pilot: {
      end: 285,
      beats: {
        pilot: {
          cue: '20 Android-Testnutzer. 14 Tage.',
          say: 'Deshalb jetzt der Pilot: 20 Android-Testnutzer, 14 Tage.',
          note: ''
        },
        measure: {
          cue: 'Fünf Messpunkte, klare Ziele.',
          say: 'Wir messen fünf Punkte: Mission erstellt, erste Session, drei Sessions, Rückkehr an Tag 7, Interview. Unsere internen Ziele: 60 Prozent erste Mission, 40 Prozent mindestens drei Sessions, 30 Prozent Rückkehr an Tag 7, dazu fünf bis acht Interviews.',
          note: 'Interne Pilotziele, keine Branchenbenchmarks.'
        },
        close: {
          cue: 'Nicht die nächsten Features. Die ersten echten Nutzer.',
          say: 'Was wir jetzt brauchen, sind nicht die nächsten Features, sondern die ersten echten Nutzer.',
          note: 'Nur mit echtem QR-Code ergänzen: „Scannt den Code und macht mit.“ Mit Platzhalter stattdessen: „Der Pilot-Link folgt.“'
        }
      }
    }
  });
})(window);
