package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.RoutineExerciseWithDetails
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.util.TimeFormatter

@Composable
internal fun RoutinePanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    spotlight: Boolean = highlighted,
    showBorder: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    IronLogCard(
        modifier = modifier,
        onClick = onClick,
        highlighted = highlighted,
        spotlight = spotlight,
        showBorder = showBorder,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineHeader(
    title: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onBack()
                    },
                    enabled = enabled,
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onHome()
                    },
                    enabled = enabled,
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = if (enabled) surfaces.accent else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        IronLogDivider(insetStart = 0.dp)
    }
}

@Composable
internal fun RoutinePrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    IronLogPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = IronLogRadius.ControlShape
    )
}

@Composable
internal fun RoutineError(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    IronLogCard(
        containerColor = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        borderColor = scheme.error.copy(alpha = 0.35f),
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.ItemGap),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
            if (onRetry != null) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onRetry()
                    },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Try again", color = scheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text("Dismiss", color = scheme.onErrorContainer)
            }
        }
    }
}

@Composable
internal fun RoutineEmpty(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    IronLogCard(
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        IronLogEmptyState(
            title = title,
            body = message,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}

private sealed interface ExercisePlanBlock {
    data class Single(val item: RoutineExerciseWithDetails) : ExercisePlanBlock
    data class Superset(val groupNumber: Int, val members: List<RoutineExerciseWithDetails>) : ExercisePlanBlock
}

/** Targets and notes are read directly from the saved plan; grouped exercises keep their rotation order. */
@Composable
internal fun RoutineExerciseBlocks(exercises: List<RoutineExerciseWithDetails>) {
    val surfaces = LocalIronLogSurfaces.current
    val blocks = remember(exercises) {
        val sorted = exercises.sortedBy { it.routineExercise.orderIndex }
        val groups = sorted.groupBy { it.routineExercise.supersetGroupId }
        val rendered = mutableSetOf<String>()
        var nextGroupNumber = 1
        buildList {
            sorted.forEach { item ->
                val key = item.routineExercise.supersetGroupId
                val members = if (key != null) groups[key].orEmpty() else emptyList()
                if (key != null && members.size > 1) {
                    if (rendered.add(key)) {
                        add(
                            ExercisePlanBlock.Superset(
                                groupNumber = nextGroupNumber++,
                                members = members.sortedBy { it.routineExercise.supersetOrderIndex }
                            )
                        )
                    }
                } else {
                    add(ExercisePlanBlock.Single(item))
                }
            }
        }
    }

    IronLogGroupedCard(showBorder = true) {
        blocks.forEachIndexed { blockIndex, block ->
            if (blockIndex > 0) {
                IronLogDivider()
            }
            when (block) {
                is ExercisePlanBlock.Single -> {
                    RoutineExerciseLine(
                        item = block.item,
                        modifier = Modifier.padding(
                            horizontal = IronLogSpace.CardPadding,
                            vertical = IronLogSpace.RowVertical
                        )
                    )
                }
                is ExercisePlanBlock.Superset -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = IronLogSpace.CompactGap)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = IronLogSpace.CardPadding,
                                vertical = 4.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            IronLogStatusChip(
                                label = "Superset ${block.groupNumber}",
                                tone = IronLogStatusTone.SUPERSET,
                                icon = Icons.Default.Link
                            )
                            Text(
                                text = "Alternate exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        block.members.forEachIndexed { memberIndex, member ->
                            if (memberIndex > 0) {
                                Spacer(modifier = Modifier.size(2.dp))
                                IronLogDivider(insetStart = IronLogSpace.CardPadding)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(horizontal = IronLogSpace.CardPadding, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(surfaces.accent.copy(alpha = 0.65f), IronLogRadius.CapsuleShape)
                                )
                                RoutineExerciseLine(
                                    item = member,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineExerciseLine(
    item: RoutineExerciseWithDetails,
    modifier: Modifier = Modifier
) {
    val plan = item.routineExercise
    val reps = when {
        plan.targetRepsMin != null && plan.targetRepsMax != null && plan.targetRepsMin != plan.targetRepsMax ->
            "${plan.targetRepsMin}–${plan.targetRepsMax} reps"
        plan.targetRepsMin != null -> "${plan.targetRepsMin} reps"
        plan.targetRepsMax != null -> "Up to ${plan.targetRepsMax} reps"
        else -> null
    }
    val rest = plan.targetRestSeconds ?: item.exercise.defaultRestSeconds
    val summary = remember(plan.targetSets, reps, rest) {
        listOfNotNull(
            "${plan.targetSets} ${if (plan.targetSets == 1) "set" else "sets"}",
            reps,
            "${TimeFormatter.formatSecondsToMMSS(rest)} rest"
        ).joinToString(" · ")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = item.exercise.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
        )
        if (!plan.notes.isNullOrBlank()) {
            Text(
                text = plan.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
