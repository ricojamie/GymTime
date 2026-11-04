package com.example.gymtime.ui.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.ui.components.RulerSliderInput
import com.example.gymtime.ui.exercise.preview.LoggerField
import com.example.gymtime.ui.exercise.preview.LoggerFields
import com.example.gymtime.ui.exercise.preview.shortLabel
import kotlinx.coroutines.awaitCancellation
import java.math.BigDecimal

internal fun LogType.inputFields(): List<LoggerField> = when (this) {
    LogType.WEIGHT_REPS -> listOf(LoggerField.WEIGHT, LoggerField.REPS)
    LogType.REPS_ONLY -> listOf(LoggerField.REPS)
    LogType.DURATION -> listOf(LoggerField.DURATION)
    LogType.WEIGHT_DISTANCE -> listOf(LoggerField.WEIGHT, LoggerField.DISTANCE)
    LogType.DISTANCE_TIME -> listOf(LoggerField.DISTANCE, LoggerField.DURATION)
    LogType.WEIGHT_TIME -> listOf(LoggerField.WEIGHT, LoggerField.DURATION)
    LogType.CALORIES_TIME -> listOf(LoggerField.CALORIES, LoggerField.DURATION)
}

private fun LoggerField.label(fields: LoggerFields): String = when (this) {
    LoggerField.WEIGHT -> "WEIGHT / lb"
    LoggerField.REPS -> "REPS"
    LoggerField.DURATION -> "Time (mm:ss)"
    LoggerField.DISTANCE -> "Distance (" + fields.distanceUnit.shortLabel + ")"
    LoggerField.CALORIES -> "Calories"
    LoggerField.RPE -> "RPE (optional, 1–10)"
    LoggerField.NOTE -> error("Notes use the text keyboard")
}

private fun LoggerFields.value(field: LoggerField): String = when (field) {
    LoggerField.WEIGHT -> weight
    LoggerField.REPS -> reps
    LoggerField.DURATION -> duration
    LoggerField.DISTANCE -> distance
    LoggerField.CALORIES -> calories
    LoggerField.RPE -> rpe
    LoggerField.NOTE -> note
}

@Composable
internal fun LoggingInputs(type: LogType, fields: LoggerFields, onSelect: (LoggerField) -> Unit) {
    val inputs = type.inputFields()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 328.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                inputs.forEach { LoggingInput(it, fields, onSelect, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inputs.forEach { LoggingInput(it, fields, onSelect, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun LoggingInput(field: LoggerField, fields: LoggerFields, onSelect: (LoggerField) -> Unit, modifier: Modifier) {
    val label = field.label(fields)
    OutlinedCard(onClick = { onSelect(field) }, modifier = modifier.semantics { contentDescription = label }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(fields.value(field).ifEmpty { "—" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LoggingKeypad(
    field: LoggerField, fields: LoggerFields, order: List<LoggerField>,
    onChange: (LoggerField, String) -> Unit, onSelect: (LoggerField?) -> Unit,
    canLog: Boolean, editing: Boolean, onLog: () -> Unit,
    lastValue: Int? = null, bestValue: Int? = null, goalValue: Int? = null
) {
    val label = field.label(fields)
    val raw = fields.value(field)
    val next = order.getOrNull(order.indexOf(field) + 1)
    val hasDial = field == LoggerField.WEIGHT || field == LoggerField.REPS
    var dial by rememberSaveable { mutableStateOf(false) }
    val usingDial = dial && hasDial
    ModalBottomSheet(
        onDismissRequest = { onSelect(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null
    ) {
        key(field) {
            var selection by remember { mutableStateOf(TextRange(0, raw.length)) }
            val editor = TextFieldValue(raw, selection)
            val focus = remember { FocusRequester() }
            fun edit(text: String, cursor: TextRange) {
                val valid = when (field) {
                    LoggerField.DURATION -> text.all { it.isDigit() || it == ':' } && text.count { it == ':' } <= 2
                    LoggerField.REPS -> text.all(Char::isDigit)
                    else -> text.all { it.isDigit() || it == '.' } && text.count { it == '.' } <= 1
                }
                if (valid && (!hasDial || text.length <= 6)) {
                    selection = cursor
                    onChange(field, text)
                }
            }
            fun insert(key: String) {
                val start = if (key.isEmpty() && editor.selection.collapsed) (editor.selection.start - 1).coerceAtLeast(0) else editor.selection.min
                val text = raw.replaceRange(start, editor.selection.max, key)
                edit(text, TextRange(start + key.length))
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Keep hardware keyboard, selection, paste and accessibility editing on the actual field.
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    OutlinedTextField(value = editor, onValueChange = { edit(it.text, it.selection) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        label = { Text(label) })
                }
                LaunchedEffect(focus) { focus.requestFocus() }
                if (usingDial) {
                    RulerSliderInput(label, raw, { edit(it, TextRange(it.length)) },
                        step = if (field == LoggerField.WEIGHT) 5 else 1,
                        minValue = if (field == LoggerField.REPS) 1 else 0,
                        centered = field == LoggerField.WEIGHT,
                        compact = lastValue == null && bestValue == null && goalValue == null,
                        lastValue = lastValue, bestValue = bestValue, goalValue = goalValue)
                }
                val nextLabel = if (next == null) "Done" else "Next"
                val separator = if (field == LoggerField.DURATION) ":" else "."
                val toggle = if (usingDial) "Keypad" else "Dial"
                val rows = if (usingDial) listOf(listOf("−", "+", toggle, nextLabel)) else listOf(
                    listOf("1", "2", "3", "+"), listOf("4", "5", "6", "−"),
                    listOf("7", "8", "9", "⌫"), listOf(separator, "0", toggle, nextLabel))
                rows.forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        keys.forEach { key ->
                            val enabled = when (key) {
                                toggle -> hasDial
                                "." -> field != LoggerField.REPS
                                else -> true
                            }
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        toggle -> dial = !dial
                                        nextLabel -> onSelect(next)
                                        "⌫" -> insert("")
                                        "+", "−" -> {
                                            val value = nudge(field, raw, if (key == "+") 1 else -1)
                                            edit(value, TextRange(value.length))
                                        }
                                        else -> insert(key)
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    when (key) {
                                        "⌫" -> contentDescription = "Backspace"
                                        "+" -> contentDescription = "Increase $label"
                                        "−" -> contentDescription = "Decrease $label"
                                    }
                                },
                                contentPadding = PaddingValues(2.dp), shape = RoundedCornerShape(10.dp)
                            ) { Text(key, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Button(onClick = { onSelect(null); onLog() }, enabled = canLog,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (editing) "Save changes" else "Log set")
                }
            }
        }
    }
}

private fun nudge(field: LoggerField, raw: String, direction: Int): String {
    if (field == LoggerField.DURATION) {
        val seconds = raw.split(':').fold(0L) { sum, part ->
            (sum * 60 + (part.toIntOrNull() ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong())
        }
        val total = (seconds + direction).coerceIn(0L, Int.MAX_VALUE.toLong())
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
    val step = if (field == LoggerField.WEIGHT) 5 else 1
    val min = if (field == LoggerField.REPS || field == LoggerField.RPE) BigDecimal.ONE else BigDecimal.ZERO
    val result = ((raw.toBigDecimalOrNull() ?: BigDecimal.ZERO) + (step * direction).toBigDecimal()).max(min)
    return (if (field == LoggerField.RPE) result.min(BigDecimal.TEN) else result).stripTrailingZeros().toPlainString()
}
