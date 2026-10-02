package com.soundschedule.theme

import androidx.compose.ui.graphics.Color

// Pure dark / AMOLED foundations
val DarkBackground = Color(0xFF0C0E12)
val DarkSurface = Color(0xFF151921)
val DarkSurfaceElevated = Color(0xFF1E232E)
val DarkCardBorder = Color(0xFF2B3240)

// High-contrast text
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Sound & focus mode accents
val ModeNormal = Color(0xFF22C55E)     // Vibrant green
val ModeVibrate = Color(0xFFF59E0B)    // Amber
val ModeMute = Color(0xFF60A5FA)       // Sky / soft blue for pure Mute
val ModeDnd = Color(0xFFEF4444)        // Crimson / red for Do Not Disturb
val ModeSilent = ModeMute              // Backward compatibility alias

// Interactive accents
val PrimaryAccent = Color(0xFF38BDF8)   // Sky blue
val PrimaryAccentDark = Color(0xFF0284C7)
val SwitchActive = Color(0xFF10B981)
