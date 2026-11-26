# Account deletion (v21)

## Client flow (implemented)

Settings → Konto & Synchronisierung → **Konto löschen**:

1. A dialog explains what is deleted (Focus Mode+ account and all cloud data: Missions, steps,
   Sessions, settings, sync data; the Google account itself stays), offers "Vorher lokales Backup
   exportieren" and the option "Auch lokale Daten auf diesem Gerät löschen" (off by default).
2. **Recent sign-in**: the user confirms with Google again (Credential Manager +
   `FirebaseUser.reauthenticate`). Canceling changes nothing. A different Google account is refused.
3. Synchronization stops; the local binding becomes `DELETION_PENDING` (nothing uploads any more,
   also after a restart).
4. The account's Firestore documents are deleted by the client, children before parents, in
   batches: steps, Missions, Sessions, tombstones, settings, syncMeta, then `/users/{uid}`. The
   Rules allow only the owner to delete. The client then checks that nothing is left.
5. The Firebase Authentication user is deleted; Credential Manager state is cleared.
6. Locally: either the full personal-data deletion runs (if chosen), or the binding and sync
   metadata are removed and the data stays as guest data.

Interrupted deletions (offline, partial failure) leave the binding in `DELETION_PENDING`; the
Account screen shows "Kontolöschung nicht abgeschlossen" with "Löschung fortsetzen". Already
deleted documents stay deleted. If Firebase reports that a recent sign-in is required, the user
is asked to confirm again.

Owner: `AccountLifecycle.deleteAccount` (domain), Firebase calls in `FirebaseAccountRepository`
and `FirestoreRemoteSyncStore`. Tests: `AccountLifecycleTest` (only the owner's data is deleted,
canceling deletes nothing, interrupted deletion stays pending and resumes, local data kept or erased).

## Trusted server boundary (implemented, not deployed)

`firebase/functions/index.js` defines `deleteUserDataOnAccountDeletion`, a Cloud Functions (1st gen)
Authentication `onDelete` trigger that calls `deleteUserData(db, uid)` in
`firebase/functions/delete-user-data.js`: `recursiveDelete` of `/users/{uid}` with the Admin SDK.
It removes anything the client could not (documents written by another device meanwhile, an app
killed between steps 4 and 5, collections added by later versions). The uid comes only from the
deleted Auth user. Emulator tests (`firebase/test/account-deletion.test.cjs`): the whole tree incl.
subcollections is deleted, other accounts are untouched, deletion is idempotent, malformed uids are
refused.

**Production recursive deletion is not complete until this function is deployed.** Without it,
the client-side deletion still removes every document the app knows about, but documents written
concurrently by another signed-in device could remain.

## Deployment requirements (owner)

1. Blaze (pay-as-you-go) plan on the Firebase project (Cloud Functions requirement) and budget alerts.
2. Choose the region next to the Firestore location (`europe-west3` is preset) **[TO BE CONFIRMED]**.
3. `cd firebase/functions && npm install` (dependencies: `firebase-admin`, `firebase-functions`).
4. `cd firebase && npx firebase-tools@15.37.0 deploy --only functions:focus-mode --project <your-project-id>`.
5. Verify in staging: delete a test account in the app, then confirm `/users/{uid}` is gone in the
   Firestore console and the function log shows no error (logs must not contain user content).
6. Document retention of function logs **[TO BE CONFIRMED]**.

## What is never deleted by sign-out

Signing out never deletes local or cloud data. Full local deletion never deletes cloud data.
Only "Konto löschen" deletes cloud data, after reauthentication.
