package com.example.gymtime.ui.history.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.domain.progression.progressLabel
import com.example.gymtime.ui.components.ExerciseIcons
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.history.DeleteWorkoutDialog
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.tabular
import com.example.gymtime.util.TimeUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    val locale = Locale.getDefault()
    var showMenu by rememberSaveable(workout.workout.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(workout.workout.id) { mutableStateOf(false) }
    val exerciseGroups = remember(sets) { sets?.groupBy { it.set.exerciseId }?.values?.toList().orEmpty() }
    val notes = remember(workout.workout.note, workout.workout.ratingNote) {
        listOfNotNull(workout.workout.note, workout.workout.ratingNote)
            .filter { it.isNotBlank() }
            .distinct()
    }
    val metrics = remember(workout, locale) {
        buildList {
            add(
                IronLogMetric(
                    label = "Sets",
                    value = "${workout.workingSetCount ?: 0}"
                )
            )
            historyDuration(workout)?.let { duration ->
                add(IronLogMetric(label = "Duration", value = duration))
            }
            workout.totalVolume?.takeIf { it > 0f }?.let { volume ->
                add(
                    IronLogMetric(
                        label = "Volume",
                        value = "${historyNumber(volume, locale)} lb",
                        emphasized = true
                    )
                )
            }
        }
    }
    val formattedDateTime = remember(workout.workout.startTime, locale) {
        historyDate(workout.workout.startTime, "EEE, MMM d · h:mm a", locale)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = scheme.background,
        contentColor = scheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = IronLogSpace.ScreenHorizontal, end = 4.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        Text(
                            text = historyWorkoutTitle(workout),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (workout.workout.endTime == null) {
                            IronLogStatusChip(
                                label = "Active",
                                tone = IronLogStatusTone.ACTIVE
                            )
                        }
                    }
                    Text(
                        text = formattedDateTime,
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = scheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Workout actions")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(if (workout.workout.endTime == null) "Resume session" else "Reopen this session")
                                    Text(
                                        text = "Continue or fix this workout",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onResume() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy workout") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { showMenu = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share workout") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { showMenu = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to routine") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = { showMenu = false; onAddToRoutine() }
                        )
                        IronLogDivider(insetStart = 0.dp)
                        DropdownMenuItem(
                            text = { Text("Delete workout", color = scheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error) },
                            onClick = { showMenu = false; showDelete = true }
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close workout details")
                }
            }

            IronLogDivider(insetStart = 0.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = IronLogSpace.CardPadding,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                if (metrics.isNotEmpty() || workout.muscleGroups.isNotEmpty()) {
                    item(key = "metrics") {
                        IronLogCard(
                            showBorder = true,
                            containerColor = scheme.surfaceContainerLow,
                            contentPadding = PaddingValues(IronLogSpace.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                        ) {
                            if (metrics.isNotEmpty()) {
                                IronLogMetricRow(metrics = metrics)
                            }
                            if (workout.muscleGroups.isNotEmpty()) {
                                if (metrics.isNotEmpty()) {
                                    IronLogDivider(insetStart = 0.dp)
                                }
                                Text(
                                    text = workout.muscleGroups.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (notes.isNotEmpty() || workout.workout.rating != null) {
                    item(key = "notes") {
                        IronLogCard(
                            showBorder = true,
                            containerColor = scheme.surfaceContainerLow,
                            contentPadding = PaddingValues(IronLogSpace.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            IronLogSectionHeader(
                                title = "Your take",
                                trailingAction = workout.workout.rating?.let { rating ->
                                    {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalFireDepartment,
                                                contentDescription = "Workout rating",
                                                modifier = Modifier.size(16.dp),
                                                tint = scheme.tertiary
                                            )
                                            Text(
                                                text = "$rating/5",
                                                style = MaterialTheme.typography.labelLarge.tabular(),
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface
                                            )
                                        }
                                    }
                                }
                            )
                            notes.forEach { note ->
                                SelectionContainer {
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                when {
                    error != null -> item(key = "error") {
                        IronLogEmptyState(
                            title = "Couldn't load sets",
                            body = error,
                            actionLabel = "Try again",
                            onAction = onRetry
                        )
                    }
                    sets == null -> item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    exerciseGroups.isEmpty() -> item(key = "empty") {
                        IronLogEmptyState(
                            title = "No sets recorded",
                            body = "No sets were recorded in this session."
                        )
                    }
                    else -> {
                        item(key = "exercises-title") {
                            IronLogSectionHeader(
                                title = "The work",
                                trailingAction = {
                                    Text(
                                        text = "${exerciseGroups.size} ${if (exerciseGroups.size == 1) "exercise" else "exercises"}",
                                        style = MaterialTheme.typography.labelMedium.tabular(),
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                        items(exerciseGroups, key = { it.first().set.exerciseId }) { group ->
                            HistoryExerciseGroup(workout.workout.id, group, locale)
                        }
                    }
                }
            }

            Surface(
                color = scheme.surfaceContainerLow,
                contentColor = scheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IronLogDivider(insetStart = 0.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogPrimaryButton(
                            text = if (workout.workout.endTime == null) "Resume workout" else "Repeat workout",
                            onClick = if (workout.workout.endTime == null) onResume else onRepeat,
                            enabled = sets != null && error == null && (workout.workout.endTime == null || sets.isNotEmpty()),
                            leadingIcon = if (workout.workout.endTime == null) Icons.Default.PlayArrow else Icons.Default.Replay,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (workout.workout.endTime != null) {
                            Text(
                                text = "Start a fresh session with these exercises.",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        DeleteWorkoutDialog(
            onConfirm = { showDelete = false; onDelete() },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun HistoryExerciseGroup(
    workoutId: Long,
    sets: List<SetWithExerciseInfo>,
    locale: Locale
) {
    val first = sets.first()
    val working = remember(sets) { sets.filter { !it.set.isWarmup } }
    val warmups = remember(sets) { sets.filter { it.set.isWarmup } }
    val completedWorkingCount = remember(working) { working.count { it.set.isComplete } }
    val isSuperset = remember(sets) { sets.any { it.set.supersetGroupId != null } }
    var showWarmups by rememberSaveable(workoutId, first.set.exerciseId) {
        mutableStateOf(completedWorkingCount == 0)
    }
    val topWeightId = remember(working) {
        working.filter { it.set.isComplete && it.set.weight != null }
            .maxWithOrNull(compareBy<SetWithExerciseInfo> { it.set.weight ?: 0f }.thenBy { it.set.reps ?: 0 })
            ?.set?.id
    }
    val scheme = MaterialTheme.colorScheme

    IronLogGroupedCard(
        showBorder = true,
        containerColor = scheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IronLogSpace.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            Surface(
                shape = IronLogRadius.ControlShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = ExerciseIcons.getIconForMuscle(first.targetMuscle),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = first.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(
                        text = first.targetMuscle,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    if (isSuperset) {
                        IronLogStatusChip(
                            label = "Superset",
                            tone = IronLogStatusTone.SUPERSET
                        )
                    }
                }
            }
            Text(
                text = "$completedWorkingCount sets",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = scheme.onSurfaceVariant
            )
        }

        if (warmups.isNotEmpty()) {
            IronLogDivider(insetStart = 0.dp)
            Surface(
                onClick = { showWarmups = !showWarmups },
                color = scheme.surfaceContainerLow,
                contentColor = scheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .padding(horizontal = IronLogSpace.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(
                        text = "${warmups.size} ${if (warmups.size == 1) "warmup" else "warmups"}",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (showWarmups) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )
                    Icon(
                        imageVector = if (showWarmups) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (showWarmups) {
                warmups.forEachIndexed { index, info ->
                    IronLogDivider()
                    HistorySetRow(
                        set = info.set,
                        number = "W${index + 1}",
                        topWeight = false,
                        locale = locale
                    )
                }
            }
        }

        working.forEachIndexed { index, info ->
            IronLogDivider(insetStart = if (index == 0 && warmups.isEmpty()) 0.dp else IronLogSpace.CardPadding)
            HistorySetRow(
                set = info.set,
                number = "${index + 1}",
                topWeight = info.set.id == topWeightId,
                locale = locale
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySetRow(
    set: WorkoutSet,
    number: String,
    topWeight: Boolean,
    locale: Locale
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IronLogSpace.CardPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 24.dp)
            )
            Text(
                text = formatHistorySet(set, locale),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                fontWeight = if (topWeight) FontWeight.Bold else FontWeight.Medium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (topWeight) {
                IronLogStatusChip(
                    label = "Top weight",
                    tone = IronLogStatusTone.ACCENT
                )
            }
        }

        if (set.rpe != null || !set.isComplete) {
            FlowRow(
                modifier = Modifier.padding(start = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                set.rpe?.let { rpe ->
                    Text(
                        text = "RPE ${historyNumber(rpe, locale)}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = scheme.onSurfaceVariant
                    )
                }
                if (!set.isComplete) {
                    IronLogStatusChip(
                        label = "Unfinished",
                        tone = IronLogStatusTone.NEUTRAL
                    )
                }
            }
        }

        set.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp)
            )
        }
    }
}

/** Formats recorded values instead of assuming strength fields; covers all seven logging modes. */
internal fun formatHistorySet(
    set: WorkoutSet,
    locale: Locale = Locale.getDefault()
): String {
    val parts = mutableListOf<String>()
    when {
        set.weight != null && set.reps != null -> parts.add("${historyNumber(set.weight, locale)} lb × ${set.reps} reps")
        set.weight != null -> parts.add("${historyNumber(set.weight, locale)} lb")
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
    set.calories?.let { parts.add("${historyNumber(it, locale)} cal") }
    set.durationSeconds?.let { parts.add(TimeUtils.formatSecondsToHMS(it)) }
    return parts.joinToString(" · ").ifEmpty { "No values recorded" }
}
