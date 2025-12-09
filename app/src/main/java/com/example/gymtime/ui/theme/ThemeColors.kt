package com.example.gymtime.ui.theme

import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val primaryAccent: Color,
    val primaryAccentDark: Color,
    val primaryAccentLight: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

object ThemeColors {
    val LimeGreen = AppColorScheme(
        primaryAccent = Color(0xFFA3E635),
        primaryAccentDark = Color(0xFF84CC16),
        primaryAccentLight = Color(0xFFBEF264),
        gradientStart = Color(0xFF0A1A0A), // Dark green tint
        gradientEnd = Color(0xFF0A0A0A)    // Very dark black
    )

    val ElectricBlue = AppColorScheme(
        primaryAccent = Color(0xFF3B82F6),
        primaryAccentDark = Color(0xFF2563EB),
        primaryAccentLight = Color(0xFF60A5FA),
        gradientStart = Color(0xFF0A0F1A), // Dark blue tint
        gradientEnd = Color(0xFF0A0A0A)    // Very dark black
    )

    val CyberPurple = AppColorScheme(
        primaryAccent = Color(0xFFA855F7),
        primaryAccentDark = Color(0xFF9333EA),
        primaryAccentLight = Color(0xFFC084FC),
        gradientStart = Color(0xFF14091A), // Dark purple tint
        gradientEnd = Color(0xFF0A0A0A)    // Very dark black
    )

    val HotPink = AppColorScheme(
        primaryAccent = Color(0xFFEC4899),
        primaryAccentDark = Color(0xFFDB2777),
        primaryAccentLight = Color(0xFFF472B6),
        gradientStart = Color(0xFF1A0A14), // Dark pink tint
        gradientEnd = Color(0xFF0A0A0A)    // Very dark black
    )

    val GoldAmber = AppColorScheme(
        primaryAccent = Color(0xFFF59E0B),
        primaryAccentDark = Color(0xFFD97706),
        primaryAccentLight = Color(0xFFFBBF24),
        gradientStart = Color(0xFF1A140A), // Dark amber tint
        gradientEnd = Color(0xFF0A0A0A)    // Very dark black
    )

    fun getScheme(colorName: String): AppColorScheme {
        return when (colorName) {
            "lime" -> LimeGreen
            "blue" -> ElectricBlue
            "purple" -> CyberPurple
            "pink" -> HotPink
            "gold" -> GoldAmber
            else -> LimeGreen
        }
    }
}
