# Visual parity v18.1 (Android ↔ PWA)

The authoritative reference is the **current PWA** in this repository, rendered with Playwright
(Chromium, 390 × 844 CSS px, DPR 2, `de-DE`) from `index.html` served locally, with computed styles
read from the live DOM (`getComputedStyle`). Tailwind's CDN is blocked in the build container, so
the same Tailwind v3 classes were compiled locally from `index.html` + `src/**/*.js` and injected
in its place; Lucide is vendored. No README text or old screenshot was used as the reference.

Android was compared with the Robolectric app-flow screenshots (`app/build/screenshots/`, 360 dp,
xhdpi; 320 dp at 160 % font; tablet; landscape). The Android UI stays Compose: no WebView, no web
CSS, no screenshots used as UI.

Legend: **Done** = implemented in v18.1. **Adapted** = deliberate native difference. **Kept** =
v18 behavior that already matched or must not change.

## Design tokens

| Token | PWA (computed) | Android v18.1 | Status |
|---|---|---|---|
| App background | `--app-bg #F6F1E7` | `FocusPalette.Cream` | Kept |
| Surface / muted | `#FCF9F4` / `#EFE9DD` | `Surface` / `SurfaceMuted` | Kept |
| Text | `#1D1A17`, secondary `#766E65`, titles `#14161B` / `#14110F` | `Graphite`, `GraphiteSoft`, new `Ink` | Done |
| Date | 17.5 px / 600 / +0.03 em / `#7B8496` uppercase | `LabelType.Date` 16 sp, `DateInk` | Done |
| Purple accent | `--primary #6D28D9`, gradient `#7C3AED → #6D28D9 → #4C1D95` | `Purple`, `PrimaryGradient` | Kept |
| Aubergine | `#25162B → #1B1020 → #160D1B` + light, glow at 88 %/16 %, lower vignette | `AubergineSurface` now draws all three overlays | Done |
| Green | `--success #16A34A`, Session badge `#5EEAD4` on `rgba(8,47,54,.52)` | `Green`, `ProtectInk/Surface/Line` | Done |
| Mission tones | `--mt-*` (aubergine, forest, ember, burgundy) | `MissionTones` (unchanged) | Kept |
| Focus shell | 170° `#040613 → #07091B → #090A1D`, blooms `rgba(109,40,217,.22)` at 50 %/34 % and `rgba(76,29,149,.28)` at 82 %/8 % | `SpaceTop/Mid/Bottom`, `SpaceBloom*` | Done |
| Radii | chips 12, tiles 15, back 17, buttons 21, cards 22, Session card 27, heroes 30, nav 999 | `Radii.sm/tile/control/button/lg/session/xl/pill` | Done |
| Shadows | cards `0 8px 22px rgba(37,22,43,.05)`, nav `0 16px 38px rgba(37,22,43,.14)`, start `0 16px 30px rgba(109,40,217,.3)` | aubergine-/purple-tinted `shadow()` elevations | Adapted (Android elevation, not CSS blur) |
| Type | Inter; screen title 32–36 px/900–950; hero 27/900; card title 22/900; button 17/900; body 13–16/600; eyebrow 9.5–12/850, +0.08–0.14 em | `FocusTypography` reweighted (headlines Black, titles ExtraBold, body Medium/SemiBold, buttons Black); `LabelType` | Done |
| Eyebrow minimum | 9–9.5 px labels | 11 sp minimum (10 sp in dense stats) | Adapted (Android legibility; sp scales) |
| Timer | `clamp(48px,15vw,68px)` = 58.5 px / 900 / −0.055 em / `tnum`, one size for setup and Session | `TimerType.Live = Readout = 58 sp`, autosize down to 30 sp to fit | Done |
| Motion | 90/160/350/390/720 ms; standard `cubic-bezier(.22,1,.36,1)` | `MotionTokens`, `Easings` (unchanged), now used by route transitions | Done |

## Screens

### Home

| Aspect | PWA | Android before | Android v18.1 | Status |
|---|---|---|---|---|
| Header | slate date, 36 px/900 brand, cream "Bereit" pill with green dot, profile avatar | 11 sp eyebrow date, 30 sp title, tinted pill | `LabelType.Date`, `LabelType.Brand`, surface pill with dot; the pill shares the date line so the brand stays on one line at 360 dp | Done / Adapted (pill position) |
| Profile avatar | round avatar opening settings | — | not shown (no accounts; the bar has "Profil") | Adapted |
| Mission hero (empty) | kicker with green dot "FOCUS MISSION" + "BEREIT" chip, 27 px/900 question, copy, inner "AI PLAN / Ziel beschreiben" card | "Bereit für ein neues Ziel?" + button | PWA kicker and chip, "Was möchtest du heute fokussiert schaffen?", honest copy, inner glass card "Missionen ansehen" | Done / Adapted: no AI-plan card because Mission creation is not in v18.1 |
| Mission hero (one Mission) | Mission tone hero, step, next-step card, start | tone hero with progress and start | PWA kicker (live dot, step chip), 27 sp title, inner next-step card with minutes tile, progress, start (filled play) | Done |
| Several Missions | horizontal Mission carousel | primary only | primary Mission plus "1 von N aktiven Missionen · Alle ansehen" → Missionen | Adapted (no carousel yet; distinct state) |
| Quick Focus | section label outside, 44 px gradient lightning tile, 13.5/800 prompt, muted "25 MIN →" chip | eyebrow inside card, tinted play icon, accent pill | as PWA | Done |
| Heute | section label + "Woche: … Sitzungen · … Min.", three centred cells with hairlines (`0m` / FOKUS …) | inline title, left-aligned stats | as PWA; streak labelled "TAGE SERIE" (local, days) | Done / Adapted (no weekly goal "von 4") |
| Calendar / reminders | "MISSIONSKALENDER" card with empty tile | "Erinnerungen" card | section label outside; empty state with calendar tile; rows with bell tiles | Adapted (Android shows real reminders, not a Mission calendar) |
| Activity | label + "Alle anzeigen" | same | rows with hairline, 22 dp radius | Done |
| Density | 18 px side padding, 8–12 px rhythm | 20 dp, 16 dp gaps | 18 dp sides, 8 dp list rhythm | Done |

### Quick Focus

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Back control | 46 px rounded-square cream button, hairline, soft shadow | `BackButton` 48 dp, `Radii.control` | Done (48 dp touch target) |
| Title | 34 px/900 "Richte deine Sitzung ein." | `headlineLarge` (34 sp Black) | Done |
| Dial | raised cream disc with shadow, beige track, purple gradient arc, glowing thumb, faint outer orbit with purple motes, `MM:00` | same structure natively drawn; the dial keeps slider semantics, custom actions and keys | Done |
| Readout | 58.5 px/900 `MM:00` | `TimerType.Readout`, shrinks to fit at large fonts | Done |
| Task field | "WORAN ARBEITEST DU?" 12/950, white 22 px field, target + pencil | uppercase label, 22 dp field, target + pencil | Done |
| Fokus-Schutz | green shield tile, "7 Apps blockiert", icon stack | shield tile **green only when protected and Usage Access is ready**, honest summary text, real selected-app icons | Adapted (no "blockiert" claim) |
| Notifications | — (web) | one compact row "Fokus-Hinweis anzeigen? · Erlauben" (was a paragraph card) | Adapted (Android permission, now shorter) |
| Start | 60 px, 21 px radius, gradient, filled play, 17/900 | `PrimaryButton` 60 dp, 21 dp, filled play | Done |

### Active Focus

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Background | `.uc-shell` gradient + blooms | `SessionBackground` (static, drawn once) | Done |
| Header | "FOKUS-SITZUNG" (12/900, +0.17 em, `#A78BFA`), task title 20/900, book icon + "25 Min.", outlined mode chip | same | Done |
| Cosmic Core | baked per-pixel sphere (rim light, nebula, glint, ring sheen), atmosphere, orbit table, dust | the PWA `bakeCore` shader ported to Kotlin and baked off the main thread; same orbit table and palette; adaptive tiers | Done (decorative detail scales with the tier) |
| Timer | centred on the sphere, white, text shadow | same, autosize, minute-level description only | Done |
| Protection badge | "● Fokusschutz aktiv ›" teal pill under the core | under the core: green dot + "Geschützter Fokus" when protected; neutral shield + "Standard-Fokus" otherwise; not interactive | Adapted (mode name, no picker shortcut) |
| Task card | 27 px radius, translucent gradient, violet border, "◎ AUFGABE", 25.7 px title | same | Done |
| Actions | "Blockierung testen" + rose "Sitzung beenden" | rose "Sitzung beenden" only | Adapted (no blocking test: Android consumer apps cannot block) |
| Orientation | portrait web layout | portrait + dedicated landscape layout | Kept |

### End flow

| PWA | Android v18.1 | Status |
|---|---|---|
| no confirmation: < 1 min cancels, otherwise saves and returns Home | the approved v18 sheet (Standard / protected barrier with reason / emergency exit), now on the aubergine surface with PWA buttons | Adapted (Android protection levels) |

### Result

| PWA | Android v18.1 | Status |
|---|---|---|
| Quick Focus returns Home; Mission results on `app-screen-result` | Result screen kept; aubergine hero with outcome dot (green only for completed), 64 sp minutes, flame tile streak card, stacked actions (no wrapping at 320 dp) | Adapted (vertical-slice Result screen) |

### History

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Header | "SITZUNGSVERLAUF" / "Deine Fokuszeit" + chart button | `ScreenHeader`, no chart button | Done / Adapted (no insights screen) |
| Summary | aubergine "Fokus-Bilanz" with period chip, big minutes, "gezählte Fokuszeit", Sessions, week line, green/lavender/amber outcome counts | same, computed for the selected filter; streak in the week line | Done |
| Rhythm chart | "Fokus-Rhythmus" 7 Tage / 4 Wochen | not implemented | Deferred |
| Filter | Heute / Diese Woche / Alle Sitzungen | Heute / Woche / Alle | Kept (short labels fit 320 dp) |
| Rows | time range, "QUICK FOCUS" chip, title, minutes, green "✓ Abgeschlossen" pill | same; Mission chip in purple | Done |

### Settings (Profil)

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Header | "KONFIGURATION & PROFIL" / "Einstellungen" + "FM+" avatar | same | Done |
| Summary | "Dein Fokus-System": active Missions x/2, saved Sessions, apps | same, real local counts; chip "Lokal gespeichert" | Done |
| Groups | Fokus & Missionen, Darstellung & Sprache, Daten & Backup | Fokus-Schutz, Benachrichtigungen, Bewegung, Daten & Datenschutz (Android settings: permissions, lock-screen privacy, DND) | Adapted |
| Honesty | "7 Apps während Sitzungen blockiert" | "Nur in geschützten Sitzungen. Apps werden nicht blockiert.", "Nutzungszugriff erforderlich", "Strikte Sperre … Verwaltetes Gerät erforderlich" | Adapted (kept) |
| Language switch | DE / EN | German only | Adapted (v18 scope) |

### App picker

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Header | back + "FOKUS-SCHUTZ" kicker, "Was lenkt dich ab?" | same | Done |
| Copy | "… während deiner Fokus-Sitzung tabu sind." | "… in einer geschützten Sitzung an deinen Fokus erinnern soll. Apps werden nicht blockiert. Deine Auswahl bleibt auf diesem Gerät." | Adapted (honest) |
| Summary | icon stack, "7 Apps ausgewählt", "Fokus-Schutz bereit" | real icon stack, count, "Hinweise in geschützten Sitzungen · nicht blockiert" | Done / Adapted |
| List | fixed illustrative list, letter tiles, purple check circles | the user's **real launchable apps** with **real icons** (loaded lazily, never stored), search, check circles, tinted selected rows | Done |
| Bulk action | "Alle abwählen", "Auswahl übernehmen" | per-row toggles save immediately | Adapted |

### Bottom navigation

| Aspect | PWA | Android v18.1 | Status |
|---|---|---|---|
| Geometry | 65 px capsule, 14 px side margin, 10 px above the safe area, 6/8 px padding | `NavGeometry` (same values), above the system navigation inset | Done |
| Items | Home · Missionen · + · Verlauf · Profil, 9.5 px/850 labels, glider, purple selected icon | same labels and icons, one shared fitted label size (10.5 sp base) | Done |
| Plus | raised gradient circle with white ring | 56 dp (touch target), 4 dp surface ring | Done |
| Clipping | — | v18 clipped "Missionen" (item clipped to a pill); fixed: the glider is a shaped background, the ripple unbounded | Done |
| Content clearance | `--fm-screen-bottom-padding` = nav + 24 px | `contentClearance` (99 dp) **plus** the system navigation inset (v18 used a fixed 108 dp, which hid content under the bar with three-button navigation) | Done |
| Wide windows | — | floating rail ≥ 600 dp | Adapted |

### Motion

| PWA | Android v18.1 | Status |
|---|---|---|
| `motion-controller.js` owns route motion; tokens in `motion.css` | NavHost is the one owner: 160 ms crossfade between roots, 350 ms slide-and-fade into flow screens (mirrored on Back), Focus fades in and settles from 0.96; reduced motion: short fades only. Bottom-nav selection 160 ms. Core formation 2.15 s (static under reduced motion). Sheets use the platform sheet motion | Done |

### Safe areas and accessibility

- Edge-to-edge with system bar insets on every screen; the floating bar adds its clearance on top
  of the navigation inset (gesture pill and three-button bar).
- Touch targets ≥ 48 dp (back, nav items, plus, rows, end action).
- Large fonts: the timer and dial readout shrink to fit instead of clipping; nav labels share one
  fitted size; one-word outcome labels shrink instead of truncating; Result actions stack.
- Semantics: screen titles are headings; nav items expose their label and selected state; the Core,
  dial face, icon stacks and app icons are decorative; the timer exposes minute-level descriptions.

## Verification status

| Check | How | Result |
|---|---|---|
| 360 dp phone | Robolectric app flow (`w360dp-h780dp`) | screenshots reviewed |
| 320 dp + 160 % font | Robolectric (`08_…`, `09_…`), `BottomNavigationTest` | no clipped labels; brand on one line |
| 320 dp + 130 % font | `BottomNavigationTest` | labels fit |
| 390 / 430 dp equivalents | not rendered separately (layouts are width-fluid) | not verified |
| Landscape, tablet | Robolectric (`11_…`, `10_…`) | screenshots reviewed |
| Gesture vs three-button navigation | inset logic reviewed in code; Robolectric reports no navigation inset | not verified on a device |
| Real device / emulator rendering | see `device-smoke-test.md` | pending |

## Remaining differences

Mission carousel, AI plan entry, Mission calendar, Fokus-Rhythmus chart, Focus insights, profile
avatar, language switch, "Blockierung testen", the PWA's direct end without a sheet, and exact CSS
blur shadows (Android uses elevation shadows).
