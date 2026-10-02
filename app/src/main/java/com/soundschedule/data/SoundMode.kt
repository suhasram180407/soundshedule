package com.soundschedule.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SoundModeSerializer : KSerializer<SoundMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SoundMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SoundMode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): SoundMode {
        val name = decoder.decodeString()
        return SoundMode.fromString(name)
    }
}

/**
 * Supported sound modes for SoundSchedule.
 * Exactly four user-facing modes:
 * 1. VOLUME (normal ringing mode)
 * 2. VIBRATE (vibration ringer mode)
 * 3. MUTE (silent ringer mode)
 * 4. DND (Do Not Disturb notification interruption filter)
 */
@Serializable(with = SoundModeSerializer::class)
enum class SoundMode(val displayName: String, val iconEmoji: String) {
    NORMAL("VOLUME", "🔊"),
    VIBRATE("VIBRATE", "📳"),
    MUTE("MUTE", "🔇"),
    DND("DND", "⛔");

    companion object {
        fun fromString(value: String): SoundMode {
            return when (value.uppercase()) {
                "SILENT" -> MUTE // Transparent migration for legacy records
                "MUTE" -> MUTE
                "DND" -> DND
                "OFF" -> MUTE // Migration fallback for any legacy OFF records
                "VIBRATE" -> VIBRATE
                "VOLUME" -> NORMAL
                "NORMAL" -> NORMAL
                else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
            }
        }
    }
}
