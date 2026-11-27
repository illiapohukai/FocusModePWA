package app.focusmodeplus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.focusmodeplus.core.domain.deeplink.DeepLinkTarget
import app.focusmodeplus.core.domain.deeplink.DeepLinks
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.repository.ReminderStore
import app.focusmodeplus.core.domain.session.FocusSessionController
import app.focusmodeplus.core.domain.session.SessionState
import app.focusmodeplus.core.model.MotionPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-shell state: theme motion preference, the authoritative Session state and pending deep links. */
@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: PreferencesRepository,
    controller: FocusSessionController,
    private val reminders: ReminderStore,
) : ViewModel() {
    val motion: StateFlow<MotionPreference> = preferences.preferences.map { it.motion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MotionPreference.SYSTEM)

    val session: StateFlow<SessionState> = controller.state

    private val _pending = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingDeepLink: StateFlow<DeepLinkTarget?> = _pending.asStateFlow()

    fun onDeepLink(target: DeepLinkTarget) {
        viewModelScope.launch {
            // A reminder link resolves through its stored occurrence; unknown keys go Home.
            _pending.value = if (target is DeepLinkTarget.Reminder) {
                reminders.get(target.key)?.let { DeepLinks.parse(it.deepLink) }?.takeIf { it !is DeepLinkTarget.Reminder } ?: DeepLinkTarget.Home
            } else target
        }
    }

    fun consumeDeepLink() { _pending.value = null }
}
