package com.soundschedule

import com.soundschedule.data.DefaultCollegeSchedule
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.ScheduleRepositoryImpl
import com.soundschedule.data.ScheduleStorage
import com.soundschedule.data.SoundMode
import com.soundschedule.ui.MainScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainScheduleViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testFile: File
    private lateinit var repository: ScheduleRepositoryImpl
    private lateinit var viewModel: MainScheduleViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        testFile = tempFolder.newFile("vm_test_schedules.json")
        val storage = ScheduleStorage(testFile, ioDispatcher = Dispatchers.Unconfined)
        repository = ScheduleRepositoryImpl(storage)
        viewModel = MainScheduleViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModel_initializesWithDefaultSchedules() = runTest {
        val schedules = viewModel.schedules.value
        assertEquals(4, schedules.size)
        assertTrue(viewModel.isMasterEnabled.value)
    }

    @Test
    fun viewModel_nextEventCalculation_findsUpcomingEventLaterToday() {
        val now = LocalTime.of(10, 0) // 10:00 AM on Monday
        val day = DayOfWeek.MONDAY
        val events = DefaultCollegeSchedule.createDefaultEvents()

        val next = MainScheduleViewModel.calculateNextEvent(events, nowTime = now, nowDay = day)
        assertNotNull(next)
        assertEquals("11:05 AM - 12:45 PM", next?.formattedTime)
        assertEquals(SoundMode.VIBRATE, next?.mode)
    }

    @Test
    fun viewModel_nextEventCalculation_wrapsToNextDayWhenTodayPast() {
        val now = LocalTime.of(19, 0) // 07:00 PM on Monday (all Monday events ended)
        val day = DayOfWeek.MONDAY
        val events = DefaultCollegeSchedule.createDefaultEvents()

        val next = MainScheduleViewModel.calculateNextEvent(events, nowTime = now, nowDay = day)
        assertNotNull(next)
        // Should find 08:00 AM - 11:00 AM on Tuesday
        assertEquals("08:00 AM - 11:00 AM", next?.formattedTime)
        assertEquals(SoundMode.VIBRATE, next?.mode)
    }

    @Test
    fun viewModel_nextEventCalculation_skipsWeekendsForWeekdaySchedule() {
        val now = LocalTime.of(19, 0) // 07:00 PM on Friday
        val day = DayOfWeek.FRIDAY
        val events = DefaultCollegeSchedule.createDefaultEvents()

        val next = MainScheduleViewModel.calculateNextEvent(events, nowTime = now, nowDay = day)
        assertNotNull(next)
        // Should find 08:00 AM - 11:00 AM on Monday
        assertEquals("08:00 AM - 11:00 AM", next?.formattedTime)
        assertEquals(SoundMode.VIBRATE, next?.mode)
    }

    @Test
    fun viewModel_masterSwitchToggle_reflectsInRepository() = runTest {
        viewModel.setMasterEnabledSuspend(false)
        assertFalse(viewModel.isMasterEnabled.value)
        assertFalse(repository.isMasterEnabled.value)
    }

    @Test
    fun viewModel_toggleScheduleEnabled_updatesState() = runTest {
        val targetId = viewModel.schedules.value.first().id
        viewModel.toggleScheduleEnabledSuspend(targetId, false)

        val updated = viewModel.schedules.value.first { it.id == targetId }
        assertFalse(updated.enabled)
    }

    @Test
    fun viewModel_saveNewSchedule_addsToList() = runTest {
        val newEvent = ScheduleEvent(
            startHour = 20,
            startMinute = 0,
            endHour = 22,
            endMinute = 0,
            mode = SoundMode.MUTE,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            label = "Evening Quiet"
        )

        val savedId = viewModel.saveScheduleSuspend(newEvent)
        assertTrue(savedId > 0L)
        val found = viewModel.schedules.value.find { it.id == savedId }
        assertNotNull(found)
        assertEquals("08:00 PM - 10:00 PM", found?.formattedTime)
        assertEquals(SoundMode.MUTE, found?.mode)
    }

    @Test
    fun viewModel_deleteSchedule_removesFromList() = runTest {
        val targetId = viewModel.schedules.value.first().id
        viewModel.deleteScheduleSuspend(targetId)

        val found = viewModel.schedules.value.find { it.id == targetId }
        assertNull(found)
        assertEquals(3, viewModel.schedules.value.size)
    }

    @Test
    fun viewModel_manualSoundMode_canBeUpdated() {
        assertEquals(SoundMode.NORMAL, viewModel.manualSoundMode.value)
        viewModel.setManualMode(SoundMode.VIBRATE)
        assertEquals(SoundMode.VIBRATE, viewModel.manualSoundMode.value)
        viewModel.setManualMode(SoundMode.MUTE)
        assertEquals(SoundMode.MUTE, viewModel.manualSoundMode.value)
    }

    @Test
    fun viewModel_requestRingerMode_updatesSystemSoundMode() {
        val fakeAdapter = FakeSystemAudioAdapter().apply {
            hasDndPermission = true
        }
        val ringerManager = com.soundschedule.audio.RingerModeManager(fakeAdapter)
        val vm = MainScheduleViewModel(repository, ringerManager)

        vm.requestRingerMode(SoundMode.VIBRATE)
        assertEquals(SoundMode.VIBRATE, vm.currentSystemSoundMode.value)
        assertTrue(vm.lastRingerModeResult.value is com.soundschedule.audio.RingerModeResult.Success)

        vm.dismissRingerModeNotice()
        assertNull(vm.lastRingerModeResult.value)
    }

    @Test
    fun viewModel_doubleTapManualMode_deselectsAndRestoresFallback() {
        val fakeAdapter = FakeSystemAudioAdapter().apply {
            hasDndPermission = true
        }
        val ringerManager = com.soundschedule.audio.RingerModeManager(fakeAdapter)
        val vm = MainScheduleViewModel(repository, ringerManager)

        // 1. First tap selects VIBRATE
        vm.toggleManualMode(SoundMode.VIBRATE)
        assertEquals(SoundMode.VIBRATE, vm.selectedManualMode.value)
        assertEquals(SoundMode.VIBRATE, vm.currentSystemSoundMode.value)

        // 2. Second tap on SAME mode deselects it
        vm.toggleManualMode(SoundMode.VIBRATE)
        assertNull(vm.selectedManualMode.value)
        // Restores fallback mode (NORMAL / VOLUME)
        assertEquals(SoundMode.NORMAL, vm.currentSystemSoundMode.value)
    }

    @Test
    fun viewModel_saveSchedule_preservesEnabledFalse() = runTest {
        val disabledEvent = ScheduleEvent(
            startHour = 14,
            startMinute = 0,
            endHour = 15,
            endMinute = 0,
            mode = SoundMode.MUTE,
            enabled = false,
            daysOfWeek = setOf(DayOfWeek.TUESDAY),
            label = "Disabled Alarm"
        )

        val id = viewModel.saveScheduleSuspend(disabledEvent)
        val saved = viewModel.schedules.value.find { it.id == id }
        assertNotNull(saved)
        assertFalse("Schedule saved with enabled=false must remain enabled=false", saved!!.enabled)
    }

    @Test
    fun viewModel_doubleTapDndMode_disablesDndAndPreservesRinger() {
        val fakeAdapter = FakeSystemAudioAdapter().apply {
            hasDndPermission = true
            currentMode = android.media.AudioManager.RINGER_MODE_VIBRATE
        }
        val ringerManager = com.soundschedule.audio.RingerModeManager(fakeAdapter)
        val vm = MainScheduleViewModel(repository, ringerManager)

        // Initial ringer is Vibrate
        assertEquals(SoundMode.VIBRATE, vm.currentSystemSoundMode.value)

        // 1. Turn on DND
        vm.toggleManualMode(SoundMode.DND)
        assertEquals(SoundMode.DND, vm.selectedManualMode.value)
        assertEquals(SoundMode.DND, vm.currentSystemSoundMode.value)
        // Hardware ringer is STILL VIBRATE
        assertEquals(android.media.AudioManager.RINGER_MODE_VIBRATE, fakeAdapter.currentMode)

        // 2. Turn off DND by tapping again
        vm.toggleManualMode(SoundMode.DND)
        assertNull(vm.selectedManualMode.value)
        // DND filter restored to ALL, so system sound mode returns to VIBRATE (not forced to NORMAL)
        assertEquals(SoundMode.VIBRATE, vm.currentSystemSoundMode.value)
        assertEquals(android.media.AudioManager.RINGER_MODE_VIBRATE, fakeAdapter.currentMode)
    }

    @Test
    fun viewModel_doubleTapMuteMode_deselectsAndRestoresFallbackWithoutActivatingDnd() {
        val fakeAdapter = FakeSystemAudioAdapter().apply {
            hasDndPermission = true
            currentMode = android.media.AudioManager.RINGER_MODE_NORMAL
            testInterruptionFilter = android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        }
        val ringerManager = com.soundschedule.audio.RingerModeManager(fakeAdapter)
        val vm = MainScheduleViewModel(repository, ringerManager)

        // 1. First tap selects MUTE
        vm.toggleManualMode(SoundMode.MUTE)
        assertEquals(SoundMode.MUTE, vm.selectedManualMode.value)
        assertEquals(SoundMode.MUTE, vm.currentSystemSoundMode.value)
        assertEquals(android.media.AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
        assertEquals(android.app.NotificationManager.INTERRUPTION_FILTER_ALL, fakeAdapter.testInterruptionFilter)

        // 2. Second tap deselects MUTE
        vm.toggleManualMode(SoundMode.MUTE)
        assertNull(vm.selectedManualMode.value)
        // Fallback restored to NORMAL
        assertEquals(SoundMode.NORMAL, vm.currentSystemSoundMode.value)
        assertEquals(android.media.AudioManager.RINGER_MODE_NORMAL, fakeAdapter.currentMode)
        // DND must NOT be activated
        assertEquals(android.app.NotificationManager.INTERRUPTION_FILTER_ALL, fakeAdapter.testInterruptionFilter)
    }
}

