package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.exercise.ExerciseSortMode
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.MachinedPressIndication

enum class PickerMode { BROWSE, BUILD_SUPERSET, ADD_TO_SUPERSET, SWAP }

data class PickerUiState(
    val rows: List<ExerciseUsageRow> = emptyList(),
    val lastSets: Map<Long, ExerciseLastSetRow> = emptyMap(),
    val isLoading: Boolean = false,
    val query: String = "",
    val muscles: List<String> = emptyList(),
    val selectedMuscles: Set<String> = emptySet(),
    val sortMode: ExerciseSortMode = ExerciseSortMode.RECENTLY_USED,
    val selectedExercises: List<Exercise> = emptyList(),
    val mode: PickerMode = PickerMode.BROWSE,
    val showBack: Boolean = true
)

sealed interface PickerAction {
    data object Back : PickerAction
    data object ToggleSuperset : PickerAction
    data object StartSuperset : PickerAction
    data object ClearFilters : PickerAction
    data object SmartLog : PickerAction
    data class Create(val name: String = "") : PickerAction
    data class Edit(val exercise: Exercise) : PickerAction
    data class Delete(val exercise: Exercise) : PickerAction
    data class TogglePr(val exercise: Exercise) : PickerAction
}

/** Plain rendering boundary; navigation, preferences and database work live in the screen/VM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewExercisePicker(
    state: PickerUiState,
    onAction: (PickerAction) -> Unit,
    onSelectExercise: (Exercise) -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleToggle: (String) -> Unit,
    onSortChange: (ExerciseSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var lastFilterKey by remember { mutableStateOf<Triple<String, Set<String>, ExerciseSortMode>?>(null) }
    val currentFilterKey = Triple(state.query, state.selectedMuscles, state.sortMode)
    LaunchedEffect(state.isLoading, currentFilterKey) {
        if (state.isLoading) return@LaunchedEffect
        val previous = lastFilterKey
        lastFilterKey = currentFilterKey
        if (previous != null && previous != currentFilterKey) {
            listState.scrollToItem(0)
        }
    }
    val muscles = remember(state.muscles) {
        val usualOrder = listOf(
            "Chest", "Back", "Shoulders", "Biceps", "Triceps",
            "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Abs", "Legs", "Cardio"
        )
        state.muscles.sortedWith(
            compareBy<String> { muscle ->
                usualOrder.indexOfFirst { it.equals(muscle, ignoreCase = true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.lowercase() }
        )
    }
    val muscleListState = rememberLazyListState()
    var revealedInitialMuscle by remember { mutableStateOf(false) }
    // A newly opened picker reveals the remembered muscle once without moving chips after each tap.
    LaunchedEffect(state.isLoading, muscles) {
        if (!state.isLoading && !revealedInitialMuscle) {
            revealedInitialMuscle = true
            val selectedIndex = muscles.indexOfFirst { it in state.selectedMuscles }
            if (selectedIndex >= 0) muscleListState.scrollToItem(selectedIndex + 1)
        }
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = colors.onBackground
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showBack) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onAction(PickerAction.Back)
                        },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
                Text(
                    text = when (state.mode) {
                        PickerMode.BUILD_SUPERSET -> "Build superset"
                        PickerMode.ADD_TO_SUPERSET -> "Add to superset"
                        PickerMode.SWAP -> "Swap exercise"
                        PickerMode.BROWSE -> if (state.showBack) "Choose exercise" else "Exercises"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (state.showBack) 0.dp else 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.mode == PickerMode.BROWSE || state.mode == PickerMode.BUILD_SUPERSET) {
                    TextButton(
                        onClick = {
                            haptics.segmentTick()
                            onAction(PickerAction.ToggleSuperset)
                        },
                        modifier = Modifier
                            .widthIn(min = IronLogSpace.TouchTargetMin)
                            .heightIn(min = IronLogSpace.TouchTargetMin),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state.mode == PickerMode.BUILD_SUPERSET) "Cancel" else "Superset",
                            maxLines = 1
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onAction(PickerAction.Back)
                        },
                        modifier = Modifier
                            .widthIn(min = IronLogSpace.TouchTargetMin)
                            .heightIn(min = IronLogSpace.TouchTargetMin),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Cancel", maxLines = 1)
                    }
                }
                Box {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            showMenu = true
                        },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.MoreVert, "Picker options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sort exercises") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                            onClick = {
                                haptics.tick()
                                showMenu = false
                                showSort = true
                            },
                            enabled = !state.isLoading
                        )
                        DropdownMenuItem(
                            text = { Text("Create exercise") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                haptics.tick()
                                showMenu = false
                                onAction(PickerAction.Create(state.query.trim()))
                            }
                        )
                        if (state.mode == PickerMode.BROWSE) {
                            DropdownMenuItem(
                                text = { Text("Smart Log") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                onClick = {
                                    haptics.tick()
                                    showMenu = false
                                    onAction(PickerAction.SmartLog)
                                }
                            )
                        }
                    }
                }
            }
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(surfaces.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.ScreenHorizontal)
                    .onFocusChanged { isSearchFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    IronLogRecessedWell(
                        modifier = Modifier.fillMaxWidth(),
                        active = isSearchFocused || state.query.isNotEmpty(),
                        accentColor = surfaces.accent,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (state.query.isNotEmpty()) 2.dp else 12.dp,
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
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "Search all exercises",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                            if (state.query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        onQueryChange("")
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
                state = muscleListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = IronLogSpace.ScreenHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "all-muscles") {
                    IronLogFilterPill(
                        label = "All",
                        selected = state.selectedMuscles.isEmpty(),
                        onClick = { onAction(PickerAction.ClearFilters) },
                        enabled = !state.isLoading,
                        showLedPip = true
                    )
                }
                items(muscles, key = { "muscle:$it" }) { muscle ->
                    IronLogFilterPill(
                        label = muscle,
                        selected = muscle in state.selectedMuscles,
                        onClick = { onMuscleToggle(muscle) },
                        enabled = !state.isLoading,
                        showLedPip = true
                    )
                }
            }
            if (state.isLoading) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = surfaces.accent
                    )
                }
            } else if (state.rows.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            top = 24.dp,
                            end = 24.dp,
                            bottom = if (!state.showBack && state.mode != PickerMode.BUILD_SUPERSET) {
                                IronLogSpace.BottomNavClearance
                            } else {
                                24.dp
                            }
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Try a different name or clear your filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    if (state.selectedMuscles.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onAction(PickerAction.ClearFilters)
                            },
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Clear filters")
                        }
                    }
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onAction(PickerAction.Create(state.query.trim()))
                        },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text(if (state.query.isBlank()) "Create exercise" else "Create “${state.query.trim()}”")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = if (!state.showBack && state.mode != PickerMode.BUILD_SUPERSET) {
                            IronLogSpace.BottomNavClearance
                        } else {
                            12.dp
                        }
                    )
                ) {
                    items(state.rows, key = { it.exercise.id }) { row ->
                        val index = state.selectedExercises.indexOfFirst { it.id == row.exercise.id }
                        PickerExerciseRow(
                            exercise = row.exercise,
                            lastSet = state.lastSets[row.exercise.id],
                            selectionOrder = if (index >= 0) index + 1 else null,
                            mode = state.mode,
                            onClick = { onSelectExercise(row.exercise) },
                            onAction = onAction
                        )
                    }
                }
            }
            if (state.mode == PickerMode.BUILD_SUPERSET) {
                SupersetSelectionFooter(
                    selected = state.selectedExercises,
                    onRemove = onSelectExercise,
                    onStart = { onAction(PickerAction.StartSuperset) },
                    extraBottomClearance = !state.showBack
                )
            }
        }
    }
    if (showSort) {
        PickerSortSheet(
            state = state,
            onSortChange = onSortChange,
            onDismiss = { showSort = false }
        )
    }
}

@Composable
private fun PickerExerciseRow(
    exercise: Exercise,
    lastSet: ExerciseLastSetRow?,
    selectionOrder: Int?,
    mode: PickerMode,
    onClick: () -> Unit,
    onAction: (PickerAction) -> Unit
) {
    var showMenu by remember(exercise.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val isSelected = selectionOrder != null
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.985f,
            highlightAlpha = 0.05f,
            cornerRadius = IronLogRadius.Control
        )
    }
    val muscleColor = when (Math.floorMod(exercise.targetMuscle.hashCode(), 3)) {
        0 -> surfaces.accent
        1 -> colors.secondary
        else -> colors.tertiary
    }
    val selectedBackground = if (isSelected) {
        surfaces.accent.copy(alpha = if (surfaces.isDark) 0.12f else 0.10f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                onClick = {
                    if (mode == PickerMode.BUILD_SUPERSET) {
                        haptics.toggle(!isSelected)
                    } else {
                        haptics.keyTap()
                    }
                    onClick()
                }
            )
            .semantics { selected = isSelected }
            .clip(IronLogRadius.ControlShape)
            .background(selectedBackground)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = IronLogBorder.Thin,
                        color = surfaces.accent.copy(alpha = 0.35f),
                        shape = IronLogRadius.ControlShape
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .background(
                        color = if (isSelected) surfaces.accent else muscleColor.copy(alpha = 0.45f),
                        shape = IronLogRadius.CapsuleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            if (mode == PickerMode.BUILD_SUPERSET) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (isSelected) colors.primary else surfaces.wellFill,
                    border = if (isSelected) null else IronLogBorder.stroke(
                        if (surfaces.isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFBFC5CE),
                        IronLogBorder.Thin
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectionOrder != null) {
                            Text(
                                text = "$selectionOrder",
                                color = colors.onPrimary,
                                style = IronLogType.tabular(MaterialTheme.typography.labelMedium).copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (exercise.isStarred) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                    }
                }
                Text(
                    text = buildString {
                        append(exercise.targetMuscle)
                        lastSet?.let { append(" · Last: ${formatLoggerSet(it.set, exercise.logType)}") }
                    },
                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                    color = colors.onSurfaceVariant
                )
            }
            if (mode == PickerMode.BROWSE) {
                Box {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            showMenu = true
                        },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "${exercise.name} options",
                            tint = colors.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit exercise") },
                            onClick = {
                                haptics.tick()
                                showMenu = false
                                onAction(PickerAction.Edit(exercise))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (exercise.isStarred) "Stop tracking PR" else "Track PR") },
                            onClick = {
                                haptics.toggle(!exercise.isStarred)
                                showMenu = false
                                onAction(PickerAction.TogglePr(exercise))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete exercise", color = colors.error) },
                            onClick = {
                                haptics.tick()
                                showMenu = false
                                onAction(PickerAction.Delete(exercise))
                            }
                        )
                    }
                }
            }
        }
    }
    IronLogDivider(insetStart = 22.dp, insetEnd = 12.dp)
}

@Composable
private fun SupersetSelectionFooter(
    selected: List<Exercise>,
    onRemove: (Exercise) -> Unit,
    onStart: () -> Unit,
    extraBottomClearance: Boolean = false
) {
    val surfaces = LocalIronLogSurfaces.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val plateBrush = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.plateTop, surfaces.plateBottom))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(plateBrush)
    ) {
        IronLogDivider(insetStart = 0.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = IronLogSpace.ScreenHorizontal,
                    top = 12.dp,
                    end = IronLogSpace.ScreenHorizontal,
                    bottom = if (extraBottomClearance) IronLogSpace.BottomNavClearance else 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            if (selected.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    items(selected, key = { it.id }) { exercise ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressIndication = remember {
                            MachinedPressIndication(
                                pressedScale = 0.95f,
                                highlightAlpha = 0.08f,
                                cornerRadius = IronLogRadius.Capsule
                            )
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSizeHeight(IronLogSpace.TouchTargetMin)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = pressIndication,
                                    onClick = {
                                        haptics.toggle(false)
                                        onRemove(exercise)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(IronLogRadius.CapsuleShape)
                                    .background(scheme.inverseSurface)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.inverseOnSurface,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${exercise.name}",
                                    tint = scheme.inverseOnSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            IronLogPrimaryButton(
                text = if (selected.size < 2) "Select at least 2 exercises" else "Start superset (${selected.size})",
                onClick = onStart,
                enabled = selected.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = IronLogSpace.TouchTargetMin),
                shape = IronLogRadius.ControlShape
            )
        }
    }
}

private fun Modifier.defaultMinSizeHeight(minHeight: androidx.compose.ui.unit.Dp): Modifier =
    this.heightIn(min = minHeight)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSortSheet(
    state: PickerUiState,
    onSortChange: (ExerciseSortMode) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = surfaces.canvas,
        contentColor = scheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                    },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(IronLogSpace.CompactGap))
            ExerciseSortMode.entries.forEach { sort ->
                val isSelected = state.sortMode == sort
                val interactionSource = remember { MutableInteractionSource() }
                val pressIndication = remember {
                    MachinedPressIndication(
                        pressedScale = 0.985f,
                        highlightAlpha = 0.05f,
                        cornerRadius = IronLogRadius.Control
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .clip(IronLogRadius.ControlShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = pressIndication,
                            onClick = {
                                haptics.segmentTick()
                                onSortChange(sort)
                            }
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(
                        text = sort.label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                    if (isSelected) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
