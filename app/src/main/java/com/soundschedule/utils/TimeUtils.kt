package com.soundschedule.utils

import com.soundschedule.data.ScheduleEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object TimeUtils {

    /**
     * Calculates the exact next start occurrence for a given [ScheduleEvent] starting strictly after [referenceZonedTime].
     * Uses device local time zone and handles arbitrary day-of-week sets, midnight crossing, and weekend skips.
     *
     * @return ZonedDateTime of next occurrence, or null if event has no active days.
     */
    fun calculateNextOccurrence(
        event: ScheduleEvent,
        referenceZonedTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): ZonedDateTime? {
        if (event.daysOfWeek.isEmpty()) return null

        val zone = referenceZonedTime.zone
        val refDate = referenceZonedTime.toLocalDate()
        val refTime = referenceZonedTime.toLocalTime()
        val eventTime = LocalTime.of(event.startHour, event.startMinute)

        // 1. Check if it can run later today (same day, strictly after reference time)
        val todayDayOfWeek = refDate.dayOfWeek
        if (todayDayOfWeek in event.daysOfWeek && eventTime.isAfter(refTime)) {
            return ZonedDateTime.of(refDate, eventTime, zone)
        }

        // 2. Search sequentially through the next 7 days
        for (dayOffset in 1L..7L) {
            val candidateDate = refDate.plusDays(dayOffset)
            if (candidateDate.dayOfWeek in event.daysOfWeek) {
                return ZonedDateTime.of(candidateDate, eventTime, zone)
            }
        }

        return null
    }

    /**
     * Calculates the exact next end occurrence for a given [ScheduleEvent] starting strictly after [referenceZonedTime].
     */
    fun calculateNextEndOccurrence(
        event: ScheduleEvent,
        referenceZonedTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): ZonedDateTime? {
        if (event.daysOfWeek.isEmpty()) return null

        val zone = referenceZonedTime.zone
        val refDate = referenceZonedTime.toLocalDate()
        val refTime = referenceZonedTime.toLocalTime()
        val endTime = LocalTime.of(event.endHour, event.endMinute)

        // If today is an active day
        val todayDayOfWeek = refDate.dayOfWeek
        if (todayDayOfWeek in event.daysOfWeek) {
            val isOvernight = event.endHour < event.startHour ||
                    (event.endHour == event.startHour && event.endMinute <= event.startMinute)
            if (isOvernight) {
                // If overnight, end time is tomorrow
                val tomorrow = refDate.plusDays(1)
                val candidate = ZonedDateTime.of(tomorrow, endTime, zone)
                if (candidate.isAfter(referenceZonedTime)) {
                    return candidate
                }
            } else if (endTime.isAfter(refTime)) {
                return ZonedDateTime.of(refDate, endTime, zone)
            }
        }

        // Check if yesterday was active and had an overnight session ending today
        val yesterday = refDate.minusDays(1)
        if (yesterday.dayOfWeek in event.daysOfWeek) {
            val isOvernight = event.endHour < event.startHour ||
                    (event.endHour == event.startHour && event.endMinute <= event.startMinute)
            if (isOvernight && endTime.isAfter(refTime)) {
                return ZonedDateTime.of(refDate, endTime, zone)
            }
        }

        // Search through the next 7 days
        for (dayOffset in 1L..7L) {
            val startDay = refDate.plusDays(dayOffset)
            if (startDay.dayOfWeek in event.daysOfWeek) {
                val isOvernight = event.endHour < event.startHour ||
                        (event.endHour == event.startHour && event.endMinute <= event.startMinute)
                val candidateDate = if (isOvernight) startDay.plusDays(1) else startDay
                return ZonedDateTime.of(candidateDate, endTime, zone)
            }
        }

        return null
    }

    /**
     * Returns true if the session is currently active at [referenceZonedTime].
     */
    fun isSessionActive(
        event: ScheduleEvent,
        referenceZonedTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled || event.daysOfWeek.isEmpty()) return false

        val refDate = referenceZonedTime.toLocalDate()
        val refTime = referenceZonedTime.toLocalTime()
        val refMinutes = refTime.hour * 60 + refTime.minute
        val startMinutes = event.startHour * 60 + event.startMinute
        val endMinutes = event.endHour * 60 + event.endMinute

        val today = refDate.dayOfWeek
        val yesterday = refDate.minusDays(1).dayOfWeek

        val isOvernight = endMinutes <= startMinutes

        if (!isOvernight) {
            if (today in event.daysOfWeek) {
                return refMinutes in startMinutes until endMinutes
            }
        } else {
            // Overnight: started yesterday evening OR started today evening
            if (yesterday in event.daysOfWeek && refMinutes < endMinutes) {
                return true
            }
            if (today in event.daysOfWeek && refMinutes >= startMinutes) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if the session has already completed for today at [referenceZonedTime].
     */
    fun isSessionCompletedToday(
        event: ScheduleEvent,
        referenceZonedTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled || event.daysOfWeek.isEmpty()) return false

        val refDate = referenceZonedTime.toLocalDate()
        val refTime = referenceZonedTime.toLocalTime()
        val refMinutes = refTime.hour * 60 + refTime.minute
        val startMinutes = event.startHour * 60 + event.startMinute
        val endMinutes = event.endHour * 60 + event.endMinute

        val today = refDate.dayOfWeek
        val isOvernight = endMinutes <= startMinutes

        if (!isOvernight) {
            if (today in event.daysOfWeek) {
                return refMinutes >= endMinutes
            }
        } else {
            val yesterday = refDate.minusDays(1).dayOfWeek
            if (yesterday in event.daysOfWeek && refMinutes >= endMinutes && refMinutes < startMinutes) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if the session is scheduled for today and has not started yet.
     */
    fun isSessionUpcomingToday(
        event: ScheduleEvent,
        referenceZonedTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        if (!event.enabled || event.daysOfWeek.isEmpty()) return false

        val refDate = referenceZonedTime.toLocalDate()
        val refTime = referenceZonedTime.toLocalTime()
        val refMinutes = refTime.hour * 60 + refTime.minute
        val startMinutes = event.startHour * 60 + event.startMinute

        val today = refDate.dayOfWeek
        if (today in event.daysOfWeek) {
            return refMinutes < startMinutes
        }
        return false
    }

    /**
     * Verifies whether [actualTime] is within a valid tolerance window ([toleranceMinutes])
     * of the scheduled [event] on the expected day.
     * Guards against executing obsolete or severely delayed alarms.
     */
    fun isWithinExecutionWindow(
        event: ScheduleEvent,
        actualTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        toleranceMinutes: Long = 15L,
        isStart: Boolean = true
    ): Boolean {
        if (event.daysOfWeek.isEmpty()) return false

        val actualDate = actualTime.toLocalDate()
        val targetHour = if (isStart) event.startHour else event.endHour
        val targetMinute = if (isStart) event.startMinute else event.endMinute

        // 1. Check if it matches today's scheduled time within tolerance
        if (actualDate.dayOfWeek in event.daysOfWeek) {
            val scheduledToday = ZonedDateTime.of(
                actualDate,
                LocalTime.of(targetHour, targetMinute),
                actualTime.zone
            )
            val diffMinutes = java.time.Duration.between(scheduledToday, actualTime).abs().toMinutes()
            if (diffMinutes <= toleranceMinutes) return true
        }

        // 2. Check if it crossed midnight (e.g. scheduled for 23:59 yesterday, triggered slightly after 00:00 today)
        val yesterday = actualDate.minusDays(1)
        if (yesterday.dayOfWeek in event.daysOfWeek) {
            val scheduledYesterday = ZonedDateTime.of(
                yesterday,
                LocalTime.of(targetHour, targetMinute),
                actualTime.zone
            )
            val diffMinutes = java.time.Duration.between(scheduledYesterday, actualTime).abs().toMinutes()
            if (diffMinutes <= toleranceMinutes) return true
        }

        return false
    }
}
