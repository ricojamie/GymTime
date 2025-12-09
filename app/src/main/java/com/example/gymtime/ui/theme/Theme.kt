package com.example.gymtime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Composition local for gradient colors
val LocalGradientColors = staticCompositionLocalOf<Pair<Color, Color>> {
    error("No gradient colors provided")
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = PrimaryAccent, // Using PrimaryAccent for secondary as well for consistency
    tertiary = PrimaryAccent, // Using PrimaryAccent for tertiary as well for consistency
    background = BackgroundCanvas,
    surface = SurfaceCards,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun GymTimeTheme(
    darkTheme: Boolean = true, // Always dark theme
    appColorScheme: AppColorScheme = ThemeColors.LimeGreen, // Dynamic color scheme
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = darkColorScheme(
        primary = appColorScheme.primaryAccent,
        secondary = appColorScheme.primaryAccent,
        tertiary = appColorScheme.primaryAccent,
        background = BackgroundCanvas,
        surface = SurfaceCards,
        onPrimary = TextPrimary,
        onSecondary = TextPrimary,
        onTertiary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

    CompositionLocalProvider(
        LocalGradientColors provides Pair(appColorScheme.gradientStart, appColorScheme.gradientEnd)
    ) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}