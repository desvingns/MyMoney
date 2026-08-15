package com.kshavrin.mymoney.core.sync.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.kshavrin.mymoney.core.sync.R

enum class MyMoneyNotificationChannel(
    val id: String,
    val nameRes: Int,
    val importance: Int,
) {
    SUBSCRIPTION(
        id = "subscription",
        nameRes = R.string.notification_channel_subscription_name,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
}

object MyMoneyNotificationChannels {
    fun registerAll(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        MyMoneyNotificationChannel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(
                    channel.id,
                    context.getString(channel.nameRes),
                    channel.importance,
                ),
            )
        }
    }
}
