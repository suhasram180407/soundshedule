package com.soundschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.data.SoundMode
import com.soundschedule.theme.DarkBackground
import com.soundschedule.theme.DarkCardBorder
import com.soundschedule.theme.DarkSurface
import com.soundschedule.theme.DarkSurfaceElevated
import com.soundschedule.theme.ModeDnd
import com.soundschedule.theme.ModeMute
import com.soundschedule.theme.ModeNormal
import com.soundschedule.theme.ModeVibrate
import com.soundschedule.theme.PrimaryAccent
import com.soundschedule.theme.SwitchActive
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary
import com.soundschedule.theme.TextSecondary

enum class SessionState {
    ACTIVE,
    UPCOMING,
    COMPLETED
}

/**
 * Schedule session card displaying time interval, mode, days, label,
 * and an individual ON/OFF switch like an alarm clock setting.
 */
@Composable
fun ScheduleItemCard(
    event: ScheduleEvent,
    sessionState: SessionState = SessionState.UPCOMING,
    onCardClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val modeColor = when (event.mode) {
        SoundMode.NORMAL -> ModeNormal
        SoundMode.VIBRATE -> ModeVibrate
        SoundMode.MUTE -> ModeMute
        SoundMode.DND -> ModeDnd
    }

    val isActive = sessionState == SessionState.ACTIVE && event.enabled
    val isCompleted = sessionState == SessionState.COMPLETED

    // Visual state: Dimmed if turned OFF or COMPLETED; bright if enabled
    val cardAlpha = when {
        !event.enabled -> 0.45f
        isCompleted -> 0.65f
        else -> 1.0f
    }

    val borderColor = when {
        isActive -> PrimaryAccent
        event.enabled -> DarkCardBorder
        else -> DarkCardBorder.copy(alpha = 0.5f)
    }

    val containerColor = when {
        isActive -> DarkSurfaceElevated
        event.enabled -> DarkSurface
        else -> DarkSurface.copy(alpha = 0.6f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Icon & Time Interval Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.formattedTime,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) PrimaryAccent else if (event.enabled) TextPrimary else TextMuted
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sound Mode Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(modeColor.copy(alpha = if (event.enabled) 0.18f else 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${event.mode.iconEmoji} ${event.mode.displayName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (event.enabled) modeColor else modeColor.copy(alpha = 0.5f)
                        )
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryAccent.copy(alpha = 0.2f))
                                .border(1.dp, PrimaryAccent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent
                            )
                        }
                    } else if (isCompleted && event.enabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.daysSummary,
                        fontSize = 12.sp,
                        color = if (event.enabled) TextSecondary else TextMuted
                    )

                    if (event.label.isNotBlank()) {
                        Text(
                            text = " • ${event.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (event.enabled) TextPrimary else TextMuted
                        )
                    }
                }
            }

            // Individual ON/OFF Switch (like setting an alarm)
            Switch(
                checked = event.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkBackground,
                    checkedTrackColor = SwitchActive,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkCardBorder
                )
            )
        }
    }
}
