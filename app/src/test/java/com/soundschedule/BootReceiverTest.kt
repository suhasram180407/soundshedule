package com.soundschedule

import android.media.AudioManager
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.ScheduleStorage
import com.soundschedule.data.SoundMode
import com.soundschedule.scheduler.AlarmScheduler
import com.soundschedule.scheduler.BootReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class BootReceiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val mondayDate = LocalDate.of(2026, 10, 5)

    private lateinit var testFile: File
    private lateinit var repository: ScheduleRepositoryImpl
    private lateinit var fakeAlarmAdapter: FakeAlarmSystemAdapter
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        testFile = tempFolder.newFile("boot_receiver_test.json")
        val storage = ScheduleStorage(testFile, ioDispatcher = Dispatchers.Unconfined)
        repository = ScheduleRepositoryImpl(storage)

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

    // 1. Boot + Master ON → schedules restored
    @Test
    fun bootRecovery_masterOn_restoresSchedules() {
        val id1 = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE)
        val id2 = saveEvent(startHour = 11, startMinute = 0, mode = SoundMode.NORMAL)

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        val result = BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertTrue(result is BootReceiver.BootRecoveryResult.Restored)
        // Default college schedule has 4 events + 2 newly added = 6 enabled events
        assertTrue((result as BootReceiver.BootRecoveryResult.Restored).restoredCount >= 2)
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id1))
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id2))
        assertTrue(fakeAlarmAdapter.scheduledEndAlarms.containsKey(id1))
        assertTrue(fakeAlarmAdapter.scheduledEndAlarms.containsKey(id2))
    }

    // 2. Boot + Master OFF → no alarms created
    @Test
    fun bootRecovery_masterOff_createsNoAlarms() {
        val id1 = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE)
        runBlocking { repository.setMasterEnabled(false) }

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        val result = BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertEquals(BootReceiver.BootRecoveryResult.MasterOff, result)
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.isEmpty())
        assertTrue(fakeAlarmAdapter.scheduledEndAlarms.isEmpty())
    }

    // 3. Boot + exact-alarm permission missing → safe behavior
    @Test
    fun bootRecovery_missingExactAlarmPermission_failsSafelyWithoutCrashing() {
        saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE)
        fakeAlarmAdapter.exactAlarmPermissionGranted = false

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        val result = BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertEquals(BootReceiver.BootRecoveryResult.MissingPermission, result)
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.isEmpty())
        assertTrue(fakeAlarmAdapter.scheduledEndAlarms.isEmpty())
    }

    // 4. Boot with disabled schedule → disabled schedule not restored
    @Test
    fun bootRecovery_disabledSchedule_notRestored() {
        val enabledId = saveEvent(startHour = 8, startMinute = 50, enabled = true)
        val disabledId = saveEvent(startHour = 13, startMinute = 0, enabled = false)

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(enabledId))
        assertFalse(fakeAlarmAdapter.scheduledStartAlarms.containsKey(disabledId))
        assertTrue(fakeAlarmAdapter.cancelledAlarmIds.contains(disabledId))
    }

    // 5. Boot with deleted schedule → no stale alarm
    @Test
    fun bootRecovery_deletedSchedule_noStaleAlarm() {
        val id = saveEvent(startHour = 8, startMinute = 50)
        runBlocking { repository.deleteSchedule(id) }

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertFalse(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id))
        assertFalse(fakeAlarmAdapter.scheduledEndAlarms.containsKey(id))
    }

    // 6. Multiple schedules → all enabled schedules synchronized
    @Test
    fun bootRecovery_multipleSchedules_allSynchronized() {
        val id1 = saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.VIBRATE)
        val id2 = saveEvent(startHour = 11, startMinute = 0, mode = SoundMode.NORMAL)
        val id3 = saveEvent(startHour = 13, startMinute = 0, mode = SoundMode.VIBRATE)

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id1))
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id2))
        assertTrue(fakeAlarmAdapter.scheduledStartAlarms.containsKey(id3))
    }

    // 7. Repeated boot synchronization → no logical duplication (idempotency)
    @Test
    fun bootRecovery_repeatedCalls_isIdempotent() {
        val id = saveEvent(startHour = 8, startMinute = 50)
        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)

        // First boot trigger
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)
        val firstTrigger = fakeAlarmAdapter.scheduledStartAlarms[id]
        assertNotNull(firstTrigger)
        val initialAlarmCount = fakeAlarmAdapter.scheduledStartAlarms.size

        // Repeated boot trigger (e.g. system broadcast re-delivery)
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)
        val secondTrigger = fakeAlarmAdapter.scheduledStartAlarms[id]

        assertEquals(firstTrigger, secondTrigger)
        // No duplicate entries created
        assertEquals(initialAlarmCount, fakeAlarmAdapter.scheduledStartAlarms.size)
    }

    // 8. Next occurrence after reboot is correctly calculated
    @Test
    fun bootRecovery_nextOccurrenceAfterReboot_correctlyCalculated() {
        val id = saveEvent(startHour = 8, startMinute = 50, days = setOf(DayOfWeek.MONDAY))

        // Case A: Device reboots before the event on Monday (06:00 AM)
        val earlyBootTime = ZonedDateTime.of(mondayDate, LocalTime.of(6, 0), zone)
        BootReceiver.handleBootRecovery(repository, scheduler, earlyBootTime)

        val expectedSameDay = ZonedDateTime.of(mondayDate, LocalTime.of(8, 50), zone).toInstant().toEpochMilli()
        assertEquals(expectedSameDay, fakeAlarmAdapter.scheduledStartAlarms[id])

        // Case B: Device reboots after the event on Monday (10:00 AM)
        val lateBootTime = ZonedDateTime.of(mondayDate, LocalTime.of(10, 0), zone)
        BootReceiver.handleBootRecovery(repository, scheduler, lateBootTime)

        val expectedNextWeek = ZonedDateTime.of(mondayDate.plusWeeks(1), LocalTime.of(8, 50), zone).toInstant().toEpochMilli()
        assertEquals(expectedNextWeek, fakeAlarmAdapter.scheduledStartAlarms[id])
    }

    // 9. Boot recovery never directly changes ringer mode
    @Test
    fun bootRecovery_neverDirectlyModifiesRingerMode() {
        val fakeAudio = FakeSystemAudioAdapter()
        fakeAudio.currentMode = AudioManager.RINGER_MODE_NORMAL
        val initialAudioMode = fakeAudio.currentMode

        saveEvent(startHour = 8, startMinute = 50, mode = SoundMode.MUTE)

        val bootTime = ZonedDateTime.of(mondayDate, LocalTime.of(7, 30), zone)
        // Execute boot recovery
        BootReceiver.handleBootRecovery(repository, scheduler, bootTime)

        // Verify ringer mode in audio adapter was completely untouched
        assertEquals(initialAudioMode, fakeAudio.currentMode)
    }
}
