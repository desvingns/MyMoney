package com.kshavrin.mymoney

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class MyMoneyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.tracesSampleRate = 0.0
                options.isAttachStacktrace = true
                options.isEnableAutoSessionTracking = true
            }
        }
    }
}
