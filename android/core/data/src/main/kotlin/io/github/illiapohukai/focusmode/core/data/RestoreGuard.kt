package io.github.illiapohukai.focusmode.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.data.db.toModel
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects that the database and preferences were restored from an OS backup (new device or
 * reinstall): the install id kept in `noBackupFilesDir` (never backed up) no longer matches the
 * id stored with the backed-up preferences. Transient, device-bound state is then neutralized:
 * a running Session is frozen (the user resumes, saves or discards), the DND restore record is
 * dropped (it belongs to the old device), and reminder occurrences are rebuilt from scratch.
 */
@Singleton
class RestoreGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FocusDatabase,
    private val prefs: DataStore<Preferences>,
    private val clock: FocusClock,
) {
    private val key = stringPreferencesKey("install_id")

    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        val marker = File(context.noBackupFilesDir, "install.id")
        val local = marker.takeIf { it.exists() }?.readText()?.trim()
        val stored = prefs.data.first()[key]
        val restored = stored != null && stored != local
        if (restored) neutralizeTransientState()
        val id = local ?: UUID.randomUUID().toString().also { marker.writeText(it) }
        if (stored != id) prefs.edit { it[key] = id }
        restored
    }

    private suspend fun neutralizeTransientState() = db.withTransaction {
        db.dndOverrideDao().clear()
        db.reminderDao().getAll().forEach { db.reminderDao().delete(it.key) }
        val active = db.sessionDao().getActive()?.toModel()
        if (active != null && active.lifecycle == SessionLifecycle.RUNNING) {
            db.sessionDao().updateActive(SessionTimeline.freeze(active, clock).toEntity())
        }
    }
}
