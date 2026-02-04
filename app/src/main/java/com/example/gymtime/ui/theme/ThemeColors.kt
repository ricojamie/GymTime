package com.example.gymtime.ui.theme

import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val primaryAccent: Color,
    val primaryAccentDark: Color,
    val primaryAccentLight: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val lightGradientStart: Color = Color(0xFFF0F0F0),
    val lightGradientEnd: Color = Color(0xFFF5F5F5)
)

object ThemeColors {
    val LimeGreen = AppColorScheme(
        primaryAccent = Color(0xFFA3E635),
        primaryAccentDark = Color(0xFF84CC16),
        primaryAccentLight = Color(0xFFBEF264),
        gradientStart = Color(0xFF0A1A0A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF5FAF0),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val ElectricBlue = AppColorScheme(
        primaryAccent = Color(0xFF3B82F6),
        primaryAccentDark = Color(0xFF2563EB),
        primaryAccentLight = Color(0xFF60A5FA),
        gradientStart = Color(0xFF0A0F1A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF0F4FF),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val CyberPurple = AppColorScheme(
        primaryAccent = Color(0xFFA855F7),
        primaryAccentDark = Color(0xFF9333EA),
        primaryAccentLight = Color(0xFFC084FC),
        gradientStart = Color(0xFF14091A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF5F0FF),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val HotPink = AppColorScheme(
        primaryAccent = Color(0xFFEC4899),
        primaryAccentDark = Color(0xFFDB2777),
        primaryAccentLight = Color(0xFFF472B6),
        gradientStart = Color(0xFF1A0A14),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFFFF0F5),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val GoldAmber = AppColorScheme(
        primaryAccent = Color(0xFFF59E0B),
        primaryAccentDark = Color(0xFFD97706),
        primaryAccentLight = Color(0xFFFBBF24),
        gradientStart = Color(0xFF1A140A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFFFFAF0),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val BloodRed = AppColorScheme(
        primaryAccent = Color(0xFFEF4444),
        primaryAccentDark = Color(0xFFB91C1C),
        primaryAccentLight = Color(0xFFF87171),
        gradientStart = Color(0xFF1A0A0A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFFFF0F0),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val SunsetOrange = AppColorScheme(
        primaryAccent = Color(0xFFF97316),
        primaryAccentDark = Color(0xFFC2410C),
        primaryAccentLight = Color(0xFFFB923C),
        gradientStart = Color(0xFF1A110A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFFFF5F0),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val MintFresh = AppColorScheme(
        primaryAccent = Color(0xFF10B981),
        primaryAccentDark = Color(0xFF047857),
        primaryAccentLight = Color(0xFF34D399),
        gradientStart = Color(0xFF0A1A14),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF0FFF8),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val SlateGrey = AppColorScheme(
        primaryAccent = Color(0xFF64748B),
        primaryAccentDark = Color(0xFF334155),
        primaryAccentLight = Color(0xFF94A3B8),
        gradientStart = Color(0xFF0F1217),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF0F2F5),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    val LavenderFocus = AppColorScheme(
        primaryAccent = Color(0xFF8B5CF6),
        primaryAccentDark = Color(0xFF6D28D9),
        primaryAccentLight = Color(0xFFA78BFA),
        gradientStart = Color(0xFF120A1A),
        gradientEnd = Color(0xFF0A0A0A),
        lightGradientStart = Color(0xFFF5F0FF),
        lightGradientEnd = Color(0xFFF5F5F5)
    )

    fun getScheme(colorName: String): AppColorScheme {
        return when (colorName) {
            "lime" -> LimeGreen
            "blue" -> ElectricBlue
            "purple" -> CyberPurple
            "pink" -> HotPink
            "gold" -> GoldAmber
            "red" -> BloodRed
            "orange" -> SunsetOrange
            "mint" -> MintFresh
            "slate" -> SlateGrey
            "lavender" -> LavenderFocus
            else -> LimeGreen
        }
    }
}
