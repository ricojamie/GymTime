package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.AnalyticsInsightSetRow
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WeeklyAnalyticsInsightKind {
    PUSH_PULL_GAP,
    STRENGTH_DECLINE,
    STALE_MUSCLE,
    WEEKLY_VOLUME_CHANGE,
    STRENGTH_IMPROVEMENT,
    WEEKLY_CONSISTENCY,
    MOST_TRAINED_MUSCLE,
    BUILDING_HISTORY
}

/**
 * Deterministic, locally verified facts for one weekly insight. [canonicalFacts]
 * is deliberately compact and stable so the narrative cache can fingerprint it.
 */
data class WeeklyAnalyticsInsightFacts(
    val kind: WeeklyAnalyticsInsightKind,
    val weekStartEpochMs: Long,
    val subjectKey: String,
    val canonicalFacts: String,
    val fallbackText: String,
    val allowedMuscleNames: Set<String>,
    val knownMuscleNames: Set<String>,
    val allowedNumbers: Set<String>
)

class WeeklyAnalyticsInsightUseCase @Inject constructor(
    private val setDao: SetDao,
    private val workoutDao: WorkoutDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val strengthMomentumUseCase: StrengthMomentumUseCase
) {

    suspend operator fun invoke(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WeeklyAnalyticsInsightFacts = withContext(Dispatchers.IO) {
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val currentWeekStart = now
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .toLocalDate()
            .atStartOfDay(zoneId)
        val earliestCompleteWeek = currentWeekStart.minusWeeks(COMPLETE_WEEK_COUNT.toLong())
        val twentyEightDaysAgo = now.minusDays(FALLBACK_LOOKBACK_DAYS.toLong())
        val queryStart = minOf(
            earliestCompleteWeek.toInstant().toEpochMilli(),
            twentyEightDaysAgo.toInstant().toEpochMilli()
        )
        val rows = setDao.getAnalyticsInsightSets(queryStart, nowMs)
        val context = InsightContext(
            now = now,
            currentWeekStart = currentWeekStart,
            rows = rows,
            knownMuscleNames = muscleGroupDao.getAllMuscleGroupNames().toSet()
        )

        persistentPushPullGap(context)?.let { return@withContext it }

        val momentum = strengthMomentumUseCase.getStrengthMomentum(nowMs)
        strengthChange(
            context = context,
            momentum = momentum,
            improving = false
        )?.let { return@withContext it }

        staleMuscle(context)?.let { return@withContext it }

        weeklyVolumeChange(context)?.let { return@withContext it }

        strengthChange(
            context = context,
            momentum = momentum,
            improving = true
        )?.let { return@withContext it }

        fallbackInsight(context)
    }

    private fun persistentPushPullGap(context: InsightContext): WeeklyAnalyticsInsightFacts? {
        val weeklyComparisons = (COMPLETE_WEEK_COUNT downTo 1).map { weeksAgo ->
            val start = context.currentWeekStart.minusWeeks(weeksAgo.toLong())
            val end = start.plusWeeks(1)
            val rows = context.rows.inWindow(start, end)
            val push = rows.weightedAggregate(PUSH_MUSCLES)
            val pull = rows.weightedAggregate(PULL_MUSCLES)
            if (
                push.setCount < MIN_PUSH_PULL_SETS_PER_SIDE ||
                pull.setCount < MIN_PUSH_PULL_SETS_PER_SIDE ||
                push.volume <= 0f ||
                pull.volume <= 0f
            ) {
                return null
            }

            val leading = if (push.volume > pull.volume) TrainingSide.PUSH else TrainingSide.PULL
            val leadingVolume = maxOf(push.volume, pull.volume)
            val trailingVolume = minOf(push.volume, pull.volume)
            val gapPercent = ((leadingVolume - trailingVolume) / leadingVolume) * 100f
            WeeklySideComparison(leading, gapPercent)
        }

        val leadingSide = weeklyComparisons.firstOrNull()?.leading ?: return null
        if (weeklyComparisons.any { it.leading != leadingSide || it.gapPercent < MIN_PERCENT_CHANGE }) {
            return null
        }

        val averageGap = weeklyComparisons.map { it.gapPercent }.average().roundToInt()
        val leadingLabel = leadingSide.label
        val trailingLabel = leadingSide.opposite.label
        return context.facts(
            kind = WeeklyAnalyticsInsightKind.PUSH_PULL_GAP,
            details = "leading=$leadingLabel|trailing=$trailingLabel|averageGapPercent=$averageGap|weeks=$COMPLETE_WEEK_COUNT",
            fallback = "$trailingLabel volume trailed $leadingLabel by about $averageGap% in each of the last 3 weeks.",
            allowedMuscles = emptySet(),
            numbers = setOf("$averageGap%", COMPLETE_WEEK_COUNT.toString())
        )
    }

    private fun strengthChange(
        context: InsightContext,
        momentum: StrengthMomentumState,
        improving: Boolean
    ): WeeklyAnalyticsInsightFacts? {
        val candidates = momentum.muscles.filter { muscle ->
            val percent = muscle.percentChange ?: return@filter false
            muscle.status == MomentumDataStatus.READY &&
                muscle.confidence == MomentumConfidence.STANDARD &&
                !muscle.hasMixedContributors &&
                if (improving) percent >= STRENGTH_CHANGE_THRESHOLD else percent <= -STRENGTH_CHANGE_THRESHOLD
        }
        val selected = if (improving) {
            candidates.maxByOrNull { it.percentChange ?: 0f }
        } else {
            candidates.minByOrNull { it.percentChange ?: 0f }
        } ?: return null

        val magnitude = abs(selected.percentChange ?: return null).roundToInt()
        val direction = if (improving) "up" else "down"
        val kind = if (improving) {
            WeeklyAnalyticsInsightKind.STRENGTH_IMPROVEMENT
        } else {
            WeeklyAnalyticsInsightKind.STRENGTH_DECLINE
        }
        return context.facts(
            kind = kind,
            details = "muscle=${selected.muscle}|direction=$direction|percent=$magnitude|confidence=standard",
            fallback = "${selected.muscle} strength momentum is $direction $magnitude% across your recent sessions.",
            allowedMuscles = setOf(selected.muscle),
            numbers = setOf("$magnitude%")
        )
    }

    private suspend fun staleMuscle(context: InsightContext): WeeklyAnalyticsInsightFacts? {
        val selected = workoutDao.getMuscleLastTrainedDates()
            .asSequence()
            .filterNot { it.muscle.equals(CARDIO_MUSCLE, ignoreCase = true) }
            .map { freshness ->
                val lastTrained = Instant.ofEpochMilli(freshness.lastTrained).atZone(context.now.zone)
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    lastTrained.toLocalDate(),
                    context.now.toLocalDate()
                ).toInt()
                freshness.muscle to days
            }
            .filter { (_, days) -> days >= STALE_MUSCLE_DAYS }
            .maxByOrNull { (_, days) -> days }
            ?: return null

        val (muscle, days) = selected
        return context.facts(
            kind = WeeklyAnalyticsInsightKind.STALE_MUSCLE,
            details = "muscle=$muscle|daysSinceLastTrained=$days",
            fallback = "$muscle has not been trained in $days days.",
            allowedMuscles = setOf(muscle),
            numbers = setOf(days.toString())
        )
    }

    private fun weeklyVolumeChange(context: InsightContext): WeeklyAnalyticsInsightFacts? {
        val previousWeekStart = context.currentWeekStart.minusWeeks(1)
        // Same local wall-clock point last week, including across DST changes.
        val previousCutoff = context.now.minusWeeks(1)
        val current = context.rows
            .inWindow(context.currentWeekStart, context.now)
            .weightedAggregate()
        val previous = context.rows
            .inWindow(previousWeekStart, previousCutoff)
            .weightedAggregate()

        if (
            current.setCount < MIN_COMPARABLE_WEEK_SETS ||
            previous.setCount < MIN_COMPARABLE_WEEK_SETS ||
            previous.volume <= 0f
        ) {
            return null
        }

        val percent = ((current.volume - previous.volume) / previous.volume) * 100f
        if (abs(percent) < MIN_PERCENT_CHANGE) return null
        val magnitude = abs(percent).roundToInt()
        val direction = if (percent > 0f) "up" else "down"
        return context.facts(
            kind = WeeklyAnalyticsInsightKind.WEEKLY_VOLUME_CHANGE,
            details = "direction=$direction|percent=$magnitude|currentSets=${current.setCount}|previousSets=${previous.setCount}",
            fallback = "Your volume is $direction $magnitude% versus the same point last week.",
            allowedMuscles = emptySet(),
            numbers = setOf("$magnitude%")
        )
    }

    private fun fallbackInsight(context: InsightContext): WeeklyAnalyticsInsightFacts {
        val currentWeekRows = context.rows.inWindow(context.currentWeekStart, context.now)
        val trainingDays = currentWeekRows
            .map { Instant.ofEpochMilli(it.timestampMs).atZone(context.now.zone).toLocalDate() }
            .distinct()
            .size

        if (trainingDays >= MIN_CONSISTENCY_DAYS) {
            return context.facts(
                kind = WeeklyAnalyticsInsightKind.WEEKLY_CONSISTENCY,
                details = "trainingDays=$trainingDays",
                fallback = "You have trained on $trainingDays days so far this week.",
                allowedMuscles = emptySet(),
                numbers = setOf(trainingDays.toString())
            )
        }

        val recentStart = context.now.minusDays(FALLBACK_LOOKBACK_DAYS.toLong())
        val topMuscle = context.rows
            .inWindow(recentStart, context.now)
            .groupingBy { it.muscle }
            .eachCount()
            .filterValues { it >= MIN_TOP_MUSCLE_SETS }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.lowercase(Locale.US) }
            )
            .firstOrNull()

        if (topMuscle != null) {
            return context.facts(
                kind = WeeklyAnalyticsInsightKind.MOST_TRAINED_MUSCLE,
                details = "muscle=${topMuscle.key}|workingSets=${topMuscle.value}|lookbackDays=$FALLBACK_LOOKBACK_DAYS",
                fallback = "${topMuscle.key} is your most-trained muscle over the last 4 weeks, with ${topMuscle.value} working sets.",
                allowedMuscles = setOf(topMuscle.key),
                numbers = setOf(topMuscle.value.toString(), "4")
            )
        }

        if (trainingDays == 1) {
            return context.facts(
                kind = WeeklyAnalyticsInsightKind.WEEKLY_CONSISTENCY,
                details = "trainingDays=1",
                fallback = "You have trained on 1 day so far this week.",
                allowedMuscles = emptySet(),
                numbers = setOf("1")
            )
        }

        return context.facts(
            kind = WeeklyAnalyticsInsightKind.BUILDING_HISTORY,
            details = "availableWorkingSets=${context.rows.size}",
            fallback = "Log a few working sets to unlock a weekly training insight.",
            allowedMuscles = emptySet(),
            numbers = emptySet()
        )
    }

    private fun InsightContext.facts(
        kind: WeeklyAnalyticsInsightKind,
        details: String,
        fallback: String,
        allowedMuscles: Set<String>,
        numbers: Set<String>
    ): WeeklyAnalyticsInsightFacts {
        val weekStartMs = currentWeekStart.toInstant().toEpochMilli()
        return WeeklyAnalyticsInsightFacts(
            kind = kind,
            weekStartEpochMs = weekStartMs,
            subjectKey = "weekly:${currentWeekStart.toLocalDate()}",
            canonicalFacts = "kind=${kind.name}|weekStart=${currentWeekStart.toLocalDate()}|$details",
            fallbackText = fallback,
            allowedMuscleNames = allowedMuscles,
            knownMuscleNames = knownMuscleNames,
            allowedNumbers = numbers
        )
    }

    private fun List<AnalyticsInsightSetRow>.inWindow(
        start: ZonedDateTime,
        endExclusive: ZonedDateTime
    ): List<AnalyticsInsightSetRow> {
        val startMs = start.toInstant().toEpochMilli()
        val endMs = endExclusive.toInstant().toEpochMilli()
        return filter { it.timestampMs >= startMs && it.timestampMs < endMs }
    }

    private fun List<AnalyticsInsightSetRow>.weightedAggregate(
        muscles: Set<String>? = null
    ): WeightedAggregate {
        var volume = 0f
        var setCount = 0
        for (row in this) {
            if (muscles != null && row.muscle.lowercase(Locale.US) !in muscles) continue
            val weight = row.weight ?: continue
            val reps = row.reps ?: continue
            if (weight <= 0f || reps <= 0) continue
            volume += weight * reps
            setCount++
        }
        return WeightedAggregate(volume, setCount)
    }

    private data class InsightContext(
        val now: ZonedDateTime,
        val currentWeekStart: ZonedDateTime,
        val rows: List<AnalyticsInsightSetRow>,
        val knownMuscleNames: Set<String>
    )

    private data class WeightedAggregate(val volume: Float, val setCount: Int)

    private data class WeeklySideComparison(
        val leading: TrainingSide,
        val gapPercent: Float
    )

    private enum class TrainingSide(val label: String) {
        PUSH("Push"),
        PULL("Pull");

        val opposite: TrainingSide
            get() = if (this == PUSH) PULL else PUSH
    }

    companion object {
        private const val COMPLETE_WEEK_COUNT = 3
        private const val MIN_PUSH_PULL_SETS_PER_SIDE = 3
        private const val MIN_COMPARABLE_WEEK_SETS = 5
        private const val MIN_PERCENT_CHANGE = 20f
        private const val STRENGTH_CHANGE_THRESHOLD = 5f
        private const val STALE_MUSCLE_DAYS = 14
        private const val FALLBACK_LOOKBACK_DAYS = 28
        private const val MIN_CONSISTENCY_DAYS = 2
        private const val MIN_TOP_MUSCLE_SETS = 3
        private const val CARDIO_MUSCLE = "Cardio"
        private val PUSH_MUSCLES = setOf("chest", "shoulders", "triceps")
        private val PULL_MUSCLES = setOf("back", "biceps")
    }
}
