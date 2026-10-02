package com.soundschedule.audio

import com.soundschedule.data.SoundMode

sealed class RingerModeResult {
    data class Success(val newMode: SoundMode) : RingerModeResult()

    data class PermissionRequired(
        val targetMode: SoundMode,
        val message: String = "Notification Policy Access (Do Not Disturb) is required to switch sound modes."
    ) : RingerModeResult()

    data class Failed(
        val reason: String,
        val throwable: Throwable? = null
    ) : RingerModeResult()
}
