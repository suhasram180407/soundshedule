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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soundschedule.data.ScheduleConflict
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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleEditorDialog(
    initialEvent: ScheduleEvent? = null,
    onSave: (ScheduleEvent) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    conflictChecker: (ScheduleEvent) -> List<ScheduleConflict>
) {
    val isEditing = initialEvent != null

    var startHour by remember { mutableIntStateOf(initialEvent?.startHour ?: 8) }
    var startMinute by remember { mutableIntStateOf(initialEvent?.startMinute ?: 50) }
    var endHour by remember { mutableIntStateOf(initialEvent?.endHour ?: 11) }
    var endMinute by remember { mutableIntStateOf(initialEvent?.endMinute ?: 0) }

    var mode by remember { mutableStateOf(initialEvent?.mode ?: SoundMode.VIBRATE) }
    var days by remember {
        mutableStateOf(
            initialEvent?.daysOfWeek ?: setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )
    }
    var label by remember { mutableStateOf(initialEvent?.label ?: "") }
    var enabled by remember { mutableStateOf(initialEvent?.enabled ?: true) }

    // "from" | "to" | null
    var pickingTimeFor by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Dynamic conflict calculation on the fly - automation is default
    val candidate = ScheduleEvent(
        id = initialEvent?.id ?: 0L,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        mode = mode,
        enabled = enabled,
        daysOfWeek = days,
        label = label.trim()
    )
    val detectedConflicts = conflictChecker(candidate)

    val formattedStartTime = remember(startHour, startMinute) {
        LocalTime.of(startHour, startMinute).format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    }
    val formattedEndTime = remember(endHour, endMinute) {
        LocalTime.of(endHour, endMinute).format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    }

    if (pickingTimeFor == "from") {
        TimePickerDialog(
            title = "Select From Time",
            initialHour = startHour,
            initialMinute = startMinute,
            onTimeSelected = { selectedH, selectedM ->
                startHour = selectedH
                startMinute = selectedM
                pickingTimeFor = null
                validationError = null
            },
            onDismiss = { pickingTimeFor = null }
        )
    } else if (pickingTimeFor == "to") {
        TimePickerDialog(
            title = "Select To Time",
            initialHour = endHour,
            initialMinute = endMinute,
            onTimeSelected = { selectedH, selectedM ->
                endHour = selectedH
                endMinute = selectedM
                pickingTimeFor = null
                validationError = null
            },
            onDismiss = { pickingTimeFor = null }
        )
    }

    if (showDeleteConfirm && initialEvent != null && onDelete != null) {
        DeleteConfirmationDialog(
            event = initialEvent,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(initialEvent.id)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Schedule" else "Add Schedule",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (initialEvent != null) "ID #${initialEvent.id}" else "New Session",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // FROM TIME and TO TIME Session Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // From Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FROM TIME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                                .clickable { pickingTimeFor = "from" }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = formattedStartTime,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryAccent
                                )
                                Text(
                                    text = "Start",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // To Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TO TIME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                                .clickable { pickingTimeFor = "to" }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = formattedEndTime,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryAccent
                                )
                                Text(
                                    text = "End",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sound Mode Selector (5 modes: NORMAL, VIBRATE, MUTE, DND, OFF)
                Text(
                    text = "SOUND MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Row 1: VOLUME, VIBRATE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundModeOption(
                        mode = SoundMode.NORMAL,
                        isSelected = mode == SoundMode.NORMAL,
                        accentColor = ModeNormal,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = SoundMode.NORMAL }
                    )
                    SoundModeOption(
                        mode = SoundMode.VIBRATE,
                        isSelected = mode == SoundMode.VIBRATE,
                        accentColor = ModeVibrate,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = SoundMode.VIBRATE }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: MUTE, DND
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundModeOption(
                        mode = SoundMode.MUTE,
                        isSelected = mode == SoundMode.MUTE,
                        accentColor = ModeMute,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = SoundMode.MUTE }
                    )
                    SoundModeOption(
                        mode = SoundMode.DND,
                        isSelected = mode == SoundMode.DND,
                        accentColor = ModeDnd,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = SoundMode.DND }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Day of Week Selector
                Text(
                    text = "ACTIVE DAYS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                DaySelector(
                    selectedDays = days,
                    onDaysChanged = {
                        days = it
                        validationError = null
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Label field
                Text(
                    text = "LABEL (OPTIONAL)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("e.g. Class, Lunch, Lab, Focus", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Conflict Banner (Section 23: Interval Conflict Warning)
                if (detectedConflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ModeVibrate.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚠️ Schedule Conflict Detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ModeVibrate
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            detectedConflicts.forEach { c ->
                                Text(
                                    text = c.description,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // Validation Error Banner
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠️ $validationError",
                        color = ModeDnd,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEditing && onDelete != null) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ModeDnd.copy(alpha = 0.15f),
                                contentColor = ModeDnd
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }

                    Button(
                        onClick = {
                            if (days.isEmpty()) {
                                validationError = "Please select at least one day of the week."
                                return@Button
                            }
                            if (startHour == endHour && startMinute == endMinute) {
                                validationError = "From Time and To Time cannot be identical."
                                return@Button
                            }
                            val eventToSave = ScheduleEvent(
                                id = initialEvent?.id ?: 0L,
                                startHour = startHour,
                                startMinute = startMinute,
                                endHour = endHour,
                                endMinute = endMinute,
                                mode = mode,
                                enabled = enabled,
                                daysOfWeek = days,
                                label = label.trim(),
                                createdAt = initialEvent?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(eventToSave)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent, contentColor = DarkBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Save Schedule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundModeOption(
    mode: SoundMode,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else DarkSurfaceElevated)
            .border(
                1.dp,
                if (isSelected) accentColor else DarkCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = mode.iconEmoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.displayName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accentColor else TextSecondary
            )
        }
    }
}
