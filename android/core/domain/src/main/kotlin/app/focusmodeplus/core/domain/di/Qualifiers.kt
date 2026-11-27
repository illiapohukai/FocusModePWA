package app.focusmodeplus.core.domain.di

import javax.inject.Qualifier

/** Process-wide scope for owners that outlive a screen (Session owner, capability owner). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

/** Public base URL of the existing Cloudflare Worker (from BuildConfig, never a secret). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class WorkerBaseUrl

/** The app's user-visible version name (BuildConfig), written into backup files. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppVersionName
