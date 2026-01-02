package com.example.gymtime.data.db.dao

import androidx.room.*
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow

data class RoutineWithDays(
    @Embedded val routine: Routine,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val days: List<RoutineDay>
)

data class RoutineDayWithExercises(
    @Embedded val day: RoutineDay,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineDayId",
        entity = RoutineExercise::class
    )
    val exercises: List<RoutineExerciseWithDetails>
)

data class RoutineExerciseWithDetails(
    @Embedded val routineExercise: RoutineExercise,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id"
    )
    val exercise: Exercise
)

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("SELECT * FROM routines")
    fun getAllRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE isActive = 1")
    fun getActiveRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun getRoutineById(id: Long): Flow<Routine?>

    @Query("UPDATE routines SET isActive = :isActive WHERE id = :routineId")
    suspend fun setRoutineActive(routineId: Long, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM routines")
    fun getRoutineCount(): Flow<Int>

    // Routine Day methods
    @Insert
    suspend fun insertRoutineDay(day: RoutineDay): Long

    @Update
    suspend fun updateRoutineDay(day: RoutineDay)

    @Delete
    suspend fun deleteRoutineDay(day: RoutineDay)

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC")
    fun getDaysForRoutine(routineId: Long): Flow<List<RoutineDay>>

    @Query("SELECT COUNT(*) FROM routine_days WHERE routineId = :routineId")
    fun getDayCountForRoutine(routineId: Long): Flow<Int>

    @Transaction
    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    fun getRoutineDayWithExercises(dayId: Long): Flow<RoutineDayWithExercises?>

    // Routine Exercise methods
    @Insert
    suspend fun insertRoutineExercise(routineExercise: RoutineExercise)

    @Delete
    suspend fun deleteRoutineExercise(routineExercise: RoutineExercise)

    @Query("DELETE FROM routine_exercises WHERE routineDayId = :dayId")
    suspend fun deleteAllExercisesForDay(dayId: Long)

    @Transaction
    @Query("SELECT * FROM routine_exercises WHERE routineDayId = :dayId ORDER BY orderIndex ASC")
    fun getExercisesForDay(dayId: Long): Flow<List<RoutineExerciseWithDetails>>

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN routine_exercises re ON e.id = re.exerciseId
        WHERE re.routineDayId = :dayId
        ORDER BY re.orderIndex ASC
    """)
    fun getExerciseListForDay(dayId: Long): Flow<List<Exercise>>
}
