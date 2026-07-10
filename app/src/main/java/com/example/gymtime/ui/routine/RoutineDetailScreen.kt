package com.example.gymtime.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.domain.analytics.RoutineExerciseTrend
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    navController: NavController,
    viewModel: RoutineDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val routine = uiState.routine

    var selectedTab by remember { mutableStateOf(0) }
    var showMaxDaysDialog by remember { mutableStateOf(false) }
    var dayToDelete by remember { mutableStateOf<RoutineDay?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        viewModel.startWorkoutEvent.collect { firstExerciseId ->
            navController.navigate(Screen.ExerciseLogging.createRoute(firstExerciseId)) {
                popUpTo(Screen.Home.route)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            routine?.name ?: "",
                            color = LocalAppColors.current.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${uiState.days.size} ${if (uiState.days.size == 1) "day" else "days"}" +
                                if (routine?.isActive == true) " · ACTIVE" else "",
                            color = accentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LocalAppColors.current.textPrimary)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = LocalAppColors.current.textPrimary)
                        }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            if (routine?.isActive != true) {
                                DropdownMenuItem(
                                    text = { Text("Set as Active", color = LocalAppColors.current.textPrimary) },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.setActive()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit Name", color = LocalAppColors.current.textPrimary) },
                                onClick = {
                                    showTopMenu = false
                                    navController.navigate(Screen.RoutineForm.createRoute(viewModel.routineId))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.canAddMoreDays) {
                            navController.navigate(Screen.RoutineDayForm.createRoute(viewModel.routineId))
                        } else {
                            showMaxDaysDialog = true
                        }
                    },
                    containerColor = if (uiState.canAddMoreDays) accentColor else LocalAppColors.current.textTertiary,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Day", modifier = Modifier.size(32.dp))
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = accentColor
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("OVERVIEW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                    selectedContentColor = accentColor,
                    unselectedContentColor = LocalAppColors.current.textTertiary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.refreshStats()
                    },
                    text = { Text("STATS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                    selectedContentColor = accentColor,
                    unselectedContentColor = LocalAppColors.current.textTertiary
                )
            }

            when (selectedTab) {
                0 -> RoutineOverviewTab(
                    uiState = uiState,
                    nextDayOrderIndex = routine?.nextDayOrderIndex,
                    onStartDay = { viewModel.startWorkoutFromDay(it) },
                    onSetNextDay = { viewModel.setNextDay(it) },
                    onEditDay = { dayId ->
                        navController.navigate(Screen.RoutineDayForm.createRoute(viewModel.routineId, dayId))
                    },
                    onDuplicateDay = { dayId ->
                        if (uiState.canAddMoreDays) viewModel.duplicateDay(dayId) else showMaxDaysDialog = true
                    },
                    onMoveDay = { dayId, direction -> viewModel.moveDay(dayId, direction) },
                    onDeleteDay = { dayToDelete = it }
                )
                1 -> RoutineStatsTab(stats = stats, dayCount = uiState.days.size)
            }
        }
    }

    if (showMaxDaysDialog) {
        AlertDialog(
            onDismissRequest = { showMaxDaysDialog = false },
            title = { Text("Maximum Days Reached") },
            text = { Text("You can only have up to 10 days per routine.") },
            confirmButton = {
                TextButton(onClick = { showMaxDaysDialog = false }) { Text("OK") }
            }
        )
    }

    dayToDelete?.let { day ->
        AlertDialog(
            onDismissRequest = { dayToDelete = null },
            title = { Text("Delete Day?") },
            text = { Text("This will permanently delete \"${day.name}\" and its exercise list.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDay(day)
                        dayToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { dayToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RoutineOverviewTab(
    uiState: RoutineDetailUiState,
    nextDayOrderIndex: Int?,
    onStartDay: (Long) -> Unit,
    onSetNextDay: (Long) -> Unit,
    onEditDay: (Long) -> Unit,
    onDuplicateDay: (Long) -> Unit,
    onMoveDay: (Long, Int) -> Unit,
    onDeleteDay: (RoutineDay) -> Unit
) {
    if (uiState.days.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📅", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text(
                    text = "No days yet.",
                    color = LocalAppColors.current.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap + to add a workout day!",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(uiState.days, key = { it.day.id }) { dayWithExercises ->
            RoutineDayDetailCard(
                dayWithExercises = dayWithExercises,
                dayNumber = uiState.days.indexOf(dayWithExercises) + 1,
                isUpNext = dayWithExercises.day.orderIndex == nextDayOrderIndex,
                stat = uiState.dayStats[dayWithExercises.day.id],
                canMoveUp = uiState.days.first().day.id != dayWithExercises.day.id,
                canMoveDown = uiState.days.last().day.id != dayWithExercises.day.id,
                onStart = { onStartDay(dayWithExercises.day.id) },
                onSetNext = { onSetNextDay(dayWithExercises.day.id) },
                onEdit = { onEditDay(dayWithExercises.day.id) },
                onDuplicate = { onDuplicateDay(dayWithExercises.day.id) },
                onMove = { direction -> onMoveDay(dayWithExercises.day.id, direction) },
                onDelete = { onDeleteDay(dayWithExercises.day) }
            )
        }
    }
}

@Composable
private fun RoutineDayDetailCard(
    dayWithExercises: RoutineDayWithExercises,
    dayNumber: Int,
    isUpNext: Boolean,
    stat: RoutineDayStat?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onStart: () -> Unit,
    onSetNext: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary
    val exercises = dayWithExercises.exercises.sortedBy { it.routineExercise.orderIndex }

    GlowCard(
        onClick = onEdit,
        onLongClick = { showMenu = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DAY $dayNumber",
                                color = LocalAppColors.current.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            if (isUpNext) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = accentColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UP NEXT",
                                        color = accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = dayWithExercises.day.name,
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Day options",
                                    tint = LocalAppColors.current.textTertiary
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (!isUpNext) {
                                    DropdownMenuItem(
                                        text = { Text("Set as Next Day", color = LocalAppColors.current.textPrimary) },
                                        onClick = { showMenu = false; onSetNext() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Edit", color = LocalAppColors.current.textPrimary) },
                                    onClick = { showMenu = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate", color = LocalAppColors.current.textPrimary) },
                                    onClick = { showMenu = false; onDuplicate() }
                                )
                                if (canMoveUp) {
                                    DropdownMenuItem(
                                        text = { Text("Move Up", color = LocalAppColors.current.textPrimary) },
                                        onClick = { showMenu = false; onMove(-1) }
                                    )
                                }
                                if (canMoveDown) {
                                    DropdownMenuItem(
                                        text = { Text("Move Down", color = LocalAppColors.current.textPrimary) },
                                        onClick = { showMenu = false; onMove(1) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentColor,
                            modifier = Modifier.size(40.dp),
                            onClick = onStart
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start ${dayWithExercises.day.name}",
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                exercises.forEach { item ->
                    val re = item.routineExercise
                    val repText = when {
                        re.targetRepsMin != null && re.targetRepsMax != null && re.targetRepsMin != re.targetRepsMax ->
                            "${re.targetRepsMin}-${re.targetRepsMax}"
                        re.targetRepsMin != null -> "${re.targetRepsMin}"
                        re.targetRepsMax != null -> "${re.targetRepsMax}"
                        else -> null
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Superset marker bar
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(
                                    color = if (re.supersetGroupId != null) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${re.targetSets}×${repText ?: "—"}",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            text = item.exercise.name,
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stat?.let { s ->
                        val last = s.lastPerformed?.let { formatShortDate(it) } ?: "never"
                        "Last: $last · ${s.timesCompleted}× completed"
                    } ?: "Not performed yet",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RoutineStatsTab(stats: RoutineStats?, dayCount: Int) {
    if (stats == null || stats.timesCompleted == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📊", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text(
                    text = "No workouts logged yet.",
                    color = LocalAppColors.current.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stats appear after your first workout from this routine.",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "WORKOUTS",
                    value = "${stats.timesCompleted}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "AVG DURATION",
                    value = stats.avgDurationMinutes?.let { "$it min" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "TOTAL VOLUME",
                    value = formatVolume(stats.totalVolume),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "FREQUENCY",
                    value = stats.workoutsPerWeekRecent?.let { "%.1f/wk".format(it) } ?: "—",
                    subtitle = if (dayCount > 0) "$dayCount-day plan" else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                text = stats.lastPerformed?.let { "Last performed ${formatLongDate(it)}" } ?: "",
                color = LocalAppColors.current.textTertiary,
                fontSize = 12.sp
            )
        }
        if (stats.exerciseTrends.isNotEmpty()) {
            item {
                Text(
                    text = "EXERCISE PROGRESS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = LocalAppColors.current.textTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(stats.exerciseTrends, key = { it.exerciseId }) { trend ->
                ExerciseTrendRow(trend)
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    GlowCard(modifier = modifier, onClick = {}) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                color = LocalAppColors.current.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = LocalAppColors.current.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ExerciseTrendRow(trend: RoutineExerciseTrend) {
    val accentColor = MaterialTheme.colorScheme.primary
    GlowCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trend.exerciseName,
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Last best: ${trend.lastBestLabel}",
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 13.sp
                )
            }
            trend.e1rmDelta?.let { delta ->
                val (text, color) = when {
                    delta > 0.5f -> "▲ %.1f".format(delta) to accentColor
                    delta < -0.5f -> "▼ %.1f".format(-delta) to MaterialTheme.colorScheme.error
                    else -> "—" to LocalAppColors.current.textTertiary
                }
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = text,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatShortDate(date: Date): String =
    TimeFormatter.formatShortDate(date)

private fun formatLongDate(date: Date): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)

private fun formatVolume(volume: Float): String = when {
    volume >= 1_000_000f -> "%.1fM".format(volume / 1_000_000f)
    volume >= 10_000f -> "%.0fk".format(volume / 1_000f)
    volume >= 1_000f -> "%.1fk".format(volume / 1_000f)
    else -> "%.0f".format(volume)
}
