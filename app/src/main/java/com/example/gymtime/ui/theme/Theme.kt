package com.example.gymtime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Holds all custom app colors that vary between light and dark mode.
 */
data class AppColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceCards: Color,
    val backgroundCanvas: Color,
    val inputBackground: Color,
    // Text-field caret color. Resolved per-theme in IronLogTheme so it is always
    // visible against the background (the accent can be too dim on dark themes).
    val cursor: Color = PrimaryAccent
)

/** WCAG contrast ratio between two colors (>= 1, higher is more contrast). */
private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = max(a.luminance(), b.luminance())
    val darker = min(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Picks a caret color that is actually visible: the theme accent when it has
 * enough contrast with the background, otherwise the primary text color.
 */
private fun resolveCursorColor(accent: Color, background: Color, textPrimary: Color): Color =
    if (contrastRatio(accent, background) >= 3f) accent else textPrimary

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
    themeFontKey: String = ThemeFontOption.BEBAS_NEUE.storageKey,
    customFontUri: String? = null,
    content: @Composable () -> Unit
) {
    val baseColors = if (darkMode) DarkAppColors else LightAppColors
    val appColors = baseColors.copy(
        cursor = resolveCursorColor(
            accent = appColorScheme.primaryAccent,
            background = baseColors.backgroundCanvas,
            textPrimary = baseColors.textPrimary
        )
    )
    val typography = rememberAppTypography(
        themeFontKey = themeFontKey,
        customFontUri = customFontUri
    )

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
            typography = typography,
            content = content
        )
    }
}
