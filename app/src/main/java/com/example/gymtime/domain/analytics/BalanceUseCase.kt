package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleDistribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

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

    suspend fun getMuscleDistribution(range: BalanceTimeRange): List<MuscleDistribution> = withContext(Dispatchers.IO) {
        getBodyPartSetCounts(range).filter { it.setVolume > 0 }
    }

    suspend fun getRadarDistribution(range: BalanceTimeRange): List<MuscleDistribution> = withContext(Dispatchers.IO) {
        getBodyPartSetCounts(range)
    }

    suspend fun getMuscleFreshness(): List<MuscleFreshnessStatus> = withContext(Dispatchers.IO) {
        val rawData = workoutDao.getMuscleLastTrainedDates()
        val now = System.currentTimeMillis()

        val allMuscles = muscleGroupDao.getAllMuscleGroupNames()
        val knownFreshness = rawData.associateBy { it.muscle }
        
        allMuscles.map { muscle ->
            val lastTrained = knownFreshness[muscle]?.lastTrained ?: 0L
            val diffMs = now - lastTrained
            val daysSince = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
            
            val status = when {
                lastTrained == 0L -> RecoveryStatus.FRESH // Treat never trained as fresh
                diffMs < TimeUnit.HOURS.toMillis(24) -> RecoveryStatus.FATIGUED
                diffMs < TimeUnit.DAYS.toMillis(3) -> RecoveryStatus.RECOVERING
                else -> RecoveryStatus.FRESH
            }

            val displayDays = if (lastTrained == 0L) 999 else daysSince

            MuscleFreshnessStatus(
                muscle = muscle,
                daysSince = displayDays,
                status = status
            )
        }.sortedBy { it.daysSince }
    }

    private suspend fun getBodyPartSetCounts(range: BalanceTimeRange): List<MuscleDistribution> {
        val allMuscles = muscleGroupDao.getAllMuscleGroupNames()
        val now = System.currentTimeMillis()
        val start = range.days?.let { days ->
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -days)
            }.timeInMillis
        } ?: 0L

        val raw = if (range == BalanceTimeRange.THIRTY_DAYS) {
            workoutDao.getMuscleSetCountsLast30Days()
        } else {
            workoutDao.getMuscleSetCountsInRange(start, now)
        }.associateBy { it.muscle }

        return allMuscles.map { muscle ->
            raw[muscle] ?: MuscleDistribution(muscle = muscle, setVolume = 0)
        }
    }
}
