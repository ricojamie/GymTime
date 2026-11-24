package com.example.gymtime.ui.exercise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogSegmentOption
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.components.RulerSliderInput
import com.example.gymtime.ui.exercise.preview.LoggerField
import com.example.gymtime.ui.exercise.preview.LoggerFields
import com.example.gymtime.ui.exercise.preview.shortLabel
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.anodizedMicroGrain
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
internal fun LoggingInputs(
    type: LogType,
    fields: LoggerFields,
    activeField: LoggerField? = null,
    onSelect: (LoggerField) -> Unit
) {
    val inputs = type.inputFields()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 328.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                inputs.forEach {
                    LoggingInput(
                        field = it,
                        fields = fields,
                        onSelect = onSelect,
                        modifier = Modifier.fillMaxWidth(),
                        isActive = activeField == it
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                inputs.forEach {
                    LoggingInput(
                        field = it,
                        fields = fields,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                        isActive = activeField == it
                    )
                }
            }
        }
    }
}

@Composable
internal fun LoggingInput(
    field: LoggerField,
    fields: LoggerFields,
    onSelect: (LoggerField) -> Unit,
    modifier: Modifier,
    isActive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val label = field.label(fields)
    val rawValue = fields.value(field)
    val isLargeFont = LocalDensity.current.fontScale > 1.3f
    val numeralStyle = IronLogType.monumentalNumeral(
        if (isLargeFont) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge
    ).copy(fontWeight = FontWeight.Bold)

    IronLogRecessedWell(
        modifier = modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .semantics { contentDescription = label },
        active = isActive,
        accentColor = surfaces.accent,
        shape = IronLogRadius.KeycapShape,
        onClick = { onSelect(field) },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = IronLogType.technicalEyebrow(MaterialTheme.typography.labelMedium),
                    color = if (isActive) surfaces.accent else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                }
            }
            OdometerText(
                text = rawValue.ifEmpty { "—" },
                style = numeralStyle,
                color = if (rawValue.isEmpty()) scheme.onSurfaceVariant.copy(alpha = 0.65f) else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    field: LoggerField,
    fields: LoggerFields,
    order: List<LoggerField>,
    onChange: (LoggerField, String) -> Unit,
    onSelect: (LoggerField?) -> Unit,
    canLog: Boolean,
    editing: Boolean,
    onLog: () -> Unit,
    lastValue: Int? = null,
    bestValue: Int? = null,
    goalValue: Int? = null
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

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

    val deckBrush = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.plateElevatedTop, surfaces.plateBottom))
    }
    val selectionColors = remember(surfaces.accent) {
        TextSelectionColors(
            handleColor = surfaces.accent,
            backgroundColor = surfaces.accent.copy(alpha = 0.28f)
        )
    }

    ModalBottomSheet(
        onDismissRequest = { onSelect(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = surfaces.plateBottom,
        contentColor = scheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.68f),
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
                } else {
                    haptics.reject()
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = deckBrush)
                    .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.025f else 0.015f)
                    .drawBehind {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, surfaces.rimTop, Color.Transparent)
                            ),
                            start = Offset(0f, 0.5.dp.toPx()),
                            end = Offset(size.width, 0.5.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = IronLogSpace.ItemGap),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    // Recessed instrument readout well preserving hardware keyboard, selection, and SetText semantics.
                    IronLogRecessedWell(
                        active = true,
                        accentColor = surfaces.accent,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = IronLogType.technicalEyebrow(MaterialTheme.typography.labelMedium),
                                    color = surfaces.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                            }
                            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                                    BasicTextField(
                                        value = editor,
                                        onValueChange = { edit(it.text, it.selection) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focus),
                                        singleLine = true,
                                        textStyle = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge)
                                            .copy(
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface
                                            ),
                                        cursorBrush = SolidColor(surfaces.accent),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (currentText.isEmpty()) {
                                                    Text(
                                                        text = "—",
                                                        style = IronLogType.monumentalNumeral(
                                                            MaterialTheme.typography.headlineLarge
                                                        ).copy(fontWeight = FontWeight.Bold),
                                                        color = scheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    LaunchedEffect(focus) { focus.requestFocus() }

                    val modeOptions = remember(hasDial) {
                        listOf(
                            IronLogSegmentOption(label = "Keypad", enabled = true),
                            IronLogSegmentOption(label = "Dial", enabled = hasDial)
                        )
                    }
                    IronLogSegmentedControl(
                        options = modeOptions,
                        selectedIndex = if (usingDial) 1 else 0,
                        onSelect = { selectedIdx -> dial = (selectedIdx == 1) },
                        shape = IronLogRadius.KeycapShape
                    )

                    val nextLabel = if (next == null) "Done" else "Next"
                    val separator = if (field == LoggerField.DURATION) ":" else "."

                    AnimatedContent(
                        targetState = usingDial,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                            } else {
                                (fadeIn(IronMotion.EffectDefault) +
                                    scaleIn(IronMotion.SpatialLayout, initialScale = 0.96f))
                                    .togetherWith(
                                        fadeOut(IronMotion.EffectFast) +
                                            scaleOut(IronMotion.SpatialLayout, targetScale = 0.98f)
                                    )
                                    .using(
                                        SizeTransform(
                                            clip = false,
                                            sizeAnimationSpec = { _, _ -> IronMotion.SpatialLayoutSize }
                                        )
                                    )
                            }
                        },
                        label = "keypadDialMorph"
                    ) { dialActive ->
                        Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                            if (dialActive) {
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Max),
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                ) {
                                    KeypadDeckKey(
                                        keyLabel = "−",
                                        fieldLabel = label,
                                        enabled = true,
                                        accented = false,
                                        hapticType = KeycapHaptic.TICK,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        val value = nudge(field, buffer.text, -1)
                                        if (value == buffer.text) haptics.reject()
                                        edit(value, TextRange(value.length))
                                    }
                                    KeypadDeckKey(
                                        keyLabel = "+",
                                        fieldLabel = label,
                                        enabled = true,
                                        accented = false,
                                        hapticType = KeycapHaptic.TICK,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        val value = nudge(field, buffer.text, 1)
                                        if (value == buffer.text) haptics.reject()
                                        edit(value, TextRange(value.length))
                                    }
                                    KeypadDeckKey(
                                        keyLabel = nextLabel,
                                        fieldLabel = label,
                                        enabled = true,
                                        accented = true,
                                        hapticType = KeycapHaptic.KEY_TAP,
                                        modifier = Modifier
                                            .weight(2f)
                                            .fillMaxHeight()
                                    ) {
                                        onSelect(next)
                                    }
                                }
                            } else {
                                val rows = listOf(
                                    listOf("1", "2", "3", "+"),
                                    listOf("4", "5", "6", "−"),
                                    listOf("7", "8", "9", "⌫"),
                                    listOf(separator, "0", nextLabel)
                                )
                                rows.forEach { keys ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Max),
                                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                    ) {
                                        keys.forEach { keyLabel ->
                                            val isNextKey = keyLabel == nextLabel
                                            val isUtilityKey = keyLabel == "+" || keyLabel == "−" || keyLabel == "⌫"
                                            val keyEnabled = when (keyLabel) {
                                                "." -> field != LoggerField.REPS
                                                else -> true
                                            }
                                            val keyWeight = if (isNextKey) 2f else 1f
                                            val haptic = when {
                                                isUtilityKey -> KeycapHaptic.TICK
                                                else -> KeycapHaptic.KEY_TAP
                                            }
                                            KeypadDeckKey(
                                                keyLabel = keyLabel,
                                                fieldLabel = label,
                                                enabled = keyEnabled,
                                                accented = isNextKey,
                                                hapticType = haptic,
                                                modifier = Modifier
                                                    .weight(keyWeight)
                                                    .fillMaxHeight()
                                            ) {
                                                when (keyLabel) {
                                                    nextLabel -> onSelect(next)
                                                    "⌫" -> insert("")
                                                    "+", "−" -> {
                                                        val value = nudge(
                                                            field,
                                                            buffer.text,
                                                            if (keyLabel == "+") 1 else -1
                                                        )
                                                        if (value == buffer.text) haptics.reject()
                                                        edit(value, TextRange(value.length))
                                                    }
                                                    else -> insert(keyLabel)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
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
}

@Composable
private fun KeypadDeckKey(
    keyLabel: String,
    fieldLabel: String,
    enabled: Boolean,
    accented: Boolean,
    hapticType: KeycapHaptic,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IronLogSculptedKeycap(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .semantics {
                when (keyLabel) {
                    "⌫" -> contentDescription = "Backspace"
                    "+" -> contentDescription = "Increase $fieldLabel"
                    "−" -> contentDescription = "Decrease $fieldLabel"
                }
            },
        enabled = enabled,
        accented = accented,
        hapticType = hapticType,
        shape = IronLogRadius.KeycapShape,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
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
