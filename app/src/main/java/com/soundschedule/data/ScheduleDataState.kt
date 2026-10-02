package com.soundschedule.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDataState(
    val isMasterEnabled: Boolean = true,
    val events: List<ScheduleEvent> = emptyList(),
    val nextEventId: Long = 1L
)
