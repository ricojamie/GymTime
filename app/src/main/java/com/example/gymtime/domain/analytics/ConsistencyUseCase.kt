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
    private var clockMs: () -> Long = System::currentTimeMillis

    internal constructor(
        workoutDao: WorkoutDao,
        setDao: SetDao,
        userPreferencesRepository: UserPreferencesRepository,
        zoneId: ZoneId,
        clockMs: () -> Long = System::currentTimeMillis
    ) : this(workoutDao, setDao, userPreferencesRepository) {
        this.zoneId = zoneId
        this.clockMs = clockMs
    }

    suspend fun getHeatMapData(): List<HeatMapDay> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getDailyVolumeForHeatMap()

        val map = mutableMapOf<Long, DailyVolume>()
        for (entry in rawData) {
            val dayMs = startOfLocalDayMillis(entry.date)
            val vol = entry.dailyVol.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val sets = entry.workingSetCount.coerceAtLeast(0)
            val existing = map[dayMs]
            map[dayMs] = if (existing == null) {
                DailyVolume(dailyVol = vol, date = dayMs, workingSetCount = sets)
            } else {
                DailyVolume(
                    dailyVol = (existing.dailyVol.toDouble() + vol).toFloat(),
                    date = dayMs,
                    workingSetCount = existing.workingSetCount + sets
                )
            }
        }

        // 1. Calculate Percentiles (only from positive non-zero data)
        val volumes = map.values
            .map { it.dailyVol }
            .filter { it.isFinite() && it > 0f }
            .sorted()

        val p33 = if (volumes.isNotEmpty()) {
            volumes[(volumes.size * 0.33).toInt().coerceAtMost(volumes.lastIndex)]
        } else 0f
        val p66 = if (volumes.isNotEmpty()) {
            volumes[(volumes.size * 0.66).toInt().coerceAtMost(volumes.lastIndex)]
        } else 0f

        val today = Instant.ofEpochMilli(clockMs()).atZone(zoneId).toLocalDate()
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
                volume <= 0f -> 0
                volume <= p33 -> 1
                volume <= p66 -> 2
                else -> 3
            }

            days.add(
                HeatMapDay(
                    date = dateMs,
                    volume = volume,
                    workingSetCount = workingSetCount,
                    level = level,
                    formattedDate = formatDate(cursor)
                )
            )
            cursor = cursor.plusDays(1)
        }

        days
    }

    suspend fun getConsistencyStats(): ConsistencyStats = withContext(Dispatchers.IO) {
        // 1. Heatmap Data -> Score
        val rawData = workoutDao.getDailyVolumeForHeatMap()
        val nowMs = clockMs()

        // Consistency Score Calculation over trailing 52 Sunday-Saturday weeks
        val activeWeeks = rawData.asSequence()
            .filter { (it.dailyVol.isFinite() && it.dailyVol > 0f) || it.workingSetCount > 0 }
            .mapTo(HashSet()) { getWeekSinceEpoch(it.date) }
        val currentWeek = getWeekSinceEpoch(nowMs)
        val totalWeeksInYear = 52
        val windowStartWeek = currentWeek - (totalWeeksInYear - 1)
        val activeWeeksCount = activeWeeks.count { it in windowStartWeek..currentWeek }
        val score = ((activeWeeksCount.toFloat() / totalWeeksInYear) * 100)
            .toInt()
            .coerceIn(0, 100)

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
            allowedSkipsPerWeek = allowedRestDays,
            asOf = Date(nowMs)
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
        val startOfYear = Instant.ofEpochMilli(nowMs)
            .atZone(zoneId)
            .toLocalDate()
            .withDayOfYear(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val ytdVolume = (setDao.getTotalVolume(startOfYear, nowMs) ?: 0f)
            .let { if (it.isFinite() && it >= 0f) it else 0f }

        ConsistencyStats(
            streakResult = streakResult,
            bestStreak = if (streakResult.streakDays > bestStreak) streakResult.streakDays else bestStreak,
            ytdWorkouts = ytdWorkouts,
            ytdVolume = ytdVolume,
            consistencyScore = score
        )
    }

    // 1970-01-01 was Thursday (+4 shifts epochDay so Sunday-Saturday weeks map to contiguous integers).
    private fun getWeekSinceEpoch(timestamp: Long): Long {
        val epochDay = Instant.ofEpochMilli(timestamp)
            .atZone(zoneId)
            .toLocalDate()
            .toEpochDay()
        return Math.floorDiv(epochDay + 4L, 7L)
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
