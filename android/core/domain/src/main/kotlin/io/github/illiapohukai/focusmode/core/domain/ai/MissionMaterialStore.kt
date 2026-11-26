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
    /** Deletes the processed photo and any camera capture that was not processed yet. */
    suspend fun clear()

    /**
     * A fresh `content://` write target for the system camera (one cache file shared through a
     * FileProvider; no camera or storage permission). [prepare] with this target re-encodes the
     * photo like a picked one and deletes the capture file right afterwards. Null if unavailable.
     */
    suspend fun captureTarget(): String? = null
}
