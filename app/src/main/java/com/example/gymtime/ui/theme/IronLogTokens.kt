package com.example.gymtime.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Corner radius scale shared across IronLog surfaces, plates, keycaps, and capsules. */
object IronLogRadius {
    // Core 4-step Material3 shape scale (preserved for existing contracts & tests)
    val Micro: Dp = 8.dp
    val Control: Dp = 12.dp
    val Card: Dp = 16.dp
    val Sheet: Dp = 24.dp

    // Obsidian Telemetry / Machined Ceramic instrument radius tokens
    val Tight: Dp = 6.dp
    val Keycap: Dp = 10.dp
    val Plate: Dp = 18.dp
    val Capsule: Dp = 999.dp

    val MicroShape: RoundedCornerShape = RoundedCornerShape(Micro)
    val ControlShape: RoundedCornerShape = RoundedCornerShape(Control)
    val CardShape: RoundedCornerShape = RoundedCornerShape(Card)
    val SheetShape: RoundedCornerShape = RoundedCornerShape(Sheet)

    val TightShape: RoundedCornerShape = RoundedCornerShape(Tight)
    val KeycapShape: RoundedCornerShape = RoundedCornerShape(Keycap)
    val PlateShape: RoundedCornerShape = RoundedCornerShape(Plate)
    val CapsuleShape: RoundedCornerShape = RoundedCornerShape(Capsule)
}

/** Material3 shape scale wired directly into [IronLogTheme]. */
val IronLogShapes: Shapes = Shapes(
    extraSmall = IronLogRadius.MicroShape,
    small = IronLogRadius.MicroShape,
    medium = IronLogRadius.ControlShape,
    large = IronLogRadius.CardShape,
    extraLarge = IronLogRadius.SheetShape
)

/** Disciplined spatial rhythm for screens, cards, rows, and touch targets. */
object IronLogSpace {
    val ScreenHorizontal: Dp = 16.dp
    val SectionGap: Dp = 20.dp
    val ItemGap: Dp = 12.dp
    val CompactGap: Dp = 8.dp
    val CardPadding: Dp = 16.dp
    val RowVertical: Dp = 14.dp
    val TouchTargetMin: Dp = 48.dp
    val BottomNavClearance: Dp = 148.dp
}

/** Crisp hairlines, structural dividers, and directional overhead specular rims. */
object IronLogBorder {
    val Hairline: Dp = 1.dp
    val Thin: Dp = 0.75.dp

    fun dividerColor(colorScheme: ColorScheme): Color =
        colorScheme.outlineVariant.copy(alpha = 0.72f)

    @Composable
    fun dividerColor(): Color = dividerColor(MaterialTheme.colorScheme)

    fun borderColor(
        colorScheme: ColorScheme,
        highlighted: Boolean = false
    ): Color = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.65f)
    } else {
        colorScheme.outlineVariant
    }

    fun stroke(
        color: Color,
        width: Dp = Hairline
    ): BorderStroke = BorderStroke(width, color)

    fun specularRimBrush(
        surfaces: IronLogSurfacePalette,
        highlighted: Boolean = false
    ): Brush = Brush.verticalGradient(
        colors = listOf(
            if (highlighted) surfaces.rimAccentTop else surfaces.rimTop,
            surfaces.rimMid,
            surfaces.rimBottom
        )
    )

    fun specularRimStroke(
        surfaces: IronLogSurfacePalette,
        highlighted: Boolean = false,
        width: Dp = Thin
    ): BorderStroke = BorderStroke(
        width = width,
        brush = specularRimBrush(surfaces = surfaces, highlighted = highlighted)
    )
}

/** Dual-voice typographic helpers for sculptural numerals, tabular alignment, and technical eyebrows. */
object IronLogType {
    fun tabular(style: TextStyle): TextStyle =
        style.copy(fontFeatureSettings = "tnum")

    fun monumentalNumeral(style: TextStyle): TextStyle =
        style.copy(
            fontFeatureSettings = "tnum",
            letterSpacing = (-0.02).em
        )

    @Composable
    fun sectionEyebrow(
        style: TextStyle = MaterialTheme.typography.labelSmall
    ): TextStyle = style.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em
    )

    fun sectionEyebrow(typography: Typography): TextStyle =
        typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em
        )

    fun technicalEyebrow(style: TextStyle = TextStyle.Default): TextStyle =
        style.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.12.em,
            fontFeatureSettings = "tnum"
        )

    fun telemetryLabel(style: TextStyle = TextStyle.Default): TextStyle =
        style.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.06.em,
            fontFeatureSettings = "tnum"
        )
}

fun TextStyle.tabular(): TextStyle = IronLogType.tabular(this)
