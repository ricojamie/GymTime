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
        get() {
            if (!hasValidOptionalRpe(rpe)) return false
            val parsedWeight = parseStrictDecimal(weight)
            val parsedReps = parseStrictPositiveInt(reps)
            val parsedDuration = parseStrictDurationSeconds(duration)
            val parsedDistance = parseStrictDecimal(distance)
            val parsedCalories = parseStrictDecimal(calories)

            return when (logType) {
                "WEIGHT_REPS" -> parsedWeight != null && parsedReps != null
                "REPS_ONLY" -> parsedReps != null
                "DURATION" -> parsedDuration != null && parsedDuration > 0
                "WEIGHT_DISTANCE" -> parsedWeight != null && positiveFloat(parsedDistance)
                "DISTANCE_TIME" -> positiveFloat(parsedDistance) && parsedDuration != null && parsedDuration > 0
                "WEIGHT_TIME" -> parsedWeight != null && parsedDuration != null && parsedDuration > 0
                "CALORIES_TIME" -> positiveFloat(parsedCalories) && parsedDuration != null && parsedDuration > 0
                else -> false
            }
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
        private val STRICT_DECIMAL_PATTERN = Regex("""(?:\d+(?:\.\d+)?|\.\d+)""")
        private val POSITIVE_INT_PATTERN = Regex("""\d+""")

        private fun positiveFloat(value: Float?): Boolean =
            value != null && value.isFinite() && value > 0f

        private fun parseStrictPositiveInt(raw: String): Int? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || !POSITIVE_INT_PATTERN.matches(trimmed)) return null
            return trimmed.toIntOrNull()?.takeIf { it > 0 }
        }

        private fun parseStrictDecimal(raw: String): Float? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || !STRICT_DECIMAL_PATTERN.matches(trimmed)) return null
            return trimmed.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
        }

        private fun hasValidOptionalRpe(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return true
            val parsed = parseStrictDecimal(trimmed) ?: return false
            return parsed in 0f..10f
        }

        private fun parseStrictDurationSeconds(raw: String): Int? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(":")
            if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
                return null
            }
            return try {
                val totalSeconds: Long = when (parts.size) {
                    1 -> parts[0].toLong()
                    2 -> {
                        val minutes = parts[0].toLong()
                        val seconds = parts[1].toLong()
                        if (seconds !in 0L..59L) return null
                        (minutes * 60L) + seconds
                    }
                    3 -> {
                        val hours = parts[0].toLong()
                        val minutes = parts[1].toLong()
                        val seconds = parts[2].toLong()
                        if (minutes !in 0L..59L || seconds !in 0L..59L) return null
                        (hours * 3_600L) + (minutes * 60L) + seconds
                    }
                    else -> return null
                }
                if (totalSeconds in 1L..Int.MAX_VALUE.toLong()) totalSeconds.toInt() else null
            } catch (_: NumberFormatException) {
                null
            }
        }

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
