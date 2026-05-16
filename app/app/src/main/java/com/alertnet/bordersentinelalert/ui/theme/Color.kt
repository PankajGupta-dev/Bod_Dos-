package com.alertnet.bordersentinelalert.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val OliveDrab = Color(0xFF3C432E)
val ArmyGreen = Color(0xFF4B5320)
val DarkCharcoal = Color(0xFF1B1B1B)
val EmergencyRed = Color(0xFFD32F2F)
val HighConfidence = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFFA000)

val MilitaryDarkColorScheme = darkColorScheme(
    primary = OliveDrab,
    onPrimary = Color.White,
    secondary = ArmyGreen,
    onSecondary = Color.White,
    background = DarkCharcoal,
    onBackground = Color.LightGray,
    surface = Color(0xFF242424),
    onSurface = Color.White,
    error = EmergencyRed,
    onError = Color.White
)