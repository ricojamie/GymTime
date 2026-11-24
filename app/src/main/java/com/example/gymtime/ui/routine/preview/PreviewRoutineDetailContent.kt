package com.example.gymtime.ui.routine.preview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.routine.RoutineDetailUiState
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import com.example.gymtime.util.TimeFormatter
import java.text.NumberFormat
import java.util.Locale

data class PreviewRoutineDetailState(
    val detail: RoutineDetailUiState = RoutineDetailUiState(),
    val stats: RoutineStats? = null,
    val statsLoading: Boolean = true,
    val statsError: String? = null,
    val startingDayId: Long? = null,
    val isWorking: Boolean = false,
    val actionError: String? = null
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PreviewRoutineDetailContent(
    state: PreviewRoutineDetailState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRename: () -> Unit,
    onUseRoutine: () -> Unit,
    onClearRoutine: () -> Unit,
    onCreateDay: () -> Unit,
    onStartDay: (Long) -> Unit,
    onEditDay: (Long) -> Unit,
    onSetNextDay: (Long) -> Unit,
    onDuplicateDay: (Long) -> Unit,
    onMoveDay: (Long, Int) -> Unit,
    onDeleteDay: (RoutineDay) -> Unit,
    onRefreshStats: () -> Unit,
    onRetryLoad: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLimit by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detail = state.detail
    val routine = detail.routine
    val busy = state.isWorking || state.startingDayId != null
    val addDay = { if (detail.canAddMoreDays) onCreateDay() else showLimit = true }
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = scheme.onBackground
    ) {
        Column {
            RoutineHeader("Routine", onBack, onHome)
            Column(
                modifier = Modifier.padding(
                    horizontal = IronLogSpace.ScreenHorizontal,
                    vertical = IronLogSpace.CompactGap
                ),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(
                        text = routine?.name ?: "Your routine",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (routine?.isActive == true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IronLogLedPip(color = surfaces.ledGlow, size = 6.dp, pulsing = true)
                            IronLogStatusChip(label = "Active", tone = IronLogStatusTone.ACTIVE)
                        }
                    }
                }
                Text(
                    text = "${detail.days.size}/10 days",
                    color = scheme.onSurfaceVariant,
                    style = IronLogType.telemetryLabel()
                )
                if (routine != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        IronLogSecondaryButton(
                            text = if (routine.isActive) "Clear selection" else "Use routine",
                            onClick = {
                                haptics.toggle(!routine.isActive)
                                if (routine.isActive) onClearRoutine() else onUseRoutine()
                            },
                            enabled = !busy,
                            outlined = true
                        )
                        IronLogSecondaryButton(
                            text = "Rename",
                            onClick = onRename,
                            enabled = !busy,
                            outlined = true
                        )
                    }
                }
                IronLogSegmentedControl(
                    options = listOf("Workout days", "Stats & trends"),
                    selectedIndex = selectedTab,
                    onSelect = { index ->
                        selectedTab = index
                        if (index == 1) onRefreshStats()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    } else {
                        val forward = targetState > initialState
                        val enter = slideInHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { width ->
                            if (forward) width / 6 else -width / 6
                        } + fadeIn(animationSpec = IronMotion.EffectDefault)
                        val exit = slideOutHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { width ->
                            if (forward) -width / 8 else width / 8
                        } + fadeOut(animationSpec = IronMotion.EffectFast)
                        enter togetherWith exit
                    }
                },
                label = "routineDetailTab"
            ) { tab ->
                if (tab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = IronLogSpace.ScreenHorizontal,
                            end = IronLogSpace.ScreenHorizontal,
                            top = IronLogSpace.CardPadding,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        if (detail.isLoading) {
                            item(key = "loading") {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = surfaces.accent,
                                    trackColor = surfaces.wellFill
                                )
                            }
                        }
                        detail.loadError?.let { error ->
                            item(key = "load-error") { RoutineError(error, onDismissError, onRetryLoad) }
                        }
                        state.actionError?.let { error ->
                            item(key = "action-error") { RoutineError(error, onDismissError) }
                        }
                        if (!detail.isLoading && routine == null && detail.loadError == null) {
                            item(key = "missing") {
                                RoutineEmpty("Routine unavailable", "It may have been deleted. Go back to choose another routine.")
                            }
                        }
                        if (routine != null) {
                            item(key = "rotation-header") {
                                IronLogSectionHeader(
                                    title = "Your rotation",
                                    trailingAction = {
                                        IronLogPrimaryButton(
                                            text = "Add day",
                                            onClick = addDay,
                                            enabled = !busy,
                                            leadingIcon = Icons.Default.Add
                                        )
                                    }
                                )
                            }
                        }
                        if (routine != null && detail.days.isEmpty()) {
                            item(key = "empty-days") {
                                RoutineEmpty(
                                    title = "Start with one workout day",
                                    message = "Add your exercises, set targets, and a name. You can add more days later.",
                                    actionLabel = "Create a day",
                                    onAction = addDay
                                )
                            }
                        }
                        itemsIndexed(detail.days, key = { _, day -> day.day.id }) { index, day ->
                            PreviewRoutineDayCard(
                                day = day,
                                dayNumber = index + 1,
                                isNext = day.day.orderIndex == routine?.nextDayOrderIndex,
                                stat = detail.dayStats[day.day.id],
                                busy = busy,
                                starting = state.startingDayId == day.day.id,
                                onStart = { onStartDay(day.day.id) },
                                onEdit = { onEditDay(day.day.id) },
                                onSetNext = { onSetNextDay(day.day.id) },
                                onDuplicate = { if (detail.canAddMoreDays) onDuplicateDay(day.day.id) else showLimit = true },
                                onMoveUp = if (index > 0) ({ onMoveDay(day.day.id, -1) }) else null,
                                onMoveDown = if (index < detail.days.lastIndex) ({ onMoveDay(day.day.id, 1) }) else null,
                                onDelete = { deleteId = day.day.id },
                                modifier = if (index < 6) {
                                    Modifier.staggeredEntranceOnce(
                                        key = "routine-day-${day.day.id}",
                                        index = index
                                    )
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                } else {
                    PreviewRoutineStatsContent(state, onRefreshStats, onDismissError)
                }
            }
        }
    }

    if (showLimit) {
        AlertDialog(
            onDismissRequest = { showLimit = false },
            shape = IronLogRadius.CardShape,
            containerColor = surfaces.plateElevatedTop,
            title = { Text("10 days saved") },
            text = { Text("Delete a day before creating or duplicating another.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.tick()
                    showLimit = false
                }) { Text("OK") }
            }
        )
    }

    detail.days.firstOrNull { it.day.id == deleteId }?.day?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            shape = IronLogRadius.CardShape,
            containerColor = surfaces.plateElevatedTop,
            title = { Text("Delete ${day.name}?") },
            text = { Text("Its planned exercises will be deleted. Your logged workouts stay saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        onDeleteDay(day)
                        deleteId = null
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.tick()
                    deleteId = null
                }) { Text("Keep day") }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PreviewRoutineDayCard(
    day: RoutineDayWithExercises,
    dayNumber: Int,
    isNext: Boolean,
    busy: Boolean,
    starting: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    stat: RoutineDayStat? = null,
    onSetNext: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(day.day.id) { mutableStateOf(isNext) }
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val totalTargetSets = remember(day.exercises) { day.exercises.sumOf { it.routineExercise.targetSets } }

    RoutinePanel(
        modifier = modifier.animateContentSize(
            animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
        ),
        highlighted = isNext,
        spotlight = isNext
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    if (isNext) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                    }
                    Text(
                        text = "DAY $dayNumber",
                        color = if (isNext) surfaces.accent else scheme.onSurfaceVariant,
                        style = IronLogType.technicalEyebrow()
                    )
                    if (isNext) {
                        IronLogStatusChip(label = "Up next", tone = IronLogStatusTone.ACCENT)
                    }
                }
                Text(
                    text = day.day.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDelete != null) {
                Box {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            menu = true
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .size(IronLogSpace.TouchTargetMin)
                            .semantics { contentDescription = "Options for ${day.day.name}" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!isNext && onSetNext != null) {
                            DropdownMenuItem(
                                text = { Text("Set as next day") },
                                onClick = {
                                    haptics.segmentTick()
                                    menu = false
                                    onSetNext()
                                },
                                enabled = !busy
                            )
                        }
                        if (onDuplicate != null) {
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = {
                                    haptics.tick()
                                    menu = false
                                    onDuplicate()
                                },
                                enabled = !busy
                            )
                        }
                        if (onMoveUp != null) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                onClick = {
                                    haptics.tick()
                                    menu = false
                                    onMoveUp()
                                },
                                enabled = !busy
                            )
                        }
                        if (onMoveDown != null) {
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                onClick = {
                                    haptics.tick()
                                    menu = false
                                    onMoveDown()
                                },
                                enabled = !busy
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = scheme.error) },
                            onClick = {
                                haptics.tick()
                                menu = false
                                onDelete()
                            },
                            enabled = !busy
                        )
                    }
                }
            }
        }
        Text(
            text = "${day.exercises.size} ${if (day.exercises.size == 1) "exercise" else "exercises"} · $totalTargetSets planned sets",
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
            color = scheme.onSurfaceVariant
        )
        if (stat != null) {
            Text(
                text = "${stat.timesCompleted} ${if (stat.timesCompleted == 1) "workout" else "workouts"}" +
                    (stat.lastPerformed?.let { " · Last ${TimeFormatter.formatShortDate(it)}" } ?: " · Not performed yet"),
                style = IronLogType.telemetryLabel(),
                color = scheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded && day.exercises.isNotEmpty(),
            enter = expandVertically(
                animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
            ) + fadeIn(animationSpec = if (reducedMotion) snap() else IronMotion.EffectDefault),
            exit = shrinkVertically(
                animationSpec = if (reducedMotion) snap() else IronMotion.SpatialLayoutSize
            ) + fadeOut(animationSpec = if (reducedMotion) snap() else IronMotion.EffectFast)
        ) {
            RoutineExerciseBlocks(day.exercises)
        }
        if (day.exercises.isNotEmpty()) {
            TextButton(
                onClick = {
                    haptics.segmentTick()
                    expanded = !expanded
                },
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(if (expanded) "Hide exercise plan" else "Show exercise plan")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            RoutinePrimaryAction(
                text = if (starting) "Starting…" else "Start day",
                onClick = onStart,
                enabled = !busy && day.exercises.isNotEmpty(),
                loading = starting
            )
            IronLogSecondaryButton(
                text = if (day.exercises.isEmpty()) "Add exercises" else "Edit day",
                onClick = onEdit,
                enabled = !busy,
                outlined = true
            )
        }
    }
}

@Composable
private fun PreviewRoutineStatsContent(
    state: PreviewRoutineDetailState,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    val stats = state.stats
    val number = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = IronLogSpace.ScreenHorizontal,
            end = IronLogSpace.ScreenHorizontal,
            top = IronLogSpace.CardPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        if (state.statsLoading) {
            item(key = "stats-loading") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = surfaces.accent,
                    trackColor = surfaces.wellFill
                )
            }
        }
        state.actionError?.let { message ->
            item(key = "action-error") { RoutineError(message, onDismissError) }
        }
        state.statsError?.let { message ->
            item(key = "stats-error") { RoutineError(message, onRetry, onRetry) }
        }
        if (!state.statsLoading && state.statsError == null && (stats == null || stats.timesCompleted == 0)) {
            item(key = "stats-empty") {
                RoutineEmpty("Your routine story starts here", "Completed workouts from this routine will appear here.")
            }
        }
        if (stats != null && stats.timesCompleted > 0) {
            item(key = "stats-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    IronLogSectionHeader(title = "Sessions so far")
                    IronLogGroupedCard(showBorder = true) {
                        IronLogMetricRow(
                            metrics = listOf(
                                IronLogMetric(label = "Workouts", value = "${stats.timesCompleted}"),
                                IronLogMetric(
                                    label = "Avg duration",
                                    value = stats.avgDurationMinutes?.let { "$it min" } ?: "—"
                                )
                            ),
                            modifier = Modifier.padding(IronLogSpace.CardPadding)
                        )
                        IronLogDivider(
                            insetStart = IronLogSpace.CardPadding,
                            insetEnd = IronLogSpace.CardPadding
                        )
                        IronLogMetricRow(
                            metrics = listOf(
                                IronLogMetric(
                                    label = "Total volume",
                                    value = "${number.format(stats.totalVolume)} lb",
                                    emphasized = true
                                ),
                                IronLogMetric(
                                    label = "Frequency",
                                    value = stats.workoutsPerWeekRecent?.let { "%.1f / wk".format(it) } ?: "—"
                                )
                            ),
                            modifier = Modifier.padding(IronLogSpace.CardPadding)
                        )
                        stats.lastPerformed?.let { lastPerformed ->
                            IronLogDivider(
                                insetStart = IronLogSpace.CardPadding,
                                insetEnd = IronLogSpace.CardPadding
                            )
                            Text(
                                text = "Last performed ${TimeFormatter.formatShortDate(lastPerformed)}",
                                color = scheme.onSurfaceVariant,
                                style = IronLogType.telemetryLabel(),
                                modifier = Modifier.padding(
                                    horizontal = IronLogSpace.CardPadding,
                                    vertical = IronLogSpace.ItemGap
                                )
                            )
                        }
                    }
                }
            }
            if (stats.exerciseTrends.isNotEmpty()) {
                item(key = "trends-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IronLogSectionHeader(title = "Exercise trends")
                        Text(
                            text = "Latest best set and estimated strength change against earlier routine sessions.",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            itemsIndexed(stats.exerciseTrends, key = { _, trend -> trend.exerciseId }) { index, trend ->
                val delta = trend.e1rmDelta
                val deltaText = when {
                    delta == null -> "Not enough comparable history"
                    delta > .5f -> "Estimated strength +${number.format(delta)} lb"
                    delta < -.5f -> "Estimated strength ${number.format(delta)} lb"
                    else -> "Estimated strength steady"
                }
                val deltaColor = when {
                    delta != null && delta > .5f -> surfaces.accent
                    delta != null && delta < -.5f -> scheme.error
                    else -> scheme.onSurfaceVariant
                }
                RoutinePanel(
                    modifier = if (index < 6) {
                        Modifier.staggeredEntranceOnce(
                            key = "routine-trend-${trend.exerciseId}",
                            index = index
                        )
                    } else {
                        Modifier
                    }
                ) {
                    Text(
                        text = trend.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Latest best · ${trend.lastBestLabel}",
                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
                    )
                    Text(
                        text = deltaText,
                        style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                        color = deltaColor
                    )
                }
            }
        }
    }
}
