package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.ui.routine.RoutineDetailUiState
import com.example.gymtime.util.TimeFormatter
import java.text.NumberFormat
import java.util.Locale

data class PreviewRoutineDetailState(
    val detail: RoutineDetailUiState = RoutineDetailUiState(), val stats: RoutineStats? = null,
    val statsLoading: Boolean = true, val statsError: String? = null,
    val startingDayId: Long? = null, val isWorking: Boolean = false, val actionError: String? = null
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PreviewRoutineDetailContent(
    state: PreviewRoutineDetailState, onBack: () -> Unit, onHome: () -> Unit, onRename: () -> Unit,
    onUseRoutine: () -> Unit, onClearRoutine: () -> Unit, onCreateDay: () -> Unit,
    onStartDay: (Long) -> Unit, onEditDay: (Long) -> Unit, onSetNextDay: (Long) -> Unit,
    onDuplicateDay: (Long) -> Unit, onMoveDay: (Long, Int) -> Unit, onDeleteDay: (RoutineDay) -> Unit,
    onRefreshStats: () -> Unit, onRetryLoad: () -> Unit, onDismissError: () -> Unit, modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLimit by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detail = state.detail
    val routine = detail.routine
    val busy = state.isWorking || state.startingDayId != null
    val addDay = { if (detail.canAddMoreDays) onCreateDay() else showLimit = true }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column {
            RoutineHeader("Routine", onBack, onHome)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(routine?.name ?: "Your routine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${detail.days.size}/10 days" + if (routine?.isActive == true) " · Selected routine" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (routine != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = if (routine.isActive) onClearRoutine else onUseRoutine,
                        enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (routine.isActive) "Clear selection" else "Use routine") }
                    TextButton(onClick = onRename, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Rename") }
                }
            }
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Workout days") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; onRefreshStats() }, text = { Text("Stats & trends") })
            }
            if (selectedTab == 0) LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (detail.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                detail.loadError?.let { error -> item { RoutineError(error, onDismissError, onRetryLoad) } }
                state.actionError?.let { error -> item { RoutineError(error, onDismissError) } }
                if (!detail.isLoading && routine == null && detail.loadError == null) item {
                    RoutineEmpty("Routine unavailable", "It may have been deleted. Go back to choose another routine.")
                }
                if (routine != null) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your rotation", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        RoutinePrimaryAction("Add day", addDay, enabled = !busy)
                    }
                }
                if (routine != null && detail.days.isEmpty()) item {
                    RoutineEmpty("Start with one workout day", "Add your exercises, set targets, and a name. You can add more days later.", "Create a day", addDay)
                }
                items(detail.days, key = { it.day.id }) { day ->
                    PreviewRoutineDayCard(
                        day = day, dayNumber = detail.days.indexOf(day) + 1,
                        isNext = day.day.orderIndex == routine?.nextDayOrderIndex, stat = detail.dayStats[day.day.id],
                        busy = busy, starting = state.startingDayId == day.day.id,
                        onStart = { onStartDay(day.day.id) }, onEdit = { onEditDay(day.day.id) },
                        onSetNext = { onSetNextDay(day.day.id) },
                        onDuplicate = { if (detail.canAddMoreDays) onDuplicateDay(day.day.id) else showLimit = true },
                        onMoveUp = if (detail.days.firstOrNull()?.day?.id != day.day.id) ({ onMoveDay(day.day.id, -1) }) else null,
                        onMoveDown = if (detail.days.lastOrNull()?.day?.id != day.day.id) ({ onMoveDay(day.day.id, 1) }) else null,
                        onDelete = { deleteId = day.day.id }
                    )
                }
            } else PreviewRoutineStatsContent(state, onRefreshStats, onDismissError)
        }
    }
    if (showLimit) AlertDialog(onDismissRequest = { showLimit = false }, title = { Text("10 days saved") },
        text = { Text("Delete a day before creating or duplicating another.") },
        confirmButton = { TextButton(onClick = { showLimit = false }) { Text("OK") } })
    detail.days.firstOrNull { it.day.id == deleteId }?.day?.let { day ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete ${day.name}?") },
            text = { Text("Its planned exercises will be deleted. Your logged workouts stay saved.") },
            confirmButton = { TextButton(onClick = { onDeleteDay(day); deleteId = null }, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep day") } })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PreviewRoutineDayCard(
    day: RoutineDayWithExercises, dayNumber: Int, isNext: Boolean, busy: Boolean, starting: Boolean,
    onStart: () -> Unit, onEdit: () -> Unit, stat: RoutineDayStat? = null,
    onSetNext: (() -> Unit)? = null, onDuplicate: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null, onMoveDown: (() -> Unit)? = null, onDelete: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(day.day.id) { mutableStateOf(isNext) }
    RoutinePanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Day $dayNumber" + if (isNext) " · Up next" else "", color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
                Text(day.day.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (onDelete != null) Box {
                TextButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "Options for ${day.day.name}" }) { Text("More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!isNext && onSetNext != null) DropdownMenuItem(text = { Text("Set as next day") }, onClick = { menu = false; onSetNext() }, enabled = !busy)
                    if (onDuplicate != null) DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() }, enabled = !busy)
                    if (onMoveUp != null) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMoveUp() }, enabled = !busy)
                    if (onMoveDown != null) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMoveDown() }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() }, enabled = !busy)
                }
            }
        }
        Text("${day.exercises.size} exercises · ${day.exercises.sumOf { it.routineExercise.targetSets }} planned sets",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded && day.exercises.isNotEmpty()) RoutineExerciseBlocks(day.exercises)
        if (day.exercises.isNotEmpty()) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)) { Text(if (expanded) "Hide exercise plan" else "Show exercise plan") }
        if (stat != null) Text("${stat.timesCompleted} ${if (stat.timesCompleted == 1) "workout" else "workouts"}" +
            (stat.lastPerformed?.let { " · Last ${TimeFormatter.formatShortDate(it)}" } ?: " · Not performed yet"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RoutinePrimaryAction(if (starting) "Starting…" else "Start day", onStart, enabled = !busy && day.exercises.isNotEmpty())
            TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (day.exercises.isEmpty()) "Add exercises" else "Edit day") }
        }
    }
}

@Composable
private fun PreviewRoutineStatsContent(state: PreviewRoutineDetailState, onRetry: () -> Unit, onDismissError: () -> Unit) {
    val stats = state.stats
    val number = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.statsLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.actionError?.let { message -> item { RoutineError(message, onDismissError) } }
        state.statsError?.let { message -> item { RoutineError(message, onRetry, onRetry) } }
        if (!state.statsLoading && state.statsError == null && (stats == null || stats.timesCompleted == 0)) item {
            RoutineEmpty("Your routine story starts here", "Completed workouts from this routine will appear here.")
        }
        if (stats != null && stats.timesCompleted > 0) {
            item {
                RoutinePanel {
                    Text("Sessions so far", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    RoutineStatLine("Workouts", "${stats.timesCompleted}")
                    RoutineStatLine("Average duration", stats.avgDurationMinutes?.let { "$it min" } ?: "—")
                    RoutineStatLine("Total volume", "${number.format(stats.totalVolume)} lb")
                    RoutineStatLine("Recent frequency", stats.workoutsPerWeekRecent?.let { "%.1f / week".format(it) } ?: "—")
                    stats.lastPerformed?.let { Text("Last performed ${TimeFormatter.formatShortDate(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (stats.exerciseTrends.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Exercise trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Latest best set; estimated strength change against earlier routine sessions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(stats.exerciseTrends, key = { it.exerciseId }) { trend ->
                RoutinePanel {
                    Text(trend.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Latest best · ${trend.lastBestLabel}", style = MaterialTheme.typography.bodyMedium)
                    val delta = trend.e1rmDelta
                    Text(when {
                        delta == null -> "Not enough comparable history"
                        delta > .5f -> "Estimated strength +${number.format(delta)} lb"
                        delta < -.5f -> "Estimated strength ${number.format(delta)} lb"
                        else -> "Estimated strength steady"
                    }, style = MaterialTheme.typography.bodyMedium,
                        color = if (delta != null && delta < -.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RoutineStatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
