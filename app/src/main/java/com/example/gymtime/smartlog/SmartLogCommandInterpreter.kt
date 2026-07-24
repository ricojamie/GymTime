package com.example.gymtime.smartlog

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartLogCommandInterpreter @Inject constructor(
    private val extractors: Set<@JvmSuppressWildcards SmartLogPromptExtractor>
) {
    suspend fun extract(commandText: String): ParsedLoggingCommand? {
        for (extractor in extractors.sortedByDescending { it.priority }) {
            val result = runCatching { extractor.extract(commandText) }.getOrNull()
            if (result != null && result.sets.isNotEmpty()) return result
        }
        return null
    }
}
