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
