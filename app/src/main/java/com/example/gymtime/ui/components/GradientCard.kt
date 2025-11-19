package com.example.gymtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.BackgroundCanvas
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.PrimaryAccentDark

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        PrimaryAccentDark.copy(alpha = 0.08f),
        BackgroundCanvas,
        BackgroundCanvas
    ),
    centerX: Float = 0.1f, // Position glow in corner (0.0 = left, 1.0 = right)
    centerY: Float = 0.1f, // Position glow in corner (0.0 = top, 1.0 = bottom)
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
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
                        colors = gradientColors,
                        center = androidx.compose.ui.geometry.Offset(
                            x = centerX,
                            y = centerY
                        ),
                        radius = 600f
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
fun PrimaryGradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GradientCard(
        modifier = modifier,
        gradientColors = listOf(
            PrimaryAccent.copy(alpha = 0.12f), // Very subtle green glow
            BackgroundCanvas,
            BackgroundCanvas
        ),
        centerX = 0.15f, // Top-left corner glow
        centerY = 0.15f,
        onClick = onClick,
        content = content
    )
}
