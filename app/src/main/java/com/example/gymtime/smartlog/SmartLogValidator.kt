package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import javax.inject.Inject

class SmartLogValidator @Inject constructor() {
    fun validate(exercise: Exercise, parsed: ParsedLoggingCommand): SmartLogValidationResult {
        if (parsed.sets.isEmpty()) return SmartLogValidationResult(errors = listOf("No sets were found."))
        if (parsed.sets.size > MAX_QUEUED_SETS) {
            return SmartLogValidationResult(errors = listOf("Smart Log supports up to $MAX_QUEUED_SETS sets at a time."))
        }

        val errors = mutableListOf<String>()
        val notices = linkedSetOf<String>()
        val validated = parsed.sets.mapIndexed { index, raw ->
            val prefix = if (parsed.sets.size == 1) "Set" else "Set ${index + 1}"
            validateFinitePositive(raw.weight, "$prefix weight", errors)
            validatePositive(raw.reps, "$prefix reps", errors)
            validateFinitePositive(raw.rpe, "$prefix RPE", errors, allowZero = false)
            if (raw.rpe != null && raw.rpe !in 1f..10f) errors += "$prefix RPE must be between 1 and 10."
            validatePositive(raw.durationSeconds, "$prefix duration", errors)
            validateFinitePositive(raw.distanceValue, "$prefix distance", errors)
            validateFinitePositive(raw.calories, "$prefix calories", errors)

            val weight = raw.weight?.let {
                if (raw.weightUnit == WeightUnit.KILOGRAMS) {
                    notices += "Kilograms were converted to pounds."
                    it * KG_TO_LB
                } else it
            }
            val normalized = raw.copy(
                weight = weight,
                weightUnit = weight?.let { WeightUnit.POUNDS },
                distanceUnit = raw.distanceValue?.let { raw.distanceUnit ?: exercise.defaultDistanceUnit },
                note = raw.note?.trim()?.takeIf { it.isNotEmpty() }
            )
            validateShape(exercise.logType, normalized, prefix, errors)
            normalized
        }

        if (errors.isNotEmpty()) return SmartLogValidationResult(errors = errors.distinct())
        return SmartLogValidationResult(
            draft = ValidatedSmartLogDraft(exercise, validated, notices.toList())
        )
    }

    private fun validateShape(logType: LogType, set: SetDraft, prefix: String, errors: MutableList<String>) {
        fun require(value: Any?, field: String) {
            if (value == null) errors += "$prefix needs $field for this exercise."
        }
        fun reject(value: Any?, field: String) {
            if (value != null) errors += "$prefix includes $field, which ${logType.label} does not log."
        }

        when (logType) {
            LogType.WEIGHT_REPS -> {
                require(set.weight, "weight"); require(set.reps, "reps")
                reject(set.durationSeconds, "duration"); reject(set.distanceValue, "distance"); reject(set.calories, "calories")
            }
            LogType.REPS_ONLY -> {
                require(set.reps, "reps")
                reject(set.weight, "weight"); reject(set.durationSeconds, "duration"); reject(set.distanceValue, "distance"); reject(set.calories, "calories")
            }
            LogType.DURATION -> {
                require(set.durationSeconds, "duration")
                reject(set.weight, "weight"); reject(set.reps, "reps"); reject(set.distanceValue, "distance"); reject(set.calories, "calories")
            }
            LogType.WEIGHT_DISTANCE -> {
                require(set.weight, "weight"); require(set.distanceValue, "distance")
                reject(set.reps, "reps"); reject(set.durationSeconds, "duration"); reject(set.calories, "calories")
            }
            LogType.DISTANCE_TIME -> {
                require(set.distanceValue, "distance"); require(set.durationSeconds, "duration")
                reject(set.weight, "weight"); reject(set.reps, "reps"); reject(set.calories, "calories")
            }
            LogType.WEIGHT_TIME -> {
                require(set.weight, "weight"); require(set.durationSeconds, "duration")
                reject(set.reps, "reps"); reject(set.distanceValue, "distance"); reject(set.calories, "calories")
            }
            LogType.CALORIES_TIME -> {
                require(set.calories, "calories"); require(set.durationSeconds, "duration")
                reject(set.weight, "weight"); reject(set.reps, "reps"); reject(set.distanceValue, "distance")
            }
        }
    }

    private fun validateFinitePositive(
        value: Float?,
        label: String,
        errors: MutableList<String>,
        allowZero: Boolean = false
    ) {
        if (value == null) return
        if (!value.isFinite() || if (allowZero) value < 0f else value <= 0f) errors += "$label must be a positive number."
    }

    private fun validatePositive(value: Int?, label: String, errors: MutableList<String>) {
        if (value != null && value <= 0) errors += "$label must be greater than zero."
    }

    private val LogType.label: String
        get() = name.lowercase().replace('_', ' ')

    companion object {
        const val MAX_QUEUED_SETS = 20
        const val KG_TO_LB = 2.20462f
    }
}
