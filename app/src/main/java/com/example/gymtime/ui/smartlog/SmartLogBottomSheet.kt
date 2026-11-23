package com.example.gymtime.ui.smartlog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.WeightUnit
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartLogBottomSheet(
    currentExercise: Exercise? = null,
    allowedExerciseIds: Set<Long>? = null,
    onDismiss: () -> Unit,
    onNavigateToLogger: (exerciseId: Long, draftToken: String) -> Unit,
    onCreateExercise: (name: String) -> Unit,
    viewModel: SmartLogViewModel = hiltViewModel()
) {
    val scheme = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    var listening by remember { mutableStateOf(false) }

    val latestCurrentExercise by rememberUpdatedState(currentExercise)
    val latestAllowedExerciseIds by rememberUpdatedState(allowedExerciseIds)
    val latestOnNavigateToLogger by rememberUpdatedState(onNavigateToLogger)
    val latestOnListeningChanged by rememberUpdatedState<(Boolean) -> Unit> { listening = it }
    val latestOnPartialText by rememberUpdatedState(viewModel::updateInput)
    val latestOnFinalText by rememberUpdatedState<(String) -> Unit> { text ->
        viewModel.updateInput(text)
        viewModel.submit(latestCurrentExercise, latestAllowedExerciseIds)
    }
    val latestOnSpeechError by rememberUpdatedState(viewModel::reportSpeechError)

    val speechController = remember(appContext) {
        OnDeviceSpeechController(
            context = appContext,
            onListeningChanged = { latestOnListeningChanged(it) },
            onPartialText = { latestOnPartialText(it) },
            onFinalText = { latestOnFinalText(it) },
            onError = { latestOnSpeechError(it) }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speechController.start()
        else viewModel.reportSpeechError("Microphone permission is needed for voice input. You can still type the set.")
    }

    val dismissSheet: () -> Unit = {
        speechController.cancel()
        viewModel.reset()
        onDismiss()
    }

    DisposableEffect(speechController, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                speechController.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speechController.destroy()
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navigation.collect { event ->
                speechController.cancel()
                latestOnNavigateToLogger(event.exerciseId, event.draftToken)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = IronLogSpace.CompactGap),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            IronLogSheetHeader(
                title = "Smart Log",
                subtitle = currentExercise?.let { "Describe sets for ${it.name}, or name another exercise." }
                    ?: "Name an exercise and describe one or more sets.",
                onClose = dismissSheet
            )

            OnDeviceAiDownloadCard()

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::updateInput,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = IronLogRadius.ControlShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedContainerColor = scheme.surfaceContainerLow
                ),
                placeholder = { Text("Incline dumbbell press, 32s, 10, 9, 8") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { viewModel.submit(currentExercise, allowedExerciseIds) }
                ),
                trailingIcon = {
                    IconButton(
                        enabled = speechController.isAvailable,
                        onClick = {
                            if (listening) {
                                speechController.stop()
                            } else if (
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                speechController.start()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (listening) "Stop listening" else "Speak set",
                            tint = if (listening) scheme.error else scheme.primary
                        )
                    }
                }
            )
            if (!speechController.isAvailable) {
                Text(
                    text = "On-device speech is unavailable on this device; typed Smart Log still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            state.createExerciseName?.let { name ->
                IronLogCard(
                    onClick = {
                        speechController.cancel()
                        viewModel.reset()
                        onCreateExercise(name)
                    },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                    highlighted = true,
                    shape = IronLogRadius.ControlShape,
                    containerColor = scheme.surfaceContainerLow,
                    borderColor = scheme.primary.copy(alpha = 0.55f),
                    contentPadding = PaddingValues(horizontal = IronLogSpace.CardPadding, vertical = 12.dp)
                ) {
                    Text(
                        text = "Add this as a new exercise: \"$name\"",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.candidates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    IronLogSectionHeader(title = "Which exercise did you mean?")
                    IronLogGroupedCard(showBorder = true) {
                        state.candidates.forEachIndexed { index, candidate ->
                            if (index > 0) {
                                IronLogDivider()
                            }
                            Surface(
                                onClick = { viewModel.chooseExercise(candidate.exercise) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Transparent,
                                contentColor = scheme.onSurface
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = IronLogSpace.TouchTargetMin)
                                        .padding(
                                            horizontal = IronLogSpace.CardPadding,
                                            vertical = 12.dp
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = candidate.exercise.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface
                                    )
                                    Text(
                                        text = candidate.exercise.targetMuscle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.review?.let { review ->
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    IronLogSectionHeader(title = "Review")
                    IronLogGroupedCard(showBorder = true) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = IronLogSpace.CardPadding,
                                    vertical = 12.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = review.exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${review.sets.size} ${if (review.sets.size == 1) "set" else "sets"}",
                                style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                color = scheme.onSurfaceVariant
                            )
                        }
                        IronLogDivider(insetStart = 0.dp)
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                            itemsIndexed(review.sets) { index, set ->
                                if (index > 0) {
                                    IronLogDivider()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = IronLogSpace.CardPadding,
                                            vertical = 10.dp
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Set ${index + 1}",
                                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                                        color = scheme.onSurfaceVariant
                                    )
                                    if (set.isWarmup) {
                                        IronLogStatusChip(
                                            label = "Warmup",
                                            tone = IronLogStatusTone.WARMUP
                                        )
                                    }
                                    Text(
                                        text = formatDraft(set),
                                        modifier = Modifier.weight(1f),
                                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = scheme.onSurface,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                        if (review.notices.isNotEmpty()) {
                            IronLogDivider(insetStart = 0.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = IronLogSpace.CardPadding,
                                        vertical = 10.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                review.notices.forEach { notice ->
                                    Text(
                                        text = notice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IronLogPrimaryButton(
                text = if (state.review != null) "OPEN LOGGER" else "PARSE SET",
                onClick = {
                    if (state.review != null) viewModel.confirmReview()
                    else viewModel.submit(currentExercise, allowedExerciseIds)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.input.isNotBlank() && !state.isProcessing && state.candidates.isEmpty(),
                loading = state.isProcessing,
                leadingIcon = Icons.Default.ArrowForward
            )
            Spacer(Modifier.height(IronLogSpace.CompactGap))
        }
    }
}

private fun formatDraft(set: SetDraft): String = buildList {
    set.weight?.let { add("${trimNumber(it)} ${if (set.weightUnit == WeightUnit.KILOGRAMS) "kg" else "lb"}") }
    set.reps?.let { add("$it reps") }
    set.durationSeconds?.let { add(formatDuration(it)) }
    set.distanceValue?.let { add("${trimNumber(it)} ${set.distanceUnit.shortLabel}") }
    set.calories?.let { add("${trimNumber(it)} cal") }
    set.rpe?.let { add("RPE ${trimNumber(it)}") }
    set.note?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
}.joinToString(" · ")

private fun trimNumber(value: Float): String {
    if (!value.isFinite()) return value.toString()
    return BigDecimal.valueOf(value.toDouble())
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0 && remainder > 0) "${minutes}m ${remainder}s"
    else if (minutes > 0) "${minutes}m" else "${remainder}s"
}

private val DistanceUnit?.shortLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.KILOMETERS -> "km"
        DistanceUnit.YARDS -> "yd"
        DistanceUnit.FEET -> "ft"
        DistanceUnit.MILES -> "mi"
        DistanceUnit.STEPS -> "steps"
        DistanceUnit.FLOORS -> "floors"
        null -> ""
    }
