package com.example.gymtime.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.PrimaryAccentDark
import kotlinx.coroutines.delay

@Composable
fun StatsRow(streak: Int, poundsLifted: Int, pbs: Int) {
    var currentIndex by remember { mutableStateOf(0) }
    val stats = listOf(
        StatItem("$streak day streak! 🔥", "Keep it going!"),
        StatItem("$poundsLifted lbs", "Lifted this week"),
        StatItem("$pbs PBs", "This week!")
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000) // Change every 3 seconds
            currentIndex = (currentIndex + 1) % stats.size
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            PrimaryAccent,
                            PrimaryAccentDark
                        )
                    )
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        (slideInHorizontally(
                            initialOffsetX = { 1000 },
                            animationSpec = tween(500)
                        ) + fadeIn(animationSpec = tween(500))).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { -1000 },
                                animationSpec = tween(500)
                            ) + fadeOut(animationSpec = tween(500))
                        )
                    },
                    label = "stats_animation"
                ) { index ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stats[index].value,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stats[index].label,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stats.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentIndex) 10.dp else 8.dp)
                                .background(
                                    color = if (index == currentIndex)
                                        Color.White
                                    else
                                        Color.White.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                        if (index < stats.size - 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
    }
}

data class StatItem(val value: String, val label: String)
