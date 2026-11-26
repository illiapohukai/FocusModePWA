# Device smoke test (v18.1, Missions added in v19, parity items added in v20)

Manual checks for a **physical Android device**. Robolectric and the emulator are not a device: OEM
battery managers, real notification shades, Doze, reboots and Usage Access timing only show up on
hardware.

**Status: every step below is PENDING.** No physical device was connected while v18.1 was prepared.
Record results in the table at the end; never mark a step as passed without running it.

## Setup

- A phone with Android 8.0 (API 26) or newer. Note model, Android version and OEM skin.
- `adb` from the Android SDK platform-tools, USB debugging on.
- A debug APK: `cd android && ./gradlew :app:assembleDebug` →
  `android/app/build/outputs/apk/debug/app-debug.apk` (application id
  `io.github.illiapohukai.focusmode.debug`).
- In a second terminal keep a filtered log running while you test:

```bash
adb logcat -b events -b main -v time | grep -E "am_anr|am_crash|ANR in|FATAL|focusmode"
```

Shorthand used below: `PKG=io.github.illiapohukai.focusmode.debug`.

## 1. Install the debug APK

1. `adb install -r -t android/app/build/outputs/apk/debug/app-debug.apk`
2. Open "Focus Mode+" from the launcher.
3. Expect: Home with date, "Focus Mode+", Bereit/Offline pill, the empty Mission hero, Quick Focus
   row, Heute, Erinnerungen, Aktivität and the floating bottom bar (Home · Missionen · + · Verlauf
   · Profil). No permission dialog at launch.

## 2. Notification permission (Android 13+)

1. Home → Quick Focus. The compact "Fokus-Hinweis anzeigen?" row appears.
2. Tap "Erlauben" → the system dialog. Deny once: the row changes to "Benachrichtigungen
   deaktiviert – der Timer läuft trotzdem." Allow from Settings → Berechtigungen.
3. Expect: the permission is asked only from these places, never at launch.

## 3. Start a 2-minute Quick Focus

1. Quick Focus: there is no 2-minute step in the dial (5–60 in steps of 5). For a short run use
   **5 min**, or run the test with a 5-minute Session and shorten waits accordingly. Enter a task.
2. Tap "Fokus starten" once (double taps must not start two Sessions).
3. Expect: the Focus screen with the Cosmic Core, `05:00` counting down, "FOKUS-SITZUNG", the task,
   the mode chip and the badge (Standard-Fokus or Geschützter Fokus), "Sitzung beenden".
4. Expect: an ongoing notification "Fokus läuft" / "Noch 5 Minuten · <Aufgabe>", silent, updated
   once a minute.

## 4. Background the app

1. Press Home. Wait 2 minutes. Reopen from the notification.
2. Expect: the timer shows the real remaining time (no pause, no jump back). The notification
   changed only at minute boundaries.
3. `adb shell dumpsys activity services $PKG` shows `FocusSessionService` as a foreground service.

## 5. Lock screen

1. Lock the device for 1 minute, then wake it.
2. Expect: lock screen shows the notification per Settings → "Inhalt auf dem Sperrbildschirm"
   (Aufgabe / Nur Status / Ausblenden). With "Nur Status" the task never appears.
3. Expect: after unlock the timer is still exact.

## 6. Notification action

1. Pull down the shade → "Beenden …".
2. Expect: the app opens the end flow (sheet); it never ends the Session directly.
3. "Weiter fokussieren" returns to the running timer.

## 7. Session completion

1. Let the Session run to the end with the app in the background.
2. Expect: "Fokus abgeschlossen" notification (sound per channel), the ongoing notification is gone, the
   foreground service stopped (`dumpsys activity services $PKG` no longer lists it).
3. Open the app: Result screen with the duration, outcome "Abgeschlossen", time range, local
   streak ("lokal berechnet, nicht verifiziert").

## 8. History

1. Bottom bar → Verlauf.
2. Expect: "Deine Fokuszeit", the Fokus-Bilanz card counts this Session once, the row shows
   "QUICK FOCUS", the minutes and "Abgeschlossen". Heute / Woche / Alle filter correctly.
3. Repeat step 7 while racing: tap "Sitzung beenden" at the same second the timer ends. Expect
   exactly one History row.

## 9. Process kill

1. Start a Session. `adb shell am kill $PKG` is not enough while a foreground service runs; use
   `adb shell am force-stop $PKG` **only** to simulate a hard kill (this also cancels alarms), or
   better: Developer options → "Don't keep activities" + swipe the app away from Recents.
2. Swipe away from Recents: expect the notification and timer continue.
3. After `force-stop`, reopen the app: expect the Session is restored with exact elapsed time (or
   finalized once as "timer_finished_while_away" if the planned time passed).

## 10. Recovery (reboot)

1. Start a Session, then reboot the device.
2. Expect after boot: a notification "Fokus-Sitzung angehalten" (the monotonic clock is gone); opening the
   app shows "Fokus-Sitzung fortsetzen?" with Fortsetzen / Fokuszeit speichern / Verwerfen.
3. Each option works once; "Verwerfen" asks for confirmation and saves nothing.

## 11. Usage Access

1. Settings (Profil) → Berechtigungen → Nutzungszugriff → open system settings, grant access.
2. Return: the row shows the granted state; "Ablenkungen überwachen" can now be switched on.
3. Expect: no Usage Access read while no protected Session runs. Verify with
   `adb shell dumpsys usagestats | grep -i $PKG` (no query bursts) and the log.

## 12. Selected distracting app

1. Profil → Ablenkende Apps ("Was lenkt dich ab?"): real launchable apps with real icons, search
   works, the selected count updates, the copy says apps are **not** blocked.
2. Select one app (for example a browser). Quick Focus → Fokus-Schutz → "Geschützter Fokus" → start.
3. Open the selected app. Expect within about 20 s (screen on): a gentle hint "Dein Fokus läuft noch"
   / "Noch N Minuten – zurück zu deiner Aufgabe?",
   at most one per 3 minutes. The app is **not** blocked or closed.
4. Expect the Focus screen's task card to show "Ablenkungen überwachen · 1 Hinweis · 1 Apps".
5. Turn the screen off for 5 minutes: no hints, no wakeups for the monitor (battery stats stay flat).

## 13. Do Not Disturb

1. Grant Notification Policy Access (Berechtigungen → Nicht stören), switch on "Nicht stören
   während des Fokus".
2. Start a Session: DND turns on (priority only). End it: DND returns to the prior state.
3. If DND was already on before the Session, Focus Mode+ must leave it untouched.

## 14. Permission revocation

1. During a protected Session revoke Usage Access in system settings.
2. Expect: the Session continues; monitoring stops (within one poll, ~20 s); the task card shows the
   capability title instead of the hint count; no crash, no restart loop of the monitor.
3. Re-grant and return to the app: monitoring resumes for the same Session.
4. Revoke notifications during a Session: the ongoing notification follows the system rule; the
   timer is unaffected.

## 15. Reboot (reminders)

1. Create a Geplanter Fokus a few minutes ahead (Plus → Geplanter Fokus). Reboot.
2. Expect the reminder still arrives approximately on time (inexact on Android 12+; the app says so).

## 16. Battery optimization

1. Settings → Apps → Focus Mode+ → Battery: set "Restricted" (or the OEM equivalent).
2. Run step 4 and 7 again. Note any delay of the end notification or a killed service. Focus Mode+
   shows the battery state read-only and never requests the exemption.

## 17. Airplane mode

1. Enable airplane mode. Home shows "Offline". Quick Focus, Focus, Result and History all work.
2. Nothing in the flow waits for the network.

## 18. App update over existing data

1. With Missions/History present, build a newer APK (bump `versionCode` locally, do not commit) and
   `adb install -r` it while a Session runs.
2. Expect: data kept (Room migration path), the running Session is restored (`PACKAGE_REPLACED`),
   reminders re-synced, no duplicate notifications.

## 19. Missions (v19)

Requires network for steps a–c only; everything else must also work in airplane mode.

a. Home → "Mission erstellen": type a goal, pick type and confidence, choose a deadline in the inline
   calendar (check that existing Mission dates are marked and open their details first), add a photo
   with the system picker (deny once, then allow), tap "Mission planen" once. Expected: one request,
   the operator works until the plan arrives, then the Briefing. Note the photo is upright.
b. Repeat and tap "Abbrechen" while the operator works. Expected: back on the form, every input kept,
   no Briefing appears later, no Mission is created.
c. Kill the app during generation (Recents → swipe). Reopen. Expected: the form shows "Die Analyse wurde
   unterbrochen …" and nothing was re-sent.
d. "Mission übernehmen" (double-tap). Expected: exactly one Mission; Home shows it with progress left and
   "Schritt starten" right.
e. Start the step, lock the device, kill the process, reopen from the notification. Expected: the same
   Mission Session ("Schritt 1 von n"), never Quick Focus.
f. End after ≥ 1 minute, review "Abgeschlossen". Expected: progress advances once on Home, Plan and
   Result; History holds one record. Repeat with "Teilweise" and "Abgebrochen": the step stays open.
g. Create a second Mission: Home shows a swipeable carousel; a third Mission is refused everywhere.
h. Archive, restore and delete a Mission. Expected: Home and the calendar update, History keeps the
   Sessions, the Mission's reminders disappear.
i. TalkBack and 200 % font: Creation, calendar days (spoken dates and Mission dates), Briefing steps,
   Plan route (ordered, current step announced), review scale.

## Performance and ANR capture (on any device or emulator)

```bash
adb shell dumpsys gfxinfo $PKG reset          # then use the Focus screen for 60 s
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky|percentile"
adb shell top -b -n 1 -m 10                    # CPU share of $PKG vs system_server / systemui
adb logcat -d -b events | grep am_anr          # the 3rd field names the ANR process
adb bugreport v18.1-bugreport.zip              # full traces (/data/anr) if an ANR occurs
```

Interpretation: `am_anr ... com.android.systemui` or `system` means the ANR belongs to Android, not
to Focus Mode+. Only an `am_anr` line naming `io.github.illiapohukai.focusmode(.debug)` is an app ANR;
then attach the main-thread stack from the bugreport.

## 20. Parity items (v20)

a. Mission Creation: tap the camera button, take a photo, confirm. The photo card appears; no
   camera permission dialog from Focus Mode+. Repeat and cancel the camera: the draft is unchanged.
   Kill the app while the camera is open, take the photo, return: the photo is still processed.
b. Tap the microphone, say a sentence: the recognized text is appended to the goal field and can
   be edited before "Mission planen". Without a speech service the button is absent.
c. Briefing with a photo: "So wurde der Plan erstellt" shows "Aus dem Material erkannt" (or the
   honest "Im Foto wurde nichts eindeutig erkannt" note); "Warum so?" shows tools and apps.
d. Mission Plan → pencil → rename. The colour, steps, History rows and reminders stay.
e. History: Fokus-Rhythmus 7 Tage / 4 Wochen, tap bars (TalkBack reads each bar), open
   "Statistik" → Dein Fokusverlauf. Check 130 % and 160 % font.
f. Settings → Daten & Backup → "Daten exportieren": save to Downloads and to a cloud provider.
   Import the file on a second device (and in the web app: Daten & Backup → Daten importieren;
   the preview must say "Backup geprüft").
g. Start a Quick Focus, then "Alle persönlichen und nutzergenerierten Daten löschen": the ongoing
   notification disappears, DND (if set by the app) is restored, Home shows the first-time card,
   no reminder arrives afterwards (also after a reboot).

## Results

| # | Step | Device / Android | Result | Notes |
|---|---|---|---|---|
| 1 | Install debug APK | — | PENDING | |
| 2 | Notification permission | — | PENDING | |
| 3 | Quick Focus start | — | PENDING | |
| 4 | Background | — | PENDING | |
| 5 | Lock screen | — | PENDING | |
| 6 | Notification action | — | PENDING | |
| 7 | Session completion | — | PENDING | |
| 8 | History | — | PENDING | |
| 9 | Process kill | — | PENDING | |
| 10 | Recovery (reboot) | — | PENDING | |
| 11 | Usage Access | — | PENDING | |
| 12 | Selected distracting app | — | PENDING | |
| 13 | Do Not Disturb | — | PENDING | |
| 14 | Permission revocation | — | PENDING | |
| 15 | Reboot (reminders) | — | PENDING | |
| 16 | Battery optimization | — | PENDING | |
| 17 | Airplane mode | — | PENDING | |
| 18 | App update over existing data | — | PENDING | |
| 19 | Missions (v19) | — | PENDING | |
| 20 | Parity items (v20) | — | PENDING | |
