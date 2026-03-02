package dev.pranav.reef.accessibility

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.services.routines.RoutineSessionManager
import dev.pranav.reef.util.ROUTINE_CHANNEL_ID
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

class RoutinesService: Service() {

    companion object {
        private const val TAG = "RoutinesService"
        private const val NOTIFICATION_ID = 5001
        private const val POLL_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, RoutinesService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start RoutinesService", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoutinesService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object: Runnable {
        override fun run() {
            try {
                RoutineSessionManager.evaluateAndSync(this@RoutinesService)
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Error in poll loop", e)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!isPrefsInitialized) {
            val deviceContext = createDeviceProtectedStorageContext()
            prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
        }
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        Log.d(TAG, "RoutinesService started with poll interval ${POLL_INTERVAL_MS}ms")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        Log.d(TAG, "RoutinesService destroyed")
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val activeNames = RoutineSessionManager.getActiveRoutineNames()

        val contentText = if (activeNames.isEmpty()) {
            getString(R.string.no_active_routines)
        } else {
            getString(R.string.active_routines_list, activeNames.joinToString(", "))
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to_routines", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ROUTINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.round_schedule_24)
            .setContentTitle(getString(R.string.routines))
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ROUTINE_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                ROUTINE_CHANNEL_ID,
                getString(R.string.routine_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.routine_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

