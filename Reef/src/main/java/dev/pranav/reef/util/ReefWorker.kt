package dev.pranav.reef.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.accessibility.RoutinesService

class ReefWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {
        val safeContext = applicationContext.createDeviceProtectedStorageContext()

        val prefs = safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val isFocusModeActive = prefs.getBoolean("focus_mode", false)

        if (!safeContext.isAccessibilityServiceEnabledForBlocker()) {
            sendInstantNotification(
                safeContext,
                channelId = "reef_alerts",
                channelName = "Reef Alerts",
                title = "Reef Accessibility Disabled",
                message = "Please re-enable Reef's Accessibility Service for proper functionality."
            )
            return Result.success()
        }

        if (isFocusModeActive) {
            val intent = Intent(safeContext, FocusModeService::class.java)
            safeContext.startForegroundService(intent)
        }

        RoutinesService.start(safeContext)

        return Result.success()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendInstantNotification(
        context: Context,
        channelId: String,
        channelName: String,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for Reef alerts"
        }
        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handle missing permission
        }
    }
}
