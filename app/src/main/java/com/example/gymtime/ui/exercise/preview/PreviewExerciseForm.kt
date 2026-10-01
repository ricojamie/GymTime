package com.example.gymtime.ui.exercise.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.ui.theme.LocalLoggerActionColors

data class ExerciseFormUiState(
    val name: String = "",
    val muscle: String = "",
    val logType: LogType? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val notes: String = "",
    val restSeconds: String = "90",
    val repTarget: String = "",
    val muscles: List<String> = emptyList(),
    val similarExercises: List<Exercise> = emptyList(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val error: String? = null,
    val saveLabel: String = "Create exercise",
    val loadError: String? = null
)

sealed interface ExerciseFormAction {
    data class Name(val value: String) : ExerciseFormAction
    data class Muscle(val value: String) : ExerciseFormAction
    data class Tracking(val value: LogType) : ExerciseFormAction
    data class Distance(val value: DistanceUnit) : ExerciseFormAction
    data class Notes(val value: String) : ExerciseFormAction
    data class Rest(val value: String) : ExerciseFormAction
    data class RepTarget(val value: String) : ExerciseFormAction
    data class UseExisting(val exercise: Exercise) : ExerciseFormAction
    data object Save : ExerciseFormAction
    data object Back : ExerciseFormAction
    data object DismissError : ExerciseFormAction
    data object RetryLoad : ExerciseFormAction
}

/** Form rendering only. The screen owns persistence, validation, and guarded navigation. */
@Composable
fun PreviewExerciseForm(
    state: ExerciseFormUiState,
    onAction: (ExerciseFormAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onAction(ExerciseFormAction.Back) },
                    enabled = !state.isSaving,
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (state.isEditMode) "Edit exercise" else "New exercise",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(horizontal = 12.dp).size(22.dp)
                )
            }
            when {
                state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text("Getting your exercise ready…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.loadError != null -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(32.dp))
                    Text("Couldn't load this exercise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(state.loadError, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = { onAction(ExerciseFormAction.RetryLoad) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Try again")
                    }
                    TextButton(onClick = { onAction(ExerciseFormAction.Back) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Go back")
                    }
                }
                else -> {
                    ExerciseFormFields(state, onAction, Modifier.weight(1f))
                    ExerciseFormSaveBar(state, onAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseFormFields(
    state: ExerciseFormUiState,
    onAction: (ExerciseFormAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var showMoreTracking by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(state.isEditMode) }
    var reuseExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val candidate = state.similarExercises.firstOrNull { it.id == reuseExerciseId }
    val commonTypes = listOf(LogType.WEIGHT_REPS, LogType.REPS_ONLY, LogType.DURATION)
    val muscles = remember(state.muscles, state.muscle) {
        val common = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Abs", "Legs", "Cardio", "Warmups")
        (state.muscles + listOfNotNull(state.muscle.takeIf(String::isNotBlank))).distinct().sortedWith(
            compareBy<String> { muscle -> common.indexOfFirst { it.equals(muscle, ignoreCase = true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.lowercase() }
        )
    }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (state.isEditMode) "Fine-tune your setup." else "Make it yours.",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(ExerciseFormAction.Name(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
                label = { Text("Exercise name") },
                placeholder = { Text("e.g. Incline dumbbell press") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                colors = formTextFieldColors()
            )
        }

        if (!state.isEditMode && state.similarExercises.isNotEmpty()) {
            Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Already in your library", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    state.similarExercises.take(3).forEach { exercise ->
                        Surface(
                            onClick = { reuseExerciseId = exercise.id },
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(exercise.targetMuscle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                }
                                Text("Use this", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormSectionHeading("Where do you feel it?", "Choose one muscle group")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                muscles.forEach { muscle ->
                    FilterChip(
                        selected = state.muscle == muscle,
                        onClick = { focus.clearFocus(); onAction(ExerciseFormAction.Muscle(muscle)) },
                        label = { Text(muscle) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        enabled = !state.isSaving,
                        leadingIcon = if (state.muscle == muscle) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (muscles.isEmpty()) {
                Text("No muscle groups are available yet.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (state.muscle.isWarmupMuscleGroup()) {
                Text(
                    "Warmups stay in your history and are excluded from training metrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormSectionHeading("What do you track?")
            TrackingOptions(
                types = commonTypes,
                selected = state.logType,
                enabled = !state.isSaving,
                onSelect = { focus.clearFocus(); onAction(ExerciseFormAction.Tracking(it)) },
                compact = true
            )
            if (!showMoreTracking && state.logType != null && state.logType !in commonTypes) {
                TrackingOptions(
                    types = listOf(state.logType),
                    selected = state.logType,
                    enabled = !state.isSaving,
                    onSelect = { onAction(ExerciseFormAction.Tracking(it)) }
                )
            }
            TextButton(
                onClick = { focus.clearFocus(); showMoreTracking = !showMoreTracking },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !state.isSaving
            ) {
                Text(if (showMoreTracking) "Fewer tracking options" else "More tracking options")
                Spacer(Modifier.width(8.dp))
                Icon(if (showMoreTracking) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp))
            }
            AnimatedVisibility(showMoreTracking) {
                TrackingOptions(
                    types = LogType.entries.filterNot { it in commonTypes },
                    selected = state.logType,
                    enabled = !state.isSaving,
                    onSelect = { focus.clearFocus(); onAction(ExerciseFormAction.Tracking(it)) }
                )
            }
            if (state.logType.usesFormDistance) {
                Text("Distance unit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DistanceUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.distanceUnit == unit,
                            onClick = { onAction(ExerciseFormAction.Distance(unit)) },
                            label = { Text(unit.formLabel) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !state.isSaving,
                            leadingIcon = if (state.distanceUnit == unit) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
            state.logType?.let { type ->
                Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(type.formIcon, null, Modifier.size(24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Example set · ${type.formLabel}", style = MaterialTheme.typography.labelMedium)
                            Text(sampleSet(type, state.distanceUnit, state.repTarget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(type.formDescription, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Surface(color = colors.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column {
                Surface(
                    onClick = { focus.clearFocus(); showDetails = !showDetails },
                    enabled = !state.isSaving,
                    color = colors.surface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, null, tint = colors.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Your setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(setupSummary(state), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (showDetails) "Collapse setup" else "Expand setup")
                    }
                }
                AnimatedVisibility(showDetails) {
                    FormSetupFields(state, onAction)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (candidate != null) {
        AlertDialog(
            onDismissRequest = { reuseExerciseId = null },
            icon = { Icon(Icons.Default.FitnessCenter, null) },
            title = { Text("Use ${candidate.name}?") },
            text = { Text("Continue with the exercise already in your library. Unsaved changes on this page will be discarded.") },
            confirmButton = {
                TextButton(onClick = { reuseExerciseId = null; onAction(ExerciseFormAction.UseExisting(candidate)) }) { Text("Use exercise") }
            },
            dismissButton = {
                TextButton(onClick = { reuseExerciseId = null }) {
                    Text(if (state.isEditMode) "Keep editing" else "Keep creating")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormSetupFields(state: ExerciseFormUiState, onAction: (ExerciseFormAction) -> Unit) {
    val focus = LocalFocusManager.current
    val restInvalid = state.restSeconds.toIntOrNull()?.let { it <= 0 } ?: true
    val repsInvalid = state.repTarget.isNotBlank() && (state.repTarget.toIntOrNull()?.let { it <= 0 } ?: true)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Rest between sets", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(60, 90, 120, 180).forEach { seconds ->
                FilterChip(
                    selected = state.restSeconds.toIntOrNull() == seconds,
                    onClick = { focus.clearFocus(); onAction(ExerciseFormAction.Rest(seconds.toString())) },
                    label = { Text(when (seconds) { 60 -> "1 min"; 90 -> "90 sec"; 120 -> "2 min"; else -> "3 min" }) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
        OutlinedTextField(
            value = state.restSeconds,
            onValueChange = { onAction(ExerciseFormAction.Rest(it.filter(Char::isDigit).take(6))) },
            label = { Text("Rest time") },
            suffix = { Text("seconds") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isSaving,
            isError = restInvalid,
            supportingText = if (restInvalid) { { Text("Enter a rest time greater than zero.") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = formTextFieldColors()
        )
        if (state.logType.usesFormRepTarget) {
            OutlinedTextField(
                value = state.repTarget,
                onValueChange = { onAction(ExerciseFormAction.RepTarget(it.filter(Char::isDigit).take(3))) },
                label = { Text("Rep target · optional") },
                placeholder = { Text("e.g. 10") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving,
                isError = repsInvalid,
                supportingText = { Text(if (repsInvalid) "Enter a target greater than zero, or leave it empty." else "A starting target for your sets.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(14.dp),
                colors = formTextFieldColors()
            )
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = { onAction(ExerciseFormAction.Notes(it)) },
            label = { Text("Exercise notes · optional") },
            placeholder = { Text("Seat height, grip, or a cue that clicks…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            colors = formTextFieldColors()
        )
    }
}

@Composable
private fun TrackingOptions(
    types: List<LogType>,
    selected: LogType?,
    enabled: Boolean,
    onSelect: (LogType) -> Unit,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (compact && maxWidth >= 320.dp && fontScale <= 1.3f) 3 else 1
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            types.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { type ->
                        val isSelected = type == selected
                        Surface(
                            modifier = Modifier.weight(1f).selectable(
                                selected = isSelected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(type) }
                            ),
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) colors.primaryContainer else colors.surface,
                            contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outlineVariant)
                        ) {
                            if (columns == 3) {
                                Column(
                                    Modifier.heightIn(min = 104.dp).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(if (isSelected) Icons.Default.CheckCircle else type.formIcon, null, Modifier.size(22.dp))
                                    Text(type.formLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Row(
                                    Modifier.heightIn(min = 64.dp).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(type.formIcon, null, Modifier.size(24.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(type.formLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(type.formDescription, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseFormSaveBar(state: ExerciseFormUiState, onAction: (ExerciseFormAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    val focus = LocalFocusManager.current
    val hint = when {
        state.name.isBlank() && state.muscle.isBlank() -> "Add a name and choose a muscle group."
        state.name.isBlank() -> "Add an exercise name."
        state.muscle.isBlank() -> "Choose a muscle group."
        state.logType == null -> "Choose what you want to track."
        state.restSeconds.toIntOrNull()?.let { it > 0 } != true -> "Set a rest time above zero in Your setup."
        state.logType.usesFormRepTarget && state.repTarget.isNotBlank() && state.repTarget.toIntOrNull()?.let { it > 0 } != true -> "Check your rep target in Your setup."
        else -> null
    }
    Surface(color = colors.surface, contentColor = colors.onSurface, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.error != null) {
                Surface(color = colors.errorContainer, contentColor = colors.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { onAction(ExerciseFormAction.DismissError) }) {
                            Icon(Icons.Default.Close, "Dismiss error", Modifier.size(20.dp))
                        }
                    }
                }
            } else if (hint != null && !state.isSaving) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Button(
                onClick = { focus.clearFocus(); onAction(ExerciseFormAction.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onSurfaceVariant)
                } else {
                    Icon(if (state.isEditMode) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (state.isSaving) "Saving…" else state.saveLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (!state.isSaving && !state.isEditMode) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FormSectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun formTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

private val LogType?.usesFormDistance: Boolean
    get() = this == LogType.WEIGHT_DISTANCE || this == LogType.DISTANCE_TIME

private val LogType?.usesFormRepTarget: Boolean
    get() = this == LogType.WEIGHT_REPS || this == LogType.REPS_ONLY

private val LogType.formLabel: String
    get() = when (this) {
        LogType.WEIGHT_REPS -> "Weight + reps"
        LogType.REPS_ONLY -> "Reps only"
        LogType.DURATION -> "Time"
        LogType.WEIGHT_DISTANCE -> "Weight + distance"
        LogType.DISTANCE_TIME -> "Distance + time"
        LogType.WEIGHT_TIME -> "Weight + time"
        LogType.CALORIES_TIME -> "Calories + time"
    }

private val LogType.formDescription: String
    get() = when (this) {
        LogType.WEIGHT_REPS -> "Presses, squats, curls"
        LogType.REPS_ONLY -> "Push-ups, pull-ups, crunches"
        LogType.DURATION -> "Planks and timed activities"
        LogType.WEIGHT_DISTANCE -> "Farmer’s walks and sled pushes"
        LogType.DISTANCE_TIME -> "Runs, rides, and rowing"
        LogType.WEIGHT_TIME -> "Weighted holds and timed carries"
        LogType.CALORIES_TIME -> "Bike and rower calorie efforts"
    }

private val LogType.formIcon: ImageVector
    get() = when (this) {
        LogType.WEIGHT_REPS -> Icons.Default.FitnessCenter
        LogType.REPS_ONLY -> Icons.Default.Repeat
        LogType.DURATION -> Icons.Default.Timer
        LogType.WEIGHT_DISTANCE -> Icons.Default.Straighten
        LogType.DISTANCE_TIME -> Icons.Default.DirectionsRun
        LogType.WEIGHT_TIME -> Icons.Default.HourglassBottom
        LogType.CALORIES_TIME -> Icons.Default.LocalFireDepartment
    }

private val DistanceUnit.formLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "Meters"
        DistanceUnit.KILOMETERS -> "Kilometers"
        DistanceUnit.YARDS -> "Yards"
        DistanceUnit.FEET -> "Feet"
        DistanceUnit.MILES -> "Miles"
        DistanceUnit.STEPS -> "Steps"
        DistanceUnit.FLOORS -> "Floors"
    }

private fun sampleSet(type: LogType, unit: DistanceUnit, repTarget: String): String {
    val reps = repTarget.toIntOrNull()?.takeIf { it > 0 } ?: 10
    val distance = when (unit) {
        DistanceUnit.METERS -> "100 m"
        DistanceUnit.KILOMETERS -> "1 km"
        DistanceUnit.YARDS -> "50 yd"
        DistanceUnit.FEET -> "100 ft"
        DistanceUnit.MILES -> "1 mi"
        DistanceUnit.STEPS -> "500 steps"
        DistanceUnit.FLOORS -> "10 floors"
    }
    return when (type) {
        LogType.WEIGHT_REPS -> "100 lb × $reps reps"
        LogType.REPS_ONLY -> "$reps reps"
        LogType.DURATION -> "00:45"
        LogType.WEIGHT_DISTANCE -> "50 lb · $distance"
        LogType.DISTANCE_TIME -> "$distance · 10:00"
        LogType.WEIGHT_TIME -> "50 lb · 00:45"
        LogType.CALORIES_TIME -> "100 cal · 10:00"
    }
}

private fun setupSummary(state: ExerciseFormUiState): String = buildList {
    add("${state.restSeconds.ifBlank { "—" }} sec rest")
    if (state.logType.usesFormRepTarget && state.repTarget.isNotBlank()) add("${state.repTarget} rep target")
    add(if (state.notes.isNotBlank()) "Notes added" else "Add cues or notes")
}.joinToString(" · ")
