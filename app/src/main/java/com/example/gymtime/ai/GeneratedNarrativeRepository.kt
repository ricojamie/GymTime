package com.example.gymtime.ai

import com.example.gymtime.data.db.dao.GeneratedNarrativeDao
import com.example.gymtime.data.db.entity.GeneratedNarrative
import javax.inject.Inject
import javax.inject.Singleton

enum class NarrativeKind {
    MONTHLY_REPORT,
    POST_WORKOUT,
    WEEKLY_ANALYTICS
}

enum class NarrativeGeneratorType {
    TEMPLATE,
    GEMINI_NANO
}

data class CachedNarrative(
    val kind: NarrativeKind,
    val subjectKey: String,
    val sourceFingerprint: String,
    val promptVersion: Int,
    val text: String,
    val generatorType: NarrativeGeneratorType,
    val modelName: String?,
    val generatedAtEpochMs: Long
)

@Singleton
class GeneratedNarrativeRepository @Inject constructor(
    private val dao: GeneratedNarrativeDao
) {
    suspend fun getLatest(kind: NarrativeKind, subjectKey: String): CachedNarrative? {
        return dao.get(kind.name, subjectKey)?.toDomainOrNull()
    }

    suspend fun getValid(
        kind: NarrativeKind,
        subjectKey: String,
        sourceFingerprint: String,
        promptVersion: Int
    ): CachedNarrative? {
        val entity = dao.get(kind.name, subjectKey) ?: return null
        if (entity.sourceFingerprint != sourceFingerprint || entity.promptVersion != promptVersion) return null
        return entity.toDomainOrNull()
    }

    suspend fun save(
        kind: NarrativeKind,
        subjectKey: String,
        workoutId: Long?,
        subjectStartEpochMs: Long?,
        sourceFingerprint: String,
        promptVersion: Int,
        text: String,
        generatorType: NarrativeGeneratorType,
        modelName: String? = null,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): CachedNarrative {
        require(subjectKey.isNotBlank()) { "subjectKey cannot be blank" }
        require(sourceFingerprint.isNotBlank()) { "sourceFingerprint cannot be blank" }
        require(promptVersion > 0) { "promptVersion must be positive" }
        require(text.isNotBlank()) { "text cannot be blank" }
        require(kind == NarrativeKind.POST_WORKOUT || workoutId == null) {
            "Only post-workout narratives can reference a workout"
        }
        require(kind != NarrativeKind.POST_WORKOUT || workoutId != null) {
            "Post-workout narratives must reference their workout"
        }

        val entity = GeneratedNarrative(
            kind = kind.name,
            subjectKey = subjectKey,
            workoutId = workoutId,
            subjectStartEpochMs = subjectStartEpochMs,
            sourceFingerprint = sourceFingerprint,
            promptVersion = promptVersion,
            modelName = modelName,
            generatorType = generatorType.name,
            text = text.trim(),
            generatedAtEpochMs = generatedAtEpochMs
        )
        dao.upsert(entity)
        return checkNotNull(entity.toDomainOrNull())
    }

    suspend fun delete(kind: NarrativeKind, subjectKey: String) {
        dao.delete(kind.name, subjectKey)
    }

    suspend fun prunePeriodicBefore(kind: NarrativeKind, cutoffEpochMs: Long) {
        require(kind != NarrativeKind.POST_WORKOUT) { "Workout narratives are pruned by cascade delete" }
        dao.deletePeriodicBefore(kind.name, cutoffEpochMs)
    }

    private fun GeneratedNarrative.toDomainOrNull(): CachedNarrative? {
        val parsedKind = runCatching { NarrativeKind.valueOf(kind) }.getOrNull() ?: return null
        val parsedGenerator = runCatching { NarrativeGeneratorType.valueOf(generatorType) }.getOrNull() ?: return null
        return CachedNarrative(
            kind = parsedKind,
            subjectKey = subjectKey,
            sourceFingerprint = sourceFingerprint,
            promptVersion = promptVersion,
            text = text,
            generatorType = parsedGenerator,
            modelName = modelName,
            generatedAtEpochMs = generatedAtEpochMs
        )
    }
}
