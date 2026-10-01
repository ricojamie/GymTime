package com.example.gymtime.domain.analytics

import androidx.room.withTransaction
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class TrainingInsightsUseCase @Inject constructor(
    private val database: GymTimeDatabase,
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val muscleGroupDao: MuscleGroupDao
) {
    /** Room only observes these tables while the new dashboard subscribes. */
    val sourceChanges: Flow<Unit>
        get() = database.invalidationTracker
            .createFlow("sets", "workouts", "exercises", "muscle_groups")
            .map { Unit }
            .flowOn(Dispatchers.IO)

    suspend fun load(
        windowDays: Int = 28,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TrainingInsightsDashboard {
        // A consistent snapshot prevents an edit from mixing old sets with new workout metadata.
        val snapshot = withContext(Dispatchers.IO) {
            database.withTransaction {
                TrainingInsightsSource(
                    sets = setDao.getPerformanceSetsWithExerciseInRange(Long.MIN_VALUE, now.toEpochMilli()),
                    exercises = exerciseDao.getAllExercisesSync(),
                    workouts = workoutDao.getAllWorkoutsSync(),
                    muscleNames = muscleGroupDao.getAllMuscleGroupNames()
                )
            }
        }
        return withContext(Dispatchers.Default) {
            TrainingInsightsCalculator.calculate(snapshot, windowDays, now, zone)
        }
    }
}
