package com.example.gymtime.ui.exercise.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.isWarmupLibraryExercise
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.exercise.LoggerCelebration
import com.example.gymtime.ui.exercise.LoggingInput
import com.example.gymtime.ui.exercise.LoggingInputs
import com.example.gymtime.ui.exercise.LoggingKeypad
import com.example.gymtime.ui.exercise.inputFields
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.ui.theme.obsidianAmbientBackdrop
import com.example.gymtime.ui.theme.shakeOnTrigger
import com.example.gymtime.util.TimeFormatter
import kotlinx.coroutines.awaitCancellation
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
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

    var prevSetCount by remember(state.exercise?.id, state.workoutId) { mutableIntStateOf(state.sets.size) }
    var logPulseTrigger by remember { mutableIntStateOf(0) }
    val logPulseAnim = remember { Animatable(0f) }

    LaunchedEffect(state.sets.size) {
        if (state.sets.size > prevSetCount) {
            logPulseTrigger++
        }
        prevSetCount = state.sets.size
    }

    LaunchedEffect(logPulseTrigger, reducedMotion) {
        if (logPulseTrigger > 0 && !reducedMotion) {
            logPulseAnim.snapTo(0.001f)
            logPulseAnim.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            logPulseAnim.snapTo(0f)
        } else {
            logPulseAnim.snapTo(0f)
        }
    }

    var lastValidation by remember { mutableStateOf<String?>(null) }
    var invalidShakeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.validation) {
        if (state.validation != null && state.validation != lastValidation) {
            invalidShakeTrigger++
            haptics.reject()
        }
        lastValidation = state.validation
    }

    var lastCelebration by remember { mutableStateOf(state.celebration) }
    if (state.celebration != null) {
        lastCelebration = state.celebration
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .obsidianAmbientBackdrop(accent = surfaces.accent, isDark = surfaces.isDark)
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaces.glassDockFill.copy(alpha = if (surfaces.isDark) 0.72f else 0.84f))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptics.keyTap()
                                onAction(LoggerAction.BACK)
                            },
                            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to workout")
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IronLogLedPip(
                                color = surfaces.ledGlow,
                                size = 5.dp,
                                pulsing = state.timerRunning
                            )
                            Text(
                                text = "Workout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onAction(LoggerAction.FINISH)
                            },
                            enabled = !state.saving,
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Finish", fontWeight = FontWeight.SemiBold)
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    showMenu = true
                                },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaces.glassDockFill)
                        .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                haptics.keyTap()
                                onAction(LoggerAction.ADD_EXERCISE)
                            },
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
                                    onClick = {
                                        haptics.keyTap()
                                        onAction(LoggerAction.PREVIOUS)
                                    },
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
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "${exercise.targetMuscle} · ${when {
                                        state.editingSetId != null -> "Editing set"
                                        state.isWarmup -> "Warm-up ${warmupSets.size + 1}"
                                        else -> "Working set ${workingSets.size + 1}"
                                    }}",
                                    style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            if (state.hasNext) {
                                IconButton(
                                    onClick = {
                                        haptics.keyTap()
                                        onAction(LoggerAction.NEXT)
                                    },
                                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next exercise")
                                }
                            }
                        }
                    }

                    item(key = "records") {
                        IronLogCard(
                            spotlight = true,
                            showBorder = true,
                            shape = IronLogRadius.PlateShape,
                            contentPadding = PaddingValues(horizontal = IronLogSpace.CardPadding, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
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
                                showBorder = true,
                                shape = IronLogRadius.KeycapShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "PLANNED",
                                    style = IronLogType.technicalEyebrow(),
                                    color = surfaces.accent
                                )
                                Text(
                                    text = plan,
                                    style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
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
                                        onClick = {
                                            haptics.tick()
                                            onAction(LoggerAction.EXIT_SUPERSET)
                                        },
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
                                        IronLogFilterPill(
                                            label = member.name,
                                            selected = member.id == exercise.id,
                                            onClick = { onSelectExercise(member.id) },
                                            showLedPip = true
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
                                spotlight = true,
                                showBorder = true,
                                shape = IronLogRadius.KeycapShape,
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
                                        tint = surfaces.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = queue,
                                        modifier = Modifier.weight(1f),
                                        style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(
                                        onClick = {
                                            haptics.tick()
                                            onAction(LoggerAction.CANCEL_SMART_LOG)
                                        },
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                    ) {
                                        Text("Cancel queue")
                                    }
                                }
                            }
                        }
                    }

                    item(key = "inputs") {
                        LoggingInputs(
                            type = exercise.logType,
                            fields = state.fields,
                            activeField = activeInput
                        ) {
                            focus.clearFocus()
                            activeInput = it
                        }
                        if (exercise.logType.usesDistanceUnit) {
                            var unitMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = {
                                        haptics.tick()
                                        unitMenu = true
                                    },
                                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                ) {
                                    Text("Distance: ${state.fields.distanceUnit.displayName}")
                                }
                                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                                    DistanceUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.displayName) },
                                            onClick = {
                                                haptics.segmentTick()
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
                                    WarmupTogglePill(
                                        checked = state.isWarmup,
                                        onToggle = {
                                            haptics.toggle(!state.isWarmup)
                                            onAction(LoggerAction.TOGGLE_WARMUP)
                                        }
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        haptics.tick()
                                        showSetDetails = !showSetDetails
                                    },
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

                            AnimatedVisibility(
                                visible = showSetDetails,
                                enter = if (reducedMotion) {
                                    fadeIn(tween(80))
                                } else {
                                    fadeIn(IronMotion.EffectDefault) +
                                        expandVertically(IronMotion.SpatialLayoutSize)
                                },
                                exit = if (reducedMotion) {
                                    fadeOut(tween(80))
                                } else {
                                    fadeOut(IronMotion.EffectFast) +
                                        shrinkVertically(IronMotion.SpatialLayoutSize)
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                                    var noteFocused by remember { mutableStateOf(false) }
                                    IronLogRecessedWell(
                                        active = noteFocused,
                                        accentColor = surfaces.accent,
                                        shape = IronLogRadius.KeycapShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "SET NOTE",
                                                style = IronLogType.technicalEyebrow(),
                                                color = if (noteFocused) surfaces.accent else colors.onSurfaceVariant
                                            )
                                            BasicTextField(
                                                value = state.fields.note,
                                                onValueChange = { onFieldChange(LoggerField.NOTE, it) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onFocusChanged { noteFocused = it.isFocused },
                                                maxLines = 3,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = colors.onSurface
                                                ),
                                                cursorBrush = SolidColor(surfaces.accent),
                                                decorationBox = { innerTextField ->
                                                    Box {
                                                        if (state.fields.note.isEmpty()) {
                                                            Text(
                                                                text = "Add a cue or feeling…",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = colors.onSurfaceVariant.copy(alpha = 0.55f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    LoggingInput(
                                        field = LoggerField.RPE,
                                        fields = state.fields,
                                        onSelect = {
                                            focus.clearFocus()
                                            activeInput = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        isActive = activeInput == LoggerField.RPE
                                    )
                                }
                            }
                        }
                    }

                    item(key = "log") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.shakeOnTrigger(invalidShakeTrigger),
                                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.editingSetId != null) {
                                    TextButton(
                                        onClick = {
                                            haptics.tick()
                                            onAction(LoggerAction.CANCEL_EDIT)
                                        },
                                        enabled = !state.saving,
                                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .drawWithCache {
                                            val corner = IronLogRadius.Control.toPx()
                                            val cornerRadius = CornerRadius(corner, corner)
                                            val baseStrokePx = 1.dp.toPx()
                                            val deltaStrokePx = 3.dp.toPx()
                                            onDrawWithContent {
                                                drawContent()
                                                val p = logPulseAnim.value
                                                if (p in 0.001f..0.999f) {
                                                    val alpha = (1f - p) * 0.65f
                                                    val strokeW = baseStrokePx + deltaStrokePx * (1f - p)
                                                    drawRoundRect(
                                                        color = surfaces.accent.copy(alpha = alpha),
                                                        cornerRadius = cornerRadius,
                                                        style = Stroke(width = strokeW)
                                                    )
                                                }
                                            }
                                        }
                                ) {
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
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp),
                                        shape = IronLogRadius.ControlShape
                                    )
                                }
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
                                    style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
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
                                    .animateContentSize(
                                        IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)
                                    )
                                    .semantics {
                                        stateDescription = if (warmupsExpanded) "Expanded" else "Collapsed"
                                    },
                                showBorder = true,
                                shape = IronLogRadius.KeycapShape,
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
                                        style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge)
                                    )
                                    Text(
                                        text = if (warmupsExpanded) "Hide" else "Show",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = surfaces.accent,
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
                                CompactSetRow(
                                    set = set,
                                    number = index + 1,
                                    onEdit = onEditSet,
                                    onDelete = onDeleteSet,
                                    onNote = onSetNote,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = if (reducedMotion) null else IronMotion.EffectDefault,
                                        placementSpec = if (reducedMotion) null else IronMotion.SpatialLayoutOffset,
                                        fadeOutSpec = if (reducedMotion) null else IronMotion.EffectFast
                                    )
                                )
                            }
                        }
                    }

                    itemsIndexed(workingSets, key = { _, set -> set.id }) { index, set ->
                        val itemMod = Modifier.animateItem(
                            fadeInSpec = if (reducedMotion) null else IronMotion.EffectDefault,
                            placementSpec = if (reducedMotion) null else IronMotion.SpatialLayoutOffset,
                            fadeOutSpec = if (reducedMotion) null else IronMotion.EffectFast
                        )
                        if (set.id in state.personalBestSetIds) {
                            PrSetRow(
                                set = set,
                                number = index + 1,
                                recordLabels = state.recordLabels[set.id].orEmpty(),
                                onEdit = onEditSet,
                                onDelete = onDeleteSet,
                                onNote = onSetNote,
                                modifier = itemMod
                            )
                        } else {
                            CompactSetRow(
                                set = set,
                                number = index + 1,
                                onEdit = onEditSet,
                                onDelete = onDeleteSet,
                                onNote = onSetNote,
                                modifier = itemMod
                            )
                        }
                    }
                }
            }
        }

        state.celebration?.let { event ->
            PrConfettiBurst(event.id, event.expiresAt - 4_000L, Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = state.celebration != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 12.dp, end = 12.dp, top = 52.dp),
            enter = if (reducedMotion) {
                fadeIn(tween(90))
            } else {
                fadeIn(IronMotion.EffectDefault) +
                    scaleIn(animationSpec = IronMotion.RewardPop, initialScale = 0.92f) +
                    slideInVertically(animationSpec = IronMotion.SpatialLayoutOffset) { -it / 4 }
            },
            exit = if (reducedMotion) {
                fadeOut(tween(90))
            } else {
                fadeOut(IronMotion.EffectFast) +
                    scaleOut(animationSpec = IronMotion.EffectFast, targetScale = 0.94f) +
                    slideOutVertically(
                        animationSpec = IronMotion.effectFast(visibilityThreshold = IntOffset.VisibilityThreshold)
                    ) { -it / 4 }
            }
        ) {
            lastCelebration?.let { event ->
                PrCelebrationBanner(
                    exerciseName = event.exerciseName,
                    setSummary = previewSetSummary(event.set),
                    recordLabels = event.recordLabels,
                    onDismiss = { onAction(LoggerAction.DISMISS_PR) }
                )
            }
        }
    }
}

@Composable
private fun WarmupTogglePill(
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.95f,
            highlightAlpha = 0.08f,
            cornerRadius = IronLogRadius.Capsule
        )
    }

    val warmAccent = colors.tertiary
    val borderColorState = animateColorAsState(
        targetValue = if (checked) warmAccent.copy(alpha = 0.72f) else colors.outlineVariant.copy(alpha = 0.65f),
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "warmupPillBorder"
    )
    val contentColorState = animateColorAsState(
        targetValue = if (checked) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = if (reducedMotion) snap() else IronMotion.effectFast(),
        label = "warmupPillText"
    )
    val bgBrush = remember(checked, surfaces, warmAccent) {
        if (checked) {
            Brush.verticalGradient(
                listOf(
                    surfaces.plateElevatedTop,
                    surfaces.plateElevatedBottom
                )
            )
        } else {
            Brush.verticalGradient(listOf(surfaces.plateTop, surfaces.plateBottom))
        }
    }

    Box(
        modifier = Modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = pressIndication,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .clip(IronLogRadius.CapsuleShape)
                .background(brush = bgBrush)
                .drawWithCache {
                    val strokeWidth = (if (checked) 1.25.dp else IronLogBorder.Hairline).toPx()
                    val inset = strokeWidth * 0.5f
                    val corner = IronLogRadius.Capsule.toPx()
                    val strokeSize = Size(
                        (size.width - strokeWidth).coerceAtLeast(1f),
                        (size.height - strokeWidth).coerceAtLeast(1f)
                    )
                    val stroke = Stroke(width = strokeWidth)
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = borderColorState.value,
                            topLeft = Offset(inset, inset),
                            size = strokeSize,
                            cornerRadius = CornerRadius(corner, corner),
                            style = stroke
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IronLogLedPip(
                color = if (checked) warmAccent else colors.onSurfaceVariant.copy(alpha = 0.35f),
                size = 5.dp,
                pulsing = checked
            )
            BasicText(
                text = "Warm-up",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                ),
                color = { contentColorState.value },
                maxLines = 1
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val liveRemaining = remainingSecondsFlow?.collectAsStateWithLifecycle()?.value ?: fallbackRemainingSeconds
    val displaySeconds = if (timerRunning) liveRemaining else restSeconds
    val isUrgent = timerRunning && displaySeconds in 1..3

    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            isStarted = true
            try {
                awaitCancellation()
            } finally {
                isStarted = false
            }
        }
    }

    val urgencyBreathState: State<Float>? = if (isUrgent && !reducedMotion && isStarted) {
        val transition = rememberInfiniteTransition(label = "restTimerUrgency")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "restTimerUrgencyAlpha"
        )
    } else {
        null
    }

    var prevRunning by remember { mutableStateOf(timerRunning) }
    var prevDisplay by remember { mutableIntStateOf(displaySeconds) }
    var completionTrigger by remember { mutableIntStateOf(0) }
    val completionScale = remember { Animatable(1f) }
    LaunchedEffect(timerRunning, displaySeconds) {
        val justFinishedNaturally = prevRunning && prevDisplay == 1 && (displaySeconds == 0 || !timerRunning)
        prevRunning = timerRunning
        prevDisplay = displaySeconds
        if (justFinishedNaturally) {
            completionTrigger++
        }
    }
    LaunchedEffect(completionTrigger, reducedMotion) {
        if (completionTrigger > 0) {
            haptics.confirm()
            if (!reducedMotion) {
                completionScale.snapTo(1.035f)
                completionScale.animateTo(1f, IronMotion.RewardPop)
            }
        }
    }

    IronLogCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = completionScale.value
                scaleX = s
                scaleY = s
            }
            .drawWithCache {
                val corner = IronLogRadius.Plate.toPx()
                val cornerRadius = CornerRadius(corner, corner)
                val borderStroke = Stroke(width = 1.5.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    if (isUrgent) {
                        val breath = urgencyBreathState?.value ?: 0.5f
                        drawRoundRect(
                            color = colors.error.copy(alpha = 0.45f + 0.45f * breath),
                            cornerRadius = cornerRadius,
                            style = borderStroke
                        )
                    }
                }
            },
        highlighted = timerRunning,
        spotlight = timerRunning,
        showBorder = !isUrgent,
        shape = IronLogRadius.PlateShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = {
                    haptics.keyTap()
                    onAction(LoggerAction.TIMER)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IronLogLedPip(
                        color = if (isUrgent) colors.error else if (timerRunning) surfaces.ledGlow else colors.onSurfaceVariant.copy(alpha = 0.4f),
                        size = 5.dp,
                        pulsing = timerRunning
                    )
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (timerRunning) surfaces.accent else colors.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val timerColor = if (isUrgent) colors.error else colors.onSurface
                        val timerStyle = IronLogType.tabular(MaterialTheme.typography.labelLarge).copy(
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Rest",
                            style = timerStyle,
                            color = timerColor,
                            maxLines = 1
                        )
                        OdometerText(
                            text = TimeFormatter.formatSecondsToMMSS(displaySeconds),
                            style = timerStyle,
                            color = timerColor,
                            maxLines = 1
                        )
                    }
                }
            }
            IronLogSculptedKeycap(
                onClick = { onAction(LoggerAction.ADD_REST) },
                hapticType = KeycapHaptic.TICK,
                shape = IronLogRadius.KeycapShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "+30s",
                    style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge).copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            IronLogSculptedKeycap(
                onClick = { onAction(if (timerRunning) LoggerAction.SKIP_TIMER else LoggerAction.START_TIMER) },
                accented = !timerRunning,
                hapticType = KeycapHaptic.KEY_TAP,
                shape = IronLogRadius.KeycapShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
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
    val surfaces = LocalIronLogSurfaces.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (emphasized) {
                IronLogLedPip(color = surfaces.ledGlow, size = 4.dp)
            }
            Text(
                text = title,
                style = IronLogType.technicalEyebrow(),
                color = if (emphasized) surfaces.accent else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OdometerText(
            text = value,
            style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                fontWeight = FontWeight.Bold
            ),
            color = colors.onSurface
        )
        Text(
            text = caption,
            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall),
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
    val surfaces = LocalIronLogSurfaces.current
    IronLogSculptedKeycap(
        onClick = onClick,
        modifier = modifier.heightIn(min = IronLogSpace.TouchTargetMin),
        hapticType = KeycapHaptic.KEY_TAP,
        shape = IronLogRadius.KeycapShape,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (LocalDensity.current.fontScale <= 1.3f) {
                Icon(icon, null, Modifier.size(18.dp), tint = surfaces.accent)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactSetRow(
    set: WorkoutSet,
    number: Int,
    onEdit: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onNote: (WorkoutSet) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.985f,
            highlightAlpha = 0.05f,
            cornerRadius = IronLogRadius.Keycap
        )
    }
    val indexText = if (set.isWarmup) "W" else number.toString().padStart(2, '0')

    Column(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = indexText,
                modifier = Modifier.width(34.dp),
                style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge),
                color = colors.onSurfaceVariant
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(IronLogRadius.KeycapShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = pressIndication,
                        onClickLabel = "Edit set $number"
                    ) {
                        haptics.keyTap()
                        onEdit(set)
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
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
                        style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = {
                    haptics.keyTap()
                    onEdit(set)
                },
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(Icons.Default.Edit, "Edit set $number", Modifier.size(20.dp))
            }
            Box {
                IconButton(
                    onClick = {
                        haptics.tick()
                        menu = true
                    },
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
        IronLogDivider(insetStart = 34.dp)
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
