package com.example.gymtime.ui.analytics.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Composable
internal fun InsightPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    IronLogCard(
        modifier = modifier,
        highlighted = highlighted,
        spotlight = highlighted,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
        content = content
    )
}

@Composable
internal fun InsightPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IronLogPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = IronLogRadius.ControlShape
    )
}

@Composable
internal fun InsightSectionTitle(
    title: String,
    detail: String? = null,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    eyebrow: String? = null
) {
    val surfaces = LocalIronLogSurfaces.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow.uppercase(),
                style = IronLogType.technicalEyebrow(),
                color = surfaces.accent
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = detailColor
            )
        }
    }
}

@Immutable
internal data class InsightChartDot(val timestampMs: Long, val value: Double)

@Immutable
private data class PreparedLineChart(
    val points: List<InsightChartDot>,
    val lower: Double,
    val upper: Double,
    val firstDate: Long,
    val dateSpan: Long,
    val rangeLabel: String,
    val startDateLabel: String,
    val endDateLabel: String?
)

/** Decoration only: exact values and dates are exposed as text by the caller. */
@Composable
internal fun InsightLineChart(points: List<InsightChartDot>, unit: String, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val lineColor = surfaces.accent
    val gridColor = scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.45f else 0.65f)
    val matrixDotColor = scheme.onSurfaceVariant.copy(alpha = if (surfaces.isDark) 0.22f else 0.24f)
    val viewportBorder = remember(surfaces) {
        if (surfaces.isDark) Color.Black.copy(alpha = 0.72f) else Color(0xFFBFC5CE)
    }

    val prepared = remember(points, unit) {
        val minimum = points.minOf { it.value }
        val maximum = points.maxOf { it.value }
        val spread = (maximum - minimum).takeIf { it > 0.0 } ?: (maximum.coerceAtLeast(1.0) * 0.1)
        val lower = (minimum - spread * 0.15).coerceAtLeast(0.0)
        val upper = maximum + spread * 0.15
        val firstDate = points.minOf { it.timestampMs }
        val lastDate = points.maxOf { it.timestampMs }
        val dateSpan = (lastDate - firstDate).coerceAtLeast(1L)
        PreparedLineChart(
            points = points,
            lower = lower,
            upper = upper,
            firstDate = firstDate,
            dateSpan = dateSpan,
            rangeLabel = "${insightNumber(minimum)}–${insightNumber(maximum)} $unit",
            startDateLabel = insightDate(firstDate),
            endDateLabel = if (points.size > 1) insightDate(lastDate) else null
        )
    }

    val revealProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(prepared, reducedMotion) {
        if (reducedMotion) {
            revealProgress.snapTo(1f)
        } else {
            revealProgress.snapTo(0f)
            revealProgress.animateTo(1f, IronMotion.SpatialSlow)
        }
    }

    val drawModifier = remember(prepared, lineColor, gridColor, matrixDotColor, surfaces) {
        Modifier.drawWithCache {
            val gutter = 12.dp.toPx()
            val chartWidth = (size.width - gutter * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - gutter * 2).coerceAtLeast(1f)
            val gridStrokeWidth = 0.75.dp.toPx()
            val tickLen = 4.dp.toPx()
            val lineStroke = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val dotRadius = 3.5.dp.toPx()
            val matrixDotRadius = 1.dp.toPx()
            val valueSpan = (prepared.upper - prepared.lower).coerceAtLeast(1e-6)
            val shadowHeight = 4.dp.toPx().coerceAtMost(size.height * 0.2f)
            val topShadow = Brush.verticalGradient(
                colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                startY = 0f,
                endY = shadowHeight
            )
            val gridLines = Array(3) { i ->
                val y = gutter + chartHeight * i / 2f
                Offset(gutter, y) to Offset(size.width - gutter, y)
            }
            val matrixDots = buildList {
                val cols = 9
                val rows = 5
                for (c in 0..cols) {
                    val x = gutter + chartWidth * (c.toFloat() / cols.toFloat())
                    for (r in 0..rows) {
                        val y = gutter + chartHeight * (r.toFloat() / rows.toFloat())
                        add(Offset(x, y))
                    }
                }
            }
            val offsets = prepared.points.map { point ->
                Offset(
                    x = if (prepared.points.size == 1) {
                        size.width / 2f
                    } else {
                        gutter + chartWidth * ((point.timestampMs - prepared.firstDate).toDouble() / prepared.dateSpan).toFloat()
                    },
                    y = gutter + chartHeight * (1.0 - (point.value - prepared.lower) / valueSpan).toFloat()
                )
            }
            val path = if (offsets.size > 1) {
                Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
            } else {
                null
            }
            val pathMeasure = if (path != null) {
                PathMeasure().apply { setPath(path, false) }
            } else {
                null
            }
            val segmentPath = Path()
            val haloColor = lineColor.copy(alpha = 0.28f)
            val coreSpecular = Color.White.copy(alpha = 0.72f)

            onDrawBehind {
                drawRect(brush = topShadow, size = Size(size.width, shadowHeight))

                matrixDots.forEach { dot ->
                    drawCircle(matrixDotColor, matrixDotRadius, dot)
                }
                gridLines.forEach { (start, end) ->
                    drawLine(gridColor, start, end, gridStrokeWidth)
                    drawLine(gridColor, start, start + Offset(0f, -tickLen), gridStrokeWidth)
                    drawLine(gridColor, end, end + Offset(0f, -tickLen), gridStrokeWidth)
                }

                val progress = revealProgress.value.coerceIn(0f, 1f)
                if (path != null && pathMeasure != null) {
                    if (progress >= 0.999f) {
                        drawPath(path, lineColor, style = lineStroke)
                    } else if (progress > 0.001f) {
                        segmentPath.reset()
                        pathMeasure.getSegment(0f, pathMeasure.length * progress, segmentPath, true)
                        drawPath(segmentPath, lineColor, style = lineStroke)
                    }
                }

                offsets.forEachIndexed { index, center ->
                    val threshold = if (offsets.size <= 1) {
                        0.15f
                    } else {
                        (index.toFloat() / (offsets.size - 1).toFloat()) * 0.95f
                    }
                    if (progress >= threshold) {
                        drawCircle(haloColor, dotRadius * 2.1f, center)
                        drawCircle(lineColor, dotRadius, center)
                        drawCircle(coreSpecular, dotRadius * 0.38f, center)
                    }
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = prepared.rangeLabel,
            style = IronLogType.telemetryLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(IronLogRadius.KeycapShape)
                .background(surfaces.wellFill)
                .border(IronLogBorder.Thin, viewportBorder, IronLogRadius.KeycapShape)
                .clearAndSetSemantics { }
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .then(drawModifier)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = prepared.startDateLabel,
                style = IronLogType.telemetryLabel(),
                color = scheme.onSurfaceVariant
            )
            prepared.endDateLabel?.let { endLabel ->
                Text(
                    text = endLabel,
                    style = IronLogType.telemetryLabel(),
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Labels/counts remain accessible outside the decorative bars. */
@Composable
internal fun InsightWeeklyBars(counts: List<Int>, modifier: Modifier = Modifier) {
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val fill = surfaces.accent
    val base = scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.50f else 0.72f)
    val matrixDotColor = scheme.onSurfaceVariant.copy(alpha = if (surfaces.isDark) 0.20f else 0.22f)
    val viewportBorder = remember(surfaces) {
        if (surfaces.isDark) Color.Black.copy(alpha = 0.72f) else Color(0xFFBFC5CE)
    }

    val barAnims = remember(counts.size) {
        List(counts.size) { Animatable(if (reducedMotion) 1f else 0f) }
    }
    LaunchedEffect(counts, reducedMotion) {
        if (reducedMotion) {
            barAnims.forEach { it.snapTo(1f) }
        } else {
            barAnims.forEach { it.snapTo(0f) }
            coroutineScope {
                barAnims.forEachIndexed { index, anim ->
                    launch {
                        val stagger = index.coerceAtMost(10) * 28L
                        if (stagger > 0L) delay(stagger)
                        anim.animateTo(1f, IronMotion.SpatialLayout)
                    }
                }
            }
        }
    }

    val drawModifier = remember(counts, fill, base, matrixDotColor, surfaces, barAnims) {
        Modifier.drawWithCache {
            if (counts.isEmpty()) {
                onDrawBehind { }
            } else {
                val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                val hPad = 10.dp.toPx()
                val usableW = (size.width - hPad * 2f).coerceAtLeast(1f)
                val cell = usableW / counts.size
                val baseStroke = 1.dp.toPx()
                val guideStroke = 0.75.dp.toPx()
                val baseY = size.height - 10.dp.toPx()
                val topPad = 12.dp.toPx()
                val usableH = (baseY - topPad).coerceAtLeast(1f)
                val maxBarWidth = 26.dp.toPx()
                val minPositiveHeight = 5.dp.toPx()
                val cornerRadius = CornerRadius(3.dp.toPx())
                val barGradientTop = lerp(fill, Color.White, 0.16f)
                val barGradientBottom = fill.copy(alpha = 0.70f)
                val capHighlight = Color.White.copy(alpha = 0.42f)
                val barBrush = Brush.verticalGradient(
                    colors = listOf(barGradientTop, fill, barGradientBottom),
                    startY = topPad,
                    endY = baseY
                )
                val capHeightMax = 2.dp.toPx()
                val matrixDotRadius = 1.dp.toPx()
                val zeroDotRadius = 1.5.dp.toPx()
                val zeroDotY = baseY - 2.dp.toPx()
                val guideColor = base.copy(alpha = 0.4f)
                val midY = topPad + usableH * 0.5f
                val shadowHeight = 4.dp.toPx().coerceAtMost(size.height * 0.25f)
                val topShadow = Brush.verticalGradient(
                    colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                    startY = 0f,
                    endY = shadowHeight
                )
                val matrixDots = buildList {
                    val rows = 3
                    for (i in counts.indices) {
                        val cx = hPad + cell * i + cell * 0.5f
                        for (r in 0..rows) {
                            val y = topPad + usableH * (r.toFloat() / rows.toFloat())
                            add(Offset(cx, y))
                        }
                    }
                }
                val bars = counts.mapIndexed { index, count ->
                    val barWidth = (cell * 0.58f).coerceAtMost(maxBarWidth)
                    val targetHeight = (usableH * count / maxCount)
                        .coerceAtLeast(if (count == 0) 0f else minPositiveHeight)
                    val left = hPad + cell * index + (cell - barWidth) / 2f
                    Triple(left, barWidth, targetHeight)
                }

                onDrawBehind {
                    drawRect(brush = topShadow, size = Size(size.width, shadowHeight))
                    matrixDots.forEach { dot ->
                        drawCircle(matrixDotColor, matrixDotRadius, dot)
                    }
                    drawLine(
                        color = guideColor,
                        start = Offset(hPad, midY),
                        end = Offset(size.width - hPad, midY),
                        strokeWidth = guideStroke
                    )
                    drawLine(base, Offset(hPad, baseY), Offset(size.width - hPad, baseY), baseStroke)

                    bars.forEachIndexed { index, (left, barWidth, targetHeight) ->
                        if (targetHeight > 0f) {
                            val animValue = barAnims.getOrNull(index)?.value?.coerceIn(0f, 1.15f) ?: 1f
                            val currentHeight = (targetHeight * animValue).coerceIn(0f, usableH)
                            if (currentHeight > 0.5f) {
                                val top = baseY - currentHeight
                                val barSize = Size(barWidth, currentHeight)
                                drawRoundRect(
                                    brush = barBrush,
                                    topLeft = Offset(left, top),
                                    size = barSize,
                                    cornerRadius = cornerRadius
                                )
                                val capHeight = capHeightMax.coerceAtMost(currentHeight)
                                drawRoundRect(
                                    color = capHighlight,
                                    topLeft = Offset(left, top),
                                    size = Size(barWidth, capHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        } else {
                            drawCircle(
                                color = base,
                                radius = zeroDotRadius,
                                center = Offset(left + barWidth * 0.5f, zeroDotY)
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(IronLogRadius.KeycapShape)
            .background(surfaces.wellFill)
            .border(IronLogBorder.Thin, viewportBorder, IronLogRadius.KeycapShape)
            .clearAndSetSemantics { }
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(drawModifier)
        )
    }
}

private val insightFullDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

internal fun insightNumber(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
    .trimEnd('0').trimEnd('.', ',')

internal fun insightLoggedNumber(value: Float): String = value.toString().removeSuffix(".0")

internal fun insightDate(timestampMs: Long): String {
    val locale = Locale.getDefault()
    val formatter = insightFullDateFormatters.computeIfAbsent(locale) { loc ->
        DateTimeFormatter.ofPattern("MMM d, yyyy", loc)
    }
    return Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).format(formatter)
}
