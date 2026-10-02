package com.soundschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.soundschedule.theme.DarkSurface
import com.soundschedule.theme.PrimaryAccent
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary

/**
 * Time selection dialog utilizing the vertical rolling/wheel clock picker.
 * Strictly avoids circular/analog clock faces.
 */
@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Vertically Rolling Wheel Time Picker (Hours, Minutes, AM/PM)
            RollingTimePicker(
                initialHour = initialHour,
                initialMinute = initialMinute,
                onTimeChanged = { h, m ->
                    selectedHour = h
                    selectedMinute = m
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        onTimeSelected(selectedHour, selectedMinute)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryAccent)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
