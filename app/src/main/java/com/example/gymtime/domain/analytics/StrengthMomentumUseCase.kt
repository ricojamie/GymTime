package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExercisePerformanceInfo
import com.example.gymtime.data.db.entity.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MomentumDirection {
    STRONG_UP,
    UP,
    FLAT,
    DOWN,
    STRONG_DOWN,
    NO_BASELINE
}

data class ExerciseMomentum(
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: String,
    val percentChange: Float,
    val recentValue: Float,
    val baselineValue: Float,
    val workoutCount: Int
)

data class MuscleMomentum(
    val muscle: String,
    val percentChange: Float?,
    val direction: MomentumDirection,
    val contributingExercises: List<ExerciseMomentum>
)

data class StrengthMomentumState(
    val muscles: List<MuscleMomentum> = emptyList(),
    val topImproving: List<MuscleMomentum> = emptyList(),
    val topDeclining: List<MuscleMomentum> = emptyList(),
    val recentWindowDays: Int = StrengthMomentumUseCase.WINDOW_DAYS,
    val baselineWindowDays: Int = StrengthMomentumUseCase.WINDOW_DAYS
)

class StrengthMomentumUseCase @Inject constructor(
    private val setDao: SetDao,
    private val muscleGroupDao: MuscleGroupDao
) {

    suspend fun getStrengthMomentum(nowMs: Long = System.currentTimeMillis()): StrengthMomentumState =
        withContext(Dispatchers.IO) {
            val windowMs = TimeUnit.DAYS.toMillis(WINDOW_DAYS.toLong())
            val historyMs = TimeUnit.DAYS.toMillis(HISTORY_DAYS.toLong())

            val recentStart = nowMs - windowMs
            val historyStart = nowMs - historyMs

            val allSets = setDao.getPerformanceSetsWithExerciseInRange(
                startDate = historyStart,
                endDate = nowMs
            ).filter { !it.set.isWarmup }

            // Current window: recent (last WINDOW_DAYS) vs baseline (the WINDOW_DAYS before that)
            val recentSets = allSets.filter { it.set.timestamp.time >= recentStart }
            val baselineSets = allSets.filter {
                it.set.timestamp.time < recentStart && it.set.timestamp.time >= recentStart - windowMs
            }
            val currentExerciseMomentum = buildExerciseMomentum(recentSets, baselineSets)
            val currentMuscleChanges = aggregatePercentChangeByMuscle(currentExerciseMomentum)

            // Historical comparison points: slide 28d-vs-prior-28d backward in 7-day steps,
            // skipping the most recent window (that's "current"). Build per-muscle series.
            val historyPerMuscle = collectHistoricalSeries(allSets, nowMs, windowMs)

            val allMuscles = buildMuscleList(currentExerciseMomentum)

            val muscles = allMuscles.map { muscle ->
                val contributions = currentExerciseMomentum
                    .filter { it.muscle.equals(muscle, ignoreCase = true) }
                    .sortedByDescending { kotlin.math.abs(it.percentChange) }

                if (contributions.isEmpty()) {
                    MuscleMomentum(
                        muscle = muscle,
                        percentChange = null,
                        direction = MomentumDirection.NO_BASELINE,
                        contributingExercises = emptyList()
                    )
                } else {
                    val percent = currentMuscleChanges[muscle.lowercase(Locale.US)]
                        ?: weightedPercent(contributions)
                    val series = historyPerMuscle[muscle.lowercase(Locale.US)].orEmpty()

                    MuscleMomentum(
                        muscle = muscle,
                        percentChange = percent,
                        direction = directionFor(percent, series),
                        contributingExercises = contributions
                    )
                }
            }

            StrengthMomentumState(
                muscles = muscles,
                topImproving = muscles
                    .filter { (it.percentChange ?: 0f) > 0f }
                    .sortedByDescending { it.percentChange ?: 0f }
                    .take(3),
                topDeclining = muscles
                    .filter { (it.percentChange ?: 0f) < 0f }
                    .sortedBy { it.percentChange ?: 0f }
                    .take(3)
            )
        }

    private suspend fun buildMuscleList(exerciseMomentum: List<ExerciseMomentum>): List<String> {
        val storedMuscles = muscleGroupDao.getAllMuscleGroupNames().map { canonicalMuscleName(it) }
        val calculatedMuscles = exerciseMomentum.map { canonicalMuscleName(it.muscle) }
        return (storedMuscles + calculatedMuscles + DEFAULT_MUSCLES)
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy({ preferredMuscleIndex(it) }, { it }))
    }

    private fun buildExerciseMomentum(
        recentSets: List<SetWithExercisePerformanceInfo>,
        baselineSets: List<SetWithExercisePerformanceInfo>
    ): List<ExerciseMomentum> {
        val recentByExercise = recentSets.groupBy { it.set.exerciseId }
        val baselineByExercise = baselineSets.groupBy { it.set.exerciseId }

        return recentByExercise.mapNotNull { (exerciseId, recentExerciseSets) ->
            val baselineExerciseSets = baselineByExercise[exerciseId].orEmpty()
            if (baselineExerciseSets.isEmpty()) return@mapNotNull null

            val sample = recentExerciseSets.firstOrNull() ?: return@mapNotNull null
            val recentTop = topWorkoutValues(recentExerciseSets)
            val baselineTop = topWorkoutValues(baselineExerciseSets)
            if (recentTop.isEmpty() || baselineTop.isEmpty()) return@mapNotNull null

            val recentValue = recentTop.average().toFloat()
            val baselineValue = baselineTop.average().toFloat()
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
                workoutCount = recentTop.size + baselineTop.size
            )
        }
    }

    private fun topWorkoutValues(sets: List<SetWithExercisePerformanceInfo>): List<Float> {
        return sets.groupBy { it.set.workoutId }
            .mapNotNull { (_, workoutSets) ->
                workoutSets.maxOfOrNull { performanceValue(it) }
                    ?.takeIf { it > 0f }
            }
            .sortedDescending()
            .take(TOP_WORKOUT_COUNT)
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
            LogType.DURATION -> (set.durationSeconds ?: 0).toFloat()
            LogType.WEIGHT_TIME -> {
                val weight = set.weight ?: return 0f
                val seconds = set.durationSeconds ?: return 0f
                weight * seconds
            }
            LogType.WEIGHT_DISTANCE -> {
                val weight = set.weight ?: return 0f
                val distance = set.distanceMeters ?: return 0f
                weight * distance
            }
            LogType.DISTANCE_TIME -> set.distanceMeters ?: set.durationSeconds?.toFloat() ?: 0f
            LogType.CALORIES_TIME -> set.calories ?: set.durationSeconds?.toFloat() ?: 0f
        }
    }

    private fun displayMuscleFor(setInfo: SetWithExercisePerformanceInfo): String {
        val raw = if (isCardio(setInfo)) CARDIO_MUSCLE else setInfo.targetMuscle
        return canonicalMuscleName(raw)
    }

    private fun canonicalMuscleName(raw: String): String =
        when {
            raw.equals("Core", ignoreCase = true) || raw.equals("Abs", ignoreCase = true) -> "Abs"
            else -> raw
        }

    private fun isCardio(setInfo: SetWithExercisePerformanceInfo): Boolean {
        return setInfo.targetMuscle.equals(CARDIO_MUSCLE, ignoreCase = true) ||
            setInfo.logType == LogType.DISTANCE_TIME ||
            setInfo.logType == LogType.CALORIES_TIME
    }

    private fun aggregatePercentChangeByMuscle(
        exerciseMomentum: List<ExerciseMomentum>
    ): Map<String, Float> {
        return exerciseMomentum
            .groupBy { it.muscle.lowercase(Locale.US) }
            .mapValues { (_, contributions) -> weightedPercent(contributions) }
    }

    private fun weightedPercent(contributions: List<ExerciseMomentum>): Float {
        if (contributions.isEmpty()) return 0f
        val weightedTotal = contributions.sumOf { c ->
            c.percentChange.toDouble() * c.workoutCount.coerceAtLeast(1)
        }
        val weight = contributions.sumOf { it.workoutCount.coerceAtLeast(1) }
        return (weightedTotal / weight).toFloat()
    }

    /**
     * Returns a map of (lowercase muscle name) → list of historical 28d-vs-prior-28d % changes,
     * sliding the comparison window backward in 7-day steps from one window ago. The most recent
     * window (the one we're currently classifying) is intentionally excluded.
     */
    private fun collectHistoricalSeries(
        allSets: List<SetWithExercisePerformanceInfo>,
        nowMs: Long,
        windowMs: Long
    ): Map<String, List<Float>> {
        val stepMs = TimeUnit.DAYS.toMillis(HISTORY_STEP_DAYS.toLong())
        val series = mutableMapOf<String, MutableList<Float>>()

        var anchorRecentEnd = nowMs - stepMs // start one step behind "current"
        repeat(HISTORY_STEPS) {
            val recentEnd = anchorRecentEnd
            val recentStart = recentEnd - windowMs
            val baselineEnd = recentStart
            val baselineStart = baselineEnd - windowMs

            val recent = allSets.filter {
                it.set.timestamp.time in baselineStart..recentEnd && it.set.timestamp.time >= recentStart
            }
            val baseline = allSets.filter {
                it.set.timestamp.time in baselineStart..recentStart - 1
            }
            if (recent.isNotEmpty() && baseline.isNotEmpty()) {
                val em = buildExerciseMomentum(recent, baseline)
                val perMuscle = aggregatePercentChangeByMuscle(em)
                perMuscle.forEach { (muscleKey, pct) ->
                    series.getOrPut(muscleKey) { mutableListOf() }.add(pct)
                }
            }
            anchorRecentEnd -= stepMs
        }
        return series
    }

    private fun directionFor(percent: Float, history: List<Float>): MomentumDirection {
        // Sub-noise wobble: don't reward sub-1.5% moves regardless of history quality.
        if (kotlin.math.abs(percent) < NOISE_FLOOR) return MomentumDirection.FLAT

        // Hard floor on absolute regressions: a real slump should never read as gray.
        val absoluteFallback = absoluteDirection(percent)

        if (history.size < MIN_HISTORY_SAMPLES) return absoluteFallback

        val mean = history.average().toFloat()
        val variance = history.sumOf { ((it - mean).toDouble()).let { d -> d * d } } / history.size
        val stddev = sqrt(variance).toFloat()
        if (stddev < MIN_STDDEV) return absoluteFallback

        val z = (percent - mean) / stddev
        val zClassified = when {
            z >= 1.5f -> MomentumDirection.STRONG_UP
            z >= 0.5f -> MomentumDirection.UP
            z <= -1.5f -> MomentumDirection.STRONG_DOWN
            z <= -0.5f -> MomentumDirection.DOWN
            else -> MomentumDirection.FLAT
        }

        // Cap: if the absolute reading says it's a real slump, never report rosier than that.
        return when {
            absoluteFallback == MomentumDirection.STRONG_DOWN -> MomentumDirection.STRONG_DOWN
            absoluteFallback == MomentumDirection.DOWN && zClassified == MomentumDirection.FLAT ->
                MomentumDirection.DOWN
            else -> zClassified
        }
    }

    private fun absoluteDirection(percent: Float): MomentumDirection {
        return when {
            percent >= 5f -> MomentumDirection.STRONG_UP
            percent >= 2f -> MomentumDirection.UP
            percent <= -5f -> MomentumDirection.STRONG_DOWN
            percent <= -2f -> MomentumDirection.DOWN
            else -> MomentumDirection.FLAT
        }
    }

    private fun preferredMuscleIndex(muscle: String): Int {
        return DEFAULT_MUSCLES.indexOfFirst { it.equals(muscle, ignoreCase = true) }
            .takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun Float.roundToSingleDecimal(): Float = (this * 10f).roundToInt() / 10f

    companion object {
        const val WINDOW_DAYS = 28
        const val HISTORY_DAYS = 7 * WINDOW_DAYS // ~6.5 months
        private const val HISTORY_STEPS = 22
        private const val HISTORY_STEP_DAYS = 7
        private const val MIN_HISTORY_SAMPLES = 5
        private const val MIN_STDDEV = 0.5f
        private const val NOISE_FLOOR = 1.5f
        private const val TOP_WORKOUT_COUNT = 3
        private const val CARDIO_MUSCLE = "Cardio"
        private val DEFAULT_MUSCLES = listOf(
            "Chest",
            "Back",
            "Shoulders",
            "Biceps",
            "Triceps",
            "Abs",
            "Legs",
            CARDIO_MUSCLE
        )
    }
}
