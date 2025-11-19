package com.example.gymtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun WeeklyVolumeCard(
    modifier: Modifier = Modifier,
    weeklyVolume: Int
) {
    GradientCard(
        modifier = modifier
            .height(150.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "VOLUME TREND",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Simple line graph
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val points = listOf(0.6f, 0.4f, 0.7f, 0.3f, 0.5f, 0.8f, 0.6f)
                    val path = Path()
                    val width = size.width
                    val height = size.height
                    val xSpacing = width / (points.size - 1)

                    points.forEachIndexed { index, point ->
                        val x = index * xSpacing
                        val y = height - (point * height)

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = PrimaryAccent.copy(alpha = 0.8f),
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${weeklyVolume} lbs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "This week",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}
