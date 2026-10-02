package com.soundschedule

import android.media.AudioManager
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.audio.RingerModeResult
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.ScheduleStorage
import com.soundschedule.data.SoundMode
import com.soundschedule.scheduler.AlarmReceiver
import com.soundschedule.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmReceiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val mondayDate = LocalDate.of(2026, 10, 5)

    private lateinit var testFile: File
    private lateinit var repository: ScheduleRepositoryImpl
    private lateinit var fakeAudio: FakeSystemAudioAdapter
    private lateinit var ringerManager: RingerModeManager
    private lateinit var fakeAlarmAdapter: FakeAlarmSystemAdapter
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        testFile = tempFolder.newFile("alarm_receiver_test.json")
        val storage = ScheduleStorage(testFile, ioDispatcher = Dispatchers.Unconfined)
        repository = ScheduleRepositoryImpl(storage)

        fakeAudio = FakeSystemAudioAdapter()
        fakeAudio.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAudio.hasDndPermission = true
        ringerManager = RingerModeManager(fakeAudio)

        fakeAlarmAdapter = FakeAlarmSystemAdapter()
        scheduler = AlarmScheduler(fakeAlarmAdapter)
    }

    private fun saveEvent(
        id: Long = 0L,
        startHour: Int,
        startMinute: Int,
        endHour: Int = (startHour + 1) % 24,
        endMinute: Int = startMinute,
        mode: SoundMode = SoundMode.VIBRATE,
        enabled: Boolean = true,
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    ): Long = runBlocking {
        repository.saveSchedule(
            ScheduleEvent(
                id = id,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                mode = mode,
                enabled = enabled,
                daysOfWeek = days,
                label = "Test"
            )
        )
    }

    @Test
    fun handleAlarmExecution_startSession_changesRingerAndReschedulesStart() {
        val eventId = saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE)
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 2), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertTrue(result is AlarmReceiver.ExecutionResult.Executed)
        val executed = result as AlarmReceiver.ExecutionResult.Executed
        assertEquals(SoundMode.VIBRATE, executed.actualSystemMode)
        assertTrue(executed.ringerResult is RingerModeResult.Success)

        // Verify hardware adapter mode was changed
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, fakeAudio.currentMode)

        // Verify next start occurrence was scheduled (Tuesday 08:50)
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(eventId))
        val nextTrigger = fakeAlarmAdapter.scheduledStartAlarms[eventId]
        val expectedTuesday = ZonedDateTime.of(mondayDate.plusDays(1), LocalTime.of(8, 50), zone).toInstant().toEpochMilli()
        assertEquals(expectedTuesday, nextTrigger)
    }

    @Test
    fun handleAlarmExecution_endSession_restoresNormalWhenNoOverlappingSession() {
        val eventId = saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE)
        // Put system in VIBRATE first
        fakeAudio.currentMode = AudioManager.RINGER_MODE_VIBRATE

        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(11, 0, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = false,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertTrue(result is AlarmReceiver.ExecutionResult.Executed)
        val executed = result as AlarmReceiver.ExecutionResult.Executed
        assertEquals(SoundMode.NORMAL, executed.actualSystemMode)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAudio.currentMode)

        // Verify next end occurrence was scheduled (Tuesday 11:00)
        assertTrue(fakeAlarmAdapter.scheduledEndAlarms.containsKey(eventId))
    }

    @Test
    fun handleAlarmExecution_masterOff_ignoresAlarmWithoutChangingAudio() {
        val eventId = saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE)
        runBlocking { repository.setMasterEnabled(false) }

        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertEquals(AlarmReceiver.ExecutionResult.MasterOff, result)
        // Verify audio state remained untouched (NORMAL)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAudio.currentMode)
        // No new alarm scheduled
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.isEmpty())
    }

    @Test
    fun handleAlarmExecution_staleAlarm_deletedScheduleIgnored() {
        val nonExistentId = 9999L
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = nonExistentId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertEquals(AlarmReceiver.ExecutionResult.ScheduleNotFound, result)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAudio.currentMode)
    }

    @Test
    fun handleAlarmExecution_staleAlarm_disabledScheduleIgnored() {
        val eventId = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE, enabled = false)
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertEquals(AlarmReceiver.ExecutionResult.ScheduleDisabled, result)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAudio.currentMode)
    }

    @Test
    fun handleAlarmExecution_outsideWindow_staleAlarmIgnoredAndRescheduled() {
        val eventId = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE)
        // Stale intent triggered 40 minutes late
        val lateTriggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(9, 30, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = lateTriggerTime
        )

        assertTrue(result is AlarmReceiver.ExecutionResult.OutsideToleranceWindow)
        // Sound was NOT changed to VIBRATE
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAudio.currentMode)
        // Next occurrence was scheduled so future triggers still work
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(eventId))
    }

    @Test
    fun handleAlarmExecution_missingDndPermission_returnsPermissionRequiredForDnd() {
        fakeAudio.hasDndPermission = false
        val eventId = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.DND)
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertTrue(result is AlarmReceiver.ExecutionResult.Executed)
        val executed = result as AlarmReceiver.ExecutionResult.Executed
        assertTrue(executed.ringerResult is RingerModeResult.PermissionRequired)
        // Next occurrence is still scheduled
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(eventId))
    }

    @Test
    fun handleAlarmExecution_muteDoesNotRequireDndPermission() {
        fakeAudio.hasDndPermission = false
        val eventId = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.MUTE)
        val triggerTime = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50, 0), zone)

        val result = AlarmReceiver.handleAlarmExecution(
            eventId = eventId,
            isStart = true,
            repository = repository,
            ringerManager = ringerManager,
            scheduler = scheduler,
            currentTime = triggerTime
        )

        assertTrue(result is AlarmReceiver.ExecutionResult.Executed)
        val executed = result as AlarmReceiver.ExecutionResult.Executed
        assertTrue(executed.ringerResult is RingerModeResult.Success)
        assertEquals(SoundMode.MUTE, executed.actualSystemMode)
    }
}
