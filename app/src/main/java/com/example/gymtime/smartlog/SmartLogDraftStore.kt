package com.example.gymtime.smartlog

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SmartLogQueueSnapshot(
    val token: String,
    val exerciseId: Long,
    val exerciseName: String,
    val currentIndex: Int,
    val total: Int,
    val current: SetDraft
)

@Singleton
class SmartLogDraftStore @Inject constructor() {
    private data class Queue(
        val draft: ValidatedSmartLogDraft,
        var index: Int = 0
    )

    private val queues = mutableMapOf<String, Queue>()

    @Synchronized
    fun put(draft: ValidatedSmartLogDraft): String {
        val token = UUID.randomUUID().toString()
        queues[token] = Queue(draft)
        return token
    }

    @Synchronized
    fun peek(token: String?, exerciseId: Long): SmartLogQueueSnapshot? {
        if (token == null) return null
        val queue = queues[token] ?: return null
        if (queue.draft.exercise.id != exerciseId) return null
        val set = queue.draft.sets.getOrNull(queue.index) ?: return null
        return SmartLogQueueSnapshot(
            token = token,
            exerciseId = exerciseId,
            exerciseName = queue.draft.exercise.name,
            currentIndex = queue.index,
            total = queue.draft.sets.size,
            current = set
        )
    }

    /** Advances only after the visible set has been committed by the normal logger. */
    @Synchronized
    fun advance(token: String?, exerciseId: Long): SmartLogQueueSnapshot? {
        if (token == null) return null
        val queue = queues[token] ?: return null
        if (queue.draft.exercise.id != exerciseId) return null
        queue.index += 1
        if (queue.index >= queue.draft.sets.size) {
            queues.remove(token)
            return null
        }
        return peek(token, exerciseId)
    }

    @Synchronized
    fun discard(token: String?) {
        if (token != null) queues.remove(token)
    }
}
