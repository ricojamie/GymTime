package com.example.gymtime.ui.history.preview

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.domain.progression.progressLabel
import com.example.gymtime.ui.components.ExerciseIcons
import com.example.gymtime.ui.history.DeleteWorkoutDialog
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewWorkoutDetailsSheet(
    workout: WorkoutWithMuscles,
    sets: List<SetWithExerciseInfo>?,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onAddToRoutine: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val actions = LocalLoggerActionColors.current
    var showMenu by rememberSaveable(workout.workout.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(workout.workout.id) { mutableStateOf(false) }
    val exerciseGroups = remember(sets) { sets?.groupBy { it.set.exerciseId }?.values?.toList().orEmpty() }
    val notes = remember(workout.workout.note, workout.workout.ratingNote) {
        listOfNotNull(workout.workout.note, workout.workout.ratingNote).filter { it.isNotBlank() }.distinct()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.background, contentColor = scheme.onBackground
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(historyWorkoutTitle(workout), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(historyDate(workout.workout.startTime, "EEE, MMM d · h:mm a"),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Workout actions") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Column {
                                Text(if (workout.workout.endTime == null) "Resume session" else "Reopen this session")
                                Text("Continue or fix this workout", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            } },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { showMenu = false; onResume() }
                        )
                        DropdownMenuItem(text = { Text("Copy workout") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { showMenu = false; onCopy() })
                        DropdownMenuItem(text = { Text("Share workout") }, leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { showMenu = false; onShare() })
                        DropdownMenuItem(text = { Text("Add to routine") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { showMenu = false; onAddToRoutine() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Delete workout", color = scheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = scheme.error) },
                            onClick = { showMenu = false; showDelete = true })
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close workout details") }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "metrics") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailMetric("${workout.workingSetCount ?: 0}", "working sets")
                        historyDuration(workout)?.let { DetailMetric(it, "duration") }
                        workout.totalVolume?.takeIf { it > 0f }?.let { DetailMetric("${historyNumber(it)} lb", "volume") }
                        if (workout.workout.endTime == null) DetailMetric("Live", "in progress")
                    }
                }
                if (workout.muscleGroups.isNotEmpty()) item(key = "muscles") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        workout.muscleGroups.forEach { HistoryMusclePill(it) }
                    }
                }
                if (notes.isNotEmpty() || workout.workout.rating != null) item(key = "notes") {
                    Surface(shape = RoundedCornerShape(18.dp), color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Notes, null, modifier = Modifier.size(18.dp))
                                Text("Your take", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                workout.workout.rating?.let { rating ->
                                    Icon(Icons.Default.LocalFireDepartment, contentDescription = "Workout rating", modifier = Modifier.size(18.dp))
                                    Text("$rating/5", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                            notes.forEach { note -> SelectionContainer { Text(note, style = MaterialTheme.typography.bodyMedium) } }
                        }
                    }
                }
                when {
                    error != null -> item(key = "error") {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                        }
                    }
                    sets == null -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    exerciseGroups.isEmpty() -> item(key = "empty") {
                        Text("No sets recorded in this session.", style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    else -> {
                        item(key = "exercises-title") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("The work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${exerciseGroups.size} ${if (exerciseGroups.size == 1) "exercise" else "exercises"}",
                                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                        items(exerciseGroups, key = { it.first().set.exerciseId }) { group ->
                            HistoryExerciseGroup(workout.workout.id, group)
                        }
                    }
                }
            }
            Surface(color = scheme.surface, contentColor = scheme.onSurface, shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = if (workout.workout.endTime == null) onResume else onRepeat,
                        enabled = sets != null && error == null && (workout.workout.endTime == null || sets.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = actions.fill, contentColor = actions.onFill)
                    ) {
                        Icon(if (workout.workout.endTime == null) Icons.Default.PlayArrow else Icons.Default.Replay, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (workout.workout.endTime == null) "Resume workout" else "Repeat workout", fontWeight = FontWeight.Bold)
                    }
                    if (workout.workout.endTime != null) Text("Start a fresh session with these exercises.",
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
    if (showDelete) DeleteWorkoutDialog(onConfirm = { showDelete = false; onDelete() }, onDismiss = { showDelete = false })
}

@Composable
private fun DetailMetric(value: String, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryExerciseGroup(workoutId: Long, sets: List<SetWithExerciseInfo>) {
    val first = sets.first()
    val working = remember(sets) { sets.filter { !it.set.isWarmup } }
    val warmups = remember(sets) { sets.filter { it.set.isWarmup } }
    val completedWorkingCount = remember(working) { working.count { it.set.isComplete } }
    var showWarmups by rememberSaveable(workoutId, first.set.exerciseId) { mutableStateOf(completedWorkingCount == 0) }
    val topWeightId = remember(working) {
        working.filter { it.set.isComplete && it.set.weight != null }.maxWithOrNull(
            compareBy<SetWithExerciseInfo> { it.set.weight ?: 0f }.thenBy { it.set.reps ?: 0 }
        )?.set?.id
    }
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surface, contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                    Icon(ExerciseIcons.getIconForMuscle(first.targetMuscle), null, modifier = Modifier.padding(10.dp).size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(first.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(first.targetMuscle, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                Text("$completedWorkingCount sets", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            if (sets.any { it.set.supersetGroupId != null }) Text("Superset", style = MaterialTheme.typography.labelSmall, color = scheme.secondary)
            if (warmups.isNotEmpty()) {
                Surface(onClick = { showWarmups = !showWarmups }, shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainerLow, contentColor = scheme.onSurfaceVariant) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${warmups.size} ${if (warmups.size == 1) "warmup" else "warmups"}",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(if (showWarmups) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                        Icon(if (showWarmups) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp))
                    }
                }
                if (showWarmups) warmups.forEachIndexed { index, info -> HistorySetRow(info.set, "W${index + 1}", topWeight = false) }
            }
            working.forEachIndexed { index, info -> HistorySetRow(info.set, "${index + 1}", topWeight = info.set.id == topWeightId) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySetRow(set: WorkoutSet, number: String, topWeight: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = if (topWeight) scheme.primaryContainer else Color.Transparent,
        contentColor = if (topWeight) scheme.onPrimaryContainer else scheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 20.dp))
                Text(formatHistorySet(set), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (topWeight) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            if (topWeight || set.rpe != null || !set.isComplete) FlowRow(
                modifier = Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (topWeight) Text("Top weight", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                set.rpe?.let { Text("RPE ${historyNumber(it)}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                if (!set.isComplete) Text("Unfinished", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            set.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 30.dp))
            }
        }
    }
}

/** Formats recorded values instead of assuming strength fields; covers all seven logging modes. */
internal fun formatHistorySet(set: WorkoutSet): String {
    val parts = mutableListOf<String>()
    when {
        set.weight != null && set.reps != null -> parts.add("${historyNumber(set.weight)} lb × ${set.reps} reps")
        set.weight != null -> parts.add("${historyNumber(set.weight)} lb")
        set.reps != null -> parts.add("${set.reps} reps")
    }
    when {
        set.distanceValue != null && set.distanceUnit != null ->
            parts.add("${TimeUtils.formatDistance(set.distanceValue, set.distanceUnit)} ${set.distanceUnit.progressLabel()}")
        set.distanceMeters != null && set.distanceUnit?.isConvertibleToMeters != false -> {
            val unit = set.distanceUnit ?: DistanceUnit.METERS
            parts.add("${TimeUtils.formatDistance(TimeUtils.metersToDistance(set.distanceMeters, unit), unit)} ${unit.progressLabel()}")
        }
    }
    set.calories?.let { parts.add("${historyNumber(it)} cal") }
    set.durationSeconds?.let { parts.add(TimeUtils.formatSecondsToHMS(it)) }
    return parts.joinToString(" · ").ifEmpty { "No values recorded" }
}
