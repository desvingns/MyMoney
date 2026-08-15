package com.kshavrin.mymoney.core.sync.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.domain.notification.EntitlementNotifier
import com.kshavrin.mymoney.core.sync.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object EntitlementNotifications {
    const val EXTRA_OPEN_PAYWALL = "com.kshavrin.mymoney.OPEN_PAYWALL"
}

@Singleton
class EntitlementNotifierImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : EntitlementNotifier {
        @SuppressLint("MissingPermission")
        override fun notify(warning: EntitlementWarning) {
            if (!canPostNotifications()) return
            val content = warning.content()
            val notification =
                NotificationCompat
                    .Builder(context, MyMoneyNotificationChannel.SUBSCRIPTION.id)
                    .setSmallIcon(R.drawable.ic_notification_subscription)
                    .setContentTitle(context.getString(content.titleRes))
                    .setContentText(context.getString(content.bodyRes))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(content.bodyRes)))
                    .setContentIntent(paywallIntent())
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            NotificationManagerCompat.from(context).notify(content.notificationId, notification)
        }

        private fun canPostNotifications(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

        private fun paywallIntent(): PendingIntent {
            val launchIntent =
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply {
                        putExtra(EntitlementNotifications.EXTRA_OPEN_PAYWALL, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
            return PendingIntent.getActivity(
                context,
                PAYWALL_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun EntitlementWarning.content(): WarningContent =
            when (this) {
                EntitlementWarning.TRIAL_ENDING_3D ->
                    WarningContent(
                        notificationId = NOTIFICATION_ID_TRIAL_ENDING,
                        titleRes = R.string.notification_trial_ending_title,
                        bodyRes = R.string.notification_trial_ending_body,
                    )

                EntitlementWarning.GRACE_ENTERED ->
                    WarningContent(
                        notificationId = NOTIFICATION_ID_GRACE_ENTERED,
                        titleRes = R.string.notification_grace_entered_title,
                        bodyRes = R.string.notification_grace_entered_body,
                    )

                EntitlementWarning.EXPIRY_IMMINENT_1D ->
                    WarningContent(
                        notificationId = NOTIFICATION_ID_EXPIRY_IMMINENT,
                        titleRes = R.string.notification_expiry_imminent_title,
                        bodyRes = R.string.notification_expiry_imminent_body,
                    )
            }

        private data class WarningContent(
            val notificationId: Int,
            val titleRes: Int,
            val bodyRes: Int,
        )

        private companion object {
            const val PAYWALL_REQUEST_CODE = 5701
            const val NOTIFICATION_ID_TRIAL_ENDING = 5711
            const val NOTIFICATION_ID_GRACE_ENTERED = 5712
            const val NOTIFICATION_ID_EXPIRY_IMMINENT = 5713
        }
    }
