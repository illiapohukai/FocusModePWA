# Protection capabilities (v18)

Focus Mode+ on Android never claims more than the platform really does. Protection is modelled as
explicit capability states (`ProtectionCapability`), resolved by one pure function
(`CapabilityResolver`) from live platform readings that the single owner
(`ProtectionCapabilityOwner`) re-reads on every resume and after returning from a settings screen.
Each assessment carries a title, an explanation, the next safe action, a settings target, the
permission state and its limitation.

States: `UNSUPPORTED`, `NOT_CONFIGURED`, `NOTIFICATION_PERMISSION_NEEDED`, `USAGE_ACCESS_NEEDED`,
`DO_NOT_DISTURB_ACCESS_NEEDED`, `ACCESSIBILITY_NOT_AVAILABLE`, `MANAGED_DEVICE_REQUIRED`,
`READY_FOR_SOFT_PROTECTION`, `READY_FOR_USAGE_MONITORING`, `READY_FOR_MANAGED_BLOCKING` (test fakes
only in v18), `TEMPORARILY_UNAVAILABLE`.

## Levels

### Standard Focus (`STANDARD`)
Timer, one ongoing notification, optional Do Not Disturb, ordinary end: "Fokuszeit speichern"
(≥ 1 min), "Abbrechen ohne zu speichern" or "Weiter fokussieren". It works without any permission.

### Protected Focus (`PROTECTED`)
- End barrier: a 10-second pause ("Atme kurz durch …") and a reason (Aufgabe erledigt, Pause nötig,
  Abgelenkt, Anderer Grund) before "Beenden" is enabled. The reason is stored locally with the result.
- Emergency exit "Notfall – sofort beenden" is always available, with no delay and no reason. The
  user is never trapped, and Android's own controls (Home, Recents, notifications) are untouched.
- Selected-app awareness through Usage Access, when the user grants it and turns on "Ablenkungen
  überwachen": a gentle, rate-limited hint ("Dein Fokus läuft noch – Noch 12 Minuten – zurück zu
  deiner Aufgabe?"). The app is **not** blocked.

### Strict managed mode (`STRICT_MANAGED`)
Architecture only. It is never offered as active on an ordinary phone ("Strikte Sperre auf diesem
Gerät nicht verfügbar · Verwaltetes Gerät erforderlich"). It requires a device-owner or
profile-owner deployment (school or enterprise). v18 ships `ConsumerManagedDeviceGateway`
(detection only) and `NoOpManagedFocusEnforcer`. No `DeviceAdminReceiver` is declared, nothing is
provisioned, and owner status is never requested.

Future managed capabilities (not implemented): `DevicePolicyManager.setLockTaskPackages` +
`startLockTask` (kiosk), `setPackagesSuspended` for selected packages, dedicated-device deployments.
All of these require the app to be device or profile owner, set up through managed provisioning by
an administrator.

## What ordinary consumer Android can and cannot do

| Can | Cannot (without a managed device) |
|---|---|
| Show an ongoing Focus notification | Block or force-close other apps |
| Detect, after the fact, that a selected app came to the foreground (Usage Access) | Prevent an app from opening |
| Show a hint notification | Draw blocking overlays (not requested: `SYSTEM_ALERT_WINDOW`) |
| Change Do Not Disturb with user-granted policy access | Disable Home/Recents or trap the user |
| Ask for an intentional pause before ending | Become device owner on its own |

## Usage Access

- Permission: `PACKAGE_USAGE_STATS`, granted by the user in system settings ("Nutzungszugriff").
  The app explains why first; the user opens settings themselves. The real state is checked through
  `AppOpsManager` after returning. It is never assumed.
- Use: only during an active **protected** Session with monitoring on and at least one selected app.
  `UsageStatsManager.queryEvents` is polled every 20 s while the screen is on (nothing can be in the
  foreground while it is off; with the screen off the monitor waits for `ACTION_SCREEN_ON` instead of
  waking), for foreground events of the selected packages only. The polling runs off the main
  thread. `DistractionMonitorOwner` keeps exactly one monitor job per protected Session (repeated
  starts for the same Session are no-ops) and cancels it on completion, cancellation and service
  shutdown; a restored process starts one new job.
- Data: per Session and package, a count plus first and last time (`session_distractions`). Kept 30
  days, deletable in Settings ("Ablenkungsdaten löschen"). Never uploaded, never sent to the AI
  Worker; the app-selection and protection modules have no dependency on the network module.
- Revoked during a Session: monitoring stops, the state becomes `TEMPORARILY_UNAVAILABLE`, and the
  Session continues normally. Polling does not restart until access is granted again (re-read on
  app resume); then it resumes within the same Session.
- Limits: some OEMs deliver events late; work-profile apps are invisible to the personal profile;
  Android Go devices may not offer Usage Access (`UNSUPPORTED`).

## Do Not Disturb

- Permission: Notification Policy Access (`ACCESS_NOTIFICATION_POLICY` declared; the user grants it
  in settings). It is never requested at first launch, and it is off by default.
- During a Session with "Nicht stören während des Fokus": if the filter is `ALL`, Focus Mode+ first
  records `{prior=ALL, applied=PRIORITY}` in Room, then sets `PRIORITY`. The user's priority
  exceptions (alarms, contacts) stay as configured, because the policy itself is not touched.
- After the Session, and on every app start or boot (crash recovery): if the filter is still the one
  Focus Mode+ set, the prior filter is restored; otherwise the user changed it, and their choice
  wins. The record is cleared either way. If the user already had DND on, nothing is changed and
  nothing is restored.
- DND silences notifications. It does not block apps, and the UI never claims it does.

## Accessibility Service — deliberately not used

No Accessibility Service is implemented or declared. Using one to watch every foreground app,
intercept navigation, press buttons, read screen content or enforce Focus with overlays conflicts
with Google Play's Accessibility API policy for apps whose primary purpose is not accessibility.
Any future use would need an explicit product decision and a Play policy review first.

## Google Play policy-sensitive areas (not reviewed)

- `FOREGROUND_SERVICE_SPECIAL_USE`: needs a Play Console declaration (subtype: user-started focus
  timer with a visible countdown).
- `PACKAGE_USAGE_STATS`: special access; the use must be clearly disclosed in-app (it is) and in the
  store listing and privacy policy.
- Exact alarms: not requested (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` are not declared).
- Package visibility: only a launcher `<queries>` intent; no `QUERY_ALL_PACKAGES`.
- Device admin, overlays, accessibility: none.
