package com.example.gymtime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val accentColor = MaterialTheme.colorScheme.primary

    GlowCard(
        modifier = modifier.fillMaxSize(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasActiveRoutine) Icons.Outlined.DateRange else Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (hasActiveRoutine) accentColor else TextTertiary
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (hasActiveRoutine && routineName != null) {
                Text(
                    text = routineName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to start",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "Routines",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tap to browse",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}