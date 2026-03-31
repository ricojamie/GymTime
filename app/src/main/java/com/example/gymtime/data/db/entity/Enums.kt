package com.example.gymtime.data.db.entity

enum class LogType {
    WEIGHT_REPS,
    REPS_ONLY,
    DURATION,
    WEIGHT_DISTANCE,
    DISTANCE_TIME,
    WEIGHT_TIME,
    CALORIES_TIME
}

enum class DistanceUnit {
    METERS,
    KILOMETERS,
    YARDS,
    FEET,
    MILES,
    STEPS,
    FLOORS;

    val isConvertibleToMeters: Boolean
        get() = this !in setOf(STEPS, FLOORS)
}
