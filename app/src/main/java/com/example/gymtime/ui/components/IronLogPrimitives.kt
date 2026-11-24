package com.example.gymtime.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.ui.theme.telemetrySpotlight
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Machined obsidian/ceramic vertical-gradient plate card with zero drop shadow,
 * directional specular rim border, optional localized accent spotlight, and
 * zero-recomposition [MachinedPressIndication] when clickable.
 */
@Composable
fun IronLogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    showBorder: Boolean = highlighted || onClick != null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = Color.Unspecified,
    shape: Shape = IronLogRadius.CardShape,
    contentPadding: PaddingValues = PaddingValues(IronLogSpace.CardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    spotlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current

    val backgroundBrush = remember(containerColor, highlighted, spotlight, surfaces) {
        if (containerColor != Color.Unspecified) {
            SolidColor(containerColor)
        } else {
            val top = when {
                spotlight -> surfaces.telemetryStageTop
                highlighted -> surfaces.plateElevatedTop
                else -> surfaces.plateTop
            }
            val bottom = when {
                spotlight -> surfaces.telemetryStageBottom
                highlighted -> surfaces.plateElevatedBottom
                else -> surfaces.plateBottom
            }
            Brush.verticalGradient(colors = listOf(top, bottom))
        }
    }

    val borderStroke = remember(showBorder, borderColor, highlighted, spotlight, surfaces) {
        if (!showBorder) {
            null
        } else if (borderColor != Color.Unspecified) {
            IronLogBorder.stroke(borderColor)
        } else {
            IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = highlighted || spotlight,
                width = IronLogBorder.Hairline
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.975f,
            highlightAlpha = 0.06f,
            cornerRadius = IronLogRadius.Card
        )
    }

    val surfaceModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = pressIndication,
                    enabled = enabled,
                    onClick = {
                        haptics.tick()
                        onClick()
                    }
                )
            } else {
                Modifier.semantics { isTraversalGroup = true }
            }
        )
        .clip(shape)
        .background(brush = backgroundBrush)
        .then(
            if (spotlight) {
                Modifier
                    .telemetrySpotlight(
                        accent = surfaces.accent,
                        vignetteAlpha = if (surfaces.isDark) 0.08f else 0f
                    )
                    .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
            } else {
                Modifier
            }
        )
        .then(
            if (borderStroke != null) {
                Modifier.border(border = borderStroke, shape = shape)
            } else {
                Modifier
            }
        )

    Box(modifier = surfaceModifier) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
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
 * Single machined plate container for grouping related rows with [IronLogDivider]
 * instead of nesting multiple floating boxes.
 */
@Composable
fun IronLogGroupedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    showBorder: Boolean = false,
    borderColor: Color = Color.Unspecified,
    shape: Shape = IronLogRadius.CardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = LocalIronLogSurfaces.current

    val backgroundBrush = remember(containerColor, surfaces) {
        if (containerColor != Color.Unspecified) {
            SolidColor(containerColor)
        } else {
            Brush.verticalGradient(colors = listOf(surfaces.plateTop, surfaces.plateBottom))
        }
    }

    val borderStroke = remember(showBorder, borderColor, surfaces) {
        if (!showBorder) {
            null
        } else if (borderColor != Color.Unspecified) {
            IronLogBorder.stroke(borderColor)
        } else {
            IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = false,
                width = IronLogBorder.Hairline
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush = backgroundBrush)
            .then(
                if (borderStroke != null) {
                    Modifier.border(border = borderStroke, shape = shape)
                } else {
                    Modifier
                }
            )
            .semantics { isTraversalGroup = true }
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
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

/**
 * High-emphasis sculpted primary actuator using [LocalLoggerActionColors],
 * asymmetric [MachinedPressIndication], and [IronHaptics] feedback.
 */
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
    val haptics = LocalIronHaptics.current
    val isInteractive = enabled && !loading

    val containerColor = if (enabled || loading) action.fill else scheme.surfaceContainerHighest
    val contentColor = if (enabled || loading) action.onFill else scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val bevelRim = remember(enabled, loading, scheme) {
        if (enabled || loading) {
            BorderStroke(
                width = IronLogBorder.Hairline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.28f)
                    )
                )
            )
        } else {
            BorderStroke(IronLogBorder.Hairline, scheme.outlineVariant.copy(alpha = 0.4f))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.965f,
            highlightAlpha = 0.14f,
            cornerRadius = IronLogRadius.Control
        )
    }

    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                enabled = isInteractive,
                role = Role.Button,
                onClick = {
                    haptics.keyTap()
                    onClick()
                }
            )
            .clip(shape)
            .background(containerColor)
            .drawBehind {
                if (enabled || loading) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f)
                            )
                        )
                    )
                }
            }
            .border(border = bevelRim, shape = shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
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
    }
}

/**
 * Secondary machined keycap or directional-hairline outlined action button
 * with a 48.dp+ touch target and [MachinedPressIndication].
 */
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current

    val contentColor = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.48f)
    val backgroundBrush = remember(outlined, enabled, surfaces, scheme) {
        when {
            outlined -> SolidColor(Color.Transparent)
            enabled -> Brush.verticalGradient(listOf(surfaces.keycapTop, surfaces.keycapBottom))
            else -> SolidColor(scheme.surfaceContainerLow)
        }
    }
    val borderStroke = remember(outlined, enabled, surfaces, scheme) {
        if (!enabled) {
            BorderStroke(IronLogBorder.Hairline, scheme.outlineVariant.copy(alpha = 0.40f))
        } else if (outlined) {
            IronLogBorder.specularRimStroke(surfaces, highlighted = false, width = IronLogBorder.Hairline)
        } else {
            BorderStroke(
                width = IronLogBorder.Thin,
                brush = Brush.verticalGradient(listOf(surfaces.keycapRimTop, surfaces.keycapShadowLip))
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.97f,
            highlightAlpha = 0.08f,
            cornerRadius = IronLogRadius.Control
        )
    }

    Box(
        modifier = modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .clip(shape)
            .background(brush = backgroundBrush)
            .border(border = borderStroke, shape = shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                SecondaryButtonContent(text = text, leadingIcon = leadingIcon)
            }
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

// ============================================================================
// Obsidian Telemetry / Machined Ceramic Instrument Primitives
// ============================================================================

/**
 * Physically recessed dark/ceramic chassis well with an inset top shadow,
 * faint bottom edge catchlight, and optional active accent perimeter glow.
 */
@Composable
fun IronLogRecessedWell(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = IronLogRadius.KeycapShape,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme

    val restingBorder = remember(surfaces) {
        if (surfaces.isDark) Color.Black.copy(alpha = 0.72f) else Color(0xFFBFC5CE)
    }
    val borderColorState = animateColorAsState(
        targetValue = if (active) accentColor.copy(alpha = 0.88f) else restingBorder,
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "recessedWellBorder"
    )
    val borderWidth = if (active) 1.5.dp else IronLogBorder.Hairline

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.985f,
            highlightAlpha = 0.05f,
            cornerRadius = IronLogRadius.Keycap
        )
    }

    val wellModifier = modifier
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = pressIndication,
                    enabled = enabled,
                    onClick = {
                        haptics.tick()
                        onClick()
                    }
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(surfaces.wellFill)
        .drawWithCache {
            val shadowHeight = 5.dp.toPx().coerceAtMost(size.height * 0.35f)
            val topShadow = Brush.verticalGradient(
                colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                startY = 0f,
                endY = shadowHeight
            )
            val catchlightStroke = 0.75.dp.toPx()
            val borderOutline = shape.createOutline(size, layoutDirection, this)
            val insetBorderStrokeWidth = borderWidth.toPx() * 2f
            val activeHalo = if (active) {
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (surfaces.isDark) 0.12f else 0.08f),
                        Color.Transparent
                    )
                )
            } else {
                null
            }
            onDrawWithContent {
                if (activeHalo != null) {
                    drawRect(brush = activeHalo)
                }
                drawRect(brush = topShadow, size = Size(size.width, shadowHeight))
                drawLine(
                    color = surfaces.wellCatchlight,
                    start = Offset(0f, size.height - catchlightStroke * 0.5f),
                    end = Offset(size.width, size.height - catchlightStroke * 0.5f),
                    strokeWidth = catchlightStroke
                )
                drawContent()
                drawOutline(
                    outline = borderOutline,
                    color = borderColorState.value,
                    style = Stroke(width = insetBorderStrokeWidth)
                )
            }
        }
        .padding(contentPadding)

    Box(
        modifier = wellModifier,
        contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}

/** Haptic profile options for [IronLogSculptedKeycap]. */
enum class KeycapHaptic {
    KEY_TAP,
    TICK,
    CONFIRM,
    NONE
}

/**
 * Tactile sculpted keycap with vertical bevel gradient, specular top rim,
 * draw-phase depressed gradient inversion on press (zero recomposition),
 * [MachinedPressIndication] (`0.93f`), and [IronHaptics] feedback.
 */
@Composable
fun IronLogSculptedKeycap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accented: Boolean = false,
    hapticType: KeycapHaptic = KeycapHaptic.KEY_TAP,
    shape: Shape = IronLogRadius.KeycapShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val surfaces = LocalIronLogSurfaces.current
    val action = LocalLoggerActionColors.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressedState = interactionSource.collectIsPressedAsState()
    val pressIndication = remember(accented) {
        MachinedPressIndication(
            pressedScale = 0.93f,
            highlightAlpha = if (accented) 0.14f else 0.06f,
            cornerRadius = IronLogRadius.Keycap
        )
    }

    val contentColor = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        accented -> action.onFill
        else -> scheme.onSurface
    }

    val unpressedFill = remember(accented, enabled, surfaces, action, scheme) {
        when {
            !enabled -> SolidColor(scheme.surfaceContainerLow)
            accented -> SolidColor(action.fill)
            else -> Brush.verticalGradient(listOf(surfaces.keycapTop, surfaces.keycapBottom))
        }
    }
    val pressedFill = remember(accented, enabled, surfaces, action, scheme) {
        when {
            !enabled -> SolidColor(scheme.surfaceContainerLow)
            accented -> SolidColor(lerp(action.fill, Color.Black, 0.12f))
            else -> Brush.verticalGradient(listOf(surfaces.keycapPressedTop, surfaces.keycapPressedBottom))
        }
    }
    val unpressedBorder = remember(accented, enabled, surfaces, scheme) {
        if (!enabled) {
            BorderStroke(IronLogBorder.Hairline, scheme.outlineVariant.copy(alpha = 0.35f))
        } else if (accented) {
            BorderStroke(
                width = IronLogBorder.Hairline,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.25f))
                )
            )
        } else {
            BorderStroke(
                width = IronLogBorder.Thin,
                brush = Brush.verticalGradient(listOf(surfaces.keycapRimTop, surfaces.keycapShadowLip))
            )
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = IronLogSpace.TouchTargetMin, minHeight = IronLogSpace.TouchTargetMin)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    when (hapticType) {
                        KeycapHaptic.KEY_TAP -> haptics.keyTap()
                        KeycapHaptic.TICK -> haptics.tick()
                        KeycapHaptic.CONFIRM -> haptics.confirm()
                        KeycapHaptic.NONE -> Unit
                    }
                    onClick()
                }
            )
            .clip(shape)
            .drawBehind {
                val isPressed = pressedState.value && enabled
                drawRect(brush = if (isPressed) pressedFill else unpressedFill)
                if (accented && enabled && !isPressed) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                }
            }
            .border(border = unpressedBorder, shape = shape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** Rich option model for [IronLogSegmentedControl]. */
@Immutable
data class IronLogSegmentOption(
    val label: String,
    val badge: String? = null,
    val enabled: Boolean = true
)

/**
 * Recessed machined track with a spring-gliding active plate thumb ([IronMotion.MagneticDetent])
 * and [IronHaptics.segmentTick] feedback.
 */
@Composable
fun IronLogSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String?> = emptyList(),
    enabled: Boolean = true,
    shape: Shape = IronLogRadius.CapsuleShape
) {
    val segmentOptions = remember(options, badges) {
        options.mapIndexed { index, label ->
            IronLogSegmentOption(
                label = label,
                badge = badges.getOrNull(index)
            )
        }
    }
    IronLogSegmentedControl(
        options = segmentOptions,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        shape = shape
    )
}

/** Overload accepting [IronLogSegmentOption] items with optional inline tags/badges. */
@Composable
fun IronLogSegmentedControl(
    options: List<IronLogSegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IronLogRadius.CapsuleShape
) {
    if (options.isEmpty()) return

    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val segmentBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    var hasInitializedThumb by remember { mutableStateOf(false) }

    val targetBounds = segmentBounds[safeIndex]
    LaunchedEffect(safeIndex, targetBounds, reducedMotion) {
        val (targetLeft, targetWidth) = targetBounds ?: return@LaunchedEffect
        if (!hasInitializedThumb || reducedMotion) {
            thumbX.snapTo(targetLeft)
            thumbW.snapTo(targetWidth)
            hasInitializedThumb = true
        } else {
            coroutineScope {
                launch { thumbX.animateTo(targetLeft, IronMotion.MagneticDetentPx) }
                launch { thumbW.animateTo(targetWidth, IronMotion.MagneticDetentPx) }
            }
        }
    }

    val trackBorderColor = remember(surfaces) {
        if (surfaces.isDark) Color.Black.copy(alpha = 0.72f) else Color(0xFFBFC5CE)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaces.wellFill)
            .border(width = IronLogBorder.Hairline, color = trackBorderColor, shape = shape)
            .drawWithCache {
                val insetPx = 3.dp.toPx()
                val shadowHeight = 4.dp.toPx().coerceAtMost(size.height * 0.35f)
                val topShadowBrush = Brush.verticalGradient(
                    colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                    startY = 0f,
                    endY = shadowHeight
                )
                val thumbTop = insetPx
                val thumbHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
                val corner = (thumbHeight * 0.5f).coerceAtMost(
                    if (shape == IronLogRadius.CapsuleShape) thumbHeight * 0.5f else 10.dp.toPx()
                )
                val thumbCorner = CornerRadius(corner, corner)
                val thumbFillBrush = Brush.verticalGradient(
                    colors = listOf(surfaces.plateElevatedTop, surfaces.plateElevatedBottom),
                    startY = thumbTop,
                    endY = thumbTop + thumbHeight
                )
                val thumbRimBrush = Brush.verticalGradient(
                    colors = listOf(surfaces.rimAccentTop, surfaces.rimMid, surfaces.rimBottom),
                    startY = thumbTop,
                    endY = thumbTop + thumbHeight
                )
                val thumbRimStroke = Stroke(width = IronLogBorder.Thin.toPx())

                onDrawWithContent {
                    drawRect(
                        brush = topShadowBrush,
                        size = Size(size.width, shadowHeight)
                    )

                    val activeBounds = segmentBounds[safeIndex]
                    val x = if (hasInitializedThumb) thumbX.value else (activeBounds?.first ?: 0f)
                    val w = if (hasInitializedThumb) thumbW.value else (activeBounds?.second ?: 0f)
                    if (w > 1f) {
                        val topLeft = Offset(insetPx + x, thumbTop)
                        val thumbSize = Size(w, thumbHeight)
                        drawRoundRect(
                            brush = thumbFillBrush,
                            topLeft = topLeft,
                            size = thumbSize,
                            cornerRadius = thumbCorner
                        )
                        drawRoundRect(
                            brush = thumbRimBrush,
                            topLeft = topLeft,
                            size = thumbSize,
                            cornerRadius = thumbCorner,
                            style = thumbRimStroke
                        )
                    }

                    drawContent()
                }
            }
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == safeIndex
                val isOptionEnabled = enabled && option.enabled
                val textColorState = animateColorAsState(
                    targetValue = when {
                        !isOptionEnabled -> scheme.onSurfaceVariant.copy(alpha = 0.42f)
                        isSelected -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                    animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
                    label = "segmentLabelColor_$index"
                )
                val labelStyle = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .onPlaced { coords ->
                            val left = coords.positionInParent().x
                            val width = coords.size.width.toFloat()
                            val prev = segmentBounds[index]
                            if (prev == null || prev.first != left || prev.second != width) {
                                segmentBounds[index] = left to width
                            }
                        }
                        .clip(shape)
                        .selectable(
                            selected = isSelected,
                            enabled = isOptionEnabled,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (index != safeIndex) {
                                    haptics.segmentTick()
                                    onSelect(index)
                                }
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                    }
                    BasicText(
                        text = option.label,
                        style = labelStyle,
                        color = { textColorState.value },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!option.badge.isNullOrBlank()) {
                        Text(
                            text = option.badge,
                            style = IronLogType.telemetryLabel(),
                            color = if (isSelected) surfaces.accent else scheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Specular-rimmed obsidian-inverted filter pill with [MachinedPressIndication],
 * [IronHaptics.segmentTick], `48.dp` accessible touch target, and `Role.Tab` semantics.
 */
@Composable
fun IronLogFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    showLedPip: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.95f,
            highlightAlpha = 0f,
            cornerRadius = IronLogRadius.Capsule
        )
    }

    val contentColorState = animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
            selected -> scheme.inverseOnSurface
            else -> scheme.onSurfaceVariant
        },
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "filterPillContent"
    )
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
    )

    val containerBrush = remember(selected, enabled, surfaces, scheme) {
        when {
            !enabled -> SolidColor(scheme.surfaceContainerLow)
            selected -> SolidColor(scheme.inverseSurface)
            else -> Brush.verticalGradient(listOf(surfaces.plateTop, surfaces.plateBottom))
        }
    }

    val borderStroke = remember(selected, enabled, surfaces, scheme) {
        when {
            !enabled -> BorderStroke(IronLogBorder.Hairline, scheme.outlineVariant.copy(alpha = 0.35f))
            selected -> BorderStroke(IronLogBorder.Hairline, scheme.inverseSurface)
            else -> IronLogBorder.specularRimStroke(surfaces, highlighted = false, width = IronLogBorder.Hairline)
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = IronLogSpace.TouchTargetMin,
                minHeight = IronLogSpace.TouchTargetMin
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = pressIndication,
                onClick = {
                    haptics.segmentTick()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(IronLogRadius.CapsuleShape)
                .background(brush = containerBrush)
                .border(border = borderStroke, shape = IronLogRadius.CapsuleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showLedPip && selected) {
                IronLogLedPip(color = scheme.inversePrimary, size = 5.dp)
            }
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides contentColorState.value) {
                    leadingIcon()
                }
            }
            BasicText(
                text = label,
                style = labelStyle,
                color = { contentColorState.value },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Glowing 6.dp accent status pip with a soft radial halo and optional lifecycle-aware
 * atmospheric breathing pulse when `pulsing = true`.
 */
@Composable
fun IronLogLedPip(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 6.dp,
    pulsing: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            isStarted = true
            try {
                awaitCancellation()
            } finally {
                isStarted = false
            }
        }
    }

    val shouldPulse = pulsing && !reducedMotion && isStarted
    val pulseState: State<Float>? = if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "ledPipPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = IronMotion.AtmosphericBreath,
                repeatMode = RepeatMode.Reverse
            ),
            label = "ledPipBreath"
        )
    } else {
        null
    }

    val pipSize = size
    Box(
        modifier = modifier
            .size(pipSize * 2.4f)
            .drawWithCache {
                val pipCenter = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
                val coreRadius = (pipSize.toPx() * 0.5f).coerceAtLeast(1f)
                val maxHaloRadius = coreRadius * 2.3f * 1.25f
                val haloBrush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = pipCenter,
                    radius = maxHaloRadius
                )
                val specCenter = pipCenter - Offset(coreRadius * 0.22f, coreRadius * 0.22f)
                val specRadius = coreRadius * 0.32f
                val specColor = Color.White.copy(alpha = 0.55f)

                onDrawBehind {
                    val pulseFraction = pulseState?.value ?: 0.5f
                    val haloScale = if (shouldPulse) 0.9f + (0.35f * pulseFraction) else 1f
                    val haloAlpha = if (shouldPulse) 0.30f + (0.40f * pulseFraction) else 0.42f

                    scale(scaleX = haloScale / 1.25f, scaleY = haloScale / 1.25f, pivot = pipCenter) {
                        drawCircle(
                            brush = haloBrush,
                            radius = maxHaloRadius,
                            center = pipCenter,
                            alpha = haloAlpha
                        )
                    }
                    drawCircle(
                        color = color,
                        radius = coreRadius,
                        center = pipCenter
                    )
                    drawCircle(
                        color = specColor,
                        radius = specRadius,
                        center = specCenter
                    )
                }
            }
    )
}

/**
 * Precision ticked calibration ruler progress bar with spring-animated LED fill
 * ([IronMotion.SpatialSlow]) inside a recessed channel.
 */
@Composable
fun IronLogCalibrationBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    tickCount: Int = 10,
    height: Dp = 14.dp
) {
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current

    val clampedTarget = progress.coerceIn(0f, 1f)
    val animatedFillState = animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = if (reducedMotion) snap() else IronMotion.SpatialSlow,
        label = "calibrationBarFill"
    )

    val safeTicks = tickCount.coerceAtLeast(2)
    val tickColor = scheme.onSurfaceVariant.copy(alpha = if (surfaces.isDark) 0.32f else 0.38f)
    val borderColor = if (surfaces.isDark) Color.Black.copy(alpha = 0.7f) else Color(0xFFBFC5CE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(IronLogRadius.TightShape)
            .background(surfaces.wellFill)
            .border(width = IronLogBorder.Thin, color = borderColor, shape = IronLogRadius.TightShape)
            .drawWithCache {
                val shadowHeight = 3.dp.toPx().coerceAtMost(size.height * 0.4f)
                val innerShadow = Brush.verticalGradient(
                    colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                    startY = 0f,
                    endY = shadowHeight
                )
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        lerp(accentColor, Color.White, 0.16f),
                        accentColor,
                        lerp(accentColor, Color.Black, 0.18f)
                    )
                )
                val ledHighlight = Color.White.copy(alpha = 0.35f)
                val fillTickColor = Color.Black.copy(alpha = 0.25f)
                val tickStroke = 1.dp.toPx()
                val maxCursorW = 2.dp.toPx()
                onDrawBehind {
                    drawRect(brush = innerShadow, size = Size(size.width, shadowHeight))

                    val fillW = (size.width * animatedFillState.value).coerceIn(0f, size.width)
                    if (fillW > 0.5f) {
                        drawRect(
                            brush = fillBrush,
                            size = Size(fillW, size.height)
                        )
                        val cursorW = maxCursorW.coerceAtMost(fillW)
                        drawRect(
                            color = ledHighlight,
                            topLeft = Offset(fillW - cursorW, 0f),
                            size = Size(cursorW, size.height)
                        )
                    }

                    for (i in 1 until safeTicks) {
                        val x = size.width * (i.toFloat() / safeTicks.toFloat())
                        val isMajor = (i * 2 == safeTicks) || (i % 5 == 0)
                        val tickH = size.height * if (isMajor) 0.65f else 0.38f
                        drawLine(
                            color = if (x < fillW) fillTickColor else tickColor,
                            start = Offset(x, size.height - tickH),
                            end = Offset(x, size.height),
                            strokeWidth = tickStroke
                        )
                    }
                }
            }
    )
}

/**
 * Custom machined obsidian/ceramic toggle switch with a recessed track,
 * glowing accent LED thumb, [IronMotion.RewardPop] bounce, [IronHaptics.toggle],
 * and `Role.Switch` accessibility semantics.
 */
@Composable
fun IronLogTactileSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

    val thumbProgressState = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else IronMotion.RewardPop,
        label = "tactileSwitchThumb"
    )
    val trackBorderColorState = animateColorAsState(
        targetValue = when {
            !enabled -> scheme.outlineVariant.copy(alpha = 0.35f)
            checked -> accentColor.copy(alpha = 0.72f)
            surfaces.isDark -> Color.Black.copy(alpha = 0.72f)
            else -> Color(0xFFBFC5CE)
        },
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "tactileSwitchBorder"
    )
    val trackTintColorState = animateColorAsState(
        targetValue = when {
            !enabled -> scheme.surfaceContainerLow
            checked -> lerp(surfaces.wellFill, accentColor, if (surfaces.isDark) 0.24f else 0.20f)
            else -> surfaces.wellFill
        },
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "tactileSwitchTint"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val switchSemantics = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = { next ->
                haptics.toggle(next)
                onCheckedChange(next)
            }
        )
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        }
    }

    val pipColor = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
        checked -> accentColor
        else -> scheme.onSurfaceVariant.copy(alpha = 0.40f)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = IronLogSpace.TouchTargetMin)
            .then(switchSemantics),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 28.dp)
                .clip(IronLogRadius.CapsuleShape)
                .drawWithCache {
                    val trackW = size.width
                    val trackH = size.height
                    val corner = CornerRadius(trackH * 0.5f, trackH * 0.5f)
                    val pad = 3.dp.toPx()
                    val thumbDiameter = (trackH - pad * 2f).coerceAtLeast(1f)
                    val thumbRadius = thumbDiameter * 0.5f
                    val travel = (trackW - pad * 2f - thumbDiameter).coerceAtLeast(0f)
                    val cy = trackH * 0.5f
                    val shadowHeight = 4.dp.toPx()
                    val topShadowBrush = Brush.verticalGradient(
                        colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                        startY = 0f,
                        endY = shadowHeight
                    )
                    val trackBorderStroke = Stroke(width = IronLogBorder.Hairline.toPx() * 2f)
                    val thumbFillBrush = Brush.verticalGradient(
                        colors = listOf(surfaces.keycapTop, surfaces.keycapBottom),
                        startY = -thumbRadius,
                        endY = thumbRadius
                    )
                    val thumbRimBrush = Brush.verticalGradient(
                        colors = listOf(surfaces.keycapRimTop, surfaces.keycapShadowLip),
                        startY = -thumbRadius,
                        endY = thumbRadius
                    )
                    val thumbRimStroke = Stroke(width = IronLogBorder.Thin.toPx())
                    val glowRadius = thumbRadius * 0.85f
                    val glowBrush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset.Zero,
                        radius = glowRadius
                    )
                    val pipRadius = 3.dp.toPx()

                    onDrawBehind {
                        val cx = (pad + thumbRadius + (travel * thumbProgressState.value.coerceIn(-0.08f, 1.08f)))
                            .coerceIn(thumbRadius + 1f, trackW - thumbRadius - 1f)

                        // Recessed track fill
                        drawRoundRect(
                            color = trackTintColorState.value,
                            cornerRadius = corner
                        )

                        // Subtle recessed track inner top shadow
                        drawRect(
                            brush = topShadowBrush,
                            size = Size(trackW, shadowHeight)
                        )

                        // Inset hairline track rim (2x width clipped cleanly to capsule)
                        drawRoundRect(
                            color = trackBorderColorState.value,
                            cornerRadius = corner,
                            style = trackBorderStroke
                        )

                        // Sculpted circular machined thumb + optional LED glow
                        translate(left = cx, top = cy) {
                            if (checked && enabled) {
                                drawCircle(
                                    brush = glowBrush,
                                    radius = glowRadius,
                                    center = Offset.Zero
                                )
                            }
                            drawCircle(
                                brush = thumbFillBrush,
                                radius = thumbRadius,
                                center = Offset.Zero
                            )
                            drawCircle(
                                brush = thumbRimBrush,
                                radius = thumbRadius,
                                center = Offset.Zero,
                                style = thumbRimStroke
                            )
                            drawCircle(
                                color = pipColor,
                                radius = pipRadius,
                                center = Offset.Zero
                            )
                        }
                    }
                }
        )
    }
}
