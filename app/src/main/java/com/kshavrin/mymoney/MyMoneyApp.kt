package com.kshavrin.mymoney

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.usecase.NormalizeLegacyUtcMidnightUseCase
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
class MyMoneyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workScheduler: WorkScheduler

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
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        workScheduler.scheduleDailyJobs()
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.tracesSampleRate = 0.0
                options.isAttachStacktrace = true
                options.isEnableAutoSessionTracking = true
            }
        }
        normalizeLegacyUtcMidnightDates()
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
