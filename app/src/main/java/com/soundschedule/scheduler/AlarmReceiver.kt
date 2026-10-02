package com.soundschedule.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.audio.RingerModeResult
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepository
import com.soundschedule.data.SoundMode
import com.soundschedule.utils.AppLog
import com.soundschedule.utils.TimeUtils
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * BroadcastReceiver triggered by AlarmManager exact alarms to apply scheduled ringer mode transitions.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_EXECUTE_SCHEDULE) {
            AppLog.w(TAG, "Received unrecognized intent action: $action. Discarding.")
            return
        }

        val eventId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val isStart = intent.getBooleanExtra(EXTRA_IS_START, true)
        if (eventId <= 0L) {
            AppLog.w(TAG, "Received alarm intent missing valid $EXTRA_SCHEDULE_ID (got $eventId). Discarding.")
            return
        }

        AppLog.i(TAG, "Alarm intent triggered for schedule id=$eventId (isStart=$isStart)")

        val repository = com.soundschedule.data.ScheduleRepositoryImpl.getInstance(context)
        val ringerManager = RingerModeManager.getInstance(context)
        val scheduler = AlarmScheduler.getInstance(context)

        handleAlarmExecution(
            eventId = eventId,
            isStart = isStart,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler
        )

        // Update home-screen widget with new audio and upcoming schedule state
        com.soundschedule.widget.SoundScheduleWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        const val TAG = "AlarmReceiver"

        /**
         * Core alarm handling logic extracted and decoupled from Android Context
         * for reliable, deterministic unit testing.
         */
        fun handleAlarmExecution(
            eventId: Long,
            isStart: Boolean = true,
            repository: ScheduleRepository,
            ringerManager: RingerModeManager,
            scheduler: AlarmScheduler,
            currentTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
        ): ExecutionResult {
            // 1. Check Master Schedule status
            val isMasterEnabled = repository.isMasterEnabledSync()
            if (!isMasterEnabled) {
                AppLog.w(TAG, "Master Schedule is OFF. Ignoring alarm for event id=$eventId without modifying phone sound state.")
                return ExecutionResult.MasterOff
            }

            // 2. Locate the schedule event
            val schedules = repository.getAllSchedulesSync()
            val event = schedules.firstOrNull { it.id == eventId }
            if (event == null) {
                AppLog.w(TAG, "Stale alarm detected: schedule id=$eventId no longer exists in repository. Ignoring.")
                return ExecutionResult.ScheduleNotFound
            }

            // 3. Check if individual schedule is enabled
            if (!event.enabled) {
                AppLog.w(TAG, "Stale alarm detected: schedule id=$eventId (${event.formattedTime}) is disabled. Ignoring.")
                return ExecutionResult.ScheduleDisabled
            }

            // 4. Verify whether current time is within acceptable execution window
            if (!TimeUtils.isWithinExecutionWindow(event, currentTime, isStart = isStart)) {
                AppLog.w(TAG, "Alarm for schedule id=$eventId (isStart=$isStart) received outside tolerance window at $currentTime. Stale alarm ignored.")
                if (isStart) {
                    scheduler.scheduleNextStartOccurrence(event, currentTime)
                } else {
                    scheduler.scheduleNextEndOccurrence(event, currentTime)
                }
                return ExecutionResult.OutsideToleranceWindow(event)
            }

            // 5. Determine target sound mode
            val targetMode: SoundMode
            if (isStart) {
                targetMode = event.mode
                AppLog.i(TAG, "Executing START for schedule id=${event.id} (${event.formattedTime}, label='${event.label}'): Target Mode=$targetMode")
            } else {
                // At END: resolve resulting sound state (check for any other active session, else NORMAL)
                val otherActive = schedules.firstOrNull { it.id != eventId && TimeUtils.isSessionActive(it, currentTime) }
                targetMode = otherActive?.mode ?: SoundMode.NORMAL
                AppLog.i(TAG, "Executing END for schedule id=${event.id} (${event.formattedTime}): Resolved resulting mode=$targetMode (overlapping=${otherActive?.label})")
            }

            val ringerResult = ringerManager.setRingerMode(targetMode)
            val actualResultingMode = ringerManager.getCurrentMode()

            when (ringerResult) {
                is RingerModeResult.Success -> {
                    AppLog.i(TAG, "Sound mode successfully changed to $actualResultingMode for schedule id=${event.id} (isStart=$isStart)")
                }
                is RingerModeResult.PermissionRequired -> {
                    AppLog.w(TAG, "Cannot apply mode $targetMode for schedule id=${event.id}: Missing DND permission (${ringerResult.message})")
                }
                is RingerModeResult.Failed -> {
                    AppLog.e(TAG, "Failed to apply mode $targetMode for schedule id=${event.id}: ${ringerResult.reason}")
                }
            }

            // 6. Reschedule next occurrence for this recurring schedule
            if (isStart) {
                scheduler.scheduleNextStartOccurrence(event, currentTime)
            } else {
                scheduler.scheduleNextEndOccurrence(event, currentTime)
            }
            AppLog.i(TAG, "Rescheduled next occurrence for schedule id=${event.id} (isStart=$isStart)")

            return ExecutionResult.Executed(
                event = event,
                ringerResult = ringerResult,
                actualSystemMode = actualResultingMode,
                isStart = isStart
            )
        }
    }

    /**
     * Outcome of processing an alarm intent.
     */
    sealed interface ExecutionResult {
        object MasterOff : ExecutionResult
        object ScheduleNotFound : ExecutionResult
        object ScheduleDisabled : ExecutionResult
        data class OutsideToleranceWindow(val event: ScheduleEvent) : ExecutionResult
        data class Executed(
            val event: ScheduleEvent,
            val ringerResult: RingerModeResult,
            val actualSystemMode: SoundMode,
            val isStart: Boolean = true
        ) : ExecutionResult
    }
}
