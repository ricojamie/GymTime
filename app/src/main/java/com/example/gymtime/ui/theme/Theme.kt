package com.example.gymtime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Composition local for gradient colors
val LocalGradientColors = staticCompositionLocalOf<Pair<Color, Color>> {
    error("No gradient colors provided")
}

@Composable
fun IronLogTheme(
    appColorScheme: AppColorScheme = ThemeColors.LimeGreen,
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
