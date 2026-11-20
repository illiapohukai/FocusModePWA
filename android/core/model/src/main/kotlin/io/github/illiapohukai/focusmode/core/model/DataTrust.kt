package io.github.illiapohukai.focusmode.core.model

/**
 * Data classification (v18). Mirrors the PWA trust map (`src/data/schema-versions.js`):
 * nothing on this device is server-authoritative today.
 *
 * - [USER_EDITABLE]: Mission titles, tasks, schedules, selected apps, settings.
 * - [LOCAL_DERIVED]: streak, totals, Activity, progress. Computed on device from local records,
 *   never trusted for competition, never accepted from an import as a "verified" value.
 * - [SERVER_AUTHORITATIVE]: verified Sessions, verified streak, League score, rank, subscription,
 *   paid entitlement, AI quota. Reserved for a future authenticated backend; no producer exists.
 */
enum class TrustLevel { USER_EDITABLE, LOCAL_DERIVED, SERVER_AUTHORITATIVE }

/** Every Session record on this device is unverified. Only a future server may produce VERIFIED. */
enum class Verification { LOCAL_UNVERIFIED, SERVER_VERIFIED }

/** Where a record came from. Imported records stay unverified and are labeled as such. */
enum class DataOrigin { LOCAL, IMPORTED_PWA }

/**
 * Future server-authoritative boundary. These types intentionally have no local producer:
 * a repository may only construct them from an authenticated server response.
 */
sealed interface ServerAuthoritative {
    data class VerifiedStreak(val days: Int, val verifiedAtEpochMs: Long) : ServerAuthoritative
    data class LeagueStanding(val score: Long, val rank: Int, val season: String) : ServerAuthoritative
    data class Entitlement(val productId: String, val active: Boolean) : ServerAuthoritative
    data class AiQuota(val remaining: Int, val resetsAtEpochMs: Long) : ServerAuthoritative
}
