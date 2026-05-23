package com.example.gymtime.domain.report

import java.util.Date

data class MonthlyReport(
    val periodStart: Date,
    val periodEnd: Date,
    val monthLabel: String,
    val workoutCount: Int,
    val totalVolume: Float,
    val totalWorkingSets: Int,
    val exerciseCount: Int,
    val topMuscles: List<MuscleTotal>,
    val undertrainedMuscles: List<String>,
    val newPRs: List<MonthlyPR>,
    val averageRating: Float?
)

data class MuscleTotal(
    val muscle: String,
    val setCount: Int
)

data class MonthlyPR(
    val exerciseName: String,
    val weight: Float,
    val reps: Int
)
