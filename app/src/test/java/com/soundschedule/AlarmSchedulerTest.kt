package com.soundschedule

import android.content.Context
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.SoundMode
import com.soundschedule.scheduler.AlarmScheduler
import com.soundschedule.scheduler.AlarmSystemAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FakeAlarmSystemAdapter : AlarmSystemAdapter {
    var exactAlarmPermissionGranted = true
    val scheduledStartAlarms = mutableMapOf<Long, Long>() // eventId -> triggerAtMillis
    val scheduledEndAlarms = mutableMapOf<Long, Long>()   // eventId -> triggerAtMillis
    val cancelledAlarmIds = mutableListOf<Long>()
    var exactAlarmSettingsOpened = false

    override fun canScheduleExactAlarms(): Boolean = exactAlarmPermissionGranted

    override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, eventId: Long, isStart: Boolean) {
        if (isStart) {
            scheduledStartAlarms[eventId] = triggerAtMillis
        } else {
            scheduledEndAlarms[eventId] = triggerAtMillis
        }
    }

    override fun cancelAlarm(eventId: Long) {
        scheduledStartAlarms.remove(eventId)
        scheduledEndAlarms.remove(eventId)
        cancelledAlarmIds.add(eventId)
    }

    override fun cancelAlarm(eventId: Long, isStart: Boolean) {
        if (isStart) scheduledStartAlarms.remove(eventId) else scheduledEndAlarms.remove(eventId)
        cancelledAlarmIds.add(eventId)
    }

    override fun openExactAlarmSettings(context: Context) {
        exactAlarmSettingsOpened = true
    }
}

class AlarmSchedulerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val mondayDate = LocalDate.of(2026, 10, 5)
    private lateinit var adapter: FakeAlarmSystemAdapter
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        adapter = FakeAlarmSystemAdapter()
        scheduler = AlarmScheduler(adapter)
    }

    private fun createEvent(
        id: Long,
        startHour: Int,
        startMinute: Int,
        endHour: Int = (startHour + 1) % 24,
        endMinute: Int = startMinute,
        mode: SoundMode = SoundMode.VIBRATE,
        enabled: Boolean = true,
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    ) = ScheduleEvent(
        id = id,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        mode = mode,
        enabled = enabled,
        daysOfWeek = days,
        label = "Event $id"
    )

    @Test
    fun scheduleEnabled_startAndEndAlarmsCreated() {
        val event = createEvent(id = 101L, startHour = 8, startMinute = 50, endHour = 11, endMinute = 0)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)

        val scheduled = scheduler.scheduleNextOccurrence(event, now)

        assertTrue(scheduled)
        assertTrue(adapter.scheduledStartAlarms.containsKey(101L))
        assertTrue(adapter.scheduledEndAlarms.containsKey(101L))

        val startTriggerMillis = adapter.scheduledStartAlarms[101L]
        val endTriggerMillis = adapter.scheduledEndAlarms[101L]
        assertNotNull(startTriggerMillis)
        assertNotNull(endTriggerMillis)

        val expectedStartTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50), zone).toInstant().toEpochMilli()
        val expectedEndTime = ZonedDateTime.of(mondayDate, LocalTime.of(11, 0), zone).toInstant().toEpochMilli()

        assertEquals(expectedStartTime, startTriggerMillis)
        assertEquals(expectedEndTime, endTriggerMillis)
    }

    @Test
    fun scheduleDisabled_bothAlarmsCancelled() {
        val event = createEvent(id = 102L, startHour = 8, startMinute = 50, enabled = false)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)

        val scheduled = scheduler.scheduleNextOccurrence(event, now)

        assertFalse(scheduled)
        assertFalse(adapter.scheduledStartAlarms.containsKey(102L))
        assertFalse(adapter.scheduledEndAlarms.containsKey(102L))
        assertTrue(adapter.cancelledAlarmIds.contains(102L))
    }

    @Test
    fun masterON_alarmsScheduled() {
        val event1 = createEvent(id = 1L, startHour = 8, startMinute = 50, enabled = true)
        val event2 = createEvent(id = 2L, startHour = 11, startMinute = 0, enabled = true)
        val event3 = createEvent(id = 3L, startHour = 13, startMinute = 0, enabled = false) // disabled

        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        scheduler.syncAllAlarms(listOf(event1, event2, event3), isMasterEnabled = true, referenceTime = now)

        assertTrue(adapter.scheduledStartAlarms.containsKey(1L))
        assertTrue(adapter.scheduledStartAlarms.containsKey(2L))
        assertFalse(adapter.scheduledStartAlarms.containsKey(3L))
        assertTrue(adapter.cancelledAlarmIds.contains(3L))
    }

    @Test
    fun masterOFF_allAlarmsCancelled() {
        // Pre-populate some alarms
        adapter.scheduledStartAlarms[1L] = 1000L
        adapter.scheduledStartAlarms[2L] = 2000L

        val event1 = createEvent(id = 1L, startHour = 8, startMinute = 50)
        val event2 = createEvent(id = 2L, startHour = 11, startMinute = 0)

        scheduler.syncAllAlarms(listOf(event1, event2), isMasterEnabled = false)

        assertTrue(adapter.scheduledStartAlarms.isEmpty())
        assertTrue(adapter.scheduledEndAlarms.isEmpty())
        assertTrue(adapter.cancelledAlarmIds.contains(1L))
        assertTrue(adapter.cancelledAlarmIds.contains(2L))
    }

    @Test
    fun missingExactAlarmPermission_refusesSchedulingSafely() {
        adapter.exactAlarmPermissionGranted = false
        val event = createEvent(id = 201L, startHour = 8, startMinute = 50)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)

        val scheduled = scheduler.scheduleNextOccurrence(event, now)

        assertFalse(scheduled)
        assertFalse(adapter.scheduledStartAlarms.containsKey(201L))
    }

    @Test
    fun changedSchedule_oldAlarmReplacedWithNewTime() {
        val oldEvent = createEvent(id = 301L, startHour = 8, startMinute = 50)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        scheduler.scheduleNextOccurrence(oldEvent, now)

        val oldTrigger = adapter.scheduledStartAlarms[301L]
        assertNotNull(oldTrigger)

        // User edits start time to 09:10
        val updatedEvent = oldEvent.copy(startHour = 9, startMinute = 10)
        scheduler.scheduleNextOccurrence(updatedEvent, now)

        val newTrigger = adapter.scheduledStartAlarms[301L]
        assertNotNull(newTrigger)
        assertTrue(newTrigger!! > oldTrigger!!)

        val expectedNewTime = ZonedDateTime.of(mondayDate, LocalTime.of(9, 10), zone).toInstant().toEpochMilli()
        assertEquals(expectedNewTime, newTrigger)
    }

    @Test
    fun alarmFires_reschedulesNextOccurrence() {
        val event = createEvent(id = 401L, startHour = 8, startMinute = 50)
        // Simulate reference time right as Monday 08:50 fires
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50), zone)

        scheduler.scheduleNextStartOccurrence(event, triggerTime)

        val nextTrigger = adapter.scheduledStartAlarms[401L]
        assertNotNull(nextTrigger)

        // Next occurrence must be Tuesday 08:50
        val expectedTuesday = ZonedDateTime.of(mondayDate.plusDays(1), LocalTime.of(8, 50), zone).toInstant().toEpochMilli()
        assertEquals(expectedTuesday, nextTrigger)
    }

    @Test
    fun alarmIdentity_multipleSchedulesSameTimeRemainDistinct() {
        // Schedule 1: 08:50 VIBRATE
        val s1 = createEvent(id = 11L, startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE, days = setOf(DayOfWeek.MONDAY))
        // Schedule 2: 08:50 NORMAL on Saturday
        val s2 = createEvent(id = 12L, startHour = 8, startMinute = 50, mode = SoundMode.NORMAL, days = setOf(DayOfWeek.SATURDAY))

        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        scheduler.scheduleNextOccurrence(s1, now)
        scheduler.scheduleNextOccurrence(s2, now)

        assertEquals(2, adapter.scheduledStartAlarms.size)
        assertTrue(adapter.scheduledStartAlarms.containsKey(11L))
        assertTrue(adapter.scheduledStartAlarms.containsKey(12L))

        // Cancelling one does not affect the other
        scheduler.cancelAlarm(11L)
        assertFalse(adapter.scheduledStartAlarms.containsKey(11L))
        assertTrue(adapter.scheduledStartAlarms.containsKey(12L))
    }
}
