package com.example.gymtime.ui.components.plate

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.util.PlateCalculator

private val MetalGradientStops = listOf(
    Color(0xFF8A939F),
    Color(0xFFE8EDF1),
    Color(0xFFB5BFCA),
    Color(0xFF646D7A)
)
private val ContactShadowColor = Color.Black.copy(alpha = 0.14f)
private val KnurlDarkColor = Color(0xFF646C78).copy(alpha = 0.72f)
private val KnurlLightColor = Color.White.copy(alpha = 0.60f)
private val HashMarkColor = Color(0xFF495260)
private val MetalHighlightColor = Color.White.copy(alpha = 0.8f)
private val PlateOutlineColor = Color.Black.copy(alpha = 0.27f)
private val PlateSpecularColor = Color.White.copy(alpha = 0.34f)
private val PlateRimColor = Color.Black.copy(alpha = 0.24f)

private class CachedMetalBar(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: CornerRadius,
    val brush: Brush,
    val highlightStart: Offset,
    val highlightEnd: Offset
)

private class CachedPlateRect(
    val plateIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val brush: Brush,
    val highlightStart: Offset,
    val highlightEnd: Offset,
    val rimTopStart: Offset,
    val rimTopEnd: Offset,
    val rimBottomStart: Offset,
    val rimBottomEnd: Offset
)

/** A code-native illustration. The exact list below the drawing remains the loadout authority. */
@Composable
internal fun OlympicBarbell(
    platesPerSide: List<Float>,
    barWeight: Float,
    loadingSides: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current

    val plates = remember(platesPerSide) { platesPerSide.sortedDescending() }
    val visible = remember(plates) { plates.take(7) }
    val hidden = plates.size - visible.size
    val sideDescription = if (loadingSides == 1) "right sleeve only" else "each loaded side"
    val formattedBarWeight = remember(barWeight) { PlateCalculator.formatWeight(barWeight) }
    val accessibilityDescription = remember(formattedBarWeight, plates.size, sideDescription, hidden) {
        "$formattedBarWeight pound Olympic style bar, ${plates.size} plates on $sideDescription. Exact sizes are listed below." +
            if (hidden > 0) " Drawing shows seven plates; $hidden additional plates omitted per loaded side." else ""
    }

    var previousVisible by remember { mutableStateOf<List<Float>>(emptyList()) }
    var changedFromIndex by remember { mutableIntStateOf(0) }
    val platePopAnim = remember { Animatable(1f) }

    LaunchedEffect(visible, loadingSides, reducedMotion) {
        if (visible != previousVisible) {
            var firstDiff = 0
            while (firstDiff < visible.size && firstDiff < previousVisible.size &&
                visible[firstDiff] == previousVisible[firstDiff]
            ) {
                firstDiff++
            }
            changedFromIndex = firstDiff
            previousVisible = visible
            if (!reducedMotion && visible.isNotEmpty()) {
                platePopAnim.snapTo(0.78f)
                platePopAnim.animateTo(1f, IronMotion.RewardPop)
            } else {
                platePopAnim.snapTo(1f)
            }
        } else if (reducedMotion) {
            platePopAnim.snapTo(1f)
        }
    }

    val visibleState = rememberUpdatedState(visible)
    val loadingSidesState = rememberUpdatedState(loadingSides)
    val changedFromState = rememberUpdatedState(changedFromIndex)

    val stageBrush = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.telemetryStageTop, surfaces.wellFill))
    }
    val rimStroke = remember(surfaces) {
        IronLogBorder.specularRimStroke(surfaces, highlighted = false, width = IronLogBorder.Hairline)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(IronLogRadius.PlateShape)
            .background(brush = stageBrush)
            .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
            .border(border = rimStroke, shape = IronLogRadius.PlateShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .semantics { contentDescription = accessibilityDescription }
                .drawWithCache {
                    val activeVisible = visibleState.value
                    val activeSides = loadingSidesState.value
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val middle = canvasHeight * 0.49f
                    val shaftStart = canvasWidth * 0.25f
                    val shaftEnd = canvasWidth * 0.75f
                    val sleeveStart = canvasWidth * 0.025f
                    val sleeveEnd = canvasWidth * 0.975f
                    val shaftHeight = 7.dp.toPx()
                    val sleeveHeight = 14.dp.toPx()
                    val dp1 = 1.dp.toPx()
                    val dp2 = 2.dp.toPx()
                    val dp3 = 3.dp.toPx()
                    val dp4 = 4.dp.toPx()
                    val dp5 = 5.dp.toPx()
                    val highlightStroke = 0.7.dp.toPx()
                    val knurlDarkStroke = 0.6.dp.toPx()
                    val knurlLightStroke = 0.5.dp.toPx()
                    val plateRimStrokeWidth = 1.3.dp.toPx()
                    val plateSpecularStrokeWidth = 0.8.dp.toPx()
                    val plateOutlineStroke = Stroke(0.8.dp.toPx())
                    val plateCornerRadius = CornerRadius(dp2, dp3)

                    val shadowTopLeft = Offset(canvasWidth * 0.035f, middle + 39.dp.toPx())
                    val shadowSize = Size(canvasWidth * 0.93f, shaftHeight)

                    fun buildMetal(start: Float, end: Float, height: Float, radius: Float): CachedMetalBar {
                        val top = middle - height / 2f
                        return CachedMetalBar(
                            x = start,
                            y = top,
                            width = (end - start).coerceAtLeast(1f),
                            height = height,
                            radius = CornerRadius(radius, radius),
                            brush = Brush.verticalGradient(
                                colors = MetalGradientStops,
                                startY = top,
                                endY = top + height
                            ),
                            highlightStart = Offset(start + radius, top + height * 0.23f),
                            highlightEnd = Offset(end - radius, top + height * 0.23f)
                        )
                    }

                    val metalBars = buildList {
                        add(buildMetal(sleeveStart, shaftStart, sleeveHeight, dp3))
                        add(buildMetal(shaftEnd, sleeveEnd, sleeveHeight, dp3))
                        add(buildMetal(shaftStart, shaftEnd, shaftHeight, dp2))
                        add(buildMetal(shaftStart - dp4, shaftStart + dp1, 23.dp.toPx(), dp1))
                        add(buildMetal(shaftEnd - dp1, shaftEnd + dp4, 23.dp.toPx(), dp1))
                        add(buildMetal(sleeveStart, sleeveStart + dp3, 17.dp.toPx(), dp1))
                        add(buildMetal(sleeveEnd - dp3, sleeveEnd, 17.dp.toPx(), dp1))
                    }

                    val knurlSegments = buildList {
                        val zones = arrayOf(0.29f to 0.42f, 0.58f to 0.71f)
                        for ((startFraction, endFraction) in zones) {
                            var x = canvasWidth * startFraction
                            val limit = canvasWidth * endFraction
                            while (x < limit) {
                                add(
                                    Pair(
                                        Offset(x, middle - shaftHeight / 2f) to Offset(x + dp3, middle + shaftHeight / 2f),
                                        Offset(x, middle + shaftHeight / 2f) to Offset(x + dp3, middle - shaftHeight / 2f)
                                    )
                                )
                                x += dp3
                            }
                        }
                    }
                    val hashXLeft = canvasWidth * 0.435f
                    val hashXRight = canvasWidth * 0.565f
                    val hashTopY = middle - shaftHeight / 2f
                    val hashBottomY = middle + shaftHeight / 2f

                    val plateRects = mutableListOf<CachedPlateRect>()
                    val collarBars = mutableListOf<CachedMetalBar>()

                    fun buildStack(right: Boolean) {
                        var cursor = if (right) shaftEnd + dp5 else shaftStart - dp5
                        val plateZone = canvasWidth * 0.195f
                        var desiredTotal = activeVisible.size * dp1
                        val desiredWidths = FloatArray(activeVisible.size) { index ->
                            val w = if (activeVisible[index] >= 25f) 12.dp.toPx() else 8.dp.toPx()
                            desiredTotal += w
                            w
                        }
                        val scale = if (desiredTotal > plateZone) plateZone / desiredTotal else 1f
                        for (index in activeVisible.indices) {
                            val weight = activeVisible[index]
                            val width = desiredWidths[index] * scale
                            val x = if (right) cursor else cursor - width
                            val height = when {
                                weight >= 45f -> 91.dp.toPx()
                                weight >= 35f -> 82.dp.toPx()
                                weight >= 25f -> 73.dp.toPx()
                                weight >= 15f -> 63.dp.toPx()
                                weight >= 10f -> 55.dp.toPx()
                                weight >= 5f -> 44.dp.toPx()
                                else -> 34.dp.toPx()
                            }
                            val y = middle - height / 2f
                            val baseColor = Color(PlateCalculator.getPlateColor(weight))
                            val darkLeft = baseColor.copy(
                                red = baseColor.red * 0.60f,
                                green = baseColor.green * 0.60f,
                                blue = baseColor.blue * 0.60f
                            )
                            plateRects += CachedPlateRect(
                                plateIndex = index,
                                x = x,
                                y = y,
                                width = width,
                                height = height,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(darkLeft, baseColor, baseColor, Color.Black.copy(alpha = 0.65f)),
                                    startX = x,
                                    endX = x + width
                                ),
                                highlightStart = Offset(x + width * 0.28f, y + dp4),
                                highlightEnd = Offset(x + width * 0.28f, y + height - dp4),
                                rimTopStart = Offset(x + dp1, y + height * 0.09f),
                                rimTopEnd = Offset(x + width - dp1, y + height * 0.09f),
                                rimBottomStart = Offset(x + dp1, y + height * 0.91f),
                                rimBottomEnd = Offset(x + width - dp1, y + height * 0.91f)
                            )
                            cursor += (width + dp1) * if (right) 1f else -1f
                        }
                        if (activeVisible.isNotEmpty()) {
                            val collarX = if (right) cursor + dp1 else cursor - dp4 - dp1
                            collarBars += buildMetal(collarX, collarX + dp4, 26.dp.toPx(), dp1)
                        }
                    }

                    if (activeSides > 1) buildStack(right = false)
                    buildStack(right = true)

                    onDrawBehind {
                        drawOval(
                            color = ContactShadowColor,
                            topLeft = shadowTopLeft,
                            size = shadowSize
                        )
                        drawCachedMetal(metalBars[0], highlightStroke)
                        drawCachedMetal(metalBars[1], highlightStroke)
                        drawCachedMetal(metalBars[2], highlightStroke)

                        for (pair in knurlSegments) {
                            drawLine(
                                color = KnurlDarkColor,
                                start = pair.first.first,
                                end = pair.first.second,
                                strokeWidth = knurlDarkStroke
                            )
                            drawLine(
                                color = KnurlLightColor,
                                start = pair.second.first,
                                end = pair.second.second,
                                strokeWidth = knurlLightStroke
                            )
                        }
                        drawLine(
                            color = HashMarkColor,
                            start = Offset(hashXLeft, hashTopY),
                            end = Offset(hashXLeft, hashBottomY),
                            strokeWidth = dp1
                        )
                        drawLine(
                            color = HashMarkColor,
                            start = Offset(hashXRight, hashTopY),
                            end = Offset(hashXRight, hashBottomY),
                            strokeWidth = dp1
                        )

                        drawCachedMetal(metalBars[3], highlightStroke)
                        drawCachedMetal(metalBars[4], highlightStroke)

                        val pop = platePopAnim.value
                        val fromIdx = changedFromState.value
                        for (plate in plateRects) {
                            if (pop != 1f && plate.plateIndex >= fromIdx) {
                                val pivot = Offset(plate.x + plate.width * 0.5f, middle)
                                scale(scaleX = 1f, scaleY = pop, pivot = pivot) {
                                    drawCachedPlate(
                                        plate = plate,
                                        cornerRadius = plateCornerRadius,
                                        outlineStroke = plateOutlineStroke,
                                        specularStrokeWidth = plateSpecularStrokeWidth,
                                        rimStrokeWidth = plateRimStrokeWidth
                                    )
                                }
                            } else {
                                drawCachedPlate(
                                    plate = plate,
                                    cornerRadius = plateCornerRadius,
                                    outlineStroke = plateOutlineStroke,
                                    specularStrokeWidth = plateSpecularStrokeWidth,
                                    rimStrokeWidth = plateRimStrokeWidth
                                )
                            }
                        }
                        for (collar in collarBars) {
                            drawCachedMetal(collar, highlightStroke)
                        }
                        drawCachedMetal(metalBars[5], highlightStroke)
                        drawCachedMetal(metalBars[6], highlightStroke)
                    }
                }
        )

        Text(
            text = when {
                hidden > 0 -> "+$hidden more on each loaded side · full load listed below"
                plates.isEmpty() -> "Bare bar · $formattedBarWeight lb"
                loadingSides == 1 -> "One sleeve loaded · $formattedBarWeight lb bar"
                loadingSides > 2 -> "$loadingSides loaded sides · two representative sleeves shown"
                else -> "Mirror this load · $formattedBarWeight lb bar"
            },
            color = scheme.onSurfaceVariant,
            style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        )
    }
}

private fun DrawScope.drawCachedMetal(bar: CachedMetalBar, highlightStroke: Float) {
    drawRoundRect(
        brush = bar.brush,
        topLeft = Offset(bar.x, bar.y),
        size = Size(bar.width, bar.height),
        cornerRadius = bar.radius
    )
    drawLine(
        color = MetalHighlightColor,
        start = bar.highlightStart,
        end = bar.highlightEnd,
        strokeWidth = highlightStroke
    )
}

private fun DrawScope.drawCachedPlate(
    plate: CachedPlateRect,
    cornerRadius: CornerRadius,
    outlineStroke: Stroke,
    specularStrokeWidth: Float,
    rimStrokeWidth: Float
) {
    val topLeft = Offset(plate.x, plate.y)
    val rectSize = Size(plate.width, plate.height)
    drawRoundRect(
        brush = plate.brush,
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cornerRadius
    )
    drawRoundRect(
        color = PlateOutlineColor,
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cornerRadius,
        style = outlineStroke
    )
    drawLine(
        color = PlateSpecularColor,
        start = plate.highlightStart,
        end = plate.highlightEnd,
        strokeWidth = specularStrokeWidth
    )
    drawLine(
        color = PlateRimColor,
        start = plate.rimTopStart,
        end = plate.rimTopEnd,
        strokeWidth = rimStrokeWidth
    )
    drawLine(
        color = PlateRimColor,
        start = plate.rimBottomStart,
        end = plate.rimBottomEnd,
        strokeWidth = rimStrokeWidth
    )
}
