package com.example.gymtime.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.R
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun RoutineCard(
    modifier: Modifier = Modifier,
    hasActiveRoutine: Boolean,
    routineName: String?,
    onClick: () -> Unit
) {
    GradientCard(
        modifier = modifier
            .height(150.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (hasActiveRoutine) R.drawable.ic_arrow_forward else R.drawable.ic_add
                    ),
                    contentDescription = if (hasActiveRoutine) "Continue Routine" else "Create Routine",
                    modifier = Modifier.size(32.dp),
                    tint = PrimaryAccent
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (hasActiveRoutine) "Continue Routine" else "Create Routine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (hasActiveRoutine && routineName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = routineName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}
