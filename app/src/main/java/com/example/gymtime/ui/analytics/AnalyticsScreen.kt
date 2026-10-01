package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.analytics.preview.TrainingInsightsViewModel
import com.example.gymtime.ui.analytics.preview.PreviewTrainingInsightsScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun AnalyticsScreen(
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onOpenHome: () -> Unit,
    previewViewModel: TrainingInsightsViewModel = hiltViewModel()
) {
    val newUiEnabled by previewViewModel.newUiEnabled.collectAsStateWithLifecycle()
    when (newUiEnabled) {
        true -> PreviewTrainingInsightsScreen(previewViewModel, onHistory, onLibrary, onOpenHome)
        false -> LegacyAnalyticsScreen()
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun LegacyAnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    // State
    val heatMapData by viewModel.heatMapData.collectAsStateWithLifecycle()
    val muscleDistribution by viewModel.muscleDistribution.collectAsStateWithLifecycle()
    val radarDistribution by viewModel.radarDistribution.collectAsStateWithLifecycle()
    val muscleFreshness by viewModel.muscleFreshness.collectAsStateWithLifecycle()
    val consistencyStats by viewModel.consistencyStats.collectAsStateWithLifecycle()
    val trophyCasePRs by viewModel.trophyCasePRs.collectAsStateWithLifecycle()
    val workoutRatingStats by viewModel.workoutRatingStats.collectAsStateWithLifecycle()
    val selectedBalanceRange by viewModel.selectedBalanceRange.collectAsStateWithLifecycle()
    val weeklyInsightText by viewModel.weeklyInsightText.collectAsStateWithLifecycle()
    
    // Tab State
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Consistency", "Balance", "Trends")

    val gradientColors = com.example.gymtime.ui.theme.LocalGradientColors.current
    val appColors = LocalAppColors.current

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDataIfDateChanged()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        gradientColors.first,
                        gradientColors.second
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))
        OnDeviceAiDownloadCard()

        weeklyInsightText?.let { insight ->
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyAnalyticsInsightCard(insight)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Tabs
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = appColors.textTertiary.copy(alpha = 0.35f)) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text(
                            text = title, 
                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else appColors.textTertiary,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        ) 
                    }
                )
            }
        }
        
        // Content
        Column(
             modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
             when (selectedTabIndex) {
                0 -> ConsistencyTabContent(heatMapData, consistencyStats, trophyCasePRs, workoutRatingStats)
                1 -> BalanceTabContent(
                    distributionData = muscleDistribution,
                    radarData = radarDistribution,
                    freshnessData = muscleFreshness,
                    selectedRange = selectedBalanceRange,
                    onRangeChange = viewModel::updateBalanceRange
                )
                2 -> TrendsTabContent(viewModel)
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun WeeklyAnalyticsInsightCard(text: String) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceCards)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "This week",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textSecondary
                )
            }
        }
    }
}
