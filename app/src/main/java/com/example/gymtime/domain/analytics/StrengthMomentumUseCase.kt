package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.util.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

enum class MomentumDirection {
    STRONG_UP,
    UP,
    FLAT,
    DOWN,
    STRONG_DOWN,
    NO_BASELINE
}

enum class MomentumDataStatus {
    READY,
    BUILDING_BASELINE,
    STALE
}

enum class MomentumConfidence {
    LOW,
    STANDARD
}

data class ExerciseMomentum(
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: String,
    val percentChange: Float?,
    val recentValue: Float?,
    val baselineValue: Float?,
    val recentSessionCount: Int,
    val baselineSessionCount: Int,
    val latestSessionTimestamp: Long,
    val status: MomentumDataStatus,
    val confidence: MomentumConfidence
)

data class MuscleMomentum(
    val muscle: String,
    val percentChange: Float?,
    val direction: MomentumDirection,
    val contributingExercises: List<ExerciseMomentum>,
    val improvingContributors: List<ExerciseMomentum> = emptyList(),
    val decliningContributors: List<ExerciseMomentum> = emptyList(),
    val hasMixedContributors: Boolean = false,
    val currentWeekVolume: Float = 0f,
    val previousWeekVolume: Float = 0f,
    val status: MomentumDataStatus = MomentumDataStatus.BUILDING_BASELINE,
    val confidence: MomentumConfidence = MomentumConfidence.LOW,
    val latestSessionTimestamp: Long? = null
)

data class StrengthMomentumState(
    val muscles: List<MuscleMomentum> = emptyList(),
    val topImproving: List<MuscleMomentum> = emptyList(),
    val topDeclining: List<MuscleMomentum> = emptyList(),
    val recentSessionCount: Int = StrengthMomentumUseCase.MAX_SESSIONS_PER_SIDE,
    val baselineSessionCount: Int = StrengthMomentumUseCase.MAX_SESSIONS_PER_SIDE,
    val minimumSessionsPerSide: Int = StrengthMomentumUseCase.MIN_SESSIONS_PER_SIDE
)

class StrengthMomentumUseCase @Inject constructor(
    private val setDao: SetDao,
    private val muscleGroupDao: MuscleGroupDao
) {

    suspend fun getStrengthMomentum(nowMs: Long = System.currentTimeMillis()): StrengthMomentumState =
        withContext(Dispatchers.IO) {
            val historyStart = nowMs - TimeUnit.DAYS.toMillis(HISTORY_DAYS.toLong())
            val staleBefore = nowMs - TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS.toLong())

            val allSets = setDao.getPerformanceSetsWithExerciseInRange(
                startDate = historyStart,
                endDate = nowMs
            ).filter {
                it.set.isComplete &&
                    !it.set.isWarmup &&
                    it.logType in STRENGTH_LOG_TYPES
            }

            val exerciseMomentum = buildExerciseMomentum(allSets, staleBefore)
            val weeklyVolumeByMuscle = setDao.getMuscleWeeklyVolumeComparison(
                previousWeekStartMs = WeekUtils.getLastWeekStartMs(),
                currentWeekStartMs = WeekUtils.getCurrentWeekStartMs(),
                nowMs = nowMs
            ).associateBy { it.muscle.lowercase(Locale.US) }

            val muscles = buildMuscleList(exerciseMomentum).map { muscle ->
                val allContributions = exerciseMomentum
                    .filter { it.muscle.equals(muscle, ignoreCase = true) }
                val ready = allContributions
                    .filter { it.status == MomentumDataStatus.READY && it.percentChange != null }
                    .sortedByDescending { kotlin.math.abs(it.percentChange ?: 0f) }
                val weeklyVolume = weeklyVolumeByMuscle[muscle.lowercase(Locale.US)]

                if (ready.isEmpty()) {
                    val latestTimestamp = allContributions.maxOfOrNull { it.latestSessionTimestamp }
                    val status = if (
                        allContributions.isNotEmpty() &&
                        allContributions.all { it.status == MomentumDataStatus.STALE }
                    ) {
                        MomentumDataStatus.STALE
                    } else {
                        MomentumDataStatus.BUILDING_BASELINE
                    }
                    MuscleMomentum(
                        muscle = muscle,
                        percentChange = null,
                        direction = MomentumDirection.NO_BASELINE,
                        contributingExercises = allContributions,
                        currentWeekVolume = weeklyVolume?.currentWeekVolume ?: 0f,
                        previousWeekVolume = weeklyVolume?.previousWeekVolume ?: 0f,
                        status = status,
                        latestSessionTimestamp = latestTimestamp
                    )
                } else {
                    val improving = ready.filter { (it.percentChange ?: 0f) >= STABLE_THRESHOLD }
                        .sortedByDescending { it.percentChange }
                    val declining = ready.filter { (it.percentChange ?: 0f) <= -STABLE_THRESHOLD }
                        .sortedBy { it.percentChange }
                    val percent = robustMusclePercent(ready)
                    MuscleMomentum(
                        muscle = muscle,
                        percentChange = percent,
                        direction = directionFor(percent),
                        contributingExercises = ready,
                        improvingContributors = improving,
                        decliningContributors = declining,
                        hasMixedContributors = improving.isNotEmpty() && declining.isNotEmpty(),
                        currentWeekVolume = weeklyVolume?.currentWeekVolume ?: 0f,
                        previousWeekVolume = weeklyVolume?.previousWeekVolume ?: 0f,
                        status = MomentumDataStatus.READY,
                        confidence = if (ready.all { it.confidence == MomentumConfidence.STANDARD }) {
                            MomentumConfidence.STANDARD
                        } else {
                            MomentumConfidence.LOW
                        },
                        latestSessionTimestamp = ready.maxOfOrNull { it.latestSessionTimestamp }
                    )
                }
            }

            StrengthMomentumState(
                muscles = muscles,
                topImproving = muscles
                    .filter { it.direction == MomentumDirection.UP || it.direction == MomentumDirection.STRONG_UP }
                    .sortedByDescending { it.percentChange ?: 0f }
                    .take(3),
                topDeclining = muscles
                    .filter { it.direction == MomentumDirection.DOWN || it.direction == MomentumDirection.STRONG_DOWN }
                    .sortedBy { it.percentChange ?: 0f }
                    .take(3)
            )
        }

    private suspend fun buildMuscleList(exerciseMomentum: List<ExerciseMomentum>): List<String> {
        val storedMuscles = muscleGroupDao.getAllMuscleGroupNames().map { canonicalMuscleName(it) }
        val calculatedMuscles = exerciseMomentum.map { canonicalMuscleName(it.muscle) }
        return (storedMuscles + calculatedMuscles + DEFAULT_MUSCLES)
            .filterNot { it.equals(CARDIO_MUSCLE, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .sortedWith(compareBy({ preferredMuscleIndex(it) }, { it }))
    }

    private fun buildExerciseMomentum(
        sets: List<SetWithExercisePerformanceInfo>,
        staleBefore: Long
    ): List<ExerciseMomentum> {
        return sets.groupBy { it.set.exerciseId }.mapNotNull { (exerciseId, exerciseSets) ->
            val sample = exerciseSets.maxByOrNull { it.set.timestamp.time } ?: return@mapNotNull null
            val sessions = exerciseSets
                .groupBy { it.set.workoutId }
                .mapNotNull { (_, workoutSets) -> sessionScore(workoutSets) }
                .sortedByDescending { it.timestamp }
            if (sessions.isEmpty()) return@mapNotNull null

            val latestTimestamp = sessions.first().timestamp
            val matchedCount = minOf(MAX_SESSIONS_PER_SIDE, sessions.size / 2)
            val confidence = if (matchedCount >= MAX_SESSIONS_PER_SIDE) {
                MomentumConfidence.STANDARD
            } else {
                MomentumConfidence.LOW
            }

            if (latestTimestamp < staleBefore || matchedCount < MIN_SESSIONS_PER_SIDE) {
                return@mapNotNull ExerciseMomentum(
                    exerciseId = exerciseId,
                    exerciseName = sample.exerciseName,
                    muscle = displayMuscleFor(sample),
                    percentChange = null,
                    recentValue = null,
                    baselineValue = null,
                    recentSessionCount = matchedCount,
                    baselineSessionCount = matchedCount,
                    latestSessionTimestamp = latestTimestamp,
                    status = if (latestTimestamp < staleBefore) {
                        MomentumDataStatus.STALE
                    } else {
                        MomentumDataStatus.BUILDING_BASELINE
                    },
                    confidence = confidence
                )
            }

            val recentValue = median(sessions.take(matchedCount).map { it.value })
            val baselineValue = median(sessions.drop(matchedCount).take(matchedCount).map { it.value })
            if (baselineValue <= 0f) return@mapNotNull null
            val percentChange = (((recentValue - baselineValue) / baselineValue) * 100f)
                .roundToSingleDecimal()

            ExerciseMomentum(
                exerciseId = exerciseId,
                exerciseName = sample.exerciseName,
                muscle = displayMuscleFor(sample),
                percentChange = percentChange,
                recentValue = recentValue,
                baselineValue = baselineValue,
                recentSessionCount = matchedCount,
                baselineSessionCount = matchedCount,
                latestSessionTimestamp = latestTimestamp,
                status = MomentumDataStatus.READY,
                confidence = confidence
            )
        }
    }

    private fun sessionScore(sets: List<SetWithExercisePerformanceInfo>): SessionScore? {
        val values = sets.mapNotNull { set ->
            performanceValue(set).takeIf { it > 0f }
        }.sortedDescending().take(TOP_SETS_PER_SESSION)
        if (values.isEmpty()) return null
        return SessionScore(
            value = values.average().toFloat(),
            timestamp = sets.maxOf { it.set.timestamp.time }
        )
    }

    private fun performanceValue(setInfo: SetWithExercisePerformanceInfo): Float {
        val set = setInfo.set
        return when (setInfo.logType) {
            LogType.WEIGHT_REPS -> {
                val weight = set.weight ?: return 0f
                val reps = set.reps ?: return 0f
                if (weight <= 0f || reps <= 0) 0f else weight * (1 + reps / 30f)
            }
            LogType.REPS_ONLY -> (set.reps ?: 0).toFloat()
            else -> 0f
        }
    }

    private fun robustMusclePercent(contributions: List<ExerciseMomentum>): Float {
        val values = contributions.mapNotNull { it.percentChange }
            .map { it.coerceIn(-MAX_EXERCISE_CHANGE, MAX_EXERCISE_CHANGE) }
        return median(values).roundToSingleDecimal()
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2f
        } else {
            sorted[middle]
        }
    }

    private fun directionFor(percent: Float): MomentumDirection = when {
        percent >= STRONG_THRESHOLD -> MomentumDirection.STRONG_UP
        percent >= STABLE_THRESHOLD -> MomentumDirection.UP
        percent <= -STRONG_THRESHOLD -> MomentumDirection.STRONG_DOWN
        percent <= -STABLE_THRESHOLD -> MomentumDirection.DOWN
        else -> MomentumDirection.FLAT
    }

    private fun displayMuscleFor(setInfo: SetWithExercisePerformanceInfo): String =
        canonicalMuscleName(setInfo.targetMuscle)

    private fun canonicalMuscleName(raw: String): String = when {
        raw.equals("Core", ignoreCase = true) || raw.equals("Abs", ignoreCase = true) -> "Abs"
        else -> raw
    }

    private fun preferredMuscleIndex(muscle: String): Int =
        DEFAULT_MUSCLES.indexOfFirst { it.equals(muscle, ignoreCase = true) }
            .takeIf { it >= 0 } ?: Int.MAX_VALUE

    private fun Float.roundToSingleDecimal(): Float = (this * 10f).roundToInt() / 10f

    private data class SessionScore(val value: Float, val timestamp: Long)

    companion object {
        const val MAX_SESSIONS_PER_SIDE = 3
        const val MIN_SESSIONS_PER_SIDE = 2
        private const val TOP_SETS_PER_SESSION = 3
        private const val HISTORY_DAYS = 365
        private const val STALE_AFTER_DAYS = 42
        private const val STABLE_THRESHOLD = 2f
        private const val STRONG_THRESHOLD = 5f
        private const val MAX_EXERCISE_CHANGE = 20f
        private const val CARDIO_MUSCLE = "Cardio"
        private val STRENGTH_LOG_TYPES = setOf(LogType.WEIGHT_REPS, LogType.REPS_ONLY)
        private val DEFAULT_MUSCLES = listOf(
            "Chest",
            "Back",
            "Shoulders",
            "Biceps",
            "Triceps",
            "Abs",
            "Legs"
        )
    }
}
