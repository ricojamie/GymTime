package com.example.gymtime.util

import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.GeneratedNarrativeDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class IronLogBackupRoundTripTest {

    private lateinit var sourceDb: FakeDataStore
    private lateinit var targetDb: FakeDataStore

    private class FakeDataStore {
        val database: GymTimeDatabase = mockk(relaxed = true)
        val exerciseDao: ExerciseDao = mockk(relaxed = true)
        val workoutDao: WorkoutDao = mockk(relaxed = true)
        val setDao: SetDao = mockk(relaxed = true)
        val routineDao: RoutineDao = mockk(relaxed = true)
        val muscleGroupDao: MuscleGroupDao = mockk(relaxed = true)
        val workoutPlanDao: WorkoutPlanDao = mockk(relaxed = true)
        val generatedNarrativeDao: GeneratedNarrativeDao = mockk(relaxed = true)
        val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
        val volumeOrbRepository: VolumeOrbRepository = mockk(relaxed = true)

        val muscleGroups = mutableListOf<MuscleGroup>()
        val exercises = mutableListOf<Exercise>()
        val workouts = mutableListOf<Workout>()
        val sets = mutableListOf<Set>()
        val routines = mutableListOf<Routine>()
        val routineDays = mutableListOf<RoutineDay>()
        val routineExercises = mutableListOf<RoutineExercise>()
        val workoutInstances = mutableListOf<WorkoutExerciseInstance>()
        var preferences = IronLogBackupFormat.BackupPreferences()

        private var nextExerciseId = 100L
        private var nextWorkoutId = 200L
        private var nextSetId = 300L
        private var nextRoutineId = 400L
        private var nextRoutineDayId = 500L
        private var nextRoutineExerciseId = 600L
        private var nextInstanceId = 700L
        var exportTransactionCount = 0
        var importTransactionCount = 0
        var volumeOrbRefreshCount = 0
        var narrativesClearedCount = 0

        init {
            coEvery { muscleGroupDao.getAllMuscleGroupNames() } answers { muscleGroups.map { it.name }.sorted() }
            val mgCheckSlot = slot<String>()
            coEvery { muscleGroupDao.muscleGroupExists(capture(mgCheckSlot)) } answers {
                muscleGroups.count { it.name.equals(mgCheckSlot.captured, ignoreCase = true) }
            }
            val mgInsertSlot = slot<MuscleGroup>()
            coEvery { muscleGroupDao.insertMuscleGroup(capture(mgInsertSlot)) } answers {
                if (muscleGroups.none { it.name.equals(mgInsertSlot.captured.name, ignoreCase = true) }) {
                    muscleGroups.add(mgInsertSlot.captured)
                }
            }
            coEvery { muscleGroupDao.deleteAllMuscleGroups() } answers { muscleGroups.clear() }

            coEvery { exerciseDao.getAllExercisesSync() } answers { exercises.sortedBy { it.id } }
            val exNameSlot = slot<String>()
            coEvery { exerciseDao.getExerciseByName(capture(exNameSlot)) } answers {
                exercises.firstOrNull { it.name.equals(exNameSlot.captured, ignoreCase = true) }
            }
            val exSetCountSlot = slot<Long>()
            coEvery { exerciseDao.getSetCountForExercise(capture(exSetCountSlot)) } answers {
                sets.count { it.exerciseId == exSetCountSlot.captured }
            }
            val exSlot = slot<Exercise>()
            coEvery { exerciseDao.insertExercise(capture(exSlot)) } answers {
                val saved = exSlot.captured.copy(id = nextExerciseId++)
                exercises.add(saved)
                saved.id
            }
            val exUpdateSlot = slot<Exercise>()
            coEvery { exerciseDao.updateExercise(capture(exUpdateSlot)) } answers {
                val idx = exercises.indexOfFirst { it.id == exUpdateSlot.captured.id }
                if (idx >= 0) exercises[idx] = exUpdateSlot.captured
            }
            coEvery { exerciseDao.deleteAllExercises() } answers { exercises.clear() }

            coEvery { workoutDao.getAllWorkoutsSync() } answers { workouts.sortedBy { it.id } }
            coEvery { workoutDao.getOngoingWorkoutSync() } answers {
                workouts.filter { it.endTime == null }.maxByOrNull { it.startTime.time }
            }
            coEvery { workoutDao.getCompletedWorkingSetTimestamps() } answers {
                sets.filter { !it.isWarmup && it.isComplete }
                    .map { it.timestamp.time }
                    .sortedDescending()
            }
            val wSlot = slot<Workout>()
            coEvery { workoutDao.insertWorkout(capture(wSlot)) } answers {
                val saved = wSlot.captured.copy(id = nextWorkoutId++)
                workouts.add(saved)
                saved.id
            }
            coEvery { workoutDao.deleteAllWorkouts() } answers { workouts.clear() }

            coEvery { setDao.getAllSets() } answers { sets.sortedBy { it.id } }
            val setsForWorkoutSlot = slot<Long>()
            coEvery { setDao.getSetsForWorkoutSync(capture(setsForWorkoutSlot)) } answers {
                sets.filter { it.workoutId == setsForWorkoutSlot.captured }
                    .sortedWith(compareBy<Set> { it.timestamp.time }.thenBy { it.id })
            }
            val sSlot = slot<Set>()
            coEvery { setDao.insertSet(capture(sSlot)) } answers {
                val saved = sSlot.captured.copy(id = nextSetId++)
                sets.add(saved)
                saved.id
            }
            coEvery { setDao.deleteAllSets() } answers { sets.clear() }

            coEvery { routineDao.getAllRoutinesSync() } answers { routines.sortedBy { it.id } }
            coEvery { routineDao.getActiveRoutineSync() } answers { routines.firstOrNull { it.isActive } }
            coEvery { routineDao.clearActiveRoutine() } answers {
                for (i in routines.indices) {
                    routines[i] = routines[i].copy(isActive = false)
                }
            }
            val rSlot = slot<Routine>()
            coEvery { routineDao.insertRoutine(capture(rSlot)) } answers {
                val saved = rSlot.captured.copy(id = nextRoutineId++)
                routines.add(saved)
                saved.id
            }
            coEvery { routineDao.deleteAllRoutines() } answers { routines.clear() }

            coEvery { routineDao.getAllRoutineDays() } answers { routineDays.sortedBy { it.id } }
            val rdSlot = slot<RoutineDay>()
            coEvery { routineDao.insertRoutineDay(capture(rdSlot)) } answers {
                val saved = rdSlot.captured.copy(id = nextRoutineDayId++)
                routineDays.add(saved)
                saved.id
            }
            coEvery { routineDao.deleteAllRoutineDays() } answers { routineDays.clear() }

            coEvery { routineDao.getAllRoutineExercises() } answers { routineExercises.sortedBy { it.id } }
            val reSlot = slot<RoutineExercise>()
            coEvery { routineDao.insertRoutineExercise(capture(reSlot)) } answers {
                val saved = reSlot.captured.copy(id = nextRoutineExerciseId++)
                routineExercises.add(saved)
                saved.id
            }
            coEvery { routineDao.deleteAllRoutineExercises() } answers { routineExercises.clear() }

            coEvery { workoutPlanDao.getAllInstancesSync() } answers { workoutInstances.sortedBy { it.id } }
            val instWorkoutSlot = slot<Long>()
            coEvery { workoutPlanDao.getInstancesForWorkoutSync(capture(instWorkoutSlot)) } answers {
                workoutInstances.filter { it.workoutId == instWorkoutSlot.captured }.sortedBy { it.orderIndex }
            }
            val instSlot = slot<WorkoutExerciseInstance>()
            coEvery { workoutPlanDao.insertInstance(capture(instSlot)) } answers {
                val saved = instSlot.captured.copy(id = nextInstanceId++)
                workoutInstances.add(saved)
                saved.id
            }
            coEvery { workoutPlanDao.deleteAllInstances() } answers { workoutInstances.clear() }

            coEvery { generatedNarrativeDao.deleteAll() } answers { narrativesClearedCount++ }

            coEvery { userPreferencesRepository.exportPortablePreferences() } answers { preferences }
            coEvery { userPreferencesRepository.getRestDaysPerWeekSync() } answers { preferences.restDaysPerWeek }
            val prefSlot = slot<IronLogBackupFormat.BackupPreferences>()
            val replaceSlot = slot<Boolean>()
            coEvery {
                userPreferencesRepository.restorePortablePreferences(capture(prefSlot), capture(replaceSlot))
            } answers {
                if (replaceSlot.captured) {
                    preferences = IronLogBackupFormat.sanitizePreferences(prefSlot.captured)
                } else {
                    preferences = preferences.copy(
                        bestStreak = maxOf(preferences.bestStreak, prefSlot.captured.bestStreak)
                    )
                }
            }
            val bestSlot = slot<Int>()
            coEvery { userPreferencesRepository.updateBestStreakIfNeeded(capture(bestSlot)) } answers {
                if (bestSlot.captured > preferences.bestStreak) {
                    preferences = preferences.copy(bestStreak = bestSlot.captured)
                }
            }

            coEvery { volumeOrbRepository.refresh() } answers { volumeOrbRefreshCount++ }
        }

        fun exporter(): IronLogExporter = IronLogExporter(
            database = database,
            exerciseDao = exerciseDao,
            workoutDao = workoutDao,
            setDao = setDao,
            routineDao = routineDao,
            muscleGroupDao = muscleGroupDao,
            workoutPlanDao = workoutPlanDao,
            userPreferencesRepository = userPreferencesRepository,
            runInTransaction = { block ->
                exportTransactionCount++
                block()
            },
            appVersionProvider = { "1.11.3" },
            clockMsProvider = { 1_732_620_000_000L }
        )

        fun importer(): IronLogImporter = IronLogImporter(
            database = database,
            exerciseDao = exerciseDao,
            workoutDao = workoutDao,
            setDao = setDao,
            routineDao = routineDao,
            muscleGroupDao = muscleGroupDao,
            workoutPlanDao = workoutPlanDao,
            generatedNarrativeDao = generatedNarrativeDao,
            userPreferencesRepository = userPreferencesRepository,
            volumeOrbRepository = volumeOrbRepository,
            runInTransaction = { block ->
                importTransactionCount++
                block()
            }
        )
    }

    @Before
    fun setup() {
        sourceDb = FakeDataStore()
        targetDb = FakeDataStore()
    }

    @Test
    fun `round trips all 8 tables manifest preferences multiline notes and quotes in replace mode`() = runTest {
        sourceDb.preferences = IronLogBackupFormat.BackupPreferences(
            userName = "Jamie",
            themeColor = "custom",
            customThemeColor = "#FF6B35",
            themeFont = "oswald",
            darkMode = false,
            timerAutoStart = false,
            timerAudioEnabled = false,
            timerVibrateEnabled = true,
            keepScreenOn = true,
            restDaysPerWeek = 3,
            bestStreak = 19,
            monthlyReportEnabled = false,
            barWeight = 35f,
            loadingSides = 1,
            availablePlates = listOf(45f, 25f, 10f, 5f, 2.5f),
            usePlateInventory = true,
            plateInventory = mapOf(45f to 4, 25f to 2, 10f to 6),
            preferredWeightUnit = "lbs"
        )
        sourceDb.muscleGroups.addAll(listOf(MuscleGroup("Back"), MuscleGroup("Cardio"), MuscleGroup(WARMUP_MUSCLE_GROUP)))
        sourceDb.exercises.add(
            Exercise(
                id = 10L,
                name = "Row, \"Barbell\"",
                targetMuscle = "Back",
                logType = LogType.WEIGHT_REPS,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = true,
                notes = "Line 1\nLine 2\r\nLine \"3\"",
                defaultRestSeconds = 120,
                isStarred = true,
                repTarget = 8
            )
        )
        sourceDb.exercises.add(
            Exercise(
                id = 11L,
                name = "Treadmill Run",
                targetMuscle = "Cardio",
                logType = LogType.DISTANCE_TIME,
                defaultDistanceUnit = DistanceUnit.KILOMETERS,
                isCustom = true,
                notes = null,
                defaultRestSeconds = 60,
                isStarred = false,
                repTarget = null
            )
        )
        sourceDb.routines.add(Routine(id = 20L, name = "PPL", isActive = true, nextDayOrderIndex = 1))
        sourceDb.routineDays.add(RoutineDay(id = 30L, routineId = 20L, name = "Pull Day", orderIndex = 0))
        sourceDb.routineExercises.add(
            RoutineExercise(
                id = 40L,
                routineDayId = 30L,
                exerciseId = 10L,
                orderIndex = 0,
                targetSets = 4,
                targetRepsMin = 6,
                targetRepsMax = 10,
                targetRestSeconds = 120,
                notes = "Pause at top\nControl negative",
                supersetGroupId = "re-ss-1",
                supersetOrderIndex = 0
            )
        )
        sourceDb.workouts.add(
            Workout(
                id = 50L,
                startTime = Date(1_700_000_000_000L),
                endTime = Date(1_700_000_360_000L),
                name = "Pull, Heavy",
                note = "Felt strong\nSecond line\r\nThird \"quoted\" line",
                rating = 5,
                ratingNote = "PR day\rKeep volume",
                routineDayId = 30L,
                routineId = 20L,
                routineNameSnapshot = "PPL",
                routineDayNameSnapshot = "Pull Day",
                startedFromRoutine = true
            )
        )
        sourceDb.sets.add(
            Set(
                id = 60L,
                workoutId = 50L,
                exerciseId = 10L,
                weight = 185f,
                reps = 8,
                rpe = 8.5f,
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(1_700_000_060_000L),
                note = "Strict form,\nno straps",
                supersetGroupId = "ss-1",
                supersetOrderIndex = 0
            )
        )
        sourceDb.sets.add(
            Set(
                id = 61L,
                workoutId = 50L,
                exerciseId = 11L,
                weight = null,
                calories = 320f,
                reps = null,
                rpe = null,
                durationSeconds = 1500,
                distanceValue = 5.0f,
                distanceUnit = DistanceUnit.KILOMETERS,
                distanceMeters = 5000f,
                isWarmup = false,
                isComplete = true,
                timestamp = Date(1_700_000_120_000L),
                note = "Easy pace"
            )
        )
        sourceDb.workoutInstances.add(
            WorkoutExerciseInstance(
                id = 70L,
                workoutId = 50L,
                exerciseId = 10L,
                routineExerciseId = 40L,
                orderIndex = 0,
                plannedSets = 4,
                repMin = 6,
                repMax = 10,
                restSeconds = 120,
                notes = "Instance note\nline 2",
                supersetGroupId = "ss-1",
                supersetOrderIndex = 1,
                isSkipped = false,
                addedDuringWorkout = true
            )
        )

        val zipBytes = ByteArrayOutputStream()
        val exportResult = sourceDb.exporter().export(zipBytes)
        assertEquals(1, sourceDb.exportTransactionCount)
        assertEquals(1, exportResult.workoutExerciseInstanceCount)
        assertTrue(exportResult.preferencesIncluded)

        // Verify manifest.json is the first ZIP entry with v2 metadata
        val zipEntryNames = readZipEntryNames(zipBytes.toByteArray())
        assertEquals(IronLogBackupFormat.MANIFEST_ENTRY_NAME, zipEntryNames[0])
        assertEquals(IronLogBackupFormat.PREFERENCES_ENTRY_NAME, zipEntryNames[1])

        val importResult = targetDb.importer().import(
            ByteArrayInputStream(zipBytes.toByteArray()),
            IronLogImporter.ImportMode.REPLACE
        )
        assertEquals(1, targetDb.importTransactionCount)
        assertEquals(1, targetDb.volumeOrbRefreshCount)
        assertEquals(1, targetDb.narrativesClearedCount)
        assertTrue("Unexpected warnings: ${importResult.errors}", importResult.errors.isEmpty())
        assertEquals(2, importResult.formatVersion)
        assertTrue(importResult.preferencesRestored)
        assertEquals(2, importResult.exercisesImported)
        assertEquals(1, importResult.workoutsImported)
        assertEquals(2, importResult.setsImported)
        assertEquals(1, importResult.routinesImported)
        assertEquals(1, importResult.routineDaysImported)
        assertEquals(1, importResult.routineExercisesImported)
        assertEquals(1, importResult.workoutExerciseInstancesImported)

        val importedWorkout = targetDb.workouts.single()
        assertEquals("Felt strong\nSecond line\r\nThird \"quoted\" line", importedWorkout.note)
        assertEquals("PR day\rKeep volume", importedWorkout.ratingNote)
        assertEquals("Strict form,\nno straps", targetDb.sets.first().note)
        assertEquals("Line 1\nLine 2\r\nLine \"3\"", targetDb.exercises.first().notes)
        assertEquals(8, targetDb.exercises.first().repTarget)
        assertTrue(targetDb.exercises.first().isStarred)

        val importedInstance = targetDb.workoutInstances.single()
        assertEquals(importedWorkout.id, importedInstance.workoutId)
        assertEquals(targetDb.exercises.first().id, importedInstance.exerciseId)
        assertEquals(targetDb.routineExercises.single().id, importedInstance.routineExerciseId)
        assertEquals("Instance note\nline 2", importedInstance.notes)
        assertTrue(importedInstance.addedDuringWorkout)

        // Portable preferences fully restored
        assertEquals("Jamie", targetDb.preferences.userName)
        assertEquals("custom", targetDb.preferences.themeColor)
        assertEquals("#FF6B35", targetDb.preferences.customThemeColor)
        assertEquals("oswald", targetDb.preferences.themeFont)
        assertFalse(targetDb.preferences.darkMode)
        assertEquals(3, targetDb.preferences.restDaysPerWeek)
        assertEquals(19, targetDb.preferences.bestStreak)
        assertEquals(35f, targetDb.preferences.barWeight)
        assertEquals(1, targetDb.preferences.loadingSides)
        assertTrue(targetDb.preferences.usePlateInventory)
        assertEquals(mapOf(45f to 4, 25f to 2, 10f to 6), targetDb.preferences.plateInventory)
    }

    @Test
    fun `fresh install merge preserves customizations on pre-seeded starter exercises`() = runTest {
        // Source user customized starter exercise "Barbell Bench Press" and "Pull-Ups"
        sourceDb.muscleGroups.addAll(listOf(MuscleGroup("Chest"), MuscleGroup("Back")))
        sourceDb.exercises.add(
            Exercise(
                id = 1L,
                name = "Barbell Bench Press",
                targetMuscle = "Chest",
                logType = LogType.WEIGHT_REPS,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = false,
                notes = "Paused on chest",
                defaultRestSeconds = 210,
                isStarred = true,
                repTarget = 5
            )
        )
        sourceDb.exercises.add(
            Exercise(
                id = 2L,
                name = "Pull-Ups",
                targetMuscle = "Back",
                logType = LogType.WEIGHT_REPS, // user changed starter REPS_ONLY -> WEIGHT_REPS
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = false,
                notes = "Weighted dip belt",
                defaultRestSeconds = 150,
                isStarred = true,
                repTarget = 8
            )
        )

        // Target simulates a fresh install with unedited starter exercises (0 logged sets)
        targetDb.muscleGroups.addAll(listOf(MuscleGroup("Chest"), MuscleGroup("Back")))
        targetDb.exercises.add(
            Exercise(
                id = 500L,
                name = "Barbell Bench Press",
                targetMuscle = "Chest",
                logType = LogType.WEIGHT_REPS,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = false,
                notes = null,
                defaultRestSeconds = 180,
                isStarred = false,
                repTarget = null
            )
        )
        targetDb.exercises.add(
            Exercise(
                id = 501L,
                name = "Pull-Ups",
                targetMuscle = "Back",
                logType = LogType.REPS_ONLY,
                defaultDistanceUnit = DistanceUnit.MILES,
                isCustom = false,
                notes = null,
                defaultRestSeconds = 120,
                isStarred = false,
                repTarget = null
            )
        )

        val zipBytes = ByteArrayOutputStream()
        sourceDb.exporter().export(zipBytes)

        val result = targetDb.importer().import(
            ByteArrayInputStream(zipBytes.toByteArray()),
            IronLogImporter.ImportMode.MERGE
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.exercisesImported)
        assertEquals(2, result.exercisesUpdated)
        assertEquals(2, targetDb.exercises.size)

        val bench = targetDb.exercises.first { it.name == "Barbell Bench Press" }
        assertTrue(bench.isStarred)
        assertEquals(5, bench.repTarget)
        assertEquals(210, bench.defaultRestSeconds)
        assertEquals("Paused on chest", bench.notes)

        val pullUps = targetDb.exercises.first { it.name == "Pull-Ups" }
        assertEquals(LogType.WEIGHT_REPS, pullUps.logType)
        assertTrue(pullUps.isStarred)
        assertEquals(8, pullUps.repTarget)
        assertEquals("Weighted dip belt", pullUps.notes)
    }

    @Test
    fun `re-importing the same backup in merge mode is idempotent and skips duplicates`() = runTest {
        sourceDb.muscleGroups.add(MuscleGroup("Legs"))
        sourceDb.exercises.add(
            Exercise(
                id = 1L, name = "Squat", targetMuscle = "Legs", logType = LogType.WEIGHT_REPS,
                isCustom = false, notes = null, defaultRestSeconds = 180
            )
        )
        sourceDb.routines.add(Routine(id = 1L, name = "Strength", isActive = true, nextDayOrderIndex = 0))
        sourceDb.routineDays.add(RoutineDay(id = 1L, routineId = 1L, name = "Legs A", orderIndex = 0))
        sourceDb.routineExercises.add(
            RoutineExercise(id = 1L, routineDayId = 1L, exerciseId = 1L, orderIndex = 0, targetSets = 3)
        )
        sourceDb.workouts.add(
            Workout(
                id = 1L,
                startTime = Date(1_700_000_000_000L),
                endTime = Date(1_700_003_600_000L),
                name = "Legs A",
                note = null
            )
        )
        sourceDb.sets.add(
            Set(
                id = 1L, workoutId = 1L, exerciseId = 1L, weight = 225f, reps = 5,
                rpe = 8f, durationSeconds = null, distanceMeters = null,
                isWarmup = false, isComplete = true, timestamp = Date(1_700_000_100_000L),
                supersetGroupId = "group-a", supersetOrderIndex = 0
            )
        )
        sourceDb.workoutInstances.add(
            WorkoutExerciseInstance(
                id = 1L, workoutId = 1L, exerciseId = 1L, routineExerciseId = 1L,
                orderIndex = 0, plannedSets = 3, supersetGroupId = "group-a", supersetOrderIndex = 0
            )
        )

        val zipBytes = ByteArrayOutputStream()
        sourceDb.exporter().export(zipBytes)

        val first = targetDb.importer().import(
            ByteArrayInputStream(zipBytes.toByteArray()),
            IronLogImporter.ImportMode.MERGE
        )
        assertEquals(1, first.workoutsImported)
        assertEquals(1, first.setsImported)
        assertEquals(1, first.routinesImported)
        assertEquals(1, first.workoutExerciseInstancesImported)
        // Superset ID remapped on MERGE and aligned between set & workout instance
        val remappedGroup = targetDb.sets.single().supersetGroupId
        assertNotNull(remappedGroup)
        assertNotEquals("group-a", remappedGroup)
        assertEquals(remappedGroup, targetDb.workoutInstances.single().supersetGroupId)

        // Second import of identical ZIP should add ZERO duplicates
        val second = targetDb.importer().import(
            ByteArrayInputStream(zipBytes.toByteArray()),
            IronLogImporter.ImportMode.MERGE
        )
        assertTrue(second.errors.isEmpty())
        assertEquals(0, second.exercisesImported)
        assertEquals(0, second.routinesImported)
        assertEquals(0, second.workoutsImported)
        assertEquals(0, second.setsImported)
        assertEquals(0, second.workoutExerciseInstancesImported)
        assertEquals(4, second.duplicatesSkipped) // 1 routine + 1 workout + 1 set + 1 instance
        assertEquals(1, targetDb.routines.size)
        assertEquals(1, targetDb.workouts.size)
        assertEquals(1, targetDb.sets.size)
        assertEquals(1, targetDb.workoutInstances.size)
    }

    @Test
    fun `preserves duplicate exercise names without merging distinct rows`() = runTest {
        sourceDb.muscleGroups.addAll(listOf(MuscleGroup("Back"), MuscleGroup("Biceps")))
        sourceDb.exercises.add(
            Exercise(id = 1L, name = "Cable Row", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = true, notes = "Wide", defaultRestSeconds = 90)
        )
        sourceDb.exercises.add(
            Exercise(id = 2L, name = "Cable Row", targetMuscle = "Biceps", logType = LogType.WEIGHT_TIME, isCustom = true, notes = "Narrow", defaultRestSeconds = 60)
        )

        val zipBytes = ByteArrayOutputStream()
        sourceDb.exporter().export(zipBytes)

        val result = targetDb.importer().import(
            ByteArrayInputStream(zipBytes.toByteArray()),
            IronLogImporter.ImportMode.MERGE
        )
        assertEquals(2, result.exercisesImported)
        assertEquals(2, targetDb.exercises.size)
        assertEquals(setOf("Back", "Biceps"), targetDb.exercises.map { it.targetMuscle }.toSet())
    }

    @Test
    fun `rejects non-zip random-zip future-format and truncated-csv before touching DB`() = runTest {
        // Pre-populate target DB to prove REPLACE never wipes on invalid backups
        targetDb.workouts.add(
            Workout(id = 99L, startTime = Date(1_700_000_000_000L), endTime = Date(1_700_001_000_000L), name = "Keep Me", note = null)
        )

        // 1. Plain text (not a ZIP)
        val nonZipError = runCatching {
            targetDb.importer().import(ByteArrayInputStream("not a zip".toByteArray()), IronLogImporter.ImportMode.REPLACE)
        }.exceptionOrNull()
        assertTrue(nonZipError is InvalidBackupException)
        assertEquals(0, targetDb.importTransactionCount)
        assertEquals(1, targetDb.workouts.size)

        // 2. Random ZIP containing unrelated files (no IronLog tables)
        val randomZip = buildZip("README.txt" to "hello", "photo.jpg" to "binary")
        val randomZipError = runCatching {
            targetDb.importer().import(ByteArrayInputStream(randomZip), IronLogImporter.ImportMode.REPLACE)
        }.exceptionOrNull()
        assertTrue(randomZipError is InvalidBackupException)
        assertEquals(0, targetDb.importTransactionCount)
        assertEquals(1, targetDb.workouts.size)

        // 3. Future manifest formatVersion
        val futureZip = buildZip(
            "manifest.json" to """{"formatVersion": 99, "weightUnit": "lbs"}""",
            "muscle_groups.csv" to "name\nChest\n"
        )
        val futureError = runCatching {
            targetDb.importer().import(ByteArrayInputStream(futureZip), IronLogImporter.ImportMode.REPLACE)
        }.exceptionOrNull()
        assertTrue(futureError is InvalidBackupException)
        assertTrue(futureError!!.message!!.contains("v99"))
        assertEquals(0, targetDb.importTransactionCount)

        // 4. Truncated / unterminated quoted CSV field
        val corruptCsvZip = buildZip(
            "exercises.csv" to "id,name\n1,\"Unterminated quote\n"
        )
        val corruptError = runCatching {
            targetDb.importer().import(ByteArrayInputStream(corruptCsvZip), IronLogImporter.ImportMode.REPLACE)
        }.exceptionOrNull()
        assertTrue(corruptError is InvalidBackupException)
        assertEquals(0, targetDb.importTransactionCount)
        assertEquals(1, targetDb.workouts.size)

        // 5. Missing required header column
        val missingHeaderZip = buildZip("exercises.csv" to "wrong_col\nfoo\n")
        val headerError = runCatching {
            targetDb.importer().import(ByteArrayInputStream(missingHeaderZip), IronLogImporter.ImportMode.REPLACE)
        }.exceptionOrNull()
        assertTrue(headerError is InvalidBackupException)
        assertEquals(0, targetDb.importTransactionCount)
    }

    @Test
    fun `exporter closes outputStream even when DAO query throws`() = runTest {
        coEvery { sourceDb.exerciseDao.getAllExercisesSync() } throws IllegalStateException("DB read error")

        var closed = false
        val trackingStream = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val thrown = runCatching { sourceDb.exporter().export(trackingStream) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertTrue("outputStream must be closed on DAO failure", closed)
    }

    @Test
    fun `importer handles case-insensitive booleans windows paths empty fallbacks auto-muscle and ongoing guard`() = runTest {
        // Target already has an active ongoing workout
        targetDb.workouts.add(
            Workout(id = 1L, startTime = Date(1_700_050_000_000L), endTime = null, name = "Current Open", note = null)
        )

        val zip = buildZip(
            "__MACOSX/._sets.csv" to "junk",
            "backup\\exercises.csv" to (
                "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget\n" +
                    "1,Custom Stretch,Warmup,DURATION,MILES,TRUE,,60,1,\n" +
                    "2,Mystery Lift,,WEIGHT_REPS,MILES,0,,90,FALSE,\n"
                ),
            "backup\\routines.csv" to (
                "id,name,isActive,nextDayOrderIndex\n" +
                    "1,,TRUE,0\n"
                ),
            "backup\\routine_days.csv" to (
                "id,routineId,name,orderIndex\n" +
                    "10,1,,0\n"
                ),
            "backup\\workouts.csv" to (
                "id,startTime,endTime,name,note,rating,ratingNote,routineDayId\n" +
                    "5,1700000000000,,Imported Open,,4,,10\n"
                ),
            "backup\\sets.csv" to (
                "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex\n" +
                    "1,5,1,,,,0,300,,,,FALSE,1,1700000600000,,,0\n" +
                    "2,5,2,100,,10,8.5,,2.5,KILOMETERS,,0,TRUE,1700000900000,,,0\n"
                )
        )

        val result = targetDb.importer().import(ByteArrayInputStream(zip), IronLogImporter.ImportMode.MERGE)
        assertEquals(2, result.exercisesImported)
        assertEquals(1, result.routinesImported)
        assertEquals(1, result.routineDaysImported)
        assertEquals(1, result.workoutsImported)
        assertEquals(2, result.setsImported)

        // Empty cells used proper fallbacks and auto-created muscle groups
        val stretch = targetDb.exercises.first { it.name == "Custom Stretch" }
        assertEquals(WARMUP_MUSCLE_GROUP, stretch.targetMuscle)
        assertTrue(stretch.isCustom)
        assertTrue(stretch.isStarred)

        val mystery = targetDb.exercises.first { it.name == "Mystery Lift" }
        assertEquals("Other", mystery.targetMuscle)
        assertFalse(mystery.isCustom)
        assertTrue(targetDb.muscleGroups.any { it.name == "Other" })
        assertTrue(targetDb.muscleGroups.any { it.name == WARMUP_MUSCLE_GROUP })

        assertEquals("Imported Routine", targetDb.routines.single().name)
        assertTrue(targetDb.routines.single().isActive)
        assertEquals("Day", targetDb.routineDays.single().name)

        // Extra ongoing workout was auto-closed at last set timestamp & legacy routine fields backfilled
        val importedWorkout = targetDb.workouts.first { it.name == "Imported Open" }
        assertEquals(1_700_000_900_000L, importedWorkout.endTime?.time)
        assertEquals(targetDb.routines.single().id, importedWorkout.routineId)
        assertTrue(importedWorkout.startedFromRoutine)
        assertEquals("Imported Routine", importedWorkout.routineNameSnapshot)
        assertEquals("Day", importedWorkout.routineDayNameSnapshot)

        // Warmup library exercise forced isWarmup = true; distanceMeters computed from distanceValue+unit
        val warmupSet = targetDb.sets.first { it.exerciseId == stretch.id }
        assertTrue(warmupSet.isWarmup)
        val cardioSet = targetDb.sets.first { it.exerciseId == mystery.id }
        assertFalse(cardioSet.isWarmup)
        assertEquals(2500f, cardioSet.distanceMeters!!, 0.01f)
    }

    @Test
    fun `importer rejects NaN and Infinity floats and enforces single active routine`() = runTest {
        targetDb.routines.add(Routine(id = 1L, name = "Existing Active", isActive = true, nextDayOrderIndex = 0))

        val zip = buildZip(
            "exercises.csv" to (
                "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget\n" +
                    "1,Squat,Legs,WEIGHT_REPS,MILES,false,,90,false,\n"
                ),
            "routines.csv" to (
                "id,name,isActive,nextDayOrderIndex\n" +
                    "1,Routine A,true,0\n" +
                    "2,Routine B,true,1\n"
                ),
            "workouts.csv" to (
                "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine\n" +
                    "1,1700000000000,1700003600000,Leg Day,,,,,,,,false\n"
                ),
            "sets.csv" to (
                "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex\n" +
                    "1,1,1,NaN,Infinity,5,-Infinity,,NaN,MILES,Infinity,false,true,1700000060000,,,0\n"
                )
        )

        val result = targetDb.importer().import(ByteArrayInputStream(zip), IronLogImporter.ImportMode.MERGE)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, targetDb.routines.count { it.isActive })
        assertEquals("Routine A", targetDb.routines.first { it.isActive }.name)

        val importedSet = targetDb.sets.single()
        assertNull(importedSet.weight)
        assertNull(importedSet.calories)
        assertNull(importedSet.rpe)
        assertNull(importedSet.distanceValue)
        assertNull(importedSet.distanceMeters)
    }

    @Test
    fun `legacy v1 out-of-order backup backfills distanceMiles and recomputes historical bestStreak`() = runTest {
        val day1 = 1_700_000_000_000L
        val day2 = day1 + 86_400_000L
        val day3 = day2 + 86_400_000L

        val zip = buildZip(
            "sets.csv" to (
                "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex\n" +
                    "1,10,5,135.0,,10,8.0,,,,1609.344,false,true,$day1,clean,,0\n" +
                    "2,11,5,140.0,,10,8.0,,,,,false,true,$day2,,,0\n" +
                    "3,12,5,145.0,,10,8.0,,,,,false,true,$day3,,,0\n"
                ),
            "workouts.csv" to (
                "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine\n" +
                    "10,$day1,${day1 + 3600000},Upper 1,,,,2,1,PPL,Push,true\n" +
                    "11,$day2,${day2 + 3600000},Upper 2,,,,,,,,false\n" +
                    "12,$day3,${day3 + 3600000},Upper 3,,,,,,,,false\n"
                ),
            "routine_days.csv" to "id,routineId,name,orderIndex\n2,1,Push,0\n",
            "routines.csv" to "id,name,isActive,nextDayOrderIndex\n1,PPL,true,0\n",
            "exercises.csv" to "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget\n5,Bench Press,Chest,WEIGHT_REPS,MILES,false,,90,false,\n"
        )

        val result = targetDb.importer().import(ByteArrayInputStream(zip), IronLogImporter.ImportMode.REPLACE)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.formatVersion)
        assertFalse(result.preferencesRestored)
        assertEquals(3, targetDb.sets.size)

        // Legacy distanceMeters backfilled distanceValue + MILES
        val firstSet = targetDb.sets.first()
        assertEquals(DistanceUnit.MILES, firstSet.distanceUnit)
        assertEquals(1.0f, firstSet.distanceValue!!, 0.01f)

        // Best streak recovered historically even without preferences.json
        assertTrue("Expected historical bestStreak >= 3, got ${targetDb.preferences.bestStreak}", targetDb.preferences.bestStreak >= 3)
    }

    private fun buildZip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun readZipEntryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return names
    }
}
