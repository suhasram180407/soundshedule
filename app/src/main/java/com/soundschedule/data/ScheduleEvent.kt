package com.soundschedule.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DayOfWeek) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): DayOfWeek {
        val name = decoder.decodeString()
        return DayOfWeek.valueOf(name.uppercase(Locale.ROOT))
    }
}

object ScheduleEventSerializer : KSerializer<ScheduleEvent> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScheduleEvent", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ScheduleEvent) {
        ScheduleEventSurrogate.serializer().serialize(
            encoder,
            ScheduleEventSurrogate(
                id = value.id,
                startHour = value.startHour,
                startMinute = value.startMinute,
                endHour = value.endHour,
                endMinute = value.endMinute,
                mode = value.mode,
                enabled = value.enabled,
                daysOfWeek = value.daysOfWeek,
                label = value.label,
                createdAt = value.createdAt,
                updatedAt = value.updatedAt
            )
        )
    }

    override fun deserialize(decoder: Decoder): ScheduleEvent {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val jsonElement = jsonDecoder.decodeJsonElement()
            val obj = jsonElement.jsonObject

            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L
            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val label = obj["label"]?.jsonPrimitive?.content ?: ""
            val createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
            val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

            val modeStr = obj["mode"]?.jsonPrimitive?.content ?: "NORMAL"
            val mode = SoundMode.fromString(modeStr)

            val days = mutableSetOf<DayOfWeek>()
            obj["daysOfWeek"]?.jsonArray?.forEach { el ->
                try {
                    days.add(DayOfWeek.valueOf(el.jsonPrimitive.content.uppercase()))
                } catch (_: Exception) {}
            }
            if (days.isEmpty() && obj["daysOfWeek"] == null) {
                days.addAll(DefaultCollegeSchedule.Weekdays)
            }

            val startHour: Int
            val startMinute: Int
            val endHour: Int
            val endMinute: Int

            if (obj.containsKey("startHour")) {
                startHour = obj["startHour"]?.jsonPrimitive?.intOrNull ?: 8
                startMinute = obj["startMinute"]?.jsonPrimitive?.intOrNull ?: 0
                endHour = obj["endHour"]?.jsonPrimitive?.intOrNull ?: ((startHour + 1) % 24)
                endMinute = obj["endMinute"]?.jsonPrimitive?.intOrNull ?: startMinute
            } else {
                // Transparent migration from legacy "hour" and "minute" point events
                startHour = obj["hour"]?.jsonPrimitive?.intOrNull ?: 8
                startMinute = obj["minute"]?.jsonPrimitive?.intOrNull ?: 0
                endHour = (startHour + 1) % 24
                endMinute = startMinute
            }

            return ScheduleEvent(
                id = id,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                mode = mode,
                enabled = enabled,
                daysOfWeek = days,
                label = label,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } else {
            val surrogate = ScheduleEventSurrogate.serializer().deserialize(decoder)
            return ScheduleEvent(
                id = surrogate.id,
                startHour = surrogate.startHour,
                startMinute = surrogate.startMinute,
                endHour = surrogate.endHour,
                endMinute = surrogate.endMinute,
                mode = surrogate.mode,
                enabled = surrogate.enabled,
                daysOfWeek = surrogate.daysOfWeek,
                label = surrogate.label,
                createdAt = surrogate.createdAt,
                updatedAt = surrogate.updatedAt
            )
        }
    }
}

@Serializable
internal data class ScheduleEventSurrogate(
    val id: Long = 0L,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val mode: SoundMode,
    val enabled: Boolean = true,
    val daysOfWeek: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek>,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * ScheduleSession / ScheduleEvent representing a session with a start time, end time, sound mode,
 * active days of the week, and label.
 */
@Serializable(with = ScheduleEventSerializer::class)
data class ScheduleEvent(
    val id: Long = 0L,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val mode: SoundMode,
    val enabled: Boolean = true,
    val daysOfWeek: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(startHour in 0..23) { "startHour must be between 0 and 23. Received: $startHour" }
        require(startMinute in 0..59) { "startMinute must be between 0 and 59. Received: $startMinute" }
        require(endHour in 0..23) { "endHour must be between 0 and 23. Received: $endHour" }
        require(endMinute in 0..59) { "endMinute must be between 0 and 59. Received: $endMinute" }
    }

    // Backward-compatibility properties for code referencing hour/minute
    val hour: Int get() = startHour
    val minute: Int get() = startMinute

    // Backward-compatible secondary constructor for existing unit tests
    constructor(
        id: Long = 0L,
        hour: Int,
        minute: Int,
        mode: SoundMode,
        enabled: Boolean = true,
        daysOfWeek: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ),
        label: String = "",
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        startHour = hour,
        startMinute = minute,
        endHour = (hour + 1) % 24,
        endMinute = minute,
        mode = mode,
        enabled = enabled,
        daysOfWeek = daysOfWeek,
        label = label,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    val formattedStartTime: String
        get() {
            val localTime = LocalTime.of(startHour, startMinute)
            return localTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
        }

    val formattedEndTime: String
        get() {
            val localTime = LocalTime.of(endHour, endMinute)
            return localTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
        }

    val formattedTime: String
        get() = "$formattedStartTime - $formattedEndTime"

    val daysSummary: String
        get() {
            if (daysOfWeek.isEmpty()) return "No days selected"
            val weekdays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
            val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            val allDays = DayOfWeek.entries.toSet()

            return when {
                daysOfWeek == allDays -> "Every day"
                daysOfWeek == weekdays -> "Mon - Fri"
                daysOfWeek == weekends -> "Weekends"
                else -> {
                    val order = listOf(
                        DayOfWeek.MONDAY to "Mon",
                        DayOfWeek.TUESDAY to "Tue",
                        DayOfWeek.WEDNESDAY to "Wed",
                        DayOfWeek.THURSDAY to "Thu",
                        DayOfWeek.FRIDAY to "Fri",
                        DayOfWeek.SATURDAY to "Sat",
                        DayOfWeek.SUNDAY to "Sun"
                    )
                    order.filter { it.first in daysOfWeek }.joinToString(", ") { it.second }
                }
            }
        }
}
