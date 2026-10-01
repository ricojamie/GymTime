package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import kotlin.math.atan2
import kotlin.math.roundToInt

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
        title = { Text("Your accent color") },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewHueWheel(hue = hue, onHue = { updateSelection(it, saturation, brightness) })
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(color))
                Text("Hue · ${hue.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, valueRange = 0f..359.9f, onValueChange = { updateSelection(it, saturation, brightness) }, modifier = Modifier.semantics { contentDescription = "Hue" })
                Text("Saturation · ${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { updateSelection(hue, it, brightness) }, modifier = Modifier.semantics { contentDescription = "Saturation" })
                Text("Brightness · ${(brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { updateSelection(hue, saturation, it) }, modifier = Modifier.semantics { contentDescription = "Brightness" })
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
                    isError = draftColor == null,
                    supportingText = { Text(if (draftColor == null) "Use six hex digits, for example #A3E635." else "Buttons adapt their text for contrast.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { draftColor?.let { onSave(previewColorToHex(it)) } },
                enabled = draftColor != null,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
            ) { Text("Use color") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } }
    )
}

@Composable
private fun PreviewHueWheel(hue: Float, onHue: (Float) -> Unit) {
    val latestOnHue by rememberUpdatedState(onHue)
    val ringColors = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).fillMaxWidth().aspectRatio(1f)
                .semantics { contentDescription = "Color wheel. Hue can also be adjusted with the slider below." }
                .pointerInput(Unit) { detectTapGestures { latestOnHue(previewHueAngle(it, size.width, size.height)) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        latestOnHue(previewHueAngle(change.position, size.width, size.height))
                    }
                }
        ) {
            val ringWidth = 24.dp.toPx()
            val radius = size.minDimension / 2 - ringWidth / 2
            drawCircle(Brush.sweepGradient(ringColors), radius = radius, style = Stroke(width = ringWidth))
            val angle = Math.toRadians(hue.toDouble())
            val marker = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius)
            drawCircle(Color.White, 9.dp.toPx(), marker)
            drawCircle(Color.Black, 5.dp.toPx(), marker)
        }
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
