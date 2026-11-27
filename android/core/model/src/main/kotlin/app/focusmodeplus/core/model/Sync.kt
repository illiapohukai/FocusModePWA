package app.focusmodeplus.core.model

import java.time.Instant
import java.time.LocalDate

/** Synchronized entity kinds (v21). Everything else stays on the device. */
enum class SyncEntityType(val wire: String) {
    MISSION("mission"), SESSION("session"), SETTINGS("settings");
    companion object { fun fromWire(v: String?): SyncEntityType? = entries.firstOrNull { it.wire == v } }
}

enum class SyncOperation(val wire: String) {
    UPSERT("upsert"), DELETE("delete");
    companion object { fun fromWire(v: String?): SyncOperation? = entries.firstOrNull { it.wire == v } }
}

/**
 * How the local dataset relates to an account. No binding row = guest data that is never
 * uploaded. Only [MIGRATING] and [ACTIVE] record outbox mutations.
 */
enum class BindingStatus(val wire: String) {
    /** The user confirmed "Lokale Daten mit diesem Konto verbinden"; upload and verification are running. */
    MIGRATING("migrating"),
    /** Local data belongs to the account and synchronizes. */
    ACTIVE("active"),
    /** Account deletion started; nothing is uploaded any more. */
    DELETION_PENDING("deletion_pending");
    companion object { fun fromWire(v: String?): BindingStatus? = entries.firstOrNull { it.wire == v } }
}

/** Privacy-safe failure classes. Never contains messages, tokens or content. */
enum class SyncFailure(val wire: String) {
    OFFLINE("offline"),
    UNAUTHENTICATED("unauthenticated"),
    PERMISSION_DENIED("permission_denied"),
    QUOTA("quota"),
    INVALID_DATA("invalid_data"),
    SERVER("server"),
    UNKNOWN("unknown");
    companion object { fun fromWire(v: String?): SyncFailure? = entries.firstOrNull { it.wire == v } }
}

enum class SyncPhase {
    /** Firebase is not configured in this build. */
    UNAVAILABLE,
    /** Guest mode: nothing synchronizes. */
    SIGNED_OUT,
    /** Signed out, but local data still belongs to an account; changes upload at the next sign-in. */
    PAUSED,
    /** Signed in; the user has not decided what happens with existing local data. */
    DECISION_REQUIRED,
    /** Signed in with a different account than the one the local data belongs to. */
    ACCOUNT_MISMATCH,
    IDLE,
    SYNCING,
    MIGRATING,
    OFFLINE,
    FAILED,
    DELETING,
}

enum class MigrationStatus { NOT_REQUIRED, PENDING_DECISION, IN_PROGRESS, COMPLETE }

data class SyncState(
    val phase: SyncPhase = SyncPhase.SIGNED_OUT,
    /** Outbox mutations (and a changed settings profile) not yet confirmed by the server. */
    val pendingChanges: Int = 0,
    /** Mutations that failed repeatedly and wait for a manual retry. */
    val parkedChanges: Int = 0,
    val lastSuccessAt: Instant? = null,
    val conflictsResolved: Int = 0,
    val failure: SyncFailure? = null,
    val migration: MigrationStatus = MigrationStatus.NOT_REQUIRED,
)

/** What signing in would connect, shown before the user decides (nothing changes until then). */
data class MigrationPreview(
    val missions: Int,
    val missionSteps: Int,
    /** Finalized Sessions that would be connected. */
    val sessions: Int,
    val historyFrom: LocalDate?,
    val historyTo: LocalDate?,
    /** Derived from the Sessions (counting ones only); never uploaded as a total. */
    val focusMinutes: Int,
    /** Mission Sessions still waiting for their step review; they synchronize after the review. */
    val awaitingReview: Int = 0,
    /** Records whose ids cannot be stored in the cloud; they stay on this device. */
    val notSyncable: Int = 0,
    val includedSettings: List<String> = SYNCED_SETTINGS_LABELS,
    val excludedLocalData: List<String> = LOCAL_ONLY_LABELS,
    /** null = the cloud could not be checked (offline). */
    val remoteMissions: Int? = null,
    val remoteSessions: Int? = null,
    /** Active Missions that would be archived to keep the two-active rule after merging. */
    val missionsArchivedByLimit: Int = 0,
) {
    val hasLocalData: Boolean get() = missions > 0 || sessions > 0
    val remoteHasData: Boolean? get() = if (remoteMissions == null || remoteSessions == null) null else remoteMissions + remoteSessions > 0

    companion object {
        val SYNCED_SETTINGS_LABELS = listOf("Standarddauer für Quick Focus", "Bewegung (reduziert)", "Inhalt auf dem Sperrbildschirm")
        val LOCAL_ONLY_LABELS = listOf(
            "Installierte und ausgewählte Apps", "Nutzungsdaten und Berechtigungen", "Erinnerungen und Geplanter Fokus",
            "Laufende Sitzung", "Entwürfe, Fotos und Anhänge",
        )
    }
}

/** A decision only the user can make after sign-in. The app never resolves these silently. */
sealed interface AccountDataDecision {
    val uid: String

    /** Local guest data exists and the account is not linked yet. */
    data class LinkLocalData(override val uid: String, val preview: MigrationPreview) : AccountDataDecision

    /** Local data belongs to another account than the one that just signed in. */
    data class AccountSwitch(
        override val uid: String,
        val pendingChangesOfPreviousAccount: Int,
        val localMissions: Int,
        val localSessions: Int,
    ) : AccountDataDecision
}

enum class AccountSwitchChoice {
    /** Sign the new account out again; local data stays with the previous account. */
    KEEP_SEPARATE,
    /** Remove local personal data from this device, then continue with the new account. */
    DELETE_LOCAL,
    /** Replace local data with the new account's cloud data. */
    USE_NEW_ACCOUNT_DATA,
}

enum class SyncSkipReason { UNAVAILABLE, NOT_SIGNED_IN, NOT_LINKED, ACCOUNT_MISMATCH, DELETION_PENDING, OFFLINE, ACTIVE_SESSION, ACCOUNT_CHANGED }

sealed interface SyncResult {
    data class Success(val uploaded: Int = 0, val downloaded: Int = 0, val conflicts: Int = 0) : SyncResult
    data class Skipped(val reason: SyncSkipReason) : SyncResult
    data class Failed(val failure: SyncFailure, val retryable: Boolean) : SyncResult
}
