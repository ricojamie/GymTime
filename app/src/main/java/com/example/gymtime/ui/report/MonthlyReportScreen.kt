package com.example.gymtime.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.domain.report.MonthlyPR
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MuscleTotal
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import com.example.gymtime.ui.components.BackNavigationIcon
import com.example.gymtime.ui.components.HomeNavigationAction
import com.example.gymtime.ui.components.IronLogCalibrationBar
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(navController: NavController) {
    val viewModel: MonthlyReportViewModel = hiltViewModel()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val narrative by viewModel.narrative.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val accent = surfaces.accent

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshIfDateChanged()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Monthly Report",
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    BackNavigationIcon(navController::navigateBackOrHome)
                },
                actions = {
                    HomeNavigationAction(navController::navigateHomeAndClearStack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when {
            isLoading && report == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = accent) }

            report == null && error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                IronLogEmptyState(
                    title = "Couldn't load monthly report",
                    body = error ?: "Please try again.",
                    actionLabel = "Retry",
                    onAction = viewModel::retry
                )
            }

            report == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                IronLogEmptyState(
                    title = "No data yet",
                    body = "Completed workouts from the previous calendar month will appear here."
                )
            }

            else -> ReportBody(
                report = report!!,
                narrative = narrative,
                error = error,
                isLoading = isLoading,
                onRetry = viewModel::retry,
                padding = padding,
                accent = accent
            )
        }
    }
}

@Composable
private fun ReportBody(
    report: MonthlyReport,
    narrative: String?,
    error: String?,
    isLoading: Boolean,
    onRetry: () -> Unit,
    padding: PaddingValues,
    accent: Color
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .navigationBarsPadding()
            .padding(horizontal = IronLogSpace.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        item(key = "report-header") {
            Column(
                modifier = Modifier.staggeredEntranceOnce("monthly-report", 0),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Spacer(modifier = Modifier.height(IronLogSpace.CompactGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                            Text(
                                text = "MONTHLY TELEMETRY",
                                style = IronLogType.technicalEyebrow(),
                                color = accent
                            )
                        }
                        Text(
                            text = report.monthLabel,
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IronLogMascotBadge(
                        size = 36.dp,
                        style = IronLogMascotStyle.MEDALLION,
                        showGlow = true
                    )
                }
                Text(
                    text = "Here's how last month went",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (error != null) {
            item(key = "report-error") {
                IronLogCard(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                    showBorder = true,
                    borderColor = scheme.error.copy(alpha = 0.35f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onRetry,
                        enabled = !isLoading,
                        shape = IronLogRadius.ControlShape,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        item(key = "report-ai") { OnDeviceAiDownloadCard() }

        narrative?.takeIf { it.isNotBlank() }?.let { recap ->
            item(key = "report-narrative") {
                IronLogCard(
                    highlighted = true,
                    spotlight = true,
                    showBorder = true,
                    shape = IronLogRadius.PlateShape,
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    modifier = Modifier.staggeredEntranceOnce("monthly-report", 1)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                        Text(
                            text = "MONTH IN REVIEW",
                            color = accent,
                            style = IronLogType.technicalEyebrow()
                        )
                    }
                    Text(
                        text = recap,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        item(key = "report-hero") {
            HeroStats(
                report = report,
                accent = accent,
                numberFormat = numberFormat,
                modifier = Modifier.staggeredEntranceOnce("monthly-report", 2)
            )
        }

        if (report.topMuscles.isNotEmpty()) {
            item(key = "report-top-muscles-header") {
                IronLogSectionHeader(title = "Top muscles trained")
            }
            item(key = "report-top-muscles") {
                MuscleList(
                    items = report.topMuscles,
                    accent = accent,
                    modifier = Modifier.staggeredEntranceOnce("monthly-report", 3)
                )
            }
        }

        if (report.undertrainedMuscles.isNotEmpty()) {
            item(key = "report-undertrained-header") {
                IronLogSectionHeader(title = "Could use more attention")
            }
            item(key = "report-undertrained") {
                IronLogCard(
                    showBorder = true,
                    shape = IronLogRadius.PlateShape,
                    modifier = Modifier.staggeredEntranceOnce("monthly-report", 4)
                ) {
                    Text(
                        text = report.undertrainedMuscles.joinToString(" · "),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (report.newPRs.isNotEmpty()) {
            item(key = "report-prs-header") {
                IronLogSectionHeader(title = "New personal records")
            }
            item(key = "report-prs") {
                PRList(
                    items = report.newPRs,
                    modifier = Modifier.staggeredEntranceOnce("monthly-report", 5)
                )
            }
        }

        report.averageRating?.let { rating ->
            item(key = "report-rating-header") {
                IronLogSectionHeader(title = "Average rating")
            }
            item(key = "report-rating") {
                IronLogCard(
                    showBorder = true,
                    shape = IronLogRadius.PlateShape,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.staggeredEntranceOnce("monthly-report", 6)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OdometerText(
                                text = String.format(Locale.getDefault(), "%.1f", rating),
                                color = accent,
                                style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineMedium).copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Text(
                                text = "out of 5 flames",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    IronLogCalibrationBar(
                        progress = (rating / 5f).coerceIn(0f, 1f),
                        accentColor = accent,
                        tickCount = 5,
                        height = 10.dp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroStats(
    report: MonthlyReport,
    accent: Color,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    IronLogCard(
        modifier = modifier,
        highlighted = true,
        spotlight = true,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        IronLogRecessedWell(
            active = true,
            accentColor = accent,
            shape = IronLogRadius.ControlShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "TOTAL VOLUME",
                    color = scheme.onSurfaceVariant,
                    style = IronLogType.technicalEyebrow()
                )
                OdometerText(
                    text = "${numberFormat.format(report.totalVolume.toLong())} lbs",
                    color = accent,
                    style = IronLogType.monumentalNumeral(MaterialTheme.typography.displaySmall).copy(
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            ReportMetricWell(
                label = "WORKOUTS",
                value = report.workoutCount.toString(),
                accent = accent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            ReportMetricWell(
                label = "SETS",
                value = report.totalWorkingSets.toString(),
                accent = accent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            ReportMetricWell(
                label = "EXERCISES",
                value = report.exerciseCount.toString(),
                accent = accent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ReportMetricWell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    IronLogRecessedWell(
        modifier = modifier,
        shape = IronLogRadius.ControlShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = IronLogType.technicalEyebrow(),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OdometerText(
                text = value,
                style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                    fontWeight = FontWeight.Bold
                ),
                color = scheme.onSurface
            )
        }
    }
}

@Composable
private fun MuscleList(
    items: List<MuscleTotal>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val maxSets = remember(items) {
        items.maxOfOrNull { it.setCount }?.coerceAtLeast(1) ?: 1
    }
    IronLogGroupedCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape
    ) {
        items.forEachIndexed { index, m ->
            if (index > 0) {
                IronLogDivider()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.RowVertical),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = m.muscle,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OdometerText(
                        text = "${m.setCount} sets",
                        color = accent,
                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                IronLogCalibrationBar(
                    progress = m.setCount.toFloat() / maxSets.toFloat(),
                    accentColor = accent,
                    tickCount = 8,
                    height = 8.dp
                )
            }
        }
    }
}

@Composable
private fun PRList(
    items: List<MonthlyPR>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    IronLogGroupedCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape
    ) {
        items.forEachIndexed { index, pr ->
            if (index > 0) {
                IronLogDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.RowVertical),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = pr.exerciseName,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${formatWeight(pr.weight)} lbs × ${pr.reps}",
                        color = scheme.onSurfaceVariant,
                        style = IronLogType.tabular(MaterialTheme.typography.bodySmall)
                    )
                }
                IronLogStatusChip(
                    label = "PR",
                    tone = IronLogStatusTone.PR,
                    icon = Icons.Default.EmojiEvents
                )
            }
        }
    }
}

private fun formatWeight(weight: Float): String =
    if (weight % 1f == 0f) weight.toInt().toString() else weight.toString()
