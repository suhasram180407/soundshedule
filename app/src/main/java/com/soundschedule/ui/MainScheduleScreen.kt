package com.soundschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundschedule.audio.RingerModeResult
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
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary
import com.soundschedule.theme.TextSecondary
import com.soundschedule.ui.components.ScheduleEditorDialog
import com.soundschedule.ui.components.ScheduleItemCard
import com.soundschedule.ui.components.SessionState
import com.soundschedule.utils.TimeUtils
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MainScheduleScreen(
    viewModel: MainScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val schedules by viewModel.schedules.collectAsState()
    val isMasterEnabled by viewModel.isMasterEnabled.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val nextEvent by viewModel.nextUpcomingEvent.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    // Real system audio state from AudioManager
    val systemSoundMode by viewModel.currentSystemSoundMode.collectAsState()
    val selectedManualMode by viewModel.selectedManualMode.collectAsState()
    val hasDndAccess by viewModel.notificationPolicyAccessGranted.collectAsState()
    val ringerResult by viewModel.lastRingerModeResult.collectAsState()
    val canScheduleExactAlarms by viewModel.canScheduleExactAlarms.collectAsState()

    var editingEvent by remember { mutableStateOf<ScheduleEvent?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNew = true },
                containerColor = PrimaryAccent,
                contentColor = DarkBackground,
                shape = CircleShape,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Add Schedule", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar with single Master Scheduler toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sound Schedule",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // Single Master Scheduler ON/OFF Control (Section 2)
                    val pillText = if (!isMasterEnabled) "○ MASTER OFF" else if (!canScheduleExactAlarms) "⚠️ NO PERM" else "● MASTER ON"
                    val pillColor = if (!isMasterEnabled) TextMuted else if (!canScheduleExactAlarms) ModeVibrate else ModeNormal
                    val pillBg = if (!isMasterEnabled) DarkSurfaceElevated else pillColor.copy(alpha = 0.18f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .border(
                                1.dp,
                                if (isMasterEnabled) pillColor else DarkCardBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable { viewModel.setMasterEnabled(!isMasterEnabled) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = pillText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillColor
                        )
                    }
                }
            }



            // Exact Alarm Permission Required Banner
            if (!canScheduleExactAlarms) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ModeVibrate.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ModeVibrate.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⏰", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exact Alarm Permission Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ModeVibrate
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android requires exact alarm permission for SoundSchedule to trigger sound changes reliably at the exact scheduled minute. Without this permission, background alarms cannot run.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.openExactAlarmSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ModeVibrate, contentColor = DarkBackground),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Exact Alarm Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Notification Policy Access / Error Banner
            if (ringerResult is RingerModeResult.PermissionRequired) {
                val perm = ringerResult as RingerModeResult.PermissionRequired
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ModeVibrate.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ModeVibrate.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🛡️", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Notification Policy Access Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ModeVibrate
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = perm.message,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.openDndSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ModeVibrate, contentColor = DarkBackground),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open DND Access Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { viewModel.dismissRingerModeNotice() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                                ) {
                                    Text("Dismiss", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else if (ringerResult is RingerModeResult.Failed) {
                val fail = ringerResult as RingerModeResult.Failed
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ModeDnd.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ModeDnd.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ ${fail.reason}",
                                fontSize = 12.sp,
                                color = ModeDnd,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissRingerModeNotice() }) {
                                Text("OK", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Manual Sound Control (4 modes: VOLUME, VIBRATE, MUTE, DND with double-tap deselect)
            item {
                Column {
                    Text(
                        text = "MANUAL SOUND CONTROL",
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
                        ManualModeButton(
                            mode = SoundMode.NORMAL,
                            isSelected = selectedManualMode == SoundMode.NORMAL,
                            color = ModeNormal,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.toggleManualMode(SoundMode.NORMAL) }
                        )
                        ManualModeButton(
                            mode = SoundMode.VIBRATE,
                            isSelected = selectedManualMode == SoundMode.VIBRATE,
                            color = ModeVibrate,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.toggleManualMode(SoundMode.VIBRATE) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: MUTE, DND
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ManualModeButton(
                            mode = SoundMode.MUTE,
                            isSelected = selectedManualMode == SoundMode.MUTE,
                            color = ModeMute,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.toggleManualMode(SoundMode.MUTE) }
                        )
                        ManualModeButton(
                            mode = SoundMode.DND,
                            isSelected = selectedManualMode == SoundMode.DND,
                            color = ModeDnd,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.toggleManualMode(SoundMode.DND) }
                        )
                    }
                }
            }

            // Conflict Warning Banner
            if (conflicts.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ModeVibrate.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ModeVibrate.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conflicting Schedules Detected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ModeVibrate
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            conflicts.forEach { c ->
                                Text(
                                    text = "• ${c.description}",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap any conflicting schedule below to edit its From/To interval.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Unified Schedules Header (Section 3: ONE unified Schedules list)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCHEDULES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "${schedules.size} sessions",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Unified Schedules List (Section 3, 10, 11, 12, 13)
            if (schedules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🔔", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Schedules Configured",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Schedule' below to set up automated sound sessions.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(schedules, key = { it.id }) { event ->
                    val sessionState = when {
                        TimeUtils.isSessionActive(event) -> SessionState.ACTIVE
                        TimeUtils.isSessionCompletedToday(event) -> SessionState.COMPLETED
                        else -> SessionState.UPCOMING
                    }

                    ScheduleItemCard(
                        event = event,
                        sessionState = sessionState,
                        onCardClick = { editingEvent = event },
                        onToggleEnabled = { isEnabled ->
                            viewModel.toggleScheduleEnabled(event.id, isEnabled)
                        }
                    )
                }
            }

            // Bottom padding for FAB clearance
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add Schedule Dialog
    if (isAddingNew) {
        ScheduleEditorDialog(
            initialEvent = null,
            onSave = {
                viewModel.saveSchedule(it)
                isAddingNew = false
            },
            onDismiss = { isAddingNew = false },
            conflictChecker = { viewModel.checkConflicts(it) }
        )
    }

    // Edit Schedule Dialog
    if (editingEvent != null) {
        ScheduleEditorDialog(
            initialEvent = editingEvent,
            onSave = {
                viewModel.saveSchedule(it)
                editingEvent = null
            },
            onDelete = { id ->
                viewModel.deleteSchedule(id)
                editingEvent = null
            },
            onDismiss = { editingEvent = null },
            conflictChecker = { viewModel.checkConflicts(it) }
        )
    }
}

@Composable
private fun ManualModeButton(
    mode: SoundMode,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.22f) else DarkSurface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) color else DarkCardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = mode.iconEmoji, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = mode.displayName,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else TextSecondary
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "✓",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}
