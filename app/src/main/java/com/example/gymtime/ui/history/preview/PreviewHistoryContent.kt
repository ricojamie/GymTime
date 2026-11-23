package com.example.gymtime.ui.history.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.tabular
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val HISTORY_SEARCH_WHITESPACE = Regex("\\s+")
private val DATE_FORMATTER_CACHE = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()
private val NUMBER_FORMAT_LOCAL = ThreadLocal<Pair<Locale, NumberFormat>?>()

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
    val locale = Locale.getDefault()
    val visible = remember(state.workouts, query, locale) {
        if (query.isBlank()) {
            state.workouts
        } else {
            val tokens = query.trim().lowercase(locale).split(HISTORY_SEARCH_WHITESPACE).filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                state.workouts
            } else {
                state.workouts.filter { item ->
                    val search = (
                        historyWorkoutTitle(item) + " " +
                            item.muscleGroups.joinToString(" ") + " " +
                            item.workout.routineNameSnapshot.orEmpty() + " " +
                            historyDate(item.workout.startTime, "MMMM yyyy", locale)
                        ).lowercase(locale)
                    tokens.all(search::contains)
                }
            }
        }
    }
    val months = remember(visible, locale) {
        visible.groupBy { historyDate(it.workout.startTime, "MMMM yyyy", locale) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Your training, on replay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = IronLogSpace.BottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            if (state.workouts.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Name, muscle, or month") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear history search")
                                }
                            }
                        } else null,
                        shape = IronLogRadius.ControlShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> item(key = "error") {
                    IronLogEmptyState(
                        title = "Couldn't load history",
                        body = state.error,
                        actionLabel = "Try again",
                        onAction = onRetry
                    )
                }
                state.workouts.isEmpty() -> item(key = "empty") {
                    IronLogEmptyState(
                        title = "Your first session starts here",
                        body = "Log a workout and build a history you can look back on.",
                        icon = Icons.Default.FitnessCenter,
                        actionLabel = "Start a workout",
                        onAction = onStartWorkout
                    )
                }
                visible.isEmpty() -> item(key = "empty-search") {
                    IronLogEmptyState(
                        title = "No matching workouts",
                        body = "Try a workout name, muscle, or month.",
                        actionLabel = "Clear search",
                        onAction = { query = "" }
                    )
                }
                else -> {
                    if (query.isNotBlank()) {
                        item(key = "search-count") {
                            Text(
                                text = "${visible.size} ${if (visible.size == 1) "workout" else "workouts"} found",
                                style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    months.forEach { (month, monthWorkouts) ->
                        item(key = "month-$month") {
                            IronLogSectionHeader(
                                title = month,
                                modifier = Modifier.padding(top = 6.dp),
                                trailingAction = {
                                    Text(
                                        text = "${monthWorkouts.size} ${if (monthWorkouts.size == 1) "session" else "sessions"}",
                                        style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                        items(monthWorkouts, key = { it.workout.id }) { workout ->
                            HistoryTimelineCard(
                                workout = workout,
                                locale = locale,
                                onClick = { onSelectWorkout(workout) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineCard(
    workout: WorkoutWithMuscles,
    locale: Locale,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dayNumber = remember(workout.workout.startTime, locale) {
        historyDate(workout.workout.startTime, "dd", locale)
    }
    val dayOfWeek = remember(workout.workout.startTime, locale) {
        historyDate(workout.workout.startTime, "EEE", locale)
    }
    val timeText = remember(workout.workout.startTime, workout.workout.endTime, locale) {
        if (workout.workout.endTime == null) null else historyDate(workout.workout.startTime, "h:mm a", locale)
    }
    val muscleLine = remember(workout.muscleGroups) {
        when {
            workout.muscleGroups.isEmpty() -> null
            workout.muscleGroups.size <= 3 -> workout.muscleGroups.joinToString(" · ")
            else -> workout.muscleGroups.take(3).joinToString(" · ") + " · +${workout.muscleGroups.size - 3}"
        }
    }
    val metricsLine = remember(workout.workingSetCount, workout.workout.startTime, workout.workout.endTime, workout.totalVolume, locale) {
        buildList {
            add("${workout.workingSetCount ?: 0} sets")
            historyDuration(workout)?.let { add(it) }
            workout.totalVolume?.takeIf { it > 0f }?.let { add("${historyNumber(it, locale)} lb") }
        }.joinToString(" · ")
    }

    IronLogCard(
        onClick = onClick,
        showBorder = true,
        containerColor = scheme.surfaceContainerLow,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = IronLogRadius.ControlShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 50.dp)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNumber,
                        style = MaterialTheme.typography.titleLarge.tabular(),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = dayOfWeek,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = historyWorkoutTitle(workout),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                        tint = scheme.primary
                    )
                }

                if (muscleLine != null) {
                    Text(
                        text = muscleLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = metricsLine,
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (timeText == null) {
                        IronLogStatusChip(
                            label = "In progress",
                            tone = IronLogStatusTone.ACTIVE
                        )
                    } else {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = scheme.onSurfaceVariant
                        )
                    }
                    workout.workout.rating?.let { rating ->
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Workout rating",
                            modifier = Modifier.size(16.dp),
                            tint = scheme.tertiary
                        )
                        Text(
                            text = "$rating/5",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal fun historyWorkoutTitle(workout: WorkoutWithMuscles): String =
    workout.workout.name?.takeIf { it.isNotBlank() }
        ?: workout.workout.routineDayNameSnapshot?.takeIf { it.isNotBlank() }
        ?: workout.muscleGroups.take(2).takeIf { it.isNotEmpty() }?.joinToString(" + ")
        ?: "Workout"

internal fun historyDate(
    date: Date,
    pattern: String,
    locale: Locale = Locale.getDefault()
): String {
    val formatter = DATE_FORMATTER_CACHE.getOrPut(pattern to locale) {
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    return Instant.ofEpochMilli(date.time)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

internal fun historyNumber(
    value: Float,
    locale: Locale = Locale.getDefault()
): String {
    val cached = NUMBER_FORMAT_LOCAL.get()
    val formatter = if (cached != null && cached.first == locale) {
        cached.second
    } else {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
        }.also { NUMBER_FORMAT_LOCAL.set(locale to it) }
    }
    return formatter.format(value)
}

internal fun historyDuration(workout: WorkoutWithMuscles): String? = workout.workout.endTime?.let { end ->
    val minutes = ((end.time - workout.workout.startTime.time).coerceAtLeast(0L) / 60_000L)
    if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}
