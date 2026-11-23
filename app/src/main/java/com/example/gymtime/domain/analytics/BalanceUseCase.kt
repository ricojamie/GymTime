package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class MuscleFreshnessStatus(
    val muscle: String,
    val daysSince: Int,
    val status: RecoveryStatus
)

enum class RecoveryStatus {
    FRESH,
    RECOVERING,
    FATIGUED
}

enum class BalanceTimeRange(val label: String, val days: Int?) {
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30),
    NINETY_DAYS("90 Days", 90),
    ONE_YEAR("1 Year", 365),
    ALL_TIME("All Time", null)
}

class BalanceUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val muscleGroupDao: MuscleGroupDao
) {
    private var zoneId: ZoneId = ZoneId.systemDefault()
    private var clockMs: () -> Long = System::currentTimeMillis

    internal constructor(
        workoutDao: WorkoutDao,
        muscleGroupDao: MuscleGroupDao,
        zoneId: ZoneId = ZoneId.systemDefault(),
        clockMs: () -> Long = System::currentTimeMillis
    ) : this(workoutDao, muscleGroupDao) {
        this.zoneId = zoneId
        this.clockMs = clockMs
    }

    suspend fun getMuscleDistribution(range: BalanceTimeRange): List<MuscleDistribution> = withContext(Dispatchers.IO) {
        getBodyPartSetCounts(range).filter { it.setVolume > 0 }
    }

    suspend fun getRadarDistribution(range: BalanceTimeRange): List<MuscleDistribution> = withContext(Dispatchers.IO) {
        getBodyPartSetCounts(range)
    }

    suspend fun getMuscleFreshness(): List<MuscleFreshnessStatus> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getMuscleLastTrainedDates()
        val now = clockMs()

        val allMuscles = muscleGroupDao.getAllMuscleGroupNames()
            .filterNot { it.isWarmupMuscleGroup() }
        val knownFreshness = mutableMapOf<String, Long>()
        for (entry in rawData) {
            if (entry.muscle.isWarmupMuscleGroup()) continue
            val key = entry.muscle.trim().lowercase(Locale.US)
            val existing = knownFreshness[key]
            if (existing == null || entry.lastTrained > existing) {
                knownFreshness[key] = entry.lastTrained
            }
        }

        allMuscles.map { muscle ->
            val lastTrained = knownFreshness[muscle.trim().lowercase(Locale.US)] ?: 0L
            val diffMs = (now - lastTrained).coerceAtLeast(0L)
            val daysSince = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()

            val status = when {
                lastTrained <= 0L -> RecoveryStatus.FRESH // Treat never trained as fresh
                diffMs < TimeUnit.HOURS.toMillis(24) -> RecoveryStatus.FATIGUED
                diffMs < TimeUnit.DAYS.toMillis(3) -> RecoveryStatus.RECOVERING
                else -> RecoveryStatus.FRESH
            }

            val displayDays = if (lastTrained <= 0L) 999 else daysSince

            MuscleFreshnessStatus(
                muscle = muscle,
                daysSince = displayDays,
                status = status
            )
        }.sortedWith(compareBy<MuscleFreshnessStatus> { it.daysSince }.thenBy { it.muscle })
    }

    private suspend fun getBodyPartSetCounts(range: BalanceTimeRange): List<MuscleDistribution> {
        val allMuscles = muscleGroupDao.getAllMuscleGroupNames()
            .filterNot { it.isWarmupMuscleGroup() }
        val now = clockMs()
        val start = range.days?.let { days ->
            Instant.ofEpochMilli(now)
                .atZone(zoneId)
                .minusDays(days.toLong())
                .toInstant()
                .toEpochMilli()
        } ?: 0L

        val raw = workoutDao.getMuscleSetCountsInRange(start, now)
        val countsByMuscle = mutableMapOf<String, Int>()
        for (entry in raw) {
            if (entry.muscle.isWarmupMuscleGroup()) continue
            val key = entry.muscle.trim().lowercase(Locale.US)
            countsByMuscle[key] = (countsByMuscle[key] ?: 0) + entry.setVolume.coerceAtLeast(0)
        }

        return allMuscles.map { muscle ->
            MuscleDistribution(
                muscle = muscle,
                setVolume = countsByMuscle[muscle.trim().lowercase(Locale.US)] ?: 0
            )
        }
    }
}
