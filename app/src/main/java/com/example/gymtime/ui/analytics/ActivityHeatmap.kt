package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.domain.analytics.HeatMapDay
import kotlinx.coroutines.delay

@Composable
fun ActivityHeatmap(
    data: List<HeatMapDay>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedDay by remember { mutableStateOf<HeatMapDay?>(null) }
    
    // Auto-scroll to the end (today) when data loads
    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            // Short delay to allow layout to calculate
            delay(100) 
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161616) // Slightly lighter than background
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Legend or status
                Text(
                    text = "Last 365 Days",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Heatmap Grid
            // We need to render columns of 7 days.
            // data is a flat list of 364/365 days.
            // We'll chunk it into weeks.
            
            if (data.isEmpty()) {
                // Loading or Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No activity data", color = Color.Gray)
                }
            } else {
                val weeks = data.chunked(7)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(bottom = 8.dp), // Padding for scrollbar if any
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weeks.forEach { week ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { day ->
                                HeatMapSquare(
                                    day = day,
                                    isSelected = selectedDay == day,
                                    onClick = { selectedDay = day }
                                )
                            }
                        }
                    }
                }
                
                // Interaction Details
                selectedDay?.let { day ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = day.formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = if (day.volume > 0) String.format(java.util.Locale.US, "%,.0f lbs", day.volume) else "Rest Day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = if (day.volume > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                } ?: run {
                    // Placeholder to keep height stable
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap a square for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun HeatMapSquare(
    day: HeatMapDay,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val baseColor = MaterialTheme.colorScheme.primary
    
    val color = when (day.level) {
        0 -> Color(0xFF1E1E1E) // Empty
        1 -> baseColor.copy(alpha = 0.3f)
        2 -> baseColor.copy(alpha = 0.6f)
        3 -> baseColor.copy(alpha = 1.0f)
        else -> Color(0xFF1E1E1E)
    }
    
    // Highlight selected day
    val borderColor = if (isSelected) Color.White else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.background(Color.White.copy(alpha = 0.2f)) else Modifier
            )
            // Note: Simple border for selection might be better or handled via alpha overlay
            // Detailed border implementation ommitted for simplicity/performance in grid, 
            // using alpha overlay logic instead roughly. 
            // Actually, let's keep it simple.
    )
}
