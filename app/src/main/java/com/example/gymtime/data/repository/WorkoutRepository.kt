package com.example.gymtime.data.repository

import androidx.room.withTransaction
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import com.example.gymtime.ui.exercise.WorkoutStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class WorkoutStartResult(
    val workoutId: Long,
    val firstExerciseId: Long
)

sealed interface WorkoutPlanEditResult {
    data object Updated : WorkoutPlanEditResult
    data object Removed : WorkoutPlanEditResult
    data object HasLoggedSets : WorkoutPlanEditResult
    data object DuplicateExercise : WorkoutPlanEditResult
    data object NotFound : WorkoutPlanEditResult
}

@Singleton
class WorkoutRepository @Inject constructor(
    private val database: GymTimeDatabase,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val workoutPlanDao: WorkoutPlanDao,
    private val volumeOrbRepository: VolumeOrbRepository
) {

    suspend fun getCurrentWorkout(): Workout {
        val ongoing = workoutDao.getOngoingWorkout().first()
        return if (ongoing != null) {
            ongoing
        } else {
            val newWorkoutId = workoutDao.insertWorkout(
                Workout(startTime = Date(), endTime = null, name = null, note = null)
            )
            workoutDao.getWorkoutById(newWorkoutId).first()!!
        }
    }

    /**
     * Starts a one-off workout whose exercise order is planned up front.
     * The snapshots support navigation and resume without linking to a routine.
     */
    suspend fun startPlannedWorkout(exerciseIds: List<Long>): WorkoutStartResult {
        val orderedExerciseIds = exerciseIds.distinct()
        require(orderedExerciseIds.isNotEmpty()) { "Select at least one exercise" }

        return database.withTransaction {
            check(workoutDao.getOngoingWorkout().first() == null) {
                "An active workout is already in progress"
            }

            val workoutId = workoutDao.insertWorkout(
                Workout(
                    startTime = Date(),
                    endTime = null,
                    name = null,
                    note = null,
                    startedFromRoutine = false
                )
            )
            workoutPlanDao.insertInstances(
                orderedExerciseIds.mapIndexed { index, exerciseId ->
                    WorkoutExerciseInstance(
                        workoutId = workoutId,
                        exerciseId = exerciseId,
                        orderIndex = index
                    )
                }
            )

            WorkoutStartResult(
                workoutId = workoutId,
                firstExerciseId = orderedExerciseIds.first()
            )
        }
    }

    fun getOngoingWorkoutFlow(): Flow<Workout?> = workoutDao.getOngoingWorkout()

    suspend fun getWorkoutById(workoutId: Long): Flow<Workout?> = workoutDao.getWorkoutById(workoutId)

    suspend fun getSetsForWorkoutExercise(workoutId: Long, exerciseId: Long): Flow<List<Set>> =
        setDao.getSetsForWorkout(workoutId)

    suspend fun getSetsForWorkout(workoutId: Long): Flow<List<Set>> = setDao.getSetsForWorkout(workoutId)

    suspend fun getLastWorkoutSetsForExercise(exerciseId: Long, workoutId: Long): List<Set> =
        setDao.getLastWorkoutSetsForExercise(exerciseId, workoutId)

    suspend fun logSet(set: Set) {
        setDao.insertSet(set)
        volumeOrbRepository.onSetLogged()
    }

    suspend fun updateSet(set: Set) {
        setDao.updateSet(set)
    }

    suspend fun deleteSet(setId: Long) {
        setDao.deleteSetById(setId)
    }

    suspend fun finishWorkout(workoutId: Long) {
        database.withTransaction {
            val workout = workoutDao.getWorkoutById(workoutId).first() ?: return@withTransaction
            val updatedWorkout = workout.copy(endTime = Date())
            workoutDao.updateWorkout(updatedWorkout)

            if (!workout.startedFromRoutine || workout.routineId == null || workout.routineDayId == null) {
                return@withTransaction
            }

            val active = routineDao.getActiveRoutineSync()
            if (active?.id != workout.routineId) return@withTransaction

            val startedCount = workoutPlanDao.getStartedInstanceCount(workoutId)
            if (startedCount <= 0) return@withTransaction

            val days = routineDao.getDaysForRoutineSync(active.id)
            if (days.isEmpty()) return@withTransaction

            val currentIndex = days.indexOfFirst { it.id == workout.routineDayId }
            if (currentIndex == -1) return@withTransaction

            val nextIndex = (currentIndex + 1) % days.size
            routineDao.updateNextDayOrderIndex(active.id, days[nextIndex].orderIndex)
        }
    }

    suspend fun reopenWorkout(workoutId: Long) {
        workoutDao.reopenWorkout(workoutId)
    }

    suspend fun getLastCompletedWorkout(): Workout? = workoutDao.getLastCompletedWorkout()

    // Plan-backed workouts (routine runs and repeats) are keyed on actual plan
    // instances, not routine linkage, so repeats of ad-hoc workouts still list
    // their planned exercises before any set is logged.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getWorkoutOverview(workoutId: Long): Flow<List<WorkoutExerciseSummary>> {
        return workoutPlanDao.getWorkoutPlanSummaries(workoutId).flatMapLatest { plan ->
            if (plan.isEmpty()) {
                setDao.getWorkoutExerciseSummaries(workoutId)
            } else {
                flowOf(
                    plan.map { item ->
                        WorkoutExerciseSummary(
                            exerciseId = item.exerciseId,
                            exerciseName = item.exerciseName,
                            targetMuscle = item.targetMuscle,
                            setCount = item.setCount,
                            bestWeight = item.bestWeight,
                            totalVolume = item.totalVolume,
                            firstSetTimestamp = item.orderIndex.toLong(),
                            supersetGroupId = item.supersetGroupId
                        )
                    }
                )
            }
        }
    }

    fun getWorkoutPlanSummaries(workoutId: Long): Flow<List<WorkoutPlanSummary>> =
        workoutPlanDao.getWorkoutPlanSummaries(workoutId)

    /**
     * Replaces an unstarted exercise in this workout's plan snapshot. The saved
     * routine is never edited; targets and superset placement stay attached to
     * the slot being replaced.
     */
    suspend fun swapWorkoutPlanExercise(
        instanceId: Long,
        replacementExerciseId: Long
    ): WorkoutPlanEditResult = database.withTransaction {
        val instance = workoutPlanDao.getInstanceById(instanceId)
            ?: return@withTransaction WorkoutPlanEditResult.NotFound
        if (instance.isSkipped) return@withTransaction WorkoutPlanEditResult.NotFound
        val workout = workoutDao.getWorkoutByIdSync(instance.workoutId)
            ?: return@withTransaction WorkoutPlanEditResult.NotFound
        if (workout.endTime != null) return@withTransaction WorkoutPlanEditResult.NotFound

        if (setDao.getSetCountForWorkoutExercise(instance.workoutId, instance.exerciseId) > 0) {
            return@withTransaction WorkoutPlanEditResult.HasLoggedSets
        }
        if (replacementExerciseId == instance.exerciseId) {
            return@withTransaction WorkoutPlanEditResult.Updated
        }

        val alreadyPlanned = workoutPlanDao.getInstancesForWorkoutSync(instance.workoutId)
            .any { !it.isSkipped && it.id != instance.id && it.exerciseId == replacementExerciseId }
        if (alreadyPlanned) return@withTransaction WorkoutPlanEditResult.DuplicateExercise

        workoutPlanDao.updateInstance(
            instance.copy(
                exerciseId = replacementExerciseId,
                routineExerciseId = null,
                addedDuringWorkout = true
            )
        )
        WorkoutPlanEditResult.Updated
    }

    /** Removes only an unstarted plan slot, preserving every logged set. */
    suspend fun removeWorkoutPlanExercise(instanceId: Long): WorkoutPlanEditResult =
        database.withTransaction {
            val instance = workoutPlanDao.getInstanceById(instanceId)
                ?: return@withTransaction WorkoutPlanEditResult.NotFound
            if (instance.isSkipped) return@withTransaction WorkoutPlanEditResult.NotFound
            val workout = workoutDao.getWorkoutByIdSync(instance.workoutId)
                ?: return@withTransaction WorkoutPlanEditResult.NotFound
            if (workout.endTime != null) return@withTransaction WorkoutPlanEditResult.NotFound

            if (setDao.getSetCountForWorkoutExercise(instance.workoutId, instance.exerciseId) > 0) {
                return@withTransaction WorkoutPlanEditResult.HasLoggedSets
            }

            workoutPlanDao.updateInstance(
                instance.copy(
                    isSkipped = true,
                    supersetGroupId = null,
                    supersetOrderIndex = 0
                )
            )

            // A one-exercise superset is not a superset. Dissolve the leftover
            // marker so overview connectors and logger rotation stay accurate.
            instance.supersetGroupId?.let { groupId ->
                val remainingGroup = workoutPlanDao.getInstancesForWorkoutSync(instance.workoutId)
                    .filter { !it.isSkipped && it.supersetGroupId == groupId }
                if (remainingGroup.size == 1) {
                    workoutPlanDao.updateInstance(
                        remainingGroup.single().copy(
                            supersetGroupId = null,
                            supersetOrderIndex = 0
                        )
                    )
                }
            }

            WorkoutPlanEditResult.Removed
        }

    suspend fun ensureWorkoutPlanInstance(workoutId: Long, exerciseId: Long): WorkoutExerciseInstance? {
        return database.withTransaction {
            workoutDao.getWorkoutById(workoutId).first() ?: return@withTransaction null
            // Blank workouts remain build-as-you-go. Existing routine and
            // one-off plans both keep newly added exercises in their order.
            if (workoutPlanDao.getInstanceCountForWorkout(workoutId) == 0) {
                return@withTransaction null
            }

            val existing = workoutPlanDao.getFirstInstanceForExercise(workoutId, exerciseId)
            if (existing != null) return@withTransaction existing

            val nextOrder = workoutPlanDao.getMaxOrderIndex(workoutId) + 1
            val instance = WorkoutExerciseInstance(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = nextOrder,
                addedDuringWorkout = true
            )
            val instanceId = workoutPlanDao.insertInstance(instance)
            workoutPlanDao.getInstanceById(instanceId)
        }
    }

    suspend fun hasWorkoutPlan(workoutId: Long): Boolean = workoutPlanDao.getInstanceCountForWorkout(workoutId) > 0

    fun calculateWorkoutStats(workout: Workout?, overview: List<WorkoutExerciseSummary>): WorkoutStats {
        val totalSets = overview.sumOf { it.setCount }
        val totalVolume = overview.sumOf { it.totalVolume.toDouble() }.toFloat()

        val duration = workout?.let {
            val durationMs = Date().time - it.startTime.time
            val minutes = durationMs / 1000 / 60
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
        } ?: "0m"

        return WorkoutStats(
            totalSets = totalSets,
            totalVolume = totalVolume,
            exerciseCount = overview.size,
            duration = duration
        )
    }

    suspend fun getWorkoutDatesWithWorkingSets(): List<String> = workoutDao.getWorkoutDatesWithWorkingSets()

    suspend fun getYearToDateWorkoutCount(): Int = workoutDao.getYearToDateWorkoutCount()

    suspend fun getTotalVolume(startTime: Long, endTime: Long): Float =
        setDao.getTotalVolume(startTime, endTime) ?: 0f
}
