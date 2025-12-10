package com.example.gymtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.BackgroundCanvas
import com.example.gymtime.ui.theme.SurfaceCards

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Color = SurfaceCards,
    content: @Composable () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f), // Very subtle glow
                            backgroundColor,
                            backgroundColor
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            x = 0.15f, // Top-left corner glow position (0.0 = left, 1.0 = right)
                            y = 0.15f  // Top-left corner glow position (0.0 = top, 1.0 = bottom)
                        ),
                        radius = 600f
                    )
                )
        ) {
            content()
        }
    }
}
