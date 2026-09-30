package com.securecam.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.securecam.app.service.BackgroundVideoRecordingService

/**
 * Application-level setup: creates the low-importance notification channel
 * for background recording exactly once, at process start.
 *
 * IMPORTANCE_LOW is deliberate (per spec): the notification is always
 * visible in the shade without intruding as a heads-up banner.
 */
class SecureCamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            BackgroundVideoRecordingService.CHANNEL_ID,
            getString(R.string.bg_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.bg_notification_channel_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }
}
