package com.example.gymtime.ui.exercise

import android.os.SystemClock
import com.example.gymtime.data.db.entity.Set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LoggerCelebration(
    val id: Long,
    val exerciseName: String,
    val set: Set,
    val recordLabels: List<String>,
    val expiresAt: Long
)

/** A brief celebration follows automatic superset navigation without delaying logging. */
@Singleton
class LoggerCelebrationState @Inject constructor() {
    private val _celebration = MutableStateFlow<LoggerCelebration?>(null)
    val celebration = _celebration.asStateFlow()
    private var sequence = 0L
    private var hapticClaimedFor: Long? = null

    fun celebrate(exerciseName: String, set: Set, recordLabels: List<String>) {
        if (recordLabels.isEmpty()) return
        _celebration.value = LoggerCelebration(++sequence, exerciseName, set, recordLabels, SystemClock.elapsedRealtime() + 4_000L)
    }

    fun claimHaptic(id: Long): Boolean {
        val current = _celebration.value ?: return false
        if (current.id != id || current.expiresAt <= SystemClock.elapsedRealtime() || hapticClaimedFor == id) return false
        hapticClaimedFor = id
        return true
    }

    fun dismiss(id: Long) {
        if (_celebration.value?.id == id) _celebration.value = null
    }
}
