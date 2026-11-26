package io.github.illiapohukai.focusmode.core.account

import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteCollection
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteDocument
import io.github.illiapohukai.focusmode.core.domain.sync.RemotePath
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSchema
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncException
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncStore
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteWrite
import io.github.illiapohukai.focusmode.core.domain.sync.ServerTimestamp
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Firestore implementation of the remote boundary. Every path is below `/users/{uid}`
 * with the uid of the signed-in account; reads always go to the server (no cached answers);
 * every call has a timeout and maps failures to privacy-safe classes.
 */
@Singleton
class FirestoreRemoteSyncStore @Inject constructor(private val runtime: FirebaseRuntime) : RemoteSyncStore {
    override val available: Boolean get() = runtime.available

    private val db get() = runtime.firestore
    private fun user(uid: String): DocumentReference {
        if (!RemoteSchema.isUid(uid)) throw RemoteSyncException(SyncFailure.UNAUTHENTICATED)
        return db.collection(USERS).document(uid)
    }

    private fun ref(uid: String, path: RemotePath): DocumentReference = when (path) {
        RemotePath.Profile -> user(uid)
        is RemotePath.Doc -> collection(uid, path.collection, path.missionId).document(path.id)
    }

    private fun collection(uid: String, c: RemoteCollection, missionId: String?): CollectionReference =
        if (c == RemoteCollection.STEPS) user(uid).collection(RemoteCollection.MISSIONS.segment).document(checkNotNull(missionId)).collection(c.segment)
        else user(uid).collection(c.segment)

    override suspend fun commit(uid: String, writes: List<RemoteWrite>) = call {
        require(writes.size <= MAX_FIRESTORE_BATCH)
        val batch = db.batch()
        writes.forEach { w ->
            when (w) {
                is RemoteWrite.Put -> batch.set(ref(uid, w.path), FirestoreValues.toFirestore(w.fields))
                is RemoteWrite.Remove -> batch.delete(ref(uid, w.path))
            }
        }
        batch.commit().await()
        Unit
    }

    override suspend fun get(uid: String, path: RemotePath): RemoteDocument? = call {
        val snapshot = ref(uid, path).get(Source.SERVER).await()
        snapshot.data?.takeIf { snapshot.exists() }?.let { RemoteDocument(path, FirestoreValues.fromFirestore(it)) }
    }

    override suspend fun list(uid: String, collection: RemoteCollection, missionId: String?, changedSinceMs: Long?): List<RemoteDocument> = call {
        var query: Query = collection(uid, collection, missionId)
        if (changedSinceMs != null) query = query.whereGreaterThanOrEqualTo(SERVER_UPDATED_AT, FirestoreValues.timestamp(changedSinceMs))
        query.get(Source.SERVER).await().documents.mapNotNull { d ->
            // A document id outside the safe-id policy is never applied.
            val path = runCatching { RemotePath.Doc(collection, d.id, missionId) }.getOrNull() ?: return@mapNotNull null
            d.data?.let { RemoteDocument(path, FirestoreValues.fromFirestore(it)) }
        }
    }

    override suspend fun existingIds(uid: String, collection: RemoteCollection, ids: Collection<String>): Set<String> = call {
        val col = collection(uid, collection, null)
        ids.filter(RemoteSchema::isDocumentId).chunked(IN_QUERY_LIMIT).flatMap { chunk ->
            col.whereIn(FieldPath.documentId(), chunk).get(Source.SERVER).await().documents.map { it.id }
        }.toSet()
    }

    override suspend fun count(uid: String, collection: RemoteCollection): Int = call {
        collection(uid, collection, null).count().get(AggregateSource.SERVER).await().count.toInt()
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (_: TimeoutCancellationException) {
        throw RemoteSyncException(SyncFailure.OFFLINE)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteSyncException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw RemoteSyncException(AccountErrorMapper.firestore(e.code))
    } catch (_: Exception) {
        throw RemoteSyncException(SyncFailure.UNKNOWN)
    }

    companion object {
        const val USERS = "users"
        const val SERVER_UPDATED_AT = "serverUpdatedAt"
        const val TIMEOUT_MS = 30_000L
        const val IN_QUERY_LIMIT = 30
        const val MAX_FIRESTORE_BATCH = 500
    }
}

/** Value conversion at the Firestore boundary: Long numbers, epoch-ms server times, the server-timestamp sentinel. */
object FirestoreValues {
    fun toFirestore(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { (_, v) -> out(v) }

    private fun out(v: Any?): Any? = when (v) {
        ServerTimestamp -> FieldValue.serverTimestamp()
        is Int -> v.toLong()
        is List<*> -> v.map(::out)
        is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to out(x) }
        else -> v
    }

    fun fromFirestore(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { (_, v) -> into(v) }

    private fun into(v: Any?): Any? = when (v) {
        is Timestamp -> v.seconds * 1000 + v.nanoseconds / 1_000_000
        is Int -> v.toLong()
        is List<*> -> v.map(::into)
        is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to into(x) }
        else -> v
    }

    fun timestamp(ms: Long): Timestamp = Timestamp(Math.floorDiv(ms, 1000L), (Math.floorMod(ms, 1000L) * 1_000_000).toInt())
}
