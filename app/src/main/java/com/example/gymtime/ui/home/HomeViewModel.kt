package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import com.example.gymtime.ui.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    val workouts = listOf(
        Workout("Chest Day", "2025-11-17", 10000, listOf("Chest", "Triceps"), "1h 15m"),
        Workout("Back Day", "2025-11-16", 12000, listOf("Back", "Biceps"), "1h 30m"),
    )

    val hasActiveRoutine = true
    val nextWorkoutName = "Legs"
    val streak = 3
    val poundsLifted = 22000
    val pbs = 3
}
