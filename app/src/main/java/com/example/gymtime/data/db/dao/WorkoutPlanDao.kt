package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import kotlinx.coroutines.flow.Flow

data class WorkoutPlanInstanceWithExercise(
    @Embedded val instance: WorkoutExerciseInstance,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id"
    )
    val exercise: Exercise
)

data class WorkoutPlanSummary(
    val instanceId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val targetMuscle: String,
    val setCount: Int,
    val bestWeight: Float?,
    val totalVolume: Float,
    val orderIndex: Int,
    val plannedSets: Int?,
    val repMin: Int?,
    val repMax: Int?,
    /** Effective display value: explicit plan override, otherwise exercise default. */
    val restSeconds: Int?,
    val notes: String?,
    val supersetGroupId: String?,
    val supersetOrderIndex: Int,
    val isSkipped: Boolean,
    val addedDuringWorkout: Boolean
)

@Dao
interface WorkoutPlanDao {
    @Insert
    suspend fun insertInstance(instance: WorkoutExerciseInstance): Long

    @Insert
    suspend fun insertInstances(instances: List<WorkoutExerciseInstance>)

    @Update
    suspend fun updateInstance(instance: WorkoutExerciseInstance)

    @Delete
    suspend fun deleteInstance(instance: WorkoutExerciseInstance)

    @Query("SELECT * FROM workout_exercise_instances WHERE id = :instanceId")
    suspend fun getInstanceById(instanceId: Long): WorkoutExerciseInstance?

    @Query("""
        SELECT * FROM workout_exercise_instances
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC
    """)
    fun getInstancesForWorkout(workoutId: Long): Flow<List<WorkoutExerciseInstance>>

    @Query("""
        SELECT * FROM workout_exercise_instances
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC
    """)
    suspend fun getInstancesForWorkoutSync(workoutId: Long): List<WorkoutExerciseInstance>

    @Query("""
        SELECT * FROM workout_exercise_instances
        WHERE workoutId = :workoutId
          AND exerciseId = :exerciseId
        ORDER BY orderIndex ASC
        LIMIT 1
    """)
    suspend fun getFirstInstanceForExercise(workoutId: Long, exerciseId: Long): WorkoutExerciseInstance?

    @Transaction
    @Query("""
        SELECT * FROM workout_exercise_instances
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC
    """)
    fun getPlanWithExercises(workoutId: Long): Flow<List<WorkoutPlanInstanceWithExercise>>

    @Query("""
        SELECT
            wei.id as instanceId,
            wei.exerciseId as exerciseId,
            e.name as exerciseName,
            e.targetMuscle as targetMuscle,
            COUNT(CASE WHEN s.isWarmup = 0 AND s.isComplete = 1 THEN s.id END) as setCount,
            MAX(CASE WHEN s.isWarmup = 0 AND s.isComplete = 1 THEN s.weight END) as bestWeight,
            COALESCE(SUM(CASE WHEN s.isWarmup = 0 AND s.isComplete = 1 AND s.weight IS NOT NULL AND s.reps IS NOT NULL THEN s.weight * s.reps ELSE 0 END), 0) as totalVolume,
            wei.orderIndex as orderIndex,
            wei.plannedSets as plannedSets,
            wei.repMin as repMin,
            wei.repMax as repMax,
            COALESCE(wei.restSeconds, e.defaultRestSeconds) as restSeconds,
            wei.notes as notes,
            wei.supersetGroupId as supersetGroupId,
            wei.supersetOrderIndex as supersetOrderIndex,
            wei.isSkipped as isSkipped,
            wei.addedDuringWorkout as addedDuringWorkout
        FROM workout_exercise_instances wei
        INNER JOIN exercises e ON e.id = wei.exerciseId
        LEFT JOIN sets s ON s.workoutId = wei.workoutId AND s.exerciseId = wei.exerciseId
        WHERE wei.workoutId = :workoutId
        GROUP BY wei.id
        ORDER BY wei.orderIndex ASC
    """)
    fun getWorkoutPlanSummaries(workoutId: Long): Flow<List<WorkoutPlanSummary>>

    @Query("""
        SELECT COALESCE(MAX(orderIndex), -1)
        FROM workout_exercise_instances
        WHERE workoutId = :workoutId
    """)
    suspend fun getMaxOrderIndex(workoutId: Long): Int

    @Query("""
        SELECT COUNT(*)
        FROM workout_exercise_instances
        WHERE workoutId = :workoutId
    """)
    suspend fun getInstanceCountForWorkout(workoutId: Long): Int

    @Query("""
        SELECT COUNT(*)
        FROM workout_exercise_instances wei
        INNER JOIN sets s ON s.workoutId = wei.workoutId AND s.exerciseId = wei.exerciseId
        WHERE wei.workoutId = :workoutId
    """)
    suspend fun getStartedInstanceCount(workoutId: Long): Int
}
