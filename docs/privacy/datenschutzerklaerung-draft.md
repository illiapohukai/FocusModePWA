# Datenschutzerklärung – Focus Mode+ (ENTWURF)

> **Hinweis: Dieser Entwurf ist eine technische Vorlage und muss vor einer Veröffentlichung von einer
> fachkundigen Person (Datenschutz/Recht) geprüft und vervollständigt werden.** Er ist keine Rechtsberatung,
> keine Zertifizierung und keine Zusicherung der Rechtskonformität. Alle Stellen mit `[TO BE CONFIRMED]`
> sind offen. Die technischen Angaben beruhen auf dem Quellcode der Version v17.17.

Stand: [TO BE CONFIRMED]

## 1. Verantwortlicher

[TO BE CONFIRMED – Name bzw. Firma]
[TO BE CONFIRMED – Anschrift]
[TO BE CONFIRMED – E-Mail-Adresse für Datenschutzanfragen]
[TO BE CONFIRMED – ggf. Datenschutzbeauftragte*r, falls benannt oder erforderlich]

## 2. Überblick: Was bleibt auf deinem Gerät, was wird übertragen?

Focus Mode+ ist eine Web-App (PWA). Deine Missionen, Aufgaben, Notizen, dein Sitzungsverlauf, deine
lokale Serie und deine Einstellungen werden **nur im Speicher deines Browsers auf deinem Gerät**
(`localStorage`) gespeichert. Es gibt kein Benutzerkonto und keine zentrale Datenbank. Wir haben keinen
Zugriff auf diese lokal gespeicherten Daten.

Daten verlassen dein Gerät nur in diesen Fällen:

1. **Aufruf der App** – technisch notwendige Verbindungsdaten an den Hosting-Anbieter (Abschnitt 3) und an
   den Anbieter eines Darstellungs-Skripts (Abschnitt 6).
2. **Mission planen / Plan anpassen** – nur wenn du es auslöst, werden die dafür nötigen Angaben an unseren
   AI-Dienst gesendet (Abschnitt 4).
3. **Datenexport** – wenn du ein Backup herunterlädst, entsteht eine Datei unter deiner Kontrolle (Abschnitt 5).

Die lokalen Daten sind nicht verschlüsselt. Personen oder Programme mit Zugriff auf dein Gerät bzw. deinen
Browser können sie lesen.

## 3. Hosting

Die App-Dateien werden über [TO BE CONFIRMED – z. B. GitHub Pages, Anbieter und Anschrift] ausgeliefert.
Beim Aufruf verarbeitet der Anbieter technisch notwendige Verbindungsdaten (insbesondere IP-Adresse,
Datum/Uhrzeit, abgerufene Datei, Browser-Kennung) in Server-Protokollen.

- Zweck: Auslieferung und Sicherheit der Website.
- Rechtsgrundlage: [TO BE CONFIRMED – z. B. Art. 6 Abs. 1 lit. f DSGVO; rechtliche Prüfung erforderlich]
- Speicherdauer der Protokolle: [TO BE CONFIRMED]
- Übermittlung in Drittländer: [TO BE CONFIRMED – z. B. USA; Garantien wie Angemessenheitsbeschluss/EU-US Data
  Privacy Framework oder Standardvertragsklauseln sind zu prüfen]

## 4. AI-Planerstellung (Cloudflare Worker und Google Gemini)

Wenn du **„Mission planen“** antippst, senden wir an unseren Server-Dienst bei **Cloudflare**
([TO BE CONFIRMED – Vertragspartner, z. B. Cloudflare, Inc.]):

- deinen Ziel-Text,
- ein von dir ausgewähltes Foto (vorher verkleinert und ohne eingebettete Metadaten wie Standort, mit
  neutralem Dateinamen),
- den gewählten Mission-Typ und deine Sicherheitseinschätzung,
- falls ausgewählt: Termin, Vorbereitungsbeginn, Lerntage, Sitzungsdauer, heutiges Datum und den Namen
  deiner Zeitzone (z. B. „Europe/Berlin“) für eine korrekte Terminplanung.

Wenn du nach einer Sitzung **„Plan anpassen“** wählst, senden wir Angaben **nur zu dieser einen Mission**:
Titel, Fach, Frist-Text, aktueller und verbleibende Schritte, geplante und tatsächliche Minuten, dein
Ergebnis, deine Bewertung und dein optionales Feedback sowie den bisherigen Fortschritt dieser Mission.

**Nicht** gesendet werden: dein übriger Sitzungsverlauf, deine Serie, andere Missionen, deine ausgewählten
Apps im Fokus-Schutz, Einstellungen oder Geräte-Kennungen.

Unser Server leitet die Anfrage an **Google Gemini** ([TO BE CONFIRMED – Google-Gesellschaft]) weiter, um den
Plan zu erzeugen. Bei jeder Verbindung werden außerdem technisch deine IP-Adresse und Browser-Kennung an
Cloudflare übertragen.

- Zweck: Erstellung bzw. Anpassung deines Lernplans auf deine Anfrage.
- Rechtsgrundlage: [TO BE CONFIRMED – z. B. Art. 6 Abs. 1 lit. b DSGVO oder Einwilligung; rechtliche
  Prüfung erforderlich]
- Besondere Kategorien: Bitte gib keine Gesundheitsdaten oder andere sensible Angaben ein, die für den Plan
  nicht nötig sind. [TO BE CONFIRMED – Umgang nach Art. 9 DSGVO prüfen]
- Speicherdauer bei Cloudflare und Google: [TO BE CONFIRMED – gemäß Konfiguration und Nutzungsbedingungen]
- Nutzung zu Trainingszwecken durch Google: [TO BE CONFIRMED – abhängig vom genutzten Tarif und den
  Bedingungen]
- Auftragsverarbeitung: [TO BE CONFIRMED – Status der Verträge nach Art. 28 DSGVO]
- Drittlandübermittlung: [TO BE CONFIRMED – Garantien]

Die AI-Funktion ist optional. Ohne Internet oder ohne AI kannst du die App weiter nutzen; eine Anpassung kann
übersprungen werden.

## 5. Datenexport und -import

Du kannst ein lokales Backup als JSON-Datei herunterladen („Daten exportieren“ oder „Nur Missionen &
Einstellungen“). Die Datei enthält deine Inhalte als lesbaren Text. Wo du sie speicherst oder an wen du sie
weitergibst, entscheidest du. Eine enthaltene Prüfsumme (SHA-256) dient nur dazu, beschädigte Dateien zu
erkennen; sie ist kein Nachweis über Fortschritt oder Serien. Beim Import wird die Datei nur lokal auf deinem
Gerät geprüft und nicht übertragen.

## 6. Darstellung (Tailwind CSS)

Zur Darstellung lädt die App beim Start ein Skript von `cdn.tailwindcss.com`
([TO BE CONFIRMED – Anbieter]). Dabei werden deine IP-Adresse und Browser-Kennung an diesen Anbieter
übertragen. Rechtsgrundlage: [TO BE CONFIRMED]. Geplant ist, dieses Skript durch eine lokal ausgelieferte
Datei zu ersetzen. Schriftarten und Symbole werden seit Version v17.17 direkt von unserer Website geladen; es
erfolgt dafür keine Übertragung an Google Fonts oder andere Dienste.

## 7. Keine Analyse- oder Tracking-Dienste

Die App verwendet keine Cookies, keine Analyse-, Werbe- oder Tracking-Dienste und erstellt keine Profile.
[TO BE CONFIRMED – bei jeder Änderung prüfen]

## 8. Speicherdauer und Löschung

Lokale Daten bleiben gespeichert, bis du sie löschst. Du kannst unter *Einstellungen → Daten & Backup*:

- „Lokale Daten zurücksetzen“ – Missionen und Sitzungen löschen (ein automatisches Backup bleibt zum
  Wiederherstellen erhalten),
- „Alle lokalen Daten löschen“ – alle Daten dieser App einschließlich automatischer Backups und
  Einstellungen von diesem Gerät entfernen.

Zusätzlich kannst du die Website-Daten in deinem Browser löschen. Der Sitzungsverlauf ist auf die neuesten
500 Einträge begrenzt.

## 9. Deine Rechte

Du hast nach der DSGVO insbesondere das Recht auf Auskunft (Art. 15), Berichtigung (Art. 16), Löschung
(Art. 17), Einschränkung (Art. 18), Datenübertragbarkeit (Art. 20) und Widerspruch (Art. 21) sowie das Recht,
eine Einwilligung jederzeit zu widerrufen (Art. 7 Abs. 3). Da deine Inhalte nur auf deinem Gerät liegen,
kannst du viele dieser Rechte direkt in der App ausüben (Export, Bearbeitung, Löschung). Für Daten bei
Cloudflare oder Google wende dich an [TO BE CONFIRMED – Kontakt].

## 10. Beschwerderecht

Du kannst dich bei einer Datenschutz-Aufsichtsbehörde beschweren, insbesondere in dem Mitgliedstaat deines
Aufenthaltsorts. Zuständige Behörde für den Verantwortlichen: [TO BE CONFIRMED].

## 11. Minderjährige

[TO BE CONFIRMED – Zielgruppe klären. Falls die App von Personen unter 16 Jahren genutzt werden soll, sind
Anforderungen an Einwilligungen (Art. 8 DSGVO) und eine altersgerechte Information zu prüfen.]

## 12. Änderungen dieser Erklärung

Wir passen diese Erklärung an, wenn sich die App oder die Rechtslage ändert. Die jeweils aktuelle Fassung ist
in der App bzw. unter [TO BE CONFIRMED – URL] abrufbar.
