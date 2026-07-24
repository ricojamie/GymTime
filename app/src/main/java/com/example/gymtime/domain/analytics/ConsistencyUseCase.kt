package com.example.gymtime.domain.analytics

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DailyVolume
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HeatMapDay(
    val date: Long, // Start of day timestamp
    val volume: Float,
    val workingSetCount: Int,
    val level: Int, // -1 = Future, 0 = Empty, 1 = Low, 2 = Medium, 3 = High
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
    private var zoneId: ZoneId = ZoneId.systemDefault()

    internal constructor(
        workoutDao: WorkoutDao,
        setDao: SetDao,
        userPreferencesRepository: UserPreferencesRepository,
        zoneId: ZoneId
    ) : this(workoutDao, setDao, userPreferencesRepository) {
        this.zoneId = zoneId
    }

    suspend fun getHeatMapData(): List<HeatMapDay> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getDailyVolumeForHeatMap()

        // 1. Calculate Percentiles (only from non-zero data)
        val volumes = rawData.map { it.dailyVol }.sorted()

        val p33 = if (volumes.isNotEmpty()) volumes[(volumes.size * 0.33).toInt()] else 0f
        val p66 = if (volumes.isNotEmpty()) volumes[(volumes.size * 0.66).toInt()] else 0f

        val map = rawData.associateBy { startOfLocalDayMillis(it.date) }

        val today = LocalDate.now(zoneId)
        val startOfYear = today.withDayOfYear(1)
        val endOfYear = LocalDate.of(today.year, 12, 31)

        val days = mutableListOf<HeatMapDay>()
        var cursor = startOfYear
        while (!cursor.isAfter(endOfYear)) {
            val dateMs = cursor.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val isFuture = cursor.isAfter(today)
            val item = map[dateMs]
            val volume = if (isFuture) 0f else (item?.dailyVol ?: 0f)
            val workingSetCount = if (isFuture) 0 else (item?.workingSetCount ?: 0)

            val level = when {
                isFuture -> -1  // Future day marker
                volume == 0f -> 0
                volume <= p33 -> 1
                volume <= p66 -> 2
                else -> 3
            }

            days.add(HeatMapDay(
                date = dateMs,
                volume = volume,
                workingSetCount = workingSetCount,
                level = level,
                formattedDate = formatDate(cursor)
            ))
            cursor = cursor.plusDays(1)
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
        val allowedRestDays = userPreferencesRepository.restDaysPerWeek.firstOrNull() ?: 2
        val workoutDates = dateStrings.mapNotNull { dateStr ->
            parseLocalDate(dateStr)?.let { localDate ->
                Date.from(localDate.atStartOfDay(zoneId).toInstant())
            }
        }
        val streakResult = StreakCalculator.calculateStreak(
            workoutDates = workoutDates,
            allowedSkipsPerWeek = allowedRestDays
        )

        // 3. Best Streak (From Prefs)
        val bestStreak = userPreferencesRepository.bestStreak.first()
        // Update preference if current is higher (HomeViewModel also does this, duplication is okay for safety)
        if (streakResult.streakDays > bestStreak) {
            userPreferencesRepository.updateBestStreakIfNeeded(streakResult.streakDays)
        }

        // 4. YTD Workouts
        val ytdWorkouts = workoutDao.getYearToDateWorkoutCount()

        // 5. YTD Volume
        val startOfYear = LocalDate.now(zoneId)
            .withDayOfYear(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
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
        return Instant.ofEpochMilli(timestamp)
            .atZone(zoneId)
            .toLocalDate()
            .toEpochDay() / 7L
    }

    private fun startOfLocalDayMillis(timestamp: Long): Long {
        return Instant.ofEpochMilli(timestamp)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun formatDate(date: LocalDate): String {
        val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        return "$month ${date.dayOfMonth}"
    }
}
