package com.example.gymtime.data

import android.util.Log
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.util.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State for the Volume Orb gamification feature.
 * Tracks weekly volume progress compared to last week.
 */
data class VolumeOrbState(
    val lastWeekVolume: Float = 0f,
    val currentWeekVolume: Float = 0f,
    val progressPercent: Float = 0f,  // 0.0 to 1.5+ (can overflow past 100%)
    val isFirstWeek: Boolean = true,   // True if no previous week data
    val hasOverflowed: Boolean = false, // True when current > last (triggers celebration once)
    val justOverflowed: Boolean = false // True only on the transition to overflow (for animation trigger)
)

/**
 * Repository for managing Volume Orb state.
 * Singleton to share state across Home, Logging, and Post-Workout screens.
 */
@Singleton
class VolumeOrbRepository @Inject constructor(
    private val setDao: SetDao
) {
    private val _orbState = MutableStateFlow(VolumeOrbState())
    val orbState: StateFlow<VolumeOrbState> = _orbState.asStateFlow()

    private val mutex = Mutex()

    // Track if we've already triggered the overflow celebration this week
    private var overflowCelebrationTriggered = false
    private var lastCurrentWeekStartMs: Long = 0L

    /**
     * Refresh the orb state from the database.
     * Should be called on app launch, after logging a set, and after finishing a workout.
     */
    suspend fun refresh() {
        mutex.withLock {
            val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
            val lastWeekStart = WeekUtils.getLastWeekStartMs()
            val lastWeekEnd = WeekUtils.getLastWeekEndMs()
            val now = WeekUtils.getCurrentTimeMs()

            // Reset overflow flag only when the calendar week boundary actually changes
            // (immune to 167h/169h DST transitions).
            if (lastCurrentWeekStartMs != 0L && currentWeekStart != lastCurrentWeekStartMs) {
                overflowCelebrationTriggered = false
            }
            lastCurrentWeekStartMs = currentWeekStart

            // Get volumes (upper bound is exclusive, so +1 includes sets at `now`)
            val lastWeekVolume = setDao.getVolumeInRange(lastWeekStart, lastWeekEnd + 1)
            val currentWeekVolume = setDao.getVolumeInRange(currentWeekStart, now + 1)

            Log.d("VolumeOrbRepository", "Last week: $lastWeekVolume, Current week: $currentWeekVolume")

            // Calculate progress
            val isFirstWeek = lastWeekVolume <= 0f
            val progressPercent = if (isFirstWeek) {
                0f // No progress to show in first week
            } else {
                currentWeekVolume / lastWeekVolume
            }

            // Check for overflow
            val hasOverflowed = !isFirstWeek && currentWeekVolume > lastWeekVolume
            val justOverflowed = hasOverflowed && !overflowCelebrationTriggered

            if (justOverflowed) {
                overflowCelebrationTriggered = true
                Log.d("VolumeOrbRepository", "OVERFLOW! Celebration triggered")
            }

            _orbState.value = VolumeOrbState(
                lastWeekVolume = lastWeekVolume,
                currentWeekVolume = currentWeekVolume,
                progressPercent = progressPercent,
                isFirstWeek = isFirstWeek,
                hasOverflowed = hasOverflowed,
                justOverflowed = justOverflowed
            )
        }
    }

    /**
     * Clear the justOverflowed flag after the animation has played.
     */
    fun clearOverflowAnimation() {
        _orbState.update { it.copy(justOverflowed = false) }
    }

    /**
     * Get volume contribution for a specific workout.
     * Useful for showing session contribution on post-workout screen.
     */
    suspend fun getSessionContribution(workoutId: Long): Float {
        return setDao.getWorkoutVolume(workoutId)
    }

    /**
     * Force refresh after logging a set.
     * This is a lightweight refresh that recalculates current week volume.
     */
    suspend fun onSetLogged() {
        refresh()
    }
}
