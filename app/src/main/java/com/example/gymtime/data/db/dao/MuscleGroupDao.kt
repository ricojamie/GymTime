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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroup(muscleGroup: MuscleGroup)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(muscleGroups: List<MuscleGroup>)

    @Delete
    suspend fun deleteMuscleGroup(muscleGroup: MuscleGroup)
}
