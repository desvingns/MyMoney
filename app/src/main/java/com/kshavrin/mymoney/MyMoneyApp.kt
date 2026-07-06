package com.kshavrin.mymoney

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.usecase.NormalizeLegacyUtcMidnightUseCase
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltAndroidApp
class MyMoneyApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workScheduler: WorkScheduler

    @Inject
    lateinit var journalSync: JournalSync

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var normalizeLegacyUtcMidnight: NormalizeLegacyUtcMidnightUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(ioDispatcher) {
            workScheduler.scheduleDailyJobs()
        }
        triggerJournalSyncOnOpen()
        initSentry()
        normalizeLegacyUtcMidnightDates()
    }

    @Suppress("DEPRECATION")
    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.tracesSampleRate = 0.0
            options.setEnableTracing(false)
            options.profilesSampleRate = 0.0
            options.profilesSampler = null
            options.isEnableAppStartProfiling = false
            options.isEnableAutoActivityLifecycleTracing = false
            options.isEnableActivityLifecycleTracingAutoFinish = false
            options.isEnablePerformanceV2 = false
            options.isEnableFramesTracking = false
            options.isEnableAutoSessionTracking = false
            options.isAttachStacktrace = true
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
        }
    }

    private fun triggerJournalSyncOnOpen() {
        applicationScope.launch(ioDispatcher) {
            runCatching { journalSync.syncNow() }
                .onFailure { throwable -> Sentry.captureException(throwable) }
        }
    }

    private fun normalizeLegacyUtcMidnightDates() {
        applicationScope.launch(ioDispatcher) {
            runCatching {
                if (appSettingsRepository.settings.first().tzNormalizedAt == null) {
                    normalizeLegacyUtcMidnight()
                    val normalizedAt = Clock.systemUTC().millis()
                    appSettingsRepository.update { settings ->
                        if (settings.tzNormalizedAt == null) {
                            settings.copy(tzNormalizedAt = normalizedAt)
                        } else {
                            settings
                        }
                    }
                }
            }.onFailure { throwable ->
                Sentry.captureException(throwable)
            }
        }
    }
}
