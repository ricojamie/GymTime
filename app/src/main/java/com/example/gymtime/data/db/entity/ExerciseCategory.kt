package com.example.gymtime.data.db.entity

const val WARMUP_MUSCLE_GROUP = "Warmups"

fun String.isWarmupMuscleGroup(): Boolean {
    val normalized = trim()
        .lowercase()
        .replace(" ", "")
        .replace("-", "")
    return normalized == "warmup" || normalized == "warmups"
}

val Exercise.isWarmupLibraryExercise: Boolean
    get() = targetMuscle.isWarmupMuscleGroup()
