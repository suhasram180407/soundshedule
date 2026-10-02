package com.soundschedule

import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.SoundMode
import com.soundschedule.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeUtilsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // Anchor: 2026-10-05 is a Monday
    private val mondayDate = LocalDate.of(2026, 10, 5)

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
    }

    private fun createEvent(
        id: Long = 1L,
        hour: Int,
        minute: Int,
        days: Set<DayOfWeek>,
        enabled: Boolean = true
    ) = ScheduleEvent(
        id = id,
        hour = hour,
        minute = minute,
        mode = SoundMode.VIBRATE,
        enabled = enabled,
        daysOfWeek = days,
        label = "Test"
    )

    // 1. Monday before event
    @Test
    fun calculateNextOccurrence_mondayBeforeEvent() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
        assertEquals(8, next.hour)
        assertEquals(50, next.minute)
        assertEquals(mondayDate, next.toLocalDate())
    }

    // 2. Monday after event
    @Test
    fun calculateNextOccurrence_mondayAfterEvent() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(9, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.TUESDAY, next!!.dayOfWeek)
        assertEquals(8, next.hour)
        assertEquals(50, next.minute)
        assertEquals(mondayDate.plusDays(1), next.toLocalDate())
    }

    // 3. Friday after event
    @Test
    fun calculateNextOccurrence_fridayAfterEvent() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val fridayDate = mondayDate.plusDays(4) // 2026-10-09
        val now = ZonedDateTime.of(fridayDate, LocalTime.of(17, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
        assertEquals(8, next.hour)
        assertEquals(50, next.minute)
        assertEquals(mondayDate.plusDays(7), next.toLocalDate())
    }

    // 4. Saturday with Mon-Fri schedule
    @Test
    fun calculateNextOccurrence_saturdayWithMonFriSchedule() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val saturdayDate = mondayDate.plusDays(5) // 2026-10-10
        val now = ZonedDateTime.of(saturdayDate, LocalTime.of(10, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
        assertEquals(8, next.hour)
        assertEquals(50, next.minute)
        assertEquals(mondayDate.plusDays(7), next.toLocalDate())
    }

    // 5. Sunday with Mon-Fri schedule
    @Test
    fun calculateNextOccurrence_sundayWithMonFriSchedule() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val sundayDate = mondayDate.plusDays(6) // 2026-10-11
        val now = ZonedDateTime.of(sundayDate, LocalTime.of(12, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
        assertEquals(8, next.hour)
        assertEquals(50, next.minute)
        assertEquals(mondayDate.plusDays(7), next.toLocalDate())
    }

    // 6. Single-day schedule
    @Test
    fun calculateNextOccurrence_singleDaySchedule() {
        val event = createEvent(
            hour = 10,
            minute = 0,
            days = setOf(DayOfWeek.WEDNESDAY)
        )
        val wednesdayDate = mondayDate.plusDays(2) // 2026-10-07

        // Before event on Wednesday
        val before = ZonedDateTime.of(wednesdayDate, LocalTime.of(9, 0), zone)
        val nextBefore = TimeUtils.calculateNextOccurrence(event, before)
        assertNotNull(nextBefore)
        assertEquals(wednesdayDate, nextBefore!!.toLocalDate())
        assertEquals(10, nextBefore.hour)
        assertEquals(0, nextBefore.minute)

        // After event on Wednesday
        val after = ZonedDateTime.of(wednesdayDate, LocalTime.of(14, 0), zone)
        val nextAfter = TimeUtils.calculateNextOccurrence(event, after)
        assertNotNull(nextAfter)
        assertEquals(wednesdayDate.plusWeeks(1), nextAfter!!.toLocalDate())
        assertEquals(DayOfWeek.WEDNESDAY, nextAfter.dayOfWeek)
        assertEquals(10, nextAfter.hour)
        assertEquals(0, nextAfter.minute)
    }

    // 7. Weekend schedule
    @Test
    fun calculateNextOccurrence_weekendSchedule() {
        val event = createEvent(
            hour = 10,
            minute = 0,
            days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        )
        val sundayDate = mondayDate.plusDays(6) // 2026-10-11
        val now = ZonedDateTime.of(sundayDate, LocalTime.of(11, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.SATURDAY, next!!.dayOfWeek)
        assertEquals(mondayDate.plusDays(12), next.toLocalDate())
        assertEquals(10, next.hour)
        assertEquals(0, next.minute)
    }

    // 8. Every-day schedule
    @Test
    fun calculateNextOccurrence_everyDaySchedule() {
        val event = createEvent(
            hour = 12,
            minute = 0,
            days = DayOfWeek.values().toSet()
        )
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(13, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.TUESDAY, next!!.dayOfWeek)
        assertEquals(mondayDate.plusDays(1), next.toLocalDate())
        assertEquals(12, next.hour)
        assertEquals(0, next.minute)
    }

    // 9. Multiple selected days (Tuesday, Thursday, Saturday)
    @Test
    fun calculateNextOccurrence_multipleSelectedDays() {
        val event = createEvent(
            hour = 9,
            minute = 0,
            days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        )
        // Reference time is Wednesday 10:00
        val wednesdayDate = mondayDate.plusDays(2)
        val now = ZonedDateTime.of(wednesdayDate, LocalTime.of(10, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.THURSDAY, next!!.dayOfWeek)
        assertEquals(wednesdayDate.plusDays(1), next.toLocalDate())
        assertEquals(9, next.hour)
        assertEquals(0, next.minute)
    }

    // 10. Event at midnight (00:00)
    @Test
    fun calculateNextOccurrence_eventAtMidnight() {
        val event = createEvent(
            hour = 0,
            minute = 0,
            days = setOf(DayOfWeek.TUESDAY)
        )
        // Reference time is Monday 23:30
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(23, 30), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)

        assertNotNull(next)
        assertEquals(DayOfWeek.TUESDAY, next!!.dayOfWeek)
        assertEquals(mondayDate.plusDays(1), next.toLocalDate())
        assertEquals(0, next.hour)
        assertEquals(0, next.minute)

        // If reference is Monday 00:00, next Tuesday 00:00 is found
        val nowMidnight = ZonedDateTime.of(mondayDate, LocalTime.of(0, 0), zone)
        val nextFromMondayMidnight = TimeUtils.calculateNextOccurrence(event, nowMidnight)
        assertNotNull(nextFromMondayMidnight)
        assertEquals(DayOfWeek.TUESDAY, nextFromMondayMidnight!!.dayOfWeek)
    }

    // 11. Event at 23:59
    @Test
    fun calculateNextOccurrence_eventAt2359() {
        val event = createEvent(
            hour = 23,
            minute = 59,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        )
        // 1 minute before on Monday
        val before = ZonedDateTime.of(mondayDate, LocalTime.of(23, 58), zone)
        val nextBefore = TimeUtils.calculateNextOccurrence(event, before)
        assertNotNull(nextBefore)
        assertEquals(mondayDate, nextBefore!!.toLocalDate())
        assertEquals(23, nextBefore.hour)
        assertEquals(59, nextBefore.minute)

        // Exactly at 23:59 on Monday (next occurrence must be Tuesday 23:59)
        val atEvent = ZonedDateTime.of(mondayDate, LocalTime.of(23, 59), zone)
        val nextAtEvent = TimeUtils.calculateNextOccurrence(event, atEvent)
        assertNotNull(nextAtEvent)
        assertEquals(mondayDate.plusDays(1), nextAtEvent!!.toLocalDate())
        assertEquals(DayOfWeek.TUESDAY, nextAtEvent.dayOfWeek)
        assertEquals(23, nextAtEvent.hour)
        assertEquals(59, nextAtEvent.minute)
    }

    // Empty days should return null
    @Test
    fun calculateNextOccurrence_emptyDaysReturnsNull() {
        val event = createEvent(
            hour = 10,
            minute = 0,
            days = emptySet()
        )
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        val next = TimeUtils.calculateNextOccurrence(event, now)
        assertNull(next)
    }

    // Execution window tolerance tests
    @Test
    fun isWithinExecutionWindow_onTimeReturnsTrue() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY)
        )
        val exact = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 5), zone)
        assertTrue(TimeUtils.isWithinExecutionWindow(event, exact, toleranceMinutes = 15L))
    }

    @Test
    fun isWithinExecutionWindow_outsideToleranceReturnsFalse() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY)
        )
        val late = ZonedDateTime.of(mondayDate, LocalTime.of(9, 10), zone) // 20 mins late
        assertFalse(TimeUtils.isWithinExecutionWindow(event, late, toleranceMinutes = 15L))
    }

    @Test
    fun isWithinExecutionWindow_wrongDayReturnsFalse() {
        val event = createEvent(
            hour = 8,
            minute = 50,
            days = setOf(DayOfWeek.MONDAY)
        )
        val tuesday = ZonedDateTime.of(mondayDate.plusDays(1), LocalTime.of(8, 50), zone)
        assertFalse(TimeUtils.isWithinExecutionWindow(event, tuesday, toleranceMinutes = 15L))
    }

    @Test
    fun isWithinExecutionWindow_midnightRolloverSupported() {
        val event = createEvent(
            hour = 23,
            minute = 59,
            days = setOf(DayOfWeek.MONDAY)
        )
        // Alarm triggered 2 minutes later on Tuesday at 00:01
        val tuesdayEarly = ZonedDateTime.of(mondayDate.plusDays(1), LocalTime.of(0, 1), zone)
        assertTrue(TimeUtils.isWithinExecutionWindow(event, tuesdayEarly, toleranceMinutes = 15L))
    }

    @Test
    fun sessionLifecycle_activeCompletedUpcomingDetectedCorrectly() {
        val session = ScheduleEvent(
            id = 10L,
            startHour = 11,
            startMinute = 0,
            endHour = 12,
            endMinute = 0,
            mode = SoundMode.VIBRATE,
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY)
        )

        // Before session: 10:30 AM -> Upcoming
        val before = ZonedDateTime.of(mondayDate, LocalTime.of(10, 30), zone)
        assertTrue(TimeUtils.isSessionUpcomingToday(session, before))
        assertFalse(TimeUtils.isSessionActive(session, before))
        assertFalse(TimeUtils.isSessionCompletedToday(session, before))

        // During session: 11:30 AM -> Active
        val during = ZonedDateTime.of(mondayDate, LocalTime.of(11, 30), zone)
        assertFalse(TimeUtils.isSessionUpcomingToday(session, during))
        assertTrue(TimeUtils.isSessionActive(session, during))
        assertFalse(TimeUtils.isSessionCompletedToday(session, during))

        // After session: 12:15 PM -> Completed
        val after = ZonedDateTime.of(mondayDate, LocalTime.of(12, 15), zone)
        assertFalse(TimeUtils.isSessionUpcomingToday(session, after))
        assertFalse(TimeUtils.isSessionActive(session, after))
        assertTrue(TimeUtils.isSessionCompletedToday(session, after))
    }

    @Test
    fun calculateNextEndOccurrence_returnsCorrectEndTime() {
        val session = ScheduleEvent(
            id = 10L,
            startHour = 8,
            startMinute = 50,
            endHour = 11,
            endMinute = 0,
            mode = SoundMode.VIBRATE,
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY)
        )

        val before = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        val nextEnd = TimeUtils.calculateNextEndOccurrence(session, before)

        assertNotNull(nextEnd)
        assertEquals(DayOfWeek.MONDAY, nextEnd!!.dayOfWeek)
        assertEquals(11, nextEnd.hour)
        assertEquals(0, nextEnd.minute)
    }
}
