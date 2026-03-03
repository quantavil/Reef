package dev.pranav.reef.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.R
import dev.pranav.reef.scheduleWatcher
import dev.pranav.reef.services.routines.RoutineSessionManager
import dev.pranav.reef.util.*
import dev.pranav.reef.util.NotificationHelper.BLOCKER_GROUP_KEY
import dev.pranav.reef.util.NotificationHelper.createNotificationChannel
import dev.pranav.reef.util.NotificationHelper.syncRoutineNotification

@SuppressLint("AccessibilityPolicy")
class BlockerService: AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val routinePollRunnable = object: Runnable {
        override fun run() {
            try {
                RoutineSessionManager.evaluateAndSync(this@BlockerService)
                syncRoutineNotification(this@BlockerService)
            } catch (e: Exception) {
                Log.e("BlockerService", "Routine poll error", e)
            }
            handler.postDelayed(this, ROUTINE_POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()

        if (!isPrefsInitialized) {
            val deviceContext = createDeviceProtectedStorageContext()
            prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
        }

        scheduleWatcher(this)
        handler.post(routinePollRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (keyguardManager.isKeyguardLocked) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) return

        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) return
        if (Whitelist.isWhitelisted(pkg)) return

        if (prefs.getBoolean("focus_mode", false)) {
            Log.d("BlockerService", "Blocking $pkg in focus mode")
            performGlobalAction(GLOBAL_ACTION_HOME)
            showFocusModeNotification(pkg)
            return
        }

        val blockReason = UsageTracker.checkBlockReason(this, pkg)
        if (blockReason == UsageTracker.BlockReason.NONE) return

        Log.d("BlockerService", "Blocking $pkg due to ${blockReason.name}")
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockedNotification(pkg, blockReason)
    }

    @SuppressLint("MissingPermission")
    private fun showBlockedNotification(pkg: String, reason: UsageTracker.BlockReason) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled().not()) {
            Log.w("BlockerService", "Notifications disabled by user")
            return
        }

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }

        val contentText = when (reason) {
            UsageTracker.BlockReason.ROUTINE_LIMIT -> getString(
                R.string.blocked_by_routine,
                appName
            )

            else -> getString(R.string.reached_limit, appName)
        }

        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_blocked))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(BLOCKER_GROUP_KEY)
            .setAutoCancel(true)
            .build()

        val summary = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup(BLOCKER_GROUP_KEY)
            .setGroupSummary(true)
            .build()

        manager.notify(pkg.hashCode(), notification)
        manager.notify(NotificationHelper.BLOCKER_SUMMARY_ID, summary)
    }

    @SuppressLint("MissingPermission")
    private fun showFocusModeNotification(pkg: String) {
        FocusStats.recordBlockEvent(pkg, "focus_mode")

        if (NotificationManagerCompat.from(this).areNotificationsEnabled().not()) return
        if (!prefs.getBoolean("focus_reminders", true)) return

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }

        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.distraction_blocked))
            .setContentText(getString(R.string.you_were_using, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(pkg.hashCode(), notification)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(routinePollRunnable)
    }

    companion object {
        private const val ROUTINE_POLL_INTERVAL_MS = 30_000L
    }
}
