package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun getWorkoutById(id: Long): Flow<Workout>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getOngoingWorkout(): Flow<Workout?>

    @Query("""
        SELECT 
            w.*,
            GROUP_CONCAT(DISTINCT e.targetMuscle) as muscleGroups
        FROM workouts w
        LEFT JOIN sets s ON w.id = s.workoutId
        LEFT JOIN exercises e ON s.exerciseId = e.id
        GROUP BY w.id
        ORDER BY w.startTime DESC
    """)
    fun getWorkoutsWithMuscles(): Flow<List<WorkoutWithMuscles>>
}
