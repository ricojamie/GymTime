package com.example.gymtime.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.domain.analytics.HeatMapDay
import com.example.gymtime.domain.analytics.MuscleFreshnessStatus

@Composable
fun ConsistencyTabContent(
    heatMapData: List<HeatMapDay>,
    stats: com.example.gymtime.domain.analytics.ConsistencyStats?
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
    }
}

@Composable
fun BalanceTabContent(
    distributionData: List<MuscleDistribution>,
    freshnessData: List<MuscleFreshnessStatus>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Training Split (30 Days)",
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MuscleDistributionChart(data = distributionData)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        MuscleFreshnessList(data = freshnessData)
    }
}

@Composable
fun TrendsTabContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Detailed Trends Coming in Phase 3", color = Color.Gray)
    }
}
