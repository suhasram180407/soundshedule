package com.soundschedule.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.audio.RingerModeResult
import com.soundschedule.data.ScheduleConflict
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepository
import com.soundschedule.data.SoundMode
import com.soundschedule.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class MainScheduleViewModel(
    private val repository: ScheduleRepository,
    private val ringerModeManager: RingerModeManager? = null,
    private val alarmScheduler: com.soundschedule.scheduler.AlarmScheduler? = null,
    private val onWidgetUpdateRequired: (() -> Unit)? = null
) : ViewModel() {

    val schedules: StateFlow<List<ScheduleEvent>> = repository.schedules
    val isMasterEnabled: StateFlow<Boolean> = repository.isMasterEnabled
    val conflicts: StateFlow<List<ScheduleConflict>> = repository.conflicts

    // Actual system sound state directly from AudioManager
    private val _currentSystemSoundMode = MutableStateFlow(
        ringerModeManager?.getCurrentMode() ?: SoundMode.NORMAL
    )
    val currentSystemSoundMode: StateFlow<SoundMode> = _currentSystemSoundMode.asStateFlow()
    val manualSoundMode: StateFlow<SoundMode> = currentSystemSoundMode

    // Manual sound control selection state (nullable to support double-tap deselect)
    private val _selectedManualMode = MutableStateFlow<SoundMode?>(null)
    val selectedManualMode: StateFlow<SoundMode?> = _selectedManualMode.asStateFlow()

    /**
     * Toggles manual sound mode:
     * - First tap on mode: selects and applies it.
     * - Second tap on same selected mode: deselects it and restores appropriate session or volume mode.
     */
    fun toggleManualMode(mode: SoundMode) {
        if (_selectedManualMode.value == mode) {
            // Deselect
            _selectedManualMode.value = null
            if (mode == SoundMode.DND && ringerModeManager != null) {
                ringerModeManager.disableDnd()
                _currentSystemSoundMode.value = ringerModeManager.getCurrentMode()
                onWidgetUpdateRequired?.invoke()
            } else {
                val active = activeSession.value
                val targetFallback = active?.mode ?: SoundMode.NORMAL
                requestRingerMode(targetFallback)
            }
        } else {
            // Select & apply
            _selectedManualMode.value = mode
            requestRingerMode(mode)
        }
    }

    fun setManualMode(mode: SoundMode) {
        toggleManualMode(mode)
    }

    // Notification Policy Access state
    private val _notificationPolicyAccessGranted = MutableStateFlow(
        ringerModeManager?.hasNotificationPolicyAccess() ?: false
    )
    val notificationPolicyAccessGranted: StateFlow<Boolean> = _notificationPolicyAccessGranted.asStateFlow()

    // Exact Alarm permission state
    private val _canScheduleExactAlarms = MutableStateFlow(
        alarmScheduler?.canScheduleExactAlarms() ?: true
    )
    val canScheduleExactAlarms: StateFlow<Boolean> = _canScheduleExactAlarms.asStateFlow()

    // Last operation result for UI feedback / permission prompt
    private val _lastRingerModeResult = MutableStateFlow<RingerModeResult?>(null)
    val lastRingerModeResult: StateFlow<RingerModeResult?> = _lastRingerModeResult.asStateFlow()

    // Filter schedules applicable to today
    val todaySchedules: StateFlow<List<ScheduleEvent>> = schedules.combine(isMasterEnabled) { list, masterOn ->
        if (!masterOn) return@combine emptyList<ScheduleEvent>()
        val today = LocalDate.now().dayOfWeek
        list.filter { it.enabled && today in it.daysOfWeek }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calculate the next upcoming event
    init {
        alarmScheduler?.syncAllAlarms(
            repository.getAllSchedulesSync(),
            repository.isMasterEnabledSync()
        )
    }

    val activeSession: StateFlow<ScheduleEvent?> = schedules.combine(isMasterEnabled) { list, masterOn ->
        if (!masterOn) return@combine null
        list.firstOrNull { TimeUtils.isSessionActive(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nextUpcomingEvent: StateFlow<ScheduleEvent?> = schedules.combine(isMasterEnabled) { list, masterOn ->
        if (!masterOn) return@combine null
        calculateNextEvent(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshSystemSoundState() {
        if (ringerModeManager != null) {
            val actual = ringerModeManager.getCurrentMode()
            val hasAccess = ringerModeManager.hasNotificationPolicyAccess()
            _currentSystemSoundMode.value = actual
            _notificationPolicyAccessGranted.value = hasAccess
        }
    }

    fun refreshExactAlarmPermission() {
        if (alarmScheduler != null) {
            val canSchedule = alarmScheduler.canScheduleExactAlarms()
            _canScheduleExactAlarms.value = canSchedule
            if (canSchedule) {
                alarmScheduler.syncAllAlarms(
                    repository.getAllSchedulesSync(),
                    repository.isMasterEnabledSync()
                )
            }
        }
    }

    fun openExactAlarmSettings(context: Context) {
        alarmScheduler?.openExactAlarmSettings(context)
    }

    fun requestRingerMode(targetMode: SoundMode) {
        if (ringerModeManager == null) {
            _currentSystemSoundMode.value = targetMode
            _lastRingerModeResult.value = RingerModeResult.Success(targetMode)
            return
        }

        val result = ringerModeManager.setRingerMode(targetMode)
        _lastRingerModeResult.value = result
        _currentSystemSoundMode.value = ringerModeManager.getCurrentMode()
        _notificationPolicyAccessGranted.value = ringerModeManager.hasNotificationPolicyAccess()
        onWidgetUpdateRequired?.invoke()
    }

    fun dismissRingerModeNotice() {
        _lastRingerModeResult.value = null
    }

    fun openDndSettings(context: Context) {
        ringerModeManager?.openNotificationPolicyAccessSettings(context)
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMasterEnabled(enabled)
            alarmScheduler?.syncAllAlarms(repository.getAllSchedulesSync(), enabled)
            onWidgetUpdateRequired?.invoke()
        }
    }

    suspend fun setMasterEnabledSuspend(enabled: Boolean): Boolean {
        val result = repository.setMasterEnabled(enabled)
        alarmScheduler?.syncAllAlarms(repository.getAllSchedulesSync(), enabled)
        onWidgetUpdateRequired?.invoke()
        return result
    }

    fun toggleScheduleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setScheduleEnabled(id, enabled)
            val updated = repository.getScheduleById(id)
            if (updated != null) {
                if (enabled && repository.isMasterEnabledSync()) {
                    alarmScheduler?.scheduleNextOccurrence(updated)
                } else {
                    alarmScheduler?.cancelAlarm(id)
                }
            }
            onWidgetUpdateRequired?.invoke()
        }
    }

    suspend fun toggleScheduleEnabledSuspend(id: Long, enabled: Boolean): Boolean {
        val result = repository.setScheduleEnabled(id, enabled)
        val updated = repository.getScheduleById(id)
        if (updated != null) {
            if (enabled && repository.isMasterEnabledSync()) {
                alarmScheduler?.scheduleNextOccurrence(updated)
            } else {
                alarmScheduler?.cancelAlarm(id)
            }
        }
        onWidgetUpdateRequired?.invoke()
        return result
    }

    fun saveSchedule(event: ScheduleEvent, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveSchedule(event)
            val saved = repository.getScheduleById(id)
            if (saved != null) {
                if (saved.enabled && repository.isMasterEnabledSync()) {
                    alarmScheduler?.scheduleNextOccurrence(saved)
                } else {
                    alarmScheduler?.cancelAlarm(id)
                }
            }
            onWidgetUpdateRequired?.invoke()
            onSaved(id)
        }
    }

    suspend fun saveScheduleSuspend(event: ScheduleEvent): Long {
        val id = repository.saveSchedule(event)
        val saved = repository.getScheduleById(id)
        if (saved != null) {
            if (saved.enabled && repository.isMasterEnabledSync()) {
                alarmScheduler?.scheduleNextOccurrence(saved)
            } else {
                alarmScheduler?.cancelAlarm(id)
            }
        }
        onWidgetUpdateRequired?.invoke()
        return id
    }

    fun deleteSchedule(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
            alarmScheduler?.cancelAlarm(id)
            onWidgetUpdateRequired?.invoke()
            onDeleted()
        }
    }

    suspend fun deleteScheduleSuspend(id: Long): Boolean {
        val result = repository.deleteSchedule(id)
        alarmScheduler?.cancelAlarm(id)
        onWidgetUpdateRequired?.invoke()
        return result
    }

    fun checkConflicts(candidate: ScheduleEvent): List<ScheduleConflict> {
        return repository.checkConflicts(candidate)
    }

    companion object {
        fun calculateNextEvent(events: List<ScheduleEvent>, nowTime: LocalTime = LocalTime.now(), nowDay: DayOfWeek = LocalDate.now().dayOfWeek): ScheduleEvent? {
            val enabledEvents = events.filter { it.enabled && it.daysOfWeek.isNotEmpty() }
            if (enabledEvents.isEmpty()) return null

            // 1. Search for upcoming event later today
            val todayUpcoming = enabledEvents
                .filter { nowDay in it.daysOfWeek && LocalTime.of(it.startHour, it.startMinute) > nowTime }
                .minByOrNull { LocalTime.of(it.startHour, it.startMinute) }

            if (todayUpcoming != null) return todayUpcoming

            // 2. Search next 6 days sequentially
            for (dayOffset in 1..7) {
                val targetDay = nowDay.plus(dayOffset.toLong())
                val dayEvents = enabledEvents
                    .filter { targetDay in it.daysOfWeek }
                    .minByOrNull { LocalTime.of(it.startHour, it.startMinute) }

                if (dayEvents != null) return dayEvents
            }

            return null
        }
    }
}

class MainScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val ringerModeManager: RingerModeManager? = null,
    private val alarmScheduler: com.soundschedule.scheduler.AlarmScheduler? = null,
    private val onWidgetUpdateRequired: (() -> Unit)? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainScheduleViewModel(repository, ringerModeManager, alarmScheduler, onWidgetUpdateRequired) as T
    }
}
