# Visual parity v20 (Android ↔ PWA)

Reference: the current PWA source (`index.html`, `styles/navigation.css`, `styles/insights.css`,
`styles/missions.css`, `src/features/history/*.js`, `src/services/ai-service.js`). The v18.1
comparison ([visual-parity-v18.1.md](visual-parity-v18.1.md)) stays valid for every screen not
listed here. Android was rendered with Robolectric (native graphics, real Compose, real Hilt
graph for the app flows) at 360 dp xhdpi, 320 dp with 160 % font, 800 × 360 dp landscape and
840 dp tablet. Robolectric is not a device: fonts, shadows and ripples are close to, not
identical with, a phone. No PWA rendering was repeated in v20 (the PWA did not change); the
PWA side below comes from its stylesheets and renderers.

Screenshots are written by the tests to `android/app/build/screenshots/` (22–34) and
`android/feature/{history,missions,settings}/build/screenshots/` and are uploaded by CI in the
`android-reports` artifact. They were opened and reviewed for this document.

## Design-system consolidation

| Token | Value | Replaces |
|---|---|---|
| `FocusPalette.GreenInk` | `#15803D` (`.status-completed`, `.home-activity-outcome`) | 8 literals in 5 features |
| `FocusPalette.AmberInk` | `#8A4B0F` (partial outcome, gentle warnings) | 2 literals |
| `FocusPalette.NodeOpen` | `#B3A897` (`.outcome-aborted .home-activity-node`) | — (new) |
| `FocusPalette.HeroText` / `HeroMuted` | `#E9E2F5` / `#B9ADC9` (text on aubergine) | 6 literals |
| `ChartMission` / `ChartQuick` / `ChartPlaceholder` / `ChartAxis` / `ChartBaseline` / `ChartAverage` | `#2E1A3B` / `#8B5CF6` / `#E6DED1` / `#8F877D` / `rgba(45,35,30,.12)` / `rgba(109,40,217,.32)` (`insights.css`) | — (new) |
| `ConsistencyEmpty` / `Low` / `Mid` (+ `Purple`) | `#ECE5D8` / `#DCCDF6` / `#A78BFA` | — (new) |
| `OutcomeDot`, `OutcomeLabel`, `OutcomeColors` | shared node + label component | History pill, Home `BadgeText` |
| `isTextCompact`, `keepDurationsTogether` | layout helpers for 320 dp / 160 % | per-screen workarounds |

No near-duplicate screen colours were added. Remaining one-off literals (amber notice surfaces,
the operator, the dial ticks, light outcome counts on the aubergine hero) are single uses.

## Screens

### Home — Activity

| Aspect | PWA | Android v19.1 | Android v20 | Status |
|---|---|---|---|---|
| Structure | one card, day labels (Heute, Gestern, "Mo., 23. Nov."), 6 rows | 3 separate cards, no day labels | one card, day labels, 6 rows, rail between rows of one day | Done |
| Outcome | node shape: filled / half / open (+ text) | coloured word only | `OutcomeDot` filled / half / open + German label in green / amber / graphite | Done |
| Mission identity | node in the Mission tone, Mission title as context | none | node in the Mission tone (`accentStrong`), "Biologie-Klausur · 09:05" | Done |
| Time | start time | end time + day | start time (PWA `formatStartTimeHHMM`) | Done |
| Empty | tile, "Noch keine Aktivität", "Deine Fokus-Sitzungen erscheinen hier." | one grey sentence | as PWA | Done |
| 320 dp / 160 % | — | — | duration and outcome move under the task (no broken words) | Adapted |
| Today cells | open History | not interactive | the card opens History | Done |

### History

| Aspect | PWA | Android v19.1 | Android v20 | Status |
|---|---|---|---|---|
| Header | kicker, title, round chart button | no button | 48 dp round "Statistik" button → Dein Fokusverlauf | Done |
| Order | summary → Fokus-Rhythmus → filters → list | summary → filters → list | as PWA | Done |
| Fokus-Rhythmus | stacked SVG bars (Mission aubergine under Quick violet), dashed average, best label, legend, best period, trend, tooltip | — | Compose Canvas, same colours and geometry (bar 10–30/46 dp, radius ≤ 9 dp), grow-in 520 ms; selection dims other bars to 38 % and shows the details inline under the plot | Done / Adapted (inline detail, no floating tooltip) |
| Rows | time range, type chip, title, Mission meta "modeLabel · Schritt 02", minutes, coloured pill with icon | no Mission meta, pill with check | Mission title · Schritt NN with a tone dot, Mission chip in the Mission tone, node + label | Done / Adapted (shape-coded outcome instead of check/x icons) |
| Empty | four PWA copies, "Erste Sitzung starten" / "Fokus starten" | two copies | PWA copies and actions | Done |
| Outcome counts at 320 dp / 160 % | — | words clipped ("Abgeschlo") | one outcome per line | Fixed |

### Dein Fokusverlauf (new)

| Aspect | PWA (`app-screen-stats`) | Android v20 | Status |
|---|---|---|---|
| Header | back, "FORTSCHRITT", "Dein Fokusverlauf" | same | Done |
| Hero | dark summary: total, Sessions badge, Ø pro Sitzung, Abschlussquote, Heute | aubergine hero, one-line auto-sized total; at 320 dp / 160 % the badge and the three values stack | Done / Adapted |
| Chart | Fokus-Rhythmus, 4 Wochen first | same component, 4 Wochen first | Done |
| Konstanz | streak value, copy, 14 × 2 grid with 4 levels, today outlined | same; the grid is one spoken summary | Done |
| Bestwerte | Stärkster Tag / Woche, Längste Sitzung | same | Done |
| Sitzungsarten | split bar + Mission/Quick rows | same | Done |
| League link | "Rangliste & Erfolge" | not shown | Deferred |
| Empty | "Dein Fortschritt beginnt mit der ersten Sitzung" | same + "Fokus starten" | Done |

### Mission Creation, Briefing, Plan

| Aspect | PWA | Android v20 | Status |
|---|---|---|---|
| Goal input actions | paperclip, mic, camera | paperclip, mic and camera (only when the system offers them) | Done (device validation pending) |
| Briefing "Noch unklar" | schedule warning + missing information | both, as a bulleted list | Done |
| Briefing "Warum so?" | reason + "Brauchst du" + "Empfohlene Apps" chips | same chips (text only) | Done |
| "So wurde der Plan erstellt" | user input / "Aus dem Material erkannt" or "Aus deinem Ziel erkannt" / Geschätzt; "AI-Erkennung: hoch/mittel/niedrig" | same groups and meter text; unreadable photo note | Done |
| Plan current step | "Du brauchst" chips | chips on the aubergine step card | Done |
| Rename | sheet with one field (80) | Material dialog, pencil in the top bar, "Der Plan und die bisherige Historie bleiben unverändert." | Adapted (dialog) |

### Settings and Daten & Backup

| Aspect | PWA | Android v20 | Status |
|---|---|---|---|
| Daten & Backup row | settings row + "Letztes Backup" | same | Done |
| Data screen summary | Aktive Missionen x/2 (n), Sitzungen, Letztes Backup | same (active Session and data-health badges not shown) | Done / Adapted |
| Actions | export full, Missionen & Einstellungen, import, restore last backup, reset, delete all | export full, Missionen & Einstellungen, import, delete all | Adapted (no internal backup copies on Android) |
| Privacy note | backup text note | same note + what is not included | Done |
| Delete confirmation | inline rose card | Material alert dialog, rose confirm "Endgültig löschen" | Adapted |
| Privacy section | — | "Was auf diesem Gerät bleibt" / "Was an die AI geht" | Native addition |

## Motion

| Moment | PWA | Android v20 |
|---|---|---|
| Chart reveal | `fmChartGrow` 520 ms, standard easing, staggered | 520 ms, `Easings.Standard`, all bars together (no per-bar stagger) |
| Range switch | re-reveal | re-reveal (keyed by range) |
| Reduced motion | shortened | no animation; final bars drawn immediately |
| Bar selection | 160 ms opacity | immediate opacity change |
| Route transitions | unchanged | unchanged (`routeEnter`/`routeExit` with `MotionTokens`) |

No animation gates any data write; deletion, export and import never wait for motion.

## Remaining visual gaps

Activity row rail is drawn per row (a hairline), not one continuous rail; chart bars do not
stagger; the League preview and link are absent; the Plan route is vertical instead of the PWA
orbital layout; no PWA rendering of the new screens was repeated side by side in v20. See
[remaining-gaps-after-v20.md](remaining-gaps-after-v20.md).
