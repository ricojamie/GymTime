package com.example.gymtime.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The selected accent stays vivid on filled actions; small text uses the tonal scheme. */
data class LoggerActionColors(val fill: Color, val onFill: Color)

val LocalLoggerActionColors = staticCompositionLocalOf { loggerActionColors(PrimaryAccent) }

/** Shared by the app shell and secondary surfaces; nested preview scopes must not retone the accent. */
val LocalLoggerPreviewThemeActive = staticCompositionLocalOf { false }

internal fun loggerActionColors(accent: Color): LoggerActionColors {
    val fill = accent.copy(alpha = 1f)
    return LoggerActionColors(fill = fill, onFill = strongestTextOn(fill))
}

/** A screen-scoped preview: the saved accent, light/dark mode and font stay in charge. */
@Composable
fun LoggerPreviewTheme(content: @Composable () -> Unit) {
    if (LocalLoggerPreviewThemeActive.current) {
        content()
        return
    }
    val current = MaterialTheme.colorScheme
    val darkMode = current.background.luminance() < 0.5f
    val scheme = remember(current.primary, darkMode) {
        loggerPreviewColorScheme(current.primary, darkMode)
    }
    val actionColors = remember(current.primary) { loggerActionColors(current.primary) }
    val appColors = remember(scheme) { loggerPreviewAppColors(scheme) }
    CompositionLocalProvider(
        LocalLoggerPreviewThemeActive provides true,
        LocalAppColors provides appColors,
        LocalLoggerActionColors provides actionColors,
        LocalGradientColors provides (scheme.background to scheme.surfaceContainerLow)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

internal fun loggerPreviewAppColors(scheme: ColorScheme) = AppColors(
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textTertiary = scheme.onSurfaceVariant,
    surfaceCards = scheme.surface,
    backgroundCanvas = scheme.background,
    inputBackground = scheme.surfaceContainerHigh,
    cursor = scheme.primary
)

/** Pure palette construction also covers arbitrary custom hex colors, including black and white. */
internal fun loggerPreviewColorScheme(accent: Color, darkMode: Boolean): ColorScheme {
    val seed = accent.copy(alpha = 1f)
    val neutral = if (darkMode) Color(0xFF111318) else Color(0xFFF8F9FC)
    val canvas = lerp(neutral, seed, if (darkMode) 0.025f else 0.035f)
    val surface = lerp(if (darkMode) Color(0xFF1C1F25) else Color.White, seed, 0.025f)
    val surfaceLow = lerp(canvas, surface, 0.55f)
    val surfaceHigh = lerp(surface, seed, if (darkMode) 0.08f else 0.06f)
    val surfaceHighest = lerp(surface, seed, if (darkMode) 0.12f else 0.10f)
    // A nearly black seed can darken a dark-mode container; a white seed can brighten
    // a light-mode one. Choose the restrictive surface by luminance, not its token name.
    val accentSurface = listOf(canvas, surface, surfaceHigh, surfaceHighest).let { surfaces ->
        if (darkMode) surfaces.maxBy { it.luminance() } else surfaces.minBy { it.luminance() }
    }
    val text = if (darkMode) Color(0xFFF5F7FA) else Color(0xFF171B24)
    val mutedText = readableOn(lerp(text, surface, 0.24f), accentSurface)
    val secondarySeed = seed.shiftHue(-28f)
    val tertiarySeed = seed.shiftHue(34f)
    // Primary is used for both buttons and text in existing shared sheets. Adjust only its
    // tone as needed so a pale yellow or deep blue custom accent stays readable there too.
    val primary = readableOn(seed, accentSurface)
    val secondary = readableOn(secondarySeed, accentSurface)
    val tertiary = readableOn(tertiarySeed, accentSurface)
    val containerTint = if (darkMode) 0.20f else 0.16f
    val primaryContainer = lerp(surface, seed, containerTint)
    val secondaryContainer = lerp(surface, secondarySeed, containerTint)
    val tertiaryContainer = lerp(surface, tertiarySeed, containerTint)
    val base = if (darkMode) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = strongestTextOn(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readableOn(seed, primaryContainer),
        secondary = secondary,
        onSecondary = strongestTextOn(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOn(secondarySeed, secondaryContainer),
        tertiary = tertiary,
        onTertiary = strongestTextOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = readableOn(tertiarySeed, tertiaryContainer),
        background = canvas,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHighest,
        onSurfaceVariant = mutedText,
        // Containers already carry the user's tint. Keep shared elevated dialogs from
        // adding another primary overlay that invalidates their checked text contrast.
        surfaceTint = surface,
        surfaceDim = canvas,
        surfaceBright = surfaceHighest,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        outline = readableOn(lerp(text, surface, 0.45f), accentSurface, 3f),
        outlineVariant = lerp(text, surface, 0.82f),
        inverseSurface = text,
        inverseOnSurface = canvas,
        inversePrimary = readableOn(seed, text)
    )
}

private fun colorContrast(a: Color, b: Color): Float =
    (max(a.luminance(), b.luminance()) + 0.05f) /
        (min(a.luminance(), b.luminance()) + 0.05f)

private fun strongestTextOn(background: Color): Color =
    if (colorContrast(Color.Black, background) >= colorContrast(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }

private fun readableOn(color: Color, background: Color, minimum: Float = 4.5f): Color {
    if (colorContrast(color, background) >= minimum) return color
    val destination = strongestTextOn(background)
    var low = 0f
    var high = 1f
    repeat(18) {
        val middle = (low + high) / 2f
        if (colorContrast(lerp(color, destination, middle), background) >= minimum) {
            high = middle
        } else {
            low = middle
        }
    }
    return lerp(color, destination, high)
}

/** Analogous accents follow the chosen hue; a neutral custom theme remains neutral. */
private fun Color.shiftHue(degrees: Float): Color {
    val highest = max(red, max(green, blue))
    val lowest = min(red, min(green, blue))
    val chroma = highest - lowest
    if (chroma < 0.001f) return this
    val lightness = (highest + lowest) / 2f
    val saturation = chroma / (1f - abs(2f * lightness - 1f))
    val hue = when (highest) {
        red -> 60f * (((green - blue) / chroma) % 6f)
        green -> 60f * (((blue - red) / chroma) + 2f)
        else -> 60f * (((red - green) / chroma) + 4f)
    }
    return Color.hsl(
        hue = (hue + degrees + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f)
    )
}
