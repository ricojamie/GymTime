package com.example.gymtime.ui.history.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.staggeredEntranceOnce
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
@Composable
fun PreviewHistoryContent(
    state: HistoryUiState,
    onSelectWorkout: (WorkoutWithMuscles) -> Unit,
    onStartWorkout: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val focusManager = LocalFocusManager.current

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
    val hasActiveWorkout = remember(state.workouts) {
        state.workouts.any { it.workout.endTime == null }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = 12.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IronLogLedPip(
                        color = if (hasActiveWorkout) surfaces.ledGlow else scheme.onSurfaceVariant.copy(alpha = 0.45f),
                        size = 5.dp,
                        pulsing = hasActiveWorkout
                    )
                    Text(
                        text = "SESSION LOG",
                        style = IronLogType.technicalEyebrow(),
                        color = if (hasActiveWorkout) surfaces.accent else scheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onBackground
                )
            }
            if (state.workouts.isNotEmpty()) {
                IronLogRecessedWell(
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OdometerText(
                            text = "${visible.size}",
                            style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = surfaces.accent
                        )
                        Text(
                            text = if (visible.size == 1) "SESSION" else "SESSIONS",
                            style = IronLogType.technicalEyebrow(),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = IronLogSpace.BottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            if (state.workouts.isNotEmpty()) {
                item(key = "search") {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(surfaces.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            IronLogRecessedWell(
                                modifier = Modifier.fillMaxWidth(),
                                active = isSearchFocused || query.isNotEmpty(),
                                accentColor = surfaces.accent,
                                shape = IronLogRadius.KeycapShape,
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = if (query.isNotEmpty()) 2.dp else 12.dp,
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
                                        tint = if (isSearchFocused) surfaces.accent else scheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = "Name, muscle, or month",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (query.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                haptics.tick()
                                                query = ""
                                            },
                                            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear history search",
                                                tint = scheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                        CircularProgressIndicator(color = surfaces.accent)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IronLogMascotBadge(
                            size = 56.dp,
                            style = IronLogMascotStyle.HERO_CREST,
                            showGlow = true
                        )
                        IronLogEmptyState(
                            title = "Your first session starts here",
                            body = "Log a workout and build a history you can look back on.",
                            actionLabel = "Start a workout",
                            onAction = onStartWorkout
                        )
                    }
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
                    var runningIndex = 0
                    months.forEach { (month, monthWorkouts) ->
                        item(key = "month-$month") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = month.uppercase(),
                                    style = IronLogType.technicalEyebrow(),
                                    color = surfaces.accent
                                )
                                IronLogDivider(
                                    insetStart = 0.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${monthWorkouts.size} ${if (monthWorkouts.size == 1) "SESSION" else "SESSIONS"}",
                                    style = IronLogType.technicalEyebrow(),
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                        itemsIndexed(monthWorkouts, key = { _, item -> item.workout.id }) { indexInMonth, workout ->
                            val entranceIndex = runningIndex++
                            HistoryTimelineRow(
                                workout = workout,
                                locale = locale,
                                showTopSpine = indexInMonth > 0,
                                showBottomSpine = indexInMonth < monthWorkouts.lastIndex,
                                onClick = { onSelectWorkout(workout) },
                                modifier = if (entranceIndex < 6) {
                                    Modifier.staggeredEntranceOnce(
                                        key = "history-${workout.workout.id}",
                                        index = entranceIndex
                                    )
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineRow(
    workout: WorkoutWithMuscles,
    locale: Locale,
    showTopSpine: Boolean,
    showBottomSpine: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val spineColor = IronLogBorder.dividerColor()
    val isInProgress = workout.workout.endTime == null

    val dayNumber = remember(workout.workout.startTime, locale) {
        historyDate(workout.workout.startTime, "dd", locale)
    }
    val dayOfWeek = remember(workout.workout.startTime, locale) {
        historyDate(workout.workout.startTime, "EEE", locale).uppercase()
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(IronLogBorder.Hairline)
                    .height(22.dp)
                    .background(if (showTopSpine) spineColor else androidx.compose.ui.graphics.Color.Transparent)
            )
            IronLogLedPip(
                color = if (isInProgress) surfaces.ledGlow else surfaces.accent.copy(alpha = 0.7f),
                size = if (isInProgress) 6.dp else 4.dp,
                pulsing = isInProgress
            )
            Box(
                modifier = Modifier
                    .width(IronLogBorder.Hairline)
                    .weight(1f)
                    .background(if (showBottomSpine) spineColor else androidx.compose.ui.graphics.Color.Transparent)
            )
        }

        IronLogCard(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
            onClick = onClick,
            spotlight = isInProgress,
            highlighted = isInProgress,
            showBorder = true,
            shape = IronLogRadius.CardShape,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
                verticalAlignment = Alignment.Top
            ) {
                IronLogRecessedWell(
                    active = isInProgress,
                    accentColor = surfaces.accent,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.widthIn(min = 38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = dayNumber,
                            style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = if (isInProgress) surfaces.accent else scheme.onSurface
                        )
                        Text(
                            text = dayOfWeek,
                            style = IronLogType.technicalEyebrow(),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
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
                            tint = surfaces.accent
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
                                style = IronLogType.telemetryLabel(),
                                color = scheme.onSurfaceVariant
                            )
                        }
                        workout.workout.rating?.let { rating ->
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Workout rating",
                                modifier = Modifier.size(14.dp),
                                tint = scheme.tertiary
                            )
                            Text(
                                text = "$rating/5",
                                style = IronLogType.telemetryLabel(),
                                color = scheme.onSurfaceVariant
                            )
                        }
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
