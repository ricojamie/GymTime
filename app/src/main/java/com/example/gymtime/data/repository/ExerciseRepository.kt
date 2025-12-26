package com.example.gymtime.data.repository

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.ui.exercise.PersonalRecords
import com.example.gymtime.util.OneRepMaxCalculator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.MuscleGroup

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val setDao: SetDao,
    private val muscleGroupDao: MuscleGroupDao
) {

    fun getAllExercises(): Flow<List<Exercise>> {
        return exerciseDao.getAllExercises()
    }

    fun getAllMuscleGroups(): Flow<List<MuscleGroup>> {
        return muscleGroupDao.getAllMuscleGroups()
    }

    suspend fun deleteExercise(exerciseId: Long) {
        exerciseDao.deleteExerciseById(exerciseId)
    }

    fun getStarredExercises(): Flow<List<Exercise>> {
        return exerciseDao.getStarredExercises()
    }

    suspend fun updateStarredStatus(exerciseId: Long, isStarred: Boolean) {
        exerciseDao.updateStarredStatus(exerciseId, isStarred)
    }

    fun getExercise(exerciseId: Long): Flow<Exercise?> {
        return exerciseDao.getExerciseById(exerciseId)
    }

    suspend fun getExerciseHistory(exerciseId: Long): Map<Long, List<Set>> {
        val allSets = setDao.getExerciseHistoryByWorkout(exerciseId)
        return allSets.groupBy { it.workoutId }
    }

    suspend fun getPersonalRecords(exerciseId: Long): PersonalRecords {
        val heaviest = setDao.getPersonalBest(exerciseId)
        val workingSets = setDao.getWorkingSetsForE1RMCalculation(exerciseId)

        // Find best E1RM
        val bestE1RM = workingSets
            .mapNotNull { set ->
                val e1rm = OneRepMaxCalculator.calculateE1RM(
                    set.weight ?: return@mapNotNull null,
                    set.reps ?: return@mapNotNull null
                )
                e1rm?.let { set to it }
            }
            .maxByOrNull { it.second }

        // Find best E10RM (premium feature)
        val bestE10RM = workingSets
            .mapNotNull { set ->
                val e10rm = OneRepMaxCalculator.calculateE10RM(
                    set.weight ?: return@mapNotNull null,
                    set.reps ?: return@mapNotNull null
                )
                e10rm?.let { set to it }
            }
            .maxByOrNull { it.second }

        return PersonalRecords(
            heaviestWeight = heaviest,
            bestE1RM = bestE1RM,
            bestE10RM = bestE10RM
        )
    }

    suspend fun getPersonalBestsByReps(exerciseId: Long): Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp> {
        val pbsWithTimestamps = setDao.getPersonalBestsWithTimestamps(exerciseId)
        val pbMap = pbsWithTimestamps.associateBy { it.reps }
        return filterDominatedPBs(pbMap)
    }

    suspend fun getHeaviestSet(exerciseId: Long): Set? {
        return setDao.getPersonalBest(exerciseId)
    }

    // Logic extracted from ViewModel
    private fun filterDominatedPBs(rawPBs: Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>): Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp> {
        val result = mutableMapOf<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>()
        val candidates = rawPBs.entries.toList()
        for (candidate in candidates) {
            val repsA = candidate.key
            val pbA = candidate.value
            var isDominated = false
            for (challenger in candidates) {
                if (candidate == challenger) continue
                val repsB = challenger.key
                val pbB = challenger.value
                val strictlyBetter = (pbB.maxWeight > pbA.maxWeight) || (repsB > repsA)
                val atLeastAsGood = (pbB.maxWeight >= pbA.maxWeight) && (repsB >= repsA)
                if (atLeastAsGood && strictlyBetter) {
                    isDominated = true
                    break
                }
            }
            if (!isDominated) result[repsA] = pbA
        }
        return result
    }
}
