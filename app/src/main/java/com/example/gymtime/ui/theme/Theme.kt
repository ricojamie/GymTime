package com.example.gymtime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Holds all custom app colors that vary between light and dark mode.
 */
data class AppColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceCards: Color,
    val backgroundCanvas: Color,
    val inputBackground: Color
)

val DarkAppColors = AppColors(
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    surfaceCards = SurfaceCards,
    backgroundCanvas = BackgroundCanvas,
    inputBackground = DarkInputBackground
)

val LightAppColors = AppColors(
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    surfaceCards = LightSurfaceCards,
    backgroundCanvas = LightBackgroundCanvas,
    inputBackground = LightInputBackground
)

// Composition local for app colors (light/dark aware)
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// Composition local for gradient colors
val LocalGradientColors = staticCompositionLocalOf<Pair<Color, Color>> {
    error("No gradient colors provided")
}

@Composable
fun IronLogTheme(
    appColorScheme: AppColorScheme = ThemeColors.LimeGreen,
    darkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val appColors = if (darkMode) DarkAppColors else LightAppColors

    val dynamicColorScheme = if (darkMode) {
        darkColorScheme(
            primary = appColorScheme.primaryAccent,
            secondary = appColorScheme.primaryAccent,
            tertiary = appColorScheme.primaryAccent,
            background = appColors.backgroundCanvas,
            surface = appColors.surfaceCards,
            onPrimary = TextPrimary,
            onSecondary = TextPrimary,
            onTertiary = TextPrimary,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = appColorScheme.primaryAccent,
            secondary = appColorScheme.primaryAccent,
            tertiary = appColorScheme.primaryAccent,
            background = appColors.backgroundCanvas,
            surface = appColors.surfaceCards,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    }

    val gradientColors = if (darkMode) {
        Pair(appColorScheme.gradientStart, appColorScheme.gradientEnd)
    } else {
        Pair(appColorScheme.lightGradientStart, appColorScheme.lightGradientEnd)
    }

    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}
