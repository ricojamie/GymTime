package com.example.gymtime.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.Set
import java.util.Calendar
import java.util.Date
import kotlin.random.Random
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymTimeDatabase {
        val database = Room.databaseBuilder(
            context,
            GymTimeDatabase::class.java,
            "gym_time_db"
        )
        .fallbackToDestructiveMigration() // For development simplicity
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Room.Callback.onCreate called - database created for first time")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Room.Callback.onOpen called - database opened")
            }
        }).build()

        // Check and seed database immediately after creation
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                Log.d(TAG, "Checking if exercises need to be seeded...")
                populateInitialData(database.exerciseDao(), database.muscleGroupDao(), context)

                // Seed historical workouts for testing (8 weeks of PPL)
                Log.d(TAG, "Checking if historical workouts need to be seeded...")
                seedHistoricalWorkouts(database.workoutDao(), database.setDao(), database.exerciseDao())
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding database", e)
            }
        }

        return database
    }

    private suspend fun populateInitialData(
        exerciseDao: ExerciseDao,
        muscleGroupDao: MuscleGroupDao,
        context: Context
    ) {
        try {
            // Seed Muscle Groups
            val existingMuscleGroups = muscleGroupDao.getAllMuscleGroups().first()
            if (existingMuscleGroups.isEmpty()) {
                Log.d(TAG, "Seeding muscle groups...")
                val muscleGroups = listOf(
                    "Back", "Biceps", "Chest", "Core", "Legs", "Shoulder", "Triceps", "Cardio"
                ).map { MuscleGroup(it) }
                muscleGroupDao.insertAll(muscleGroups)
            }

            Log.d(TAG, "populateInitialData: Checking how many exercises are in the database...")

            // Check if any exercises already exist in the database
            // This is more reliable than checking DataStore
            val existingExercises = exerciseDao.getAllExercises().first()
            val exerciseCount = existingExercises.size
            Log.d(TAG, "Found $exerciseCount exercises in database")

            if (exerciseCount > 0) {
                Log.d(TAG, "Exercises already exist in database, skipping seed...")
                return
            }

            Log.d(TAG, "No exercises found, proceeding with seed data insertion...")

            val exercises = listOf(
                // Chest
                Exercise(name = "Barbell Bench Press", targetMuscle = "Chest", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 180),
                Exercise(name = "Incline Dumbbell Press", targetMuscle = "Chest", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 120),
                Exercise(name = "Dumbbell Flyes", targetMuscle = "Chest", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Push-Ups", targetMuscle = "Chest", logType = LogType.REPS_ONLY, isCustom = false, notes = null, defaultRestSeconds = 60),

                // Back
                Exercise(name = "Deadlift", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 240),
                Exercise(name = "Barbell Row", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 150),
                Exercise(name = "Pull-Ups", targetMuscle = "Back", logType = LogType.REPS_ONLY, isCustom = false, notes = null, defaultRestSeconds = 120),
                Exercise(name = "Lat Pulldown", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Dumbbell Row", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),

                // Legs
                Exercise(name = "Barbell Squat", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 240),
                Exercise(name = "Leg Press", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 180),
                Exercise(name = "Romanian Deadlift", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 150),
                Exercise(name = "Leg Curl", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Leg Extension", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Calf Raise", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 60),

                // Shoulders
                Exercise(name = "Overhead Press", targetMuscle = "Shoulders", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 180),
                Exercise(name = "Dumbbell Lateral Raise", targetMuscle = "Shoulders", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 60),
                Exercise(name = "Face Pull", targetMuscle = "Shoulders", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 60),

                // Biceps
                Exercise(name = "Barbell Curl", targetMuscle = "Biceps", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Hammer Curl", targetMuscle = "Biceps", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 60),

                // Triceps
                Exercise(name = "Tricep Dips", targetMuscle = "Triceps", logType = LogType.REPS_ONLY, isCustom = false, notes = null, defaultRestSeconds = 90),
                Exercise(name = "Skull Crusher", targetMuscle = "Triceps", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90),

                // Core
                Exercise(name = "Plank", targetMuscle = "Core", logType = LogType.DURATION, isCustom = false, notes = null, defaultRestSeconds = 60),
                Exercise(name = "Hanging Leg Raise", targetMuscle = "Core", logType = LogType.REPS_ONLY, isCustom = false, notes = null, defaultRestSeconds = 60),
                Exercise(name = "Cable Crunch", targetMuscle = "Core", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 60)
            )

            Log.d(TAG, "Inserting ${exercises.size} seed exercises...")
            var insertedCount = 0
            exercises.forEach { exercise ->
                exerciseDao.insertExercise(exercise)
                insertedCount++
            }
            Log.d(TAG, "Successfully inserted $insertedCount exercises")
        } catch (e: Exception) {
            Log.e(TAG, "Error during seed population", e)
        }
    }

    private suspend fun seedHistoricalWorkouts(
        workoutDao: WorkoutDao,
        setDao: SetDao,
        exerciseDao: ExerciseDao
    ) {
        try {
            // Check if we already have workouts (skip if data exists)
            val existingWorkouts = workoutDao.getAllWorkouts().first()
            if (existingWorkouts.isNotEmpty()) {
                Log.d(TAG, "Historical workouts already exist, skipping seed...")
                return
            }

            Log.d(TAG, "Seeding 8 weeks of historical Push/Pull/Legs workouts...")

            val exercises = exerciseDao.getAllExercises().first()
            val exerciseMap = exercises.associateBy { it.name }

            // Define Push/Pull/Legs exercises
            val pushExercises = listOf("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press", "Dumbbell Lateral Raise", "Skull Crusher")
            val pullExercises = listOf("Deadlift", "Barbell Row", "Pull-Ups", "Lat Pulldown", "Barbell Curl", "Hammer Curl")
            val legExercises = listOf("Barbell Squat", "Romanian Deadlift", "Leg Press", "Leg Curl", "Calf Raise")

            val calendar = Calendar.getInstance()
            var workoutCount = 0

            // Go back 8 weeks (56 days)
            for (weeksAgo in 8 downTo 1) {
                val weekNumber = 9 - weeksAgo
                val isDeloadWeek = weekNumber % 4 == 0 // Every 4th week is deload

                // 3-4 workouts per week (Monday, Wednesday, Friday, sometimes Sunday)
                val workoutDays = if (weeksAgo % 2 == 0) listOf(1, 3, 5) else listOf(1, 3, 5, 7) // Mon/Wed/Fri or Mon/Wed/Fri/Sun

                for (dayOfWeek in workoutDays) {
                    calendar.timeInMillis = System.currentTimeMillis()
                    calendar.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
                    calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    calendar.set(Calendar.HOUR_OF_DAY, 18 + Random.nextInt(3)) // 6-9 PM
                    calendar.set(Calendar.MINUTE, Random.nextInt(60))

                    val workoutType = when (workoutCount % 3) {
                        0 -> "Push"
                        1 -> "Pull"
                        else -> "Legs"
                    }

                    val selectedExercises = when (workoutType) {
                        "Push" -> pushExercises
                        "Pull" -> pullExercises
                        else -> legExercises
                    }.take(5)

                    // Create workout
                    val workoutStart = Date(calendar.timeInMillis)
                    calendar.add(Calendar.MINUTE, 45 + Random.nextInt(30)) // 45-75 min workouts
                    val workoutEnd = Date(calendar.timeInMillis)

                    val workoutId = workoutDao.insertWorkout(
                        Workout(
                            startTime = workoutStart,
                            endTime = workoutEnd,
                            name = "$workoutType Day",
                            note = null
                        )
                    )

                    // Add sets for each exercise
                    val setTimestamp = workoutStart.time
                    selectedExercises.forEach { exerciseName ->
                        val exercise = exerciseMap[exerciseName] ?: return@forEach
                        val baseWeight = getBaseWeight(exerciseName, weekNumber, isDeloadWeek)

                        // 3-5 sets per exercise
                        val numSets = 3 + Random.nextInt(3)
                        repeat(numSets) { setIndex ->
                            val isWarmup = setIndex == 0 && Random.nextBoolean()
                            val weight = if (isWarmup) baseWeight * 0.6f else baseWeight
                            val reps = getTargetReps(exerciseName, setIndex, numSets)

                            setDao.insertSet(
                                Set(
                                    workoutId = workoutId,
                                    exerciseId = exercise.id,
                                    weight = if (exercise.logType == LogType.WEIGHT_REPS) weight else null,
                                    reps = if (exercise.logType == LogType.REPS_ONLY || exercise.logType == LogType.WEIGHT_REPS) reps else null,
                                    rpe = null,
                                    durationSeconds = if (exercise.logType == LogType.DURATION) 60 + Random.nextInt(60) else null,
                                    distanceMeters = null,
                                    isWarmup = isWarmup,
                                    isComplete = true,
                                    timestamp = Date(setTimestamp + (setIndex * 3 * 60 * 1000)) // 3 min between sets
                                )
                            )
                        }
                    }

                    workoutCount++
                }
            }

            Log.d(TAG, "Successfully seeded $workoutCount historical workouts")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding historical workouts", e)
        }
    }

    private fun getBaseWeight(exerciseName: String, weekNumber: Int, isDeloadWeek: Boolean): Float {
        val baseWeights = mapOf(
            "Barbell Bench Press" to 185f,
            "Incline Dumbbell Press" to 60f,
            "Overhead Press" to 95f,
            "Dumbbell Lateral Raise" to 25f,
            "Skull Crusher" to 60f,
            "Deadlift" to 225f,
            "Barbell Row" to 135f,
            "Lat Pulldown" to 140f,
            "Barbell Curl" to 70f,
            "Hammer Curl" to 35f,
            "Barbell Squat" to 185f,
            "Romanian Deadlift" to 155f,
            "Leg Press" to 270f,
            "Leg Curl" to 90f,
            "Calf Raise" to 135f
        )

        val base = baseWeights[exerciseName] ?: 100f
        val progression = (weekNumber - 1) * 5f // 5 lbs per week progression
        val deloadMultiplier = if (isDeloadWeek) 0.9f else 1f

        return (base + progression) * deloadMultiplier
    }

    private fun getTargetReps(exerciseName: String, setIndex: Int, totalSets: Int): Int {
        // First set highest reps, gradual fatigue
        val baseReps = when {
            exerciseName.contains("Lateral Raise") || exerciseName.contains("Curl") -> 12
            exerciseName.contains("Squat") || exerciseName.contains("Deadlift") -> 6
            exerciseName == "Pull-Ups" -> 8
            else -> 8
        }

        // Simulate fatigue (lose 0-2 reps per set)
        val fatigueLoss = if (setIndex > 0) Random.nextInt(3) else 0
        return (baseReps - fatigueLoss).coerceAtLeast(3)
    }

    @Provides
    fun provideExerciseDao(database: GymTimeDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideWorkoutDao(database: GymTimeDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideSetDao(database: GymTimeDatabase): SetDao = database.setDao()

    @Provides
    fun provideRoutineDao(database: GymTimeDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideMuscleGroupDao(database: GymTimeDatabase): MuscleGroupDao = database.muscleGroupDao()
}
