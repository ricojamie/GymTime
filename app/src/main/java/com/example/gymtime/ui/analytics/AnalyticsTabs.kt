package com.example.gymtime.ui.analytics

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.domain.analytics.*
import com.example.gymtime.ui.theme.*
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConsistencyTabContent(
    heatMapData: List<HeatMapDay>,
    stats: com.example.gymtime.domain.analytics.ConsistencyStats?,
    trophyCasePRs: List<TrophyPR>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        // Heatmap
        ActivityHeatmap(data = heatMapData)

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Card
        if (stats != null) {
            ConsistencyStatsCard(stats = stats)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Trophy Case Section
        Text(
            text = "PR Trophy Case",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Text(
            text = "Star up to 3 exercises to track them here",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (trophyCasePRs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(trophyCasePRs) { pr ->
                    TrophyCard(pr = pr)
                }
            }
        } else {
            Text(
                "Go to Library > Exercises and star your main lifts!",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

@Composable
fun BalanceTabContent(
    distributionData: List<com.example.gymtime.data.db.entity.MuscleDistribution>,
    freshnessData: List<MuscleFreshnessStatus>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Training Split (30 Days)",
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        MuscleDistributionChart(data = distributionData)

        Spacer(modifier = Modifier.height(24.dp))

        MuscleFreshnessList(data = freshnessData)
    }
}

@Composable
fun TrendsTabContent(
    viewModel: AnalyticsViewModel
) {
    val trendData by viewModel.trendData.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedInterval by viewModel.selectedInterval.collectAsState()
    val selectedMuscle by viewModel.selectedMuscleFilter.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseFilterId.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Filters Section
        TrendsFilters(
            selectedMetric = selectedMetric,
            selectedPeriod = selectedPeriod,
            selectedInterval = selectedInterval,
            selectedMuscle = selectedMuscle,
            selectedExerciseId = selectedExerciseId,
            allExercises = allExercises,
            onMetricChange = viewModel::updateMetric,
            onPeriodChange = viewModel::updatePeriod,
            onIntervalChange = viewModel::updateInterval,
            onMuscleChange = viewModel::updateMuscleFilter,
            onExerciseChange = viewModel::updateExerciseFilter
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Chart Section
        if (trendData.isNotEmpty()) {
            TrendLineChart(data = trendData, metric = selectedMetric)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(SurfaceCards, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for current filters", color = TextTertiary)
            }
        }

    }
}

@Composable
private fun TrendsFilters(
    selectedMetric: TrendMetric,
    selectedPeriod: TimePeriod,
    selectedInterval: AggregateInterval,
    selectedMuscle: String?,
    selectedExerciseId: Long?,
    allExercises: List<com.example.gymtime.data.db.entity.Exercise>,
    onMetricChange: (TrendMetric) -> Unit,
    onPeriodChange: (TimePeriod) -> Unit,
    onIntervalChange: (AggregateInterval) -> Unit,
    onMuscleChange: (String?) -> Unit,
    onExerciseChange: (Long?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Metric & Period
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                label = "Metric",
                current = selectedMetric.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                options = TrendMetric.values().map { it.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } },
                onSelect = { onMetricChange(TrendMetric.valueOf(it.replace(" ", "_").uppercase())) },
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = "Period",
                current = selectedPeriod.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                options = TimePeriod.values().map { it.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } },
                onSelect = { onPeriodChange(TimePeriod.valueOf(it.replace(" ", "_").uppercase())) },
                modifier = Modifier.weight(1f)
            )
        }

        // Interval & Filter (Muscle/Exercise)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                label = "Interval",
                current = selectedInterval.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                options = AggregateInterval.values().map { it.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } },
                onSelect = { onIntervalChange(AggregateInterval.valueOf(it.replace(" ", "_").uppercase())) },
                modifier = Modifier.weight(1f)
            )

            val filterLabel = if (selectedExerciseId != null) {
                allExercises.find { it.id == selectedExerciseId }?.name ?: "Exercise"
            } else if (selectedMuscle != null && selectedMuscle != "All") {
                selectedMuscle
            } else "All Exercises"

            FilterDropdown(
                label = "Filter",
                current = filterLabel,
                options = listOf("All") +
                          listOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core") +
                          allExercises.map { it.name },
                onSelect = { selected ->
                    when {
                        selected == "All" -> onMuscleChange("All")
                        selected in listOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core") -> onMuscleChange(selected)
                        else -> {
                            val exercise = allExercises.find { it.name == selected }
                            onExerciseChange(exercise?.id)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(start = 4.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCards, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceCards)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = TextPrimary) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrophyCard(pr: TrophyPR) {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pr.exercise.name,
                style = MaterialTheme.typography.labelLarge,
                color = TextTertiary,
                maxLines = 1
            )

            Column {
                Text(
                    text = "${pr.weight.toInt()} lbs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "for ${pr.reps} reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Text(
                text = sdf.format(pr.date),
                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                color = TextTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun TrendLineChart(
    data: List<TrendPoint>,
    metric: TrendMetric
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Update the chart data when data changes
    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(data.map { it.value.toDouble() })
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(SurfaceCards, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (data.isNotEmpty()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize(),
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(zoomEnabled = true),
            )
        }
    }
}
