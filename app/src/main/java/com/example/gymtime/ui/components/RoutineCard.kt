package com.example.gymtime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextSecondary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun RoutineCard(
    hasActiveRoutine: Boolean,
    routineName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasActiveRoutine) "ACTIVE ROUTINE" else "NO ACTIVE ROUTINE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = TextTertiary
                )
            }

            Text(
                text = routineName ?: "Select a Routine",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = if (hasActiveRoutine) "Tap to start workout" else "Tap to view routines",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}