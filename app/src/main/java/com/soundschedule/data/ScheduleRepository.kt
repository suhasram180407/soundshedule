package com.soundschedule.data

import android.content.Context
import com.soundschedule.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface ScheduleRepository {
    val schedules: StateFlow<List<ScheduleEvent>>
    val isMasterEnabled: StateFlow<Boolean>
    val conflicts: StateFlow<List<ScheduleConflict>>

    suspend fun getAllSchedules(): List<ScheduleEvent>
    fun getAllSchedulesSync(): List<ScheduleEvent>
    suspend fun getScheduleById(id: Long): ScheduleEvent?
    suspend fun saveSchedule(event: ScheduleEvent): Long
    suspend fun deleteSchedule(id: Long): Boolean
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean): Boolean
    suspend fun setMasterEnabled(enabled: Boolean): Boolean
    fun isMasterEnabledSync(): Boolean
    suspend fun resetToDefaultSchedule()
    fun checkConflicts(candidate: ScheduleEvent): List<ScheduleConflict>
    fun findConflicts(events: List<ScheduleEvent>): List<ScheduleConflict>
}

class ScheduleRepositoryImpl(
    private val storage: ScheduleStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ScheduleRepository {

    private val tag = "ScheduleRepository"

    private val _schedules = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    override val schedules: StateFlow<List<ScheduleEvent>> = _schedules.asStateFlow()

    private val _isMasterEnabled = MutableStateFlow(true)
    override val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _conflicts = MutableStateFlow<List<ScheduleConflict>>(emptyList())
    override val conflicts: StateFlow<List<ScheduleConflict>> = _conflicts.asStateFlow()

    private var nextId: Long = 1L

    init {
        // Load initial state synchronously so flows are initialized immediately
        val initial = storage.loadStateSync()
        _schedules.value = initial.events.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
        _isMasterEnabled.value = initial.isMasterEnabled
        nextId = initial.nextEventId
        _conflicts.value = findConflicts(_schedules.value)
    }

    override suspend fun getAllSchedules(): List<ScheduleEvent> {
        return _schedules.value
    }

    override fun getAllSchedulesSync(): List<ScheduleEvent> {
        return _schedules.value
    }

    override suspend fun getScheduleById(id: Long): ScheduleEvent? {
        return _schedules.value.firstOrNull { it.id == id }
    }

    override suspend fun saveSchedule(event: ScheduleEvent): Long {
        val currentEvents = _schedules.value.toMutableList()
        val finalId: Long
        val now = System.currentTimeMillis()

        if (event.id <= 0L) {
            finalId = nextId++
            val newEvent = event.copy(id = finalId, createdAt = now, updatedAt = now)
            currentEvents.add(newEvent)
            AppLog.d(tag, "Created new schedule: id=$finalId, time=${newEvent.formattedTime}, mode=${newEvent.mode}")
        } else {
            finalId = event.id
            val index = currentEvents.indexOfFirst { it.id == finalId }
            val updatedEvent = event.copy(updatedAt = now)
            if (index >= 0) {
                currentEvents[index] = updatedEvent
                AppLog.d(tag, "Updated schedule: id=$finalId, time=${updatedEvent.formattedTime}, mode=${updatedEvent.mode}")
            } else {
                currentEvents.add(updatedEvent)
                AppLog.d(tag, "Added schedule with specified id: id=$finalId")
            }
        }

        val sorted = currentEvents.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
        val newState = ScheduleDataState(
            isMasterEnabled = _isMasterEnabled.value,
            events = sorted,
            nextEventId = maxOf(nextId, (sorted.maxOfOrNull { it.id } ?: 0L) + 1L)
        )

        storage.saveState(newState)
        _schedules.value = sorted
        _conflicts.value = findConflicts(sorted)
        return finalId
    }

    override suspend fun deleteSchedule(id: Long): Boolean {
        val currentEvents = _schedules.value.toMutableList()
        val removed = currentEvents.removeAll { it.id == id }
        if (removed) {
            val newState = ScheduleDataState(
                isMasterEnabled = _isMasterEnabled.value,
                events = currentEvents,
                nextEventId = nextId
            )
            storage.saveState(newState)
            _schedules.value = currentEvents
            _conflicts.value = findConflicts(currentEvents)
            AppLog.d(tag, "Deleted schedule: id=$id")
            return true
        }
        return false
    }

    override suspend fun setScheduleEnabled(id: Long, enabled: Boolean): Boolean {
        val currentEvents = _schedules.value.map {
            if (it.id == id) it.copy(enabled = enabled, updatedAt = System.currentTimeMillis()) else it
        }
        val newState = ScheduleDataState(
            isMasterEnabled = _isMasterEnabled.value,
            events = currentEvents,
            nextEventId = nextId
        )
        storage.saveState(newState)
        _schedules.value = currentEvents
        _conflicts.value = findConflicts(currentEvents)
        AppLog.d(tag, "Toggled schedule id=$id enabled=$enabled")
        return true
    }

    override suspend fun setMasterEnabled(enabled: Boolean): Boolean {
        val newState = ScheduleDataState(
            isMasterEnabled = enabled,
            events = _schedules.value,
            nextEventId = nextId
        )
        storage.saveState(newState)
        _isMasterEnabled.value = enabled
        AppLog.d(tag, "Master schedule enabled state changed: $enabled")
        return true
    }

    override fun isMasterEnabledSync(): Boolean {
        return _isMasterEnabled.value
    }

    override suspend fun resetToDefaultSchedule() {
        val defaultEvents = DefaultCollegeSchedule.createDefaultEvents()
        val newState = ScheduleDataState(
            isMasterEnabled = true,
            events = defaultEvents,
            nextEventId = (defaultEvents.maxOfOrNull { it.id } ?: 0L) + 1L
        )
        storage.saveState(newState)
        _schedules.value = defaultEvents
        _isMasterEnabled.value = true
        nextId = newState.nextEventId
        _conflicts.value = findConflicts(defaultEvents)
        AppLog.d(tag, "Reset schedules to default college schedule.")
    }

    private fun sessionsOverlap(a: ScheduleEvent, b: ScheduleEvent): Boolean {
        val aStart = a.startHour * 60 + a.startMinute
        val aEnd = if (a.endHour * 60 + a.endMinute <= aStart) (a.endHour * 60 + a.endMinute + 24 * 60) else (a.endHour * 60 + a.endMinute)
        val bStart = b.startHour * 60 + b.startMinute
        val bEnd = if (b.endHour * 60 + b.endMinute <= bStart) (b.endHour * 60 + b.endMinute + 24 * 60) else (b.endHour * 60 + b.endMinute)
        return maxOf(aStart, bStart) < minOf(aEnd, bEnd)
    }

    override fun checkConflicts(candidate: ScheduleEvent): List<ScheduleConflict> {
        val conflictsList = mutableListOf<ScheduleConflict>()
        for (other in _schedules.value) {
            if (other.id == candidate.id) continue
            if (!candidate.enabled || !other.enabled) continue
            val overlap = candidate.daysOfWeek.intersect(other.daysOfWeek)
            if (overlap.isNotEmpty() && sessionsOverlap(candidate, other)) {
                val desc = "Sessions overlap between ${candidate.formattedTime} (${candidate.mode.displayName}) and ${other.formattedTime} (${other.mode.displayName}) on ${overlap.joinToString { it.name }}."
                conflictsList.add(ScheduleConflict(candidate, other, overlap, desc))
            }
        }
        return conflictsList
    }

    override fun findConflicts(events: List<ScheduleEvent>): List<ScheduleConflict> {
        val conflictsList = mutableListOf<ScheduleConflict>()
        val active = events.filter { it.enabled }
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]
                val overlap = a.daysOfWeek.intersect(b.daysOfWeek)
                if (overlap.isNotEmpty() && sessionsOverlap(a, b)) {
                    val desc = "Sessions overlap between ${a.formattedTime} (${a.mode.displayName}) and ${b.formattedTime} (${b.mode.displayName}) on ${overlap.joinToString { it.name }}."
                    conflictsList.add(ScheduleConflict(a, b, overlap, desc))
                }
            }
        }
        return conflictsList
    }

    companion object {
        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepositoryImpl(
                    storage = ScheduleStorage(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}
