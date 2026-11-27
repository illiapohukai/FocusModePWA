package app.focusmodeplus.core.domain.sync

import app.focusmodeplus.core.model.SyncFailure

/** Collections below `/users/{uid}`. Mission steps live under `/users/{uid}/missions/{missionId}/steps`. */
enum class RemoteCollection(val segment: String) {
    MISSIONS("missions"),
    STEPS("steps"),
    SESSIONS("sessions"),
    SETTINGS("settings"),
    SYNC_META("syncMeta"),
    TOMBSTONES("tombstones"),
}

/** A document path relative to `/users/{uid}`. The uid itself is passed separately and always comes from the signed-in account. */
sealed interface RemotePath {
    /** `/users/{uid}` itself. */
    data object Profile : RemotePath

    data class Doc(val collection: RemoteCollection, val id: String, val missionId: String? = null) : RemotePath {
        init {
            require(RemoteSchema.isDocumentId(id)) { "invalid_document_id" }
            require((collection == RemoteCollection.STEPS) == (missionId != null)) { "steps_need_mission" }
            require(missionId == null || RemoteSchema.isDocumentId(missionId)) { "invalid_document_id" }
        }
    }
}

/** Marker replaced by the remote implementation with the server's commit time. */
object ServerTimestamp

sealed interface RemoteWrite {
    val path: RemotePath
    /** Writes the complete document (no merge): unknown fields can never survive. */
    data class Put(override val path: RemotePath, val fields: Map<String, Any?>) : RemoteWrite
    data class Remove(override val path: RemotePath) : RemoteWrite
}

/** A document as read from the server. Server timestamps arrive as epoch milliseconds (Long). */
data class RemoteDocument(val path: RemotePath, val fields: Map<String, Any?>)

class RemoteSyncException(val failure: SyncFailure, cause: Throwable? = null) : Exception(failure.wire, cause)

/**
 * Cloud Firestore boundary. Every call is scoped to one uid; all paths are below `/users/{uid}`.
 * Failures are thrown as [RemoteSyncException] with a privacy-safe class, never with content.
 */
interface RemoteSyncStore {
    val available: Boolean

    /** One atomic batch (at most [RemoteSchema.MAX_BATCH_WRITES] writes). */
    suspend fun commit(uid: String, writes: List<RemoteWrite>)

    suspend fun get(uid: String, path: RemotePath): RemoteDocument?

    /**
     * Documents of one collection, optionally only those whose `serverUpdatedAt` is at or after
     * [changedSinceMs]. [missionId] selects a Mission's steps.
     */
    suspend fun list(uid: String, collection: RemoteCollection, missionId: String? = null, changedSinceMs: Long? = null): List<RemoteDocument>

    /** Which of [ids] exist in [collection] (migration verification). */
    suspend fun existingIds(uid: String, collection: RemoteCollection, ids: Collection<String>): Set<String>

    suspend fun count(uid: String, collection: RemoteCollection): Int

    /** No remote store in this build (Firebase not configured). */
    object Unavailable : RemoteSyncStore {
        override val available = false
        private fun fail(): Nothing = throw RemoteSyncException(SyncFailure.UNAUTHENTICATED)
        override suspend fun commit(uid: String, writes: List<RemoteWrite>) = fail()
        override suspend fun get(uid: String, path: RemotePath): RemoteDocument? = fail()
        override suspend fun list(uid: String, collection: RemoteCollection, missionId: String?, changedSinceMs: Long?): List<RemoteDocument> = fail()
        override suspend fun existingIds(uid: String, collection: RemoteCollection, ids: Collection<String>): Set<String> = fail()
        override suspend fun count(uid: String, collection: RemoteCollection): Int = fail()
    }
}
