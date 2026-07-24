package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise

enum class WeightUnit {
    POUNDS,
    KILOGRAMS
}

data class ParsedLoggingCommand(
    val exerciseQuery: String? = null,
    val sets: List<SetDraft> = emptyList()
)

data class SetDraft(
    val weight: Float? = null,
    val weightUnit: WeightUnit? = null,
    val reps: Int? = null,
    val rpe: Float? = null,
    val durationSeconds: Int? = null,
    val distanceValue: Float? = null,
    val distanceUnit: DistanceUnit? = null,
    val calories: Float? = null,
    val isWarmup: Boolean = false,
    val note: String? = null
)

data class ValidatedSmartLogDraft(
    val exercise: Exercise,
    val sets: List<SetDraft>,
    val notices: List<String> = emptyList()
)

data class ExerciseMatchCandidate(
    val exercise: Exercise,
    val score: Float,
    val allTimeSetCount: Int = 0,
    val lastUsedMs: Long? = null
)

sealed interface ExerciseMatchResult {
    data class Matched(val candidate: ExerciseMatchCandidate) : ExerciseMatchResult
    data class NeedsConfirmation(val candidates: List<ExerciseMatchCandidate>) : ExerciseMatchResult
    data object NoMatch : ExerciseMatchResult
}

data class SmartLogValidationResult(
    val draft: ValidatedSmartLogDraft? = null,
    val errors: List<String> = emptyList()
) {
    val isValid: Boolean get() = draft != null && errors.isEmpty()
}

/**
 * Boundary implemented by both the deterministic parser and the optional
 * Gemini Nano adapter. Higher-priority extractors are attempted first.
 */
interface SmartLogPromptExtractor {
    val priority: Int
    suspend fun extract(commandText: String): ParsedLoggingCommand?
}
