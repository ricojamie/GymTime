package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.ui.theme.IronLogSpace

data class PreviewRoutineDayStartState(
    val routineName: String = "",
    val routineExists: Boolean = true,
    val nextDayOrderIndex: Int? = null,
    val days: List<RoutineDayWithExercises> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val startingDayId: Long? = null,
    val startError: String? = null
)

@Composable
fun PreviewRoutineDayStartContent(
    state: PreviewRoutineDayStartState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onStartDay: (Long) -> Unit,
    onEditDay: (Long) -> Unit,
    onAddDay: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column {
            RoutineHeader("Choose a day", onBack, onHome)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = IronLogSpace.ItemGap,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = state.routineName.ifBlank { "Your routine" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pick the workout you want to do today.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (state.isLoading) {
                    item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                state.loadError?.let { message ->
                    item(key = "load-error") { RoutineError(message, onDismissError, onRetry) }
                }
                state.startError?.let { message ->
                    item(key = "start-error") { RoutineError(message, onDismissError) }
                }
                if (!state.isLoading && state.loadError == null) {
                    if (!state.routineExists) {
                        item(key = "missing") {
                            RoutineEmpty("Routine unavailable", "Go back to choose another routine.")
                        }
                    } else if (state.days.isEmpty()) {
                        item(key = "empty") {
                            RoutineEmpty(
                                title = "No workout days yet",
                                message = "Add a day and your exercise plan to get started.",
                                actionLabel = "Create a day",
                                onAction = onAddDay
                            )
                        }
                    }
                }
                itemsIndexed(state.days, key = { _, day -> day.day.id }) { index, day ->
                    PreviewRoutineDayCard(
                        day = day,
                        dayNumber = index + 1,
                        isNext = day.day.orderIndex == state.nextDayOrderIndex,
                        busy = state.startingDayId != null,
                        starting = state.startingDayId == day.day.id,
                        onStart = { onStartDay(day.day.id) },
                        onEdit = { onEditDay(day.day.id) }
                    )
                }
            }
        }
    }
}
