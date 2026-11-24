package com.example.gymtime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtime.R
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalIronLogSurfaces

/** Visual surface treatment presets for [IronLogMascotBadge]. */
enum class IronLogMascotStyle {
    /** Circular machined plate medallion with specular rim; ideal for headers and badges. */
    MEDALLION,

    /** Squircle telemetry stage crest with overhead spotlight; ideal for hero and summary headers. */
    HERO_CREST,

    /** Recessed subtle circular emblem with hairline rim; ideal for compact rows and footers. */
    SUBTLE
}

private val HeroCrestShape: Shape = RoundedCornerShape(percent = 28)

/**
 * Reusable IronLog swole chimp emblem rendered inside a machined obsidian/ceramic badge
 * with zero per-frame recomposition or draw allocations. Safe for both Dark ("Obsidian
 * Telemetry") and Light ("Machined Ceramic") themes.
 */
@Composable
fun IronLogMascotBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    style: IronLogMascotStyle = IronLogMascotStyle.MEDALLION,
    accentColor: Color = LocalIronLogSurfaces.current.accent,
    showGlow: Boolean = false,
    contentDescription: String? = null
) {
    val surfaces = LocalIronLogSurfaces.current
    val isDark = surfaces.isDark

    val shape = when (style) {
        IronLogMascotStyle.HERO_CREST -> HeroCrestShape
        IronLogMascotStyle.MEDALLION, IronLogMascotStyle.SUBTLE -> CircleShape
    }

    val backgroundBrush = remember(style, surfaces) {
        when (style) {
            IronLogMascotStyle.MEDALLION -> Brush.verticalGradient(
                colors = listOf(surfaces.plateElevatedTop, surfaces.plateBottom)
            )

            IronLogMascotStyle.HERO_CREST -> Brush.verticalGradient(
                colors = listOf(surfaces.telemetryStageTop, surfaces.telemetryStageBottom)
            )

            IronLogMascotStyle.SUBTLE -> Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(surfaces.plateTop, surfaces.wellFill)
                } else {
                    listOf(surfaces.plateTop, surfaces.plateBottom)
                }
            )
        }
    }

    val borderStroke = remember(style, surfaces, showGlow) {
        when (style) {
            IronLogMascotStyle.HERO_CREST -> IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = true,
                width = IronLogBorder.Hairline
            )

            IronLogMascotStyle.MEDALLION -> IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = showGlow,
                width = IronLogBorder.Hairline
            )

            IronLogMascotStyle.SUBTLE -> IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = false,
                width = IronLogBorder.Thin
            )
        }
    }

    val innerPadding = remember(size, style) {
        val ratio = if (style == IronLogMascotStyle.HERO_CREST) 0.08f else 0.10f
        (size * ratio).coerceAtLeast(2.dp)
    }

    val outerGlowModifier = if (showGlow) {
        Modifier.drawWithCache {
            val glowCenter = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
            val glowRadius = (this.size.minDimension * 0.78f).coerceAtLeast(1f)
            val peakAlpha = if (isDark) 0.28f else 0.18f
            val midAlpha = if (isDark) 0.09f else 0.05f
            val glowBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to accentColor.copy(alpha = peakAlpha),
                    0.55f to accentColor.copy(alpha = midAlpha),
                    1.0f to Color.Transparent
                ),
                center = glowCenter,
                radius = glowRadius
            )
            onDrawBehind {
                drawCircle(
                    brush = glowBrush,
                    radius = glowRadius,
                    center = glowCenter
                )
            }
        }
    } else {
        Modifier
    }

    val innerSpotlightModifier = if (style == IronLogMascotStyle.HERO_CREST || showGlow) {
        Modifier.drawWithCache {
            val spotlightCenter = Offset(this.size.width * 0.5f, this.size.height * 0.26f)
            val spotlightRadius = (this.size.maxDimension * 0.65f).coerceAtLeast(1f)
            val topAlpha = when {
                style == IronLogMascotStyle.HERO_CREST && isDark -> 0.16f
                style == IronLogMascotStyle.HERO_CREST -> 0.10f
                isDark -> 0.11f
                else -> 0.07f
            }
            val spotlightBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to accentColor.copy(alpha = topAlpha),
                    0.6f to accentColor.copy(alpha = topAlpha * 0.35f),
                    1.0f to Color.Transparent
                ),
                center = spotlightCenter,
                radius = spotlightRadius
            )
            onDrawBehind {
                drawRect(brush = spotlightBrush)
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(outerGlowModifier)
            .clip(shape)
            .background(brush = backgroundBrush)
            .then(innerSpotlightModifier)
            .border(border = borderStroke, shape = shape)
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ironlog_mascot),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Mascot Badges - Dark", showBackground = true, backgroundColor = 0xFF07080B)
@Composable
private fun IronLogMascotBadgeDarkPreview() {
    IronLogTheme(darkMode = true) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronLogMascotBadge(size = 36.dp, style = IronLogMascotStyle.SUBTLE)
            IronLogMascotBadge(size = 44.dp, style = IronLogMascotStyle.MEDALLION, showGlow = true)
            IronLogMascotBadge(size = 72.dp, style = IronLogMascotStyle.HERO_CREST, showGlow = true)
        }
    }
}

@Preview(name = "Mascot Badges - Light", showBackground = true, backgroundColor = 0xFFE9ECEF)
@Composable
private fun IronLogMascotBadgeLightPreview() {
    IronLogTheme(darkMode = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronLogMascotBadge(size = 36.dp, style = IronLogMascotStyle.SUBTLE)
            IronLogMascotBadge(size = 44.dp, style = IronLogMascotStyle.MEDALLION, showGlow = true)
            IronLogMascotBadge(size = 72.dp, style = IronLogMascotStyle.HERO_CREST, showGlow = true)
        }
    }
}
