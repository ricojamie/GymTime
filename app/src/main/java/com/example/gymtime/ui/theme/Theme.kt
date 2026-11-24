package com.example.gymtime.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The selected accent stays vivid on filled actions; small text uses the contrast-safe tonal scheme. */
@Immutable
data class LoggerActionColors(val fill: Color, val onFill: Color)

/**
 * Physical material surface & rim-lighting tokens for Dark ("Obsidian Telemetry") and
 * Light ("Machined Ceramic") palettes, dynamically tinted from the user's active accent.
 */
@Immutable
data class IronLogSurfacePalette(
    val isDark: Boolean,
    val accent: Color,
    val canvas: Color,
    val canvasBottom: Color,
    val plateTop: Color,
    val plateBottom: Color,
    val plateElevatedTop: Color,
    val plateElevatedBottom: Color,
    val rimTop: Color,
    val rimMid: Color,
    val rimBottom: Color,
    val rimAccentTop: Color,
    val wellFill: Color,
    val wellInnerShadow: Color,
    val wellCatchlight: Color,
    val telemetryStageTop: Color,
    val telemetryStageBottom: Color,
    val telemetrySpotlight: Color,
    val keycapTop: Color,
    val keycapBottom: Color,
    val keycapPressedTop: Color,
    val keycapPressedBottom: Color,
    val keycapRimTop: Color,
    val keycapShadowLip: Color,
    val glassDockFill: Color,
    val glassDockRimTop: Color,
    val ledGlow: Color
)

val LocalLoggerActionColors = staticCompositionLocalOf { loggerActionColors(PrimaryAccent) }

val LocalIronLogSurfaces = staticCompositionLocalOf {
    ironLogSurfacePalette(PrimaryAccent, darkMode = true)
}

val LocalGradientColors = staticCompositionLocalOf<Pair<Color, Color>> {
    error("No gradient colors provided")
}

internal fun loggerActionColors(accent: Color): LoggerActionColors {
    val fill = accent.copy(alpha = 1f)
    return LoggerActionColors(fill = fill, onFill = strongestTextOn(fill))
}

internal fun ironLogSurfacePalette(accent: Color, darkMode: Boolean): IronLogSurfacePalette {
    val seed = accent.copy(alpha = 1f)
    return if (darkMode) {
        val canvas = lerp(IronLogDarkCanvasNeutral, seed, 0.02f)
        val canvasBottom = lerp(Color(0xFF040507), seed, 0.015f)
        val plateTop = lerp(Color(0xFF171A21), seed, 0.03f)
        val plateBottom = lerp(Color(0xFF0E1015), seed, 0.015f)
        val plateElevatedTop = lerp(Color(0xFF1E222B), seed, 0.045f)
        // Check against the brightest dark surface so accent text maintains >= 4.5:1 across all plates/wells.
        val accentReadable = readableOn(seed, plateElevatedTop)
        IronLogSurfacePalette(
            isDark = true,
            accent = accentReadable,
            canvas = canvas,
            canvasBottom = canvasBottom,
            plateTop = plateTop,
            plateBottom = plateBottom,
            plateElevatedTop = plateElevatedTop,
            plateElevatedBottom = lerp(Color(0xFF13161D), seed, 0.025f),
            rimTop = Color.White.copy(alpha = 0.14f),
            rimMid = Color.White.copy(alpha = 0.04f),
            rimBottom = Color.Black.copy(alpha = 0.45f),
            rimAccentTop = accentReadable.copy(alpha = 0.48f),
            wellFill = lerp(Color(0xFF0A0C10), seed, 0.015f),
            wellInnerShadow = Color.Black.copy(alpha = 0.55f),
            wellCatchlight = Color.White.copy(alpha = 0.06f),
            telemetryStageTop = lerp(Color(0xFF131720), seed, 0.04f),
            telemetryStageBottom = lerp(Color(0xFF0B0D12), seed, 0.02f),
            telemetrySpotlight = seed.copy(alpha = 0.18f),
            keycapTop = lerp(Color(0xFF232730), seed, 0.025f),
            keycapBottom = lerp(Color(0xFF171A20), seed, 0.015f),
            keycapPressedTop = lerp(Color(0xFF13161B), seed, 0.015f),
            keycapPressedBottom = lerp(Color(0xFF1F232B), seed, 0.025f),
            keycapRimTop = Color.White.copy(alpha = 0.16f),
            keycapShadowLip = Color.Black.copy(alpha = 0.60f),
            glassDockFill = lerp(Color(0xFF0D0F14), seed, 0.02f).copy(alpha = 0.88f),
            glassDockRimTop = Color.White.copy(alpha = 0.18f),
            ledGlow = accentReadable
        )
    } else {
        val canvas = lerp(IronLogLightCanvasNeutral, seed, 0.02f)
        val canvasBottom = lerp(Color(0xFFDFE3E8), seed, 0.02f)
        val plateTop = Color.White
        val plateBottom = lerp(Color(0xFFF2F4F7), seed, 0.015f)
        val wellFill = lerp(Color(0xFFDFE3E8), seed, 0.015f)
        // Check against the darkest light surface (wellFill) so accent text maintains >= 4.5:1 across plates, canvas, and wells.
        val accentReadable = readableOn(seed, wellFill)
        IronLogSurfacePalette(
            isDark = false,
            accent = accentReadable,
            canvas = canvas,
            canvasBottom = canvasBottom,
            plateTop = plateTop,
            plateBottom = plateBottom,
            plateElevatedTop = Color.White,
            plateElevatedBottom = lerp(Color(0xFFEDF0F4), seed, 0.03f),
            rimTop = Color.White.copy(alpha = 0.90f),
            rimMid = Color(0xFFDCE1E7),
            rimBottom = Color(0xFFC7CDD4),
            rimAccentTop = accentReadable.copy(alpha = 0.55f),
            wellFill = wellFill,
            wellInnerShadow = Color.Black.copy(alpha = 0.10f),
            wellCatchlight = Color.White.copy(alpha = 0.85f),
            telemetryStageTop = Color.White,
            telemetryStageBottom = lerp(Color(0xFFF3F5F8), seed, 0.025f),
            telemetrySpotlight = accentReadable.copy(alpha = 0.14f),
            keycapTop = Color.White,
            keycapBottom = lerp(Color(0xFFE8ECF1), seed, 0.015f),
            keycapPressedTop = lerp(Color(0xFFDCDFE5), seed, 0.015f),
            keycapPressedBottom = lerp(Color(0xFFF5F7FA), seed, 0.01f),
            keycapRimTop = Color.White.copy(alpha = 0.95f),
            keycapShadowLip = Color(0xFFB8BFC8),
            glassDockFill = Color(0xFFF7F8FA).copy(alpha = 0.90f),
            glassDockRimTop = Color.White.copy(alpha = 0.92f),
            ledGlow = accentReadable
        )
    }
}

/** Pure palette construction covering all preset and arbitrary custom hex colors, including black and white. */
internal fun ironLogColorScheme(accent: Color, darkMode: Boolean): ColorScheme {
    val seed = accent.copy(alpha = 1f)
    val neutral = if (darkMode) IronLogDarkCanvasNeutral else IronLogLightCanvasNeutral
    val canvas = lerp(neutral, seed, if (darkMode) 0.02f else 0.025f)
    val surface = lerp(if (darkMode) IronLogDarkSurfaceBase else Color.White, seed, 0.025f)
    val surfaceLow = lerp(canvas, surface, 0.55f)
    val surfaceHigh = lerp(surface, seed, if (darkMode) 0.08f else 0.06f)
    val surfaceHighest = lerp(surface, seed, if (darkMode) 0.12f else 0.10f)
    // A nearly black seed can darken a dark-mode container; a white seed can brighten
    // a light-mode one. Choose the restrictive surface by luminance, not its token name.
    val accentSurface = listOf(canvas, surface, surfaceHigh, surfaceHighest).let { surfaces ->
        if (darkMode) surfaces.maxBy { it.luminance() } else surfaces.minBy { it.luminance() }
    }
    val text = if (darkMode) IronLogDarkTextPrimary else IronLogLightTextPrimary
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

@Composable
fun IronLogTheme(
    appColorScheme: AppColorScheme = ThemeColors.LimeGreen,
    darkMode: Boolean = true,
    themeFontKey: String = ThemeFontOption.BEBAS_NEUE.storageKey,
    customFontUri: String? = null,
    content: @Composable () -> Unit
) {
    val typography = rememberAppTypography(
        themeFontKey = themeFontKey,
        customFontUri = customFontUri
    )
    val scheme = remember(appColorScheme.primaryAccent, darkMode) {
        ironLogColorScheme(appColorScheme.primaryAccent, darkMode)
    }
    val actionColors = remember(appColorScheme.primaryAccent) {
        loggerActionColors(appColorScheme.primaryAccent)
    }
    val surfaces = remember(appColorScheme.primaryAccent, darkMode) {
        ironLogSurfacePalette(appColorScheme.primaryAccent, darkMode)
    }
    val gradientColors = remember(surfaces) {
        surfaces.canvas to surfaces.canvasBottom
    }
    val shapes = remember { IronLogShapes }
    val reducedMotion = rememberReducedMotion()
    val haptics = rememberIronHaptics()

    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        LocalLoggerActionColors provides actionColors,
        LocalIronLogSurfaces provides surfaces,
        LocalReducedMotion provides reducedMotion,
        LocalIronHaptics provides haptics
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
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
