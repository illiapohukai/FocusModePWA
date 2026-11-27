package app.focusmodeplus.core.data.sync

import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.SyncOutboxEntity
import app.focusmodeplus.core.data.db.SyncTombstoneEntity
import app.focusmodeplus.core.domain.sync.RemoteSchema
import app.focusmodeplus.core.model.BindingStatus
import app.focusmodeplus.core.model.SyncEntityType
import app.focusmodeplus.core.model.SyncOperation
import java.time.Instant
import java.util.UUID

/**
 * Writes the outbox mutation for a synchronized product change. Must be called inside the same
 * Room transaction as the change itself, so the change and its mutation commit or roll back
 * together. Guest data (no binding) and a dataset whose account deletion started record nothing.
 */
internal class SyncOutboxWriter(private val db: FocusDatabase) {

    suspend fun record(type: SyncEntityType, entityId: String, operation: SyncOperation, now: Instant) {
        val dao = db.syncDao()
        val binding = dao.binding() ?: return
        val status = BindingStatus.fromWire(binding.status)
        if (status != BindingStatus.ACTIVE && status != BindingStatus.MIGRATING) return
        insert(binding.uid, type, entityId, operation, now)
        if (operation == SyncOperation.DELETE) {
            dao.putTombstone(SyncTombstoneEntity(type.wire, entityId, binding.uid, now, ORIGIN_LOCAL))
        }
    }

    /** Unconditional insert for a known bound uid (migration enqueue, re-queue after verification). */
    suspend fun insert(uid: String, type: SyncEntityType, entityId: String, operation: SyncOperation, now: Instant) {
        val dao = db.syncDao()
        dao.insertOutbox(
            SyncOutboxEntity(
                mutationId = newMutationId(),
                accountUid = uid,
                entityType = type.wire,
                entityId = entityId,
                operation = operation.wire,
                schemaVersion = RemoteSchema.SCHEMA_VERSION.toInt(),
                localVersion = dao.maxLocalVersion() + 1,
                createdAt = now,
                nextAttemptAt = now,
            ),
        )
    }

    companion object {
        const val ORIGIN_LOCAL = "local"
        const val ORIGIN_REMOTE = "remote"
        fun newMutationId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
