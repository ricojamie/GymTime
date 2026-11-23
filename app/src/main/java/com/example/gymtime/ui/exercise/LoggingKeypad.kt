package com.example.gymtime.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.RulerSliderInput
import com.example.gymtime.ui.exercise.preview.LoggerField
import com.example.gymtime.ui.exercise.preview.LoggerFields
import com.example.gymtime.ui.exercise.preview.shortLabel
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
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
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                inputs.forEach { LoggingInput(it, fields, onSelect, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                inputs.forEach { LoggingInput(it, fields, onSelect, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun LoggingInput(field: LoggerField, fields: LoggerFields, onSelect: (LoggerField) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = field.label(fields)
    val rawValue = fields.value(field)
    IronLogCard(
        onClick = { onSelect(field) },
        modifier = modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .semantics { contentDescription = label },
        shape = IronLogRadius.ControlShape,
        containerColor = scheme.surfaceContainerLow,
        showBorder = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = IronLogType.sectionEyebrow(MaterialTheme.typography.labelMedium),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = rawValue.ifEmpty { "—" },
            style = IronLogType.tabular(MaterialTheme.typography.headlineMedium).copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (rawValue.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun TextRange.clampTo(length: Int): TextRange =
    TextRange(start.coerceIn(0, length), end.coerceIn(0, length))

private class KeypadBuffer(initialText: String) {
    var text by mutableStateOf(initialText)
    var selection by mutableStateOf(TextRange(0, initialText.length))
    var lastObservedRaw: String = initialText
    private val pendingOutputs = ArrayDeque<String>()

    fun syncExternal(raw: String) {
        if (raw == lastObservedRaw) return
        val matchIndex = pendingOutputs.indexOfFirst { it == raw }
        if (matchIndex >= 0) {
            repeat(matchIndex + 1) { pendingOutputs.removeFirst() }
            lastObservedRaw = raw
            return
        }
        pendingOutputs.clear()
        lastObservedRaw = raw
        text = raw
        selection = selection.clampTo(raw.length)
    }

    fun applyLocalEdit(newText: String, newSelection: TextRange) {
        val clamped = newSelection.clampTo(newText.length)
        text = newText
        selection = clamped
        if (pendingOutputs.size >= 32) {
            pendingOutputs.removeFirst()
        }
        pendingOutputs.addLast(newText)
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
    val scheme = MaterialTheme.colorScheme
    val label = field.label(fields)
    val raw = fields.value(field)
    val next = order.getOrNull(order.indexOf(field) + 1)
    val hasDial = field == LoggerField.WEIGHT || field == LoggerField.REPS
    val maxLength = when {
        hasDial -> 6
        field == LoggerField.RPE -> 4
        else -> 8
    }
    var dial by rememberSaveable { mutableStateOf(false) }
    val usingDial = dial && hasDial
    ModalBottomSheet(
        onDismissRequest = { onSelect(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        dragHandle = null
    ) {
        key(field) {
            val buffer = remember { KeypadBuffer(raw) }
            buffer.syncExternal(raw)
            val currentText = buffer.text
            val clampedSelection = buffer.selection.clampTo(currentText.length)
            val editor = TextFieldValue(currentText, clampedSelection)
            val focus = remember { FocusRequester() }

            fun edit(text: String, cursor: TextRange) {
                val activeText = buffer.text
                if (text == activeText) {
                    buffer.selection = cursor.clampTo(activeText.length)
                    return
                }
                val valid = when (field) {
                    LoggerField.DURATION -> text.all { it.isDigit() || it == ':' } && text.count { it == ':' } <= 2
                    LoggerField.REPS -> text.all(Char::isDigit)
                    else -> text.all { it.isDigit() || it == '.' } && text.count { it == '.' } <= 1
                }
                if (valid && (text.length <= maxLength || text.length < activeText.length)) {
                    buffer.applyLocalEdit(text, cursor)
                    onChange(field, text)
                }
            }

            fun insert(keyValue: String) {
                val activeText = buffer.text
                val activeSel = buffer.selection.clampTo(activeText.length)
                val start = if (keyValue.isEmpty() && activeSel.collapsed) {
                    (activeSel.start - 1).coerceAtLeast(0)
                } else {
                    activeSel.min
                }
                val end = activeSel.max.coerceIn(start, activeText.length)
                val text = activeText.replaceRange(start, end, keyValue)
                edit(text, TextRange(start + keyValue.length))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = IronLogSpace.ItemGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                // Keep hardware keyboard, selection, paste and accessibility editing on the actual field.
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    OutlinedTextField(
                        value = editor,
                        onValueChange = { edit(it.text, it.selection) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        singleLine = true,
                        shape = IronLogRadius.ControlShape,
                        textStyle = IronLogType.tabular(MaterialTheme.typography.headlineSmall).copy(
                            fontWeight = FontWeight.Bold
                        ),
                        label = { Text(label) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = scheme.surfaceContainerLow,
                            unfocusedContainerColor = scheme.surfaceContainerLow,
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outlineVariant
                        )
                    )
                }
                LaunchedEffect(focus) { focus.requestFocus() }
                if (usingDial) {
                    RulerSliderInput(
                        label = label,
                        value = currentText,
                        onValueChange = { edit(it, TextRange(it.length)) },
                        step = if (field == LoggerField.WEIGHT) 5 else 1,
                        minValue = if (field == LoggerField.REPS) 1 else 0,
                        centered = field == LoggerField.WEIGHT,
                        compact = lastValue == null && bestValue == null && goalValue == null,
                        lastValue = lastValue,
                        bestValue = bestValue,
                        goalValue = goalValue
                    )
                }
                val nextLabel = if (next == null) "Done" else "Next"
                val separator = if (field == LoggerField.DURATION) ":" else "."
                val toggle = if (usingDial) "Keypad" else "Dial"
                val rows = if (usingDial) listOf(listOf("−", "+", toggle, nextLabel)) else listOf(
                    listOf("1", "2", "3", "+"),
                    listOf("4", "5", "6", "−"),
                    listOf("7", "8", "9", "⌫"),
                    listOf(separator, "0", toggle, nextLabel)
                )
                rows.forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                        keys.forEach { keyLabel ->
                            val enabled = when (keyLabel) {
                                toggle -> hasDial
                                "." -> field != LoggerField.REPS
                                else -> true
                            }
                            val isNextKey = keyLabel == nextLabel
                            val isUtilityKey = keyLabel == "+" || keyLabel == "−" || keyLabel == "⌫" || keyLabel == toggle
                            val buttonColors = when {
                                isNextKey -> ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.primaryContainer,
                                    contentColor = scheme.onPrimaryContainer
                                )
                                isUtilityKey -> ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.surfaceContainerHighest,
                                    contentColor = scheme.onSurface
                                )
                                else -> ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.surfaceContainerHigh,
                                    contentColor = scheme.onSurface
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    when (keyLabel) {
                                        toggle -> dial = !dial
                                        nextLabel -> onSelect(next)
                                        "⌫" -> insert("")
                                        "+", "−" -> {
                                            val value = nudge(field, buffer.text, if (keyLabel == "+") 1 else -1)
                                            edit(value, TextRange(value.length))
                                        }
                                        else -> insert(keyLabel)
                                    }
                                },
                                enabled = enabled,
                                colors = buttonColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = IronLogSpace.TouchTargetMin)
                                    .semantics {
                                        when (keyLabel) {
                                            "⌫" -> contentDescription = "Backspace"
                                            "+" -> contentDescription = "Increase $label"
                                            "−" -> contentDescription = "Decrease $label"
                                        }
                                    },
                                contentPadding = PaddingValues(2.dp),
                                shape = IronLogRadius.ControlShape
                            ) {
                                Text(
                                    text = keyLabel,
                                    style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                IronLogPrimaryButton(
                    text = if (editing) "Save changes" else "Log set",
                    onClick = {
                        onSelect(null)
                        onLog()
                    },
                    enabled = canLog,
                    modifier = Modifier.fillMaxWidth()
                )
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
