package com.example.gymtime.data.repository

import androidx.room.withTransaction
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.ExerciseHistorySet
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.util.OneRepMaxCalculator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class PersonalRecords(
    val heaviestWeight: Set?,
    val bestE1RM: Pair<Set, Float>?,
    val bestE10RM: Pair<Set, Float>?
)

sealed interface MuscleGroupMutationResult {
    data object Success : MuscleGroupMutationResult
    data object InvalidName : MuscleGroupMutationResult
    data object DuplicateExists : MuscleGroupMutationResult
    data object ProtectedCategory : MuscleGroupMutationResult
}

sealed interface MuscleGroupDeleteResult {
    data object Deleted : MuscleGroupDeleteResult
    data class HasExercises(val exerciseCount: Int) : MuscleGroupDeleteResult
    data class BlockedByLoggedSets(val setCount: Int) : MuscleGroupDeleteResult
    data object ProtectedCategory : MuscleGroupDeleteResult
}

@Singleton
class ExerciseRepository @Inject constructor(
    private val database: GymTimeDatabase,
    private val exerciseDao: ExerciseDao,
    private val setDao: SetDao,
    private val muscleGroupDao: MuscleGroupDao
) {

    fun getAllExercises(): Flow<List<Exercise>> {
        return exerciseDao.getAllExercises()
    }

    fun getExercisesWithUsageStats(recentStartMs: Long): Flow<List<ExerciseUsageRow>> {
        return exerciseDao.getExercisesWithUsageStats(recentStartMs)
    }

    fun observeLastWorkoutSets(): Flow<List<ExerciseLastSetRow>> =
        exerciseDao.observeLastWorkoutSets()

    fun getAllMuscleGroups(): Flow<List<MuscleGroup>> {
        return muscleGroupDao.getAllMuscleGroups()
    }

    suspend fun createMuscleGroup(name: String): MuscleGroupMutationResult {
        val trimmed = name.trim()
        if (trimmed.length < 2) return MuscleGroupMutationResult.InvalidName
        if (trimmed.isWarmupMuscleGroup()) return MuscleGroupMutationResult.ProtectedCategory

        return database.withTransaction {
            if (muscleGroupDao.muscleGroupExists(trimmed) > 0) {
                return@withTransaction MuscleGroupMutationResult.DuplicateExists
            }
            muscleGroupDao.insertMuscleGroup(MuscleGroup(trimmed))
            MuscleGroupMutationResult.Success
        }
    }

    suspend fun renameMuscleGroup(oldName: String, newName: String): MuscleGroupMutationResult {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        if (trimmedOld.isWarmupMuscleGroup() || trimmedNew.isWarmupMuscleGroup()) {
            return MuscleGroupMutationResult.ProtectedCategory
        }
        if (trimmedNew.length < 2) return MuscleGroupMutationResult.InvalidName

        return database.withTransaction {
            val existing = muscleGroupDao.findByNameIgnoreCase(trimmedNew)
            if (existing != null && !existing.name.equals(trimmedOld, ignoreCase = true)) {
                return@withTransaction MuscleGroupMutationResult.DuplicateExists
            }
            if (trimmedOld != trimmedNew) {
                exerciseDao.updateExercisesTargetMuscle(trimmedOld, trimmedNew)
                muscleGroupDao.deleteMuscleGroupByName(trimmedOld)
                muscleGroupDao.insertMuscleGroup(MuscleGroup(trimmedNew))
            }
            MuscleGroupMutationResult.Success
        }
    }

    suspend fun deleteMuscleGroupSafely(
        name: String,
        reassignTo: String? = null
    ): MuscleGroupDeleteResult {
        if (name.isWarmupMuscleGroup()) return MuscleGroupDeleteResult.ProtectedCategory

        return database.withTransaction {
            val setCount = muscleGroupDao.getLoggedSetCountForMuscle(name)
            if (setCount > 0) {
                return@withTransaction MuscleGroupDeleteResult.BlockedByLoggedSets(setCount)
            }

            val exerciseCount = muscleGroupDao.getExerciseCountForMuscle(name)
            if (exerciseCount > 0) {
                if (reassignTo == null) {
                    return@withTransaction MuscleGroupDeleteResult.HasExercises(exerciseCount)
                }
                val target = reassignTo.trim().ifBlank { "Uncategorized" }
                if (target.isWarmupMuscleGroup()) {
                    return@withTransaction MuscleGroupDeleteResult.ProtectedCategory
                }
                val existingTarget = muscleGroupDao.findByNameIgnoreCase(target)
                val canonicalTarget = existingTarget?.name ?: target
                if (existingTarget == null) {
                    muscleGroupDao.insertMuscleGroup(MuscleGroup(canonicalTarget))
                }
                exerciseDao.updateExercisesTargetMuscle(name, canonicalTarget)
            }

            muscleGroupDao.deleteMuscleGroupByName(name)
            MuscleGroupDeleteResult.Deleted
        }
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

    suspend fun updateExercise(exercise: Exercise) {
        exerciseDao.updateExercise(exercise)
    }

    suspend fun getExerciseHistory(exerciseId: Long): Map<Long, List<Set>> {
        val allSets = setDao.getExerciseHistoryByWorkout(exerciseId)
        return allSets.groupBy { it.workoutId }
    }

    fun observeFullExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySet>> =
        setDao.observeFullExerciseHistory(exerciseId)

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
