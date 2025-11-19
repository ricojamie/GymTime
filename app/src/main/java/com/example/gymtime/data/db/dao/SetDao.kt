package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Set
import kotlinx.coroutines.flow.Flow

@Dao
interface SetDao {
    @Insert
    suspend fun insertSet(set: Set): Long

    @Update
    suspend fun updateSet(set: Set)

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY timestamp DESC")
    fun getSetsForWorkout(workoutId: Long): Flow<List<Set>>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<Set>>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSetForExercise(exerciseId: Long): Set?
}
