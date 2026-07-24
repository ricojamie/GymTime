package com.example.gymtime.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NarrativeRequest(
    val kind: NarrativeKind,
    val subjectKey: String,
    val subjectStartEpochMs: Long?,
    val workoutId: Long? = null,
    val sourceFingerprint: String,
    val promptVersion: Int,
    val prompt: String,
    val fallbackText: String,
    val validationRules: NarrativeValidationRules,
    val maxOutputTokens: Int = 96
) {
    init {
        require(subjectKey.isNotBlank()) { "subjectKey cannot be blank" }
        require(sourceFingerprint.isNotBlank()) { "sourceFingerprint cannot be blank" }
        require(promptVersion > 0) { "promptVersion must be positive" }
        require(prompt.isNotBlank()) { "prompt cannot be blank" }
        require(fallbackText.isNotBlank()) { "fallbackText cannot be blank" }
        require(maxOutputTokens > 0) { "maxOutputTokens must be positive" }
        require(kind == NarrativeKind.POST_WORKOUT || workoutId == null) {
            "Only post-workout narratives can reference a workout"
        }
        require(kind != NarrativeKind.POST_WORKOUT || workoutId != null) {
            "Post-workout narratives must reference their workout"
        }
    }
}

data class NarrativeResult(
    val text: String,
    val generatorType: NarrativeGeneratorType,
    val modelName: String? = null,
    val fromCache: Boolean = false
)

@Singleton
class NarrativeGenerator @Inject constructor(
    private val aiClient: OnDeviceAiClient,
    private val repository: GeneratedNarrativeRepository,
    private val validator: NarrativeOutputValidator
) {
    fun generate(request: NarrativeRequest): Flow<NarrativeResult> = flow {
        val cached = repository.getValid(
            kind = request.kind,
            subjectKey = request.subjectKey,
            sourceFingerprint = request.sourceFingerprint,
            promptVersion = request.promptVersion
        )
        if (cached != null) {
            emit(cached.toResult(fromCache = true))
            if (cached.generatorType == NarrativeGeneratorType.GEMINI_NANO) return@flow
        } else {
            val fallback = repository.save(
                kind = request.kind,
                subjectKey = request.subjectKey,
                workoutId = request.workoutId,
                subjectStartEpochMs = request.subjectStartEpochMs,
                sourceFingerprint = request.sourceFingerprint,
                promptVersion = request.promptVersion,
                text = request.fallbackText,
                generatorType = NarrativeGeneratorType.TEMPLATE
            )
            emit(fallback.toResult(fromCache = false))
        }

        pruneOldPeriodicEntries(request.kind)
        if (aiClient.refreshCapability() !is AiCapability.Ready) return@flow

        val generated = aiClient.generateText(
            AiTextRequest(
                prompt = request.prompt,
                temperature = 0.2f,
                topK = 10,
                maxOutputTokens = request.maxOutputTokens
            )
        )
        if (generated !is AiGenerationResult.Success) return@flow

        val validated = validator.validate(generated.value, request.validationRules)
        if (validated !is NarrativeValidationResult.Valid) return@flow

        val nano = repository.save(
            kind = request.kind,
            subjectKey = request.subjectKey,
            workoutId = request.workoutId,
            subjectStartEpochMs = request.subjectStartEpochMs,
            sourceFingerprint = request.sourceFingerprint,
            promptVersion = request.promptVersion,
            text = validated.text,
            generatorType = NarrativeGeneratorType.GEMINI_NANO,
            modelName = generated.modelName
        )
        emit(nano.toResult(fromCache = false))
    }

    private suspend fun pruneOldPeriodicEntries(kind: NarrativeKind) {
        val retentionMs = when (kind) {
            NarrativeKind.MONTHLY_REPORT -> MONTHLY_RETENTION_MS
            NarrativeKind.WEEKLY_ANALYTICS -> WEEKLY_RETENTION_MS
            NarrativeKind.POST_WORKOUT -> return
        }
        repository.prunePeriodicBefore(kind, System.currentTimeMillis() - retentionMs)
    }

    private fun CachedNarrative.toResult(fromCache: Boolean) = NarrativeResult(
        text = text,
        generatorType = generatorType,
        modelName = modelName,
        fromCache = fromCache
    )

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val MONTHLY_RETENTION_MS = 550L * DAY_MS
        const val WEEKLY_RETENTION_MS = 84L * DAY_MS
    }
}
