package com.example.gymtime.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Greeting() {
    val calendar = Calendar.getInstance()
    val greeting = when (calendar.get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val date = dateFormat.format(calendar.time)

    Column {
        Text(text = greeting, style = MaterialTheme.typography.headlineMedium)
        Text(text = date, style = MaterialTheme.typography.bodyLarge)
    }
}
