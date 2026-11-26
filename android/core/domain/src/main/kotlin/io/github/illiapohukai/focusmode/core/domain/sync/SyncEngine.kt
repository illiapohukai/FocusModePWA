package io.github.illiapohukai.focusmode.core.domain.sync

import io.github.illiapohukai.focusmode.core.domain.account.AccountRepository
import io.github.illiapohukai.focusmode.core.domain.account.LocalEraseAccountHook
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.AccountDataDecision
import io.github.illiapohukai.focusmode.core.model.AccountState
import io.github.illiapohukai.focusmode.core.model.BindingStatus
import io.github.illiapohukai.focusmode.core.model.MigrationPreview
import io.github.illiapohukai.focusmode.core.model.MigrationStatus
import io.github.illiapohukai.focusmode.core.model.SyncEntityType
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import io.github.illiapohukai.focusmode.core.model.SyncOperation
import io.github.illiapohukai.focusmode.core.model.SyncPhase
import io.github.illiapohukai.focusmode.core.model.SyncResult
import io.github.illiapohukai.focusmode.core.model.SyncSkipReason
import io.github.illiapohukai.focusmode.core.model.SyncState
import io.github.illiapohukai.focusmode.core.model.activeUid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one synchronization owner and the one local-migration owner (v21).
 *
 * - Guest data is never uploaded: only a dataset bound to the signed-in uid synchronizes, and
 *   binding guest data needs the user's explicit "Lokale Daten mit diesem Konto verbinden".
 * - Order of a sync: download and merge first (so a stale device never overwrites newer cloud
 *   data or resurrects a deleted Mission), then upload the outbox, then the settings profile.
 * - Migration: one transaction enqueues every local Mission and finalized Session; uploads are
 *   idempotent (document id = entity id); the migration is complete only after the server is
 *   shown to hold every migrated id. Local data is never deleted by a migration.
 * - Every remote call is followed by an account check, and every local write re-checks the
 *   binding inside its transaction, so a sign-out or account switch mid-sync cannot leak data.
 * - Active Sessions never synchronize; a Session uploads once finalized and reviewed.
 * - All work runs under one mutex: a duplicate tap, the worker and a manual sync never interleave.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val accounts: AccountRepository,
    private val local: SyncLocalStore,
    private val remote: RemoteSyncStore,
    private val scheduler: SyncScheduler,
    private val preferences: PreferencesRepository,
    private val network: NetworkStatus,
    private val clock: FocusClock,
) : SyncRepository, LocalEraseAccountHook {

    private val mutex = Mutex()
    private val busy = MutableStateFlow<SyncPhase?>(null)
    private val lastFailure = MutableStateFlow<SyncFailure?>(null)
    private val settingsDirty = MutableStateFlow(false)
    private val _decision = MutableStateFlow<AccountDataDecision?>(null)
    private val _state = MutableStateFlow(SyncState(phase = if (accounts.configured) SyncPhase.SIGNED_OUT else SyncPhase.UNAVAILABLE))
    private val ensuredProfiles = HashSet<String>()
    private var started = false

    override val syncState: StateFlow<SyncState> = _state.asStateFlow()
    override val decision: StateFlow<AccountDataDecision?> = _decision.asStateFlow()

    /** Starts observing account, binding, outbox and settings. Called once from the Application. */
    fun start(scope: CoroutineScope) {
        if (started || !accounts.configured) return
        started = true
        scope.launch {
            val base = combine(accounts.accountState, local.observeBinding(), local.observeHasLocalData(), network.online) { a, b, has, online -> Quad(a, b, has, online) }
            val counts = combine(local.observePendingCount(), local.observeParkedCount(), busy, lastFailure, settingsDirty) { p, k, busyPhase, f, dirty -> Counts(p, k, busyPhase, f, dirty) }
            combine(base, counts) { q, c -> computeState(q, c) }.distinctUntilChanged().collect { _state.value = it }
        }
        // The account at start time (app launch with an existing sign-in) never prompts; any later
        // sign-in does, even if it lands before this collector subscribes. Captured synchronously.
        val startUid = accounts.accountState.value.activeUid
        var seenStart = false
        scope.launch {
            accounts.accountState.map { it.activeUid }.distinctUntilChanged().collect { uid ->
                val prompt = seenStart || uid != startUid
                seenStart = true
                onAccountChanged(uid, promptForDecision = prompt)
            }
        }
        scope.launch {
            local.observePendingCount().distinctUntilChanged().collect { count -> if (count > 0) scheduleIfBound(delayMs = COALESCE_MS) }
        }
        scope.launch {
            preferences.preferences.map { SyncedSettings.of(it).fingerprint }.distinctUntilChanged().drop(1).collect {
                refreshSettingsDirty()
                scheduleIfBound(delayMs = COALESCE_MS)
            }
        }
        scope.launch { local.observeBinding().collect { refreshSettingsDirty() } }
    }

    private data class Quad(val account: AccountState, val binding: AccountBinding?, val hasLocal: Boolean, val online: Boolean)
    private data class Counts(val pending: Int, val parked: Int, val busy: SyncPhase?, val failure: SyncFailure?, val settingsDirty: Boolean)

    private fun computeState(q: Quad, c: Counts): SyncState {
        val uid = q.account.activeUid
        val b = q.binding
        val phase = when {
            !accounts.configured -> SyncPhase.UNAVAILABLE
            c.busy != null -> c.busy
            uid == null -> if (b != null && b.status != BindingStatus.DELETION_PENDING) SyncPhase.PAUSED else SyncPhase.SIGNED_OUT
            b == null -> if (q.hasLocal) SyncPhase.DECISION_REQUIRED else SyncPhase.IDLE
            b.uid != uid -> SyncPhase.ACCOUNT_MISMATCH
            b.status == BindingStatus.DELETION_PENDING -> SyncPhase.DELETING
            b.status == BindingStatus.MIGRATING -> SyncPhase.MIGRATING
            !q.online -> SyncPhase.OFFLINE
            c.failure != null && c.failure != SyncFailure.OFFLINE -> SyncPhase.FAILED
            else -> SyncPhase.IDLE
        }
        val migration = when {
            b?.status == BindingStatus.MIGRATING -> MigrationStatus.IN_PROGRESS
            b?.migrationCompletedAt != null -> MigrationStatus.COMPLETE
            b == null && q.hasLocal && uid != null -> MigrationStatus.PENDING_DECISION
            else -> MigrationStatus.NOT_REQUIRED
        }
        val ownPending = if (b != null && (uid == null || b.uid == uid)) c.pending + (if (c.settingsDirty) 1 else 0) else 0
        return SyncState(
            phase = phase,
            pendingChanges = ownPending,
            parkedChanges = if (b != null && (uid == null || b.uid == uid)) c.parked else 0,
            lastSuccessAt = b?.lastSyncAt?.takeIf { uid == null || b.uid == uid },
            conflictsResolved = b?.conflictsResolved ?: 0,
            failure = c.failure,
            migration = migration,
        )
    }

    private suspend fun refreshSettingsDirty() {
        val b = local.binding()
        settingsDirty.value = b != null && b.status == BindingStatus.ACTIVE &&
            b.settingsFingerprint != SyncedSettings.of(preferences.current()).fingerprint
    }

    private suspend fun scheduleIfBound(delayMs: Long) {
        val uid = accounts.accountState.value.activeUid ?: return
        val b = local.binding() ?: return
        if (b.uid == uid && b.status != BindingStatus.DELETION_PENDING) scheduler.schedule(uid, delayMs)
    }

    // ------------------------------------------------------------------------------ account changes

    private suspend fun onAccountChanged(uid: String?, promptForDecision: Boolean) {
        if (uid == null) {
            scheduler.cancel()
            _decision.value = null
            lastFailure.value = null
            return
        }
        // Disabled, deleted or revoked accounts are detected by Firebase on refresh (best effort).
        if (network.online.value) runCatching { accounts.refreshAccount() }
        if (accounts.accountState.value.activeUid != uid) return
        val b = local.binding()
        when {
            b == null -> {
                val summary = local.localSummary()
                if (summary.isEmpty) {
                    initialSync(uid) // nothing local to attach: restore the account's cloud data
                } else if (promptForDecision) {
                    _decision.value = AccountDataDecision.LinkLocalData(uid, prepareMigration(uid))
                }
            }
            b.uid == uid -> {
                _decision.value = null
                if (b.status != BindingStatus.DELETION_PENDING) scheduler.schedule(uid)
            }
            else -> {
                scheduler.cancel()
                val summary = local.localSummary()
                _decision.value = AccountDataDecision.AccountSwitch(uid, local.pendingCount(b.uid), summary.missions, summary.sessions)
            }
        }
    }

    override fun postponeDecision() { _decision.value = null }

    // ------------------------------------------------------------------------------ migration

    override suspend fun prepareMigration(uid: String): MigrationPreview {
        val s = local.localSummary()
        var missions: Int? = null
        var sessions: Int? = null
        if (remote.available && network.online.value && accounts.accountState.value.activeUid == uid) {
            runCatching {
                missions = remote.count(uid, RemoteCollection.MISSIONS)
                sessions = remote.count(uid, RemoteCollection.SESSIONS)
            }
        }
        return MigrationPreview(
            missions = s.missions, missionSteps = s.missionSteps, sessions = s.sessions,
            historyFrom = s.historyFrom, historyTo = s.historyTo, focusMinutes = s.focusMinutes,
            awaitingReview = s.awaitingReview, notSyncable = s.notSyncable,
            remoteMissions = missions, remoteSessions = sessions,
        )
    }

    override suspend fun migrateLocalData(uid: String): SyncResult {
        val early = mutex.withLock {
            if (accounts.accountState.value.activeUid != uid) return@withLock SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN)
            val b = local.binding()
            when {
                b != null && b.uid != uid -> SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH)
                b?.status == BindingStatus.DELETION_PENDING -> SyncResult.Skipped(SyncSkipReason.DELETION_PENDING)
                b?.status == BindingStatus.ACTIVE -> SyncResult.Success()
                else -> {
                    // A resumed or repeated confirmation keeps the same migration id: idempotent.
                    val migrationId = b?.migrationId ?: newId()
                    if (!local.beginMigration(uid, migrationId, clock.now())) SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH)
                    else { _decision.value = null; null }
                }
            }
        }
        return if (early == null || early is SyncResult.Success) synchronize() else early
    }

    override suspend fun initialSync(uid: String): SyncResult {
        val early = mutex.withLock {
            if (accounts.accountState.value.activeUid != uid) return@withLock SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN)
            val b = local.binding()
            when {
                b != null && b.uid != uid -> SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH)
                b != null -> null
                !local.localSummary().isEmpty -> SyncResult.Skipped(SyncSkipReason.NOT_LINKED) // never attach existing data implicitly
                else -> { local.activate(uid, clock.now()); _decision.value = null; null }
            }
        }
        return early ?: synchronize()
    }

    override suspend fun adoptCloudData(uid: String): SyncResult = mutex.withLock {
        if (accounts.accountState.value.activeUid != uid) return@withLock SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN)
        if (!remote.available) return@withLock SyncResult.Skipped(SyncSkipReason.UNAVAILABLE)
        if (local.hasActiveSession()) return@withLock SyncResult.Skipped(SyncSkipReason.ACTIVE_SESSION)
        if (!network.online.value) return@withLock SyncResult.Failed(SyncFailure.OFFLINE, retryable = true)
        busy.value = SyncPhase.SYNCING
        try {
            scheduler.cancel()
            val (changes, cursors) = fetch(uid, null)
            guard(uid)
            val report = local.replaceWithRemote(uid, changes, clock.now(), cursors)
            if (!report.accepted) return@withLock SyncResult.Skipped(SyncSkipReason.ACCOUNT_CHANGED)
            _decision.value = null
            ensureProfile(uid)
            syncSettings(uid)
            local.recordSuccess(uid, clock.now())
            lastFailure.value = null
            scheduler.schedule(uid)
            SyncResult.Success(downloaded = report.missionsApplied + report.sessionsApplied, conflicts = report.conflicts)
        } catch (e: RemoteSyncException) {
            failed(uid, e.failure)
        } catch (_: AccountChanged) {
            SyncResult.Skipped(SyncSkipReason.ACCOUNT_CHANGED)
        } finally {
            busy.value = null
        }
    }

    override suspend fun unlinkAccount(): SyncResult = mutex.withLock {
        scheduler.cancel()
        local.clearAccountSyncState()
        _decision.value = null
        lastFailure.value = null
        SyncResult.Success()
    }

    override suspend fun beforeLocalErase(signOut: Boolean) {
        scheduler.cancel()
        if (signOut && accounts.accountState.value.user != null) runCatching { accounts.signOut() }
        mutex.withLock {
            local.clearAccountSyncState()
            _decision.value = null
            lastFailure.value = null
        }
    }

    override suspend fun retryParked() {
        val uid = accounts.accountState.value.activeUid ?: return
        local.retryParked(uid, clock.now())
        scheduler.schedule(uid)
    }

    // ------------------------------------------------------------------------------ sync

    override suspend fun synchronize(): SyncResult = run(download = true, upload = true)
    override suspend fun uploadPendingMutations(): SyncResult = run(download = false, upload = true)
    override suspend fun downloadRemoteChanges(): SyncResult = run(download = true, upload = false)

    private suspend fun run(download: Boolean, upload: Boolean): SyncResult {
        val uid = accounts.accountState.value.activeUid ?: return SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN)
        if (!remote.available) return SyncResult.Skipped(SyncSkipReason.UNAVAILABLE)
        return mutex.withLock { runLocked(uid, download, upload) }
    }

    private suspend fun runLocked(uid: String, download: Boolean, upload: Boolean): SyncResult {
        val b = local.binding() ?: return SyncResult.Skipped(SyncSkipReason.NOT_LINKED)
        if (b.uid != uid) return SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH)
        if (b.status == BindingStatus.DELETION_PENDING) return SyncResult.Skipped(SyncSkipReason.DELETION_PENDING)
        if (!network.online.value) {
            lastFailure.value = SyncFailure.OFFLINE
            return SyncResult.Failed(SyncFailure.OFFLINE, retryable = true)
        }
        busy.value = if (b.status == BindingStatus.MIGRATING) SyncPhase.MIGRATING else SyncPhase.SYNCING
        return try {
            guard(uid)
            ensureProfile(uid)
            var downloaded = 0
            var conflicts = 0
            if (download && b.status == BindingStatus.ACTIVE) {
                downloadLocked(uid, b).let { downloaded += it.missionsApplied + it.sessionsApplied + it.tombstonesApplied; conflicts += it.conflicts }
            }
            if (b.status == BindingStatus.MIGRATING) {
                remote.commit(uid, listOf(RemoteWrite.Put(migrationPath(b.migrationId!!), RemoteCodec(uid).migration(b.migrationId, false, 0, 0, b.migrationStartedAt ?: clock.now(), null, clock.now()))))
            }
            val uploaded = if (upload) uploadLocked(uid) else 0
            if (upload) syncSettings(uid)
            if (b.status == BindingStatus.MIGRATING) {
                if (!verifyMigration(uid, b)) {
                    lastFailure.value = null
                    return SyncResult.Failed(SyncFailure.SERVER, retryable = true)
                }
                local.binding()?.let { fresh ->
                    downloadLocked(uid, fresh).let { downloaded += it.missionsApplied + it.sessionsApplied + it.tombstonesApplied; conflicts += it.conflicts }
                }
                if (upload) uploadLocked(uid) // archive changes from the two-active rule after merging
            }
            guard(uid)
            local.recordSuccess(uid, clock.now())
            lastFailure.value = null
            SyncResult.Success(uploaded, downloaded, conflicts)
        } catch (e: RemoteSyncException) {
            failed(uid, e.failure)
        } catch (_: AccountChanged) {
            SyncResult.Skipped(SyncSkipReason.ACCOUNT_CHANGED)
        } finally {
            busy.value = null
        }
    }

    private suspend fun failed(uid: String, failure: SyncFailure): SyncResult.Failed {
        lastFailure.value = failure
        runCatching { local.recordFailure(uid, failure) }
        if (failure == SyncFailure.UNAUTHENTICATED || failure == SyncFailure.PERMISSION_DENIED) runCatching { accounts.refreshAccount() }
        val retryable = failure == SyncFailure.OFFLINE || failure == SyncFailure.SERVER || failure == SyncFailure.UNKNOWN || failure == SyncFailure.QUOTA
        return SyncResult.Failed(failure, retryable)
    }

    private class AccountChanged : Exception()

    private fun guard(uid: String) {
        if (accounts.accountState.value.activeUid != uid) throw AccountChanged()
    }

    private suspend fun ensureProfile(uid: String) {
        if (uid in ensuredProfiles) return
        if (remote.get(uid, RemotePath.Profile) == null) {
            guard(uid)
            remote.commit(uid, listOf(RemoteWrite.Put(RemotePath.Profile, RemoteCodec(uid).profile(clock.now(), clock.now()))))
        }
        ensuredProfiles += uid
    }

    // ------------------------------------------------------------------------------ download

    private suspend fun fetch(uid: String, b: AccountBinding?): Pair<RemoteChangeSet, SyncCursors> {
        val codec = RemoteCodec(uid)
        fun since(c: Long?) = c?.minus(CURSOR_OVERLAP_MS)
        val tombDocs = remote.list(uid, RemoteCollection.TOMBSTONES, changedSinceMs = since(b?.cursorTombstonesMs))
        guard(uid)
        val missionDocs = remote.list(uid, RemoteCollection.MISSIONS, changedSinceMs = since(b?.cursorMissionsMs))
        guard(uid)
        val sessionDocs = remote.list(uid, RemoteCollection.SESSIONS, changedSinceMs = since(b?.cursorSessionsMs))
        guard(uid)
        val missions = ArrayList<RemoteMission>()
        var missionCursor = b?.cursorMissionsMs
        var oldestInvalid: Long? = null
        missionDocs.forEach { doc ->
            val id = (doc.path as RemotePath.Doc).id
            val steps = remote.list(uid, RemoteCollection.STEPS, missionId = id)
            guard(uid)
            val decoded = codec.decodeMission(doc, steps)
            val ts = doc.fields["serverUpdatedAt"] as? Long
            if (decoded != null) missions += decoded else if (ts != null) oldestInvalid = minOf(oldestInvalid ?: ts, ts)
            if (ts != null) missionCursor = maxOf(missionCursor ?: ts, ts)
        }
        // An undecodable Mission (e.g. a step set that is not complete yet) is fetched again next time.
        oldestInvalid?.let { missionCursor = minOf(missionCursor ?: it, it - 1) }
        val sessions = sessionDocs.mapNotNull(codec::decodeSession)
        val tombstones = tombDocs.mapNotNull(codec::decodeTombstone)
        val cursors = SyncCursors(
            missionsMs = missionCursor,
            sessionsMs = maxOfOrNull(b?.cursorSessionsMs, sessionDocs.mapNotNull { it.fields["serverUpdatedAt"] as? Long }.maxOrNull()),
            tombstonesMs = maxOfOrNull(b?.cursorTombstonesMs, tombDocs.mapNotNull { it.fields["serverUpdatedAt"] as? Long }.maxOrNull()),
        )
        return RemoteChangeSet(missions, sessions, tombstones) to cursors
    }

    private suspend fun downloadLocked(uid: String, b: AccountBinding): ApplyReport {
        val (changes, cursors) = fetch(uid, b)
        guard(uid)
        val report = local.applyRemote(uid, changes, clock.now(), cursors)
        if (!report.accepted) throw AccountChanged()
        return report
    }

    // ------------------------------------------------------------------------------ upload

    private class UploadUnit(
        val writes: List<RemoteWrite>,
        val mutationIds: List<String>,
        val state: EntityState?,
        val entityType: SyncEntityType,
        val entityId: String,
        val operation: SyncOperation,
        val retryCount: Int,
    )

    private suspend fun uploadLocked(uid: String): Int {
        var uploaded = 0
        repeat(MAX_UPLOAD_PASSES) {
            guard(uid)
            val due = local.dueMutations(uid, clock.now(), DUE_BATCH)
            if (due.isEmpty()) return uploaded
            val units = due.groupBy { it.entityType to it.entityId }.values.mapNotNull { group -> buildUnit(uid, group.sortedBy { it.localVersion }) }
            val (empty, real) = units.partition { it.writes.isEmpty() }
            if (empty.isNotEmpty()) local.markUploaded(uid, empty.flatMap { it.mutationIds }, empty.mapNotNull { it.state })
            pack(real).forEach { batch ->
                guard(uid)
                try {
                    remote.commit(uid, batch.flatMap { it.writes })
                    if (!local.markUploaded(uid, batch.flatMap { it.mutationIds }, batch.mapNotNull { it.state })) throw AccountChanged()
                    uploaded += batch.size
                } catch (e: RemoteSyncException) {
                    if (e.failure == SyncFailure.PERMISSION_DENIED) {
                        uploaded += resolveDenied(uid, batch)
                    } else {
                        batch.forEach { markRetry(it, e.failure) }
                        throw e
                    }
                }
            }
        }
        return uploaded
    }

    /** One entity at a time; a Mission refused for a stale version is merged with the server copy and retried once. */
    private suspend fun resolveDenied(uid: String, batch: List<UploadUnit>): Int {
        var ok = 0
        for (unit in batch) {
            guard(uid)
            val committed = tryCommit(uid, unit) || when {
                unit.entityType == SyncEntityType.MISSION && unit.operation == SyncOperation.UPSERT -> {
                    refreshMission(uid, unit.entityId)
                    val rebuilt = local.mutationsFor(uid, SyncEntityType.MISSION, unit.entityId)
                        .sortedBy { it.localVersion }
                        .takeIf { it.isNotEmpty() }?.let { buildUnit(uid, it) }
                    rebuilt == null || tryCommit(uid, rebuilt)
                }
                unit.entityType == SyncEntityType.SESSION -> {
                    // Append-only: if the server already holds this Session, the server copy stands.
                    val exists = remote.get(uid, RemotePath.Doc(RemoteCollection.SESSIONS, unit.entityId)) != null
                    if (exists) local.markUploaded(uid, unit.mutationIds, listOfNotNull(unit.state))
                    exists
                }
                else -> false
            }
            if (committed) ok++ else markRetry(unit, SyncFailure.PERMISSION_DENIED)
        }
        return ok
    }

    private suspend fun tryCommit(uid: String, unit: UploadUnit): Boolean = try {
        remote.commit(uid, unit.writes)
        local.markUploaded(uid, unit.mutationIds, listOfNotNull(unit.state))
    } catch (e: RemoteSyncException) {
        if (e.failure != SyncFailure.PERMISSION_DENIED) throw e
        false
    }

    private suspend fun refreshMission(uid: String, missionId: String) {
        val doc = remote.get(uid, RemotePath.Doc(RemoteCollection.MISSIONS, missionId)) ?: return
        val steps = remote.list(uid, RemoteCollection.STEPS, missionId = missionId)
        val decoded = RemoteCodec(uid).decodeMission(doc, steps) ?: return
        guard(uid)
        local.applyRemote(uid, RemoteChangeSet(missions = listOf(decoded)), clock.now())
    }

    private suspend fun markRetry(unit: UploadUnit, failure: SyncFailure) {
        val attempts = unit.retryCount + 1
        val delay = (BACKOFF_BASE_MS shl minOf(attempts, 12)).coerceAtMost(BACKOFF_MAX_MS)
        local.markFailed(unit.mutationIds, failure, clock.now().plusMillis(delay), park = attempts >= MAX_ATTEMPTS)
    }

    private suspend fun buildUnit(uid: String, group: List<OutboxEntry>): UploadUnit? {
        val last = group.last()
        val ids = group.map { it.mutationId }
        val codec = RemoteCodec(uid)
        val now = clock.now()
        val retry = group.maxOf { it.retryCount }
        fun unit(writes: List<RemoteWrite>, state: EntityState?) = UploadUnit(writes, ids, state, last.entityType, last.entityId, last.operation, retry)
        return when (last.entityType) {
            SyncEntityType.MISSION -> {
                val state = local.entityState(SyncEntityType.MISSION, last.entityId)
                val version = (state?.remoteVersion ?: 0) + 1
                if (!RemoteSchema.isDocumentId(last.entityId)) return invalid(ids)
                when (last.operation) {
                    SyncOperation.UPSERT -> {
                        val snapshot = local.loadMission(last.entityId) ?: return unit(emptyList(), null) // deleted since; its DELETE follows
                        val doc = codec.mission(snapshot, version, last.mutationId) ?: return invalid(ids)
                        val missionId = snapshot.mission.id.value
                        val stepIds = snapshot.mission.steps.sortedBy { it.position }.map { it.id.value }
                        val writes = ArrayList<RemoteWrite>()
                        writes += RemoteWrite.Put(RemotePath.Doc(RemoteCollection.MISSIONS, missionId), doc)
                        snapshot.mission.steps.forEach { s ->
                            writes += RemoteWrite.Put(RemotePath.Doc(RemoteCollection.STEPS, s.id.value, missionId), codec.step(s, version, last.mutationId, snapshot.mission.updatedAt))
                        }
                        (state?.remoteStepIds.orEmpty() - stepIds.toSet()).filter(RemoteSchema::isDocumentId).forEach { gone ->
                            writes += RemoteWrite.Remove(RemotePath.Doc(RemoteCollection.STEPS, gone, missionId))
                        }
                        unit(writes, EntityState(SyncEntityType.MISSION, missionId, uid, version, stepIds, last.mutationId))
                    }
                    SyncOperation.DELETE -> {
                        val writes = ArrayList<RemoteWrite>()
                        writes += RemoteWrite.Put(
                            RemotePath.Doc(RemoteCollection.TOMBSTONES, RemoteSchema.tombstoneId(SyncEntityType.MISSION, last.entityId)),
                            codec.tombstone(SyncEntityType.MISSION, last.entityId, last.createdAt, version, last.mutationId),
                        )
                        state?.remoteStepIds.orEmpty().filter(RemoteSchema::isDocumentId).forEach { s ->
                            writes += RemoteWrite.Remove(RemotePath.Doc(RemoteCollection.STEPS, s, last.entityId))
                        }
                        writes += RemoteWrite.Remove(RemotePath.Doc(RemoteCollection.MISSIONS, last.entityId))
                        unit(writes, EntityState(SyncEntityType.MISSION, last.entityId, uid, version, emptyList(), last.mutationId))
                    }
                }
            }
            SyncEntityType.SESSION -> {
                if (last.operation != SyncOperation.UPSERT || !RemoteSchema.isDocumentId(last.entityId)) return invalid(ids)
                val result = local.loadSession(last.entityId) ?: return unit(emptyList(), null)
                val doc = codec.session(result, last.mutationId, now) ?: return invalid(ids)
                unit(listOf(RemoteWrite.Put(RemotePath.Doc(RemoteCollection.SESSIONS, last.entityId), doc)), EntityState(SyncEntityType.SESSION, last.entityId, uid, 1, emptyList(), last.mutationId))
            }
            SyncEntityType.SETTINGS -> unit(emptyList(), null) // the settings profile is compared by fingerprint, not queued
        }
    }

    private suspend fun invalid(ids: List<String>): UploadUnit? {
        local.markFailed(ids, SyncFailure.INVALID_DATA, clock.now(), park = true)
        return null
    }

    private fun pack(units: List<UploadUnit>): List<List<UploadUnit>> {
        val batches = ArrayList<List<UploadUnit>>()
        var current = ArrayList<UploadUnit>()
        var size = 0
        units.forEach { u ->
            if (size + u.writes.size > RemoteSchema.MAX_BATCH_WRITES && current.isNotEmpty()) { batches += current; current = ArrayList(); size = 0 }
            current += u
            size += u.writes.size
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    // ------------------------------------------------------------------------------ settings

    private suspend fun syncSettings(uid: String) {
        val b = local.binding()?.takeIf { it.uid == uid && it.status != BindingStatus.DELETION_PENDING } ?: return
        val codec = RemoteCodec(uid)
        val path = RemotePath.Doc(RemoteCollection.SETTINGS, RemoteSchema.SETTINGS_DOC)
        val mine = SyncedSettings.of(preferences.current())
        val doc = remote.get(uid, path)
        guard(uid)
        val theirs = doc?.let(codec::decodeSettings)
        val localChanged = b.settingsFingerprint != mine.fingerprint
        val remoteChanged = theirs != null && (theirs.serverUpdatedAtMs ?: 0L) > (b.settingsRemoteMs ?: -1L)
        val adoptRemote = b.settingsFingerprint == null && theirs != null && b.migrationId == null
        when {
            adoptRemote || (!localChanged && remoteChanged) -> {
                theirs!!
                preferences.setDefaultQuickFocusMinutes(theirs.defaultQuickFocusMinutes)
                preferences.setMotion(theirs.motion)
                preferences.setNotificationDetail(theirs.notificationDetail)
                local.recordSettings(uid, SyncedSettings.of(preferences.current()).fingerprint, theirs.serverUpdatedAtMs)
            }
            localChanged -> {
                remote.commit(uid, listOf(RemoteWrite.Put(path, codec.settings(mine, newId(), clock.now()))))
                val stored = remote.get(uid, path)?.fields?.get("serverUpdatedAt") as? Long
                guard(uid)
                local.recordSettings(uid, mine.fingerprint, stored)
            }
        }
        refreshSettingsDirty()
    }

    // ------------------------------------------------------------------------------ verification

    private suspend fun verifyMigration(uid: String, b: AccountBinding): Boolean {
        if (local.pendingCount(uid) > 0) return false // uploads still due (backoff) or parked
        val scope = local.migrationScope()
        guard(uid)
        val presentMissions = remote.existingIds(uid, RemoteCollection.MISSIONS, scope.missionIds)
        val presentSessions = remote.existingIds(uid, RemoteCollection.SESSIONS, scope.sessionIds)
        guard(uid)
        val missingMissions = scope.missionIds - presentMissions
        val missingSessions = scope.sessionIds - presentSessions
        if (missingMissions.isNotEmpty() || missingSessions.isNotEmpty()) {
            local.requeue(uid, missingMissions, missingSessions, clock.now())
            return false
        }
        val now = clock.now()
        remote.commit(
            uid,
            listOf(RemoteWrite.Put(migrationPath(b.migrationId!!), RemoteCodec(uid).migration(b.migrationId, true, scope.missionIds.size, scope.sessionIds.size, b.migrationStartedAt ?: now, now, now))),
        )
        guard(uid)
        return local.completeMigration(uid, b.migrationId, now)
    }

    companion object {
        const val COALESCE_MS = 3_000L
        const val CURSOR_OVERLAP_MS = 60_000L
        const val MAX_UPLOAD_PASSES = 5
        const val DUE_BATCH = 300
        const val MAX_ATTEMPTS = 8
        const val BACKOFF_BASE_MS = 15_000L
        const val BACKOFF_MAX_MS = 6 * 60 * 60 * 1000L
        private fun migrationPath(migrationId: String) = RemotePath.Doc(RemoteCollection.SYNC_META, RemoteSchema.migrationDocId(migrationId))

        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
        private fun maxOfOrNull(a: Long?, b: Long?): Long? = when { a == null -> b; b == null -> a; else -> maxOf(a, b) }
    }
}
