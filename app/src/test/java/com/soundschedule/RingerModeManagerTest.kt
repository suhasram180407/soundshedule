package com.soundschedule

import android.app.NotificationManager
import android.media.AudioManager
import com.soundschedule.audio.RingerModeManager
import com.soundschedule.audio.RingerModeResult
import com.soundschedule.audio.SystemAudioAdapter
import com.soundschedule.data.SoundMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RingerModeManagerTest {

    private lateinit var fakeAdapter: FakeSystemAudioAdapter
    private lateinit var manager: RingerModeManager

    @Before
    fun setUp() {
        com.soundschedule.utils.AppLog.isTestEnvironment = true
        fakeAdapter = FakeSystemAudioAdapter()
        manager = RingerModeManager(fakeAdapter)
    }

    @Test
    fun mapping_systemModeToSoundMode_convertsAccurately() {
        fakeAdapter.hasDndPermission = true
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        assertEquals(SoundMode.NORMAL, manager.getCurrentMode())

        fakeAdapter.currentMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(SoundMode.VIBRATE, manager.getCurrentMode())

        fakeAdapter.currentMode = AudioManager.RINGER_MODE_SILENT
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        assertEquals(SoundMode.MUTE, manager.getCurrentMode())

        // DND active when interruption filter is PRIORITY and ringer is normal/vibrate
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        assertEquals(SoundMode.DND, manager.getCurrentMode())

        // MUTE takes precedence if phone is in SILENT ringer mode even if zen mode is PRIORITY
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_SILENT
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        assertEquals(SoundMode.MUTE, manager.getCurrentMode())
    }

    @Test
    fun setRingerMode_toNormal_succeedsWithoutDndPermission() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_VIBRATE
        fakeAdapter.hasDndPermission = false

        val result = manager.setRingerMode(SoundMode.NORMAL)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.NORMAL, (result as RingerModeResult.Success).newMode)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fakeAdapter.currentMode)
    }

    @Test
    fun setRingerMode_toVibrate_succeedsWithoutDndPermission() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.hasDndPermission = false

        val result = manager.setRingerMode(SoundMode.VIBRATE)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.VIBRATE, (result as RingerModeResult.Success).newMode)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, fakeAdapter.currentMode)
    }

    @Test
    fun setRingerMode_muteDoesNotRequireDndPermissionAndDoesNotTriggerDnd() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.hasDndPermission = false

        val result = manager.setRingerMode(SoundMode.MUTE)

        // MUTE must NOT require DND permission upfront and must succeed
        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.MUTE, (result as RingerModeResult.Success).newMode)
        assertEquals(AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
        // Interruption filter must remain ALL, NOT Priority/Alarms
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, fakeAdapter.testInterruptionFilter)
    }

    @Test
    fun setRingerMode_dndRequiresPermission_returnsPermissionRequiredIfMissing() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.hasDndPermission = false

        val result = manager.setRingerMode(SoundMode.DND)

        assertTrue(result is RingerModeResult.PermissionRequired)
        val perm = result as RingerModeResult.PermissionRequired
        assertEquals(SoundMode.DND, perm.targetMode)
        assertTrue(perm.message.contains("Notification Policy Access"))
    }

    @Test
    fun setRingerMode_dndWithPermission_activatesPriorityInterruptionFilterWithoutAlteringRinger() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_VIBRATE
        fakeAdapter.hasDndPermission = true

        val result = manager.setRingerMode(SoundMode.DND)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.DND, (result as RingerModeResult.Success).newMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, fakeAdapter.testInterruptionFilter)
        // Ringer mode MUST remain untouched (still VIBRATE)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, fakeAdapter.currentMode)
    }

    @Test
    fun setRingerMode_mutePreservesExistingDndStateWhenDndAlreadyActive() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        fakeAdapter.hasDndPermission = true

        val result = manager.setRingerMode(SoundMode.MUTE)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.MUTE, (result as RingerModeResult.Success).newMode)
        assertEquals(AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
        // Interruption filter MUST remain PRIORITY (DND preserved)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, fakeAdapter.testInterruptionFilter)
    }

    @Test
    fun setRingerMode_muteDoesNotActivateVibrate() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL

        val result = manager.setRingerMode(SoundMode.MUTE)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
        assertFalse(fakeAdapter.currentMode == AudioManager.RINGER_MODE_VIBRATE)
    }

    @Test
    fun setRingerMode_muteDoesNotTriggerDnd() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

        val result = manager.setRingerMode(SoundMode.MUTE)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
        assertFalse("DND must not be active when MUTE is engaged", manager.dndManager.isDndActive())
        assertEquals(SoundMode.MUTE, manager.getCurrentMode())
    }

    @Test
    fun setRingerMode_dndDoesNotAlterRingerMode() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_SILENT
        fakeAdapter.hasDndPermission = true

        val result = manager.setRingerMode(SoundMode.DND)

        assertTrue(result is RingerModeResult.Success)
        assertEquals(SoundMode.DND, (result as RingerModeResult.Success).newMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, fakeAdapter.testInterruptionFilter)
        // Ringer mode must remain untouched (still SILENT)
        assertEquals(AudioManager.RINGER_MODE_SILENT, fakeAdapter.currentMode)
    }

    @Test
    fun setRingerMode_unexpectedExceptionThrown_returnsFailed() {
        fakeAdapter.currentMode = AudioManager.RINGER_MODE_NORMAL
        fakeAdapter.throwGenericException = true

        val result = manager.setRingerMode(SoundMode.VIBRATE)

        assertTrue(result is RingerModeResult.Failed)
        val fail = result as RingerModeResult.Failed
        assertTrue(fail.reason.contains("Audio hardware error"))
    }
}

open class FakeSystemAudioAdapter : SystemAudioAdapter {
    var currentMode: Int = AudioManager.RINGER_MODE_NORMAL
    var testInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    var hasDndPermission: Boolean = false
    var throwSecurityException: Boolean = false
    var throwGenericException: Boolean = false
    var simulateAospZenModeHelper: Boolean = false

    override fun getRingerMode(): Int = currentMode

    override fun setRingerMode(mode: Int) {
        if (throwSecurityException) {
            throw SecurityException("Not allowed by NotificationPolicy")
        }
        if (throwGenericException) {
            throw RuntimeException("Audio hardware error")
        }
        currentMode = mode
        if (simulateAospZenModeHelper && mode == AudioManager.RINGER_MODE_SILENT) {
            testInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
    }

    override fun isNotificationPolicyAccessGranted(): Boolean = hasDndPermission

    override fun setInterruptionFilter(filter: Int) {
        if (!hasDndPermission && filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            throw SecurityException("Notification policy access required")
        }
        testInterruptionFilter = filter
    }

    override fun getInterruptionFilter(): Int = testInterruptionFilter
}
