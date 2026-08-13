package com.kshavrin.mymoney

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import com.kshavrin.mymoney.core.domain.usecase.NormalizeLegacyUtcMidnightUseCase
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.WorkScheduler
import dagger.Lazy
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
    lateinit var workerFactory: Lazy<HiltWorkerFactory>

    @Inject
    lateinit var workScheduler: Lazy<WorkScheduler>

    @Inject
    lateinit var journalSync: Lazy<JournalSync>

    @Inject
    lateinit var appSettingsRepository: Lazy<AppSettingsRepository>

    @Inject
    lateinit var billingGateway: Lazy<BillingGateway>

    @Inject
    lateinit var supporterSync: Lazy<SupporterSync>

    @Inject
    lateinit var normalizeLegacyUtcMidnight: Lazy<NormalizeLegacyUtcMidnightUseCase>

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
                .setWorkerFactory(workerFactory.get())
                .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(ioDispatcher) {
            workScheduler.get().scheduleDailyJobs()
        }
        recoverSubscriptions()
        recoverSupporterPurchases()
        triggerJournalSyncOnOpen()
        initSentry()
        normalizeLegacyUtcMidnightDates()
    }

    @Suppress("DEPRECATION")
    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            return
        }
        applicationScope.launch(ioDispatcher) {
            SentryAndroid.init(this@MyMoneyApp) { options ->
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
    }

    private fun triggerJournalSyncOnOpen() {
        applicationScope.launch(ioDispatcher) {
            runCatching { journalSync.get().syncNow() }
                .onFailure { throwable -> Sentry.captureException(throwable) }
        }
    }

    private fun recoverSubscriptions() {
        applicationScope.launch(ioDispatcher) {
            billingGateway.get().resolveSubscriptionPurchases()
        }
    }

    private fun recoverSupporterPurchases() {
        applicationScope.launch(ioDispatcher) {
            supporterSync.get().restore()
            billingGateway.get().resolvePendingPurchases()
        }
    }

    private fun normalizeLegacyUtcMidnightDates() {
        applicationScope.launch(ioDispatcher) {
            runCatching {
                val settingsRepository = appSettingsRepository.get()
                if (settingsRepository.settings.first().tzNormalizedAt == null) {
                    normalizeLegacyUtcMidnight.get().invoke()
                    val normalizedAt = Clock.systemUTC().millis()
                    settingsRepository.update { settings ->
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
