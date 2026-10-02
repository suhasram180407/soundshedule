package com.soundschedule.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soundschedule.data.ScheduleRepository
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.utils.AppLog
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * BroadcastReceiver triggered by Android OS when device finishes booting (ACTION_BOOT_COMPLETED).
 * Restores all future exact alarms for configured schedules without altering current device sound mode.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            AppLog.w(TAG, "BootReceiver received unexpected action: $action. Discarding.")
            return
        }

        AppLog.i(TAG, "ACTION_BOOT_COMPLETED received. Initiating schedule recovery.")
        val repository = ScheduleRepositoryImpl.getInstance(context)
        val scheduler = AlarmScheduler.getInstance(context)

        handleBootRecovery(repository, scheduler)

        // Update home-screen widget after boot
        com.soundschedule.widget.SoundScheduleWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        const val TAG = "BootReceiver"

        /**
         * Core reboot recovery logic decoupled from Android Context for deterministic unit testing.
         * Guarantees idempotency and ensures current device ringer mode is never altered during boot.
         */
        fun handleBootRecovery(
            repository: ScheduleRepository,
            scheduler: AlarmScheduler,
            referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
        ): BootRecoveryResult {
            val isMasterEnabled = repository.isMasterEnabledSync()
            if (!isMasterEnabled) {
                AppLog.i(TAG, "Master Schedule is OFF after boot. No alarms created; phone sound state left unchanged.")
                scheduler.cancelAllAlarms(repository.getAllSchedulesSync())
                return BootRecoveryResult.MasterOff
            }

            if (!scheduler.canScheduleExactAlarms()) {
                AppLog.w(TAG, "Exact alarm permission missing after boot. Exact alarms not restored; device sound state left unchanged.")
                return BootRecoveryResult.MissingPermission
            }

            val schedules = repository.getAllSchedulesSync()
            AppLog.i(TAG, "Master Schedule is ON. Synchronizing alarms for ${schedules.size} schedules after boot.")
            scheduler.syncAllAlarms(
                events = schedules,
                isMasterEnabled = true,
                referenceTime = referenceTime
            )

            val enabledCount = schedules.count { it.enabled && it.daysOfWeek.isNotEmpty() }
            return BootRecoveryResult.Restored(enabledCount)
        }
    }

    sealed interface BootRecoveryResult {
        object MasterOff : BootRecoveryResult
        object MissingPermission : BootRecoveryResult
        data class Restored(val restoredCount: Int) : BootRecoveryResult
    }
}
