package com.example.gymtime.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.R
import com.example.gymtime.ui.components.PrimaryGradientCard
import com.example.gymtime.ui.theme.GymTimeTheme
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun QuickStartCard(onClick: () -> Unit) {
    PrimaryGradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // "EMPTY SESSION" tag in top right
            Text(
                text = "EMPTY SESSION",
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryAccent,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )

            // Main content centered
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play_arrow),
                    contentDescription = "Quick Start",
                    modifier = Modifier.size(48.dp),
                    tint = PrimaryAccent
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Start Workout",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Build as you go",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        }
    }
}
