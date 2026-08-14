package com.kshavrin.mymoney.core.ads.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.kshavrin.mymoney.core.ads.ConsentResult
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface UmpConsentGateway {
    suspend fun ensureConsent(activity: Activity): ConsentResult
}

@Singleton
class UmpConsentGatewayImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UmpConsentGateway {
        private val consentInformation: ConsentInformation by lazy {
            UserMessagingPlatform.getConsentInformation(context)
        }

        override suspend fun ensureConsent(activity: Activity): ConsentResult =
            try {
                ensureConsentSafely(activity)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                error.reportToSentry()
                ConsentResult.Unavailable
            }

        private suspend fun ensureConsentSafely(activity: Activity): ConsentResult {
            val updateError = requestConsentInfoUpdate(activity)
            if (updateError != null && !consentInformation.canRequestAds()) {
                updateError.reportToSentry()
                return ConsentResult.Unavailable
            }
            if (updateError != null) {
                updateError.reportToSentry()
                return resolveConsent(activity)
            }

            val formError = loadAndShowConsentFormIfRequired(activity)
            if (formError != null && !consentInformation.canRequestAds()) {
                formError.reportToSentry()
                return ConsentResult.Unavailable
            }
            if (formError != null) {
                formError.reportToSentry()
            }
            return resolveConsent(activity)
        }

        private suspend fun requestConsentInfoUpdate(activity: Activity): FormError? =
            suspendCancellableCoroutine { continuation ->
                consentInformation.requestConsentInfoUpdate(
                    activity,
                    ConsentRequestParameters.Builder().build(),
                    {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    },
                    { error ->
                        if (continuation.isActive) {
                            continuation.resume(error)
                        }
                    },
                )
            }

        private suspend fun loadAndShowConsentFormIfRequired(activity: Activity): FormError? =
            suspendCancellableCoroutine { continuation ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (continuation.isActive) {
                        continuation.resume(error)
                    }
                }
            }

        private fun resolveConsent(activity: Activity): ConsentResult =
            when {
                !consentInformation.canRequestAds() -> ConsentResult.Unavailable
                consentInformation.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED ->
                    ConsentResult.NotRequired

                activity.hasPersonalizationConsent() -> ConsentResult.Granted
                else -> ConsentResult.Denied
            }
    }

private fun Activity.hasPersonalizationConsent(): Boolean {
    val purposeConsents =
        getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
            .getString(TCF_PURPOSE_CONSENTS_KEY, null)
            .orEmpty()
    return PERSONALIZATION_PURPOSE_INDICES.all { index -> purposeConsents.getOrNull(index) == '1' }
}

private fun FormError.reportToSentry() {
    IllegalStateException("UMP consent error code=$errorCode message=$message").reportToSentry()
}

private const val TCF_PURPOSE_CONSENTS_KEY = "IABTCF_PurposeConsents"
private val PERSONALIZATION_PURPOSE_INDICES = setOf(0, 2, 3)
