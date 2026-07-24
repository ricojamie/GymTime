package com.example.gymtime.ai

import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.smartlog.ParsedLoggingCommand
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.SmartLogPromptExtractor
import com.example.gymtime.smartlog.WeightUnit
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

    private fun parseJson(raw: String): ParsedLoggingCommand? = runCatching {
        val jsonText = raw.substringAfter('{', missingDelimiterValue = "")
            .substringBeforeLast('}', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { "{$it}" }
            ?: return null
        val json = JSONObject(jsonText)
        val array = json.optJSONArray("sets") ?: return null
        val sets = buildList {
            for (index in 0 until minOf(array.length(), MAX_SETS)) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    SetDraft(
                        weight = item.optionalFloat("weight"),
                        weightUnit = item.optionalEnum<WeightUnit>("weightUnit"),
                        reps = item.optionalInt("reps"),
                        rpe = item.optionalFloat("rpe"),
                        durationSeconds = item.optionalInt("durationSeconds"),
                        distanceValue = item.optionalFloat("distanceValue"),
                        distanceUnit = item.optionalEnum<DistanceUnit>("distanceUnit"),
                        calories = item.optionalFloat("calories"),
                        isWarmup = item.optBoolean("isWarmup", false),
                        note = item.optionalString("note")
                    )
                )
            }
        }
        if (sets.isEmpty()) return null
        ParsedLoggingCommand(
            exerciseQuery = json.optionalString("exerciseQuery"),
            sets = sets
        )
    }.getOrNull()

    private fun buildPrompt(commandText: String): String = """
        Extract this English workout logging command into JSON. Return only one JSON object, no markdown.
        Use this exact shape:
        {"exerciseQuery":string|null,"sets":[{"weight":number|null,"weightUnit":"POUNDS"|"KILOGRAMS"|null,"reps":integer|null,"rpe":number|null,"durationSeconds":integer|null,"distanceValue":number|null,"distanceUnit":"METERS"|"KILOMETERS"|"YARDS"|"FEET"|"MILES"|"STEPS"|"FLOORS"|null,"calories":number|null,"isWarmup":boolean,"note":string|null}]}
        Expand phrases such as 3x10 into three sets. Shared weight applies to every listed rep count. Do not double dumbbell weight. Convert spoken time to seconds, but do not convert weight or distance. Use null for anything not stated and never invent values.
        Command: ${commandText.trim()}
    """.trimIndent()

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.optionalFloat(key: String): Float? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)?.toFloat()

    private fun JSONObject.optionalInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private inline fun <reified T : Enum<T>> JSONObject.optionalEnum(key: String): T? {
        val value = optionalString(key) ?: return null
        return enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    private companion object {
        const val MAX_SETS = 20
    }
}
