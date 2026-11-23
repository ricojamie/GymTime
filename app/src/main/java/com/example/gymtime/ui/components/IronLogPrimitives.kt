package com.example.gymtime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalLoggerActionColors

/**
 * Tonal surface card with zero drop shadow and optional 1.dp hairline border
 * reserved for highlighted or interactive states.
 */
@Composable
fun IronLogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    showBorder: Boolean = highlighted || onClick != null,
    containerColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    },
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = IronLogBorder.borderColor(MaterialTheme.colorScheme, highlighted),
    shape: Shape = IronLogRadius.CardShape,
    contentPadding: PaddingValues = PaddingValues(IronLogSpace.CardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = remember(showBorder, borderColor) {
        if (showBorder) IronLogBorder.stroke(borderColor) else null
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = border
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = border
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    }
}

/**
 * Single 16.dp card container for grouping related rows with [IronLogDivider]
 * instead of nesting multiple floating boxes.
 */
@Composable
fun IronLogGroupedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    showBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = IronLogRadius.CardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = remember(showBorder, borderColor) {
        if (showBorder) IronLogBorder.stroke(borderColor) else null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/** Inset 1.dp hairline divider for separating rows inside [IronLogGroupedCard]. */
@Composable
fun IronLogDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = IronLogSpace.CardPadding,
    insetEnd: Dp = 0.dp,
    color: Color = IronLogBorder.dividerColor()
) {
    HorizontalDivider(
        modifier = modifier.padding(start = insetStart, end = insetEnd),
        thickness = IronLogBorder.Hairline,
        color = color
    )
}

/** Tracked uppercase section eyebrow with an optional trailing action slot. */
@Composable
fun IronLogSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val eyebrowStyle = IronLogType.sectionEyebrow()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = eyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingAction != null) {
            Spacer(modifier = Modifier.width(IronLogSpace.CompactGap))
            trailingAction()
        }
    }
}

/** Convenience overload pairing a tracked section eyebrow with a quiet text action. */
@Composable
fun IronLogSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true
) {
    IronLogSectionHeader(
        title = title,
        modifier = modifier,
        trailingAction = {
            TextButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/** High-emphasis filled action button using [LocalLoggerActionColors] with crisp press feedback. */
@Composable
fun IronLogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    shape: Shape = IronLogRadius.ControlShape
) {
    val action = LocalLoggerActionColors.current
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "primaryButtonScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = action.fill,
            contentColor = action.onFill,
            disabledContainerColor = if (loading) action.fill else scheme.surfaceContainerHighest,
            disabledContentColor = if (loading) action.onFill else scheme.onSurfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = action.onFill
            )
            Spacer(modifier = Modifier.width(IronLogSpace.CompactGap))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(IronLogSpace.CompactGap))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Secondary tonal or hairline-outlined action button with a 48.dp+ touch target. */
@Composable
fun IronLogSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    leadingIcon: ImageVector? = null,
    shape: Shape = IronLogRadius.ControlShape
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "secondaryButtonScale"
    )
    val buttonModifier = modifier
        .heightIn(min = IronLogSpace.TouchTargetMin)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(
                width = IronLogBorder.Hairline,
                color = if (enabled) scheme.outlineVariant else scheme.outlineVariant.copy(alpha = 0.45f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface,
                disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.48f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = buttonModifier
        ) {
            SecondaryButtonContent(text = text, leadingIcon = leadingIcon)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                disabledContainerColor = scheme.surfaceContainerLow,
                disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.48f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = buttonModifier
        ) {
            SecondaryButtonContent(text = text, leadingIcon = leadingIcon)
        }
    }
}

@Composable
private fun SecondaryButtonContent(
    text: String,
    leadingIcon: ImageVector?
) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(IronLogSpace.CompactGap))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Semantic tones for genuine live training states in [IronLogStatusChip]. */
enum class IronLogStatusTone {
    ACCENT,
    WARMUP,
    PR,
    SUPERSET,
    ACTIVE,
    NEUTRAL,
    ERROR
}

/**
 * Restrained Micro-radius semantic tag reserved for genuine live states
 * (`WARMUP`, `PR`, `SUPERSET`, `ACTIVE`), rather than passive metadata.
 */
@Composable
fun IronLogStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: IronLogStatusTone = IronLogStatusTone.ACCENT,
    icon: ImageVector? = null,
    uppercase: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    val (containerColor, contentColor, borderColor) = when (tone) {
        IronLogStatusTone.ACCENT, IronLogStatusTone.ACTIVE -> Triple(
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
            scheme.primary.copy(alpha = 0.35f)
        )
        IronLogStatusTone.PR -> Triple(
            action.fill,
            action.onFill,
            action.fill
        )
        IronLogStatusTone.SUPERSET -> Triple(
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
            scheme.secondary.copy(alpha = 0.30f)
        )
        IronLogStatusTone.WARMUP -> Triple(
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
            scheme.tertiary.copy(alpha = 0.30f)
        )
        IronLogStatusTone.NEUTRAL -> Triple(
            scheme.surfaceContainerHighest,
            scheme.onSurface,
            scheme.outlineVariant
        )
        IronLogStatusTone.ERROR -> Triple(
            scheme.errorContainer,
            scheme.onErrorContainer,
            scheme.error.copy(alpha = 0.35f)
        )
    }

    Surface(
        modifier = modifier,
        shape = IronLogRadius.MicroShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(IronLogBorder.Hairline, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = if (uppercase) label.uppercase() else label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.em
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Data model for a single column inside [IronLogMetricRow]. */
@Immutable
data class IronLogMetric(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val emphasized: Boolean = false
)

/** Tabular-numeral metric readout with eyebrow label and optional supporting line. */
@Composable
fun IronLogMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    emphasized: Boolean = false,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = IronLogType.sectionEyebrow(),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (emphasized) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!supporting.isNullOrBlank()) {
            Text(
                text = supporting,
                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Horizontal strip of [IronLogMetricCell]s separated by vertical 1.dp hairlines. */
@Composable
fun IronLogMetricRow(
    metrics: List<IronLogMetric>,
    modifier: Modifier = Modifier
) {
    if (metrics.isEmpty()) return
    val dividerColor = IronLogBorder.dividerColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metrics.forEachIndexed { index, metric ->
            val startPad = if (index == 0) 0.dp else IronLogSpace.ItemGap
            val endPad = if (index == metrics.lastIndex) 0.dp else IronLogSpace.ItemGap
            IronLogMetricCell(
                label = metric.label,
                value = metric.value,
                supporting = metric.supporting,
                emphasized = metric.emphasized,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = startPad, end = endPad)
            )
            if (index < metrics.lastIndex) {
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    thickness = IronLogBorder.Hairline,
                    color = dividerColor
                )
            }
        }
    }
}

/** Unified bottom sheet header with an accessible 48.dp close touch target. */
@Composable
fun IronLogSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeContentDescription: String = "Close"
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = closeContentDescription,
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

/** Calm, centered empty state without decorative icon boxes or visual clutter. */
@Composable
fun IronLogEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            IronLogPrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(min = 180.dp)
            )
        }
    }
}
