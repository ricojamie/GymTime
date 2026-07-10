package com.example.gymtime.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.domain.analytics.LinearRegression
import com.example.gymtime.ui.components.ExerciseIcons
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.util.OneRepMaxCalculator
import com.example.gymtime.util.TimeFormatter
import com.example.gymtime.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SupersetIndicatorPills(
    exercises: List<Exercise>,
    currentExerciseIndex: Int,
    currentExerciseId: Long?,
    onExerciseClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val nameMaxChars = if (exercises.size > 3) 8 else 12

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Superset label
        Surface(
            shape = RoundedCornerShape(50),
            color = accentColor.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "SUPERSET",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Exercise pills
        exercises.forEachIndexed { index, exercise ->
            val isActive = exercise.id == currentExerciseId

            Surface(
                onClick = { onExerciseClick(exercise.id) },
                shape = RoundedCornerShape(50),
                color = if (isActive) accentColor else Color.Transparent,
                border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, accentColor) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.Black else accentColor
                    )
                    Text(
                        text = exercise.name.take(nameMaxChars) + if (exercise.name.length > nameMaxChars) ".." else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color.Black else LocalAppColors.current.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun LogSetButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            isPressed = true
            keyboardController?.hide()
            scope.launch {
                delay(100)
                isPressed = false
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "LOG SET ✓",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = Color.Black
        )
    }
}

@Composable
fun ExerciseSetLogCard(
    set: Set,
    setNumber: Int,
    isPersonalBest: Boolean = false,
    onEdit: (Set) -> Unit = {},
    onDelete: (Set) -> Unit = {},
    onAddNote: (Set) -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPersonalBest) accentColor.copy(alpha = 0.15f) else LocalAppColors.current.surfaceCards
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isPersonalBest) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = { showContextMenu = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show trophy for personal best
                if (isPersonalBest) {
                    Text(
                        text = "🏆",
                        fontSize = 18.sp
                    )
                } else {
                    Text(
                        text = "$setNumber",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                set.weight?.let {
                    Text(
                        text = "$it LBS",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPersonalBest) MaterialTheme.colorScheme.primary else LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                set.calories?.let {
                    if (set.weight != null) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalAppColors.current.textTertiary
                        )
                    }
                    Text(
                        text = "${it.toInt()} CAL",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (set.weight != null && (set.reps != null || displayDistance(set) != null)) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textTertiary
                    )
                }

                set.reps?.let {
                    Text(
                        text = "$it REPS",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPersonalBest) MaterialTheme.colorScheme.primary else LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                displayDistance(set)?.let { distanceText ->
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (displayDistance(set) != null && set.durationSeconds != null) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textTertiary
                    )
                }

                set.durationSeconds?.let { seconds ->
                    Text(
                        text = TimeUtils.formatSecondsToHMS(seconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (set.isWarmup) {
                    Text(
                        text = "WU",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Superset Indicator
                if (set.supersetGroupId != null) {
                    Text(
                        text = "SS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // PB Badge
                if (isPersonalBest) {
                    Text(
                        text = "PB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Note indicator
                set.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "📝",
                        fontSize = 14.sp
                    )
                }
            }

            // Three-dot menu icon (affordance hint)
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = LocalAppColors.current.textTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }

        // Show note text if present
        set.note?.takeIf { it.isNotBlank() }?.let { noteText ->
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textTertiary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showContextMenu = false
                    onEdit(set)
                }
            )
            DropdownMenuItem(
                text = { Text(if (set.note.isNullOrBlank()) "Add Note" else "Edit Note") },
                onClick = {
                    showContextMenu = false
                    onAddNote(set)
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showContextMenu = false
                    onDelete(set)
                }
            )
        }
    }
}

private fun displayDistance(set: Set): String? {
    val unit = set.distanceUnit ?: DistanceUnit.MILES
    val value = when {
        set.distanceValue != null -> set.distanceValue
        set.distanceMeters != null && unit.isConvertibleToMeters -> TimeUtils.metersToDistance(set.distanceMeters, unit)
        else -> null
    } ?: return null

    val label = when (unit) {
        DistanceUnit.METERS -> "M"
        DistanceUnit.KILOMETERS -> "KM"
        DistanceUnit.YARDS -> "YD"
        DistanceUnit.FEET -> "FT"
        DistanceUnit.MILES -> "MI"
        DistanceUnit.STEPS -> "STEPS"
        DistanceUnit.FLOORS -> "FLOORS"
    }
    return "${TimeUtils.formatDistance(value, unit)} $label"
}


@Composable
fun WorkoutOverviewCommandPanel(
    panelData: WorkoutPanelData,
    currentExerciseId: Long?,
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit
) {
    val exercises = panelData.exercises
    val stats = panelData.stats

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Workout",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary,
                maxLines = 1
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    text = stats.duration,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        WorkoutOverviewMetricStrip(
            sets = stats.totalSets,
            duration = stats.duration,
            volume = stats.totalVolume
        )

        if (panelData.muscleBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            WorkoutOverviewMuscleChips(panelData.muscleBreakdown)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (exercises.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = LocalAppColors.current.inputBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No exercises yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.textPrimary
                    )
                    Text(
                        text = "Add an exercise to start logging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(exercises) { index, summary ->
                    val prevSummary = exercises.getOrNull(index - 1)
                    val nextSummary = exercises.getOrNull(index + 1)

                    WorkoutOverviewPanelRow(
                        summary = summary,
                        setPreviews = panelData.setPreviews[summary.exerciseId].orEmpty(),
                        plannedSets = panelData.plannedSets[summary.exerciseId],
                        isActive = summary.exerciseId == currentExerciseId,
                        isConnectedTop = summary.supersetGroupId != null &&
                            prevSummary?.supersetGroupId == summary.supersetGroupId,
                        isConnectedBottom = summary.supersetGroupId != null &&
                            nextSummary?.supersetGroupId == summary.supersetGroupId,
                        onClick = { onExerciseClick(summary.exerciseId) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAddExercise,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Exercise", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WorkoutOverviewMetricStrip(
    sets: Int,
    duration: String,
    volume: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = LocalAppColors.current.inputBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkoutOverviewMetricCell(
                label = "Sets",
                value = sets.toString(),
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            WorkoutOverviewMetricCell(
                label = "Duration",
                value = duration,
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            WorkoutOverviewMetricCell(
                label = "Volume",
                value = if (volume <= 0f) "—" else "${formatPanelVolume(volume.toInt())} lbs",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WorkoutOverviewMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(LocalAppColors.current.textTertiary.copy(alpha = 0.25f))
    )
}

@Composable
private fun WorkoutOverviewMuscleChips(breakdown: List<MuscleBreakdown>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breakdown.forEach { entry ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = ExerciseIcons.getIconForMuscle(entry.muscle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = entry.muscle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = buildString {
                            append("${entry.setCount} ")
                            append(if (entry.setCount == 1) "set" else "sets")
                            if (entry.volume > 0f) append(" · ${formatPanelVolume(entry.volume.toInt())} lbs")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatPanelVolume(volume: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(volume)

@Composable
private fun WorkoutOverviewPanelRow(
    summary: WorkoutExerciseSummary,
    setPreviews: List<String>,
    plannedSets: Int?,
    isActive: Boolean,
    isConnectedTop: Boolean,
    isConnectedBottom: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkoutOverviewSupersetConnector(
            isSuperset = summary.supersetGroupId != null,
            isConnectedTop = isConnectedTop,
            isConnectedBottom = isConnectedBottom
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) accent.copy(alpha = 0.10f) else Color(0xFF0D0D0D)
            ),
            shape = RoundedCornerShape(10.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(if (isActive) accent else Color.Transparent)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isActive) accent.copy(alpha = 0.18f)
                                else LocalAppColors.current.inputBackground,
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ExerciseIcons.getIconForMuscle(summary.targetMuscle),
                            contentDescription = summary.targetMuscle,
                            tint = if (isActive) accent else LocalAppColors.current.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = summary.exerciseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) accent else LocalAppColors.current.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (setPreviews.isEmpty()) "Not started" else setPreviews.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (setPreviews.isEmpty()) {
                                LocalAppColors.current.textTertiary
                            } else {
                                LocalAppColors.current.textSecondary
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Plan progress: "done/planned" with a check when complete
                    if (plannedSets != null && plannedSets > 0) {
                        val done = setPreviews.size
                        val complete = done >= plannedSets
                        Text(
                            text = if (complete) "✓ $done/$plannedSets" else "$done/$plannedSets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                complete -> accent
                                done > 0 -> LocalAppColors.current.textSecondary
                                else -> LocalAppColors.current.textTertiary.copy(alpha = 0.7f)
                            },
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isActive) accent else LocalAppColors.current.textTertiary.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutOverviewSupersetConnector(
    isSuperset: Boolean,
    isConnectedTop: Boolean,
    isConnectedBottom: Boolean
) {
    if (!isSuperset) {
        Spacer(modifier = Modifier.width(18.dp))
        return
    }

    Column(
        modifier = Modifier
            .width(18.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isConnectedTop) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )

        if (isConnectedBottom) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private data class SessionSummary(
    val workoutId: Long,
    val date: java.util.Date,
    val sets: List<Set>,
    val workingSets: List<Set>,
    val topSet: Set?,
    val topE1RM: Float?,
    val volume: Float,
    val warmupCount: Int,
    val isPR: Boolean
)

private fun buildSessionSummaries(history: Map<Long, List<Set>>): List<SessionSummary> {
    // history entries arrive newest-first; flip to chronological to detect PRs as the running max grows.
    val chronological = history.entries.toList().reversed()
    var runningMaxE1RM = 0f
    val byOldest = chronological.map { (workoutId, sets) ->
        val working = sets.filter { !it.isWarmup }
        val topSet = working
            .filter { it.weight != null && it.reps != null }
            .maxByOrNull { (it.weight ?: 0f) * (1 + (it.reps ?: 0) / 30f) }
        val topE1RM = topSet?.let { s ->
            val w = s.weight ?: return@let null
            val r = s.reps ?: return@let null
            OneRepMaxCalculator.calculateE1RM(w, r)
        }
        val isPR = topE1RM != null && topE1RM > runningMaxE1RM
        if (topE1RM != null && topE1RM > runningMaxE1RM) runningMaxE1RM = topE1RM
        val volume = working.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()
        SessionSummary(
            workoutId = workoutId,
            date = sets.firstOrNull()?.timestamp ?: java.util.Date(0),
            sets = sets,
            workingSets = working,
            topSet = topSet,
            topE1RM = topE1RM,
            volume = volume,
            warmupCount = sets.count { it.isWarmup },
            isPR = isPR
        )
    }
    return byOldest.reversed() // back to newest-first for display
}

private fun relativeDay(date: java.util.Date): String {
    val now = System.currentTimeMillis()
    val diffMs = now - date.time
    val days = (diffMs / (1000L * 60 * 60 * 24)).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "1d ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

@Composable
fun ExerciseHistoryContent(
    exerciseName: String,
    personalRecords: PersonalRecords?,
    history: Map<Long, List<Set>>,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val sessions = remember(history) { buildSessionSummaries(history) }
    val lastSession = sessions.firstOrNull()
    val bestE1RMValue = personalRecords?.bestE1RM?.second

    // Group max-weight per rep count (working sets only) — answers "what have I done for X reps?"
    val bestByReps: List<Pair<Int, Float>> = remember(history) {
        history.values.flatten()
            .filter { !it.isWarmup && it.weight != null && it.reps != null }
            .groupBy { it.reps!! }
            .mapValues { entry -> entry.value.maxOf { it.weight!! } }
            .toList()
            .sortedBy { it.first }
    }

    var expandedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            HistorySheetHeader(
                exerciseName = exerciseName,
                sessionCount = sessions.size,
                lastDate = lastSession?.date
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (sessions.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No history yet — your sets will show up here once you log a workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            return@LazyColumn
        }

        item {
            StatRow(
                personalRecords = personalRecords,
                lastSession = lastSession
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Sparkline only if we have at least 2 data points
        val sparklinePoints = sessions
            .mapNotNull { it.topE1RM }
            .reversed() // chronological for left→right
        if (sparklinePoints.size >= 2) {
            item {
                E1RMSparkline(
                    points = sparklinePoints,
                    prCeiling = bestE1RMValue
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (bestByReps.isNotEmpty()) {
            item {
                RepMaxChipsRow(bestByReps = bestByReps)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        item {
            Text(
                text = "SESSIONS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        itemsIndexed(sessions.take(15)) { _, session ->
            SessionRow(
                session = session,
                expanded = expandedWorkoutId == session.workoutId,
                onToggle = {
                    expandedWorkoutId = if (expandedWorkoutId == session.workoutId) null else session.workoutId
                }
            )
            HorizontalDivider(
                color = colors.textTertiary.copy(alpha = 0.10f),
                thickness = 1.dp
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistorySheetHeader(
    exerciseName: String,
    sessionCount: Int,
    lastDate: java.util.Date?
) {
    val colors = LocalAppColors.current
    Column {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        val subtitle = if (sessionCount == 0) {
            "No sessions yet"
        } else {
            val label = if (sessionCount == 1) "session" else "sessions"
            val tail = lastDate?.let { " · last ${relativeDay(it)}" } ?: ""
            "$sessionCount $label$tail"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = colors.textTertiary.copy(alpha = 0.15f),
            thickness = 1.dp
        )
    }
}

@Composable
private fun StatRow(
    personalRecords: PersonalRecords?,
    lastSession: SessionSummary?
) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary

    val heaviest = personalRecords?.heaviestWeight
    val bestE1RM = personalRecords?.bestE1RM
    val lastTop = lastSession?.topSet

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatBlock(
            label = "HEAVIEST",
            value = heaviest?.weight?.let { "${it.toInt()}" } ?: "—",
            valueSuffix = if (heaviest?.weight != null) "lb" else null,
            subtitle = heaviest?.reps?.let { "× $it reps" } ?: " ",
            valueColor = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatBlock(
            label = "BEST E1RM",
            value = bestE1RM?.second?.toInt()?.toString() ?: "—",
            valueSuffix = if (bestE1RM != null) "lb" else null,
            subtitle = bestE1RM?.let { (s, _) ->
                val w = s.weight?.toInt() ?: return@let " "
                val r = s.reps ?: return@let " "
                "from $w × $r"
            } ?: " ",
            valueColor = primary,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatBlock(
            label = "LAST SESSION",
            value = lastTop?.let { s ->
                val w = s.weight?.toInt() ?: return@let "—"
                val r = s.reps ?: return@let "—"
                "$w × $r"
            } ?: "—",
            valueSuffix = null,
            subtitle = lastSession?.date?.let { relativeDay(it) } ?: " ",
            valueColor = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    valueSuffix: String?,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 22.sp
            )
            if (valueSuffix != null) {
                Text(
                    text = " $valueSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(LocalAppColors.current.textTertiary.copy(alpha = 0.15f))
    )
}

@Composable
private fun E1RMSparkline(
    points: List<Float>,
    prCeiling: Float?
) {
    if (points.size < 2) return
    val primary = MaterialTheme.colorScheme.primary
    val colors = LocalAppColors.current

    // Fitted-line values at x = 0 and x = n-1: drawn as the dashed trend line and
    // folded into the y-range so the line stays inside the canvas.
    val trendEndpoints = remember(points) {
        LinearRegression.fit(points)?.let {
            listOf(it.intercept, it.intercept + it.slope * (points.size - 1))
        } ?: emptyList()
    }
    val minVal = (points + trendEndpoints).min()
    val maxVal = (listOf(points.max()) + listOfNotNull(prCeiling) + trendEndpoints).max()
    val span = (maxVal - minVal).coerceAtLeast(1f)
    val ceilingDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val trendDash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "E1RM TREND",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold
            )
            val first = points.first()
            val last = points.last()
            val deltaPct = if (first > 0f) ((last - first) / first) * 100f else 0f
            val deltaText = when {
                deltaPct >= 0.5f -> "↑ ${"%.0f".format(deltaPct)}%"
                deltaPct <= -0.5f -> "↓ ${"%.0f".format(-deltaPct)}%"
                else -> "flat"
            }
            val deltaColor = when {
                deltaPct >= 0.5f -> primary
                deltaPct <= -0.5f -> colors.textSecondary
                else -> colors.textTertiary
            }
            Text(
                text = deltaText,
                style = MaterialTheme.typography.labelSmall,
                color = deltaColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val w = size.width
            val h = size.height
            val padY = 6f
            val usableH = h - 2 * padY
            val stepX = if (points.size > 1) w / (points.size - 1) else w

            fun yFor(value: Float): Float {
                val norm = (value - minVal) / span
                return h - padY - norm * usableH
            }

            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                color = primary.copy(alpha = 0.10f)
            )

            // PR ceiling dashed line
            if (prCeiling != null && prCeiling in minVal..maxVal) {
                val ceilingY = yFor(prCeiling)
                drawLine(
                    color = primary.copy(alpha = 0.35f),
                    start = Offset(0f, ceilingY),
                    end = Offset(w, ceilingY),
                    strokeWidth = 1f,
                    pathEffect = ceilingDash
                )
            }

            // Regression trend line
            if (trendEndpoints.size == 2) {
                drawLine(
                    color = colors.textTertiary.copy(alpha = 0.7f),
                    start = Offset(0f, yFor(trendEndpoints.first())),
                    end = Offset(w, yFor(trendEndpoints.last())),
                    strokeWidth = 1.5f,
                    pathEffect = trendDash
                )
            }

            drawPath(
                path = linePath,
                color = primary,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )

            // Highlight the latest point
            val lastX = (points.size - 1) * stepX
            val lastY = yFor(points.last())
            drawCircle(
                color = primary,
                radius = 4f,
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = primary.copy(alpha = 0.25f),
                radius = 8f,
                center = Offset(lastX, lastY)
            )
        }
    }
}

@Composable
private fun RepMaxChipsRow(bestByReps: List<Pair<Int, Float>>) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary
    Column {
        Text(
            text = "BEST AT EACH REP COUNT",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bestByReps.forEach { (reps, weight) ->
                Box(
                    modifier = Modifier
                        .background(
                            color = colors.surfaceCards,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${weight.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = " × $reps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary
    val noteSet = session.workingSets.firstOrNull { !it.note.isNullOrBlank() }
        ?: session.sets.firstOrNull { !it.note.isNullOrBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left rail: date
            Column(modifier = Modifier.width(60.dp)) {
                Text(
                    text = TimeFormatter.formatShortDate(session.date, java.util.Locale.US),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = relativeDay(session.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center: top set + summary
            Column(modifier = Modifier.weight(1f)) {
                val topSet = session.topSet
                val topText = if (topSet?.weight != null && topSet.reps != null) {
                    "${topSet.weight.toInt()} × ${topSet.reps}"
                } else if (topSet?.reps != null) {
                    "${topSet.reps} reps"
                } else {
                    "—"
                }
                Text(
                    text = topText,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                val workingCount = session.workingSets.size
                val setLabel = if (workingCount == 1) "set" else "sets"
                val warmupSuffix = if (session.warmupCount > 0) " (+${session.warmupCount} WU)" else ""
                val volumeStr = if (session.volume > 0f) {
                    " · ${formatVolume(session.volume)} vol"
                } else ""
                val e1rmStr = session.topE1RM?.let { " · E1RM ${it.toInt()}" } ?: ""
                Text(
                    text = "$workingCount $setLabel$warmupSuffix$volumeStr$e1rmStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    fontSize = 12.sp
                )
                if (noteSet != null) {
                    Text(
                        text = "“${noteSet.note}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // PR pill
            if (session.isPR) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(
                            color = primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PR",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .padding(start = 72.dp, end = 4.dp)
            ) {
                session.sets.forEachIndexed { index, set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (set.isWarmup) "WU" else "Set ${index + 1 - session.warmupCount.coerceAtMost(index)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = buildString {
                                set.weight?.let { append("${it.toInt()} lb") }
                                set.reps?.let {
                                    if (set.weight != null) append(" × ") else if (isNotEmpty()) append(" ")
                                    append("$it")
                                }
                                set.rpe?.let { append(" @ RPE ${"%.1f".format(it)}") }
                            }.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (set.isWarmup) colors.textTertiary else colors.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatVolume(v: Float): String {
    val i = v.toInt()
    return if (i >= 1000) {
        val k = i / 1000.0
        "${"%.1f".format(k).trimEnd('0').trimEnd('.')}k lb"
    } else {
        "$i lb"
    }
}
