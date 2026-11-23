package com.example.gymtime.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
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
    val accent = scheme.primary

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
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = IronLogSpace.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        item {
            Spacer(modifier = Modifier.height(IronLogSpace.CompactGap))
            Text(
                text = report.monthLabel,
                color = scheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Here's how last month went",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (error != null) {
            item {
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

        item { OnDeviceAiDownloadCard() }

        narrative?.takeIf { it.isNotBlank() }?.let { recap ->
            item {
                IronLogCard(
                    highlighted = true,
                    showBorder = true,
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    borderColor = IronLogBorder.borderColor(scheme, highlighted = true),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    Text(
                        text = "MONTH IN REVIEW",
                        color = scheme.onPrimaryContainer,
                        style = IronLogType.sectionEyebrow()
                    )
                    Text(
                        text = recap,
                        color = scheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        item {
            HeroStats(report = report, accent = accent, numberFormat = numberFormat)
        }

        if (report.topMuscles.isNotEmpty()) {
            item { IronLogSectionHeader(title = "Top muscles trained") }
            item { MuscleList(report.topMuscles, accent) }
        }

        if (report.undertrainedMuscles.isNotEmpty()) {
            item { IronLogSectionHeader(title = "Could use more attention") }
            item {
                IronLogCard(showBorder = true) {
                    Text(
                        text = report.undertrainedMuscles.joinToString(" · "),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (report.newPRs.isNotEmpty()) {
            item { IronLogSectionHeader(title = "New personal records") }
            item { PRList(report.newPRs) }
        }

        report.averageRating?.let { rating ->
            item { IronLogSectionHeader(title = "Average rating") }
            item {
                IronLogCard(showBorder = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f", rating),
                            color = accent,
                            style = IronLogType.tabular(MaterialTheme.typography.headlineMedium).copy(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        Spacer(modifier = Modifier.width(IronLogSpace.CompactGap))
                        Text(
                            text = "out of 5 flames",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroStats(report: MonthlyReport, accent: Color, numberFormat: NumberFormat) {
    val scheme = MaterialTheme.colorScheme
    IronLogCard(
        showBorder = true,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        IronLogMetricRow(
            metrics = listOf(
                IronLogMetric(label = "Workouts", value = report.workoutCount.toString()),
                IronLogMetric(label = "Sets", value = report.totalWorkingSets.toString()),
                IronLogMetric(label = "Exercises", value = report.exerciseCount.toString())
            )
        )
        IronLogDivider(insetStart = 0.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "VOLUME",
                color = scheme.onSurfaceVariant,
                style = IronLogType.sectionEyebrow()
            )
            Text(
                text = "${numberFormat.format(report.totalVolume.toLong())} lbs",
                color = accent,
                style = IronLogType.tabular(MaterialTheme.typography.headlineLarge).copy(
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}

@Composable
private fun MuscleList(items: List<MuscleTotal>, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    IronLogGroupedCard(showBorder = true) {
        items.forEachIndexed { index, m ->
            if (index > 0) {
                IronLogDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.RowVertical),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = m.muscle,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${m.setCount} sets",
                    color = accent,
                    style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun PRList(items: List<MonthlyPR>) {
    val scheme = MaterialTheme.colorScheme
    IronLogGroupedCard(showBorder = true) {
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
