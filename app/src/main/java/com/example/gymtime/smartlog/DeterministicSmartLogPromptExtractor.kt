package com.example.gymtime.smartlog

import com.example.gymtime.data.db.entity.DistanceUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class DeterministicSmartLogPromptExtractor @Inject constructor() : SmartLogPromptExtractor {
    override val priority: Int = 0

    override suspend fun extract(commandText: String): ParsedLoggingCommand? = parse(commandText)

    private data class ExtractedQuery(
        val exerciseQuery: String?,
        val payload: String,
        val leadingWarmup: Boolean
    )

    private sealed interface CountRepsOutcome {
        data object NotMatched : CountRepsOutcome
        data object InvalidZeroCount : CountRepsOutcome
        data class Matched(val sets: List<SetDraft>) : CountRepsOutcome
    }

    fun parse(commandText: String): ParsedLoggingCommand? {
        val rawInput = commandText.trim()
        if (rawInput.isEmpty()) return null

        val normalizedInput = normalizeNumbers(rawInput)
        val extracted = extractExerciseAndPayload(normalizedInput)
        val lower = extracted.payload.lowercase(Locale.ROOT)
        val isWarmup = extracted.leadingWarmup || WARMUP_REGEX.containsMatchIn(lower)
        val rpe = RPE_REGEX.find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        val weightUnit = when {
            KG_UNIT_REGEX.containsMatchIn(lower) -> WeightUnit.KILOGRAMS
            LB_UNIT_REGEX.containsMatchIn(lower) -> WeightUnit.POUNDS
            else -> null
        }

        when (val countReps = parseCountRepsWeight(lower, weightUnit, rpe, isWarmup)) {
            is CountRepsOutcome.Matched -> return ParsedLoggingCommand(extracted.exerciseQuery, countReps.sets)
            CountRepsOutcome.InvalidZeroCount -> return null
            CountRepsOutcome.NotMatched -> Unit
        }
        parseRepsByWeight(lower, weightUnit, rpe, isWarmup)?.let {
            return ParsedLoggingCommand(extracted.exerciseQuery, listOf(it))
        }
        parseDumbbellShorthand(lower, weightUnit, rpe, isWarmup)?.let {
            return ParsedLoggingCommand(extracted.exerciseQuery, it)
        }

        val calories = CALORIES_REGEX.find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        val distanceMatch = DISTANCE_REGEX.find(lower)
        val distanceValue = distanceMatch?.groupValues?.get(1)?.toFloatOrNull()
        val distanceUnit = distanceMatch?.groupValues?.get(2)?.let(::parseDistanceUnit)
        val explicitWeight = EXPLICIT_WEIGHT_REGEX.find(lower)?.groupValues?.get(1)?.toFloatOrNull()
        var reps = EXPLICIT_REPS_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val durationSeconds = parseDurationSeconds(lower, allowBareS = reps == null)

        if (explicitWeight == null &&
            reps == null &&
            durationSeconds == null &&
            distanceValue == null &&
            calories == null
        ) {
            val barePayload = lower
                .replace(RPE_REGEX, " ")
                .replace(WARMUP_REGEX, " ")
                .trim(' ', ',', ':', ';', '-')
            reps = BARE_INT_REGEX.matchEntire(barePayload)?.groupValues?.get(1)?.toIntOrNull()
        }

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
        return ParsedLoggingCommand(extracted.exerciseQuery, listOf(draft))
    }

    private fun normalizeNumbers(input: String): String =
        input.replace(THOUSANDS_COMMA_REGEX, "")
            .replace(LEADING_DOT_REGEX, "0.")

    private fun extractExerciseAndPayload(input: String): ExtractedQuery {
        var working = LEADING_COMMAND_PREFIX_REGEX.replaceFirst(input, "")
        val warmupMatch = LEADING_WARMUP_PREFIX_REGEX.find(working)
        val leadingWarmup = warmupMatch != null
        if (warmupMatch != null) {
            working = working.substring(warmupMatch.range.last + 1)
        }

        val firstNumber = working.indexOfFirst { it.isDigit() }
        if (firstNumber <= 0) {
            return ExtractedQuery(
                exerciseQuery = null,
                payload = working.trimStart(' ', ',', ':', '-'),
                leadingWarmup = leadingWarmup
            )
        }

        var cleanedPrefix = working.substring(0, firstNumber)
            .trim()
            .trimEnd(',', ':', '-', 'x', '×')
        while (true) {
            val stripped = TRAILING_COMMAND_WORD_REGEX.replace(cleanedPrefix, "")
                .trim()
                .trimEnd(',', ':', '-', 'x', '×')
            if (stripped == cleanedPrefix) break
            cleanedPrefix = stripped
        }
        val exerciseQuery = cleanedPrefix.takeIf { it.length >= 2 }
        val payload = working.substring(firstNumber).trimStart(' ', ',', ':', '-')
        return ExtractedQuery(
            exerciseQuery = exerciseQuery,
            payload = payload,
            leadingWarmup = leadingWarmup
        )
    }

    private fun parseCountRepsWeight(
        input: String,
        unit: WeightUnit?,
        rpe: Float?,
        warmup: Boolean
    ): CountRepsOutcome {
        val countMatch = COUNT_REPS_WEIGHT_REGEX.find(input)
            ?: SETS_OF_REPS_WEIGHT_REGEX.find(input)
        if (countMatch != null) {
            val count = countMatch.groupValues[1].toIntOrNull() ?: return CountRepsOutcome.NotMatched
            if (count <= 0) return CountRepsOutcome.InvalidZeroCount
            val reps = countMatch.groupValues[2].toIntOrNull() ?: return CountRepsOutcome.NotMatched
            val weightRaw = countMatch.groupValues[3].ifEmpty { countMatch.groupValues[4] }
            val weight = weightRaw.toFloatOrNull() ?: return CountRepsOutcome.NotMatched
            val boundedCount = minOf(count, MAX_OVERFLOW_SETS)
            return CountRepsOutcome.Matched(
                List(boundedCount) { SetDraft(weight, unit, reps, rpe, isWarmup = warmup) }
            )
        }

        val repsOnlyMatch = SETS_OF_REPS_ONLY_REGEX.find(input) ?: return CountRepsOutcome.NotMatched
        val count = repsOnlyMatch.groupValues[1].toIntOrNull() ?: return CountRepsOutcome.NotMatched
        if (count <= 0) return CountRepsOutcome.InvalidZeroCount
        val reps = repsOnlyMatch.groupValues[2].toIntOrNull() ?: return CountRepsOutcome.NotMatched
        val boundedCount = minOf(count, MAX_OVERFLOW_SETS)
        return CountRepsOutcome.Matched(
            List(boundedCount) { SetDraft(reps = reps, rpe = rpe, isWarmup = warmup) }
        )
    }

    private fun parseRepsByWeight(
        input: String,
        unit: WeightUnit?,
        rpe: Float?,
        warmup: Boolean
    ): SetDraft? {
        REPS_BY_WEIGHT_REGEX.find(input)?.let { match ->
            return SetDraft(
                weight = match.groupValues[2].toFloatOrNull(),
                weightUnit = unit,
                reps = match.groupValues[1].toIntOrNull(),
                rpe = rpe,
                isWarmup = warmup
            )
        }

        WEIGHT_BY_REPS_REGEX.find(input)?.let { match ->
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
        val match = DUMBBELL_SHORTHAND_REGEX.find(input) ?: return null
        val weight = match.groupValues[1].toFloatOrNull() ?: return null
        val reps = INTEGER_TOKEN_REGEX.findAll(match.groupValues[2])
            .mapNotNull { it.value.toIntOrNull() }
            .take(MAX_OVERFLOW_SETS)
            .toList()
        if (reps.isEmpty()) return null
        return reps.map { SetDraft(weight, unit, it, rpe, isWarmup = warmup) }
    }

    private fun parseDurationSeconds(input: String, allowBareS: Boolean): Int? {
        HMS_DURATION_REGEX.find(input)?.let { match ->
            val a = match.groupValues[1].toIntOrNull()
            val b = match.groupValues[2].toIntOrNull()
            val c = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            if (a != null && b != null) {
                val total = if (c == null) {
                    if (b in 0..59) a * 60 + b else -1
                } else {
                    if (b in 0..59 && c in 0..59) a * 3600 + b * 60 + c else -1
                }
                if (total > 0) return total
            }
        }
        val hours = HOURS_REGEX.find(input)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val minutes = MINUTES_REGEX.find(input)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val seconds = SECONDS_REGEX.find(input)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: (if (allowBareS) BARE_S_DURATION_REGEX.find(input)?.groupValues?.get(1)?.toDoubleOrNull() else null)
            ?: 0.0
        val total = (hours * 3600.0 + minutes * 60.0 + seconds).roundToInt()
        return total.takeIf { it > 0 }
    }

    private fun parseDistanceUnit(raw: String): DistanceUnit = when (raw.lowercase(Locale.ROOT)) {
        "km", "kilometer", "kilometers" -> DistanceUnit.KILOMETERS
        "mile", "miles", "mi" -> DistanceUnit.MILES
        "yard", "yards", "yd" -> DistanceUnit.YARDS
        "feet", "ft" -> DistanceUnit.FEET
        "step", "steps" -> DistanceUnit.STEPS
        "floor", "floors" -> DistanceUnit.FLOORS
        else -> DistanceUnit.METERS
    }

    private companion object {
        const val MAX_OVERFLOW_SETS = SmartLogValidator.MAX_QUEUED_SETS + 1
        const val WEIGHT_UNITS_PATTERN = "(?:lb|lbs|pounds?|kg|kgs|kilos?|kilograms?)"
        const val NON_REP_UNITS_PATTERN =
            "(?:lb|lbs|pounds?|kg|kgs|kilos?|kilograms?|s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?|km|kilometers?|mi|miles?|meters?|metres?|yd|yards?|ft|feet|steps?|floors?|cal|cals|calorie|calories|sets?)"

        val THOUSANDS_COMMA_REGEX = Regex("(?<=\\d),(?=\\d{3}(?!\\d))")
        val LEADING_DOT_REGEX = Regex("(?<!\\d)\\.(?=\\d)")
        val LEADING_COMMAND_PREFIX_REGEX = Regex("^(?:log|record|add)\\b[\\s,:-]*", RegexOption.IGNORE_CASE)
        val LEADING_WARMUP_PREFIX_REGEX = Regex("^(?:warm[ -]?up|wu)\\b[\\s,:-]*", RegexOption.IGNORE_CASE)
        val TRAILING_COMMAND_WORD_REGEX = Regex("\\b(?:log|record|add|sets?|for|at|with|of)\\s*$", RegexOption.IGNORE_CASE)

        val WARMUP_REGEX = Regex("\\b(?:warm[ -]?up|wu)\\b")
        val RPE_REGEX = Regex("\\brpe\\s*(?:of\\s*)?(\\d+(?:\\.\\d+)?)\\b")
        val KG_UNIT_REGEX = Regex("(?<![a-z])(?:kg|kgs|kilo|kilos|kilogram|kilograms)\\b")
        val LB_UNIT_REGEX = Regex("(?<![a-z])(?:lb|lbs|pound|pounds)\\b")

        val COUNT_REPS_WEIGHT_REGEX = Regex(
            "\\b(\\d+)\\s*[x×]\\s*(\\d+)\\s*(?:reps?\\s*)?(?:(?:at|@|with)\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?=$WEIGHT_UNITS_PATTERN\\b))"
        )
        val SETS_OF_REPS_WEIGHT_REGEX = Regex(
            "\\b(\\d+)\\s*sets?\\s*(?:of\\s*)?(\\d+)\\s*(?:reps?\\s*)?(?:(?:at|@|with)\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?=$WEIGHT_UNITS_PATTERN\\b))"
        )
        val SETS_OF_REPS_ONLY_REGEX = Regex("\\b(\\d+)\\s*sets?\\s*(?:of\\s*)?(\\d+)\\s*(?:reps?)?\\b")

        val REPS_BY_WEIGHT_REGEX = Regex(
            "\\b(\\d{1,3})\\s*(?:reps?\\s*)?(?:by|x|×)\\s*(\\d+(?:\\.\\d+)?)\\s*$WEIGHT_UNITS_PATTERN\\b"
        )
        val WEIGHT_BY_REPS_REGEX = Regex(
            "\\b(\\d+(?:\\.\\d+)?)\\s*$WEIGHT_UNITS_PATTERN?\\s*(?:x|×|for)\\s*(\\d{1,3})\\b(?![\\d.]|\\s+\\d|\\s*:\\d|\\s*$NON_REP_UNITS_PATTERN\\b)"
        )

        val DUMBBELL_SHORTHAND_REGEX = Regex(
            "^\\s*(\\d+(?:\\.\\d+)?)(?:\\s*$WEIGHT_UNITS_PATTERN\\b|s)?\\s*[,;]\\s*(\\d{1,3}(?:\\s*[,;]\\s*\\d{1,3})+)(?!\\s*$NON_REP_UNITS_PATTERN\\b)"
        )
        val INTEGER_TOKEN_REGEX = Regex("\\d{1,3}")

        val CALORIES_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:cal|cals|calorie|calories)\\b")
        val DISTANCE_REGEX = Regex(
            "(\\d+(?:\\.\\d+)?)\\s*(km|kilometers?|miles?|mi|meters?|metres?|yards?|yd|feet|ft|steps?|floors?)\\b"
        )
        val EXPLICIT_WEIGHT_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*$WEIGHT_UNITS_PATTERN\\b")
        val EXPLICIT_REPS_REGEX = Regex("(\\d+)\\s*(?:reps?|repetitions?)\\b")
        val BARE_INT_REGEX = Regex("^(\\d{1,4})$")

        val HMS_DURATION_REGEX = Regex("\\b(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?\\b")
        val HOURS_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|hr)\\b")
        val MINUTES_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:minutes?|mins?|min)\\b")
        val SECONDS_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:seconds?|secs?|sec)\\b")
        val BARE_S_DURATION_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*s\\b")
    }
}
