package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.domain.analytics.ConsistencyStats
import com.example.gymtime.util.StreakCalculator
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ConsistencyStatsCard(
    stats: ConsistencyStats
) {
    var showConsistencyTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161616))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Streak Section (Iron Streak) ---
        StreakSection(stats)

        // --- 2. Stats Grid (Best, YTD) ---
        StatsGridSection(stats)

        // --- 3. Consistency Score (with Tooltip) ---
        ConsistencyScoreSection(
            score = stats.consistencyScore,
            onInfoClick = { showConsistencyTooltip = true }
        )
    }

    if (showConsistencyTooltip) {
        AlertDialog(
            onDismissRequest = { showConsistencyTooltip = false },
            title = { Text("Consistency Score") },
            text = {
                Text("This score represents the percentage of weeks in the last year where you logged at least one workout.\n\nA score of 100% means you never missed a week!")
            },
            confirmButton = {
                TextButton(onClick = { showConsistencyTooltip = false }) {
                    Text("Got it")
                }
            },
            containerColor = Color(0xFF222222),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}

@Composable
private fun StreakSection(stats: ConsistencyStats) {
    val result = stats.streakResult
    val accentColor = MaterialTheme.colorScheme.primary
    
    val (stateIcon, stateColor) = when (result.state) {
        StreakCalculator.StreakState.ACTIVE -> Pair("\uD83D\uDD25", accentColor) // Fire
        StreakCalculator.StreakState.RESTING -> Pair("\u2744\uFE0F", Color(0xFF64B5F6)) // Snowflake
        StreakCalculator.StreakState.BROKEN -> Pair("\uD83D\uDC80", Color(0xFFEF5350)) // Skull
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "IRON STREAK",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stateIcon,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "${result.streakDays}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = stateColor,
                    lineHeight = 32.sp
                )
                Text(
                    text = " DAYS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )
            }
        }

        // Skips Dots
        Column(horizontalAlignment = Alignment.End) {
             Text(
                text = "SKIPS LEFT",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { index ->
                    val isLit = index < result.skipsRemaining
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                brush = if (isLit) Brush.radialGradient(
                                    colors = listOf(Color(0xFF64B5F6), Color.Transparent)
                                ) else Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray)),
                                shape = CircleShape
                            )
                            .alpha(if (isLit) 1f else 0.3f)
                            .then(
                                if (isLit) Modifier.background(Color(0xFF64B5F6), CircleShape) else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGridSection(stats: ConsistencyStats) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatGridItem(
            label = "BEST",
            value = "${stats.bestStreak} Days"
        )
        StatGridItem(
            label = "2025 LOGS",
            value = "${stats.ytdWorkouts}"
        )
        StatGridItem(
            label = "2025 VOL",
            value = "${compactVolumeFormat(stats.ytdVolume)}"
        )
    }
}

@Composable
private fun StatGridItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ConsistencyScoreSection(score: Int, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInfoClick() }
            .padding(vertical = 4.dp), // Add hit target area
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Consistency Score",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Text(
            text = "$score%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (score >= 80) MaterialTheme.colorScheme.primary else Color.White
        )
    }
}

private fun compactVolumeFormat(volume: Float): String {
    return when {
        volume >= 1_000_000 -> String.format(Locale.US, "%.1fm", volume / 1_000_000)
        volume >= 1_000 -> String.format(Locale.US, "%.1fk", volume / 1_000)
        else -> volume.toInt().toString()
    }
}
