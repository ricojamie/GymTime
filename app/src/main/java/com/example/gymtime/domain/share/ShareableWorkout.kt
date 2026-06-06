package com.example.gymtime.domain.share

import java.util.Date

data class ShareableWorkout(
    val date: Date,
    val durationMinutes: Int?,
    val totalVolume: Float,
    val totalWorkingSets: Int,
    val exercises: List<ShareableExercise>
)

data class ShareableExercise(
    val name: String,
    val targetMuscle: String,
    val sets: List<ShareableSet>
)

data class ShareableSet(
    val weight: Float?,
    val reps: Int?,
    val isWarmup: Boolean,
    val isPersonalRecord: Boolean,
    val durationSeconds: Int? = null,
    val distanceMeters: Float? = null,
    val calories: Float? = null
)
