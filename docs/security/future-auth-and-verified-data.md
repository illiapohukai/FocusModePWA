# Future Authentication and Verified Data (design only — not implemented)

Status: architecture proposal for a later version. Nothing in v17.17 implements accounts, verified data,
Leagues, subscriptions or quotas. v17.17 only adds the guard rails described in §1.

## 1. Trust classes (enforced today)

| Class | Examples | Source of truth | May feed competitive/paid features? |
|---|---|---|---|
| A. User-editable data | Mission title, tasks, notes, schedule preferences, selected apps, settings, local Session History | Device (`localStorage`), backups | **Never** |
| B. Local derived data | Local streak, totals, progress, Activity, achievements | Computed on the device from A | **Never** |
| C. Server-authoritative data (future) | Verified Session completion, verified streak, League score/rank, entitlement, subscription tier, AI quota, competitive achievements | Authenticated backend only | Only this class |

Code anchors in v17.17:

- `src/data/schema-versions.js`: `TRUST_CLASSES`, `DATA_TRUST_MAP`, `classifyTrust()`,
  `isServerAuthoritative()` (returns `false` for every local domain) and `RESERVED_SERVER_FIELDS`.
- `src/data/data-validator.js` `inspectUntrustedStructure()`: an imported backup containing any reserved
  field name (`verified*`, `serverSessionId`, `serverSignature`, `leagueScore`, `leagueRank`, `entitlement(s)`,
  `subscription*`, `aiQuota`, …) is rejected (`import_reserved_server_field`).
- Exports carry `"trust": "local_backup_unverified"`; the import preview states that History, streak and
  progress from a file are not verified records.
- `src/features/league/league.js` is a static, labelled demo (`isDemo: true`).

Rule for every future feature: a value may be shown as verified, ranked or paid **only** if it was returned
by the authenticated backend for the signed-in account. Local values and imported files are display-only.

## 2. Identity

- Accounts with **passkeys (WebAuthn)** as the primary method; email magic link or OAuth as recovery.
  No passwords stored by us if avoidable.
- Short-lived access tokens in memory; refresh via `HttpOnly`, `Secure`, `SameSite=Strict` cookie on the API
  origin. Never store tokens in `localStorage`.
- Account ownership checked server-side on every write (object-level authorisation, OWASP API1).

## 3. Server-authoritative Session ledger

Recommended flow:

1. Authenticated user starts a Session → `POST /sessions` → server creates `sessionId` (random, server-issued)
   and records `startedAt` from the **server clock**, owner and planned duration.
2. Client runs the timer (may be offline; see §5).
3. Client submits completion → `POST /sessions/{id}/complete` with an **idempotency key**.
4. Server checks: ownership, Session exists and is open, elapsed server time within an allowed window
   (≥ minimum, ≤ planned + tolerance), not already completed (unique constraint on `sessionId` completion),
   rate limits per account.
5. Server updates verified streak and score **in the same transaction** as the completion record.
6. Client displays server-returned values; local values remain clearly labelled "Lokal".

Duplicate completion: unique `(sessionId)` completion row + idempotency key table (key → stored response,
24 h TTL). Replays return the stored response, never a second credit.

## 4. Derived verified values

- Verified streak: computed server-side from completed ledger entries in the user's declared time zone, with
  the time zone change rate-limited (prevents day-boundary gaming).
- League score: computed server-side from ledger entries only; recalculable; anomaly flags (impossible
  density, overlapping Sessions, clock skew).
- Achievements with competitive value: server-derived only.

## 5. Offline completion — limitations

Offline Sessions cannot be verified at completion time. Options: (a) count offline Sessions only locally;
(b) accept late submission within a short window with stricter plausibility checks and no League credit
beyond a cap. Either way the server never trusts client timestamps; perfect anti-cheat is not achievable for
a client-run timer, so competitive rules must tolerate this.

## 6. Subscriptions and AI quota

- Entitlements stored server-side, updated only from **signature-verified** payment-provider webhooks,
  processed idempotently (event ID table), reconciled periodically against the provider.
- AI quota counted at the Worker/API per account (and per IP for anonymous use), not in the client.
- The client may cache entitlements for display only; the API re-checks on every request.

## 7. Local data and sync

- Local data stays usable without an account.
- Optional sync: server stores Missions as user-editable data (class A) with `updatedAt` + version for
  conflict detection; conflicts resolved per field with "last writer wins" for text and union for
  completed-step sets, shown to the user when ambiguous.
- Sync never upgrades class A/B data to class C.

## 8. Backups

| Backup class | Content | Use | Accepted for scoring? |
|---|---|---|---|
| Local backup (today) | Editable JSON incl. local History and streak inputs | Restore on any device | **No** |
| Verified account data (future) | Server-side records | Restored by signing in | Yes (it never leaves server authority) |

A local JSON file can never replace verified account data. No client-side signature scheme is planned; a
server-signed export (if ever needed) would be verified by the server, not the client.

## 9. Export, portability and deletion

- Account export (Art. 20 GDPR): server provides a machine-readable export of account data incl. ledger.
- Account deletion: deletes profile, ledger, derived scores, entitlements (subject to legal retention such as
  invoices) and revokes sessions/passkeys; local device data is deleted separately by the user.
- Privacy notice, retention policy and processor register must be updated before launch.
