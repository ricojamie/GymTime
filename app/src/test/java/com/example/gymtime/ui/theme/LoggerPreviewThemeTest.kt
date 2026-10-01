package com.example.gymtime.ui.theme

import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerPreviewThemeTest {
    @Test
    fun `filled action keeps exact custom accent with readable foreground`() {
        val channels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        for (red in channels) for (green in channels) for (blue in channels) {
            val seed = Color(red, green, blue, alpha = 0.3f)
            val action = loggerActionColors(seed)
            assertEquals(seed.copy(alpha = 1f), action.fill)
            assertTrue("Action contrast for $seed", contrast(action.onFill, action.fill) >= 4.5f)
        }
        val coral = Color(0xFFFF6B35)
        assertEquals(coral, loggerActionColors(coral).fill)
        assertEquals(Color.Black, loggerActionColors(coral).onFill)
    }

    @Test
    fun `custom accent extremes retain readable text in both modes`() {
        val channels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        for (darkMode in listOf(false, true)) {
            for (red in channels) for (green in channels) for (blue in channels) {
                val seed = Color(red, green, blue)
                val scheme = loggerPreviewColorScheme(seed, darkMode)
                val textPairs = listOf(
                    scheme.onBackground to scheme.background,
                    scheme.onSurface to scheme.surface,
                    scheme.onSurfaceVariant to scheme.surface,
                    scheme.onSurfaceVariant to scheme.surfaceContainerHighest,
                    scheme.primary to scheme.surface,
                    scheme.primary to scheme.background,
                    scheme.primary to scheme.surfaceContainerHigh,
                    scheme.primary to scheme.surfaceContainerHighest,
                    scheme.primary to scheme.surfaceColorAtElevation(6.dp),
                    scheme.secondary to scheme.surfaceContainerHighest,
                    scheme.tertiary to scheme.surfaceContainerHighest,
                    scheme.onPrimary to scheme.primary,
                    scheme.onSecondary to scheme.secondary,
                    scheme.onTertiary to scheme.tertiary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer
                )
                textPairs.forEachIndexed { index, (foreground, background) ->
                    val ratio = contrast(foreground, background)
                    assertTrue(
                        "seed=$seed dark=$darkMode pair=$index contrast=$ratio",
                        ratio >= 4.49f
                    )
                }
                assertEquals(darkMode, scheme.background.luminance() < 0.5f)
            }
        }
    }

    @Test
    fun `readable custom accent is preserved and neutral colors stay neutral`() {
        val blue = Color(0xFF1256A0)
        assertEquals(blue, loggerPreviewColorScheme(blue, darkMode = false).primary)
        for (seed in listOf(Color.Black, Color.White, Color.Gray)) {
            for (darkMode in listOf(false, true)) {
                val scheme = loggerPreviewColorScheme(seed, darkMode)
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { accent ->
                    assertEquals(accent.red, accent.green, 0.001f)
                    assertEquals(accent.green, accent.blue, 0.001f)
                }
            }
        }
    }

    @Test
    fun `custom color alpha cannot make controls transparent`() {
        val seed = Color(0.8f, 0.3f, 0.6f, 0.1f)
        val opaque = loggerPreviewColorScheme(seed.copy(alpha = 1f), darkMode = true)
        val translucent = loggerPreviewColorScheme(seed, darkMode = true)
        assertEquals(opaque.primary, translucent.primary)
        assertEquals(opaque.primaryContainer, translucent.primaryContainer)
        assertEquals(opaque.surface, translucent.surface)
        assertEquals(1f, translucent.primary.alpha, 0f)
    }

    private fun contrast(a: Color, b: Color): Float =
        (max(a.luminance(), b.luminance()) + 0.05f) /
            (min(a.luminance(), b.luminance()) + 0.05f)
}
