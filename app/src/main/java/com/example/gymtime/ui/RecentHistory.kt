package com.example.gymtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.R
import com.example.gymtime.ui.theme.GymTimeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.gymtime.ui.theme.PrimaryAccent

data class Workout(
    val name: String,
    val date: String,
    val totalVolume: Int,
    val musclesHit: List<String>,
    val duration: String
)

@Composable
fun RecentWorkoutCard(workout: Workout?) {
    if (workout == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_timer),
                    contentDescription = "Recent Workout",
                    tint = PrimaryAccent
                )

                Column {
                    Text(
                        text = workout.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${getRelativeDateString(workout.date)} • ${workout.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Repeat",
                color = PrimaryAccent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getRelativeDateString(dateString: String): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = dateFormat.parse(dateString) ?: return dateString
    val calendar = Calendar.getInstance()
    val today = calendar.time
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = calendar.time
    calendar.time = today
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val lastWeek = calendar.time

    return when {
        android.text.format.DateUtils.isToday(date.time) -> "Today"
        android.text.format.DateUtils.isToday(yesterday.time) -> "Yesterday"
        date.after(lastWeek) -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> dateString
    }
}

@Preview(showBackground = true)
@Composable
fun RecentWorkoutCardPreview() {
    GymTimeTheme {
        RecentWorkoutCard(
            workout = Workout("Upper Body Power", "2025-11-17", 10000, listOf("Chest", "Triceps"), "1h 15m")
        )
    }
}
