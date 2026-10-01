package com.example.gymtime.ui.history.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryUiState(
    val workouts: List<WorkoutWithMuscles> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Saved search is presentation state; Room data and navigation remain outside this screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewHistoryContent(
    state: HistoryUiState,
    onSelectWorkout: (WorkoutWithMuscles) -> Unit,
    onStartWorkout: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(state.workouts, query) {
        val tokens = query.trim().lowercase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotBlank() }
        state.workouts.filter { item ->
            val search = (historyWorkoutTitle(item) + " " + item.muscleGroups.joinToString(" ") + " " +
                item.workout.routineNameSnapshot.orEmpty() + " " + historyDate(item.workout.startTime, "MMMM yyyy"))
                .lowercase(Locale.getDefault())
            tokens.all(search::contains)
        }
    }
    val months = remember(visible) { visible.groupBy { historyDate(it.workout.startTime, "MMMM yyyy") } }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("Your training, on replay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.workouts.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Name, muscle, or month") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {{
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear history search") }
                        }} else null,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.error != null -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    }
                }
                state.workouts.isEmpty() -> item {
                    HistoryEmptyState(onStartWorkout)
                }
                visible.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matching workouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Try a workout name, muscle, or month.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear search") }
                    }
                }
                else -> {
                    if (query.isNotBlank()) item(key = "search-count") {
                        Text("${visible.size} ${if (visible.size == 1) "workout" else "workouts"} found",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    months.forEach { (month, workouts) ->
                        item(key = "month-$month") {
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(month, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${workouts.size} ${if (workouts.size == 1) "session" else "sessions"}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(workouts, key = { it.workout.id }) { workout ->
                            HistoryTimelineCard(workout, onClick = { onSelectWorkout(workout) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTimelineCard(workout: WorkoutWithMuscles, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = scheme.surface, contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                Column(Modifier.widthIn(min = 50.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(historyDate(workout.workout.startTime, "dd"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(historyDate(workout.workout.startTime, "EEE"), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(historyWorkoutTitle(workout), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp), tint = scheme.primary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    workout.muscleGroups.take(3).forEach { HistoryMusclePill(it) }
                    if (workout.muscleGroups.size > 3) HistoryMusclePill("+${workout.muscleGroups.size - 3}")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HistoryMetricText("${workout.workingSetCount ?: 0} sets")
                    historyDuration(workout)?.let { HistoryMetricText(it) }
                    workout.totalVolume?.takeIf { it > 0f }?.let { HistoryMetricText("${historyNumber(it)} lb") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (workout.workout.endTime == null) "In progress" else historyDate(workout.workout.startTime, "h:mm a"),
                        style = MaterialTheme.typography.labelSmall, color = if (workout.workout.endTime == null) scheme.primary else scheme.onSurfaceVariant)
                    workout.workout.rating?.let { rating ->
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = "Workout rating", modifier = Modifier.size(16.dp), tint = scheme.tertiary)
                        Text("$rating/5", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryMusclePill(muscle: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(muscle, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun HistoryMetricText(value: String) {
    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HistoryEmptyState(onStartWorkout: () -> Unit) {
    val colors = LocalLoggerActionColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.padding(20.dp).size(32.dp))
        }
        Text("Your first session starts here", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Log a workout and build a history you can look back on.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.fill, contentColor = colors.onFill)) {
            Text("Start a workout", fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
    }
}

internal fun historyWorkoutTitle(workout: WorkoutWithMuscles): String =
    workout.workout.name?.takeIf { it.isNotBlank() }
        ?: workout.workout.routineDayNameSnapshot?.takeIf { it.isNotBlank() }
        ?: workout.muscleGroups.take(2).takeIf { it.isNotEmpty() }?.joinToString(" + ")
        ?: "Workout"

internal fun historyDate(date: Date, pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(date)

internal fun historyNumber(value: Float): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    maximumFractionDigits = 1
}.format(value)

internal fun historyDuration(workout: WorkoutWithMuscles): String? = workout.workout.endTime?.let { end ->
    val minutes = ((end.time - workout.workout.startTime.time).coerceAtLeast(0L) / 60_000L)
    if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}
