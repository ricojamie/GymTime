package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineExerciseWithDetails
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.util.TimeFormatter

@Composable
internal fun RoutinePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
internal fun RoutineHeader(title: String, onBack: () -> Unit, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("Home") }
    }
}

@Composable
internal fun RoutinePrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val action = LocalLoggerActionColors.current
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)) { Text(text) }
}

@Composable
internal fun RoutineError(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row {
                if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
            }
        }
    }
}

@Composable
internal fun RoutineEmpty(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    RoutinePanel {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) RoutinePrimaryAction(actionLabel, onAction)
    }
}

/** Targets and notes are read directly from the saved plan; grouped exercises keep their rotation order. */
@Composable
internal fun RoutineExerciseBlocks(exercises: List<RoutineExerciseWithDetails>) {
    val sorted = exercises.sortedBy { it.routineExercise.orderIndex }
    val groups = sorted.filter { it.routineExercise.supersetGroupId != null }.groupBy { it.routineExercise.supersetGroupId }
    val rendered = mutableSetOf<String>()
    val groupNumbers = groups.keys.mapIndexed { index, key -> key to index + 1 }.toMap()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.forEach { item ->
            val key = item.routineExercise.supersetGroupId
            val members = groups[key].orEmpty()
            if (key != null && members.size > 1) {
                if (rendered.add(key)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Superset ${groupNumbers[key]} · alternate these exercises", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            members.sortedBy { it.routineExercise.supersetOrderIndex }.forEach { RoutineExerciseLine(it) }
                        }
                    }
                }
            } else RoutineExerciseLine(item)
        }
    }
}

@Composable
private fun RoutineExerciseLine(item: RoutineExerciseWithDetails) {
    val plan = item.routineExercise
    val reps = when {
        plan.targetRepsMin != null && plan.targetRepsMax != null && plan.targetRepsMin != plan.targetRepsMax -> "${plan.targetRepsMin}–${plan.targetRepsMax} reps"
        plan.targetRepsMin != null -> "${plan.targetRepsMin} reps"
        plan.targetRepsMax != null -> "Up to ${plan.targetRepsMax} reps"
        else -> null
    }
    val rest = plan.targetRestSeconds ?: item.exercise.defaultRestSeconds
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(listOfNotNull("${plan.targetSets} ${if (plan.targetSets == 1) "set" else "sets"}", reps,
            "${TimeFormatter.formatSecondsToMMSS(rest)} rest").joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (!plan.notes.isNullOrBlank()) Text(plan.notes, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
