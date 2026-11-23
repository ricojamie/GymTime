package com.example.gymtime.ai

import com.example.gymtime.data.db.dao.GeneratedNarrativeDao
import com.example.gymtime.data.db.entity.GeneratedNarrative
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(kind: NarrativeKind, subjectKey: String): Mutex =
        keyMutexes.computeIfAbsent("${kind.name}:$subjectKey") { Mutex() }

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
        validateRequestFields(kind, subjectKey, workoutId, sourceFingerprint, promptVersion, text)
        val entity = buildEntity(
            kind = kind,
            subjectKey = subjectKey,
            workoutId = workoutId,
            subjectStartEpochMs = subjectStartEpochMs,
            sourceFingerprint = sourceFingerprint,
            promptVersion = promptVersion,
            text = text,
            generatorType = generatorType,
            modelName = modelName,
            generatedAtEpochMs = generatedAtEpochMs
        )
        return mutexFor(kind, subjectKey).withLock {
            dao.upsert(entity)
            checkNotNull(entity.toDomainOrNull())
        }
    }

    /**
     * Atomically upserts a template fallback while avoiding downgrading an existing valid
     * GEMINI_NANO entry for the same fingerprint/version.
     */
    suspend fun saveTemplateIfNotUpgraded(
        kind: NarrativeKind,
        subjectKey: String,
        workoutId: Long?,
        subjectStartEpochMs: Long?,
        sourceFingerprint: String,
        promptVersion: Int,
        text: String,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): CachedNarrative {
        validateRequestFields(kind, subjectKey, workoutId, sourceFingerprint, promptVersion, text)
        val entity = buildEntity(
            kind = kind,
            subjectKey = subjectKey,
            workoutId = workoutId,
            subjectStartEpochMs = subjectStartEpochMs,
            sourceFingerprint = sourceFingerprint,
            promptVersion = promptVersion,
            text = text,
            generatorType = NarrativeGeneratorType.TEMPLATE,
            modelName = null,
            generatedAtEpochMs = generatedAtEpochMs
        )
        return mutexFor(kind, subjectKey).withLock {
            val existing = dao.get(kind.name, subjectKey)?.toDomainOrNull()
            if (existing != null &&
                existing.sourceFingerprint == sourceFingerprint &&
                existing.promptVersion == promptVersion &&
                existing.generatorType == NarrativeGeneratorType.GEMINI_NANO
            ) {
                existing
            } else {
                dao.upsert(entity)
                checkNotNull(entity.toDomainOrNull())
            }
        }
    }

    /**
     * Atomically saves a GEMINI_NANO narrative only if no newer fingerprint/promptVersion has
     * superseded this request in the database while inference was in flight.
     */
    suspend fun saveGeneratedIfCurrent(
        kind: NarrativeKind,
        subjectKey: String,
        workoutId: Long?,
        subjectStartEpochMs: Long?,
        sourceFingerprint: String,
        promptVersion: Int,
        text: String,
        modelName: String? = null,
        generatedAtEpochMs: Long = System.currentTimeMillis()
    ): CachedNarrative? {
        validateRequestFields(kind, subjectKey, workoutId, sourceFingerprint, promptVersion, text)
        val entity = buildEntity(
            kind = kind,
            subjectKey = subjectKey,
            workoutId = workoutId,
            subjectStartEpochMs = subjectStartEpochMs,
            sourceFingerprint = sourceFingerprint,
            promptVersion = promptVersion,
            text = text,
            generatorType = NarrativeGeneratorType.GEMINI_NANO,
            modelName = modelName,
            generatedAtEpochMs = generatedAtEpochMs
        )
        return mutexFor(kind, subjectKey).withLock {
            val existing = dao.get(kind.name, subjectKey)
            if (existing != null &&
                (existing.sourceFingerprint != sourceFingerprint || existing.promptVersion != promptVersion)
            ) {
                null
            } else {
                dao.upsert(entity)
                checkNotNull(entity.toDomainOrNull())
            }
        }
    }

    suspend fun delete(kind: NarrativeKind, subjectKey: String) {
        mutexFor(kind, subjectKey).withLock {
            dao.delete(kind.name, subjectKey)
        }
    }

    suspend fun deleteIfFingerprintMatches(
        kind: NarrativeKind,
        subjectKey: String,
        sourceFingerprint: String,
        promptVersion: Int
    ) {
        mutexFor(kind, subjectKey).withLock {
            val existing = dao.get(kind.name, subjectKey) ?: return@withLock
            if (existing.sourceFingerprint == sourceFingerprint && existing.promptVersion == promptVersion) {
                dao.delete(kind.name, subjectKey)
            }
        }
    }

    suspend fun prunePeriodicBefore(kind: NarrativeKind, cutoffEpochMs: Long) {
        require(kind != NarrativeKind.POST_WORKOUT) { "Workout narratives are pruned by cascade delete" }
        dao.deletePeriodicBefore(kind.name, cutoffEpochMs)
    }

    private fun validateRequestFields(
        kind: NarrativeKind,
        subjectKey: String,
        workoutId: Long?,
        sourceFingerprint: String,
        promptVersion: Int,
        text: String
    ) {
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
    }

    private fun buildEntity(
        kind: NarrativeKind,
        subjectKey: String,
        workoutId: Long?,
        subjectStartEpochMs: Long?,
        sourceFingerprint: String,
        promptVersion: Int,
        text: String,
        generatorType: NarrativeGeneratorType,
        modelName: String?,
        generatedAtEpochMs: Long
    ) = GeneratedNarrative(
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
