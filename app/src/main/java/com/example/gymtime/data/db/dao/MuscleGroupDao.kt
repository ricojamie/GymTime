package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gymtime.data.db.entity.MuscleGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleGroupDao {
    @Query("SELECT * FROM muscle_groups ORDER BY name ASC")
    fun getAllMuscleGroups(): Flow<List<MuscleGroup>>

    @Query("SELECT name FROM muscle_groups ORDER BY name ASC")
    suspend fun getAllMuscleGroupNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroup(muscleGroup: MuscleGroup)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(muscleGroups: List<MuscleGroup>)

    @Delete
    suspend fun deleteMuscleGroup(muscleGroup: MuscleGroup)

    @Query("DELETE FROM muscle_groups WHERE name = :name")
    suspend fun deleteMuscleGroupByName(name: String)

    @Query("SELECT COUNT(*) FROM exercises WHERE LOWER(targetMuscle) = LOWER(:muscleName)")
    suspend fun getExerciseCountForMuscle(muscleName: String): Int

    @Query("""
        SELECT COUNT(*) FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE LOWER(e.targetMuscle) = LOWER(:muscleName)
    """)
    suspend fun getLoggedSetCountForMuscle(muscleName: String): Int

    @Query("SELECT COUNT(*) FROM muscle_groups WHERE LOWER(name) = LOWER(:name)")
    suspend fun muscleGroupExists(name: String): Int
}
