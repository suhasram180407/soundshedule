package com.soundschedule

import android.media.AudioManager
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.ScheduleStorage
import com.soundschedule.data.SoundMode
import com.soundschedule.widget.SoundScheduleWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class SoundScheduleWidgetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val mondayDate = LocalDate.of(2026, 10, 5)

    private lateinit var testFile: File
    private lateinit var repository: ScheduleRepositoryImpl
    private lateinit var fakeAudio: FakeSystemAudioAdapter
    private lateinit var ringerManager: RingerModeManager

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        testFile = tempFolder.newFile("widget_test_schedules.json")
        val storage = ScheduleStorage(testFile, ioDispatcher = Dispatchers.Unconfined)
        repository = ScheduleRepositoryImpl(storage)

        fakeAudio = FakeSystemAudioAdapter()
        fakeAudio.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAudio.hasDndPermission = true
        ringerManager = RingerModeManager(fakeAudio)
    }

    private fun saveEvent(
        id: Long = 0L,
        startHour: Int,
        startMinute: Int,
        endHour: Int = (startHour + 1) % 24,
        endMinute: Int = startMinute,
        mode: SoundMode = SoundMode.VIBRATE,
        enabled: Boolean = true,
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        label: String = "Test"
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
                label = label
            )
        )
    }

    @Test
    fun buildWidgetModel_caseC_onlyNextExists() {
        val all = repository.getAllSchedulesSync()
        runBlocking {
            all.forEach { repository.deleteSchedule(it.id) }
        }
        // Monday 08:00 AM reference time (schedule is 08:50 - 11:00 AM)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE, label = "College Start")

        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertTrue(model.isMasterEnabled)
        assertFalse(model.hasCurrent)
        assertTrue(model.hasNext)
        assertEquals("VIBRATE", model.nextTitle)
        assertEquals("08:50 – 11:00", model.nextTimeRange)
        assertEquals("College Start", model.nextLabel)
    }

    @Test
    fun buildWidgetModel_caseA_bothCurrentAndNextExist() {
        val all = repository.getAllSchedulesSync()
        runBlocking {
            all.forEach { repository.deleteSchedule(it.id) }
        }
        // Current: 08:50 - 11:00 Vibrate
        saveEvent(id = 1L, startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE, label = "Class")
        // Next: 11:30 - 12:30 Normal/Volume
        saveEvent(id = 2L, startHour = 11, startMinute = 30, endHour = 12, endMinute = 30, mode = SoundMode.NORMAL, label = "Lunch")

        // Monday 09:30 AM reference time (inside session 1, before session 2)
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(9, 30), zone)
        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertTrue(model.isMasterEnabled)
        assertTrue(model.hasCurrent)
        assertEquals("VIBRATE", model.currentTitle)
        assertEquals("08:50 – 11:00", model.currentTimeRange)
        assertEquals("Class", model.currentLabel)

        assertTrue(model.hasNext)
        assertEquals("VOLUME", model.nextTitle)
        assertEquals("11:30 – 12:30", model.nextTimeRange)
        assertEquals("Lunch", model.nextLabel)
    }

    @Test
    fun buildWidgetModel_caseB_onlyCurrentExists() {
        val all = repository.getAllSchedulesSync()
        runBlocking {
            all.forEach { repository.deleteSchedule(it.id) }
        }
        // Single session: 08:50 - 11:00 Vibrate, only on Mondays
        saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE, label = "Solo Session", days = setOf(DayOfWeek.MONDAY))

        // Monday 09:30 AM reference time
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(9, 30), zone)
        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertTrue(model.isMasterEnabled)
        assertTrue(model.hasCurrent)
        assertEquals("VIBRATE", model.currentTitle)
        assertEquals("08:50 – 11:00", model.currentTimeRange)
        assertEquals("Solo Session", model.currentLabel)

        // The only occurrence is currently active, so next is next week Monday
        assertTrue(model.hasNext) // Next week occurrence
    }

    @Test
    fun buildWidgetModel_caseD_noSchedulesExist() {
        val all = repository.getAllSchedulesSync()
        runBlocking {
            all.forEach { repository.deleteSchedule(it.id) }
        }

        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertTrue(model.isMasterEnabled)
        assertFalse(model.hasCurrent)
        assertFalse(model.hasNext)
        assertEquals("No active schedule", model.currentTitle)
        assertNull(model.currentTimeRange)
        assertEquals("No upcoming schedule", model.nextTitle)
        assertNull(model.nextTimeRange)
    }

    @Test
    fun buildWidgetModel_caseE_masterOff() {
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)
        saveEvent(startHour = 8, startMinute = 50, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE)
        runBlocking { repository.setMasterEnabled(false) }

        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertFalse(model.isMasterEnabled)
    }

    @Test
    fun buildWidgetModel_currentSoundMode_reflectsAllFourModes() {
        val now = ZonedDateTime.of(mondayDate, LocalTime.of(8, 0), zone)

        // 1. VOLUME / NORMAL
        fakeAudio.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAudio.testInterruptionFilter = android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        var model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)
        assertEquals(SoundMode.NORMAL, model.currentMode)

        // 2. VIBRATE
        fakeAudio.currentMode = AudioManager.RINGER_MODE_VIBRATE
        model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)
        assertEquals(SoundMode.VIBRATE, model.currentMode)

        // 3. MUTE
        fakeAudio.currentMode = AudioManager.RINGER_MODE_SILENT
        model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)
        assertEquals(SoundMode.MUTE, model.currentMode)

        // 4. DND (priority filter active with normal ringer)
        fakeAudio.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAudio.testInterruptionFilter = android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
        model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)
        assertEquals(SoundMode.DND, model.currentMode)
    }

    @Test
    fun buildWidgetModel_compact_whenCurrentExists_prioritizesCurrent() {
        val all = repository.getAllSchedulesSync()
        runBlocking { all.forEach { repository.deleteSchedule(it.id) } }

        saveEvent(id = 1L, startHour = 8, startMinute = 0, endHour = 11, endMinute = 0, mode = SoundMode.VIBRATE, label = "Morning")
        saveEvent(id = 2L, startHour = 11, startMinute = 5, endHour = 12, endMinute = 45, mode = SoundMode.VIBRATE, label = "Late Morning")

        val now = ZonedDateTime.of(mondayDate, LocalTime.of(9, 0), zone)
        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertTrue(model.hasCurrent)
        assertTrue(model.hasNext)
        assertEquals("08:00 – 11:00", model.currentTimeRange)
        assertEquals("Morning", model.currentLabel)
        assertEquals("11:05 – 12:45", model.nextTimeRange)
        assertEquals("Late Morning", model.nextLabel)
    }

    @Test
    fun buildWidgetModel_compact_whenNoCurrent_fallsBackToNext() {
        val all = repository.getAllSchedulesSync()
        runBlocking { all.forEach { repository.deleteSchedule(it.id) } }

        saveEvent(id = 1L, startHour = 14, startMinute = 0, endHour = 15, endMinute = 50, mode = SoundMode.VIBRATE, label = "Afternoon")

        val now = ZonedDateTime.of(mondayDate, LocalTime.of(12, 0), zone)
        val model = SoundScheduleWidgetUpdater.buildWidgetModel(repository, ringerManager, now)

        assertFalse(model.hasCurrent)
        assertTrue(model.hasNext)
        assertEquals("14:00 – 15:50", model.nextTimeRange)
        assertEquals("Afternoon", model.nextLabel)
    }
}
