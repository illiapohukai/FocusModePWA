# Technical privacy draft: Google account and synchronization (Android, v21)

Technical description for the operator and for legal review. **This is not legal advice and not
a certification.** Everything marked **[TO BE CONFIRMED]** must be decided or verified before a
pilot; none of these placeholders appear in the app. The PWA's own drafts live in `docs/privacy/`
and are unchanged (the PWA has no account feature).

## 1. Services involved (only in a build configured with the owner's Firebase project)

| Service | Purpose | Data | Region / transfer | Contract / DPA |
|---|---|---|---|---|
| Google Sign-In via Android Credential Manager (Google Play services) | Choose a Google account, obtain a Google ID token | Google account id, name, email, profile photo URL inside the ID token (on device) | Google **[TO BE CONFIRMED]** | Google terms **[TO BE CONFIRMED]** |
| Firebase Authentication (Google) | Firebase account for the user (UID) | UID, email, display name, photo URL, sign-in times, provider, IP addresses in logs | Google; international transfer **[TO BE CONFIRMED]** | Firebase Data Processing and Security Terms **[TO BE CONFIRMED]** |
| Cloud Firestore (Google) | Account-backed copy of Missions, steps, finalized Sessions, three settings, sync metadata, tombstones | see section 2 | European location chosen at creation **[TO BE CONFIRMED]**; support/sub-processors **[TO BE CONFIRMED]** | same **[TO BE CONFIRMED]** |
| Firebase App Check with Play Integrity (Google) | Attest genuine app and device | device/app integrity verdicts, app id | Google **[TO BE CONFIRMED]** | same **[TO BE CONFIRMED]** |
| Cloud Functions (Google), if deployed | Recursive deletion of `/users/{uid}` after account deletion | UID; operational logs (no content) | same region as Firestore **[TO BE CONFIRMED]** | same **[TO BE CONFIRMED]** |

No analytics, crash reporting, advertising or tracking SDK is added. Firebase Analytics is not a
dependency. The Cloudflare Worker / Gemini processing for AI planning is unchanged and separate.

## 2. Categories

**Synchronized with the account (only after sign-in and the user's confirmation for existing data):**
Missions and steps (titles, tasks, reasons, schedule, tools and recommended apps as text,
progress entries), finalized Focus Sessions (task, durations, times, local day and time zone,
outcome, rating, review note, protection mode used, origin), Quick Focus default duration, motion
preference, lock-screen detail level, sync metadata (migration record, tombstones, versions).

**Local only:** installed and selected apps, UsageStats and opening attempts, permission states,
DND state, exact-alarm capability, battery optimization, notification channels, WorkManager jobs,
reminders and Geplanter Fokus, the active Session and its timing, drafts, photos, attachments,
speech audio, AI request state, diagnostics. Not uploaded to Firebase by design (no field exists;
the Rules refuse unknown fields).

**Sent to the AI only when requested:** unchanged from v20 (goal, type, confidence, Zeitplan, an
explicitly selected photo on "Mission planen"; one Mission's data on "Plan mit AI anpassen").

**Stored on the device about the account:** the Firebase UID in the local binding (Room, included
in OS backup), two flags (guest choice, explicit sign-out; not backed up), Firebase Auth's own
session state (managed by the SDK; not included in OS backup by the app's backup rules). No
token, email or name is written to Room or DataStore by the app.

## 3. Legal basis

**[TO BE CONFIRMED]** for each purpose (account, synchronization, App Check, deletion function).
The account is optional; the app is fully usable without it.

## 4. User controls (implemented)

- Choose guest mode; sign in only by tapping "Mit Google anmelden".
- Preview and explicit confirmation before local data is linked; "Später" and backup export first.
- Manual synchronization; visible pending changes, last sync, failures.
- Local backup export (unchanged envelope) independent of the account.
- Sign-out (cloud data stays; local data stays or is removed by choice).
- Account deletion in the app with reauthentication; optional local deletion.
- Full local deletion (also signs the device out; cloud data stays).

## 5. Retention

Cloud data stays until the user deletes the account **[TO BE CONFIRMED: retention for inactive
accounts]**. Firebase Authentication and Cloud Functions logs: provider defaults **[TO BE CONFIRMED]**.
Tombstones are kept with the account data and deleted with it.

## 6. Open questions for the processor register

1. Firebase contracting entity and DPA acceptance **[TO BE CONFIRMED]**.
2. Firestore location and sub-processor list **[TO BE CONFIRMED]**.
3. International transfers for Authentication, App Check and support access **[TO BE CONFIRMED]**.
4. Whether Firebase Authentication data for EU users is processed outside the EU **[TO BE CONFIRMED]**.
5. Google Play Data Safety declarations for account data and synced content **[TO BE CONFIRMED]**.
6. Incident contact and breach process **[TO BE CONFIRMED]**.
