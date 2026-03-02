package dev.pranav.reef.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import dev.pranav.reef.accessibility.BlockerService
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.accessibility.RoutinesService
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val safeContext =
            context.createDeviceProtectedStorageContext()

        if (!isPrefsInitialized) {
            prefs = safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }

        Log.d("BootReceiver", "Action received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> {
                refreshServices(safeContext)

                RoutinesService.start(safeContext)

                if (prefs.getBoolean("daily_summary", false)) {
                    DailySummaryScheduler.scheduleDailySummary(safeContext)
                }

                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    prefs.edit { putBoolean("show_dialog", true) }
                }
            }
        }
    }

    private fun refreshServices(context: Context) {
        if (context.isAccessibilityServiceEnabledForBlocker()) {
            val accessibilityIntent = Intent(context, BlockerService::class.java)
            try {
                context.startService(accessibilityIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Could not nudge BlockerService", e)
            }
        }

        if (prefs.getBoolean("focus_mode", false)) {
            val serviceIntent = Intent(context, FocusModeService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
