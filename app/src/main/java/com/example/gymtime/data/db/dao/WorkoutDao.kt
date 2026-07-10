package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.data.db.entity.DailyVolume
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.data.db.entity.MuscleFreshness
import kotlinx.coroutines.flow.Flow
import java.util.Date

import androidx.room.Delete

data class RoutineDayStat(
    val routineDayId: Long,
    val timesCompleted: Int,
    val lastPerformed: Date?
)

data class RoutineWorkoutSummaryRow(
    val workoutId: Long,
    val routineDayId: Long?,
    val startTime: Date,
    val endTime: Date?,
    val totalVolume: Float,
    val workingSetCount: Int
)

data class RoutineSetRow(
    val exerciseId: Long,
    val exerciseName: String,
    val workoutId: Long,
    val startTime: Date,
    val weight: Float,
    val reps: Int
)

data class RatedWorkoutSetInfo(
    val workoutId: Long,
    val startTime: Date,
    val endTime: Date?,
    val rating: Int,
    val setId: Long?,
    val exerciseId: Long?,
    val targetMuscle: String?,
    val weight: Float?,
    val reps: Int?,
    val isWarmup: Boolean?
)

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

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutByIdSync(id: Long): Workout?

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts")
    suspend fun getAllWorkoutsSync(): List<Workout>

    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getOngoingWorkout(): Flow<Workout?>

    // Reopen a finished workout (for "Resume" feature)
    @Query("UPDATE workouts SET endTime = NULL WHERE id = :workoutId")
    suspend fun reopenWorkout(workoutId: Long)

    // Get the most recently completed workout
    @Query("SELECT * FROM workouts WHERE endTime IS NOT NULL ORDER BY endTime DESC LIMIT 1")
    suspend fun getLastCompletedWorkout(): Workout?

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

    // Get daily weighted volume for the heat map (last 365 days).
    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN s.weight IS NOT NULL AND s.reps IS NOT NULL THEN s.weight * s.reps ELSE 0 END), 0) as dailyVol,
            COUNT(s.id) as workingSetCount,
            MIN(w.startTime) as date
        FROM workouts w
        INNER JOIN sets s ON w.id = s.workoutId
        WHERE s.isWarmup = 0
          AND w.startTime > (strftime('%s', 'now', '-1 year') * 1000)
        GROUP BY date(w.startTime / 1000, 'unixepoch')
        ORDER BY w.startTime ASC
    """)
    suspend fun getDailyVolumeForHeatMap(): List<DailyVolume>

    // Muscle Distribution (Last 30 days)
    @Query("""
        SELECT 
            e.targetMuscle as muscle, 
            COUNT(s.id) as setVolume
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.isWarmup = 0
          AND s.timestamp > (strftime('%s', 'now', '-30 days') * 1000)
        GROUP BY e.targetMuscle
        ORDER BY setVolume DESC
    """)
    suspend fun getMuscleSetCountsLast30Days(): List<MuscleDistribution>

    @Query("""
        SELECT
            e.targetMuscle as muscle,
            COUNT(s.id) as setVolume
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.isWarmup = 0
          AND s.timestamp BETWEEN :startTime AND :endTime
        GROUP BY e.targetMuscle
        ORDER BY setVolume DESC
    """)
    suspend fun getMuscleSetCountsInRange(startTime: Long, endTime: Long): List<MuscleDistribution>

    // Muscle Freshness (Last trained date for each muscle)
    @Query("""
        SELECT 
            e.targetMuscle as muscle,
            MAX(s.timestamp) as lastTrained
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.isWarmup = 0
        GROUP BY e.targetMuscle
    """)
    suspend fun getMuscleLastTrainedDates(): List<MuscleFreshness>

    @Query("""
        SELECT
            w.id as workoutId,
            w.startTime as startTime,
            w.endTime as endTime,
            w.rating as rating,
            s.id as setId,
            s.exerciseId as exerciseId,
            e.targetMuscle as targetMuscle,
            s.weight as weight,
            s.reps as reps,
            s.isWarmup as isWarmup
        FROM workouts w
        LEFT JOIN sets s ON w.id = s.workoutId
        LEFT JOIN exercises e ON s.exerciseId = e.id
        WHERE w.endTime IS NOT NULL
          AND w.rating IS NOT NULL
          AND w.startTime BETWEEN :startTime AND :endTime
        ORDER BY w.startTime ASC, s.timestamp ASC
    """)
    suspend fun getRatedWorkoutSetInfo(
        startTime: Long,
        endTime: Long
    ): List<RatedWorkoutSetInfo>

    // Per-day completion stats for a routine (times done + most recent).
    @Query("""
        SELECT routineDayId, COUNT(*) as timesCompleted, MAX(startTime) as lastPerformed
        FROM workouts
        WHERE routineId = :routineId AND routineDayId IS NOT NULL AND endTime IS NOT NULL
        GROUP BY routineDayId
    """)
    fun getRoutineDayStats(routineId: Long): Flow<List<RoutineDayStat>>

    // All completed workouts for a routine with volume + working sets (for routine stats).
    @Query("""
        SELECT w.id as workoutId, w.routineDayId, w.startTime, w.endTime,
            COALESCE(SUM(CASE WHEN s.isWarmup = 0 AND s.weight IS NOT NULL AND s.reps IS NOT NULL THEN s.weight * s.reps ELSE 0 END), 0) as totalVolume,
            COALESCE(SUM(CASE WHEN s.isWarmup = 0 THEN 1 ELSE 0 END), 0) as workingSetCount
        FROM workouts w
        LEFT JOIN sets s ON s.workoutId = w.id
        WHERE w.routineId = :routineId AND w.endTime IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startTime DESC
    """)
    suspend fun getCompletedWorkoutsForRoutine(routineId: Long): List<RoutineWorkoutSummaryRow>

    // All working sets logged in a routine's completed workouts (for per-exercise progression).
    @Query("""
        SELECT e.id as exerciseId, e.name as exerciseName, w.id as workoutId, w.startTime,
            s.weight, s.reps
        FROM sets s
        INNER JOIN workouts w ON w.id = s.workoutId
        INNER JOIN exercises e ON e.id = s.exerciseId
        WHERE w.routineId = :routineId AND w.endTime IS NOT NULL
          AND s.isWarmup = 0 AND s.weight IS NOT NULL AND s.reps IS NOT NULL
        ORDER BY w.startTime ASC
    """)
    suspend fun getWorkingSetsForRoutine(routineId: Long): List<RoutineSetRow>
}
