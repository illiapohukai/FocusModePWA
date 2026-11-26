# Sync conflict policy (v21)

Deterministic rules, implemented in `core/domain/.../sync/SyncConflictPolicy.kt` and applied by
`RoomSyncLocalStore.applyRemote` inside one Room transaction. The same inputs give the same
result on every device. Tested in `SyncConflictPolicyTest` (pure) and `SyncEngineTest` (Room +
server stand-in).

## Order of a synchronization

1. Download tombstones, Missions (with steps) and Sessions changed since the last cursor (minus a
   60 s overlap; applying is idempotent).
2. Merge locally (rules below).
3. Upload the outbox (Missions with the next `syncVersion`, Sessions, tombstones).
4. Settings profile.

Downloading first means a stale device never overwrites newer cloud data and never resurrects a
deleted Mission.

## A. Missions

- **Base version**: `sync_entity_state.remote_version` is the version the server last confirmed.
  An upload writes exactly `base + 1`; the Rules refuse anything else (optimistic concurrency).
- **No local change pending** → the newer remote version replaces the local copy.
- **Local change pending, remote unchanged since base** → keep local; it uploads.
- **Own write whose acknowledgement was lost** (remote `deviceMutationId` equals a pending
  mutation) → treated as confirmed; only newer local mutations remain.
- **Both changed** → last write wins by `updatedAt` for the Mission's simple fields (title,
  status, schedule, …); ties are broken by the larger device mutation id, so every device picks
  the same winner. On top of the winner:
  - a step completed on either side stays completed; a step the server already holds as completed
    keeps the server's copy (completed steps are immutable in the Rules as well);
  - completed steps that exist only on the losing side are kept;
  - the winner's open steps follow in its order; positions are renumbered without gaps;
  - progress entries are a union by Session id;
  - current step and status are normalized (all steps done → Mission completed).
  The merged Mission is uploaded as the next version.
- **Irreconcilable structural edits** (both sides changed the step set, e.g. an adaptation on one
  device and a step completion on another) → the newer plan wins as above and a conflict record
  (`sync_conflicts`: entity, time, `newer_remote_plan_kept` / `newer_local_plan_kept`) is kept;
  the Account screen counts resolved conflicts. No content is stored in the record.
- **Two-active rule after merging**: if more than two Missions are active, the most recently
  updated stay active and the others are archived (Missions with a running Session are never
  archived); the archive itself synchronizes.
- **A Mission used by the running Session** is not changed by a download until the Session ends.
- **Step id clash** (a remote step id already belongs to another local Mission) → that Mission is
  not applied and a conflict record is kept; nothing local is overwritten.

## B. Completed Sessions

Append-only by Session id. A Session uploads only once it is final (a Mission Session after its
review). An existing local record is never overwritten; the only accepted change is a remote
review for a Session that is still awaiting its review locally. The Rules allow updating only
`reviewNote` and sync metadata.

## C. Mission steps

Replacing open steps (adaptation) writes the whole step set with a new Mission version, so a
replacement based on an old version is refused by the server and merged first. Completed steps
are never reopened or rewritten (Rules: `completedStepKept`).

## D. Settings

Quick Focus default duration, motion and lock-screen detail level: last write wins by **server
arrival**. A local change (fingerprint differs from the last synchronized one) is uploaded after
downloading, so the later write is the one the server keeps; an unchanged device applies a newer
remote profile. A device that adopts an account (empty device, "Cloud-Daten verwenden") takes the
cloud profile; a device that links its own data uploads its profile.

## E. Deletion

A Mission deletion writes a tombstone (`/users/{uid}/tombstones/mission_{id}`), removes the Mission
and its steps in one batch and keeps a local tombstone. A tombstone wins over any concurrent edit:
the receiving device deletes the Mission, drops its pending edits and records
`deleted_on_other_device`. The Rules refuse to create a Mission document whose tombstone exists,
so an offline device can never resurrect it. History keeps its Sessions.

## F. Active Session

Never synchronized and never remotely authoritative. It belongs to the device that started it;
no other device can resume, cancel, complete or change it. Only the finalized (and reviewed)
result synchronizes. Cross-device active-Session handoff is deferred.
