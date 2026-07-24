package com.example.gymtime.ai

import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

data class NarrativeValidationRules(
    val maxWords: Int,
    val maxSentences: Int,
    val allowedNumbers: Set<String> = emptySet(),
    val allowedNames: Set<String> = emptySet(),
    val knownNames: Set<String> = emptySet()
) {
    init {
        require(maxWords > 0) { "maxWords must be positive" }
        require(maxSentences > 0) { "maxSentences must be positive" }
    }
}

sealed interface NarrativeValidationResult {
    data class Valid(val text: String) : NarrativeValidationResult
    data class Invalid(val reason: String) : NarrativeValidationResult
}

class NarrativeOutputValidator @Inject constructor() {
    fun validate(output: String, rules: NarrativeValidationRules): NarrativeValidationResult {
        val text = output.replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) return NarrativeValidationResult.Invalid("Narrative is blank")
        if (text.any { it.isISOControl() }) {
            return NarrativeValidationResult.Invalid("Narrative contains control characters")
        }

        val wordCount = WORD.findAll(text).count()
        if (wordCount > rules.maxWords) {
            return NarrativeValidationResult.Invalid("Narrative exceeds ${rules.maxWords} words")
        }

        val sentenceCount = SENTENCE_END.findAll(text).count().coerceAtLeast(1)
        if (sentenceCount > rules.maxSentences) {
            return NarrativeValidationResult.Invalid("Narrative exceeds ${rules.maxSentences} sentences")
        }

        val allowedNumbers = rules.allowedNumbers.map(::normalizeNumber).toSet()
        val unsupportedNumber = NUMBER.findAll(text)
            .map { normalizeNumber(it.value) }
            .firstOrNull { it !in allowedNumbers }
        if (unsupportedNumber != null) {
            return NarrativeValidationResult.Invalid("Narrative contains an unsupported number")
        }

        val allowedNames = rules.allowedNames.map { it.lowercase(Locale.ROOT) }.toSet()
        val unsupportedName = rules.knownNames
            .sortedByDescending(String::length)
            .firstOrNull { known ->
                known.isNotBlank() &&
                    text.contains(known, ignoreCase = true) &&
                    known.lowercase(Locale.ROOT) !in allowedNames
            }
        if (unsupportedName != null) {
            return NarrativeValidationResult.Invalid("Narrative contains an unsupported name")
        }

        return NarrativeValidationResult.Valid(text)
    }

    private fun normalizeNumber(value: String): String {
        val withoutFormatting = value.lowercase(Locale.ROOT).replace(",", "").removePrefix("+")
        val suffix = if (withoutFormatting.endsWith('%')) "%" else ""
        val numeric = withoutFormatting.removeSuffix("%")
        val normalized = numeric.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: numeric
        return normalized + suffix
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val WORD = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")
        val SENTENCE_END = Regex("[.!?]+")
        val NUMBER = Regex("(?<![\\p{L}\\d])[-+]?\\d[\\d,]*(?:\\.\\d+)?%?")
    }
}

object NarrativeFingerprint {
    fun sha256(canonicalFacts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalFacts.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
