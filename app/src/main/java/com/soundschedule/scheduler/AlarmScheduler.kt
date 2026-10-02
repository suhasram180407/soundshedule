package com.soundschedule.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.utils.AppLog
import com.soundschedule.utils.TimeUtils
import java.time.ZoneId
import java.time.ZonedDateTime

const val ACTION_EXECUTE_SCHEDULE = "com.soundschedule.ACTION_EXECUTE_SCHEDULE"
const val EXTRA_SCHEDULE_ID = "com.soundschedule.EXTRA_SCHEDULE_ID"
const val EXTRA_IS_START = "com.soundschedule.EXTRA_IS_START"

/**
 * Interface abstracting system alarm operations for unit testability without Android runtime stubs.
 */
interface AlarmSystemAdapter {
    fun canScheduleExactAlarms(): Boolean
    fun setExactAndAllowWhileIdle(triggerAtMillis: Long, eventId: Long, isStart: Boolean = true)
    fun cancelAlarm(eventId: Long)
    fun cancelAlarm(eventId: Long, isStart: Boolean)
    fun openExactAlarmSettings(context: Context)
}

/**
 * Android implementation of [AlarmSystemAdapter] using [AlarmManager] and [PendingIntent].
 */
class AndroidAlarmSystemAdapter(private val context: Context) : AlarmSystemAdapter {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, eventId: Long, isStart: Boolean) {
        if (alarmManager == null) {
            AppLog.e("AlarmScheduler", "AlarmManager service unavailable on device.")
            return
        }

        val pendingIntent = createPendingIntent(eventId, isStart)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            AppLog.e("AlarmScheduler", "SecurityException scheduling exact alarm for id=$eventId isStart=$isStart: ${e.message}")
        }
    }

    override fun cancelAlarm(eventId: Long) {
        cancelAlarm(eventId, true)
        cancelAlarm(eventId, false)
    }

    override fun cancelAlarm(eventId: Long, isStart: Boolean) {
        if (alarmManager == null) return
        val pendingIntent = createPendingIntent(eventId, isStart)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLog.e("AlarmScheduler", "Failed to launch ACTION_REQUEST_SCHEDULE_EXACT_ALARM: ${e.message}")
            }
        }
    }

    private fun createPendingIntent(eventId: Long, isStart: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_EXECUTE_SCHEDULE
            putExtra(EXTRA_SCHEDULE_ID, eventId)
            putExtra(EXTRA_IS_START, isStart)
            data = Uri.parse("soundschedule://alarm/${if (isStart) "start" else "end"}/$eventId")
        }
        val requestCode = if (isStart) {
            ((eventId * 2) and 0x7FFFFFFF).toInt()
        } else {
            (((eventId * 2) + 1) and 0x7FFFFFFF).toInt()
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}

/**
 * Background AlarmScheduler responsible for computing occurrences and managing AlarmManager alarms.
 */
class AlarmScheduler(
    private val adapter: AlarmSystemAdapter
) {
    private val tag = "AlarmScheduler"

    fun canScheduleExactAlarms(): Boolean = adapter.canScheduleExactAlarms()

    /**
     * Schedules the next start occurrence for [event].
     */
    fun scheduleNextStartOccurrence(
        event: ScheduleEvent,
        referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled || event.daysOfWeek.isEmpty() || !adapter.canScheduleExactAlarms()) {
            return false
        }
        val nextOccurrence = TimeUtils.calculateNextOccurrence(event, referenceTime) ?: return false
        val triggerMillis = nextOccurrence.toInstant().toEpochMilli()
        adapter.setExactAndAllowWhileIdle(triggerMillis, event.id, isStart = true)
        AppLog.i(tag, "Scheduled START alarm: id=${event.id}, target=${event.formattedTime} (${event.mode}), trigger=$nextOccurrence ($triggerMillis ms)")
        return true
    }

    /**
     * Schedules the next end occurrence for [event].
     */
    fun scheduleNextEndOccurrence(
        event: ScheduleEvent,
        referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled || event.daysOfWeek.isEmpty() || !adapter.canScheduleExactAlarms()) {
            return false
        }
        val nextOccurrence = TimeUtils.calculateNextEndOccurrence(event, referenceTime) ?: return false
        val triggerMillis = nextOccurrence.toInstant().toEpochMilli()
        adapter.setExactAndAllowWhileIdle(triggerMillis, event.id, isStart = false)
        AppLog.i(tag, "Scheduled END alarm: id=${event.id}, target=${event.formattedTime}, trigger=$nextOccurrence ($triggerMillis ms)")
        return true
    }

    /**
     * Schedules the next start and end occurrences of session [event] relative to [referenceTime].
     * Returns true if successfully scheduled, false otherwise.
     */
    fun scheduleNextOccurrence(
        event: ScheduleEvent,
        referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled) {
            AppLog.d(tag, "Schedule id=${event.id} is disabled. Cancelling alarm.")
            adapter.cancelAlarm(event.id)
            return false
        }

        if (event.daysOfWeek.isEmpty()) {
            AppLog.w(tag, "Schedule id=${event.id} has no active days. Cancelling alarm.")
            adapter.cancelAlarm(event.id)
            return false
        }

        if (!adapter.canScheduleExactAlarms()) {
            AppLog.w(tag, "Exact alarm permission missing. Cannot schedule exact alarm for schedule id=${event.id}.")
            return false
        }

        val startScheduled = scheduleNextStartOccurrence(event, referenceTime)
        val endScheduled = scheduleNextEndOccurrence(event, referenceTime)
        return startScheduled || endScheduled
    }

    /**
     * Cancels existing alarms (both start and end) for the given schedule [eventId].
     */
    fun cancelAlarm(eventId: Long) {
        adapter.cancelAlarm(eventId)
        AppLog.d(tag, "Cancelled start and end alarms for schedule id=$eventId")
    }

    /**
     * Cancels all alarms for the given collection of [events].
     */
    fun cancelAllAlarms(events: List<ScheduleEvent>) {
        for (event in events) {
            cancelAlarm(event.id)
        }
        AppLog.i(tag, "Cancelled all alarms for ${events.size} schedules.")
    }

    /**
     * Synchronizes all active schedules with AlarmManager.
     * When Master Schedule is OFF, cancels all alarms without modifying device sound state.
     * When Master Schedule is ON, creates exact alarms for enabled events and cancels disabled events.
     */
    fun syncAllAlarms(
        events: List<ScheduleEvent>,
        isMasterEnabled: Boolean,
        referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ) {
        if (!isMasterEnabled) {
            AppLog.i(tag, "Master Schedule is OFF. Cancelling all alarms without changing phone ringer mode.")
            cancelAllAlarms(events)
            return
        }

        AppLog.i(tag, "Master Schedule is ON. Synchronizing alarms for ${events.size} schedules.")
        for (event in events) {
            if (event.enabled) {
                scheduleNextOccurrence(event, referenceTime)
            } else {
                cancelAlarm(event.id)
            }
        }
    }

    fun openExactAlarmSettings(context: Context) {
        adapter.openExactAlarmSettings(context)
    }

    companion object {
        @Volatile
        private var instance: AlarmScheduler? = null

        fun getInstance(context: Context): AlarmScheduler {
            return instance ?: synchronized(this) {
                instance ?: AlarmScheduler(
                    adapter = AndroidAlarmSystemAdapter(context.applicationContext)
                ).also { instance = it }
            }
        }

        // For unit test injection
        fun setTestInstance(testInstance: AlarmScheduler?) {
            instance = testInstance
        }
    }
}
