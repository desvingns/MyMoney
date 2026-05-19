package com.kshavrin.mymoney.core.common.exception

import io.sentry.Sentry

fun Throwable.reportToSentry() {
    Sentry.captureException(this)
}
