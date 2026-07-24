package com.example.gymtime.ui.smartlog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.WeightUnit
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import java.util.Locale

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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    lateinit var speechController: OnDeviceSpeechController
    speechController = remember(context, currentExercise?.id, allowedExerciseIds) {
        OnDeviceSpeechController(
            context = context,
            onListeningChanged = { listening = it },
            onPartialText = viewModel::updateInput,
            onFinalText = { text ->
                viewModel.updateInput(text)
                viewModel.submit(currentExercise, allowedExerciseIds)
            },
            onError = viewModel::reportSpeechError
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speechController.start()
        else viewModel.reportSpeechError("Microphone permission is needed for voice input. You can still type the set.")
    }

    DisposableEffect(speechController) {
        onDispose { speechController.destroy() }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            onNavigateToLogger(event.exerciseId, event.draftToken)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            speechController.stop()
            viewModel.reset()
            onDismiss()
        },
        containerColor = LocalAppColors.current.surfaceCards
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Smart Log", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = currentExercise?.let { "Describe sets for ${it.name}, or name another exercise." }
                    ?: "Name an exercise and describe one or more sets.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textSecondary
            )

            OnDeviceAiDownloadCard()

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::updateInput,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
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
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                speechController.start()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (listening) "Stop listening" else "Speak set",
                            tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            if (!speechController.isAvailable) {
                Text(
                    "On-device speech is unavailable on this device; typed Smart Log still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textTertiary
                )
            }

            state.createExerciseName?.let { name ->
                OutlinedButton(
                    onClick = {
                        speechController.stop()
                        viewModel.reset()
                        onCreateExercise(name)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Add this as a new exercise: \"$name\"",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.candidates.isNotEmpty()) {
                Text("Which exercise did you mean?", fontWeight = FontWeight.Bold)
                state.candidates.forEach { candidate ->
                    Surface(
                        onClick = { viewModel.chooseExercise(candidate.exercise) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, LocalAppColors.current.textTertiary.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(candidate.exercise.name, fontWeight = FontWeight.Bold)
                            Text(candidate.exercise.targetMuscle, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                        }
                    }
                }
            }

            state.review?.let { review ->
                Text("Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(review.exercise.name, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        LazyColumn(modifier = Modifier.height((review.sets.size.coerceAtMost(4) * 40 + 8).dp)) {
                            itemsIndexed(review.sets) { index, set ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Set ${index + 1}", color = LocalAppColors.current.textTertiary)
                                    Text(formatDraft(set), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        review.notices.forEach { notice ->
                            Text(notice, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSecondary)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (state.review != null) viewModel.confirmReview()
                    else viewModel.submit(currentExercise, allowedExerciseIds)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.input.isNotBlank() && !state.isProcessing && state.candidates.isEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (state.review != null) "OPEN LOGGER" else "PARSE SET",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
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
    if (set.isWarmup) add("warmup")
}.joinToString(" · ")

private fun trimNumber(value: Float): String = if (value % 1f == 0f) value.toInt().toString()
else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

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
