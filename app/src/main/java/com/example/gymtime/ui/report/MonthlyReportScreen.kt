package com.example.gymtime.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.domain.report.MonthlyPR
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MuscleTotal
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.ui.components.BackNavigationIcon
import com.example.gymtime.ui.components.HomeNavigationAction
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.theme.LocalLoggerPreviewThemeActive
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(navController: NavController) {
    val viewModel: MonthlyReportViewModel = hiltViewModel()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val narrative by viewModel.narrative.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

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
                        color = LocalAppColors.current.textPrimary,
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
        containerColor = Color.Transparent
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = accent) }

            report == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No data yet.",
                    color = LocalAppColors.current.textTertiary
                )
            }

            else -> ReportBody(
                report = report!!,
                narrative = narrative,
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
    padding: PaddingValues,
    accent: Color
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val previewActive = LocalLoggerPreviewThemeActive.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (previewActive) 12.dp else 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = report.monthLabel,
                color = LocalAppColors.current.textPrimary,
                fontSize = if (previewActive) 26.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Here's how last month went",
                color = LocalAppColors.current.textTertiary,
                fontSize = 14.sp
            )
        }

        item { OnDeviceAiDownloadCard() }

        narrative?.takeIf { it.isNotBlank() }?.let { recap ->
            item {
                ReportCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MONTH IN REVIEW",
                            color = accent,
                            fontSize = if (previewActive) 12.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp
                        )
                        Text(
                            text = recap,
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }

        item {
            HeroStats(report = report, accent = accent, numberFormat = numberFormat)
        }

        if (report.topMuscles.isNotEmpty()) {
            item { SectionTitle("Top muscles trained") }
            item { MuscleList(report.topMuscles, accent) }
        }

        if (report.undertrainedMuscles.isNotEmpty()) {
            item { SectionTitle("Could use more attention") }
            item {
                ReportCard {
                    Text(
                        text = report.undertrainedMuscles.joinToString(" • "),
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (report.newPRs.isNotEmpty()) {
            item { SectionTitle("New personal records") }
            item { PRList(report.newPRs, accent) }
        }

        report.averageRating?.let { rating ->
            item { SectionTitle("Average rating") }
            item {
                ReportCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f", rating),
                            color = accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "out of 5 flames",
                            color = LocalAppColors.current.textTertiary,
                            fontSize = 14.sp
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
    val previewActive = LocalLoggerPreviewThemeActive.current
    ReportCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val statModifier = if (previewActive) Modifier.weight(1f) else Modifier
                StatBlock("WORKOUTS", report.workoutCount.toString(), statModifier)
                StatBlock("SETS", report.totalWorkingSets.toString(), statModifier)
                StatBlock("EXERCISES", report.exerciseCount.toString(), statModifier)
            }
            Spacer(modifier = Modifier.height(if (previewActive) 12.dp else 20.dp))
            HorizontalDivider(color = if (previewActive) MaterialTheme.colorScheme.outlineVariant else LocalAppColors.current.textTertiary.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(if (previewActive) 12.dp else 20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VOLUME",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = if (previewActive) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${numberFormat.format(report.totalVolume.toLong())} lbs",
                    color = accent,
                    fontSize = if (previewActive) 28.sp else 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = LocalAppColors.current.textTertiary,
            fontSize = if (previewActive) 12.sp else 10.sp,
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
    }
}

@Composable
private fun MuscleList(items: List<MuscleTotal>, accent: Color) {
    ReportCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.muscle, color = LocalAppColors.current.textPrimary, fontSize = 15.sp)
                    Text(
                        text = "${m.setCount} sets",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PRList(items: List<MonthlyPR>, accent: Color) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    ReportCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { pr ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pr.exerciseName,
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${formatWeight(pr.weight)} lbs × ${pr.reps}",
                            color = LocalAppColors.current.textTertiary,
                            fontSize = 12.sp
                        )
                    }
                    if (previewActive) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text(text = "🏆", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

private fun formatWeight(weight: Float): String =
    if (weight % 1f == 0f) weight.toInt().toString() else weight.toString()

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalAppColors.current.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ReportCard(content: @Composable () -> Unit) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
        shape = RoundedCornerShape(if (previewActive) 22.dp else 16.dp),
        border = if (previewActive) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (previewActive) 1.dp else 0.dp)
    ) {
        Box(modifier = Modifier.padding(if (previewActive) 14.dp else 20.dp)) { content() }
    }
}
