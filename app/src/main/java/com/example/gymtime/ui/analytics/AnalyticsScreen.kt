package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ui.theme.LocalAppColors

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    // State
    val heatMapData by viewModel.heatMapData.collectAsState()
    val muscleDistribution by viewModel.muscleDistribution.collectAsState()
    val radarDistribution by viewModel.radarDistribution.collectAsState()
    val muscleFreshness by viewModel.muscleFreshness.collectAsState()
    val consistencyStats by viewModel.consistencyStats.collectAsState()
    val trophyCasePRs by viewModel.trophyCasePRs.collectAsState()
    val workoutRatingStats by viewModel.workoutRatingStats.collectAsState()
    val selectedBalanceRange by viewModel.selectedBalanceRange.collectAsState()
    
    // Tab State
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Consistency", "Balance", "Trends")

    val gradientColors = com.example.gymtime.ui.theme.LocalGradientColors.current
    val appColors = LocalAppColors.current

    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
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
