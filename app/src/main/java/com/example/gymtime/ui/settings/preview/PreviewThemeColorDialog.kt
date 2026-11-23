package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.tabular
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PreviewThemeColorDialog(initialHex: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var hexDraft by rememberSaveable(initialHex) { mutableStateOf(initialHex) }
    val initialHsv = remember(initialHex) { (previewParseHex(initialHex) ?: Color.White).previewHsv() }
    var hue by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    val draftColor = previewParseHex(hexDraft)
    val action = LocalLoggerActionColors.current
    val colors = MaterialTheme.colorScheme

    fun updateSelection(nextHue: Float, nextSaturation: Float, nextBrightness: Float) {
        hue = nextHue
        saturation = nextSaturation
        brightness = nextBrightness
        hexDraft = previewColorToHex(Color.hsv(nextHue, nextSaturation, nextBrightness))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IronLogRadius.CardShape,
        title = { Text("Your accent color") },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                PreviewHueWheel(hue = hue, onHue = { updateSelection(it, saturation, brightness) })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(IronLogRadius.ControlShape)
                        .background(color)
                )
                Text("Hue · ${hue.roundToInt()}°", style = MaterialTheme.typography.labelLarge.tabular())
                Slider(
                    value = hue,
                    valueRange = 0f..359.9f,
                    onValueChange = { updateSelection(it, saturation, brightness) },
                    modifier = Modifier.semantics { contentDescription = "Hue" }
                )
                Text("Saturation · ${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge.tabular())
                Slider(
                    value = saturation,
                    onValueChange = { updateSelection(hue, it, brightness) },
                    modifier = Modifier.semantics { contentDescription = "Saturation" }
                )
                Text("Brightness · ${(brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge.tabular())
                Slider(
                    value = brightness,
                    onValueChange = { updateSelection(hue, saturation, it) },
                    modifier = Modifier.semantics { contentDescription = "Brightness" }
                )
                OutlinedTextField(
                    value = hexDraft,
                    onValueChange = {
                        hexDraft = it
                        previewParseHex(it)?.previewHsv()?.let { parsed ->
                            // Neutral colors have no observable hue; keep the user's wheel
                            // position so raising saturation/brightness restores that choice.
                            if (parsed[1] > 0f && parsed[2] > 0f) hue = parsed[0]
                            if (parsed[2] > 0f) saturation = parsed[1]
                            brightness = parsed[2]
                        }
                    },
                    label = { Text("Hex color") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                    shape = IronLogRadius.ControlShape,
                    isError = draftColor == null,
                    supportingText = {
                        Text(
                            if (draftColor == null) {
                                "Use six hex digits, for example #A3E635."
                            } else {
                                "Buttons adapt their text for contrast."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { draftColor?.let { onSave(previewColorToHex(it)) } },
                enabled = draftColor != null,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
            ) { Text("Use color") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun PreviewHueWheel(hue: Float, onHue: (Float) -> Unit) {
    val latestOnHue by rememberUpdatedState(onHue)
    val ringColors = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .sizeIn(maxWidth = 200.dp, maxHeight = 200.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = "Color wheel. Hue can also be adjusted with the slider below." }
                .pointerInput(Unit) { detectTapGestures { latestOnHue(previewHueAngle(it, size.width, size.height)) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        latestOnHue(previewHueAngle(change.position, size.width, size.height))
                    }
                }
                .drawWithCache {
                    val ringWidth = 24.dp.toPx()
                    val radius = size.minDimension / 2f - ringWidth / 2f
                    val ringStroke = Stroke(width = ringWidth)
                    val sweepBrush = Brush.sweepGradient(ringColors)
                    val outerDotRadius = 9.dp.toPx()
                    val innerDotRadius = 5.dp.toPx()
                    onDrawBehind {
                        drawCircle(sweepBrush, radius = radius, style = ringStroke)
                        val angle = Math.toRadians(hue.toDouble())
                        val marker = Offset(
                            center.x + cos(angle).toFloat() * radius,
                            center.y + sin(angle).toFloat() * radius
                        )
                        drawCircle(Color.White, outerDotRadius, marker)
                        drawCircle(Color.Black, innerDotRadius, marker)
                    }
                }
        )
    }
}

private fun previewHueAngle(position: Offset, width: Int, height: Int): Float {
    val offset = position - Offset(width / 2f, height / 2f)
    return (Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat() + 360f) % 360f
}

internal fun previewColorToHex(color: Color): String = "#%02X%02X%02X".format(
    (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt()
)

private fun previewParseHex(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return null
    return runCatching {
        Color(hex.substring(0, 2).toInt(16) / 255f, hex.substring(2, 4).toInt(16) / 255f, hex.substring(4, 6).toInt(16) / 255f)
    }.getOrNull()
}

private fun Color.previewHsv(): FloatArray {
    val highest = maxOf(red, green, blue)
    val lowest = minOf(red, green, blue)
    val delta = highest - lowest
    val hue = when {
        delta == 0f -> 0f
        highest == red -> ((green - blue) / delta).mod(6f) * 60f
        highest == green -> (((blue - red) / delta) + 2f) * 60f
        else -> (((red - green) / delta) + 4f) * 60f
    }
    return floatArrayOf((hue + 360f) % 360f, if (highest == 0f) 0f else delta / highest, highest)
}
