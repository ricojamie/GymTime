package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.isWarmupLibraryExercise
import com.example.gymtime.ui.components.RulerSliderInput
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.exercise.LoggerCelebration
import com.example.gymtime.util.TimeFormatter

enum class LoggerField { WEIGHT, REPS, DURATION, DISTANCE, CALORIES, RPE, NOTE }
enum class LoggerAction {
    BACK, HOME, FINISH, PREVIOUS, NEXT, ADD_EXERCISE, OVERVIEW,
    HISTORY, PLATES, NOTES, EDIT_EXERCISE, THEME, SUPERSET, EXIT_SUPERSET,
    SMART_LOG, CANCEL_SMART_LOG, TOGGLE_WARMUP, LOG, CANCEL_EDIT,
    TIMER, START_TIMER, SKIP_TIMER, ADD_REST, DISMISS_PR
}

data class LoggerFields(
    val weight: String = "", val reps: String = "", val duration: String = "",
    val distance: String = "", val calories: String = "", val rpe: String = "",
    val note: String = "", val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

data class PreviewLoggerState(
    val exercise: Exercise?,
    val fields: LoggerFields = LoggerFields(),
    val sets: List<WorkoutSet> = emptyList(),
    val best: String = "No working sets yet",
    val last: String = "First workout",
    val bestLabel: String = "Heaviest working set",
    val plan: String? = null,
    val isWarmup: Boolean = false,
    val editingSetId: Long? = null,
    val canLog: Boolean = false,
    val saving: Boolean = false,
    val validation: String? = null,
    val restSeconds: Int = 90,
    val remainingSeconds: Int = 0,
    val timerRunning: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val superset: List<Exercise> = emptyList(),
    val smartQueue: String? = null,
    val personalBestSetIds: kotlin.collections.Set<Long> = emptySet(),
    val recordLabels: Map<Long, List<String>> = emptyMap(),
    val workoutId: Long? = null,
    val celebration: LoggerCelebration? = null
)

/** Rendering boundary: all persistence and navigation stay in the existing screen/ViewModel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewLoggerContent(
    state: PreviewLoggerState,
    onAction: (LoggerAction) -> Unit,
    onFieldChange: (LoggerField, String) -> Unit,
    onDistanceUnitChange: (DistanceUnit) -> Unit,
    onSelectExercise: (Long) -> Unit,
    onEditSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
    onSetNote: (WorkoutSet) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val actionColors = LocalLoggerActionColors.current
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showSetDetails by rememberSaveable(state.exercise?.id) { mutableStateOf(false) }
    val warmupSets = state.sets.filter { it.isWarmup }
    val workingSets = state.sets.filterNot { it.isWarmup }
    val hasWorkingSet = workingSets.any { it.isComplete }
    // Only the transition into working sets resets this choice; later sets respect an explicit expand.
    var warmupsExpanded by rememberSaveable(state.exercise?.id, state.workoutId, hasWorkingSet) {
        mutableStateOf(!hasWorkingSet)
    }
    LaunchedEffect(state.editingSetId) {
        if (state.editingSetId != null) {
            showSetDetails = state.fields.note.isNotBlank() || state.fields.rpe.isNotBlank()
            listState.animateScrollToItem(0)
        }
    }
    Box(modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onAction(LoggerAction.BACK) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to workout")
                }
                Text("Workout", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onAction(LoggerAction.FINISH) }, enabled = !state.saving) { Text("Finish") }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More workout options") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf(
                            "Home" to LoggerAction.HOME,
                            "Edit exercise" to LoggerAction.EDIT_EXERCISE,
                            (if (state.superset.isEmpty()) "Start superset" else "Add to superset") to LoggerAction.SUPERSET,
                            "Smart Log" to LoggerAction.SMART_LOG,
                            "Your theme" to LoggerAction.THEME
                        ).forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { showMenu = false; onAction(action) })
                        }
                    }
                }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAction(LoggerAction.ADD_EXERCISE) }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Exercise")
                }
                FilledTonalButton(
                    onClick = { onAction(LoggerAction.OVERVIEW) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Workout overview") }
            }
        }
    ) { padding ->
        val exercise = state.exercise
        if (exercise == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "exercise") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hasPrevious) {
                            IconButton(onClick = { onAction(LoggerAction.PREVIOUS) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous exercise")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${exercise.targetMuscle} · ${when {
                                    state.editingSetId != null -> "Editing set"
                                    state.isWarmup -> "Warm-up ${warmupSets.size + 1}"
                                    else -> "Working set ${workingSets.size + 1}"
                                }}",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                            )
                        }
                        if (state.hasNext) IconButton(onClick = { onAction(LoggerAction.NEXT) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next exercise")
                        }
                    }
                }
                item(key = "records") {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordTile("ALL-TIME BEST", state.best, state.bestLabel, Modifier.weight(1f).fillMaxHeight(), primary = true)
                        RecordTile("LAST WORKOUT", state.last, state.bestLabel, Modifier.weight(1f).fillMaxHeight(), primary = false)
                    }
                }
                item(key = "tools") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolButton("History", Icons.Default.History, Modifier.weight(1f)) { onAction(LoggerAction.HISTORY) }
                        if (exercise.logType in weightedTypes) {
                            ToolButton("Plates", Icons.Default.FitnessCenter, Modifier.weight(1f)) { onAction(LoggerAction.PLATES) }
                        }
                        ToolButton("Notes", Icons.Default.Notes, Modifier.weight(1f)) { onAction(LoggerAction.NOTES) }
                    }
                }
                state.plan?.let { plan -> item(key = "plan") {
                    Text(plan, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                } }
                if (state.superset.isNotEmpty()) item(key = "superset") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Superset", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onAction(LoggerAction.EXIT_SUPERSET) }) { Text("Exit superset") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.superset.forEach { member ->
                            FilterChip(
                                selected = member.id == exercise.id,
                                onClick = { onSelectExercise(member.id) },
                                label = { Text(member.name) }
                            )
                        }
                    }
                }
                state.smartQueue?.let { queue -> item(key = "queue") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(queue, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onAction(LoggerAction.CANCEL_SMART_LOG) }) { Text("Cancel queue") }
                    }
                } }
                item(key = "inputs") {
                    LoggerInputs(exercise.logType, state.fields, onFieldChange)
                    if (exercise.logType.usesDistanceUnit) {
                        var unitMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { unitMenu = true }) { Text("Distance: ${state.fields.distanceUnit.displayName}") }
                            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                                DistanceUnit.entries.forEach { unit ->
                                    DropdownMenuItem(text = { Text(unit.displayName) }, onClick = { onDistanceUnitChange(unit); unitMenu = false })
                                }
                            }
                        }
                    }
                }
                item(key = "extras") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                        if (exercise.isWarmupLibraryExercise) {
                            Text("Warm-up activity · excluded from records", style = MaterialTheme.typography.bodySmall)
                        } else {
                            FilterChip(
                                selected = state.isWarmup,
                                onClick = { onAction(LoggerAction.TOGGLE_WARMUP) },
                                label = { Text("Warm-up") },
                                leadingIcon = if (state.isWarmup) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null
                            )
                        }
                        TextButton(onClick = { showSetDetails = !showSetDetails }) {
                            Icon(Icons.Default.EditNote, null, Modifier.size(20.dp))
                            Text(if (state.fields.note.isNotBlank() || state.fields.rpe.isNotBlank()) "Note / RPE •" else "Note / RPE")
                        }
                    }
                    if (showSetDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.fields.note, onValueChange = { onFieldChange(LoggerField.NOTE, it) },
                                label = { Text("Set note") }, modifier = Modifier.fillMaxWidth(), maxLines = 3
                            )
                            OutlinedTextField(
                                value = state.fields.rpe, onValueChange = { onFieldChange(LoggerField.RPE, it) },
                                label = { Text("RPE (optional, 1–10)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                            )
                        }
                    }
                }
                item(key = "log") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.editingSetId != null) TextButton(onClick = { onAction(LoggerAction.CANCEL_EDIT) }, enabled = !state.saving) { Text("Cancel") }
                        Button(
                            onClick = { focus.clearFocus(); onAction(LoggerAction.LOG) },
                            enabled = state.canLog && !state.saving,
                            colors = ButtonDefaults.buttonColors(containerColor = actionColors.fill, contentColor = actionColors.onFill),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.saving) "Saving…" else if (state.editingSetId != null) "Save changes" else "Log set",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    state.validation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                }
                item(key = "timer") {
                    Surface(color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onAction(LoggerAction.TIMER) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer)) {
                                Icon(Icons.Default.Timer, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rest ${TimeFormatter.formatSecondsToMMSS(if (state.timerRunning) state.remainingSeconds else state.restSeconds)}", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onAction(LoggerAction.ADD_REST) }, colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer)) { Text("+30s") }
                            TextButton(
                                onClick = { onAction(if (state.timerRunning) LoggerAction.SKIP_TIMER else LoggerAction.START_TIMER) },
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer)
                            ) { Text(if (state.timerRunning) "Skip" else "Start") }
                        }
                    }
                }
                item(key = "sets-header") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("This workout", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${workingSets.size} working · ${warmupSets.size} warm-up", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    if (state.sets.isEmpty()) Text("Your first set goes here. Let's lift.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                }
                if (warmupSets.isNotEmpty()) {
                    item(key = "warmups-toggle") {
                        Surface(
                            onClick = { warmupsExpanded = !warmupsExpanded },
                            modifier = Modifier.fillMaxWidth().semantics {
                                stateDescription = if (warmupsExpanded) "Expanded" else "Collapsed"
                            },
                            color = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Whatshot, null, Modifier.size(20.dp), tint = colors.secondary)
                                Text("${warmupSets.size} warm-up ${if (warmupSets.size == 1) "set" else "sets"}", Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge)
                                Text(if (warmupsExpanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                                Icon(if (warmupsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        }
                    }
                    if (warmupsExpanded) {
                        itemsIndexed(warmupSets, key = { _, set -> set.id }) { index, set ->
                            CompactSetRow(set, index + 1, onEditSet, onDeleteSet, onSetNote)
                        }
                    }
                }
                itemsIndexed(workingSets, key = { _, set -> set.id }) { index, set ->
                    if (set.id in state.personalBestSetIds) {
                        PrSetRow(set, index + 1, state.recordLabels[set.id].orEmpty(), onEditSet, onDeleteSet, onSetNote)
                    } else {
                        CompactSetRow(set, index + 1, onEditSet, onDeleteSet, onSetNote)
                    }
                }
            }
        }
    }
    state.celebration?.let { event ->
        PrConfettiBurst(event.id, event.expiresAt - 4_000L, Modifier.fillMaxSize())
        PrCelebrationBanner(
            exerciseName = event.exerciseName,
            setSummary = previewSetSummary(event.set),
            recordLabels = event.recordLabels,
            onDismiss = { onAction(LoggerAction.DISMISS_PR) },
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 12.dp, end = 12.dp, top = 52.dp)
        )
    }
    }
}

private val weightedTypes = setOf(LogType.WEIGHT_REPS, LogType.WEIGHT_TIME, LogType.WEIGHT_DISTANCE)

@Composable
private fun RecordTile(title: String, value: String, caption: String, modifier: Modifier, primary: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = if (primary) colors.primaryContainer else colors.secondaryContainer,
        contentColor = if (primary) colors.onPrimaryContainer else colors.onSecondaryContainer
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(14.dp)) {
        if (LocalDensity.current.fontScale <= 1.3f) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoggerInputs(logType: LogType, fields: LoggerFields, onChange: (LoggerField, String) -> Unit) {
    val inputs = when (logType) {
        LogType.WEIGHT_REPS -> listOf(LoggerField.WEIGHT, LoggerField.REPS)
        LogType.REPS_ONLY -> listOf(LoggerField.REPS)
        LogType.DURATION -> listOf(LoggerField.DURATION)
        LogType.WEIGHT_DISTANCE -> listOf(LoggerField.WEIGHT, LoggerField.DISTANCE)
        LogType.DISTANCE_TIME -> listOf(LoggerField.DISTANCE, LoggerField.DURATION)
        LogType.WEIGHT_TIME -> listOf(LoggerField.WEIGHT, LoggerField.DURATION)
        LogType.CALORIES_TIME -> listOf(LoggerField.CALORIES, LoggerField.DURATION)
    }
    BoxWithConstraints {
        val stack = maxWidth < 328.dp || LocalDensity.current.fontScale > 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                inputs.forEach { NumericInput(it, fields, onChange, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inputs.forEach { NumericInput(it, fields, onChange, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NumericInput(field: LoggerField, fields: LoggerFields, onChange: (LoggerField, String) -> Unit, modifier: Modifier) {
    if (field == LoggerField.WEIGHT || field == LoggerField.REPS) {
        RulerSliderInput(
            label = if (field == LoggerField.WEIGHT) "WEIGHT / lb" else "REPS",
            value = if (field == LoggerField.WEIGHT) fields.weight else fields.reps,
            onValueChange = { onChange(field, it) }, step = if (field == LoggerField.WEIGHT) 5 else 1,
            minValue = if (field == LoggerField.WEIGHT) 0 else 1,
            centered = field == LoggerField.WEIGHT, compact = true, modifier = modifier
        )
    } else {
        val focus = LocalFocusManager.current
        val (label, value) = when (field) {
            LoggerField.DURATION -> "Time (mm:ss)" to fields.duration
            LoggerField.DISTANCE -> "Distance (${fields.distanceUnit.shortLabel})" to fields.distance
            else -> "Calories" to fields.calories
        }
        OutlinedTextField(
            value = value, onValueChange = { onChange(field, it) }, label = { Text(label) },
            modifier = modifier.heightIn(min = 96.dp), singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = if (field == LoggerField.DURATION) KeyboardType.Text else KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun CompactSetRow(set: WorkoutSet, number: Int, onEdit: (WorkoutSet) -> Unit, onDelete: (WorkoutSet) -> Unit, onNote: (WorkoutSet) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (set.isWarmup) "W" else number.toString(), Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.weight(1f).clickable(onClickLabel = "Edit set $number") { onEdit(set) }.padding(vertical = 6.dp)) {
                Text(previewSetSummary(set), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                set.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                set.rpe?.let { Text("RPE $it", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
            }
            IconButton(onClick = { onEdit(set) }) { Icon(Icons.Default.Edit, "Edit set $number", Modifier.size(20.dp)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for set $number", Modifier.size(20.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Set note") }, onClick = { menu = false; onNote(set) })
                    DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete(set) })
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

fun previewSetSummary(set: WorkoutSet): String = buildList {
    set.weight?.let { add("${previewNumber(it)} lb") }
    set.reps?.let { add("$it reps") }
    set.distanceValue?.let { add("${previewNumber(it)} ${set.distanceUnit?.shortLabel ?: "m"}") }
        ?: set.distanceMeters?.let { add("${previewNumber(it)} m") }
    set.durationSeconds?.let { add(TimeFormatter.formatSecondsToMMSS(it)) }
    set.calories?.let { add("${previewNumber(it)} cal") }
}.joinToString(" × ")

private fun previewNumber(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else value.toString()

private val LogType.usesDistanceUnit: Boolean
    get() = this == LogType.WEIGHT_DISTANCE || this == LogType.DISTANCE_TIME

private val DistanceUnit.displayName: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private val DistanceUnit.shortLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.KILOMETERS -> "km"
        DistanceUnit.YARDS -> "yd"
        DistanceUnit.FEET -> "ft"
        DistanceUnit.MILES -> "mi"
        DistanceUnit.STEPS -> "steps"
        DistanceUnit.FLOORS -> "floors"
    }
