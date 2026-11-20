package io.github.illiapohukai.focusmode.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.SessionNotifications

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds abstract fun session(impl: FocusNotifier): SessionNotifications
    @Binds abstract fun reminders(impl: FocusNotifier): ReminderNotifications
}
