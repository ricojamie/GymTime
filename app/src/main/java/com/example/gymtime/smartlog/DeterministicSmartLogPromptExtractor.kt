package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicSmartLogPromptExtractor @Inject constructor() : SmartLogPromptExtractor {
    override val priority: Int = 0

    override suspend fun extract(commandText: String): ParsedLoggingCommand? = parse(commandText)

    fun parse(commandText: String): ParsedLoggingCommand? {
        val input = commandText.trim()
        if (input.isEmpty()) return null

        val exerciseQuery = extractExerciseQuery(input)
        val payload = if (exerciseQuery == null) input else input.removePrefix(exerciseQuery).trimStart(' ', ',', ':', '-')
        val lower = payload.lowercase()
        val isWarmup = Regex("\\b(warm[ -]?up|wu)\\b").containsMatchIn(lower)
        val rpe = Regex("\\brpe\\s*(?:of\\s*)?(10|[1-9](?:\\.\\d+)?)\\b")
            .find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        val weightUnit = when {
            Regex("\\b(kg|kgs|kilo|kilos|kilogram|kilograms)\\b").containsMatchIn(lower) -> WeightUnit.KILOGRAMS
            Regex("\\b(lb|lbs|pound|pounds)\\b").containsMatchIn(lower) -> WeightUnit.POUNDS
            else -> null
        }

        parseCountRepsWeight(lower, weightUnit, rpe, isWarmup)?.let {
            return ParsedLoggingCommand(exerciseQuery, it)
        }
        parseRepsByWeight(lower, weightUnit, rpe, isWarmup)?.let {
            return ParsedLoggingCommand(exerciseQuery, listOf(it))
        }
        parseDumbbellShorthand(lower, weightUnit, rpe, isWarmup)?.let {
            return ParsedLoggingCommand(exerciseQuery, it)
        }

        val calories = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:cal|cals|calorie|calories)\\b")
            .find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        val distanceMatch = Regex(
            "(\\d+(?:\\.\\d+)?)\\s*(km|kilometers?|miles?|mi|meters?|metres?|yards?|yd|feet|ft|steps?|floors?)\\b"
        ).find(lower)
        val distanceValue = distanceMatch?.groupValues?.get(1)?.toFloatOrNull()
        val distanceUnit = distanceMatch?.groupValues?.get(2)?.let(::parseDistanceUnit)
        val durationSeconds = parseDurationSeconds(lower)

        val explicitWeight = Regex(
            "(\\d+(?:\\.\\d+)?)\\s*(?:lb|lbs|pounds?|kg|kgs|kilos?|kilograms?)\\b"
        ).find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        val reps = Regex("(\\d+)\\s*(?:reps?|repetitions?)\\b")
            .find(lower)?.groupValues?.get(1)?.toIntOrNull()

        val draft = SetDraft(
            weight = explicitWeight,
            weightUnit = weightUnit,
            reps = reps,
            rpe = rpe,
            durationSeconds = durationSeconds,
            distanceValue = distanceValue,
            distanceUnit = distanceUnit,
            calories = calories,
            isWarmup = isWarmup
        )
        if (draft == SetDraft(isWarmup = isWarmup)) return null
        return ParsedLoggingCommand(exerciseQuery, listOf(draft))
    }

    private fun extractExerciseQuery(input: String): String? {
        if (input.firstOrNull()?.isDigit() == true) return null
        val firstNumber = input.indexOfFirst { it.isDigit() }
        if (firstNumber <= 0) return null
        return input.substring(0, firstNumber)
            .trim()
            .trimEnd(',', ':', '-', 'x', '×')
            .replace(Regex("\\b(log|record|add|set|for)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.length >= 2 }
    }

    private fun parseCountRepsWeight(
        input: String,
        unit: WeightUnit?,
        rpe: Float?,
        warmup: Boolean
    ): List<SetDraft>? {
        val match = Regex(
            "\\b(\\d{1,2})\\s*[x×]\\s*(\\d{1,3})\\s*(?:reps?\\s*)?(?:at|@)\\s*(\\d+(?:\\.\\d+)?)"
        ).find(input) ?: return null
        val count = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..20 } ?: return null
        val reps = match.groupValues[2].toIntOrNull() ?: return null
        val weight = match.groupValues[3].toFloatOrNull() ?: return null
        return List(count) { SetDraft(weight, unit, reps, rpe, isWarmup = warmup) }
    }

    private fun parseRepsByWeight(
        input: String,
        unit: WeightUnit?,
        rpe: Float?,
        warmup: Boolean
    ): SetDraft? {
        Regex(
            "\\b(\\d{1,3})\\s*(?:reps?\\s*)?(?:by|x|×)\\s*(\\d+(?:\\.\\d+)?)\\s*(lb|lbs|pounds?|kg|kgs|kilos?|kilograms?)\\b"
        ).find(input)?.let { match ->
            return SetDraft(
                weight = match.groupValues[2].toFloatOrNull(),
                weightUnit = unit,
                reps = match.groupValues[1].toIntOrNull(),
                rpe = rpe,
                isWarmup = warmup
            )
        }

        Regex(
            "\\b(\\d+(?:\\.\\d+)?)\\s*(?:lb|lbs|pounds?|kg|kgs|kilos?|kilograms?)?\\s*(?:x|×|for)\\s*(\\d{1,3})\\b"
        ).find(input)?.let { match ->
            return SetDraft(
                weight = match.groupValues[1].toFloatOrNull(),
                weightUnit = unit,
                reps = match.groupValues[2].toIntOrNull(),
                rpe = rpe,
                isWarmup = warmup
            )
        }
        return null
    }

    private fun parseDumbbellShorthand(
        input: String,
        unit: WeightUnit?,
        rpe: Float?,
        warmup: Boolean
    ): List<SetDraft>? {
        val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)s?\\s*[,;]\\s*(\\d{1,3}(?:\\s*[,;]\\s*\\d{1,3})+)")
            .find(input) ?: return null
        val weight = match.groupValues[1].toFloatOrNull() ?: return null
        val reps = Regex("\\d{1,3}").findAll(match.groupValues[2]).mapNotNull { it.value.toIntOrNull() }.toList()
        if (reps.isEmpty() || reps.size > 20) return null
        return reps.map { SetDraft(weight, unit, it, rpe, isWarmup = warmup) }
    }

    private fun parseDurationSeconds(input: String): Int? {
        Regex("\\b(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?\\b").find(input)?.let { match ->
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val c = match.groupValues[3].toIntOrNull()
            return if (c == null) a * 60 + b else a * 3600 + b * 60 + c
        }
        val hours = Regex("(\\d+)\\s*(?:hours?|hrs?|hr)\\b").find(input)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)\\s*(?:minutes?|mins?|min)\\b").find(input)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = Regex("(\\d+)\\s*(?:seconds?|secs?|sec)\\b").find(input)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 3600 + minutes * 60 + seconds).takeIf { it > 0 }
    }

    private fun parseDistanceUnit(raw: String): DistanceUnit = when (raw.lowercase()) {
        "km", "kilometer", "kilometers" -> DistanceUnit.KILOMETERS
        "mile", "miles", "mi" -> DistanceUnit.MILES
        "yard", "yards", "yd" -> DistanceUnit.YARDS
        "feet", "ft" -> DistanceUnit.FEET
        "step", "steps" -> DistanceUnit.STEPS
        "floor", "floors" -> DistanceUnit.FLOORS
        else -> DistanceUnit.METERS
    }
}
