package com.example.gymtime.domain.analytics

import androidx.compose.ui.graphics.Color
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleDistribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import com.example.gymtime.ui.theme.ThemeColors

// UI Model for Freshness
data class MuscleFreshnessStatus(
    val muscle: String,
    val daysSince: Int,
    val status: RecoveryStatus,
    val color: Color
)

enum class RecoveryStatus {
    FRESH,      // > 3 days (Green)
    RECOVERING, // 1-3 days (Yellow)
    FATIGUED    // < 24 hours (Red)
}

class BalanceUseCase @Inject constructor(
    private val workoutDao: WorkoutDao
) {

    suspend fun getMuscleDistribution(): List<MuscleDistribution> = withContext(Dispatchers.IO) {
        workoutDao.getMuscleSetCountsLast30Days().filter { it.setVolume > 0 }
    }

    suspend fun getMuscleFreshness(): List<MuscleFreshnessStatus> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getMuscleLastTrainedDates()
        val now = System.currentTimeMillis()
        
        // Define all major muscles we want to track
        val allMuscles = listOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core")
        
        // Map existing data
        val knownFreshness = rawData.associateBy { it.muscle }
        
        allMuscles.map { muscle ->
            val lastTrained = knownFreshness[muscle]?.lastTrained ?: 0L
            val diffMs = now - lastTrained
            val daysSince = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
            
            // Logic:
            // < 24 hours -> Fatigued (Red)
            // 1-3 days -> Recovering (Yellow)
            // > 3 days -> Fresh (Green)
            
            val (status, color) = when {
                lastTrained == 0L -> Pair(RecoveryStatus.FRESH, ThemeColors.LimeGreen.primaryAccent) // Treat never trained as fresh
                diffMs < TimeUnit.HOURS.toMillis(24) -> Pair(RecoveryStatus.FATIGUED, Color(0xFFEF4444)) // Red
                diffMs < TimeUnit.DAYS.toMillis(3) -> Pair(RecoveryStatus.RECOVERING, Color(0xFFF59E0B)) // Amber
                else -> Pair(RecoveryStatus.FRESH, ThemeColors.LimeGreen.primaryAccent) // Green/Theme Color
            }
            
            // Adjust "never trained" display
            val displayDays = if (lastTrained == 0L) 999 else daysSince

            MuscleFreshnessStatus(
                muscle = muscle,
                daysSince = displayDays,
                status = status,
                color = color
            )
        }.sortedBy { it.daysSince } // Show most recently trained (Fatigued) first? Or mostly fresh?
        // Let's sort by freshness: Fatigued (0 days) on top to show what NOT to train.
    }
}
