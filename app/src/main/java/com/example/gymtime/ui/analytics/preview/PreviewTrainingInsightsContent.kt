package com.example.gymtime.ui.analytics.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.domain.analytics.TrainingInsightComparison
import com.example.gymtime.domain.analytics.TrainingInsightDirection
import com.example.gymtime.domain.analytics.TrainingInsightExercise
import com.example.gymtime.domain.analytics.TrainingInsightMetric
import com.example.gymtime.domain.analytics.TrainingInsightMetricKind
import com.example.gymtime.domain.analytics.TrainingInsightMuscle
import com.example.gymtime.domain.analytics.TrainingInsightProgressStatus
import com.example.gymtime.domain.analytics.TrainingInsightRecordEvent
import com.example.gymtime.domain.analytics.TrainingInsightRecordKind
import com.example.gymtime.domain.analytics.TrainingInsightsDashboard
import com.example.gymtime.domain.analytics.TrainingInsightsPeriod
import com.example.gymtime.ui.components.IronLogCalibrationBar
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Durable selections arrive as values; only temporary disclosure mechanics live here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewTrainingInsightsContent(
    state: TrainingInsightsUiState,
    onWindowChange: (Int) -> Unit,
    onExerciseChange: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleFilterChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onOpenHome: () -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showMethods by rememberSaveable { mutableStateOf(false) }
    var detailExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    val dashboard = state.dashboard
    val exercise = dashboard?.exercises?.firstOrNull { it.id == state.selectedExerciseId }
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val rowIndication = remember {
        MachinedPressIndication(pressedScale = 0.98f, highlightAlpha = 0.06f, cornerRadius = IronLogRadius.Control)
    }
    val actionableObservations = remember(dashboard?.observations) {
        dashboard?.observations
            .orEmpty()
            .filter { it.exerciseId != null || it.muscle != null }
            .take(2)
    }
    val windowOptions = remember { listOf(28 to "4 weeks", 84 to "12 weeks") }
    val selectedWindowIndex = remember(state.windowDays) {
        windowOptions.indexOfFirst { it.first == state.windowDays }.coerceAtLeast(0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = IronLogSpace.ScreenHorizontal,
            end = IronLogSpace.ScreenHorizontal,
            top = 16.dp,
            bottom = IronLogSpace.BottomNavClearance
        ),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        item(key = "insights-header") {
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                            Text(
                                text = "TELEMETRY · LOCAL LOG",
                                style = IronLogType.technicalEyebrow(),
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Training insights",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    TextButton(
                        onClick = { showMethods = true },
                        shape = IronLogRadius.ControlShape,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("How to read this")
                    }
                }
                IronLogSegmentedControl(
                    options = windowOptions.map { it.second },
                    selectedIndex = selectedWindowIndex,
                    onSelect = { index -> onWindowChange(windowOptions[index].first) }
                )
            }
        }
        state.error?.let { message ->
            item(key = "insights-error") {
                IronLogCard(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                    showBorder = true,
                    borderColor = scheme.error.copy(alpha = 0.35f),
                    shape = IronLogRadius.CardShape,
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onRetry,
                        shape = IronLogRadius.ControlShape,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
        if (state.isLoading) {
            item(key = "insights-loading") {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    IronLogCalibrationBar(
                        progress = if (dashboard == null) 0.45f else 0.78f,
                        accentColor = surfaces.accent,
                        tickCount = 12,
                        height = 10.dp
                    )
                    Text(
                        text = if (dashboard == null) "Reading your workout history…" else "Updating your insights…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
        if (dashboard != null && dashboard.period.windowDays != state.windowDays) {
            item(key = "insights-stale-window") {
                Text(
                    text = "Showing the last loaded ${dashboard.period.windowDays / 7}-week period while the requested ${state.windowDays / 7}-week view ${if (state.error != null) "is unavailable. Tap Try again to refresh." else "loads."}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (dashboard != null) {
            item(key = "insights-overview") {
                if (dashboard.cadence.totalCompletedWorkouts == 0) {
                    IronLogCard(
                        highlighted = true,
                        spotlight = true,
                        shape = IronLogRadius.PlateShape,
                        modifier = Modifier.staggeredEntranceOnce("insights", 0)
                    ) {
                        IronLogEmptyState(
                            title = "Your next workout starts the story",
                            body = "Completed workouts with working sets will appear here. Warmups and unfinished sets stay out of the comparisons.",
                            actionLabel = "Go to Home",
                            onAction = onOpenHome
                        )
                        TextButton(
                            onClick = onLibrary,
                            shape = IronLogRadius.ControlShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Explore exercises")
                        }
                    }
                } else {
                    InsightOverview(
                        dashboard = dashboard,
                        modifier = Modifier.staggeredEntranceOnce("insights", 0)
                    )
                }
            }
            if (actionableObservations.isNotEmpty()) {
                item(key = "insights-observations") {
                    IronLogGroupedCard(
                        showBorder = true,
                        shape = IronLogRadius.PlateShape,
                        modifier = Modifier.staggeredEntranceOnce("insights", 1)
                    ) {
                        actionableObservations.forEachIndexed { index, observation ->
                            if (index > 0) {
                                IronLogDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rowIndication,
                                        onClick = {
                                            haptics.tick()
                                            if (observation.exerciseId != null) {
                                                detailExerciseId = observation.exerciseId
                                            } else if (observation.muscle != null) {
                                                detailMuscle = observation.muscle
                                            }
                                        }
                                    )
                                    .padding(IronLogSpace.CardPadding),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(observation.text, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "See the sets behind this",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = surfaces.accent
                                )
                            }
                        }
                    }
                }
            }
            item(key = "insights-lift-progress") {
                LiftProgressPanel(
                    exercise = exercise,
                    onChoose = { showPicker = true },
                    onEvidence = { detailExerciseId = exercise?.id },
                    modifier = Modifier.staggeredEntranceOnce("insights", 2)
                )
            }
            item(key = "insights-body-parts") {
                BodyPartsPanel(
                    muscles = dashboard.muscles,
                    onSelectMuscle = { detailMuscle = it },
                    modifier = Modifier.staggeredEntranceOnce("insights", 3)
                )
            }
            item(key = "insights-rhythm") {
                RhythmPanel(
                    dashboard = dashboard,
                    onHistory = onHistory,
                    modifier = Modifier.staggeredEntranceOnce("insights", 4)
                )
            }
            item(key = "insights-records") {
                RecentRecordsPanel(
                    dashboard = dashboard,
                    onChooseExercise = { showPicker = true },
                    onOpenExercise = { detailExerciseId = it },
                    modifier = Modifier.staggeredEntranceOnce("insights", 5)
                )
            }
        }
    }
    if (showPicker && dashboard != null) {
        InsightExercisePicker(
            exercises = dashboard.exercises,
            muscles = dashboard.muscles.map { it.name },
            selectedExerciseId = state.selectedExerciseId,
            query = state.exerciseQuery,
            muscleFilter = state.muscleFilter,
            onQueryChange = onQueryChange,
            onMuscleChange = onMuscleFilterChange,
            onSelect = { onExerciseChange(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
    dashboard?.exercises?.firstOrNull { it.id == detailExerciseId }?.let { selected ->
        InsightExerciseEvidenceSheet(selected, onDismiss = { detailExerciseId = null })
    }
    dashboard?.muscles?.firstOrNull { it.name == detailMuscle }?.let { muscle ->
        InsightMuscleEvidenceSheet(
            muscle = muscle,
            period = dashboard.period,
            onExercise = { detailMuscle = null; detailExerciseId = it },
            onDismiss = { detailMuscle = null }
        )
    }
    if (showMethods) {
        InsightMethodsSheet(dashboard?.period, onDismiss = { showMethods = false })
    }
}

@Composable
private fun InsightOverview(
    dashboard: TrainingInsightsDashboard,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val cadence = dashboard.cadence
    val prevWorkouts = cadence.previousWorkouts.coerceAtLeast(1)
    val workoutRatio = if (cadence.currentWorkouts == 0 && cadence.previousWorkouts == 0) {
        0f
    } else {
        (cadence.currentWorkouts.toFloat() / prevWorkouts.toFloat()).coerceIn(0f, 1f)
    }

    InsightPanel(modifier = modifier, highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IronLogLedPip(color = surfaces.ledGlow, size = 6.dp, pulsing = cadence.currentWorkouts > 0)
                Text(
                    text = "TRAINING IN VIEW",
                    style = IronLogType.technicalEyebrow(),
                    color = surfaces.accent
                )
            }
            Text(
                text = periodLabel(dashboard.period),
                style = IronLogType.telemetryLabel(),
                color = scheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronLogRecessedWell(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                active = cadence.currentWorkouts > 0,
                accentColor = surfaces.accent,
                shape = IronLogRadius.ControlShape,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "COMPLETED WORKOUTS",
                        style = IronLogType.technicalEyebrow(),
                        color = scheme.onSurfaceVariant
                    )
                    OdometerText(
                        text = cadence.currentWorkouts.toString(),
                        style = IronLogType.monumentalNumeral(MaterialTheme.typography.displaySmall).copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = surfaces.accent
                    )
                    Text(
                        text = "vs ${cadence.previousWorkouts} prior",
                        style = IronLogType.telemetryLabel(),
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                IronLogRecessedWell(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IronLogRadius.ControlShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "WORKING SETS",
                            style = IronLogType.technicalEyebrow(),
                            color = scheme.onSurfaceVariant
                        )
                        OdometerText(
                            text = cadence.currentWorkingSets.toString(),
                            style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onSurface
                        )
                    }
                }
                IronLogRecessedWell(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IronLogRadius.ControlShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "ACTIVE DAYS",
                            style = IronLogType.technicalEyebrow(),
                            color = scheme.onSurfaceVariant
                        )
                        OdometerText(
                            text = cadence.currentActiveDays.toString(),
                            style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PERIOD CADENCE VS PRIOR",
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = "${cadence.currentWorkouts} / ${cadence.previousWorkouts}",
                    style = IronLogType.telemetryLabel(),
                    color = scheme.onSurface
                )
            }
            IronLogCalibrationBar(
                progress = workoutRatio,
                accentColor = surfaces.accent,
                tickCount = 10,
                height = 10.dp
            )
        }

        if (cadence.currentWorkouts == 0) {
            Text(
                text = "A quiet period in your log. Earlier workouts are still in your history.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiftProgressPanel(
    exercise: TrainingInsightExercise?,
    onChoose: () -> Unit,
    onEvidence: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
    var showValues by rememberSaveable(exercise?.id) { mutableStateOf(false) }
    var showMetricHelp by rememberSaveable(exercise?.id) { mutableStateOf(false) }

    InsightPanel(modifier = modifier) {
        InsightSectionTitle(
            title = "Lift progress",
            detail = "Compare an exercise with its own history",
            eyebrow = "STRENGTH TELEMETRY"
        )
        IronLogCard(
            onClick = onChoose,
            highlighted = exercise != null,
            shape = IronLogRadius.ControlShape,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (exercise != null) {
                    IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                }
                Text(
                    text = exercise?.name ?: "Choose an exercise",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = exercise?.let { "${it.muscle} · Tap to change" } ?: "Search or filter by body part",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (exercise != null) {
            val progress = exercise.progress
            progress.latestBestSet?.let { set ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "LATEST BEST WORKING SET · ${insightDate(set.timestampMs).uppercase()}",
                        style = IronLogType.technicalEyebrow(),
                        color = scheme.onSurfaceVariant
                    )
                    OdometerText(
                        text = insightSetLabel(set, exercise.logType),
                        style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineMedium).copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = surfaces.accent
                    )
                }
            }
            if (exercise.totalWorkingSets == 0) {
                Text(
                    text = "No completed working sets for this exercise yet. Choose another lift to explore its history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            progress.metric?.let { metric ->
                Text(metric.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) {
                    val comparableSet = progress.latestComparableSet
                    val comparableValue = progress.latestComparableValue
                    if (comparableSet != null && comparableValue != null) {
                        Text(
                            text = "Latest eligible estimate: ${insightNumber(comparableValue)} ${metric.unit} · ${insightDate(comparableSet.timestampMs)}",
                            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                if (progress.chartPoints.isEmpty()) {
                    Text(
                        text = "No ${if (metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) "eligible estimated-strength" else metric.label.lowercase()} session points in this loaded period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    val chartDots = remember(progress.chartPoints) {
                        progress.chartPoints.map { InsightChartDot(it.timestampMs, it.value) }
                    }
                    InsightLineChart(chartDots, metric.unit)
                    Text(
                        text = "${progress.chartSessionCount} comparable workouts · ${progress.chartPoints.size} ${if (progress.chartPoints.size < progress.chartSessionCount) "sampled " else ""}dots in this period",
                        style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                        color = scheme.onSurfaceVariant
                    )
                }
                ComparisonText(progress.comparison, metric)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    TextButton(
                        onClick = { showMetricHelp = !showMetricHelp },
                        shape = IronLogRadius.ControlShape,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text(if (showMetricHelp) "Hide metric help" else "About metric")
                    }
                    if (progress.chartPoints.isNotEmpty()) {
                        TextButton(
                            onClick = { showValues = !showValues },
                            shape = IronLogRadius.ControlShape,
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text(if (showValues) "Hide values" else "Chart values")
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showMetricHelp,
                    enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                        expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
                    exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                        shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
                ) {
                    Text(
                        text = progress.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = showValues && progress.chartPoints.isNotEmpty(),
                    enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                        expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
                    exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                        shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
                ) {
                    IronLogGroupedCard(showBorder = true) {
                        progress.chartPoints.forEachIndexed { index, point ->
                            if (index > 0) {
                                IronLogDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = IronLogSpace.CardPadding, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${insightDate(point.timestampMs)} · ${insightNumber(point.value)} ${metric.unit}",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = insightSetLabel(point.set, exercise.logType),
                                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = onEvidence,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text("Full history & records")
            }
        }
    }
}

@Composable
private fun BodyPartsPanel(
    muscles: List<TrainingInsightMuscle>,
    onSelectMuscle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val dividerColor = IronLogBorder.dividerColor()
    val rows = remember(muscles) { muscles.chunked(2) }
    val maxSets = remember(muscles) {
        muscles.maxOfOrNull { it.currentWorkingSets }?.coerceAtLeast(1) ?: 1
    }
    val cellIndication = remember {
        MachinedPressIndication(pressedScale = 0.97f, highlightAlpha = 0.06f, cornerRadius = IronLogRadius.Control)
    }

    InsightPanel(modifier = modifier) {
        InsightSectionTitle(
            title = "Body parts",
            detail = "Completed working sets · current vs prior period",
            eyebrow = "MUSCLE DISTRIBUTION"
        )
        IronLogGroupedCard(
            showBorder = true,
            shape = IronLogRadius.ControlShape
        ) {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    IronLogDivider(insetStart = 0.dp, color = dividerColor)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, muscle ->
                        if (colIndex > 0) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = IronLogBorder.Hairline,
                                color = dividerColor
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 96.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = cellIndication,
                                    onClick = {
                                        haptics.tick()
                                        onSelectMuscle(muscle.name)
                                    }
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = muscle.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${muscle.currentWorkingSets} sets · ${muscle.previousWorkingSets} prior",
                                style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
                            )
                            IronLogCalibrationBar(
                                progress = muscle.currentWorkingSets.toFloat() / maxSets.toFloat(),
                                accentColor = surfaces.accent,
                                tickCount = 6,
                                height = 6.dp
                            )
                            Text(
                                text = lastLoggedLabel(muscle.daysSinceLastLogged),
                                style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (row.size == 1) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = IronLogBorder.Hairline,
                            color = dividerColor
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            text = "Tap a body part for its exercises and history. One primary muscle per exercise; this is a log count, not recovery or muscle growth.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentRecordsPanel(
    dashboard: TrainingInsightsDashboard,
    onChooseExercise: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val recordSets = remember(dashboard.recentRecords) {
        dashboard.recentRecords.distinctBy { it.set.id }
    }
    val exerciseLookup = remember(dashboard.exercises) {
        dashboard.exercises.associateBy { it.id }
    }

    InsightPanel(modifier = modifier) {
        InsightSectionTitle(
            title = "Recent records",
            detail = "First observations are benchmarks. A record must improve on earlier completed working sets.",
            eyebrow = "PEAK OUTPUT"
        )
        if (recordSets.isEmpty()) {
            Text(
                text = "No new record events in this period. Your earlier bests are available in each exercise’s history.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            TextButton(
                onClick = onChooseExercise,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text("Choose an exercise")
            }
        } else {
            val displayed = remember(recordSets) { recordSets.take(6) }
            IronLogGroupedCard(
                showBorder = true,
                shape = IronLogRadius.ControlShape
            ) {
                displayed.forEachIndexed { index, event ->
                    if (index > 0) {
                        IronLogDivider()
                    }
                    RecordEventRow(
                        event = event,
                        exercise = exerciseLookup[event.exerciseId],
                        onClick = { onOpenExercise(event.exerciseId) }
                    )
                }
            }
            Text(
                text = "Latest ${displayed.size} sets with record events in this period · ties are not new records.",
                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComparisonText(comparison: TrainingInsightComparison, metric: TrainingInsightMetric) {
    val scheme = MaterialTheme.colorScheme
    if (comparison.status == TrainingInsightProgressStatus.NO_COMPARABLE_DATA && metric.kind != TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) {
        Text(
            text = "Logged ${metric.label.lowercase()} history · no estimated-strength comparison for this metric.",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    val description = when (comparison.status) {
        TrainingInsightProgressStatus.READY -> {
            val change = comparison.percentChange?.let {
                when {
                    comparison.direction == TrainingInsightDirection.STABLE -> "Similar (less than 2%)"
                    it > 0.0 -> "${insightNumber(abs(it))}% higher"
                    it < 0.0 -> "${insightNumber(abs(it))}% lower"
                    else -> "Unchanged"
                }
            } ?: "Not enough data for a percentage"
            "Recent vs earlier: $change. " +
                "${comparison.recentSessionCount} recent sessions vs ${comparison.baselineSessionCount} earlier sessions."
        }
        TrainingInsightProgressStatus.STALE -> "The last comparable session is over 42 days old, so a current comparison is withheld. Your history remains available."
        TrainingInsightProgressStatus.BUILDING_BASELINE -> "Building a comparison · ${comparison.recentSessionCount + comparison.baselineSessionCount} comparable sessions so far. At least 4 are needed."
        TrainingInsightProgressStatus.NO_COMPARABLE_DATA -> "No comparable sets yet for this metric. Your actual logged sets remain in history."
    }
    Text(description, style = IronLogType.tabular(MaterialTheme.typography.bodyMedium))
    if (comparison.status == TrainingInsightProgressStatus.READY) {
        val recent = comparison.recentMedian
        val earlier = comparison.baselineMedian
        if (recent != null && earlier != null) {
            Text(
                text = "Median session best: ${insightNumber(recent)} ${metric.unit} recent · ${insightNumber(earlier)} ${metric.unit} earlier",
                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RhythmPanel(
    dashboard: TrainingInsightsDashboard,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
    var showWeeks by rememberSaveable { mutableStateOf(false) }
    val cadence = dashboard.cadence
    InsightPanel(modifier = modifier) {
        InsightSectionTitle(
            title = "Training rhythm",
            detail = "Completed workouts, counted once each",
            eyebrow = "WEEKLY CADENCE"
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OdometerText(
                text = cadence.thisWeekWorkouts.toString(),
                style = IronLogType.monumentalNumeral(MaterialTheme.typography.displaySmall).copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = surfaces.accent
            )
            Text(
                text = "this week so far",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = "${cadence.currentWorkouts} in this period · ${cadence.previousWorkouts} in the previous ${dashboard.period.windowDays} days",
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
        )
        Text(
            text = "${cadence.currentActiveDays} days with a completed workout · ${cadence.previousActiveDays} in the previous period",
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = scheme.onSurfaceVariant
        )
        val weekCounts = remember(cadence.weeks) { cadence.weeks.map { it.workouts } }
        InsightWeeklyBars(weekCounts)
        val rangeLabel = remember(cadence.weeks) {
            val first = cadence.weeks.firstOrNull()?.let {
                "${shortDate(it.startDate)}–${shortDate(it.endDate)}${if (it.isPartialWeek) " · partial" else ""}"
            }.orEmpty()
            val last = cadence.weeks.lastOrNull()?.let {
                "${shortDate(it.startDate)}–${shortDate(it.endDate)} · so far"
            }.orEmpty()
            "$first → $last"
        }
        Text(
            text = rangeLabel,
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = scheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            TextButton(
                onClick = { showWeeks = !showWeeks },
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text(if (showWeeks) "Hide weekly counts" else "Exact weekly counts")
            }
            TextButton(
                onClick = onHistory,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text("Open history")
            }
        }
        AnimatedVisibility(
            visible = showWeeks && cadence.weeks.isNotEmpty(),
            enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
            exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
        ) {
            IronLogGroupedCard(showBorder = true) {
                cadence.weeks.forEachIndexed { index, week ->
                    if (index > 0) {
                        IronLogDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = IronLogSpace.CardPadding, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${shortDate(week.startDate)}–${shortDate(week.endDate)}${
                                when {
                                    week.isCurrentWeek -> " · so far"
                                    week.isPartialWeek -> " · partial"
                                    else -> ""
                                }
                            }",
                            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            text = "${week.workouts} workouts",
                            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordEventRow(
    event: TrainingInsightRecordEvent,
    exercise: TrainingInsightExercise?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val rowIndication = remember {
        MachinedPressIndication(pressedScale = 0.98f, highlightAlpha = 0.06f, cornerRadius = IronLogRadius.Control)
    }
    val isPr = event.kind == TrainingInsightRecordKind.PERSONAL_RECORD
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rowIndication,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .padding(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.exerciseName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IronLogStatusChip(
                label = if (isPr) "PR" else "Benchmark",
                tone = if (isPr) IronLogStatusTone.PR else IronLogStatusTone.ACCENT
            )
        }
        Text(
            text = "${event.metric.label}: ${insightNumber(event.value)} ${event.metric.unit}",
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
        )
        val detail = listOfNotNull(
            exercise?.let { insightSetLabel(event.set, it.logType) },
            insightDate(event.set.timestampMs)
        ).joinToString(" · ")
        Text(
            text = detail,
            style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
            color = scheme.onSurfaceVariant
        )
    }
}

private val shortDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

internal fun periodLabel(period: TrainingInsightsPeriod): String =
    "${shortDate(period.currentStartDate)}–${shortDate(period.currentEndDate)} · rolling ${period.windowDays} days"

internal fun shortDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    val formatter = shortDateFormatters.computeIfAbsent(locale) { loc ->
        DateTimeFormatter.ofPattern("MMM d", loc)
    }
    return date.format(formatter)
}

internal fun lastLoggedLabel(days: Long?): String = when (days) {
    null -> "No working sets logged"
    0L -> "Last logged today"
    1L -> "Last logged yesterday"
    else -> "Last logged $days days ago"
}
