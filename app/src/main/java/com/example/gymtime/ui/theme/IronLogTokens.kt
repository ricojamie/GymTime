package com.example.gymtime.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** Strict 4-step corner radius scale shared across IronLog surfaces and controls. */
object IronLogRadius {
    val Micro: Dp = 8.dp
    val Control: Dp = 12.dp
    val Card: Dp = 16.dp
    val Sheet: Dp = 24.dp

    val MicroShape: RoundedCornerShape = RoundedCornerShape(Micro)
    val ControlShape: RoundedCornerShape = RoundedCornerShape(Control)
    val CardShape: RoundedCornerShape = RoundedCornerShape(Card)
    val SheetShape: RoundedCornerShape = RoundedCornerShape(Sheet)
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
    val BottomNavClearance: Dp = 104.dp
}

/** Crisp 1.dp hairlines and subtle inset dividers for dark-first tonal surfaces. */
object IronLogBorder {
    val Hairline: Dp = 1.dp

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
}

/** Typographic helpers for numeric alignment and tracked section eyebrows. */
object IronLogType {
    fun tabular(style: TextStyle): TextStyle =
        style.copy(fontFeatureSettings = "tnum")

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
}

fun TextStyle.tabular(): TextStyle = IronLogType.tabular(this)
