package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.domain.sync.RemoteCollection
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteDocument
import io.github.illiapohukai.focusmode.core.domain.sync.RemotePath
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSchema
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncException
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncStore
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteWrite
import io.github.illiapohukai.focusmode.core.domain.sync.ServerTimestamp
import io.github.illiapohukai.focusmode.core.model.SyncFailure

/**
 * In-memory Firestore stand-in for engine tests. It models the parts of `firestore.rules` the
 * engine depends on (the Rules themselves are tested against the emulator):
 * every path is below the given uid; `ownerUid` must be that uid; reserved server fields are
 * refused; a Mission is created at syncVersion 1 and updated only to exactly +1; a tombstoned
 * Mission cannot be created again; a Session only accepts note/metadata changes. Batches are
 * atomic. Failures can be injected, including "applied but acknowledgement lost".
 */
class FakeRemoteSyncStore : RemoteSyncStore {
    override val available = true

    /** Full path ("users/{uid}/missions/{id}") -> fields (server timestamps resolved to Long). */
    val docs = LinkedHashMap<String, Map<String, Any?>>()
    var serverTimeMs = 1_800_000_000_000L
    var offline = false
    /** The next commit is applied, but the client sees a network failure. */
    var loseNextAck = false
    /** The next N commits fail with [failure] before anything is applied. */
    var failNextCommits = 0
    var failure = SyncFailure.SERVER
    var commits = 0
    var writes = 0
    var reads = 0
    /** Called before every list/get; lets a test switch accounts in the middle of a sync. */
    var beforeRead: (() -> Unit)? = null
    /** Writes to matching full paths are acknowledged but not stored (verification tests). */
    var silentlyDrop: (String) -> Boolean = { false }

    fun key(uid: String, path: RemotePath): String = when (path) {
        RemotePath.Profile -> "users/$uid"
        is RemotePath.Doc -> if (path.collection == RemoteCollection.STEPS) "users/$uid/missions/${path.missionId}/steps/${path.id}" else "users/$uid/${path.collection.segment}/${path.id}"
    }

    fun doc(uid: String, path: RemotePath): Map<String, Any?>? = docs[key(uid, path)]
    fun collection(uid: String, collection: RemoteCollection, missionId: String? = null): Map<String, Map<String, Any?>> {
        val prefix = if (collection == RemoteCollection.STEPS) "users/$uid/missions/$missionId/steps/" else "users/$uid/${collection.segment}/"
        return docs.filterKeys { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
    }
    fun allFor(uid: String): Map<String, Map<String, Any?>> = docs.filterKeys { it == "users/$uid" || it.startsWith("users/$uid/") }

    private fun checkOnline() { if (offline) throw RemoteSyncException(SyncFailure.OFFLINE) }

    override suspend fun commit(uid: String, writes: List<RemoteWrite>) {
        checkOnline()
        require(writes.size <= 500) { "batch_too_large" }
        if (failNextCommits > 0) { failNextCommits--; throw RemoteSyncException(failure) }
        serverTimeMs += 1_000
        val staged = LinkedHashMap(docs)
        writes.forEach { w ->
            val k = key(uid, w.path)
            when (w) {
                is RemoteWrite.Remove -> staged.remove(k)
                is RemoteWrite.Put -> {
                    val fields = w.fields.mapValues { (_, v) -> if (v === ServerTimestamp) serverTimeMs else normalize(v) }
                    if (!allowed(uid, w.path, staged[k], fields, staged)) throw RemoteSyncException(SyncFailure.PERMISSION_DENIED)
                    staged[k] = fields
                }
            }
        }
        docs.clear()
        docs.putAll(staged.filterKeys { k -> !silentlyDrop(k) || docs.containsKey(k) })
        commits++
        this.writes += writes.size
        if (loseNextAck) { loseNextAck = false; throw RemoteSyncException(SyncFailure.OFFLINE) }
    }

    private fun normalize(v: Any?): Any? = when (v) {
        is Int -> v.toLong()
        is List<*> -> v.map(::normalize)
        is Map<*, *> -> v.entries.associate { (k, x) -> k as String to normalize(x) }
        else -> v
    }

    private fun allowed(uid: String, path: RemotePath, old: Map<String, Any?>?, new: Map<String, Any?>, staged: Map<String, Map<String, Any?>>): Boolean {
        if (new["ownerUid"] != uid) return false
        if (new.keys.any { it in RemoteSchema.RESERVED_SERVER_FIELDS }) return false
        if (new["schemaVersion"] != RemoteSchema.SCHEMA_VERSION) return false
        if (path !is RemotePath.Doc) return old == null || old["createdAt"] == new["createdAt"]
        return when (path.collection) {
            RemoteCollection.MISSIONS -> {
                val tomb = staged.containsKey("users/$uid/tombstones/mission_${path.id}")
                if (new["id"] != path.id || new.keys != RemoteSchema.Fields.MISSION.toSet()) false
                else if (old == null) !tomb && new["syncVersion"] == 1L
                else new["syncVersion"] == (old["syncVersion"] as Long) + 1 && old["createdAt"] == new["createdAt"]
            }
            RemoteCollection.STEPS -> new["missionId"] == path.missionId && new["id"] == path.id && new.keys == RemoteSchema.Fields.STEP.toSet()
            RemoteCollection.SESSIONS -> {
                if (new["id"] != path.id || new.keys != RemoteSchema.Fields.SESSION.toSet()) false
                else if (old == null) true
                else (old.keys + new.keys).filter { old[it] != new[it] }.all { it in SESSION_MUTABLE }
            }
            RemoteCollection.TOMBSTONES -> new["id"] == path.id && new.keys == RemoteSchema.Fields.TOMBSTONE.toSet() &&
                (old == null || (old["entityType"] == new["entityType"] && old["entityId"] == new["entityId"]))
            RemoteCollection.SETTINGS -> path.id == RemoteSchema.SETTINGS_DOC && new.keys == RemoteSchema.Fields.SETTINGS.toSet()
            RemoteCollection.SYNC_META -> path.id == RemoteSchema.migrationDocId(new["migrationId"] as String) && new.keys == RemoteSchema.Fields.MIGRATION.toSet() &&
                (old == null || old["startedAt"] == new["startedAt"])
        }
    }

    override suspend fun get(uid: String, path: RemotePath): RemoteDocument? {
        beforeRead?.invoke()
        checkOnline()
        reads++
        return docs[key(uid, path)]?.let { RemoteDocument(path, it) }
    }

    override suspend fun list(uid: String, collection: RemoteCollection, missionId: String?, changedSinceMs: Long?): List<RemoteDocument> {
        beforeRead?.invoke()
        checkOnline()
        reads++
        return collection(uid, collection, missionId)
            .filter { (_, f) -> changedSinceMs == null || ((f["serverUpdatedAt"] as? Long) ?: 0L) >= changedSinceMs }
            .map { (k, f) -> RemoteDocument(RemotePath.Doc(collection, k.substringAfterLast('/'), missionId), f) }
    }

    override suspend fun existingIds(uid: String, collection: RemoteCollection, ids: Collection<String>): Set<String> {
        checkOnline()
        val present = collection(uid, collection).keys.map { it.substringAfterLast('/') }.toSet()
        return ids.filter { it in present }.toSet()
    }

    override suspend fun count(uid: String, collection: RemoteCollection): Int {
        checkOnline()
        return collection(uid, collection).size
    }

    companion object {
        val SESSION_MUTABLE = setOf("reviewNote", "updatedAt", "deviceMutationId", "serverUpdatedAt")
    }
}
