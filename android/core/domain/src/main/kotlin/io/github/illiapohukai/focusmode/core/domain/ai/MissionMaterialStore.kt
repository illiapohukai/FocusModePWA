package io.github.illiapohukai.focusmode.core.domain.ai

import io.github.illiapohukai.focusmode.core.domain.mission.DraftAttachment

sealed interface MaterialPreparation {
    data class Ready(val attachment: DraftAttachment) : MaterialPreparation
    /** [message] is safe German copy (PWA wording); [error] the matching error class. */
    data class Rejected(val error: GenerationError, val message: String) : MaterialPreparation
}

/**
 * Holds the one draft photo. [prepare] reads a system-picker `content://` Uri, checks the real
 * format, size and declared dimensions before decoding, decodes down-sampled, re-encodes it
 * (dropping EXIF, GPS and camera metadata) and keeps only the processed bytes in no-backup app
 * storage. The original is never copied or uploaded; nothing leaves the device before the user
 * taps "Mission planen".
 */
interface MissionMaterialStore {
    suspend fun prepare(source: String): MaterialPreparation
    suspend fun load(): MissionMaterial?
    suspend fun clear()
}
