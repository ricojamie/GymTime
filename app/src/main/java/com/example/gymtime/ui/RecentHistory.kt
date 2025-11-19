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
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.ui.theme.GymTimeTheme
import com.example.gymtime.ui.theme.PrimaryAccent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
                        text = workout.name ?: "Unnamed Workout",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${getRelativeDateString(workout.startTime)} • ${workout.endTime?.let { getDurationString(workout.startTime, it) } ?: "Ongoing"}",
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

private fun getDurationString(start: Date, end: Date): String {
    val diff = end.time - start.time
    val minutes = diff / (1000 * 60)
    return "${minutes}m"
}

private fun getRelativeDateString(date: Date): String {
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
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}

@Preview(showBackground = true)
@Composable
fun RecentWorkoutCardPreview() {
    GymTimeTheme {
        RecentWorkoutCard(
            workout = Workout(id = 1, name = "Upper Body Power", startTime = Date(), endTime = Date(), note = null)
        )
    }
}
