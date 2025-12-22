package com.example.gymtime.domain.analytics

import androidx.compose.ui.graphics.Color
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DailyVolume
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class HeatMapDay(
    val date: Long, // Start of day timestamp
    val volume: Float,
    val level: Int, // 0 = Empty, 1 = Low, 2 = Medium, 3 = High
    val formattedDate: String // e.g. "Oct 12"
)

data class ConsistencyStats(
    val streakResult: StreakCalculator.StreakResult,
    val bestStreak: Int,
    val ytdWorkouts: Int,
    val ytdVolume: Float,
    val consistencyScore: Int // % active weeks in last year
)

class ConsistencyUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun getHeatMapData(): List<HeatMapDay> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getDailyVolumeForHeatMap()
        
        if (rawData.isEmpty()) return@withContext emptyList()

        // 1. Calculate Percentiles
        val volumes = rawData.map { it.dailyVol }.sorted()
        
        val p33 = if (volumes.isNotEmpty()) volumes[(volumes.size * 0.33).toInt()] else 0f
        val p66 = if (volumes.isNotEmpty()) volumes[(volumes.size * 0.66).toInt()] else 0f
        
        val map = rawData.associateBy { stripTime(it.date) }
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val days = mutableListOf<HeatMapDay>()
        val totalDays = 365 // 52 weeks * 7 approx
        
        // Go back 364 days
        calendar.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))
        
        for (i in 0 until totalDays) {
            val dateMs = calendar.timeInMillis
            val item = map[dateMs]
            val volume = item?.dailyVol ?: 0f
            
            val level = when {
                volume == 0f -> 0
                volume <= p33 -> 1
                volume <= p66 -> 2
                else -> 3
            }
            
            days.add(HeatMapDay(
                date = dateMs,
                volume = volume,
                level = level,
                formattedDate = formatDate(dateMs)
            ))
            
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        days
    }

    suspend fun getConsistencyStats(): ConsistencyStats = withContext(Dispatchers.IO) {
        // 1. Heatmap Data -> Score
        val rawData = workoutDao.getDailyVolumeForHeatMap()
        
        // Consistency Score Calculation (Existing logic)
        val activeDates = rawData.map { it.date }
        val activeWeeks = activeDates.map { getWeekSinceEpoch(it) }.toSet()
        val currentWeek = getWeekSinceEpoch(System.currentTimeMillis())
        val totalWeeksInYear = 52
        val activeWeeksCount = activeWeeks.count { it >= currentWeek - 52 }
        val score = if (totalWeeksInYear > 0) ((activeWeeksCount.toFloat() / totalWeeksInYear) * 100).toInt() else 0

        // 2. Iron Streak Data (From HomeViewModel logic)
        val dateStrings = workoutDao.getWorkoutDatesWithWorkingSets()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val workoutDates = dateStrings.mapNotNull { dateStr ->
            try { dateFormat.parse(dateStr) } catch (e: Exception) { null }
        }
        val streakResult = StreakCalculator.calculateStreak(workoutDates)

        // 3. Best Streak (From Prefs)
        val bestStreak = userPreferencesRepository.bestStreak.first()
        // Update preference if current is higher (HomeViewModel also does this, duplication is okay for safety)
        if (streakResult.streakDays > bestStreak) {
            userPreferencesRepository.updateBestStreakIfNeeded(streakResult.streakDays)
        }

        // 4. YTD Workouts
        val ytdWorkouts = workoutDao.getYearToDateWorkoutCount()

        // 5. YTD Volume
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfYear = cal.timeInMillis
        val endOfToday = System.currentTimeMillis()
        val ytdVolume = setDao.getTotalVolume(startOfYear, endOfToday) ?: 0f

        ConsistencyStats(
            streakResult = streakResult,
            bestStreak = if (streakResult.streakDays > bestStreak) streakResult.streakDays else bestStreak,
            ytdWorkouts = ytdWorkouts,
            ytdVolume = ytdVolume,
            consistencyScore = score
        )
    }

    private fun getWeekSinceEpoch(timestamp: Long): Long {
        return timestamp / (1000L * 60 * 60 * 24 * 7)
    }
    
    private fun stripTime(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    private fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault())
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$month $day"
    }
}
