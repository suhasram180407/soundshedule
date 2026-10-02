package com.soundschedule.data

import java.time.DayOfWeek

data class ScheduleConflict(
    val eventA: ScheduleEvent,
    val eventB: ScheduleEvent,
    val overlappingDays: Set<DayOfWeek>,
    val description: String
)
