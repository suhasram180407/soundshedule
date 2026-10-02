package com.soundschedule.data

import java.time.DayOfWeek

object DefaultCollegeSchedule {

    val Weekdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    fun createDefaultEvents(): List<ScheduleEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            ScheduleEvent(
                id = 1L,
                startHour = 8,
                startMinute = 0,
                endHour = 11,
                endMinute = 0,
                mode = SoundMode.VIBRATE,
                enabled = true,
                daysOfWeek = Weekdays,
                label = "Morning",
                createdAt = now,
                updatedAt = now
            ),
            ScheduleEvent(
                id = 2L,
                startHour = 11,
                startMinute = 5,
                endHour = 12,
                endMinute = 45,
                mode = SoundMode.VIBRATE,
                enabled = true,
                daysOfWeek = Weekdays,
                label = "Late Morning",
                createdAt = now + 1,
                updatedAt = now + 1
            ),
            ScheduleEvent(
                id = 3L,
                startHour = 14,
                startMinute = 0,
                endHour = 15,
                endMinute = 50,
                mode = SoundMode.VIBRATE,
                enabled = true,
                daysOfWeek = Weekdays,
                label = "Afternoon",
                createdAt = now + 2,
                updatedAt = now + 2
            ),
            ScheduleEvent(
                id = 4L,
                startHour = 17,
                startMinute = 0,
                endHour = 18,
                endMinute = 0,
                mode = SoundMode.MUTE,
                enabled = true,
                daysOfWeek = Weekdays,
                label = "Evening",
                createdAt = now + 3,
                updatedAt = now + 3
            )
        )
    }
}
