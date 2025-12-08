package com.example.gymtime.ui.theme

import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val primaryAccent: Color,
    val primaryAccentDark: Color,
    val primaryAccentLight: Color
)

object ThemeColors {
    val LimeGreen = AppColorScheme(
        primaryAccent = Color(0xFFA3E635),
        primaryAccentDark = Color(0xFF84CC16),
        primaryAccentLight = Color(0xFFBEF264)
    )

    val ElectricBlue = AppColorScheme(
        primaryAccent = Color(0xFF3B82F6),
        primaryAccentDark = Color(0xFF2563EB),
        primaryAccentLight = Color(0xFF60A5FA)
    )

    val CyberPurple = AppColorScheme(
        primaryAccent = Color(0xFFA855F7),
        primaryAccentDark = Color(0xFF9333EA),
        primaryAccentLight = Color(0xFFC084FC)
    )

    val HotPink = AppColorScheme(
        primaryAccent = Color(0xFFEC4899),
        primaryAccentDark = Color(0xFFDB2777),
        primaryAccentLight = Color(0xFFF472B6)
    )

    val GoldAmber = AppColorScheme(
        primaryAccent = Color(0xFFF59E0B),
        primaryAccentDark = Color(0xFFD97706),
        primaryAccentLight = Color(0xFFFBBF24)
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
