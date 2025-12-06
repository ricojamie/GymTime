package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.gymtime.data.db.entity.Set
import kotlinx.coroutines.flow.Flow

// Data class for workout overview (exercise summary)
data class WorkoutExerciseSummary(
    val exerciseId: Long,
    val exerciseName: String,
    val targetMuscle: String,
    val setCount: Int,
    val bestWeight: Float?,
    val firstSetTimestamp: Long
)

// Data class for set with exercise info (for history)
data class SetWithExerciseInfo(
    @Embedded val set: Set,
    val exerciseName: String,
    val targetMuscle: String
)

// Data class for personal records (analytics)
data class PersonalRecordData(
    val exerciseId: Long,
    val exerciseName: String,
    val weight: Float,
    val reps: Int,
    val timestamp: java.util.Date
)

// Data class for volume by muscle (analytics)
data class MuscleVolumeData(
    val muscle: String,
    val week: String,
    val volume: Float
)

@Dao
interface SetDao {
    @Insert
    suspend fun insertSet(set: Set): Long

    @Update
    suspend fun updateSet(set: Set)

    @Delete
    suspend fun deleteSet(set: Set)

    @Query("DELETE FROM sets WHERE id = :setId")
    suspend fun deleteSetById(setId: Long)

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY timestamp DESC")
    fun getSetsForWorkout(workoutId: Long): Flow<List<Set>>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<Set>>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSetForExercise(exerciseId: Long): Set?

    // Get sets from the LAST completed workout for this exercise (excluding current workout)
    @Query("""
        SELECT s.* FROM sets s
        INNER JOIN workouts w ON s.workoutId = w.id
        WHERE s.exerciseId = :exerciseId
          AND s.workoutId != :currentWorkoutId
          AND w.endTime IS NOT NULL
        ORDER BY w.startTime DESC, s.timestamp ASC
        LIMIT 20
    """)
    suspend fun getLastWorkoutSetsForExercise(
        exerciseId: Long,
        currentWorkoutId: Long
    ): List<Set>

    // Get workout overview (all exercises in current workout, ordered by first set timestamp)
    @Query("""
        SELECT
            e.id as exerciseId,
            e.name as exerciseName,
            e.targetMuscle as targetMuscle,
            COUNT(s.id) as setCount,
            MAX(s.weight) as bestWeight,
            MIN(s.timestamp) as firstSetTimestamp
        FROM exercises e
        LEFT JOIN sets s ON e.id = s.exerciseId AND s.workoutId = :workoutId
        WHERE e.id IN (
            SELECT DISTINCT exerciseId FROM sets WHERE workoutId = :workoutId
        )
        GROUP BY e.id
        ORDER BY MIN(s.timestamp) ASC
    """)
    fun getWorkoutExerciseSummaries(workoutId: Long): Flow<List<WorkoutExerciseSummary>>

    // Get workout overview including routine exercises (for routine-based workouts)
    // Shows logged exercises + unstarted routine exercises
    @Query("""
        SELECT exerciseId, exerciseName, targetMuscle, setCount, bestWeight, firstSetTimestamp
        FROM (
            SELECT
                e.id as exerciseId,
                e.name as exerciseName,
                e.targetMuscle as targetMuscle,
                COUNT(s.id) as setCount,
                MAX(s.weight) as bestWeight,
                MIN(s.timestamp) as firstSetTimestamp
            FROM exercises e
            INNER JOIN sets s ON e.id = s.exerciseId AND s.workoutId = :workoutId
            GROUP BY e.id

            UNION ALL

            SELECT
                e.id as exerciseId,
                e.name as exerciseName,
                e.targetMuscle as targetMuscle,
                0 as setCount,
                NULL as bestWeight,
                9223372036854775807 as firstSetTimestamp
            FROM routine_exercises re
            INNER JOIN exercises e ON re.exerciseId = e.id
            WHERE re.routineDayId = :routineDayId
              AND e.id NOT IN (SELECT DISTINCT exerciseId FROM sets WHERE workoutId = :workoutId)
        )
        ORDER BY firstSetTimestamp ASC
    """)
    fun getWorkoutExerciseSummariesWithRoutine(workoutId: Long, routineDayId: Long): Flow<List<WorkoutExerciseSummary>>

    // Get all sets for exercise history screen
    @Query("""
        SELECT s.*, e.name as exerciseName, e.targetMuscle as targetMuscle
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.exerciseId = :exerciseId
        ORDER BY s.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getExerciseHistory(
        exerciseId: Long,
        limit: Int = 100,
        offset: Int = 0
    ): List<SetWithExerciseInfo>

    // Get personal best (heaviest weight) for an exercise
    @Query("""
        SELECT * FROM sets
        WHERE exerciseId = :exerciseId
          AND weight IS NOT NULL
          AND isWarmup = 0
        ORDER BY weight DESC, reps DESC
        LIMIT 1
    """)
    suspend fun getPersonalBest(exerciseId: Long): Set?

    // Get all working sets for an exercise (for E1RM/E10RM calculation)
    @Query("""
        SELECT * FROM sets
        WHERE exerciseId = :exerciseId
          AND weight IS NOT NULL
          AND reps IS NOT NULL
          AND isWarmup = 0
        ORDER BY timestamp DESC
        LIMIT 100
    """)
    suspend fun getWorkingSetsForE1RMCalculation(exerciseId: Long): List<Set>

    // Get past workouts for exercise history (grouped by workout)
    @Query("""
        SELECT s.*, w.startTime as workoutDate
        FROM sets s
        INNER JOIN workouts w ON s.workoutId = w.id
        WHERE s.exerciseId = :exerciseId
          AND w.endTime IS NOT NULL
        ORDER BY w.startTime DESC
        LIMIT 50
    """)
    suspend fun getExerciseHistoryByWorkout(exerciseId: Long): List<Set>

    // Get set count for a workout
    @Query("SELECT COUNT(*) FROM sets WHERE workoutId = :workoutId")
    suspend fun getSetCountForWorkout(workoutId: Long): Int

    // Get all sets for a workout with exercise info
    @Query("""
        SELECT s.*, e.name as exerciseName, e.targetMuscle as targetMuscle
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.workoutId = :workoutId
        ORDER BY s.timestamp ASC
    """)
    suspend fun getWorkoutSetsWithExercises(workoutId: Long): List<SetWithExerciseInfo>

    @Query("SELECT timestamp FROM sets WHERE workoutId = :workoutId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSetTimestamp(workoutId: Long): java.util.Date?

    // ===== ANALYTICS QUERIES =====

    // Training frequency - count distinct workout days in date range
    @Query("""
        SELECT COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch')) as days
        FROM sets
        WHERE timestamp BETWEEN :startDate AND :endDate
    """)
    suspend fun getTrainingDaysCount(startDate: Long, endDate: Long): Int

    // Total volume for a time period (for hero card)
    @Query("""
        SELECT SUM(s.weight * s.reps) as totalVolume
        FROM sets s
        WHERE s.weight IS NOT NULL
          AND s.reps IS NOT NULL
          AND s.isWarmup = 0
          AND s.timestamp BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalVolume(startDate: Long, endDate: Long): Float?

    // Best sets for 1RM calculation (across all exercises in date range)
    @Query("""
        SELECT * FROM sets
        WHERE weight IS NOT NULL
          AND reps IS NOT NULL
          AND isWarmup = 0
          AND timestamp BETWEEN :startDate AND :endDate
        ORDER BY weight DESC
        LIMIT 50
    """)
    suspend fun getTopSetsForE1RM(startDate: Long, endDate: Long): List<Set>

    // Enhanced personal records (all exercises)
    @Query("""
        SELECT
            e.id as exerciseId,
            e.name as exerciseName,
            s.weight as weight,
            s.reps as reps,
            s.timestamp as timestamp
        FROM exercises e
        INNER JOIN (
            SELECT exerciseId, MAX(weight) as maxWeight
            FROM sets
            WHERE weight IS NOT NULL AND isWarmup = 0
            GROUP BY exerciseId
        ) max_sets ON e.id = max_sets.exerciseId
        INNER JOIN sets s ON s.exerciseId = e.id AND s.weight = max_sets.maxWeight
        WHERE s.isWarmup = 0
        GROUP BY e.id
        ORDER BY s.timestamp DESC
    """)
    suspend fun getAllPersonalRecords(): List<PersonalRecordData>

    // Volume by muscle and week (for volume chart)
    @Query("""
        SELECT
            e.targetMuscle as muscle,
            strftime('%Y-%W', datetime(s.timestamp / 1000, 'unixepoch')) as week,
            SUM(s.weight * s.reps) as volume
        FROM sets s
        INNER JOIN exercises e ON s.exerciseId = e.id
        WHERE s.weight IS NOT NULL
          AND s.reps IS NOT NULL
          AND s.isWarmup = 0
          AND s.timestamp BETWEEN :startDate AND :endDate
        GROUP BY e.targetMuscle, week
        ORDER BY week ASC
    """)
    suspend fun getVolumeByMuscleAndWeek(startDate: Long, endDate: Long): List<MuscleVolumeData>
}
