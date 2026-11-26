# Future verified League (not implemented)

v21 only **reserves and protects** the field names a future server-authoritative system needs.
There is no League UI, no points, no ranks, tiers or seasons, no verified streak and no public
profile in the app, and no producer of any of these values exists.

## Reserved server-authoritative fields

`verifiedFocusMinutes`, `verifiedSessionCount`, `verifiedStreak`, `verifiedSession`,
`leaguePoints`, `leagueRank`, `leagueTier`, `leagueSeason`, `subscriptionTier`, `entitlement`,
`aiQuota`, `moderationStatus`, `adminRole`, `serverTrust`.

- Listed in `RemoteSchema.RESERVED_SERVER_FIELDS`; the client codec never writes them (tested).
- `firebase/firestore.rules` refuses any client write that contains one, on every document type,
  and refuses every path outside the known collections (e.g. `/users/{uid}/verified/...`,
  `/leagues/...`) — tested for each field against the emulator.
- Downloads with an unknown or reserved field are invalid and never applied.
- Backup imports never accept them (v17.17 / v20 behavior, unchanged).

## Why synchronized History is not verified History

Synchronized Sessions are the user's own records: they are written by the client, can come from
an import, and are never checked by a server. They are labelled and stored as ordinary,
unverified History (`Verification.LOCAL_UNVERIFIED`; the remote model has no verification field).
Linking local data to an account explicitly creates no League points.

## What a verified League would need (later version)

1. A **server-authoritative Session ledger**: Sessions started and finished through a trusted
   backend (e.g. Cloud Functions with the Admin SDK) with server timestamps, plausibility checks
   and replay protection; the client never writes ledger entries.
2. App Check enforcement (genuine app) plus Authentication (genuine user) on those endpoints.
3. Server-computed `verified*` and `league*` values in server-only documents.
4. Abuse handling (`moderationStatus`) and an appeal path.
5. Privacy review for any public or shared ranking.

League security is **not** complete before such a ledger exists.
