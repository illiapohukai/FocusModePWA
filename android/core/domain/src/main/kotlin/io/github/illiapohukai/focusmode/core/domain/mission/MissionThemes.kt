package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme

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

    fun resolve(missions: List<Mission>): Map<MissionId, MissionTheme> {
        data class Hold(val idx: Int, val end: Long)
        val held = mutableListOf<Hold>()
        val map = LinkedHashMap<MissionId, MissionTheme>()
        missions.sortedWith(compareBy<Mission>({ it.createdAt.toEpochMilli() }, { it.id.value })).forEach { m ->
            val born = m.createdAt.toEpochMilli()
            val taken = held.filter { it.end > born }.map { it.idx }.toSet()
            var idx = hashIndex(m.id.value)
            var i = 0
            while (i < ORDER.size && idx in taken) { idx = (idx + 1) % ORDER.size; i++ }
            map[m.id] = ORDER[idx]
            val end = if (m.status == MissionStatus.ACTIVE) Long.MAX_VALUE
            else (m.completedAt ?: m.archivedAt ?: m.updatedAt).toEpochMilli()
            held += Hold(idx, end)
        }
        return map
    }
}
