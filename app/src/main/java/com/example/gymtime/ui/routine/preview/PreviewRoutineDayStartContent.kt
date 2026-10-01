package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineDayWithExercises

data class PreviewRoutineDayStartState(val routineName: String = "", val routineExists: Boolean = true,
    val nextDayOrderIndex: Int? = null, val days: List<RoutineDayWithExercises> = emptyList(),
    val isLoading: Boolean = true, val loadError: String? = null, val startingDayId: Long? = null, val startError: String? = null)

@Composable
fun PreviewRoutineDayStartContent(state: PreviewRoutineDayStartState,
    onBack: () -> Unit, onHome: () -> Unit, onStartDay: (Long) -> Unit, onEditDay: (Long) -> Unit,
    onAddDay: () -> Unit, onRetry: () -> Unit, onDismissError: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column {
            RoutineHeader("Choose a day", onBack, onHome)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(state.routineName.ifBlank { "Your routine" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Pick the workout you want to do today.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                state.loadError?.let { message -> item { RoutineError(message, onDismissError, onRetry) } }
                state.startError?.let { message -> item { RoutineError(message, onDismissError) } }
                if (!state.isLoading && state.loadError == null) {
                    if (!state.routineExists) item { RoutineEmpty("Routine unavailable", "Go back to choose another routine.") }
                    else if (state.days.isEmpty()) item { RoutineEmpty("No workout days yet", "Add a day and your exercise plan to get started.", "Create a day", onAddDay) }
                }
                items(state.days, key = { it.day.id }) { day ->
                    PreviewRoutineDayCard(day = day, dayNumber = state.days.indexOf(day) + 1,
                        isNext = day.day.orderIndex == state.nextDayOrderIndex, busy = state.startingDayId != null,
                        starting = state.startingDayId == day.day.id,
                        onStart = { onStartDay(day.day.id) }, onEdit = { onEditDay(day.day.id) })
                }
            }
        }
    }
}
