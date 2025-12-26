package com.example.gymtime.data.repository

import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.ui.exercise.WorkoutStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
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

    fun getOngoingWorkoutFlow(): Flow<Workout?> {
        return workoutDao.getOngoingWorkout()
    }

    suspend fun getWorkoutById(workoutId: Long): Flow<Workout?> {
        return workoutDao.getWorkoutById(workoutId)
    }

    suspend fun getSetsForWorkoutExercise(workoutId: Long, exerciseId: Long): Flow<List<Set>> {
        return setDao.getSetsForWorkout(workoutId) // Filter in VM or add specifc DAO method if needed
    }
    
    // Add specific DAO method logic simulation (VM filtered it)
    suspend fun getSetsForWorkout(workoutId: Long): Flow<List<Set>> {
        return setDao.getSetsForWorkout(workoutId)
    }

    suspend fun getLastWorkoutSetsForExercise(exerciseId: Long, workoutId: Long): List<Set> {
        return setDao.getLastWorkoutSetsForExercise(exerciseId, workoutId)
    }

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
        val workout = workoutDao.getWorkoutById(workoutId).first() ?: return
        val updatedWorkout = workout.copy(endTime = Date())
        workoutDao.updateWorkout(updatedWorkout)
    }
    
    fun getWorkoutOverview(workoutId: Long, routineDayId: Long?): Flow<List<WorkoutExerciseSummary>> {
        return if (routineDayId != null) {
            setDao.getWorkoutExerciseSummariesWithRoutine(workoutId, routineDayId)
        } else {
            setDao.getWorkoutExerciseSummaries(workoutId)
        }
    }

    // Calculation logic extracted from ViewModel
    fun calculateWorkoutStats(workout: Workout?, overview: List<WorkoutExerciseSummary>): WorkoutStats {
        val totalSets = overview.sumOf { it.setCount }
        val totalVolume = overview.sumOf { summary ->
            ((summary.bestWeight ?: 0f) * summary.setCount).toDouble()
        }.toFloat()

        val duration = workout?.let {
            val durationMs = Date().time - it.startTime.time
             // Simple formatting logic reuse
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

    suspend fun getWorkoutDatesWithWorkingSets(): List<String> {
        return workoutDao.getWorkoutDatesWithWorkingSets()
    }

    suspend fun getYearToDateWorkoutCount(): Int {
        return workoutDao.getYearToDateWorkoutCount()
    }

    suspend fun getTotalVolume(startTime: Long, endTime: Long): Float {
        return setDao.getTotalVolume(startTime, endTime) ?: 0f
    }
}
