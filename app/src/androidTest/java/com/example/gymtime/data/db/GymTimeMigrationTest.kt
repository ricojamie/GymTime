package com.example.gymtime.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gymtime.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymTimeMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GymTimeDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val v11DatabaseName = "migration-v11-to-v14-test"
    private val v5DatabaseName = "migration-v5-to-v14-test"

    @After
    fun cleanup() {
        context.deleteDatabase(v11DatabaseName)
        context.deleteDatabase(v5DatabaseName)
    }

    @Test
    fun migrate11To14_preservesRoutinesAndRoutineBackedWorkoutContext() = runBlocking {
        helper.createDatabase(v11DatabaseName, 11).apply {
            createVersion11Schema()
            seedVersion11RoutineData()
            close()
        }

        helper.runMigrationsAndValidate(
            v11DatabaseName,
            14,
            true,
            *DatabaseModule.ALL_MIGRATIONS
        )

        val database = openMigratedDatabase(v11DatabaseName)
        try {
            val routines = database.routineDao().getAllRoutinesSync()
            assertEquals(1, routines.size)

            val routine = routines.single()
            assertEquals("Push/Pull", routine.name)
            assertTrue(routine.isActive)
            assertEquals(
                "nextDayOrderIndex should point to the first real day order after migration",
                2,
                routine.nextDayOrderIndex
            )

            val days = database.routineDao().getDaysForRoutineSync(routine.id)
            assertEquals(listOf("Push Day", "Pull Day"), days.map { it.name })
            assertEquals(listOf(2, 4), days.map { it.orderIndex })

            val dayByOrder = days.associateBy { it.orderIndex }
            val allExercises = database.routineDao().getAllRoutineExercises()

            val pushExercises = allExercises
                .filter { it.routineDayId == dayByOrder.getValue(2).id }
                .sortedBy { it.orderIndex }
            assertEquals(listOf(10L, 11L), pushExercises.map { it.exerciseId })
            assertEquals(3, pushExercises.first().targetSets)
            assertNull(pushExercises.first().targetRepsMin)
            assertNull(pushExercises.first().targetRestSeconds)
            assertNull(pushExercises.first().notes)

            val pullExercises = allExercises
                .filter { it.routineDayId == dayByOrder.getValue(4).id }
                .sortedBy { it.orderIndex }
            assertEquals(listOf(12L), pullExercises.map { it.exerciseId })
            assertEquals("superset-a", pullExercises.single().supersetGroupId)
            assertEquals(1, pullExercises.single().supersetOrderIndex)

            val workouts = database.workoutDao().getAllWorkoutsSync().sortedBy { it.id }
            val routineWorkout = workouts.first { it.id == 100L }
            assertEquals(dayByOrder.getValue(4).id, routineWorkout.routineDayId)
            assertEquals(routine.id, routineWorkout.routineId)
            assertEquals("Push/Pull", routineWorkout.routineNameSnapshot)
            assertEquals("Pull Day", routineWorkout.routineDayNameSnapshot)
            assertTrue(routineWorkout.startedFromRoutine)

            val freeWorkout = workouts.first { it.id == 101L }
            assertNull(freeWorkout.routineDayId)
            assertNull(freeWorkout.routineId)
            assertNull(freeWorkout.routineNameSnapshot)
            assertNull(freeWorkout.routineDayNameSnapshot)
            assertFalse(freeWorkout.startedFromRoutine)

            assertTrue(database.workoutPlanDao().getInstancesForWorkoutSync(100L).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate5To14_preservesLegacyRoutineExercisesIntoSyntheticDay() = runBlocking {
        helper.createDatabase(v5DatabaseName, 5).apply {
            createVersion5Schema()
            seedVersion5RoutineData()
            close()
        }

        helper.runMigrationsAndValidate(
            v5DatabaseName,
            14,
            true,
            *DatabaseModule.ALL_MIGRATIONS
        )

        val database = openMigratedDatabase(v5DatabaseName)
        try {
            val routines = database.routineDao().getAllRoutinesSync()
            assertEquals(1, routines.size)

            val routine = routines.single()
            assertEquals("Legacy Full Body", routine.name)
            assertFalse(routine.isActive)
            assertEquals(0, routine.nextDayOrderIndex)

            val days = database.routineDao().getDaysForRoutineSync(routine.id)
            assertEquals(1, days.size)
            assertEquals("Day 1", days.single().name)
            assertEquals(0, days.single().orderIndex)

            val migratedExercises = database.routineDao().getAllRoutineExercises()
                .filter { it.routineDayId == days.single().id }
                .sortedBy { it.orderIndex }

            assertEquals(listOf(20L, 21L), migratedExercises.map { it.exerciseId })
            assertEquals(listOf(0, 1), migratedExercises.map { it.orderIndex })
            assertEquals(listOf(3, 3), migratedExercises.map { it.targetSets })
        } finally {
            database.close()
        }
    }

    private fun openMigratedDatabase(databaseName: String): GymTimeDatabase {
        return Room.databaseBuilder(
            context,
            GymTimeDatabase::class.java,
            databaseName
        )
            .addMigrations(*DatabaseModule.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun SupportSQLiteDatabase.createVersion11Schema() {
        execStatements(
            """
            CREATE TABLE IF NOT EXISTS `exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetMuscle` TEXT NOT NULL,
                `logType` TEXT NOT NULL,
                `defaultDistanceUnit` TEXT NOT NULL DEFAULT 'MILES',
                `isCustom` INTEGER NOT NULL,
                `notes` TEXT,
                `defaultRestSeconds` INTEGER NOT NULL,
                `isStarred` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `workouts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `startTime` INTEGER NOT NULL,
                `endTime` INTEGER,
                `name` TEXT,
                `note` TEXT,
                `rating` INTEGER,
                `ratingNote` TEXT,
                `routineDayId` INTEGER
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workoutId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `weight` REAL,
                `calories` REAL,
                `reps` INTEGER,
                `rpe` REAL,
                `durationSeconds` INTEGER,
                `distanceValue` REAL,
                `distanceUnit` TEXT,
                `distanceMeters` REAL,
                `isWarmup` INTEGER NOT NULL,
                `isComplete` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `note` TEXT,
                `supersetGroupId` TEXT,
                `supersetOrderIndex` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `routines` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `routine_days` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `routineId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `routine_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `routineDayId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `supersetGroupId` TEXT,
                `supersetOrderIndex` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`routineDayId`) REFERENCES `routine_days`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `muscle_groups` (
                `name` TEXT NOT NULL,
                PRIMARY KEY(`name`)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_sets_workoutId` ON `sets` (`workoutId`)",
            "CREATE INDEX IF NOT EXISTS `index_sets_exerciseId` ON `sets` (`exerciseId`)",
            "CREATE INDEX IF NOT EXISTS `index_sets_timestamp` ON `sets` (`timestamp`)",
            "CREATE INDEX IF NOT EXISTS `index_sets_supersetGroupId` ON `sets` (`supersetGroupId`)",
            "CREATE INDEX IF NOT EXISTS `index_routine_days_routineId` ON `routine_days` (`routineId`)",
            "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineDayId` ON `routine_exercises` (`routineDayId`)",
            "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)",
            "CREATE INDEX IF NOT EXISTS `index_workouts_routineDayId` ON `workouts` (`routineDayId`)"
        )
    }

    private fun SupportSQLiteDatabase.seedVersion11RoutineData() {
        execStatements(
            "INSERT INTO `muscle_groups` (`name`) VALUES ('Chest'), ('Back')",
            """
            INSERT INTO `exercises`
                (`id`, `name`, `targetMuscle`, `logType`, `defaultDistanceUnit`, `isCustom`, `notes`, `defaultRestSeconds`, `isStarred`)
            VALUES
                (10, 'Bench Press', 'Chest', 'WEIGHT_REPS', 'MILES', 0, NULL, 180, 1),
                (11, 'Incline Press', 'Chest', 'WEIGHT_REPS', 'MILES', 0, NULL, 150, 0),
                (12, 'Pull-Up', 'Back', 'REPS_ONLY', 'MILES', 0, NULL, 120, 0)
            """.trimIndent(),
            "INSERT INTO `routines` (`id`, `name`, `isActive`) VALUES (1, 'Push/Pull', 1)",
            """
            INSERT INTO `routine_days` (`id`, `routineId`, `name`, `orderIndex`)
            VALUES
                (1, 1, 'Push Day', 2),
                (2, 1, 'Pull Day', 4)
            """.trimIndent(),
            """
            INSERT INTO `routine_exercises`
                (`id`, `routineDayId`, `exerciseId`, `orderIndex`, `supersetGroupId`, `supersetOrderIndex`)
            VALUES
                (1, 1, 10, 0, NULL, 0),
                (2, 1, 11, 1, NULL, 0),
                (3, 2, 12, 0, 'superset-a', 1)
            """.trimIndent(),
            """
            INSERT INTO `workouts`
                (`id`, `startTime`, `endTime`, `name`, `note`, `rating`, `ratingNote`, `routineDayId`)
            VALUES
                (100, 1700000000000, 1700003600000, 'Routine Workout', 'Felt strong', 5, 'Great', 2),
                (101, 1700100000000, 1700101800000, 'Open Workout', NULL, NULL, NULL, NULL)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.createVersion5Schema() {
        execStatements(
            """
            CREATE TABLE IF NOT EXISTS `exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetMuscle` TEXT NOT NULL,
                `logType` TEXT NOT NULL,
                `isCustom` INTEGER NOT NULL,
                `notes` TEXT,
                `defaultRestSeconds` INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `workouts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `startTime` INTEGER NOT NULL,
                `endTime` INTEGER,
                `name` TEXT,
                `note` TEXT,
                `rating` INTEGER,
                `ratingNote` TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workoutId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `weight` REAL,
                `reps` INTEGER,
                `rpe` REAL,
                `durationSeconds` INTEGER,
                `distanceMeters` REAL,
                `isWarmup` INTEGER NOT NULL,
                `isComplete` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `routines` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `routine_exercises` (
                `routineId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                PRIMARY KEY(`routineId`, `exerciseId`),
                FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `muscle_groups` (
                `name` TEXT NOT NULL,
                PRIMARY KEY(`name`)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_sets_workoutId` ON `sets` (`workoutId`)",
            "CREATE INDEX IF NOT EXISTS `index_sets_exerciseId` ON `sets` (`exerciseId`)",
            "CREATE INDEX IF NOT EXISTS `index_sets_timestamp` ON `sets` (`timestamp`)",
            "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)",
            "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)"
        )
    }

    private fun SupportSQLiteDatabase.seedVersion5RoutineData() {
        execStatements(
            "INSERT INTO `muscle_groups` (`name`) VALUES ('Chest'), ('Legs')",
            """
            INSERT INTO `exercises`
                (`id`, `name`, `targetMuscle`, `logType`, `isCustom`, `notes`, `defaultRestSeconds`)
            VALUES
                (20, 'Bench Press', 'Chest', 'WEIGHT_REPS', 0, NULL, 180),
                (21, 'Squat', 'Legs', 'WEIGHT_REPS', 0, NULL, 240)
            """.trimIndent(),
            "INSERT INTO `routines` (`id`, `name`) VALUES (1, 'Legacy Full Body')",
            """
            INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `orderIndex`)
            VALUES
                (1, 20, 0),
                (1, 21, 1)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.execStatements(vararg sqlStatements: String) {
        sqlStatements.forEach { execSQL(it) }
    }
}
