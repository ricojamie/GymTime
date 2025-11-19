package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Insert
    suspend fun insertRoutineExercise(routineExercise: RoutineExercise)

    @Query("SELECT * FROM routines")
    fun getAllRoutines(): Flow<List<Routine>>

    // This would be more complex, returning a RoutineWithExercises object
    // For now, keeping it simple as per the initial schema.
    @Query("SELECT * FROM routines WHERE id = :id")
    fun getRoutineById(id: Long): Flow<Routine>
}
