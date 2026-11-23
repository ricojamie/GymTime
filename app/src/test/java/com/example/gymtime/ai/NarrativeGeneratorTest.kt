package com.example.gymtime.ai

import com.example.gymtime.data.db.dao.GeneratedNarrativeDao
import com.example.gymtime.data.db.entity.GeneratedNarrative
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeGeneratorTest {

    @Test
    fun `invalid cached gemini nano narrative is invalidated and regenerated`() = runTest {
        val dao = FakeGeneratedNarrativeDao()
        val repo = GeneratedNarrativeRepository(dao)
        val aiClient = FakeOnDeviceAiClient(
            initialCapability = AiCapability.Ready("nano-1"),
            response = AiGenerationResult.Success("Chest led the week.", "nano-1")
        )
        val generator = NarrativeGenerator(aiClient, repo, NarrativeOutputValidator())

        val request = monthlyRequest(
            fingerprint = "fp-1",
            fallbackText = "Chest was your top muscle this month.",
            allowedNames = setOf("Chest"),
            knownNames = setOf("Chest", "Back")
        )
        // Seed invalid cached GEMINI_NANO entry mentioning disallowed "Back"
        repo.save(
            kind = request.kind,
            subjectKey = request.subjectKey,
            workoutId = null,
            subjectStartEpochMs = request.subjectStartEpochMs,
            sourceFingerprint = request.sourceFingerprint,
            promptVersion = request.promptVersion,
            text = "Back dominated this month.",
            generatorType = NarrativeGeneratorType.GEMINI_NANO,
            modelName = "nano-1"
        )

        val emissions = generator.generate(request).toList()

        assertEquals(2, emissions.size)
        assertEquals(NarrativeGeneratorType.TEMPLATE, emissions[0].generatorType)
        assertEquals("Chest was your top muscle this month.", emissions[0].text)
        assertEquals(NarrativeGeneratorType.GEMINI_NANO, emissions[1].generatorType)
        assertEquals("Chest led the week.", emissions[1].text)
    }

    @Test
    fun `valid cached gemini nano hit still prunes old periodic entries`() = runTest {
        val dao = FakeGeneratedNarrativeDao()
        val repo = GeneratedNarrativeRepository(dao)
        val aiClient = FakeOnDeviceAiClient(
            initialCapability = AiCapability.Ready("nano-1"),
            response = AiGenerationResult.Success("Unused", "nano-1")
        )
        val generator = NarrativeGenerator(aiClient, repo, NarrativeOutputValidator())
        val request = monthlyRequest(
            fingerprint = "fp-2",
            fallbackText = "Chest template.",
            allowedNames = setOf("Chest"),
            knownNames = setOf("Chest", "Back")
        )
        repo.save(
            kind = request.kind,
            subjectKey = request.subjectKey,
            workoutId = null,
            subjectStartEpochMs = request.subjectStartEpochMs,
            sourceFingerprint = request.sourceFingerprint,
            promptVersion = request.promptVersion,
            text = "Chest led this month.",
            generatorType = NarrativeGeneratorType.GEMINI_NANO,
            modelName = "nano-1"
        )

        val emissions = generator.generate(request).toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.single().fromCache)
        assertEquals(1, dao.pruneCount.get())
        assertEquals(0, aiClient.generateCalls.get())
    }

    @Test
    fun `cached template refreshes when fallbackText changes`() = runTest {
        val dao = FakeGeneratedNarrativeDao()
        val repo = GeneratedNarrativeRepository(dao)
        val aiClient = FakeOnDeviceAiClient(
            initialCapability = AiCapability.Unavailable,
            response = AiGenerationResult.Failure(AiError.NOT_AVAILABLE)
        )
        val generator = NarrativeGenerator(aiClient, repo, NarrativeOutputValidator())

        val initialRequest = monthlyRequest(
            fingerprint = "fp-3",
            fallbackText = "Old template text.",
            allowedNames = setOf("Chest"),
            knownNames = setOf("Chest")
        )
        repo.save(
            kind = initialRequest.kind,
            subjectKey = initialRequest.subjectKey,
            workoutId = null,
            subjectStartEpochMs = initialRequest.subjectStartEpochMs,
            sourceFingerprint = initialRequest.sourceFingerprint,
            promptVersion = initialRequest.promptVersion,
            text = "Old template text.",
            generatorType = NarrativeGeneratorType.TEMPLATE
        )

        val updatedRequest = initialRequest.copy(fallbackText = "Updated template text.")
        val emissions = generator.generate(updatedRequest).toList()

        assertEquals(1, emissions.size)
        assertEquals("Updated template text.", emissions.single().text)
        assertEquals("Updated template text.", repo.getLatest(NarrativeKind.MONTHLY_REPORT, "2026-11")?.text)
    }

    @Test
    fun `stale fingerprint cannot overwrite newer fingerprint in repository`() = runTest {
        val dao = FakeGeneratedNarrativeDao()
        val repo = GeneratedNarrativeRepository(dao)

        repo.saveTemplateIfNotUpgraded(
            kind = NarrativeKind.MONTHLY_REPORT,
            subjectKey = "2026-11",
            workoutId = null,
            subjectStartEpochMs = 1_000L,
            sourceFingerprint = "fp-new",
            promptVersion = 1,
            text = "Newer template."
        )

        val staleWrite = repo.saveGeneratedIfCurrent(
            kind = NarrativeKind.MONTHLY_REPORT,
            subjectKey = "2026-11",
            workoutId = null,
            subjectStartEpochMs = 1_000L,
            sourceFingerprint = "fp-old",
            promptVersion = 1,
            text = "Stale nano text.",
            modelName = "nano-1"
        )

        assertNull(staleWrite)
        assertEquals("Newer template.", repo.getLatest(NarrativeKind.MONTHLY_REPORT, "2026-11")?.text)
    }

    private fun monthlyRequest(
        fingerprint: String,
        fallbackText: String,
        allowedNames: Set<String>,
        knownNames: Set<String>
    ) = NarrativeRequest(
        kind = NarrativeKind.MONTHLY_REPORT,
        subjectKey = "2026-11",
        subjectStartEpochMs = 1_000L,
        workoutId = null,
        sourceFingerprint = fingerprint,
        promptVersion = 1,
        prompt = "Summarize month",
        fallbackText = fallbackText,
        validationRules = NarrativeValidationRules(
            maxWords = 30,
            maxSentences = 2,
            allowedNames = allowedNames,
            knownNames = knownNames
        )
    )

    private class FakeGeneratedNarrativeDao : GeneratedNarrativeDao {
        private val store = ConcurrentHashMap<Pair<String, String>, GeneratedNarrative>()
        val pruneCount = AtomicInteger(0)

        override suspend fun get(kind: String, subjectKey: String): GeneratedNarrative? =
            store[kind to subjectKey]

        override suspend fun upsert(narrative: GeneratedNarrative) {
            store[narrative.kind to narrative.subjectKey] = narrative
        }

        override suspend fun delete(kind: String, subjectKey: String) {
            store.remove(kind to subjectKey)
        }

        override suspend fun deletePeriodicBefore(kind: String, cutoffEpochMs: Long) {
            pruneCount.incrementAndGet()
        }
    }

    private class FakeOnDeviceAiClient(
        initialCapability: AiCapability,
        private val response: AiGenerationResult<String>
    ) : OnDeviceAiClient {
        private val _capability = MutableStateFlow(initialCapability)
        override val capability: StateFlow<AiCapability> = _capability.asStateFlow()
        val generateCalls = AtomicInteger(0)

        override suspend fun refreshCapability(): AiCapability = _capability.value

        override fun downloadModel(): Flow<AiDownloadState> = emptyFlow()

        override suspend fun warmUp(): AiGenerationResult<Unit> = AiGenerationResult.Success(Unit)

        override suspend fun generateText(request: AiTextRequest): AiGenerationResult<String> {
            generateCalls.incrementAndGet()
            return response
        }
    }
}
