package com.example.gymtime.ai

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.smartlog.ParsedLoggingCommand
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.SmartLogPromptExtractor
import com.example.gymtime.smartlog.SmartLogValidator
import com.example.gymtime.smartlog.WeightUnit
import java.util.Locale
import javax.inject.Inject
import org.json.JSONObject

class MlKitSmartLogPromptExtractor @Inject constructor(
    private val aiClient: OnDeviceAiClient
) : SmartLogPromptExtractor {
    override val priority: Int = 100

    override suspend fun extract(commandText: String): ParsedLoggingCommand? {
        if (commandText.isBlank()) return null
        val result = aiClient.generateText(
            AiTextRequest(
                prompt = buildPrompt(commandText),
                temperature = 0.1f,
                topK = 8,
                maxOutputTokens = 384
            )
        )
        if (result !is AiGenerationResult.Success) return null
        return parseJson(result.value)
    }

    internal fun parseJson(raw: String): ParsedLoggingCommand? = runCatching {
        val jsonText = extractFirstBalancedJsonObject(raw) ?: return null
        val json = JSONObject(jsonText)
        val array = json.optJSONArray("sets") ?: return null
        if (array.length() == 0) return null
        // Parse up to MAX_QUEUED_SETS + 1 so >20 sets reaches SmartLogValidator's explicit
        // 20-set limit error instead of silently truncating sets 21+, while bounding memory.
        val parseCount = minOf(array.length(), SmartLogValidator.MAX_QUEUED_SETS + 1)
        val sets = buildList {
            for (index in 0 until parseCount) {
                val item = array.optJSONObject(index) ?: return null
                val draft = SetDraft(
                    weight = item.strictOptionalFloat("weight"),
                    weightUnit = item.strictOptionalEnum<WeightUnit>("weightUnit"),
                    reps = item.strictOptionalInt("reps"),
                    rpe = item.strictOptionalFloat("rpe"),
                    durationSeconds = item.strictOptionalInt("durationSeconds"),
                    distanceValue = item.strictOptionalFloat("distanceValue"),
                    distanceUnit = item.strictOptionalEnum<DistanceUnit>("distanceUnit"),
                    calories = item.strictOptionalFloat("calories"),
                    isWarmup = item.strictOptionalBoolean("isWarmup", false),
                    note = item.optionalString("note")
                )
                if (!draft.hasAnyMetric()) return null
                add(draft)
            }
        }
        if (sets.isEmpty()) return null
        ParsedLoggingCommand(
            exerciseQuery = json.optionalString("exerciseQuery"),
            sets = sets
        )
    }.getOrNull()

    private fun extractFirstBalancedJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val ch = raw[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') {
                    depth += 1
                } else if (ch == '}') {
                    depth -= 1
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun SetDraft.hasAnyMetric(): Boolean =
        weight != null ||
            reps != null ||
            rpe != null ||
            durationSeconds != null ||
            distanceValue != null ||
            calories != null

    private fun buildPrompt(commandText: String): String = """
        Extract this English workout logging command into JSON. Return only one JSON object, no markdown.
        Use this exact shape:
        {"exerciseQuery":string|null,"sets":[{"weight":number|null,"weightUnit":"POUNDS"|"KILOGRAMS"|null,"reps":integer|null,"rpe":number|null,"durationSeconds":integer|null,"distanceValue":number|null,"distanceUnit":"METERS"|"KILOMETERS"|"YARDS"|"FEET"|"MILES"|"STEPS"|"FLOORS"|null,"calories":number|null,"isWarmup":boolean,"note":string|null}]}
        Expand phrases such as 3x10 into three sets. Shared weight applies to every listed rep count. Do not double dumbbell weight. Convert spoken time to seconds, but do not convert weight or distance. Use null for anything not stated and never invent values.
        Command: ${commandText.trim()}
    """.trimIndent()

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = (opt(key) as? String)?.trim() ?: return null
        if (value.isEmpty() || value.lowercase(Locale.ROOT) in NULL_SENTINELS) return null
        return value
    }

    private fun JSONObject.strictOptionalFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toFloat().takeIf(Float::isFinite)
                ?: throw IllegalArgumentException("Invalid float for $key")
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed.lowercase(Locale.ROOT) in NULL_SENTINELS) {
                    null
                } else {
                    trimmed.toFloatOrNull()?.takeIf(Float::isFinite)
                        ?: throw IllegalArgumentException("Invalid float string for $key")
                }
            }
            else -> throw IllegalArgumentException("Invalid float type for $key")
        }
    }

    private fun JSONObject.strictOptionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Int -> raw
            is Long -> {
                if (raw !in Int.MIN_VALUE..Int.MAX_VALUE) {
                    throw IllegalArgumentException("Int overflow for $key")
                }
                raw.toInt()
            }
            is Number -> {
                val d = raw.toDouble()
                if (!d.isFinite() || d % 1.0 != 0.0 || d < Int.MIN_VALUE || d > Int.MAX_VALUE) {
                    throw IllegalArgumentException("Non-integer number for $key")
                }
                d.toInt()
            }
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed.lowercase(Locale.ROOT) in NULL_SENTINELS) {
                    null
                } else {
                    trimmed.toIntOrNull()
                        ?: throw IllegalArgumentException("Non-integer string for $key")
                }
            }
            else -> throw IllegalArgumentException("Invalid int type for $key")
        }
    }

    private fun JSONObject.strictOptionalBoolean(key: String, default: Boolean): Boolean {
        if (!has(key) || isNull(key)) return default
        return when (val raw = opt(key)) {
            is Boolean -> raw
            is String -> when (raw.trim().lowercase(Locale.ROOT)) {
                "true" -> true
                "false" -> false
                in NULL_SENTINELS, "" -> default
                else -> throw IllegalArgumentException("Invalid boolean string for $key")
            }
            else -> throw IllegalArgumentException("Invalid boolean type for $key")
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.strictOptionalEnum(key: String): T? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key) as? String ?: throw IllegalArgumentException("Invalid enum type for $key")
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.lowercase(Locale.ROOT) in NULL_SENTINELS) return null
        return enumValues<T>().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown enum value '$trimmed' for $key")
    }

    private companion object {
        val NULL_SENTINELS = setOf("null", "none", "n/a", "na", "nil", "undefined")
    }
}
