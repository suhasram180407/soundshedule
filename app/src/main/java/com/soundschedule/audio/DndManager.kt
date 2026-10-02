package com.soundschedule.audio

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.soundschedule.utils.AppLog

/**
 * Dedicated DND Manager implementing official Android Notification Policy / Zen API.
 * Keeps DND completely decoupled from ringer mode / SILENT implementations.
 */
class DndManager(
    private val adapter: SystemAudioAdapter
) {
    private val tag = "DndManager"

    var isExplicitlyEnabled: Boolean = false
        private set

    fun hasPermission(): Boolean {
        return adapter.isNotificationPolicyAccessGranted()
    }

    fun isDndActive(): Boolean {
        if (!hasPermission()) return false
        val filter = adapter.getInterruptionFilter()
        val isZen = filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                filter == NotificationManager.INTERRUPTION_FILTER_NONE
        if (!isZen) {
            isExplicitlyEnabled = false
            return false
        }
        // If explicitly enabled via DND mode, report true
        if (isExplicitlyEnabled) return true
        // In Android AOSP, AudioService automatically sets ZenMode to PRIORITY
        // whenever any external app calls setRingerMode(SILENT).
        // To keep MUTE and DND completely decoupled as independent states,
        // DND mode is not considered active when ringer is SILENT unless DND was explicitly activated.
        return adapter.getRingerMode() != android.media.AudioManager.RINGER_MODE_SILENT
    }

    fun enableDnd(): Boolean {
        if (!hasPermission()) {
            AppLog.w(tag, "Cannot enable DND: Notification policy access not granted.")
            return false
        }
        return try {
            adapter.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            isExplicitlyEnabled = true
            AppLog.i(tag, "DND enabled via INTERRUPTION_FILTER_PRIORITY.")
            true
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to set DND filter: ${e.message}", e)
            false
        }
    }

    fun disableDnd(): Boolean {
        isExplicitlyEnabled = false
        if (!hasPermission()) return false
        return try {
            adapter.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            AppLog.i(tag, "DND disabled, restored INTERRUPTION_FILTER_ALL.")
            true
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to disable DND: ${e.message}", e)
            false
        }
    }

    fun openSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to open DND settings: ${e.message}", e)
        }
    }
}
