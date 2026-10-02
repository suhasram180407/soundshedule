package com.soundschedule.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.soundschedule.data.ScheduleEvent
import com.soundschedule.theme.DarkCardBorder
import com.soundschedule.theme.DarkSurface
import com.soundschedule.theme.ModeSilent
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary
import com.soundschedule.theme.TextSecondary

@Composable
fun DeleteConfirmationDialog(
    event: ScheduleEvent,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Delete Schedule?",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete the scheduled sound change for ${event.formattedTime} (${event.mode.displayName})? This action cannot be undone.",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = ModeSilent)
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text("Cancel")
            }
        }
    )
}
