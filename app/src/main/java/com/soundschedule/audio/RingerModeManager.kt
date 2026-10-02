package com.soundschedule.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.soundschedule.data.SoundMode
import com.soundschedule.utils.AppLog

/**
 * Hardware audio adapter decoupled for 100% deterministic unit testing.
 */
interface SystemAudioAdapter {
    fun getRingerMode(): Int
    fun setRingerMode(mode: Int)
    fun isNotificationPolicyAccessGranted(): Boolean
    fun getInterruptionFilter(): Int
    fun setInterruptionFilter(filter: Int)
}

/**
 * Production Android implementation using official Android system services.
 */
class AndroidSystemAudioAdapter(private val context: Context) : SystemAudioAdapter {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun getRingerMode(): Int = audioManager.ringerMode

    override fun setRingerMode(mode: Int) {
        audioManager.ringerMode = mode
    }

    override fun isNotificationPolicyAccessGranted(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    override fun getInterruptionFilter(): Int {
        return try {
            notificationManager.currentInterruptionFilter
        } catch (_: Exception) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    override fun setInterruptionFilter(filter: Int) {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            try {
                notificationManager.setInterruptionFilter(filter)
            } catch (e: Exception) {
                AppLog.e("SystemAudioAdapter", "Failed to set interruption filter: ${e.message}")
            }
        }
    }
}

/**
 * Primary manager for hardware audio and sound modes:
 * - VOLUME  -> AudioManager.RINGER_MODE_NORMAL
 * - VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
 * - MUTE    -> AudioManager.RINGER_MODE_SILENT (Silent mode, independent of DND)
 * - DND     -> NotificationManager INTERRUPTION_FILTER_PRIORITY (independent of ringer mode)
 */
class RingerModeManager(
    private val adapter: SystemAudioAdapter,
    val dndManager: DndManager = DndManager(adapter)
) {
    private val tag = "RingerModeManager"

    private fun ringerModeToString(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "SILENT"
        AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
        AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
        else -> "UNKNOWN($mode)"
    }

    fun getCurrentMode(): SoundMode {
        val ringer = adapter.getRingerMode()

        // 1. If ringer is SILENT, the phone is in Silent mode (MUTE)
        if (ringer == AudioManager.RINGER_MODE_SILENT) {
            return SoundMode.MUTE
        }

        // 2. If DND is actively filtering notifications and ringer is not silent, report DND
        if (dndManager.isDndActive()) {
            return SoundMode.DND
        }

        // 3. Otherwise inspect hardware ringer mode
        return when (ringer) {
            AudioManager.RINGER_MODE_NORMAL -> SoundMode.NORMAL
            AudioManager.RINGER_MODE_VIBRATE -> SoundMode.VIBRATE
            else -> SoundMode.NORMAL
        }
    }

    fun hasNotificationPolicyAccess(): Boolean {
        return dndManager.hasPermission()
    }

    fun canChangeRingerMode(targetMode: SoundMode): Boolean {
        if (targetMode == SoundMode.DND) {
            return hasNotificationPolicyAccess()
        }
        return true
    }

    fun disableDnd() {
        dndManager.disableDnd()
    }

    fun setRingerMode(targetMode: SoundMode): RingerModeResult {
        val prevRinger = adapter.getRingerMode()
        val prevDnd = dndManager.isDndActive()

        AppLog.d(tag, "Attempting to change sound mode to: ${targetMode.displayName}")

        // Pre-flight check for DND
        if (!canChangeRingerMode(targetMode)) {
            AppLog.w(tag, "Notification Policy Access is required to apply ${targetMode.displayName} mode.")
            return RingerModeResult.PermissionRequired(
                targetMode = targetMode,
                message = "Notification Policy Access (DND access) is required to activate ${targetMode.displayName} mode."
            )
        }

        val androidOp = when (targetMode) {
            SoundMode.NORMAL -> "AudioManager.setRingerMode(RINGER_MODE_NORMAL)"
            SoundMode.VIBRATE -> "AudioManager.setRingerMode(RINGER_MODE_VIBRATE)"
            SoundMode.MUTE -> "AudioManager.setRingerMode(RINGER_MODE_SILENT)"
            SoundMode.DND -> "NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)"
        }

        return try {
            when (targetMode) {
                SoundMode.NORMAL -> {
                    // Turn off DND if priority was set
                    if (dndManager.isDndActive()) {
                        dndManager.disableDnd()
                    }
                    adapter.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                }

                SoundMode.VIBRATE -> {
                    if (dndManager.isDndActive()) {
                        dndManager.disableDnd()
                    }
                    adapter.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
                }

                SoundMode.MUTE -> {
                    // MUTE: Activate Android Silent mode directly.
                    // Strictly conforms to Part 25:
                    // AudioManager.RINGER_MODE_SILENT ONLY.
                    // MUTE must NOT:
                    // - activate DND
                    // - call DndManager.enableDnd()
                    // - call setInterruptionFilter()
                    // - create/enable AutomaticZenRule
                    // - change notification interruption filter
                    // - activate VIBRATE
                    adapter.setRingerMode(AudioManager.RINGER_MODE_SILENT)
                }

                SoundMode.DND -> {
                    // Explicitly activate Android Do Not Disturb ONLY.
                    // CRITICAL: DND must NEVER alter AudioManager.ringerMode!
                    dndManager.enableDnd()
                }
            }

            val actualRinger = adapter.getRingerMode()
            val dndAfter = dndManager.isDndActive()

            val isVerified = when (targetMode) {
                SoundMode.NORMAL -> actualRinger == AudioManager.RINGER_MODE_NORMAL
                SoundMode.VIBRATE -> actualRinger == AudioManager.RINGER_MODE_VIBRATE
                SoundMode.MUTE -> actualRinger == AudioManager.RINGER_MODE_SILENT
                SoundMode.DND -> dndAfter
            }

            // Structured logging conforming exactly to Part 9 specification
            if (targetMode == SoundMode.MUTE) {
                AppLog.i("SoundSchedule", """
                Requested = MUTE
                RingerBefore = ${ringerModeToString(prevRinger)}
                DndBefore = $prevDnd
                Applying = RINGER_MODE_SILENT
                RingerAfter = ${ringerModeToString(actualRinger)}
                DndAfter = $dndAfter
                """.trimIndent())

                if (actualRinger != AudioManager.RINGER_MODE_SILENT || dndAfter != prevDnd) {
                    AppLog.e("SoundSchedule", "ERROR:\nMUTE unexpectedly changed DND state")
                }
            } else if (targetMode == SoundMode.DND) {
                AppLog.i("SoundSchedule", """
                Requested = DND
                RingerBefore = ${ringerModeToString(prevRinger)}
                DndBefore = $prevDnd
                Applying = INTERRUPTION_FILTER_PRIORITY
                RingerAfter = ${ringerModeToString(actualRinger)}
                DndAfter = $dndAfter
                """.trimIndent())
            } else {
                AppLog.i("SoundSchedule", """
                Requested = ${targetMode.displayName}
                RingerBefore = ${ringerModeToString(prevRinger)}
                DndBefore = $prevDnd
                Applying = $androidOp
                RingerAfter = ${ringerModeToString(actualRinger)}
                DndAfter = $dndAfter
                """.trimIndent())
            }

            if (!isVerified) {
                return RingerModeResult.Failed(
                    reason = "Requested ${targetMode.displayName}, but actual ringer mode is ${ringerModeToString(actualRinger)}",
                    throwable = null
                )
            }

            RingerModeResult.Success(targetMode)
        } catch (e: SecurityException) {
            AppLog.e(tag, "SecurityException while changing sound mode: ${e.message}", e)
            RingerModeResult.PermissionRequired(
                targetMode = targetMode,
                message = "Permission was rejected by the system: ${e.message}"
            )
        } catch (e: Exception) {
            AppLog.e(tag, "Unexpected error changing sound mode: ${e.message}", e)
            RingerModeResult.Failed(
                reason = "Failed to change sound mode: ${e.message}",
                throwable = e
            )
        }
    }

    fun openNotificationPolicyAccessSettings(context: Context) {
        dndManager.openSettings(context)
    }

    companion object {
        @Volatile
        private var instance: RingerModeManager? = null

        fun getInstance(context: Context): RingerModeManager {
            return instance ?: synchronized(this) {
                instance ?: RingerModeManager(
                    AndroidSystemAudioAdapter(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}
