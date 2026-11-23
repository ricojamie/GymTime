package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.isWarmupLibraryExercise
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.exercise.LoggerCelebration
import com.example.gymtime.ui.exercise.LoggingInput
import com.example.gymtime.ui.exercise.LoggingInputs
import com.example.gymtime.ui.exercise.LoggingKeypad
import com.example.gymtime.ui.exercise.inputFields
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.util.TimeFormatter
import kotlinx.coroutines.flow.StateFlow

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
    modifier: Modifier = Modifier,
    remainingSecondsFlow: StateFlow<Int>? = null
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showSetDetails by rememberSaveable(state.exercise?.id) { mutableStateOf(false) }
    var activeInput by rememberSaveable(state.exercise?.id, state.exercise?.logType, state.editingSetId) {
        mutableStateOf<LoggerField?>(null)
    }
    val warmupSets = remember(state.sets) { state.sets.filter { it.isWarmup } }
    val workingSets = remember(state.sets) { state.sets.filterNot { it.isWarmup } }
    val hasWorkingSet = remember(workingSets) { workingSets.any { it.isComplete } }
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
    activeInput?.let { field ->
        val order = state.exercise?.logType?.inputFields().orEmpty() +
            if (showSetDetails) listOf(LoggerField.RPE) else emptyList()
        LoggingKeypad(
            field = field,
            fields = state.fields,
            order = order,
            onChange = onFieldChange,
            onSelect = { activeInput = it },
            canLog = state.canLog && !state.saving,
            editing = state.editingSetId != null,
            onLog = { onAction(LoggerAction.LOG) }
        )
    }
    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = colors.background,
            topBar = {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onAction(LoggerAction.BACK) },
                            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to workout")
                        }
                        Text(
                            text = "Workout",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { onAction(LoggerAction.FINISH) },
                            enabled = !state.saving,
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Finish", fontWeight = FontWeight.SemiBold)
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                            ) {
                                Icon(Icons.Default.MoreVert, "More workout options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                listOf(
                                    "Home" to LoggerAction.HOME,
                                    "Edit exercise" to LoggerAction.EDIT_EXERCISE,
                                    (if (state.superset.isEmpty()) "Start superset" else "Add to superset") to LoggerAction.SUPERSET,
                                    "Smart Log" to LoggerAction.SMART_LOG,
                                    "Your theme" to LoggerAction.THEME
                                ).forEach { (label, action) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showMenu = false
                                            onAction(action)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IronLogDivider(insetStart = 0.dp)
                }
            },
            bottomBar = {
                Surface(
                    color = colors.surface,
                    contentColor = colors.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        IronLogDivider(insetStart = 0.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onAction(LoggerAction.ADD_EXERCISE) },
                                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Exercise", fontWeight = FontWeight.SemiBold)
                            }
                            IronLogSecondaryButton(
                                text = "Workout overview",
                                onClick = { onAction(LoggerAction.OVERVIEW) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val exercise = state.exercise
            if (exercise == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    contentPadding = PaddingValues(horizontal = IronLogSpace.ScreenHorizontal, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "exercise") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.hasPrevious) {
                                IconButton(
                                    onClick = { onAction(LoggerAction.PREVIOUS) },
                                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous exercise")
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${exercise.targetMuscle} · ${when {
                                        state.editingSetId != null -> "Editing set"
                                        state.isWarmup -> "Warm-up ${warmupSets.size + 1}"
                                        else -> "Working set ${workingSets.size + 1}"
                                    }}",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            if (state.hasNext) {
                                IconButton(
                                    onClick = { onAction(LoggerAction.NEXT) },
                                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next exercise")
                                }
                            }
                        }
                    }
                    item(key = "records") {
                        IronLogGroupedCard(showBorder = true) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RecordMetricCell(
                                    title = "ALL-TIME BEST",
                                    value = state.best,
                                    caption = state.bestLabel,
                                    emphasized = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = IronLogSpace.ItemGap)
                                )
                                VerticalDivider(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 2.dp),
                                    thickness = IronLogBorder.Hairline,
                                    color = IronLogBorder.dividerColor()
                                )
                                RecordMetricCell(
                                    title = "LAST WORKOUT",
                                    value = state.last,
                                    caption = state.bestLabel,
                                    emphasized = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = IronLogSpace.ItemGap)
                                )
                            }
                        }
                    }
                    item(key = "tools") {
                        Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                            ToolButton("History", Icons.Default.History, Modifier.weight(1f)) {
                                onAction(LoggerAction.HISTORY)
                            }
                            if (exercise.logType in weightedTypes) {
                                ToolButton("Plates", Icons.Default.FitnessCenter, Modifier.weight(1f)) {
                                    onAction(LoggerAction.PLATES)
                                }
                            }
                            ToolButton("Notes", Icons.Default.Notes, Modifier.weight(1f)) {
                                onAction(LoggerAction.NOTES)
                            }
                        }
                    }
                    state.plan?.let { plan ->
                        item(key = "plan") {
                            IronLogCard(
                                showBorder = false,
                                shape = IronLogRadius.ControlShape,
                                containerColor = colors.surfaceContainerLow,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "PLANNED",
                                    style = IronLogType.sectionEyebrow(),
                                    color = colors.primary
                                )
                                Text(
                                    text = plan,
                                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (state.superset.isNotEmpty()) {
                        item(key = "superset") {
                            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IronLogStatusChip(
                                        label = "SUPERSET",
                                        tone = IronLogStatusTone.SUPERSET,
                                        icon = Icons.Default.Sync
                                    )
                                    TextButton(
                                        onClick = { onAction(LoggerAction.EXIT_SUPERSET) },
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                    ) {
                                        Text("Exit superset")
                                    }
                                }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                ) {
                                    state.superset.forEach { member ->
                                        FilterChip(
                                            selected = member.id == exercise.id,
                                            onClick = { onSelectExercise(member.id) },
                                            label = { Text(member.name) },
                                            shape = IronLogRadius.ControlShape,
                                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    state.smartQueue?.let { queue ->
                        item(key = "queue") {
                            IronLogCard(
                                highlighted = true,
                                showBorder = true,
                                shape = IronLogRadius.ControlShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = queue,
                                        modifier = Modifier.weight(1f),
                                        style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(
                                        onClick = { onAction(LoggerAction.CANCEL_SMART_LOG) },
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                    ) {
                                        Text("Cancel queue")
                                    }
                                }
                            }
                        }
                    }
                    item(key = "inputs") {
                        LoggingInputs(exercise.logType, state.fields) {
                            focus.clearFocus()
                            activeInput = it
                        }
                        if (exercise.logType.usesDistanceUnit) {
                            var unitMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { unitMenu = true },
                                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                ) {
                                    Text("Distance: ${state.fields.distanceUnit.displayName}")
                                }
                                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                                    DistanceUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.displayName) },
                                            onClick = {
                                                onDistanceUnitChange(unit)
                                                unitMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "extras") {
                        Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (exercise.isWarmupLibraryExercise) {
                                    Row(
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                    ) {
                                        IronLogStatusChip(label = "WARMUP", tone = IronLogStatusTone.WARMUP)
                                        Text(
                                            text = "Warm-up activity · excluded from records",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    FilterChip(
                                        selected = state.isWarmup,
                                        onClick = { onAction(LoggerAction.TOGGLE_WARMUP) },
                                        label = { Text("Warm-up") },
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                                        shape = IronLogRadius.ControlShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.tertiaryContainer,
                                            selectedLabelColor = colors.onTertiaryContainer,
                                            selectedLeadingIconColor = colors.onTertiaryContainer
                                        ),
                                        leadingIcon = if (state.isWarmup) {
                                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                        } else null
                                    )
                                }
                                TextButton(
                                    onClick = { showSetDetails = !showSetDetails },
                                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                ) {
                                    Icon(Icons.Default.EditNote, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (state.fields.note.isNotBlank() || state.fields.rpe.isNotBlank()) {
                                            "Note / RPE •"
                                        } else {
                                            "Note / RPE"
                                        }
                                    )
                                }
                            }
                            if (showSetDetails) {
                                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                                    OutlinedTextField(
                                        value = state.fields.note,
                                        onValueChange = { onFieldChange(LoggerField.NOTE, it) },
                                        label = { Text("Set note") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        shape = IronLogRadius.ControlShape,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = colors.surfaceContainerLow,
                                            unfocusedContainerColor = colors.surfaceContainerLow,
                                            focusedBorderColor = colors.primary,
                                            unfocusedBorderColor = colors.outlineVariant
                                        )
                                    )
                                    LoggingInput(
                                        field = LoggerField.RPE,
                                        fields = state.fields,
                                        onSelect = {
                                            focus.clearFocus()
                                            activeInput = it
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    item(key = "log") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.editingSetId != null) {
                                    TextButton(
                                        onClick = { onAction(LoggerAction.CANCEL_EDIT) },
                                        enabled = !state.saving,
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                                IronLogPrimaryButton(
                                    text = if (state.saving) "Saving…" else if (state.editingSetId != null) "Save changes" else "Log set",
                                    onClick = {
                                        focus.clearFocus()
                                        onAction(LoggerAction.LOG)
                                    },
                                    enabled = state.canLog && !state.saving,
                                    loading = state.saving,
                                    leadingIcon = Icons.Default.Check,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp),
                                    shape = IronLogRadius.ControlShape
                                )
                            }
                            state.validation?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                            }
                        }
                    }
                    item(key = "timer") {
                        RestTimerCard(
                            restSeconds = state.restSeconds,
                            fallbackRemainingSeconds = state.remainingSeconds,
                            remainingSecondsFlow = remainingSecondsFlow,
                            timerRunning = state.timerRunning,
                            onAction = onAction
                        )
                    }
                    item(key = "sets-header") {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "This workout",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${workingSets.size} working · ${warmupSets.size} warm-up",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            if (state.sets.isEmpty()) {
                                Text(
                                    text = "Your first set goes here. Let's lift.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                    if (warmupSets.isNotEmpty()) {
                        item(key = "warmups-toggle") {
                            IronLogCard(
                                onClick = { warmupsExpanded = !warmupsExpanded },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        stateDescription = if (warmupsExpanded) "Expanded" else "Collapsed"
                                    },
                                containerColor = colors.surfaceContainerHigh,
                                showBorder = false,
                                shape = IronLogRadius.ControlShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 36.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.tertiary
                                    )
                                    Text(
                                        text = "${warmupSets.size} warm-up ${if (warmupSets.size == 1) "set" else "sets"}",
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp),
                                        style = IronLogType.tabular(MaterialTheme.typography.labelLarge)
                                    )
                                    Text(
                                        text = if (warmupsExpanded) "Hide" else "Show",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (warmupsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant
                                    )
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 12.dp, end = 12.dp, top = 52.dp)
            )
        }
    }
}

@Composable
private fun RestTimerCard(
    restSeconds: Int,
    fallbackRemainingSeconds: Int,
    remainingSecondsFlow: StateFlow<Int>?,
    timerRunning: Boolean,
    onAction: (LoggerAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val liveRemaining = remainingSecondsFlow?.collectAsStateWithLifecycle()?.value ?: fallbackRemainingSeconds
    val displaySeconds = if (timerRunning) liveRemaining else restSeconds
    val contentColor = if (timerRunning) colors.onPrimaryContainer else colors.onSurface
    IronLogCard(
        highlighted = timerRunning,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        containerColor = if (timerRunning) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = contentColor,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onAction(LoggerAction.TIMER) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
            ) {
                Icon(Icons.Default.Timer, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Rest ${TimeFormatter.formatSecondsToMMSS(displaySeconds)}",
                    style = IronLogType.tabular(MaterialTheme.typography.labelLarge).copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            TextButton(
                onClick = { onAction(LoggerAction.ADD_REST) },
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
            ) {
                Text(
                    text = "+30s",
                    style = IronLogType.tabular(MaterialTheme.typography.labelLarge).copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            TextButton(
                onClick = { onAction(if (timerRunning) LoggerAction.SKIP_TIMER else LoggerAction.START_TIMER) },
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (timerRunning) contentColor else colors.primary
                )
            ) {
                Text(
                    text = if (timerRunning) "Skip" else "Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val weightedTypes = setOf(LogType.WEIGHT_REPS, LogType.WEIGHT_TIME, LogType.WEIGHT_DISTANCE)

@Composable
private fun RecordMetricCell(
    title: String,
    value: String,
    caption: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = IronLogType.sectionEyebrow(),
            color = if (emphasized) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                fontWeight = FontWeight.Bold
            ),
            color = colors.onSurface
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = IronLogSpace.TouchTargetMin),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = IronLogRadius.ControlShape,
        border = IronLogBorder.stroke(colors.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceContainerLow,
            contentColor = colors.onSurface
        )
    ) {
        if (LocalDensity.current.fontScale <= 1.3f) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun CompactSetRow(
    set: WorkoutSet,
    number: Int,
    onEdit: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onNote: (WorkoutSet) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (set.isWarmup) "W" else number.toString(),
                modifier = Modifier.width(28.dp),
                style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                color = colors.onSurfaceVariant
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Edit set $number") { onEdit(set) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = previewSetSummary(set),
                        fontWeight = FontWeight.Bold,
                        style = IronLogType.tabular(MaterialTheme.typography.bodyLarge),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (set.isWarmup) {
                        IronLogStatusChip(label = "WARMUP", tone = IronLogStatusTone.WARMUP)
                    }
                }
                set.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                set.rpe?.let {
                    Text(
                        text = "RPE $it",
                        style = IronLogType.tabular(MaterialTheme.typography.labelSmall),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = { onEdit(set) },
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(Icons.Default.Edit, "Edit set $number", Modifier.size(20.dp))
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(Icons.Default.MoreVert, "Options for set $number", Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Set note") }, onClick = { menu = false; onNote(set) })
                    DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete(set) })
                }
            }
        }
        IronLogDivider(insetStart = 28.dp)
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

internal val DistanceUnit.shortLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.KILOMETERS -> "km"
        DistanceUnit.YARDS -> "yd"
        DistanceUnit.FEET -> "ft"
        DistanceUnit.MILES -> "mi"
        DistanceUnit.STEPS -> "steps"
        DistanceUnit.FLOORS -> "floors"
    }
