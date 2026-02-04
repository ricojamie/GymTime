package com.example.gymtime.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.ui.components.ExerciseIcons
import com.example.gymtime.ui.theme.LocalAppColors
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

                if (set.weight != null && (set.reps != null || set.distanceMeters != null)) {
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

                set.distanceMeters?.let { meters ->
                    val miles = TimeUtils.metersToMiles(meters)
                    Text(
                        text = "${TimeUtils.formatMiles(miles)} MI",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (set.distanceMeters != null && set.durationSeconds != null) {
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

@Composable
fun WorkoutOverviewContent(
    exercises: List<WorkoutExerciseSummary>,
    currentExerciseId: Long?,
    workoutStats: WorkoutStats,
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Current Workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Duration: ${workoutStats.duration}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textTertiary
            )
            Text(
                text = "·",
                color = LocalAppColors.current.textTertiary
            )
            Text(
                text = "${workoutStats.totalSets} sets",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise list
        exercises.forEachIndexed { index, summary ->
            val isActive = summary.exerciseId == currentExerciseId
            val isSuperset = summary.supersetGroupId != null
            
            // Determine connector visibility
            val prevSummary = exercises.getOrNull(index - 1)
            val nextSummary = exercises.getOrNull(index + 1)
            
            val isConnectedTop = isSuperset && prevSummary?.supersetGroupId == summary.supersetGroupId
            val isConnectedBottom = isSuperset && nextSummary?.supersetGroupId == summary.supersetGroupId

            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Visual Superset Connector
                if (isSuperset) {
                    Column(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Line
                        if (isConnectedTop) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        // Icon/Dot
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        
                        // Bottom Line
                        if (isConnectedBottom) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFF0D0D0D)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onExerciseClick(summary.exerciseId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Muscle group icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else LocalAppColors.current.inputBackground,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ExerciseIcons.getIconForMuscle(summary.targetMuscle),
                                    contentDescription = summary.targetMuscle,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else LocalAppColors.current.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = summary.exerciseName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else LocalAppColors.current.textPrimary
                                    )
                                    if (isActive) {
                                        Text(
                                            text = "→",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = summary.targetMuscle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.textTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${summary.setCount} sets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textTertiary
                            )
                            summary.bestWeight?.let { weight ->
                                Text(
                                    text = "${weight.toInt()} lbs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.textTertiary.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add Exercise button
        OutlinedButton(
            onClick = onAddExercise,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("+ Add Another Exercise", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ExerciseHistoryContent(
    exerciseName: String,
    personalRecords: PersonalRecords?,
    history: Map<Long, List<Set>>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PERSONAL RECORDS",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.textTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PR Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Heaviest Weight PR
            personalRecords?.heaviestWeight?.let { set ->
                PRBadge(
                    title = "Heaviest Weight",
                    value = "${set.weight?.toInt()} lbs",
                    subtitle = "×${set.reps} reps",
                    modifier = Modifier.weight(1f)
                )
            }

            // Best E1RM PR
            personalRecords?.bestE1RM?.let { (set, e1rm) ->
                PRBadge(
                    title = "Best E1RM",
                    value = "${e1rm.toInt()} lbs",
                    subtitle = "from ${set.weight?.toInt()}×${set.reps}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RECENT HISTORY",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.textTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // History list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            history.entries.take(10).forEach { (workoutId, sets) ->
                item {
                    WorkoutHistoryCard(sets = sets)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Close button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Close", color = LocalAppColors.current.textTertiary)
        }
    }
}

@Composable
fun PRBadge(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏆",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun WorkoutHistoryCard(
    sets: List<Set>
) {
    val firstSet = sets.firstOrNull()
    val dateStr = firstSet?.timestamp?.let {
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        formatter.format(it)
    } ?: "Unknown date"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D0D0D)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            sets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Set ${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textTertiary
                    )
                    Text(
                        text = buildString {
                            set.weight?.let { append("${it.toInt()} lbs") }
                            append(" × ")
                            set.reps?.let { append("$it reps") }
                            if (set.isWarmup) append(" (WU)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textPrimary
                    )
                }
                if (index < sets.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
