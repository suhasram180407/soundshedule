package com.soundschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundschedule.theme.DarkBackground
import com.soundschedule.theme.DarkCardBorder
import com.soundschedule.theme.DarkSurfaceElevated
import com.soundschedule.theme.PrimaryAccent
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary
import com.soundschedule.theme.TextSecondary
import java.time.DayOfWeek

@Composable
fun DaySelector(
    selectedDays: Set<DayOfWeek>,
    onDaysChanged: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayList = listOf(
        DayOfWeek.MONDAY to "M",
        DayOfWeek.TUESDAY to "T",
        DayOfWeek.WEDNESDAY to "W",
        DayOfWeek.THURSDAY to "T",
        DayOfWeek.FRIDAY to "F",
        DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "S"
    )

    val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )
    val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val allDays = DayOfWeek.entries.toSet()

    Column(modifier = modifier.fillMaxWidth()) {
        // Individual Day Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayList.forEach { (day, label) ->
                val isSelected = day in selectedDays
                val fullDayName = day.name.lowercase().replaceFirstChar { it.uppercase() }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) PrimaryAccent else DarkSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PrimaryAccent else DarkCardBorder,
                            shape = CircleShape
                        )
                        .clickable {
                            val newSet = if (isSelected) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                            onDaysChanged(newSet)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isSelected) DarkBackground else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset shortcut chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Mon - Fri",
                isSelected = selectedDays == weekdays,
                onClick = { onDaysChanged(weekdays) }
            )
            PresetChip(
                label = "Weekends",
                isSelected = selectedDays == weekends,
                onClick = { onDaysChanged(weekends) }
            )
            PresetChip(
                label = "Every day",
                isSelected = selectedDays == allDays,
                onClick = { onDaysChanged(allDays) }
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else DarkSurfaceElevated)
            .border(
                1.dp,
                if (isSelected) PrimaryAccent else DarkCardBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryAccent else TextSecondary
        )
    }
}
