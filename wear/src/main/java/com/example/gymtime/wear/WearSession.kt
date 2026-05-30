package com.example.gymtime.wear

import com.google.android.gms.wearable.DataMap

data class WearSession(
    val active: Boolean = false,
    val workoutId: Long = -1L,
    val exerciseId: Long = -1L,
    val exerciseName: String = "",
    val targetMuscle: String = "",
    val logType: String = "",
    val distanceUnit: String = "",
    val setNumber: Int = 0,
    val weight: String = "",
    val reps: String = "",
    val rpe: String = "",
    val duration: String = "",
    val distance: String = "",
    val calories: String = "",
    val isWarmup: Boolean = false,
    val restSeconds: Int = 0,
    val timerRemainingSeconds: Int = 0,
    val timerRunning: Boolean = false,
    val timerCompletionId: Long = 0,
    val setSaveConfirmationId: Long = 0,
    val updatedAt: Long = 0
) {
    val canLog: Boolean
        get() = when (logType) {
            "WEIGHT_REPS" -> weight.isNotBlank() && reps.isNotBlank()
            "REPS_ONLY" -> reps.isNotBlank()
            "DURATION" -> duration.isNotBlank()
            "WEIGHT_DISTANCE" -> weight.isNotBlank() && distance.isNotBlank()
            "DISTANCE_TIME" -> distance.isNotBlank() && duration.isNotBlank()
            "WEIGHT_TIME" -> weight.isNotBlank() && duration.isNotBlank()
            "CALORIES_TIME" -> calories.isNotBlank() && duration.isNotBlank()
            else -> false
        }

    fun toDataMap(): DataMap = DataMap().apply {
        putLong(WearContract.KEY_WORKOUT_ID, workoutId)
        putLong(WearContract.KEY_EXERCISE_ID, exerciseId)
        putString(WearContract.KEY_WEIGHT, weight)
        putString(WearContract.KEY_REPS, reps)
        putString(WearContract.KEY_RPE, rpe)
        putString(WearContract.KEY_DURATION, duration)
        putString(WearContract.KEY_DISTANCE, distance)
        putString(WearContract.KEY_CALORIES, calories)
        putBoolean(WearContract.KEY_IS_WARMUP, isWarmup)
    }

    companion object {
        fun fromDataMap(dataMap: DataMap): WearSession = WearSession(
            active = dataMap.getBoolean(WearContract.KEY_ACTIVE, false),
            workoutId = dataMap.getLong(WearContract.KEY_WORKOUT_ID, -1L),
            exerciseId = dataMap.getLong(WearContract.KEY_EXERCISE_ID, -1L),
            exerciseName = dataMap.getString(WearContract.KEY_EXERCISE_NAME).orEmpty(),
            targetMuscle = dataMap.getString(WearContract.KEY_TARGET_MUSCLE).orEmpty(),
            logType = dataMap.getString(WearContract.KEY_LOG_TYPE).orEmpty(),
            distanceUnit = dataMap.getString(WearContract.KEY_DISTANCE_UNIT).orEmpty(),
            setNumber = dataMap.getInt(WearContract.KEY_SET_NUMBER, 0),
            weight = dataMap.getString(WearContract.KEY_WEIGHT).orEmpty(),
            reps = dataMap.getString(WearContract.KEY_REPS).orEmpty(),
            rpe = dataMap.getString(WearContract.KEY_RPE).orEmpty(),
            duration = dataMap.getString(WearContract.KEY_DURATION).orEmpty(),
            distance = dataMap.getString(WearContract.KEY_DISTANCE).orEmpty(),
            calories = dataMap.getString(WearContract.KEY_CALORIES).orEmpty(),
            isWarmup = dataMap.getBoolean(WearContract.KEY_IS_WARMUP, false),
            restSeconds = dataMap.getInt(WearContract.KEY_REST_SECONDS, 0),
            timerRemainingSeconds = dataMap.getInt(WearContract.KEY_TIMER_REMAINING_SECONDS, 0),
            timerRunning = dataMap.getBoolean(WearContract.KEY_TIMER_RUNNING, false),
            timerCompletionId = dataMap.getLong(WearContract.KEY_TIMER_COMPLETION_ID, 0),
            setSaveConfirmationId = dataMap.getLong(WearContract.KEY_SET_SAVE_CONFIRMATION_ID, 0),
            updatedAt = dataMap.getLong(WearContract.KEY_UPDATED_AT, 0)
        )
    }
}
