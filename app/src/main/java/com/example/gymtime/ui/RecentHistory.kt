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
fun RecentHistory(workouts: List<Workout>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(workouts) { workout ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = workout.name, fontWeight = FontWeight.Bold)
                        Text(text = getRelativeDateString(workout.date), color = PrimaryAccent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_weight), contentDescription = "Volume")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Volume: ${workout.totalVolume} lbs")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_bicep), contentDescription = "Muscles")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Muscles: ${workout.musclesHit.joinToString()}")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_timer), contentDescription = "Duration")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Duration: ${workout.duration}")
                    }
                }
            }
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
fun RecentHistoryPreview() {
    GymTimeTheme {
        RecentHistory(
            workouts = listOf(
                Workout("Chest Day", "2025-11-17", 10000, listOf("Chest", "Triceps"), "1h 15m"),
                Workout("Back Day", "2025-11-16", 12000, listOf("Back", "Biceps"), "1h 30m"),
            )
        )
    }
}
