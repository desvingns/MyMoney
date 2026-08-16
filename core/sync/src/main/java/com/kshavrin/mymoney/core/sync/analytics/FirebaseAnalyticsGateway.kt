package com.kshavrin.mymoney.core.sync.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.sync.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AnalyticsGateway {
        private val analytics: FirebaseAnalytics? by lazy {
            if (BuildConfig.HAS_FIREBASE) {
                FirebaseAnalytics.getInstance(context)
            } else {
                null
            }
        }

        override fun log(event: AnalyticsEvent) {
            val firebaseAnalytics = analytics ?: return
            when (event) {
                AnalyticsEvent.SupportOpened ->
                    firebaseAnalytics.logEvent(EVENT_SUPPORT_OPENED, null)

                is AnalyticsEvent.SupportPurchaseStarted ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUPPORT_PURCHASE_STARTED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                        },
                    )

                is AnalyticsEvent.SupportPurchaseCompleted ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUPPORT_PURCHASE_COMPLETED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                            putString(PARAM_OUTCOME, event.outcome)
                        },
                    )

                is AnalyticsEvent.PaywallShown ->
                    firebaseAnalytics.logEvent(
                        EVENT_PAYWALL_SHOWN,
                        Bundle().apply {
                            putString(PARAM_ENTRY_POINT, event.entryPoint)
                        },
                    )

                is AnalyticsEvent.TrialStarted ->
                    firebaseAnalytics.logEvent(
                        EVENT_TRIAL_STARTED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                        },
                    )

                is AnalyticsEvent.SubscriptionPurchased ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUBSCRIPTION_PURCHASED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                            putBoolean(PARAM_IS_TRIAL_CONVERSION, event.isTrialConversion)
                        },
                    )

                is AnalyticsEvent.SubscriptionCancelled ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUBSCRIPTION_CANCELLED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                            putString(PARAM_REASON, event.reason)
                        },
                    )

                is AnalyticsEvent.GraceEntered ->
                    firebaseAnalytics.logEvent(
                        EVENT_GRACE_ENTERED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                        },
                    )

                is AnalyticsEvent.SharedDetached ->
                    firebaseAnalytics.logEvent(
                        EVENT_SHARED_DETACHED,
                        Bundle().apply {
                            putString(PARAM_REASON, event.reason)
                        },
                    )
            }
        }

        private companion object {
            const val EVENT_SUPPORT_OPENED = "support_opened"
            const val EVENT_SUPPORT_PURCHASE_STARTED = "support_purchase_started"
            const val EVENT_SUPPORT_PURCHASE_COMPLETED = "support_purchase_completed"
            const val EVENT_PAYWALL_SHOWN = "paywall_shown"
            const val EVENT_TRIAL_STARTED = "trial_started"
            const val EVENT_SUBSCRIPTION_PURCHASED = "subscription_purchased"
            const val EVENT_SUBSCRIPTION_CANCELLED = "subscription_cancelled"
            const val EVENT_GRACE_ENTERED = "grace_entered"
            const val EVENT_SHARED_DETACHED = "shared_detached"
            const val PARAM_PRODUCT_ID = "product_id"
            const val PARAM_OUTCOME = "outcome"
            const val PARAM_ENTRY_POINT = "entry_point"
            const val PARAM_IS_TRIAL_CONVERSION = "is_trial_conversion"
            const val PARAM_REASON = "reason"
        }
    }
