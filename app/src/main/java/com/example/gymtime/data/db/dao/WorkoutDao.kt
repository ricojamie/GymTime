package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import kotlinx.coroutines.flow.Flow

import androidx.room.Delete

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun getWorkoutById(id: Long): Flow<Workout>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getOngoingWorkout(): Flow<Workout?>

    @Query("""
        SELECT
            w.*,
            GROUP_CONCAT(DISTINCT e.targetMuscle) as muscleGroups,
            SUM(CASE WHEN s.isWarmup = 0 AND s.weight IS NOT NULL AND s.reps IS NOT NULL THEN s.weight * s.reps ELSE 0 END) as totalVolume,
            SUM(CASE WHEN s.isWarmup = 0 THEN 1 ELSE 0 END) as workingSetCount
        FROM workouts w
        LEFT JOIN sets s ON w.id = s.workoutId
        LEFT JOIN exercises e ON s.exerciseId = e.id
        GROUP BY w.id
        ORDER BY w.startTime DESC
    """)
    fun getWorkoutsWithMuscles(): Flow<List<WorkoutWithMuscles>>

    // Get distinct workout dates where at least 1 working set was logged (for streak calculation)
    @Query("""
        SELECT DISTINCT DATE(w.startTime / 1000, 'unixepoch') as workoutDate
        FROM workouts w
        INNER JOIN sets s ON w.id = s.workoutId
        WHERE s.isWarmup = 0
        ORDER BY w.startTime DESC
    """)
    suspend fun getWorkoutDatesWithWorkingSets(): List<String>

    // Get count of workouts with at least 1 working set this year (YTD)
    @Query("""
        SELECT COUNT(DISTINCT w.id)
        FROM workouts w
        INNER JOIN sets s ON w.id = s.workoutId
        WHERE s.isWarmup = 0
          AND strftime('%Y', w.startTime / 1000, 'unixepoch') = strftime('%Y', 'now')
    """)
    suspend fun getYearToDateWorkoutCount(): Int
}
