package app.focusmodeplus.core.domain.mission

import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionTheme
import java.time.Instant

/**
 * Stable Mission identity colors, ported from PWA v17.7 (`resolveMissionThemesV177`): an FNV-1a
 * hash of the id picks a tone; a Mission avoids a tone still held by an earlier Mission that was
 * running when it was created. Nothing is stored.
 */
object MissionThemes {
    private val ORDER = MissionTheme.entries

    /** FNV-1a 32-bit over the first UTF-16 unit of each code point (matches JS `for…of` + charCodeAt(0)). */
    fun hashIndex(id: String): Int {
        var h = 2166136261L.toInt()
        id.codePoints().forEach { cp ->
            val unit = if (cp > 0xFFFF) Character.highSurrogate(cp).code else cp
            h = h xor unit
            h *= 16777619
        }
        return ((h.toLong() and 0xFFFFFFFFL) % ORDER.size).toInt()
    }

    private data class Entry(val id: MissionId, val born: Long, val end: Long)

    fun resolve(missions: List<Mission>): Map<MissionId, MissionTheme> = resolveEntries(missions.map(::entry))

    /**
     * PWA `previewMissionThemeV177`: the tone a draft Mission created [now] will get, so the
     * Briefing already shows the identity color the stored Mission keeps.
     */
    fun preview(missions: List<Mission>, draftId: MissionId, now: Instant): MissionTheme {
        val entries = missions.filter { it.id != draftId }.map(::entry) + Entry(draftId, now.toEpochMilli(), Long.MAX_VALUE)
        return resolveEntries(entries)[draftId] ?: ORDER[hashIndex(draftId.value)]
    }

    private fun entry(m: Mission) = Entry(
        m.id, m.createdAt.toEpochMilli(),
        if (m.status == MissionStatus.ACTIVE) Long.MAX_VALUE else (m.completedAt ?: m.archivedAt ?: m.updatedAt).toEpochMilli(),
    )

    private fun resolveEntries(entries: List<Entry>): Map<MissionId, MissionTheme> {
        data class Hold(val idx: Int, val end: Long)
        val held = mutableListOf<Hold>()
        val map = LinkedHashMap<MissionId, MissionTheme>()
        entries.sortedWith(compareBy<Entry>({ it.born }, { it.id.value })).forEach { m ->
            val taken = held.filter { it.end > m.born }.map { it.idx }.toSet()
            var idx = hashIndex(m.id.value)
            var i = 0
            while (i < ORDER.size && idx in taken) { idx = (idx + 1) % ORDER.size; i++ }
            map[m.id] = ORDER[idx]
            held += Hold(idx, m.end)
        }
        return map
    }
}
