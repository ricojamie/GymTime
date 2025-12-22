package com.example.gymtime.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.ui.theme.ThemeColors

@Composable
fun MuscleDistributionChart(
    data: List<MuscleDistribution>,
    modifier: Modifier = Modifier,
    chartSize: Dp = 200.dp,
    strokeWidth: Dp = 28.dp
) {
    val totalVolume = remember(data) { data.sumOf { it.setVolume }.toFloat() }
    
    // Generate colors
    val baseColor = MaterialTheme.colorScheme.primary
    val colors = remember(data, baseColor) {
        listOf(
            baseColor,
            Color(0xFF3B82F6), // Blue
            Color(0xFFEC4899), // Pink
            Color(0xFFA855F7), // Purple
            Color(0xFFF59E0B), // Amber
            Color(0xFF10B981), // Emerald
            Color(0xFF6366F1), // Indigo
            Color(0xFFEF4444), // Red
            Color(0xFF06B6D4)  // Cyan
        )
    }

    // Interaction State
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    // Derived state for center text
    val centerText = if (selectedIndex >= 0 && selectedIndex < data.size) {
        val item = data[selectedIndex]
        item.muscle
    } else {
        "Last 30 Days"
    }

    val centerSubText = if (selectedIndex >= 0 && selectedIndex < data.size) {
        val item = data[selectedIndex]
        "${item.setVolume} sets"
    } else {
        "${totalVolume.toInt()}"
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Chart
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(chartSize)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val delta = offset - center
                            val angle = (Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat() + 360f) % 360f
                            
                            // Adjust for startAngle = -90
                            // Our drawing starts at -90 (270 degrees)
                            // So 0 degrees in our calculation (3 o'clock) corresponds to 90 degrees offset from start?
                            // Let's normalize touch angle to match draw start of -90.
                            // Draw starts at 12 o'clock (-90 deg).
                            // Atan2: 3 o'clock = 0, 6 = 90, 9 = 180, 12 = -90/270.
                            
                            // Let's convert touch angle (0 at 3 o'clock) to matched layout (0 at 12 o'clock)
                            // Touch: 0 (Right), 90 (Bottom), 180 (Left), 270 (Top)
                            // Draw: 0 starts at Top (-90 in Canvas coords).
                            
                            // Normalized Angle where 0 is Top (12 o'clock) and increases clockwise:
                            // Touch(270) -> Norm(0)
                            // Touch(0) -> Norm(90)
                            // Touch(90) -> Norm(180)
                            
                            val normalizedAngle = (angle + 90) % 360
                            
                            var currentAngle = 0f
                            var foundIndex = -1
                            
                            for (i in data.indices) {
                                val item = data[i]
                                val sweep = (item.setVolume / totalVolume) * 360f
                                if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + sweep) {
                                    foundIndex = i
                                    break
                                }
                                currentAngle += sweep
                            }
                            
                            selectedIndex = if (selectedIndex == foundIndex) -1 else foundIndex
                        }
                    }
            ) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
                var startAngle = -90f
                
                if (data.isEmpty()) {
                     drawArc(
                        color = Color.DarkGray,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                } else {
                    data.forEachIndexed { index, item ->
                        val sweepAngle = (item.setVolume / totalVolume) * 360f
                        val color = colors[index % colors.size]
                        
                        // Highlight logic
                        val isSelected = selectedIndex == index
                        val alpha = if (selectedIndex == -1 || isSelected) 1f else 0.3f
                        val currentStrokeWidth = if (isSelected) strokeWidth.toPx() + 10f else strokeWidth.toPx()
                        
                        drawArc(
                            color = color.copy(alpha = alpha),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Butt)
                        )
                        
                        startAngle += sweepAngle
                    }
                }
            }
            
            // Inner Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text(
                    text = centerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = centerSubText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (selectedIndex == -1) {
                    Text(
                        text = "Sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        
        // Full Legend (FlowRow would be ideal, but simple Column of Rows is fine for now)
        // Let's use a simple wrapped layout or just a list since we are in a scrollable parent.
        // Actually, user asked for full legend. Vertical list is clear.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEachIndexed { index, item ->
                val color = colors[index % colors.size]
                val percentage = (item.setVolume / totalVolume * 100).toInt()
                val isSelected = selectedIndex == index
                
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .clickable { selectedIndex = if (isSelected) -1 else index }
                       .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                       .padding(4.dp),
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = color)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.muscle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedIndex == -1 || isSelected) Color.White else Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$percentage% (${item.setVolume})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
