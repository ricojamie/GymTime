package com.example.gymtime.smartlog

import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class ExerciseMatcher @Inject constructor() {
    fun match(
        query: String?,
        rows: List<ExerciseUsageRow>,
        currentExercise: Exercise? = null
    ): ExerciseMatchResult {
        if (query.isNullOrBlank()) {
            return currentExercise?.let {
                ExerciseMatchResult.Matched(ExerciseMatchCandidate(it, 1f))
            } ?: ExerciseMatchResult.NoMatch
        }
        if (rows.isEmpty()) return ExerciseMatchResult.NoMatch

        val normalizedQuery = normalize(query)
        val candidates = rows.map { row ->
            val normalizedName = normalize(row.exercise.name)
            ExerciseMatchCandidate(
                exercise = row.exercise,
                score = similarity(normalizedQuery, normalizedName),
                allTimeSetCount = row.allTimeSetCount,
                lastUsedMs = row.lastUsedMs
            )
        }.sortedWith(
            compareByDescending<ExerciseMatchCandidate> { it.score }
                .thenByDescending { it.allTimeSetCount }
                .thenByDescending { it.lastUsedMs ?: Long.MIN_VALUE }
                .thenBy { it.exercise.name.lowercase(Locale.US) }
        )

        val top = candidates.first()
        val runnerUp = candidates.getOrNull(1)
        val isExact = normalize(top.exercise.name) == normalizedQuery
        val hasClearLead = runnerUp == null || top.score - runnerUp.score >= AUTO_MATCH_LEAD
        return when {
            isExact || (top.score >= AUTO_MATCH_THRESHOLD && hasClearLead) -> ExerciseMatchResult.Matched(top)
            top.score >= MIN_SUGGESTION_SCORE -> ExerciseMatchResult.NeedsConfirmation(candidates.take(3))
            else -> ExerciseMatchResult.NoMatch
        }
    }

    internal fun normalize(value: String): String {
        val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.US)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val aliases = mapOf(
            "db" to "dumbbell",
            "bb" to "barbell",
            "ohp" to "overhead press",
            "rdl" to "romanian deadlift",
            "pullups" to "pull up",
            "pullup" to "pull up",
            "pushups" to "push up",
            "pushup" to "push up"
        )
        return withoutMarks.split(Regex("\\s+"))
            .flatMap { token -> (aliases[token] ?: token).split(' ') }
            .map { singularize(it) }
            .joinToString(" ")
    }

    private fun singularize(token: String): String = when {
        token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
        token.endsWith("s") && !token.endsWith("ss") && token.length > 3 -> token.dropLast(1)
        else -> token
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        val aTokens = a.split(' ').toSet()
        val bTokens = b.split(' ').toSet()
        val intersection = aTokens.intersect(bTokens).size.toFloat()
        val containment = intersection / minOf(aTokens.size, bTokens.size).coerceAtLeast(1)
        val dice = (2f * intersection) / (aTokens.size + bTokens.size).coerceAtLeast(1)
        val maxLength = maxOf(a.length, b.length)
        val charSimilarity = 1f - levenshtein(a, b).toFloat() / maxLength
        return maxOf(charSimilarity, 0.55f * containment + 0.30f * dice + 0.15f * charSimilarity)
            .coerceIn(0f, 1f)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    companion object {
        const val AUTO_MATCH_THRESHOLD = 0.78f
        const val AUTO_MATCH_LEAD = 0.10f
        const val MIN_SUGGESTION_SCORE = 0.35f
    }
}
