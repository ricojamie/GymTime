package com.example.gymtime.util

import androidx.room.withTransaction
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
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
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Date
import java.util.zip.ZipInputStream
import javax.inject.Inject

class IronLogImporter @Inject constructor(
    private val database: GymTimeDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val workoutPlanDao: WorkoutPlanDao
) {
    private var runInTransaction: suspend (suspend () -> ImportResult) -> ImportResult =
        { block -> database.withTransaction { block() } }

    internal constructor(
        database: GymTimeDatabase,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        setDao: SetDao,
        routineDao: RoutineDao,
        muscleGroupDao: MuscleGroupDao,
        workoutPlanDao: WorkoutPlanDao,
        runInTransaction: suspend (suspend () -> ImportResult) -> ImportResult
    ) : this(database, exerciseDao, workoutDao, setDao, routineDao, muscleGroupDao, workoutPlanDao) {
        this.runInTransaction = runInTransaction
    }

    data class ImportResult(
        val exercisesImported: Int,
        val workoutsImported: Int,
        val setsImported: Int,
        val routinesImported: Int,
        val routineDaysImported: Int,
        val routineExercisesImported: Int,
        val muscleGroupsImported: Int,
        val errors: List<String>,
        val workoutExerciseInstancesImported: Int = 0
    )

    private data class DeferredCsvTable(
        val headerIndex: Map<String, Int>,
        val rows: List<List<String>>
    )

    private data class RoutineExerciseLookupKey(
        val routineDayId: Long,
        val exerciseId: Long,
        val orderIndex: Int
    )

    private data class PendingRoutineExerciseMapping(
        val oldId: Long,
        val key: RoutineExerciseLookupKey
    )

    private class ImportState(
        val errors: MutableList<String> = mutableListOf(),
        val exerciseIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val workoutIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineDayIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineExerciseIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val pendingRoutineExerciseMappings: MutableList<PendingRoutineExerciseMapping> = mutableListOf(),
        val processedTables: MutableSet<String> = mutableSetOf(),
        val deferredTables: MutableMap<String, DeferredCsvTable> = mutableMapOf(),
        var activatedImportedRoutine: Boolean = false,
        var routineExerciseMapResolved: Boolean = false,
        var muscleGroupsImported: Int = 0,
        var exercisesImported: Int = 0,
        var routinesImported: Int = 0,
        var routineDaysImported: Int = 0,
        var workoutsImported: Int = 0,
        var routineExercisesImported: Int = 0,
        var setsImported: Int = 0,
        var workoutExerciseInstancesImported: Int = 0
    )

    suspend fun import(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        runInTransaction {
            val state = ImportState()
            var anyCsvFound = false

            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val isDirectory = entry.isDirectory
                    val name = entry.name.substringAfterLast('/')
                    try {
                        if (!isDirectory && name.isNotEmpty()) {
                            anyCsvFound = true
                            // Do NOT wrap entryReader in .use {}; closing it would close the parent ZipInputStream.
                            val entryReader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                            processOrDeferEntry(name, entryReader, state)
                            drainReadyDeferredTables(state)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        state.errors.add("$name error: ${e.message}")
                    } finally {
                        zip.closeEntry()
                    }
                    entry = zip.nextEntry
                }
            }

            if (!anyCsvFound) {
                return@runInTransaction ImportResult(
                    0, 0, 0, 0, 0, 0, 0,
                    listOf("No CSV files found in ZIP")
                )
            }

            // Flush any deferred tables (e.g., partial backups missing optional CSVs or out-of-order ZIPs).
            for (tableName in TABLE_IMPORT_ORDER) {
                val deferred = state.deferredTables.remove(tableName) ?: continue
                importDeferredTable(tableName, deferred, state)
            }

            ImportResult(
                exercisesImported = state.exercisesImported,
                workoutsImported = state.workoutsImported,
                setsImported = state.setsImported,
                routinesImported = state.routinesImported,
                routineDaysImported = state.routineDaysImported,
                routineExercisesImported = state.routineExercisesImported,
                muscleGroupsImported = state.muscleGroupsImported,
                errors = state.errors,
                workoutExerciseInstancesImported = state.workoutExerciseInstancesImported
            )
        }
    }

    private suspend fun processOrDeferEntry(
        tableName: String,
        reader: BufferedReader,
        state: ImportState
    ) {
        if (tableName !in TABLE_IMPORT_ORDER) return

        val ready = isTableReady(tableName, state.processedTables)
        var headerIndex: Map<String, Int>? = null
        val deferredRows = if (ready) null else mutableListOf<List<String>>()

        CsvParser.forEachRecord(reader) { recordNumber, fields ->
            if (recordNumber == 1) {
                headerIndex = fields.withIndex().associate { it.value to it.index }
                return@forEachRecord
            }
            val headers = headerIndex ?: return@forEachRecord
            if (deferredRows != null) {
                deferredRows.add(fields)
            } else {
                importRow(tableName, headers, fields, state)
            }
        }

        if (deferredRows != null) {
            val headers = headerIndex
            if (headers != null && deferredRows.isNotEmpty()) {
                state.deferredTables[tableName] = DeferredCsvTable(headers, deferredRows)
            } else {
                state.processedTables.add(tableName)
            }
        } else {
            state.processedTables.add(tableName)
        }
    }

    private suspend fun drainReadyDeferredTables(state: ImportState) {
        var progressed: Boolean
        do {
            progressed = false
            for (tableName in TABLE_IMPORT_ORDER) {
                if (tableName in state.deferredTables && isTableReady(tableName, state.processedTables)) {
                    val deferred = state.deferredTables.remove(tableName) ?: continue
                    importDeferredTable(tableName, deferred, state)
                    progressed = true
                }
            }
        } while (progressed)
    }

    private suspend fun importDeferredTable(
        tableName: String,
        table: DeferredCsvTable,
        state: ImportState
    ) {
        for (row in table.rows) {
            importRow(tableName, table.headerIndex, row, state)
        }
        state.processedTables.add(tableName)
    }

    private fun isTableReady(tableName: String, processedTables: Set<String>): Boolean = when (tableName) {
        "muscle_groups.csv",
        "exercises.csv",
        "routines.csv" -> true
        "routine_days.csv" -> "routines.csv" in processedTables
        "workouts.csv" -> "routines.csv" in processedTables && "routine_days.csv" in processedTables
        "routine_exercises.csv" -> "routine_days.csv" in processedTables && "exercises.csv" in processedTables
        "sets.csv" -> "workouts.csv" in processedTables && "exercises.csv" in processedTables
        "workout_exercise_instances.csv" ->
            "workouts.csv" in processedTables &&
                "exercises.csv" in processedTables &&
                "routine_exercises.csv" in processedTables
        else -> false
    }

    private suspend fun importRow(
        tableName: String,
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        when (tableName) {
            "muscle_groups.csv" -> importMuscleGroupRow(headers, values, state)
            "exercises.csv" -> importExerciseRow(headers, values, state)
            "routines.csv" -> importRoutineRow(headers, values, state)
            "routine_days.csv" -> importRoutineDayRow(headers, values, state)
            "workouts.csv" -> importWorkoutRow(headers, values, state)
            "routine_exercises.csv" -> importRoutineExerciseRow(headers, values, state)
            "sets.csv" -> importSetRow(headers, values, state)
            "workout_exercise_instances.csv" -> importWorkoutInstanceRow(headers, values, state)
        }
    }

    private suspend fun importMuscleGroupRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        val importedName = values.field(headers, "name")?.takeIf { it.isNotBlank() } ?: return
        val name = if (importedName.isWarmupMuscleGroup()) WARMUP_MUSCLE_GROUP else importedName
        try {
            if (muscleGroupDao.muscleGroupExists(name) == 0) {
                muscleGroupDao.insertMuscleGroup(MuscleGroup(name = name))
                state.muscleGroupsImported++
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Ignore conflict if muscle group already exists
        }
    }

    private suspend fun importExerciseRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldId = values.field(headers, "id")?.toLongOrNull() ?: return
            val name = values.field(headers, "name")?.takeIf { it.isNotBlank() } ?: return

            val existing = exerciseDao.getExerciseByName(name)
            if (existing != null) {
                state.exerciseIdMap[oldId] = existing.id
                return
            }

            val exercise = Exercise(
                id = 0,
                name = name,
                targetMuscle = (values.field(headers, "targetMuscle") ?: "Other").let { muscle ->
                    if (muscle.isWarmupMuscleGroup()) WARMUP_MUSCLE_GROUP else muscle
                },
                logType = values.field(headers, "logType")
                    ?.let { runCatching { LogType.valueOf(it) }.getOrNull() }
                    ?: LogType.WEIGHT_REPS,
                defaultDistanceUnit = values.field(headers, "defaultDistanceUnit")
                    ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                    ?: DistanceUnit.MILES,
                isCustom = values.field(headers, "isCustom")?.toBooleanStrictOrNull() ?: true,
                notes = values.field(headers, "notes")?.takeIf { it.isNotEmpty() },
                defaultRestSeconds = values.field(headers, "defaultRestSeconds")?.toIntOrNull() ?: 90,
                isStarred = values.field(headers, "isStarred")?.toBooleanStrictOrNull() ?: false,
                repTarget = values.field(headers, "repTarget")?.toIntOrNull()
            )
            val newId = exerciseDao.insertExercise(exercise)
            state.exerciseIdMap[oldId] = newId
            state.exercisesImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Exercise error: ${e.message}")
        }
    }

    private suspend fun importRoutineRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldId = values.field(headers, "id")?.toLongOrNull() ?: return
            val wantsActive = values.field(headers, "isActive")?.toBooleanStrictOrNull() ?: false
            val isActive = if (wantsActive && !state.activatedImportedRoutine) {
                if (routineDao.getActiveRoutineSync() != null) {
                    routineDao.clearActiveRoutine()
                }
                state.activatedImportedRoutine = true
                true
            } else {
                false
            }

            val routine = Routine(
                id = 0,
                name = values.field(headers, "name") ?: "Imported Routine",
                isActive = isActive,
                nextDayOrderIndex = values.field(headers, "nextDayOrderIndex")?.toIntOrNull() ?: 0
            )
            val newId = routineDao.insertRoutine(routine)
            state.routineIdMap[oldId] = newId
            state.routinesImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Routine error: ${e.message}")
        }
    }

    private suspend fun importRoutineDayRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldId = values.field(headers, "id")?.toLongOrNull() ?: return
            val oldRoutineId = values.field(headers, "routineId")?.toLongOrNull() ?: return
            val newRoutineId = state.routineIdMap[oldRoutineId] ?: return

            val day = RoutineDay(
                id = 0,
                routineId = newRoutineId,
                name = values.field(headers, "name") ?: "Day",
                orderIndex = values.field(headers, "orderIndex")?.toIntOrNull() ?: 0
            )
            val newId = routineDao.insertRoutineDay(day)
            state.routineDayIdMap[oldId] = newId
            state.routineDaysImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Routine day error: ${e.message}")
        }
    }

    private suspend fun importWorkoutRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldId = values.field(headers, "id")?.toLongOrNull() ?: return
            val startTimeMs = values.field(headers, "startTime")?.toLongOrNull() ?: return
            val oldRoutineDayId = values.field(headers, "routineDayId")?.toLongOrNull()
            val newRoutineDayId = oldRoutineDayId?.let { state.routineDayIdMap[it] }

            val workout = Workout(
                id = 0,
                startTime = Date(startTimeMs),
                endTime = values.field(headers, "endTime")?.toLongOrNull()?.let { Date(it) },
                name = values.field(headers, "name")?.takeIf { it.isNotEmpty() },
                note = values.field(headers, "note")?.takeIf { it.isNotEmpty() },
                rating = values.field(headers, "rating")?.toIntOrNull(),
                ratingNote = values.field(headers, "ratingNote")?.takeIf { it.isNotEmpty() },
                routineDayId = newRoutineDayId,
                routineId = values.field(headers, "routineId")?.toLongOrNull()?.let { state.routineIdMap[it] },
                routineNameSnapshot = values.field(headers, "routineNameSnapshot")?.takeIf { it.isNotEmpty() },
                routineDayNameSnapshot = values.field(headers, "routineDayNameSnapshot")?.takeIf { it.isNotEmpty() },
                startedFromRoutine = values.field(headers, "startedFromRoutine")?.toBooleanStrictOrNull() ?: false
            )
            val newId = workoutDao.insertWorkout(workout)
            state.workoutIdMap[oldId] = newId
            state.workoutsImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Workout error: ${e.message}")
        }
    }

    private suspend fun importRoutineExerciseRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldId = values.field(headers, "id")?.toLongOrNull()
            val oldRoutineDayId = values.field(headers, "routineDayId")?.toLongOrNull() ?: return
            val oldExerciseId = values.field(headers, "exerciseId")?.toLongOrNull() ?: return
            val newRoutineDayId = state.routineDayIdMap[oldRoutineDayId] ?: return
            val newExerciseId = state.exerciseIdMap[oldExerciseId] ?: return
            val orderIndex = values.field(headers, "orderIndex")?.toIntOrNull() ?: 0

            val re = RoutineExercise(
                id = 0,
                routineDayId = newRoutineDayId,
                exerciseId = newExerciseId,
                orderIndex = orderIndex,
                targetSets = values.field(headers, "targetSets")?.toIntOrNull() ?: 3,
                targetRepsMin = values.field(headers, "targetRepsMin")?.toIntOrNull(),
                targetRepsMax = values.field(headers, "targetRepsMax")?.toIntOrNull(),
                targetRestSeconds = values.field(headers, "targetRestSeconds")?.toIntOrNull(),
                notes = values.field(headers, "notes")?.takeIf { it.isNotEmpty() },
                supersetGroupId = values.field(headers, "supersetGroupId")?.takeIf { it.isNotEmpty() },
                supersetOrderIndex = values.field(headers, "supersetOrderIndex")?.toIntOrNull() ?: 0
            )
            routineDao.insertRoutineExercise(re)
            if (oldId != null) {
                state.pendingRoutineExerciseMappings.add(
                    PendingRoutineExerciseMapping(
                        oldId = oldId,
                        key = RoutineExerciseLookupKey(newRoutineDayId, newExerciseId, orderIndex)
                    )
                )
            }
            state.routineExercisesImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Routine exercise error: ${e.message}")
        }
    }

    private suspend fun importSetRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldWorkoutId = values.field(headers, "workoutId")?.toLongOrNull() ?: return
            val oldExerciseId = values.field(headers, "exerciseId")?.toLongOrNull() ?: return
            val newWorkoutId = state.workoutIdMap[oldWorkoutId] ?: return
            val newExerciseId = state.exerciseIdMap[oldExerciseId] ?: return
            val timestampMs = values.field(headers, "timestamp")?.toLongOrNull() ?: return

            val set = com.example.gymtime.data.db.entity.Set(
                id = 0,
                workoutId = newWorkoutId,
                exerciseId = newExerciseId,
                weight = values.field(headers, "weight").toFiniteFloatOrNull(),
                calories = values.field(headers, "calories").toFiniteFloatOrNull(),
                reps = values.field(headers, "reps")?.toIntOrNull(),
                rpe = values.field(headers, "rpe").toFiniteFloatOrNull(),
                durationSeconds = values.field(headers, "durationSeconds")?.toIntOrNull(),
                distanceValue = values.field(headers, "distanceValue").toFiniteFloatOrNull(),
                distanceUnit = values.field(headers, "distanceUnit")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() },
                distanceMeters = values.field(headers, "distanceMeters").toFiniteFloatOrNull(),
                isWarmup = values.field(headers, "isWarmup")?.toBooleanStrictOrNull() ?: false,
                isComplete = values.field(headers, "isComplete")?.toBooleanStrictOrNull() ?: true,
                timestamp = Date(timestampMs),
                note = values.field(headers, "note")?.takeIf { it.isNotEmpty() },
                supersetGroupId = values.field(headers, "supersetGroupId")?.takeIf { it.isNotEmpty() },
                supersetOrderIndex = values.field(headers, "supersetOrderIndex")?.toIntOrNull() ?: 0
            )
            setDao.insertSet(set)
            state.setsImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Set error: ${e.message}")
        }
    }

    private suspend fun importWorkoutInstanceRow(
        headers: Map<String, Int>,
        values: List<String>,
        state: ImportState
    ) {
        try {
            val oldWorkoutId = values.field(headers, "workoutId")?.toLongOrNull() ?: return
            val oldExerciseId = values.field(headers, "exerciseId")?.toLongOrNull() ?: return
            val newWorkoutId = state.workoutIdMap[oldWorkoutId] ?: return
            val newExerciseId = state.exerciseIdMap[oldExerciseId] ?: return

            resolveRoutineExerciseIdsIfNeeded(state)

            val oldRoutineExerciseId = values.field(headers, "routineExerciseId")?.toLongOrNull()
            val newRoutineExerciseId = oldRoutineExerciseId?.let { state.routineExerciseIdMap[it] }

            val instance = WorkoutExerciseInstance(
                id = 0,
                workoutId = newWorkoutId,
                exerciseId = newExerciseId,
                routineExerciseId = newRoutineExerciseId,
                orderIndex = values.field(headers, "orderIndex")?.toIntOrNull() ?: 0,
                plannedSets = values.field(headers, "plannedSets")?.toIntOrNull(),
                repMin = values.field(headers, "repMin")?.toIntOrNull(),
                repMax = values.field(headers, "repMax")?.toIntOrNull(),
                restSeconds = values.field(headers, "restSeconds")?.toIntOrNull(),
                notes = values.field(headers, "notes")?.takeIf { it.isNotEmpty() },
                supersetGroupId = values.field(headers, "supersetGroupId")?.takeIf { it.isNotEmpty() },
                supersetOrderIndex = values.field(headers, "supersetOrderIndex")?.toIntOrNull() ?: 0,
                isSkipped = values.field(headers, "isSkipped")?.toBooleanStrictOrNull() ?: false,
                addedDuringWorkout = values.field(headers, "addedDuringWorkout")?.toBooleanStrictOrNull() ?: false
            )
            workoutPlanDao.insertInstance(instance)
            state.workoutExerciseInstancesImported++
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            state.errors.add("Workout exercise instance error: ${e.message}")
        }
    }

    private suspend fun resolveRoutineExerciseIdsIfNeeded(state: ImportState) {
        if (state.routineExerciseMapResolved) return
        state.routineExerciseMapResolved = true
        if (state.pendingRoutineExerciseMappings.isEmpty()) return

        val insertedByKey = routineDao.getAllRoutineExercises()
            .groupBy { RoutineExerciseLookupKey(it.routineDayId, it.exerciseId, it.orderIndex) }
        for (pending in state.pendingRoutineExerciseMappings) {
            val matched = insertedByKey[pending.key]?.maxByOrNull { it.id }
            if (matched != null) {
                state.routineExerciseIdMap[pending.oldId] = matched.id
            }
        }
    }

    private fun List<String>.field(headers: Map<String, Int>, name: String): String? {
        val index = headers[name] ?: return null
        return getOrNull(index)
    }

    private fun String?.toFiniteFloatOrNull(): Float? =
        this?.toFloatOrNull()?.takeIf { it.isFinite() }

    private companion object {
        private val TABLE_IMPORT_ORDER = listOf(
            "muscle_groups.csv",
            "exercises.csv",
            "routines.csv",
            "routine_days.csv",
            "workouts.csv",
            "routine_exercises.csv",
            "sets.csv",
            "workout_exercise_instances.csv"
        )
    }
}
