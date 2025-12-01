package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ui.theme.GradientEnd
import com.example.gymtime.ui.theme.GradientStart

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val availableTargets by viewModel.availableTargets.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val currentValue by viewModel.currentValue.collectAsState()
    val maxValue by viewModel.maxValue.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientStart,
                        GradientEnd
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metric Selector (Volume / 1RM)
        MetricSelector(
            selected = selectedMetric,
            onSelect = { viewModel.setMetric(it) }
        )

        // Target Selector (Muscle / Exercise)
        TargetSelector(
            selected = selectedTarget,
            options = availableTargets,
            onSelect = { viewModel.setTarget(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // Time range selector
        TimeRangeSelector(
            selectedRange = selectedTimeRange,
            onRangeSelected = { viewModel.setTimeRange(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Main Chart
        MainLineChart(data = chartData)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Stats Summary
        StatsSummaryRow(current = currentValue, max = maxValue)

        Spacer(modifier = Modifier.height(32.dp))
    }
}
