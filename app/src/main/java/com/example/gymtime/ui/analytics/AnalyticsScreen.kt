package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ui.theme.GradientEnd
import com.example.gymtime.ui.theme.GradientStart

/**
 * Analytics Screen (v1.0 MVP - Phase 1 Placeholder)
 *
 * This is a placeholder for Phase 1 implementation.
 * Full UI with hero cards, charts, and PR list will be added in Phase 2-3.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    Box(
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Phase 1 Complete ✓",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFA3E635) // Lime green
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Foundation ready: ViewModel, DAO queries, and navigation",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9CA3AF) // Gray
            )
        }
    }
}
