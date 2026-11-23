package com.example.gymtime.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.GeneratedNarrativeDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val seedMutex = Mutex()

    private val DEFAULT_MUSCLE_GROUP_NAMES = listOf(
        "Back", "Biceps", "Chest", "Core", "Legs", "Shoulders", "Triceps", "Cardio",
        WARMUP_MUSCLE_GROUP
    )

    private val STARTER_EXERCISES = listOf(
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

    // Migration from version 1 to 2: No schema changes (placeholder for migration path)
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 1 -> 2: No schema changes needed")
            // No schema changes between v1 and v2
        }
    }

    // Migration from version 2 to 3: Add timestamp index for analytics performance
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 2 -> 3: Adding timestamp index to sets table")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sets_timestamp ON sets(timestamp)")
        }
    }

    // Migration from version 3 to 4: Add rating fields to workouts table
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 3 -> 4: Adding rating and ratingNote to workouts table")
            db.execSQL("ALTER TABLE workouts ADD COLUMN rating INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE workouts ADD COLUMN ratingNote TEXT DEFAULT NULL")
        }
    }

    // Migration from version 4 to 5: Fix Shoulder -> Shoulders
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 4 -> 5: Fixing Shoulder -> Shoulders")
            db.execSQL("UPDATE muscle_groups SET name = 'Shoulders' WHERE name = 'Shoulder'")
            db.execSQL("UPDATE exercises SET targetMuscle = 'Shoulders' WHERE targetMuscle = 'Shoulder'")
        }
    }

    // Migration from version 5 to 6: Adding routine days while preserving legacy routines
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 5 -> 6: Adding routine days structure")

            // Create routine_days table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS routine_days (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routineId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_days_routineId ON routine_days(routineId)")

            // Preserve the legacy one-routine-to-many-exercises structure by creating a
            // synthetic first day for each routine, then remapping legacy routine exercises.
            db.execSQL("ALTER TABLE routine_exercises RENAME TO routine_exercises_legacy")

            // Create new routine_exercises table with routineDayId
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS routine_exercises (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routineDayId INTEGER NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    FOREIGN KEY(routineDayId) REFERENCES routine_days(id) ON DELETE CASCADE,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_routineDayId ON routine_exercises(routineDayId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_exerciseId ON routine_exercises(exerciseId)")

            db.execSQL("""
                INSERT INTO routine_days (routineId, name, orderIndex)
                SELECT id, 'Day 1', 0
                FROM routines
            """.trimIndent())

            db.execSQL("""
                INSERT INTO routine_exercises (routineDayId, exerciseId, orderIndex)
                SELECT rd.id, legacy.exerciseId, legacy.orderIndex
                FROM routine_exercises_legacy legacy
                INNER JOIN routine_days rd
                    ON rd.routineId = legacy.routineId
                   AND rd.orderIndex = 0
            """.trimIndent())

            db.execSQL("DROP TABLE IF EXISTS routine_exercises_legacy")

            // Add routineDayId to workouts table
            db.execSQL("ALTER TABLE workouts ADD COLUMN routineDayId INTEGER DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_routineDayId ON workouts(routineDayId)")
        }
    }

    // Migration from version 6 to 7: Adding set notes and routine isActive
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 6 -> 7: Adding set notes and routine isActive")

            // Add note column to sets table
            db.execSQL("ALTER TABLE sets ADD COLUMN note TEXT DEFAULT NULL")

            // Add isActive column to routines table (default true)
            db.execSQL("ALTER TABLE routines ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        }
    }

    // Migration from version 7 to 8: Adding superset support to sets table
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 7 -> 8: Adding superset support to sets table")

            // Add supersetGroupId column (UUID linking all sets in a superset)
            db.execSQL("ALTER TABLE sets ADD COLUMN supersetGroupId TEXT DEFAULT NULL")

            // Add supersetOrderIndex column (position in rotation: 0, 1, etc.)
            db.execSQL("ALTER TABLE sets ADD COLUMN supersetOrderIndex INTEGER NOT NULL DEFAULT 0")

            // Create index for fast superset queries
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sets_supersetGroupId ON sets(supersetGroupId)")
        }
    }

    // Migration from version 8 to 9: Adding exercise starring support
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 8 -> 9: Adding isStarred column to exercises table")
            db.execSQL("ALTER TABLE exercises ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Migration from version 9 to 10: Adding superset support to routine_exercises
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 9 -> 10: Adding superset columns to routine_exercises")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN supersetGroupId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN supersetOrderIndex INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Migration from version 10 to 11: Add richer cardio logging support
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 10 -> 11: Adding cardio unit and calorie support")

            db.execSQL("ALTER TABLE exercises ADD COLUMN defaultDistanceUnit TEXT NOT NULL DEFAULT 'MILES'")

            db.execSQL("ALTER TABLE sets ADD COLUMN calories REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE sets ADD COLUMN distanceValue REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE sets ADD COLUMN distanceUnit TEXT DEFAULT NULL")

            // Existing distance rows were entered/displayed as miles and stored normalized in meters.
            // Backfill the new raw distance fields so upgrades preserve the displayed value users expect.
            db.execSQL(
                """
                UPDATE sets
                SET distanceUnit = 'MILES',
                    distanceValue = CASE
                        WHEN distanceMeters IS NOT NULL THEN distanceMeters / 1609.344
                        ELSE NULL
                    END
                WHERE distanceMeters IS NOT NULL
                """.trimIndent()
            )
        }
    }

    // Migration from version 11 to 12: Add workout plan snapshots without deleting routines
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 11 -> 12: Preserving routines and adding workout plan snapshots")

            db.execSQL("ALTER TABLE routines ADD COLUMN nextDayOrderIndex INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                UPDATE routines
                SET nextDayOrderIndex = COALESCE(
                    (SELECT MIN(orderIndex) FROM routine_days WHERE routineId = routines.id),
                    0
                )
            """.trimIndent())

            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetSets INTEGER NOT NULL DEFAULT 3")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetRepsMin INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetRepsMax INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN targetRestSeconds INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN notes TEXT DEFAULT NULL")

            db.execSQL("ALTER TABLE workouts ADD COLUMN routineId INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE workouts ADD COLUMN routineNameSnapshot TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE workouts ADD COLUMN routineDayNameSnapshot TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE workouts ADD COLUMN startedFromRoutine INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_routineId ON workouts(routineId)")
            db.execSQL("""
                UPDATE workouts
                SET routineId = (
                        SELECT rd.routineId
                        FROM routine_days rd
                        WHERE rd.id = workouts.routineDayId
                    ),
                    routineNameSnapshot = (
                        SELECT r.name
                        FROM routine_days rd
                        INNER JOIN routines r ON r.id = rd.routineId
                        WHERE rd.id = workouts.routineDayId
                    ),
                    routineDayNameSnapshot = (
                        SELECT rd.name
                        FROM routine_days rd
                        WHERE rd.id = workouts.routineDayId
                    ),
                    startedFromRoutine = CASE
                        WHEN routineDayId IS NOT NULL THEN 1
                        ELSE 0
                    END
                WHERE routineDayId IS NOT NULL
            """.trimIndent())

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workout_exercise_instances (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    workoutId INTEGER NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    routineExerciseId INTEGER DEFAULT NULL,
                    orderIndex INTEGER NOT NULL,
                    plannedSets INTEGER DEFAULT NULL,
                    repMin INTEGER DEFAULT NULL,
                    repMax INTEGER DEFAULT NULL,
                    restSeconds INTEGER DEFAULT NULL,
                    notes TEXT DEFAULT NULL,
                    supersetGroupId TEXT DEFAULT NULL,
                    supersetOrderIndex INTEGER NOT NULL DEFAULT 0,
                    isSkipped INTEGER NOT NULL DEFAULT 0,
                    addedDuringWorkout INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_instances_workoutId ON workout_exercise_instances(workoutId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_instances_exerciseId ON workout_exercise_instances(exerciseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_instances_routineExerciseId ON workout_exercise_instances(routineExerciseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_instances_supersetGroupId ON workout_exercise_instances(supersetGroupId)")
        }
    }

    // Migration from version 12 to 13: Add optional rep target to exercises
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 12 -> 13: Adding exercise rep targets")
            db.execSQL("ALTER TABLE exercises ADD COLUMN repTarget INTEGER DEFAULT NULL")
        }
    }

    // Migration from version 13 to 14: Add a derived cache for on-device AI narratives.
    internal val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 13 -> 14: Adding generated narrative cache")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `generated_narratives` (
                    `kind` TEXT NOT NULL,
                    `subjectKey` TEXT NOT NULL,
                    `workoutId` INTEGER,
                    `subjectStartEpochMs` INTEGER,
                    `sourceFingerprint` TEXT NOT NULL,
                    `promptVersion` INTEGER NOT NULL,
                    `modelName` TEXT,
                    `generatorType` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `generatedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`kind`, `subjectKey`),
                    FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_generated_narratives_workoutId` ON `generated_narratives` (`workoutId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_generated_narratives_kind_subjectStartEpochMs` ON `generated_narratives` (`kind`, `subjectStartEpochMs`)"
            )
        }
    }

    // Migration from version 14 to 15: Add composite query indexes for workouts and sets.
    internal val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running migration 14 -> 15: Adding composite indexes for workouts and sets")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_endTime_startTime` ON `workouts` (`endTime`, `startTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_startTime` ON `workouts` (`startTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sets_exerciseId_timestamp` ON `sets` (`exerciseId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sets_workoutId_exerciseId` ON `sets` (`workoutId`, `exerciseId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sets_exerciseId_workoutId` ON `sets` (`exerciseId`, `workoutId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sets_isWarmup_isComplete_timestamp` ON `sets` (`isWarmup`, `isComplete`, `timestamp`)")
        }
    }

    internal val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymTimeDatabase {
        val database = Room.databaseBuilder(
            context,
            GymTimeDatabase::class.java,
            "gym_time_db"
        )
            .addMigrations(*ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.beginTransaction()
                    try {
                        seedInitialDataSync(db)
                        installWarmupLibraryPolicyInTransaction(db)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    Log.d(TAG, "Room.Callback.onCreate called - database created and seeded")
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    installWarmupLibraryPolicy(db)
                    Log.d(TAG, "Room.Callback.onOpen called - database opened")
                }
            }).build()

        // Ensure existing/upgraded installs still receive system categories and starter data if empty.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            populateInitialData(database)
        }

        return database
    }

    private fun seedInitialDataSync(db: SupportSQLiteDatabase) {
        val muscleValues = ContentValues(1)
        for (name in DEFAULT_MUSCLE_GROUP_NAMES) {
            muscleValues.clear()
            muscleValues.put("name", name)
            db.insert("muscle_groups", SQLiteDatabase.CONFLICT_IGNORE, muscleValues)
        }

        val exerciseValues = ContentValues(9)
        for (exercise in STARTER_EXERCISES) {
            exerciseValues.clear()
            exerciseValues.put("name", exercise.name)
            exerciseValues.put("targetMuscle", exercise.targetMuscle)
            exerciseValues.put("logType", exercise.logType.name)
            exerciseValues.put("defaultDistanceUnit", exercise.defaultDistanceUnit.name)
            exerciseValues.put("isCustom", if (exercise.isCustom) 1 else 0)
            if (exercise.notes == null) {
                exerciseValues.putNull("notes")
            } else {
                exerciseValues.put("notes", exercise.notes)
            }
            exerciseValues.put("defaultRestSeconds", exercise.defaultRestSeconds)
            exerciseValues.put("isStarred", if (exercise.isStarred) 1 else 0)
            if (exercise.repTarget == null) {
                exerciseValues.putNull("repTarget")
            } else {
                exerciseValues.put("repTarget", exercise.repTarget)
            }
            db.insert("exercises", SQLiteDatabase.CONFLICT_IGNORE, exerciseValues)
        }
    }

    private suspend fun populateInitialData(database: GymTimeDatabase) {
        seedMutex.withLock {
            try {
                database.withTransaction {
                    val muscleGroupDao = database.muscleGroupDao()
                    val exerciseDao = database.exerciseDao()

                    val existingMuscleNames = muscleGroupDao.getAllMuscleGroupNames()
                    if (existingMuscleNames.isEmpty()) {
                        muscleGroupDao.insertAll(DEFAULT_MUSCLE_GROUP_NAMES.map(::MuscleGroup))
                    } else if (existingMuscleNames.none { it == WARMUP_MUSCLE_GROUP }) {
                        // Warmups is a system library category and must also reach existing installs.
                        muscleGroupDao.insertMuscleGroup(MuscleGroup(WARMUP_MUSCLE_GROUP))
                    }

                    val existingExercises = exerciseDao.getAllExercisesSync()
                    if (existingExercises.isEmpty()) {
                        exerciseDao.insertAll(STARTER_EXERCISES)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error during seed population", e)
            }
        }
    }

    /**
     * Treat the Warmups body-part category as library-only activity. The triggers keep
     * every write path safe (phone logger, Wear, edits, and imports), while the backfill
     * covers exercises that are moved into the category after they already have history.
     */
    private fun installWarmupLibraryPolicy(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            installWarmupLibraryPolicyInTransaction(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun installWarmupLibraryPolicyInTransaction(db: SupportSQLiteDatabase) {
        val triggerCount = db.query(
            """SELECT COUNT(*) FROM sqlite_master
               WHERE type = 'trigger'
                 AND name IN (
                     'enforce_warmup_library_set_insert',
                     'enforce_warmup_library_set_update',
                     'backfill_warmup_library_exercise_update'
                 )"""
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (triggerCount == 3) return

        val normalizedTarget =
            "LOWER(REPLACE(REPLACE(TRIM(targetMuscle), ' ', ''), '-', ''))"
        val normalizedNewTarget =
            "LOWER(REPLACE(REPLACE(TRIM(NEW.targetMuscle), ' ', ''), '-', ''))"

        db.execSQL(
            """UPDATE exercises
                SET targetMuscle = '$WARMUP_MUSCLE_GROUP'
                WHERE $normalizedTarget IN ('warmup', 'warmups')
                  AND targetMuscle != '$WARMUP_MUSCLE_GROUP'
            """.trimIndent()
        )
        db.execSQL(
            """DELETE FROM muscle_groups
                WHERE LOWER(REPLACE(REPLACE(TRIM(name), ' ', ''), '-', ''))
                    IN ('warmup', 'warmups')
                  AND name != '$WARMUP_MUSCLE_GROUP'
            """.trimIndent()
        )
        db.execSQL(
            """UPDATE sets
                SET isWarmup = 1
                WHERE isWarmup = 0
                  AND exerciseId IN (
                      SELECT id FROM exercises
                      WHERE $normalizedTarget IN ('warmup', 'warmups')
                  )
            """.trimIndent()
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS enforce_warmup_library_set_insert
                AFTER INSERT ON sets
                WHEN EXISTS (
                    SELECT 1 FROM exercises
                    WHERE id = NEW.exerciseId
                      AND $normalizedTarget IN ('warmup', 'warmups')
                )
                BEGIN
                    UPDATE sets SET isWarmup = 1 WHERE id = NEW.id;
                END
            """.trimIndent()
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS enforce_warmup_library_set_update
                AFTER UPDATE OF exerciseId, isWarmup ON sets
                WHEN NEW.isWarmup = 0 AND EXISTS (
                    SELECT 1 FROM exercises
                    WHERE id = NEW.exerciseId
                      AND $normalizedTarget IN ('warmup', 'warmups')
                )
                BEGIN
                    UPDATE sets SET isWarmup = 1 WHERE id = NEW.id;
                END
            """.trimIndent()
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS backfill_warmup_library_exercise_update
                AFTER UPDATE OF targetMuscle ON exercises
                WHEN $normalizedNewTarget IN ('warmup', 'warmups')
                BEGIN
                    UPDATE sets SET isWarmup = 1 WHERE exerciseId = NEW.id;
                END
            """.trimIndent()
        )
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
    fun provideWorkoutPlanDao(database: GymTimeDatabase): WorkoutPlanDao = database.workoutPlanDao()

    @Provides
    fun provideMuscleGroupDao(database: GymTimeDatabase): MuscleGroupDao = database.muscleGroupDao()

    @Provides
    fun provideGeneratedNarrativeDao(database: GymTimeDatabase): GeneratedNarrativeDao =
        database.generatedNarrativeDao()
}
