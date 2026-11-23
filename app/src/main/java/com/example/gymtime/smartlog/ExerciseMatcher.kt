package com.example.gymtime.smartlog

import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class ExerciseMatcher @Inject constructor() {
    private data class ScoredCandidate(
        val candidate: ExerciseMatchCandidate,
        val normalizedName: String,
        val tokens: Set<String>
    )

    private class LevenshteinScratch {
        private var prev = IntArray(64)
        private var curr = IntArray(64)

        fun distance(first: String, second: String): Int {
            val a: String
            val b: String
            if (first.length >= second.length) {
                a = first
                b = second
            } else {
                a = second
                b = first
            }
            val needed = b.length + 1
            if (prev.size < needed) {
                prev = IntArray(needed)
                curr = IntArray(needed)
            }
            for (j in 0..b.length) prev[j] = j
            for (i in a.indices) {
                curr[0] = i + 1
                for (j in b.indices) {
                    val cost = if (a[i] == b[j]) 0 else 1
                    curr[j + 1] = minOf(
                        curr[j] + 1,
                        prev[j + 1] + 1,
                        prev[j] + cost
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }
    }

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
        if (normalizedQuery.isBlank()) return ExerciseMatchResult.NoMatch

        val queryTokens = normalizedQuery.split(' ').toSet()
        val scratch = LevenshteinScratch()

        val scored = rows.mapNotNull { row ->
            val normalizedName = normalize(row.exercise.name)
            if (normalizedName.isBlank()) return@mapNotNull null
            val candidateTokens = normalizedName.split(' ').toSet()
            ScoredCandidate(
                candidate = ExerciseMatchCandidate(
                    exercise = row.exercise,
                    score = similarity(normalizedQuery, queryTokens, normalizedName, candidateTokens, scratch),
                    allTimeSetCount = row.allTimeSetCount,
                    lastUsedMs = row.lastUsedMs
                ),
                normalizedName = normalizedName,
                tokens = candidateTokens
            )
        }.sortedWith(
            compareByDescending<ScoredCandidate> { it.candidate.score }
                .thenByDescending { it.candidate.allTimeSetCount }
                .thenByDescending { it.candidate.lastUsedMs ?: Long.MIN_VALUE }
                .thenBy { it.candidate.exercise.name.lowercase(Locale.US) }
        )

        val top = scored.firstOrNull() ?: return ExerciseMatchResult.NoMatch
        val runnerUp = scored.getOrNull(1)
        val isExact = top.normalizedName == normalizedQuery
        val singleQueryToken = if (queryTokens.size == 1) queryTokens.first() else null
        val isAmbiguousSingleToken = !isExact &&
            singleQueryToken != null &&
            scored.count { singleQueryToken in it.tokens } >= 2
        val hasClearLead = runnerUp == null || top.candidate.score - runnerUp.candidate.score >= AUTO_MATCH_LEAD

        return when {
            isExact || (!isAmbiguousSingleToken && top.candidate.score >= AUTO_MATCH_THRESHOLD && hasClearLead) ->
                ExerciseMatchResult.Matched(top.candidate)
            top.candidate.score >= MIN_SUGGESTION_SCORE ->
                ExerciseMatchResult.NeedsConfirmation(scored.take(3).map { it.candidate })
            else -> ExerciseMatchResult.NoMatch
        }
    }

    internal fun normalize(value: String): String {
        val folded = if (value.any { it.code > 0x7F }) {
            Normalizer.normalize(value, Normalizer.Form.NFD).replace(COMBINING_MARKS_REGEX, "")
        } else {
            value
        }
        val withoutMarks = folded
            .lowercase(Locale.US)
            .replace("&", " and ")
            .replace(NON_ALNUM_REGEX, " ")
            .trim()
        if (withoutMarks.isEmpty()) return ""
        return withoutMarks.split(WHITESPACE_REGEX)
            .flatMap { token -> (ALIASES[token] ?: token).split(' ') }
            .map { singularize(it) }
            .joinToString(" ")
    }

    private fun singularize(token: String): String = when {
        token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
        (token.endsWith("sses") ||
            token.endsWith("shes") ||
            token.endsWith("ches") ||
            token.endsWith("xes") ||
            token.endsWith("zes")) && token.length > 4 -> token.dropLast(2)
        token.endsWith("s") && !token.endsWith("ss") && token.length > 3 -> token.dropLast(1)
        else -> token
    }

    private fun similarity(
        a: String,
        aTokens: Set<String>,
        b: String,
        bTokens: Set<String>,
        scratch: LevenshteinScratch
    ): Float {
        if (a == b) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        val intersection = aTokens.intersect(bTokens).size.toFloat()
        val containment = intersection / minOf(aTokens.size, bTokens.size).coerceAtLeast(1)
        val dice = (2f * intersection) / (aTokens.size + bTokens.size).coerceAtLeast(1)
        val maxLength = maxOf(a.length, b.length)
        val charSimilarity = 1f - scratch.distance(a, b).toFloat() / maxLength
        return maxOf(charSimilarity, 0.55f * containment + 0.30f * dice + 0.15f * charSimilarity)
            .coerceIn(0f, 1f)
    }

    companion object {
        const val AUTO_MATCH_THRESHOLD = 0.78f
        const val AUTO_MATCH_LEAD = 0.10f
        const val MIN_SUGGESTION_SCORE = 0.35f

        private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ALIASES = mapOf(
            "db" to "dumbbell",
            "bb" to "barbell",
            "ohp" to "overhead press",
            "rdl" to "romanian deadlift",
            "pullups" to "pull up",
            "pullup" to "pull up",
            "pushups" to "push up",
            "pushup" to "push up"
        )
    }
}
