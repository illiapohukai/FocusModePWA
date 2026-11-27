package app.focusmodeplus.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.focusmodeplus.core.domain.platform.ReminderNotifications
import app.focusmodeplus.core.domain.platform.SessionNotifications

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds abstract fun session(impl: FocusNotifier): SessionNotifications
    @Binds abstract fun reminders(impl: FocusNotifier): ReminderNotifications
}
