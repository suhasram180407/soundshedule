package com.soundschedule

import com.soundschedule.data.DefaultCollegeSchedule
import com.soundschedule.data.ScheduleDataState
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.ScheduleStorage
import com.soundschedule.data.SoundMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

class ScheduleDataLayerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testStorageFile: File

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        testStorageFile = tempFolder.newFile("test_schedules.json")
    }

    @Test
    fun scheduleEvent_formattedTimeAndDaysSummary_areAccurate() {
        val event = ScheduleEvent(
            id = 1L,
            startHour = 8,
            startMinute = 50,
            endHour = 11,
            endMinute = 0,
            mode = SoundMode.VIBRATE,
            daysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            label = "College Start"
        )

        assertEquals("08:50 AM - 11:00 AM", event.formattedTime)
        assertEquals("Mon - Fri", event.daysSummary)
        assertEquals(SoundMode.VIBRATE, event.mode)
    }

    @Test
    fun scheduleEvent_jsonSerialization_roundtripsCorrectly() {
        val original = ScheduleEvent(
            id = 42L,
            startHour = 13,
            startMinute = 30,
            endHour = 16,
            endMinute = 30,
            mode = SoundMode.NORMAL,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            label = "Lab Session"
        )

        val json = Json { prettyPrint = true }
        val encoded = json.encodeToString(ScheduleEvent.serializer(), original)
        val decoded = json.decodeFromString(ScheduleEvent.serializer(), encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.startHour, decoded.startHour)
        assertEquals(original.startMinute, decoded.startMinute)
        assertEquals(original.endHour, decoded.endHour)
        assertEquals(original.endMinute, decoded.endMinute)
        assertEquals(original.mode, decoded.mode)
        assertEquals(original.daysOfWeek, decoded.daysOfWeek)
        assertEquals(original.label, decoded.label)
    }

    @Test
    fun legacyData_safelyMigratesPointInTimeAndSilentEnum() {
        // Legacy JSON simulating pre-Phase-8 storage with point-in-time and SILENT enum
        val legacyJson = """
        {
            "id": 99,
            "hour": 8,
            "minute": 50,
            "mode": "SILENT",
            "enabled": true,
            "daysOfWeek": ["MONDAY", "TUESDAY"],
            "label": "Old Point Schedule",
            "createdAt": 1700000000000,
            "updatedAt": 1700000000000
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val migrated = json.decodeFromString(ScheduleEvent.serializer(), legacyJson)

        assertEquals(99L, migrated.id)
        assertEquals(8, migrated.startHour)
        assertEquals(50, migrated.startMinute)
        assertEquals(9, migrated.endHour)
        assertEquals(50, migrated.endMinute)
        // SILENT mapped safely to MUTE (separate from DND)
        assertEquals(SoundMode.MUTE, migrated.mode)
        assertEquals("08:50 AM - 09:50 AM", migrated.formattedTime)
    }

    @Test
    fun repository_initializesWithDefaultCollegeSchedule() = runTest {
        val storage = createTestStorage()
        val repository = ScheduleRepositoryImpl(storage)

        val schedules = repository.getAllSchedules()
        assertEquals(4, schedules.size)
        assertTrue(repository.isMasterEnabled.value)

        // Check default college schedule session intervals
        assertEquals("08:00 AM - 11:00 AM", schedules[0].formattedTime)
        assertEquals(SoundMode.VIBRATE, schedules[0].mode)
        assertEquals("Morning", schedules[0].label)

        assertEquals("11:05 AM - 12:45 PM", schedules[1].formattedTime)
        assertEquals(SoundMode.VIBRATE, schedules[1].mode)
        assertEquals("Late Morning", schedules[1].label)

        assertEquals("02:00 PM - 03:50 PM", schedules[2].formattedTime)
        assertEquals(SoundMode.VIBRATE, schedules[2].mode)
        assertEquals("Afternoon", schedules[2].label)

        assertEquals("05:00 PM - 06:00 PM", schedules[3].formattedTime)
        assertEquals(SoundMode.MUTE, schedules[3].mode)
        assertEquals("Evening", schedules[3].label)
    }

    @Test
    fun repository_crudOperations_workCorrectly() = runTest {
        val storage = createTestStorage()
        val repository = ScheduleRepositoryImpl(storage)

        // 1. Create
        val newEvent = ScheduleEvent(
            startHour = 18,
            startMinute = 0,
            endHour = 20,
            endMinute = 0,
            mode = SoundMode.MUTE,
            daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            label = "Weekend Study"
        )
        val createdId = repository.saveSchedule(newEvent)
        assertTrue(createdId > 0L)

        val retrieved = repository.getScheduleById(createdId)
        assertNotNull(retrieved)
        assertEquals(18, retrieved?.startHour)
        assertEquals("06:00 PM - 08:00 PM", retrieved?.formattedTime)
        assertEquals(SoundMode.MUTE, retrieved?.mode)
        assertEquals("Weekend Study", retrieved?.label)

        // 2. Update
        val updated = retrieved!!.copy(label = "Updated Study Time", startHour = 19, endHour = 21)
        repository.saveSchedule(updated)

        val afterUpdate = repository.getScheduleById(createdId)
        assertEquals(19, afterUpdate?.startHour)
        assertEquals("07:00 PM - 09:00 PM", afterUpdate?.formattedTime)
        assertEquals("Updated Study Time", afterUpdate?.label)

        // 3. Toggle Enabled
        repository.setScheduleEnabled(createdId, false)
        val afterToggle = repository.getScheduleById(createdId)
        assertFalse(afterToggle?.enabled ?: true)

        // 4. Delete
        val deleted = repository.deleteSchedule(createdId)
        assertTrue(deleted)
        assertNull(repository.getScheduleById(createdId))
    }

    @Test
    fun repository_conflictDetection_flagsOverlappingIntervals() = runTest {
        val storage = createTestStorage()
        val repository = ScheduleRepositoryImpl(storage)

        // Default has 08:50 AM - 11:00 AM on weekdays.
        // Add overlapping interval 10:00 AM - 10:45 AM with MUTE mode (strictly overlaps with 08:50-11:00)
        val conflictingEvent = ScheduleEvent(
            startHour = 10,
            startMinute = 0,
            endHour = 10,
            endMinute = 45,
            mode = SoundMode.MUTE,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            label = "Conflicting Event"
        )

        val candidateConflicts = repository.checkConflicts(conflictingEvent)
        assertEquals(1, candidateConflicts.size)
        assertTrue(candidateConflicts[0].description.contains("Sessions overlap"))

        repository.saveSchedule(conflictingEvent)
        val activeConflicts = repository.conflicts.value
        assertTrue(activeConflicts.isNotEmpty())
    }

    @Test
    fun repository_nonOverlappingDays_doNotConflict() = runTest {
        val storage = createTestStorage()
        val repository = ScheduleRepositoryImpl(storage)

        // Event at 08:50 AM on Sunday only (default schedule is Mon-Fri)
        val weekendEvent = ScheduleEvent(
            startHour = 8,
            startMinute = 50,
            endHour = 11,
            endMinute = 0,
            mode = SoundMode.NORMAL,
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            label = "Sunday Service"
        )

        val candidateConflicts = repository.checkConflicts(weekendEvent)
        assertTrue(candidateConflicts.isEmpty())
    }

    @Test
    fun repository_persistenceSurvivesReload() = runTest {
        val storage = createTestStorage()
        val repository1 = ScheduleRepositoryImpl(storage)

        val custom = ScheduleEvent(
            startHour = 22,
            startMinute = 15,
            endHour = 23,
            endMinute = 15,
            mode = SoundMode.MUTE,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            label = "Bedtime"
        )
        val customId = repository1.saveSchedule(custom)
        repository1.setMasterEnabled(false)

        // Simulate app restart with a fresh repository instance reading from same storage
        val repository2 = ScheduleRepositoryImpl(storage)
        assertFalse(repository2.isMasterEnabled.value)

        val loaded = repository2.getScheduleById(customId)
        assertNotNull(loaded)
        assertEquals("10:15 PM - 11:15 PM", loaded?.formattedTime)
        assertEquals(SoundMode.MUTE, loaded?.mode)
        assertEquals("Bedtime", loaded?.label)
    }

    private fun createTestStorage(): ScheduleStorage {
        return ScheduleStorage(testStorageFile)
    }
}
