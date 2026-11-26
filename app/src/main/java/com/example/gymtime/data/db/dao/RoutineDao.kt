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

data class RoutineWithNextDay(
    val routineId: Long,
    val routineName: String,
    val nextDayId: Long?,
    val nextDayName: String?,
    val nextDayOrderIndex: Int
)

data class RoutineBrowseSummary(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val dayCount: Int,
    val exerciseCount: Int,
    val nextDayName: String?
)

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("SELECT * FROM routines ORDER BY isActive DESC, name ASC")
    fun getAllRoutines(): Flow<List<Routine>>

    @Query("""
        SELECT
            r.id,
            r.name,
            r.isActive,
            COUNT(DISTINCT rd.id) AS dayCount,
            COUNT(re.id) AS exerciseCount,
            COALESCE(
                (
                    SELECT rd_next.name
                    FROM routine_days rd_next
                    WHERE rd_next.routineId = r.id
                      AND rd_next.orderIndex = r.nextDayOrderIndex
                    ORDER BY rd_next.orderIndex ASC
                    LIMIT 1
                ),
                (
                    SELECT rd_first.name
                    FROM routine_days rd_first
                    WHERE rd_first.routineId = r.id
                    ORDER BY rd_first.orderIndex ASC
                    LIMIT 1
                )
            ) AS nextDayName
        FROM routines r
        LEFT JOIN routine_days rd ON rd.routineId = r.id
        LEFT JOIN routine_exercises re ON re.routineDayId = rd.id
        GROUP BY r.id
        ORDER BY r.isActive DESC, r.name ASC
    """)
    fun getRoutineBrowseSummaries(): Flow<List<RoutineBrowseSummary>>

    @Query("SELECT * FROM routines WHERE isActive = 1")
    fun getActiveRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    fun getActiveRoutine(): Flow<Routine?>

    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRoutineSync(): Routine?

    @Query("SELECT * FROM routines WHERE id = :id")
    fun getRoutineById(id: Long): Flow<Routine?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineByIdSync(id: Long): Routine?

    @Query("UPDATE routines SET isActive = :isActive WHERE id = :routineId")
    suspend fun setRoutineActive(routineId: Long, isActive: Boolean)

    @Query("UPDATE routines SET isActive = 0")
    suspend fun clearActiveRoutine()

    @Query("UPDATE routines SET nextDayOrderIndex = :nextDayOrderIndex WHERE id = :routineId")
    suspend fun updateNextDayOrderIndex(routineId: Long, nextDayOrderIndex: Int)

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

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getDaysForRoutineSync(routineId: Long): List<RoutineDay>

    @Query("SELECT COUNT(*) FROM routine_days WHERE routineId = :routineId")
    fun getDayCountForRoutine(routineId: Long): Flow<Int>

    @Query("""
        SELECT * FROM routine_days
        WHERE routineId = :routineId
          AND orderIndex = :orderIndex
        LIMIT 1
    """)
    suspend fun getDayByOrderIndex(routineId: Long, orderIndex: Int): RoutineDay?

    @Transaction
    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    fun getRoutineDayWithExercises(dayId: Long): Flow<RoutineDayWithExercises?>

    @Transaction
    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    suspend fun getRoutineDayWithExercisesSync(dayId: Long): RoutineDayWithExercises?

    @Transaction
    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC")
    fun getDaysWithExercisesForRoutine(routineId: Long): Flow<List<RoutineDayWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getDaysWithExercisesForRoutineSync(routineId: Long): List<RoutineDayWithExercises>

    @Query("UPDATE routine_days SET orderIndex = :orderIndex WHERE id = :dayId")
    suspend fun updateDayOrderIndex(dayId: Long, orderIndex: Int)

    // Routine Exercise methods
    @Insert
    suspend fun insertRoutineExercise(routineExercise: RoutineExercise): Long

    @Insert
    suspend fun insertRoutineExercises(routineExercises: List<RoutineExercise>)

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

    @Query("SELECT * FROM routines ORDER BY id ASC")
    suspend fun getAllRoutinesSync(): List<Routine>

    @Query("SELECT * FROM routine_days ORDER BY id ASC")
    suspend fun getAllRoutineDays(): List<RoutineDay>

    @Query("SELECT * FROM routine_exercises ORDER BY id ASC")
    suspend fun getAllRoutineExercises(): List<RoutineExercise>

    @Query("DELETE FROM routine_exercises")
    suspend fun deleteAllRoutineExercises()

    @Query("DELETE FROM routine_days")
    suspend fun deleteAllRoutineDays()

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()
}
