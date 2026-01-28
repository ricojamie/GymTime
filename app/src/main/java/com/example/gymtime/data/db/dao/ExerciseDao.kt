package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExerciseById(id: Long)

    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun getExerciseById(id: Long): Flow<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseByIdSync(id: Long): Exercise?

    @Query("UPDATE exercises SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredStatus(id: Long, isStarred: Boolean)

    @Query("SELECT * FROM exercises WHERE isStarred = 1 LIMIT 3")
    fun getStarredExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getExerciseByName(name: String): Exercise?
}
