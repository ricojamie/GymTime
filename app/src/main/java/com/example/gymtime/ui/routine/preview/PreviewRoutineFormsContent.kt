package com.example.gymtime.ui.routine.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.components.IronLogTactileSwitch
import com.example.gymtime.ui.routine.canMoveRoutineExerciseBlock
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.staggeredEntranceOnce

data class RoutineFormUiState(
    val name: String = "",
    val editing: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val canEdit: Boolean = true,
    val canSave: Boolean = false,
    val error: String? = null
)

data class RoutineDayFormUiState(
    val name: String = "",
    val editing: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val canEdit: Boolean = true,
    val canSave: Boolean = false,
    val error: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val sets: Map<Long, String> = emptyMap(),
    val repMin: Map<Long, String> = emptyMap(),
    val repMax: Map<Long, String> = emptyMap(),
    val rest: Map<Long, String> = emptyMap(),
    val notes: Map<Long, String> = emptyMap(),
    val links: Set<Int> = emptySet(),
    val pickerOpen: Boolean = false,
    val pickerQuery: String = "",
    val pickerMuscle: String = ""
)

data class RoutineDayFormActions(
    val name: (String) -> Unit,
    val sets: (Long, String) -> Unit,
    val repMin: (Long, String) -> Unit,
    val repMax: (Long, String) -> Unit,
    val rest: (Long, String) -> Unit,
    val notes: (Long, String) -> Unit,
    val move: (Long, Int) -> Unit,
    val remove: (Long) -> Unit,
    val link: (Int) -> Unit,
    val toggleExercise: (Long) -> Unit,
    val pickerQuery: (String) -> Unit,
    val pickerMuscle: (String) -> Unit,
    val openPicker: () -> Unit,
    val closePicker: () -> Unit,
    val save: () -> Unit,
    val retry: () -> Unit,
    val back: () -> Unit,
    val home: () -> Unit
)

/** Plain form rendering; navigation, persistence and lifecycle belong to the route wrapper. */
@Composable
fun PreviewRoutineFormContent(
    state: RoutineFormUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        topBar = {
            RoutineHeader(
                title = if (state.editing) "Edit routine" else "New routine",
                onBack = onBack,
                onHome = onHome,
                enabled = !state.saving
            )
        },
        bottomBar = {
            RoutineFormFooter(
                error = state.error,
                enabled = state.canSave,
                saving = state.saving,
                label = if (state.editing) "Save routine" else "Create routine",
                save = onSave
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(IronLogSpace.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CardPadding)
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    Text(
                        text = if (state.editing) "ROUTINE CONFIG" else "NEW PROGRAM",
                        style = IronLogType.technicalEyebrow(),
                        color = surfaces.accent
                    )
                    Text(
                        text = if (state.editing) "Make it yours" else "A plan you can come back to",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Give your routine a name. Add its training days next.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            item(key = "name-card") {
                IronLogCard(showBorder = true, shape = IronLogRadius.CardShape) {
                    RoutineRecessedInput(
                        label = "Routine name",
                        value = state.name,
                        onValueChange = onName,
                        placeholder = "Push / Pull / Legs",
                        enabled = state.canEdit && !state.loading && !state.saving,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = surfaces.accent,
                        trackColor = surfaces.wellFill
                    )
                }
            }
            if (state.error != null && !state.canEdit && state.editing && !state.loading) {
                item(key = "reload") {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onRetry()
                        },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Reload routine")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewRoutineDayFormContent(state: RoutineDayFormUiState, actions: RoutineDayFormActions) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val inputEnabled = state.canEdit && !state.loading && !state.saving
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        topBar = {
            RoutineHeader(
                title = if (state.editing) "Edit day" else "New training day",
                onBack = actions.back,
                onHome = actions.home,
                enabled = !state.saving
            )
        },
        bottomBar = {
            RoutineFormFooter(
                error = state.error,
                enabled = state.canSave,
                saving = state.saving,
                label = "Save day",
                save = actions.save
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(IronLogSpace.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            item(key = "day-name") {
                RoutineRecessedInput(
                    label = "Day name",
                    value = state.name,
                    onValueChange = actions.name,
                    placeholder = "Push day",
                    enabled = inputEnabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    textStyle = MaterialTheme.typography.titleMedium
                )
            }
            item(key = "order-header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    IronLogSectionHeader(
                        title = "Exercise order",
                        actionLabel = "Add exercises",
                        onAction = {
                            haptics.tick()
                            actions.openPicker()
                        },
                        actionEnabled = inputEnabled
                    )
                    Text(
                        text = "${state.exercises.size} exercises · linked pairs rotate together",
                        style = IronLogType.telemetryLabel(),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = surfaces.accent,
                        trackColor = surfaces.wellFill
                    )
                }
            }
            if (state.error != null && !state.loading && !state.canEdit && state.editing) {
                item(key = "reload") {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            actions.retry()
                        },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Reload day")
                    }
                }
            }
            if (state.exercises.isEmpty() && !state.loading && state.canEdit) {
                item(key = "empty") {
                    RoutineEmpty(
                        title = "Build the workout you want",
                        message = "Choose exercises, set your targets, and put them in the order you'll lift.",
                        actionLabel = "Choose exercises",
                        onAction = actions.openPicker
                    )
                }
            }
            itemsIndexed(state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                RoutineExerciseEditor(
                    exercise = exercise,
                    index = index,
                    state = state,
                    actions = actions,
                    modifier = if (index < 6) {
                        Modifier.staggeredEntranceOnce(
                            key = "day-form-ex-${exercise.id}",
                            index = index
                        )
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
    if (state.pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = actions.closePicker,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = IronLogRadius.SheetShape,
            containerColor = surfaces.canvas,
            contentColor = colors.onSurface
        ) {
            RoutineFormExercisePicker(state, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineExerciseEditor(
    exercise: Exercise,
    index: Int,
    state: RoutineDayFormUiState,
    actions: RoutineDayFormActions,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val id = exercise.id
    val linkedBefore = index > 0 && (index - 1) in state.links
    val linkedAfter = index in state.links
    val isSuperset = linkedBefore || linkedAfter
    val hasReps = exercise.logType == LogType.WEIGHT_REPS || exercise.logType == LogType.REPS_ONLY
    var details by rememberSaveable(id) { mutableStateOf(state.notes[id].orEmpty().isNotEmpty()) }
    val enabled = state.canEdit && !state.loading && !state.saving
    val canMoveUp = enabled && canMoveRoutineExerciseBlock(index, -1, state.links, state.exercises.size)
    val canMoveDown = enabled && canMoveRoutineExerciseBlock(index, 1, state.links, state.exercises.size)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IronLogCard(
            modifier = Modifier.animateContentSize(
                animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
            ),
            highlighted = isSuperset,
            spotlight = isSuperset,
            showBorder = true,
            shape = IronLogRadius.CardShape,
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${exercise.name}",
                        style = IronLogType.tabular(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = exercise.targetMuscle,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isSuperset) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                        IronLogStatusChip(
                            label = "Superset",
                            tone = IronLogStatusTone.SUPERSET,
                            icon = Icons.Default.Link
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                RoutineTargetField(
                    label = "Sets",
                    value = state.sets[id].orEmpty(),
                    change = { actions.sets(id, it) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 128.dp),
                    placeholder = "3"
                )
                if (hasReps) {
                    RoutineTargetField(
                        label = "Min reps",
                        value = state.repMin[id].orEmpty(),
                        change = { actions.repMin(id, it) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 128.dp),
                        placeholder = "Optional"
                    )
                    RoutineTargetField(
                        label = "Max reps",
                        value = state.repMax[id].orEmpty(),
                        change = { actions.repMax(id, it) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 128.dp),
                        placeholder = "Optional"
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        haptics.segmentTick()
                        details = !details
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text(if (details) "Hide rest & notes" else "Rest & notes")
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        actions.move(id, -1)
                    },
                    enabled = canMoveUp,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Up")
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        actions.move(id, 1)
                    },
                    enabled = canMoveDown,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Down")
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        actions.remove(id)
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Remove", color = colors.error)
                }
            }
            AnimatedVisibility(
                visible = details,
                enter = expandVertically(
                    animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
                ) + fadeIn(animationSpec = if (reducedMotion) snap() else IronMotion.EffectDefault),
                exit = shrinkVertically(
                    animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
                ) + fadeOut(animationSpec = if (reducedMotion) snap() else IronMotion.EffectFast)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    RoutineTargetField(
                        label = "Rest in seconds",
                        value = state.rest[id].orEmpty(),
                        change = { actions.rest(id, it) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = exercise.defaultRestSeconds.toString()
                    )
                    Text(
                        text = "Leave rest blank to use this exercise's default (${exercise.defaultRestSeconds}s).",
                        color = colors.onSurfaceVariant,
                        style = IronLogType.telemetryLabel()
                    )
                    RoutineRecessedInput(
                        label = "Exercise notes",
                        value = state.notes[id].orEmpty(),
                        onValueChange = { actions.notes(id, it) },
                        placeholder = "Cues, setup, or your plan",
                        enabled = enabled,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 6
                    )
                }
            }
        }
        if (index < state.exercises.lastIndex) {
            val otherLinked = (index - 1) in state.links || (index + 1) in state.links
            TextButton(
                onClick = {
                    haptics.toggle(!linkedAfter)
                    actions.link(index)
                },
                enabled = enabled && (linkedAfter || !otherLinked),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text(
                    when {
                        linkedAfter -> "Unlink superset"
                        otherLinked -> "An exercise is already paired"
                        else -> "Pair with next exercise"
                    }
                )
            }
        }
    }
}

@Composable
private fun RoutineTargetField(
    label: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    placeholder: String
) {
    RoutineRecessedInput(
        label = label,
        value = value,
        onValueChange = change,
        placeholder = placeholder,
        enabled = enabled,
        modifier = modifier,
        textStyle = IronLogType.tabular(LocalTextStyle.current),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun RoutineRecessedInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = IronLogType.technicalEyebrow(),
            color = if (isFocused) surfaces.accent else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle.copy(color = if (enabled) colors.onSurface else colors.onSurfaceVariant),
            cursorBrush = SolidColor(surfaces.accent),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                IronLogRecessedWell(
                    modifier = Modifier.fillMaxWidth(),
                    active = isFocused,
                    accentColor = surfaces.accent,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (singleLine) 22.dp else 44.dp),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.onSurfaceVariant.copy(alpha = 0.65f),
                                maxLines = if (singleLine) 1 else maxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
private fun RoutineFormExercisePicker(state: RoutineDayFormUiState, actions: RoutineDayFormActions) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val muscles = remember(state.availableExercises) {
        state.availableExercises.map { it.targetMuscle }.distinct().sorted()
    }
    val filtered = remember(state.availableExercises, state.pickerQuery, state.pickerMuscle) {
        val query = state.pickerQuery.trim()
        state.availableExercises.filter { exercise ->
            (state.pickerMuscle.isBlank() || exercise.targetMuscle == state.pickerMuscle) &&
                (query.isEmpty() || exercise.name.contains(query, true) || exercise.targetMuscle.contains(query, true))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .padding(horizontal = IronLogSpace.ScreenHorizontal)
    ) {
        IronLogSheetHeader(
            title = "Add exercises",
            subtitle = "${state.selectedIds.size} selected · tap to add or remove",
            onClose = actions.closePicker
        )
        BasicTextField(
            value = state.pickerQuery,
            onValueChange = actions.pickerQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(surfaces.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = IronLogSpace.ItemGap)
                .onFocusChanged { isSearchFocused = it.isFocused },
            decorationBox = { innerTextField ->
                IronLogRecessedWell(
                    modifier = Modifier.fillMaxWidth(),
                    active = isSearchFocused || state.pickerQuery.isNotEmpty(),
                    accentColor = surfaces.accent,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = if (state.pickerQuery.isNotEmpty()) 2.dp else 12.dp,
                        top = 2.dp,
                        bottom = 2.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = IronLogSpace.TouchTargetMin),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isSearchFocused) surfaces.accent else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (state.pickerQuery.isEmpty()) {
                                Text(
                                    text = "Search exercises",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        if (state.pickerQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    actions.pickerQuery("")
                                },
                                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            contentPadding = PaddingValues(vertical = IronLogSpace.CompactGap)
        ) {
            item(key = "all") {
                IronLogFilterPill(
                    label = "All",
                    selected = state.pickerMuscle.isBlank(),
                    onClick = { actions.pickerMuscle("") },
                    showLedPip = true
                )
            }
            items(muscles, key = { it }) { muscle ->
                IronLogFilterPill(
                    label = muscle,
                    selected = state.pickerMuscle == muscle,
                    onClick = { actions.pickerMuscle(if (state.pickerMuscle == muscle) "" else muscle) },
                    showLedPip = true
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            contentPadding = PaddingValues(bottom = IronLogSpace.CompactGap)
        ) {
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No exercises match. Try another body part or search.",
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(IronLogSpace.CardPadding)
                    )
                }
            }
            items(filtered, key = { it.id }) { exercise ->
                val selected = exercise.id in state.selectedIds
                IronLogCard(
                    onClick = {
                        haptics.toggle(!selected)
                        actions.toggleExercise(exercise.id)
                    },
                    highlighted = selected,
                    spotlight = selected,
                    showBorder = true,
                    shape = IronLogRadius.CardShape,
                    contentPadding = PaddingValues(IronLogSpace.ItemGap),
                    modifier = Modifier.semantics(mergeDescendants = true) { this.selected = selected }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = IronLogSpace.TouchTargetMin),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = exercise.targetMuscle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        IronLogTactileSwitch(
                            checked = selected,
                            onCheckedChange = null
                        )
                    }
                }
            }
        }
        IronLogPrimaryButton(
            text = "Done · ${state.selectedIds.size} selected",
            onClick = actions.closePicker,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = IronLogSpace.CompactGap)
        )
    }
}

@Composable
private fun RoutineFormFooter(
    error: String?,
    enabled: Boolean,
    saving: Boolean,
    label: String,
    save: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val plateBrush = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.plateTop, surfaces.plateBottom))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(plateBrush)
            .navigationBarsPadding()
            .imePadding()
    ) {
        IronLogDivider(insetStart = 0.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = IronLogSpace.ItemGap),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            error?.let {
                Text(
                    text = it,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IronLogPrimaryButton(
                text = if (saving) "Saving…" else label,
                onClick = save,
                enabled = enabled,
                loading = saving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
