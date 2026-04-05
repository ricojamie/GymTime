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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class WorkoutStartResult(
    val workoutId: Long,
    val firstExerciseId: Long
)

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

    fun getWorkoutOverview(workoutId: Long, routineDayId: Long?): Flow<List<WorkoutExerciseSummary>> {
        return if (hasPlanBackedWorkout(routineDayId, workoutId)) {
            workoutPlanDao.getWorkoutPlanSummaries(workoutId).map { plan ->
                plan.map { item ->
                    WorkoutExerciseSummary(
                        exerciseId = item.exerciseId,
                        exerciseName = item.exerciseName,
                        targetMuscle = item.targetMuscle,
                        setCount = item.setCount,
                        bestWeight = item.bestWeight,
                        firstSetTimestamp = item.orderIndex.toLong(),
                        supersetGroupId = item.supersetGroupId
                    )
                }
            }
        } else {
            setDao.getWorkoutExerciseSummaries(workoutId)
        }
    }

    fun getWorkoutPlanSummaries(workoutId: Long): Flow<List<WorkoutPlanSummary>> =
        workoutPlanDao.getWorkoutPlanSummaries(workoutId)

    suspend fun ensureWorkoutPlanInstance(workoutId: Long, exerciseId: Long): WorkoutExerciseInstance? {
        return database.withTransaction {
            val workout = workoutDao.getWorkoutById(workoutId).first() ?: return@withTransaction null
            if (!workout.startedFromRoutine) return@withTransaction null

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
        val totalVolume = overview.sumOf { summary ->
            ((summary.bestWeight ?: 0f) * summary.setCount).toDouble()
        }.toFloat()

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

    private fun hasPlanBackedWorkout(routineDayId: Long?, workoutId: Long): Boolean {
        return routineDayId != null && workoutId >= 0
    }
}
