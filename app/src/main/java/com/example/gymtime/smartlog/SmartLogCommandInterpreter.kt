package com.example.gymtime.smartlog

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SmartLogCommandInterpreter @Inject constructor(
    extractors: Set<@JvmSuppressWildcards SmartLogPromptExtractor>
) {
    private val orderedExtractors = extractors.sortedByDescending { it.priority }

    suspend fun extract(commandText: String): ParsedLoggingCommand? {
        for (extractor in orderedExtractors) {
            val result = try {
                extractor.extract(commandText)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (result != null && result.sets.isNotEmpty()) return result
        }
        return null
    }
}
