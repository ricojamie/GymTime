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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.LocalAppColors

@Composable
fun RoutineCard(
    hasActiveRoutine: Boolean,
    routineName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextDayName: String? = null,
    dayPosition: String? = null,
    exercisePreview: List<String> = emptyList(),
    lastPerformedLabel: String? = null
) {
    val accentColor = MaterialTheme.colorScheme.primary

    GlowCard(
        modifier = modifier.fillMaxSize(),
        onClick = onClick
    ) {
        if (hasActiveRoutine && routineName != null && nextDayName != null) {
            // Active routine: show what's up next at a glance.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = (dayPosition ?: routineName).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = nextDayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exercisePreview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exercisePreview.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastPerformedLabel ?: "Not done yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
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
                    tint = if (hasActiveRoutine) accentColor else LocalAppColors.current.textTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (hasActiveRoutine && routineName != null) {
                    Text(
                        text = routineName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to view",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary
                    )
                } else {
                    Text(
                        text = "Routines",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.textPrimary
                    )
                    Text(
                        text = "Tap to browse",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary
                    )
                }
            }
        }
    }
}
