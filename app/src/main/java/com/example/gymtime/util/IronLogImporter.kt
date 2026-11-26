package com.example.gymtime.util

import androidx.room.withTransaction
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
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject

class IronLogImporter @Inject constructor(
    private val database: GymTimeDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val workoutPlanDao: WorkoutPlanDao,
    private val generatedNarrativeDao: GeneratedNarrativeDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val volumeOrbRepository: VolumeOrbRepository
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
        generatedNarrativeDao: GeneratedNarrativeDao,
        userPreferencesRepository: UserPreferencesRepository,
        volumeOrbRepository: VolumeOrbRepository,
        runInTransaction: suspend (suspend () -> ImportResult) -> ImportResult
    ) : this(
        database = database,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        setDao = setDao,
        routineDao = routineDao,
        muscleGroupDao = muscleGroupDao,
        workoutPlanDao = workoutPlanDao,
        generatedNarrativeDao = generatedNarrativeDao,
        userPreferencesRepository = userPreferencesRepository,
        volumeOrbRepository = volumeOrbRepository
    ) {
        this.runInTransaction = runInTransaction
    }

    enum class ImportMode {
        /**
         * Clears local workout data atomically and restores the backup verbatim,
         * preserving starter exercise customizations, duplicate exercise names,
         * routines, workout plans, and portable preferences.
         */
        REPLACE,

        /**
         * Keeps existing local data and merges backup records while deduplicating
         * identical workouts, sets, routines, and exercises.
         */
        MERGE
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
        val workoutExerciseInstancesImported: Int = 0,
        val exercisesUpdated: Int = 0,
        val duplicatesSkipped: Int = 0,
        val preferencesRestored: Boolean = false,
        val formatVersion: Int = 1
    ) {
        val totalRecordsWritten: Int
            get() = exercisesImported +
                exercisesUpdated +
                workoutsImported +
                setsImported +
                routinesImported +
                routineDaysImported +
                routineExercisesImported +
                muscleGroupsImported +
                workoutExerciseInstancesImported
    }

    private data class ParsedCsvTable(
        val headers: Map<String, Int>,
        val rows: List<Pair<Int, List<String>>>
    )

    private data class ParsedBackupArchive(
        val manifest: IronLogBackupFormat.BackupManifest?,
        val preferences: IronLogBackupFormat.BackupPreferences?,
        val tables: Map<String, ParsedCsvTable>
    )

    private data class ParsedRoutine(
        val oldId: Long,
        val recordNumber: Int,
        val name: String,
        val wantsActive: Boolean,
        val nextDayOrderIndex: Int
    )

    private data class ParsedRoutineDay(
        val oldId: Long,
        val oldRoutineId: Long,
        val recordNumber: Int,
        val name: String,
        val orderIndex: Int
    )

    private data class ParsedRoutineExercise(
        val oldId: Long?,
        val oldRoutineDayId: Long,
        val oldExerciseId: Long,
        val recordNumber: Int,
        val orderIndex: Int,
        val targetSets: Int,
        val targetRepsMin: Int?,
        val targetRepsMax: Int?,
        val targetRestSeconds: Int?,
        val notes: String?,
        val supersetGroupId: String?,
        val supersetOrderIndex: Int
    )

    private data class ParsedWorkout(
        val oldId: Long,
        val recordNumber: Int,
        val startTimeMs: Long,
        val endTimeMs: Long?,
        val name: String?,
        val note: String?,
        val rating: Int?,
        val ratingNote: String?,
        val oldRoutineDayId: Long?,
        val oldRoutineId: Long?,
        val routineNameSnapshot: String?,
        val routineDayNameSnapshot: String?,
        val startedFromRoutine: Boolean,
        val hasStartedFromRoutineColumn: Boolean
    )

    private data class ParsedSet(
        val recordNumber: Int,
        val oldWorkoutId: Long,
        val oldExerciseId: Long,
        val weight: Float?,
        val calories: Float?,
        val reps: Int?,
        val rpe: Float?,
        val durationSeconds: Int?,
        val distanceValue: Float?,
        val distanceUnit: DistanceUnit?,
        val distanceUnitUnrecognized: Boolean,
        val distanceMeters: Float?,
        val isWarmup: Boolean,
        val isComplete: Boolean,
        val timestampMs: Long,
        val note: String?,
        val supersetGroupId: String?,
        val supersetOrderIndex: Int
    )

    private data class ParsedWorkoutInstance(
        val recordNumber: Int,
        val oldWorkoutId: Long,
        val oldExerciseId: Long,
        val oldRoutineExerciseId: Long?,
        val orderIndex: Int,
        val plannedSets: Int?,
        val repMin: Int?,
        val repMax: Int?,
        val restSeconds: Int?,
        val notes: String?,
        val supersetGroupId: String?,
        val supersetOrderIndex: Int,
        val isSkipped: Boolean,
        val addedDuringWorkout: Boolean
    )

    private data class RoutineExerciseSignature(
        val orderIndex: Int,
        val exerciseId: Long,
        val targetSets: Int,
        val targetRepsMin: Int?,
        val targetRepsMax: Int?,
        val targetRestSeconds: Int?,
        val notes: String?
    )

    private data class RoutineDaySignature(
        val orderIndex: Int,
        val nameLower: String,
        val exercises: List<RoutineExerciseSignature>
    )

    private data class RoutineSignature(
        val nameLower: String,
        val days: List<RoutineDaySignature>
    )

    private data class WorkoutKey(
        val startTimeMs: Long,
        val endTimeMs: Long?,
        val nameLower: String
    )

    private data class SetFingerprint(
        val exerciseId: Long,
        val timestampMs: Long,
        val weightBits: Int?,
        val caloriesBits: Int?,
        val reps: Int?,
        val rpeBits: Int?,
        val durationSeconds: Int?,
        val distanceValueBits: Int?,
        val distanceUnit: DistanceUnit?,
        val isWarmup: Boolean,
        val isComplete: Boolean,
        val note: String?
    )

    private data class InstanceFingerprint(
        val exerciseId: Long,
        val orderIndex: Int,
        val plannedSets: Int?,
        val repMin: Int?,
        val repMax: Int?,
        val restSeconds: Int?,
        val notes: String?,
        val isSkipped: Boolean,
        val addedDuringWorkout: Boolean
    )

    private class ImportContext(
        val mode: ImportMode,
        val warnings: MutableList<String> = mutableListOf(),
        val knownMuscleGroupsLower: MutableSet<String> = mutableSetOf(),
        val exerciseIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val exerciseIsWarmup: MutableMap<Long, Boolean> = mutableMapOf(),
        val exerciseDefaultDistanceUnit: MutableMap<Long, DistanceUnit> = mutableMapOf(),
        val routineIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineDayIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineExerciseIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val routineDayToRoutineId: MutableMap<Long, Long> = mutableMapOf(),
        val routineNameById: MutableMap<Long, String> = mutableMapOf(),
        val routineDayNameById: MutableMap<Long, String> = mutableMapOf(),
        val workoutIdMap: MutableMap<Long, Long> = mutableMapOf(),
        val matchedExistingWorkoutIds: MutableSet<Long> = mutableSetOf(),
        val workoutSupersetMaps: MutableMap<Long, MutableMap<String, String>> = mutableMapOf(),
        var activatedImportedRoutine: Boolean = false,
        var muscleGroupsImported: Int = 0,
        var exercisesImported: Int = 0,
        var exercisesUpdated: Int = 0,
        var routinesImported: Int = 0,
        var routineDaysImported: Int = 0,
        var routineExercisesImported: Int = 0,
        var workoutsImported: Int = 0,
        var setsImported: Int = 0,
        var workoutExerciseInstancesImported: Int = 0,
        var duplicatesSkipped: Int = 0
    )

    suspend fun import(
        inputStream: InputStream,
        mode: ImportMode = ImportMode.MERGE
    ): ImportResult = withContext(Dispatchers.IO) {
        // Phase 1: Parse and validate the entire ZIP archive BEFORE opening any DB transaction
        // so malformed ZIPs, unsupported future versions, missing headers, or truncated CSVs
        // never partially commit or wipe local data in REPLACE mode.
        val archive = parseAndValidateArchive(inputStream)

        // Phase 2: Execute all Room writes inside a single atomic transaction.
        // Any database exception propagates out and triggers a complete SQLite rollback.
        val dbResult = runInTransaction {
            applyArchiveInTransaction(archive, mode)
        }

        // Phase 3: Post-commit DataStore preference restore, historical best-streak recovery,
        // and derived Volume Orb cache refresh.
        var preferencesRestored = false
        if (archive.preferences != null) {
            userPreferencesRepository.restorePortablePreferences(
                backup = archive.preferences,
                replaceMode = mode == ImportMode.REPLACE
            )
            preferencesRestored = true
        }

        recomputeBestStreakAfterImport(
            overrideRestDays = if (mode == ImportMode.REPLACE) archive.preferences?.restDaysPerWeek else null
        )
        volumeOrbRepository.refresh()

        dbResult.copy(
            preferencesRestored = preferencesRestored,
            formatVersion = archive.manifest?.formatVersion ?: 1
        )
    }

    private fun parseAndValidateArchive(inputStream: InputStream): ParsedBackupArchive {
        val pushback = PushbackInputStream(inputStream, 4)
        val header = ByteArray(4)
        val bytesRead = readFully(pushback, header)
        if (bytesRead < 4 || !isZipSignature(header)) {
            throw InvalidBackupException("The selected file is not a valid IronLog ZIP backup.")
        }
        pushback.unread(header, 0, bytesRead)

        var manifest: IronLogBackupFormat.BackupManifest? = null
        var preferences: IronLogBackupFormat.BackupPreferences? = null
        val tables = linkedMapOf<String, ParsedCsvTable>()
        val seenEntries = mutableSetOf<String>()
        var totalDecompressedBytes = 0L

        try {
            ZipInputStream(pushback).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawPath = entry.name ?: ""
                    val isDirectory = entry.isDirectory
                    val isMacOsJunk = rawPath.startsWith("__MACOSX/") || rawPath.contains("/__MACOSX/")
                    val baseName = rawPath.substringAfterLast('/').substringAfterLast('\\').trim()

                    try {
                        if (!isDirectory && !isMacOsJunk && baseName.isNotEmpty() && baseName != ".DS_Store") {
                            val boundedStream = BoundedEntryInputStream(
                                delegate = zip,
                                maxEntryBytes = MAX_ENTRY_BYTES,
                                remainingTotalBytes = (MAX_TOTAL_BYTES - totalDecompressedBytes).coerceAtLeast(0L)
                            )

                            when {
                                baseName == IronLogBackupFormat.MANIFEST_ENTRY_NAME -> {
                                    if (!seenEntries.add(baseName)) {
                                        throw InvalidBackupException("Duplicate manifest.json entry in backup ZIP")
                                    }
                                    val jsonText = boundedStream.bufferedReader(Charsets.UTF_8).readText()
                                    totalDecompressedBytes += boundedStream.bytesRead
                                    manifest = IronLogBackupFormat.decodeManifest(jsonText)
                                }

                                baseName == IronLogBackupFormat.PREFERENCES_ENTRY_NAME -> {
                                    if (!seenEntries.add(baseName)) {
                                        throw InvalidBackupException("Duplicate preferences.json entry in backup ZIP")
                                    }
                                    val jsonText = boundedStream.bufferedReader(Charsets.UTF_8).readText()
                                    totalDecompressedBytes += boundedStream.bytesRead
                                    preferences = IronLogBackupFormat.decodePreferences(jsonText)
                                }

                                baseName in TABLE_IMPORT_ORDER -> {
                                    if (!seenEntries.add(baseName)) {
                                        throw InvalidBackupException("Duplicate backup table '$baseName' in ZIP")
                                    }
                                    val reader = BufferedReader(InputStreamReader(boundedStream, Charsets.UTF_8))
                                    val parsed = parseCsvTable(baseName, reader)
                                    totalDecompressedBytes += boundedStream.bytesRead
                                    tables[baseName] = parsed
                                }
                            }
                        }
                    } finally {
                        zip.closeEntry()
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBackupException("Could not read IronLog backup ZIP: ${e.message}", e)
        }

        if (tables.isEmpty()) {
            throw InvalidBackupException("No IronLog backup tables were found in the selected ZIP file.")
        }

        return ParsedBackupArchive(
            manifest = manifest,
            preferences = preferences,
            tables = tables
        )
    }

    private fun parseCsvTable(tableName: String, reader: BufferedReader): ParsedCsvTable {
        var headers: Map<String, Int>? = null
        val rows = mutableListOf<Pair<Int, List<String>>>()
        try {
            CsvParser.forEachRecord(reader) { recordNumber, fields ->
                if (recordNumber == 1) {
                    val trimmedHeaders = linkedMapOf<String, Int>()
                    fields.forEachIndexed { index, raw ->
                        val col = raw.trim()
                        if (col.isNotEmpty() && col !in trimmedHeaders) {
                            trimmedHeaders[col] = index
                        }
                    }
                    val required = REQUIRED_COLUMNS[tableName].orEmpty()
                    val missing = required.filterNot { it in trimmedHeaders }
                    if (missing.isNotEmpty()) {
                        throw InvalidBackupException(
                            "$tableName is missing required column(s): ${missing.joinToString(", ")}"
                        )
                    }
                    headers = trimmedHeaders
                } else {
                    if (rows.size >= MAX_ROWS_PER_TABLE) {
                        throw InvalidBackupException("$tableName exceeds maximum supported row count ($MAX_ROWS_PER_TABLE)")
                    }
                    rows.add(recordNumber to fields)
                }
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBackupException("$tableName has invalid CSV formatting: ${e.message}", e)
        }

        return ParsedCsvTable(
            headers = headers ?: emptyMap(),
            rows = rows
        )
    }

    private suspend fun applyArchiveInTransaction(
        archive: ParsedBackupArchive,
        mode: ImportMode
    ): ImportResult {
        val ctx = ImportContext(mode = mode)

        if (mode == ImportMode.REPLACE) {
            // Clear tables in FK-safe order before restoring verbatim.
            setDao.deleteAllSets()
            workoutPlanDao.deleteAllInstances()
            routineDao.deleteAllRoutineExercises()
            routineDao.deleteAllRoutineDays()
            generatedNarrativeDao.deleteAll()
            workoutDao.deleteAllWorkouts()
            routineDao.deleteAllRoutines()
            exerciseDao.deleteAllExercises()
            muscleGroupDao.deleteAllMuscleGroups()
        } else {
            muscleGroupDao.getAllMuscleGroupNames().forEach {
                ctx.knownMuscleGroupsLower.add(it.trim().lowercase(Locale.US))
            }
        }

        // 1. Muscle groups
        importMuscleGroups(archive.tables["muscle_groups.csv"], ctx)
        if (mode == ImportMode.REPLACE) {
            ensureMuscleGroupExists(WARMUP_MUSCLE_GROUP, ctx, countAsImported = false)
        }

        // 2. Exercises
        importExercises(archive.tables["exercises.csv"], ctx)

        // 3. Routines + Routine Days + Routine Exercises
        importRoutinesGraph(
            routinesTable = archive.tables["routines.csv"],
            daysTable = archive.tables["routine_days.csv"],
            routineExercisesTable = archive.tables["routine_exercises.csv"],
            ctx = ctx
        )

        // 4. Workouts + Sets + Workout Exercise Instances
        importWorkoutsGraph(
            workoutsTable = archive.tables["workouts.csv"],
            setsTable = archive.tables["sets.csv"],
            instancesTable = archive.tables["workout_exercise_instances.csv"],
            ctx = ctx
        )

        return ImportResult(
            exercisesImported = ctx.exercisesImported,
            exercisesUpdated = ctx.exercisesUpdated,
            workoutsImported = ctx.workoutsImported,
            setsImported = ctx.setsImported,
            routinesImported = ctx.routinesImported,
            routineDaysImported = ctx.routineDaysImported,
            routineExercisesImported = ctx.routineExercisesImported,
            muscleGroupsImported = ctx.muscleGroupsImported,
            errors = ctx.warnings,
            workoutExerciseInstancesImported = ctx.workoutExerciseInstancesImported,
            duplicatesSkipped = ctx.duplicatesSkipped
        )
    }

    private suspend fun ensureMuscleGroupExists(
        rawName: String,
        ctx: ImportContext,
        countAsImported: Boolean
    ): String {
        val trimmed = rawName.trim().ifEmpty { "Other" }
        val normalized = if (trimmed.isWarmupMuscleGroup()) WARMUP_MUSCLE_GROUP else trimmed
        val lower = normalized.lowercase(Locale.US)
        if (lower !in ctx.knownMuscleGroupsLower) {
            if (muscleGroupDao.muscleGroupExists(normalized) == 0) {
                muscleGroupDao.insertMuscleGroup(MuscleGroup(name = normalized))
                if (countAsImported) {
                    ctx.muscleGroupsImported++
                }
            }
            ctx.knownMuscleGroupsLower.add(lower)
        }
        return normalized
    }

    private suspend fun importMuscleGroups(table: ParsedCsvTable?, ctx: ImportContext) {
        if (table == null) return
        for ((recordNumber, values) in table.rows) {
            val rawName = values.field(table.headers, "name")?.trim()
            if (rawName.isNullOrEmpty()) {
                ctx.warnings.add("muscle_groups.csv row $recordNumber: skipped blank name")
                continue
            }
            ensureMuscleGroupExists(rawName, ctx, countAsImported = true)
        }
    }

    private suspend fun importExercises(table: ParsedCsvTable?, ctx: ImportContext) {
        if (table == null) return

        val existingExercises = if (ctx.mode == ImportMode.MERGE) {
            exerciseDao.getAllExercisesSync()
        } else {
            emptyList()
        }
        val claimedExistingIds = mutableSetOf<Long>()

        for ((recordNumber, values) in table.rows) {
            val oldId = values.field(table.headers, "id")?.trim()?.toLongOrNull()
            val name = values.field(table.headers, "name")?.trim()?.takeIf { it.isNotEmpty() }
            if (oldId == null || name == null) {
                ctx.warnings.add("exercises.csv row $recordNumber: skipped row missing valid id or name")
                continue
            }

            val rawMuscle = values.field(table.headers, "targetMuscle")?.trim()?.takeIf { it.isNotEmpty() } ?: "Other"
            val targetMuscle = ensureMuscleGroupExists(rawMuscle, ctx, countAsImported = false)

            val rawLogType = values.field(table.headers, "logType")?.trim()?.takeIf { it.isNotEmpty() }
            val logType = if (rawLogType == null) {
                LogType.WEIGHT_REPS
            } else {
                runCatching { LogType.valueOf(rawLogType) }.getOrElse {
                    ctx.warnings.add("exercises.csv row $recordNumber: unrecognized logType '$rawLogType', defaulted to WEIGHT_REPS")
                    LogType.WEIGHT_REPS
                }
            }

            val rawUnit = values.field(table.headers, "defaultDistanceUnit")?.trim()?.takeIf { it.isNotEmpty() }
            val defaultDistanceUnit = if (rawUnit == null) {
                DistanceUnit.MILES
            } else {
                runCatching { DistanceUnit.valueOf(rawUnit) }.getOrElse {
                    ctx.warnings.add("exercises.csv row $recordNumber: unrecognized defaultDistanceUnit '$rawUnit', defaulted to MILES")
                    DistanceUnit.MILES
                }
            }

            val isCustom = parseCsvBoolean(
                raw = values.field(table.headers, "isCustom"),
                default = true,
                tableName = "exercises.csv",
                recordNumber = recordNumber,
                fieldName = "isCustom",
                warnings = ctx.warnings
            )
            val notes = values.field(table.headers, "notes")?.takeIf { it.isNotEmpty() }
            val rawRest = values.field(table.headers, "defaultRestSeconds")?.trim()?.toIntOrNull()
            val defaultRestSeconds = when {
                rawRest == null -> 90
                rawRest > 0 -> rawRest
                else -> {
                    ctx.warnings.add("exercises.csv row $recordNumber: invalid defaultRestSeconds '$rawRest', defaulted to 90")
                    90
                }
            }
            val isStarred = parseCsvBoolean(
                raw = values.field(table.headers, "isStarred"),
                default = false,
                tableName = "exercises.csv",
                recordNumber = recordNumber,
                fieldName = "isStarred",
                warnings = ctx.warnings
            )
            val rawRepTarget = values.field(table.headers, "repTarget")?.trim()?.takeIf { it.isNotEmpty() }
            val repTarget = if (rawRepTarget == null) {
                null
            } else {
                val parsed = rawRepTarget.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    parsed
                } else {
                    ctx.warnings.add("exercises.csv row $recordNumber: invalid repTarget '$rawRepTarget', ignored")
                    null
                }
            }

            if (ctx.mode == ImportMode.MERGE) {
                // Match unclaimed existing exercise by (name, targetMuscle, logType) first,
                // then fallback to unclaimed name match so duplicate exercise names in the
                // backup never collapse onto the same target row.
                val matched = existingExercises.firstOrNull { existing ->
                    existing.id !in claimedExistingIds &&
                        existing.name.trim().equals(name, ignoreCase = true) &&
                        existing.targetMuscle.trim().equals(targetMuscle, ignoreCase = true) &&
                        existing.logType == logType
                } ?: existingExercises.firstOrNull { existing ->
                    existing.id !in claimedExistingIds &&
                        existing.name.trim().equals(name, ignoreCase = true)
                }

                if (matched != null) {
                    claimedExistingIds.add(matched.id)
                    val existingSetCount = exerciseDao.getSetCountForExercise(matched.id)
                    val updated = if (existingSetCount == 0) {
                        // Safe to restore user's backup customizations onto an unused (e.g. freshly seeded) exercise.
                        matched.copy(
                            targetMuscle = targetMuscle,
                            logType = logType,
                            defaultDistanceUnit = defaultDistanceUnit,
                            isCustom = isCustom,
                            notes = notes,
                            defaultRestSeconds = defaultRestSeconds,
                            isStarred = isStarred,
                            repTarget = repTarget
                        )
                    } else {
                        // Exercise already has local logged history: preserve non-destructive metadata.
                        matched.copy(
                            notes = matched.notes ?: notes,
                            isStarred = matched.isStarred || isStarred,
                            repTarget = matched.repTarget ?: repTarget
                        )
                    }
                    if (updated != matched) {
                        exerciseDao.updateExercise(updated)
                        ctx.exercisesUpdated++
                    }
                    ctx.exerciseIdMap[oldId] = updated.id
                    ctx.exerciseIsWarmup[updated.id] = updated.targetMuscle.isWarmupMuscleGroup()
                    ctx.exerciseDefaultDistanceUnit[updated.id] = updated.defaultDistanceUnit
                    continue
                }
            }

            val exercise = Exercise(
                id = 0,
                name = name,
                targetMuscle = targetMuscle,
                logType = logType,
                defaultDistanceUnit = defaultDistanceUnit,
                isCustom = isCustom,
                notes = notes,
                defaultRestSeconds = defaultRestSeconds,
                isStarred = isStarred,
                repTarget = repTarget
            )
            val newId = exerciseDao.insertExercise(exercise)
            ctx.exerciseIdMap[oldId] = newId
            ctx.exerciseIsWarmup[newId] = targetMuscle.isWarmupMuscleGroup()
            ctx.exerciseDefaultDistanceUnit[newId] = defaultDistanceUnit
            ctx.exercisesImported++
        }
    }

    private suspend fun importRoutinesGraph(
        routinesTable: ParsedCsvTable?,
        daysTable: ParsedCsvTable?,
        routineExercisesTable: ParsedCsvTable?,
        ctx: ImportContext
    ) {
        val parsedRoutines = parseRoutines(routinesTable, ctx.warnings)
        val parsedDays = parseRoutineDays(daysTable, ctx.warnings)
        val parsedRoutineExercises = parseRoutineExercises(routineExercisesTable, ctx.warnings)

        val daysByOldRoutineId = parsedDays.groupBy { it.oldRoutineId }
        val exercisesByOldDayId = parsedRoutineExercises.groupBy { it.oldRoutineDayId }

        // Warn on orphan days or orphan routine exercises
        val validRoutineOldIds = parsedRoutines.mapTo(mutableSetOf()) { it.oldId }
        for (day in parsedDays) {
            if (day.oldRoutineId !in validRoutineOldIds) {
                ctx.warnings.add("routine_days.csv row ${day.recordNumber}: skipped day referencing missing routineId ${day.oldRoutineId}")
            }
        }
        val validDayOldIds = parsedDays.filter { it.oldRoutineId in validRoutineOldIds }.mapTo(mutableSetOf()) { it.oldId }
        for (re in parsedRoutineExercises) {
            if (re.oldRoutineDayId !in validDayOldIds) {
                ctx.warnings.add("routine_exercises.csv row ${re.recordNumber}: skipped row referencing missing routineDayId ${re.oldRoutineDayId}")
            } else if (re.oldExerciseId !in ctx.exerciseIdMap) {
                ctx.warnings.add("routine_exercises.csv row ${re.recordNumber}: skipped row referencing missing exerciseId ${re.oldExerciseId}")
            }
        }

        // Load existing routines graph in MERGE mode for deduplication.
        val existingRoutineEntries = if (ctx.mode == ImportMode.MERGE) {
            val exRoutines = routineDao.getAllRoutinesSync()
            val exDaysByRoutine = routineDao.getAllRoutineDays().groupBy { it.routineId }
            val exExercisesByDay = routineDao.getAllRoutineExercises().groupBy { it.routineDayId }
            exRoutines.map { routine ->
                val days = exDaysByRoutine[routine.id].orEmpty().sortedBy { it.orderIndex }
                val sig = RoutineSignature(
                    nameLower = routine.name.trim().lowercase(Locale.US),
                    days = days.map { day ->
                        val res = exExercisesByDay[day.id].orEmpty().sortedBy { it.orderIndex }
                        RoutineDaySignature(
                            orderIndex = day.orderIndex,
                            nameLower = day.name.trim().lowercase(Locale.US),
                            exercises = res.map { re ->
                                RoutineExerciseSignature(
                                    orderIndex = re.orderIndex,
                                    exerciseId = re.exerciseId,
                                    targetSets = re.targetSets,
                                    targetRepsMin = re.targetRepsMin,
                                    targetRepsMax = re.targetRepsMax,
                                    targetRestSeconds = re.targetRestSeconds,
                                    notes = re.notes
                                )
                            }
                        )
                    }
                )
                Triple(routine, days.associateWith { d -> exExercisesByDay[d.id].orEmpty().sortedBy { it.orderIndex } }, sig)
            }
        } else {
            emptyList()
        }
        val claimedExistingRoutineIds = mutableSetOf<Long>()

        for (routine in parsedRoutines) {
            val rDays = daysByOldRoutineId[routine.oldId].orEmpty().sortedBy { it.orderIndex }
            val importedSig = RoutineSignature(
                nameLower = routine.name.trim().lowercase(Locale.US),
                days = rDays.map { day ->
                    val res = exercisesByOldDayId[day.oldId].orEmpty()
                        .filter { it.oldExerciseId in ctx.exerciseIdMap }
                        .sortedBy { it.orderIndex }
                    RoutineDaySignature(
                        orderIndex = day.orderIndex,
                        nameLower = day.name.trim().lowercase(Locale.US),
                        exercises = res.map { re ->
                            RoutineExerciseSignature(
                                orderIndex = re.orderIndex,
                                exerciseId = ctx.exerciseIdMap.getValue(re.oldExerciseId),
                                targetSets = re.targetSets,
                                targetRepsMin = re.targetRepsMin,
                                targetRepsMax = re.targetRepsMax,
                                targetRestSeconds = re.targetRestSeconds,
                                notes = re.notes
                            )
                        }
                    )
                }
            )

            if (ctx.mode == ImportMode.MERGE) {
                val existingMatch = existingRoutineEntries.firstOrNull { (existingRoutine, _, sig) ->
                    existingRoutine.id !in claimedExistingRoutineIds && sig == importedSig
                }
                if (existingMatch != null) {
                    val (existingRoutine, existingDaysMap, _) = existingMatch
                    claimedExistingRoutineIds.add(existingRoutine.id)
                    ctx.routineIdMap[routine.oldId] = existingRoutine.id
                    ctx.routineNameById[existingRoutine.id] = existingRoutine.name
                    val existingDaysOrdered = existingDaysMap.keys.sortedBy { it.orderIndex }
                    rDays.zip(existingDaysOrdered).forEach { (oldDay, existingDay) ->
                        ctx.routineDayIdMap[oldDay.oldId] = existingDay.id
                        ctx.routineDayToRoutineId[existingDay.id] = existingRoutine.id
                        ctx.routineDayNameById[existingDay.id] = existingDay.name
                        val oldRes = exercisesByOldDayId[oldDay.oldId].orEmpty()
                            .filter { it.oldExerciseId in ctx.exerciseIdMap }
                            .sortedBy { it.orderIndex }
                        val existingRes = existingDaysMap[existingDay].orEmpty()
                        oldRes.zip(existingRes).forEach { (oldRe, existingRe) ->
                            if (oldRe.oldId != null) {
                                ctx.routineExerciseIdMap[oldRe.oldId] = existingRe.id
                            }
                        }
                    }
                    ctx.duplicatesSkipped++
                    continue
                }
            }

            val isActive = if (routine.wantsActive && !ctx.activatedImportedRoutine) {
                if (routineDao.getActiveRoutineSync() != null) {
                    routineDao.clearActiveRoutine()
                }
                ctx.activatedImportedRoutine = true
                true
            } else {
                false
            }

            val newRoutineId = routineDao.insertRoutine(
                Routine(
                    id = 0,
                    name = routine.name,
                    isActive = isActive,
                    nextDayOrderIndex = routine.nextDayOrderIndex.coerceAtLeast(0)
                )
            )
            ctx.routineIdMap[routine.oldId] = newRoutineId
            ctx.routineNameById[newRoutineId] = routine.name
            ctx.routinesImported++

            for (day in rDays) {
                val newDayId = routineDao.insertRoutineDay(
                    RoutineDay(
                        id = 0,
                        routineId = newRoutineId,
                        name = day.name,
                        orderIndex = day.orderIndex
                    )
                )
                ctx.routineDayIdMap[day.oldId] = newDayId
                ctx.routineDayToRoutineId[newDayId] = newRoutineId
                ctx.routineDayNameById[newDayId] = day.name
                ctx.routineDaysImported++

                val daySupersetMap = mutableMapOf<String, String>()
                val dayExercises = exercisesByOldDayId[day.oldId].orEmpty()
                for (re in dayExercises) {
                    val newExerciseId = ctx.exerciseIdMap[re.oldExerciseId] ?: continue
                    val mappedSupersetId = if (ctx.mode == ImportMode.MERGE) {
                        remapSupersetId(re.supersetGroupId, daySupersetMap)
                    } else {
                        re.supersetGroupId
                    }
                    val newReId = routineDao.insertRoutineExercise(
                        RoutineExercise(
                            id = 0,
                            routineDayId = newDayId,
                            exerciseId = newExerciseId,
                            orderIndex = re.orderIndex,
                            targetSets = re.targetSets,
                            targetRepsMin = re.targetRepsMin,
                            targetRepsMax = re.targetRepsMax,
                            targetRestSeconds = re.targetRestSeconds,
                            notes = re.notes,
                            supersetGroupId = mappedSupersetId,
                            supersetOrderIndex = re.supersetOrderIndex
                        )
                    )
                    if (re.oldId != null) {
                        ctx.routineExerciseIdMap[re.oldId] = newReId
                    }
                    ctx.routineExercisesImported++
                }
            }
        }
    }

    private suspend fun importWorkoutsGraph(
        workoutsTable: ParsedCsvTable?,
        setsTable: ParsedCsvTable?,
        instancesTable: ParsedCsvTable?,
        ctx: ImportContext
    ) {
        val parsedWorkouts = parseWorkouts(workoutsTable, ctx.warnings)
        val parsedSets = parseSets(setsTable, ctx.warnings)
        val parsedInstances = parseWorkoutInstances(instancesTable, ctx.warnings)

        val maxSetTimeByOldWorkoutId = parsedSets
            .groupBy { it.oldWorkoutId }
            .mapValues { (_, list) -> list.maxOfOrNull { it.timestampMs } }

        val existingOngoing = if (ctx.mode == ImportMode.MERGE) {
            workoutDao.getOngoingWorkoutSync()
        } else {
            null
        }
        val existingWorkoutsByKey = if (ctx.mode == ImportMode.MERGE) {
            workoutDao.getAllWorkoutsSync()
                .groupBy { WorkoutKey(it.startTime.time, it.endTime?.time, (it.name ?: "").trim().lowercase(Locale.US)) }
                .mapValues { it.value.toMutableList() }
        } else {
            emptyMap()
        }

        // Only allow a single ongoing (endTime == null) workout across DB + backup.
        val openImportedWorkouts = parsedWorkouts.filter { it.endTimeMs == null }
        val allowedOpenOldWorkoutId = if (existingOngoing == null && openImportedWorkouts.isNotEmpty()) {
            openImportedWorkouts.maxByOrNull { it.startTimeMs }?.oldId
        } else {
            null
        }

        for (w in parsedWorkouts) {
            var resolvedEndTimeMs = w.endTimeMs
            if (resolvedEndTimeMs == null && w.oldId != allowedOpenOldWorkoutId) {
                resolvedEndTimeMs = maxOf(w.startTimeMs, maxSetTimeByOldWorkoutId[w.oldId] ?: w.startTimeMs)
                ctx.warnings.add(
                    "workouts.csv row ${w.recordNumber}: auto-closed extra ongoing workout so only one active workout exists"
                )
            }

            if (ctx.mode == ImportMode.MERGE) {
                val key = WorkoutKey(w.startTimeMs, resolvedEndTimeMs, (w.name ?: "").trim().lowercase(Locale.US))
                val matchingExisting = existingWorkoutsByKey[key]?.firstOrNull()
                if (matchingExisting != null) {
                    ctx.workoutIdMap[w.oldId] = matchingExisting.id
                    ctx.matchedExistingWorkoutIds.add(matchingExisting.id)
                    ctx.duplicatesSkipped++
                    continue
                }
            }

            val newRoutineDayId = w.oldRoutineDayId?.let { ctx.routineDayIdMap[it] }
            val explicitRoutineId = w.oldRoutineId?.let { ctx.routineIdMap[it] }
            val resolvedRoutineId = explicitRoutineId ?: newRoutineDayId?.let { ctx.routineDayToRoutineId[it] }
            val resolvedStartedFromRoutine = if (w.hasStartedFromRoutineColumn) {
                w.startedFromRoutine
            } else {
                newRoutineDayId != null || resolvedRoutineId != null
            }
            val resolvedRoutineNameSnapshot = w.routineNameSnapshot
                ?: resolvedRoutineId?.let { ctx.routineNameById[it] }
            val resolvedRoutineDayNameSnapshot = w.routineDayNameSnapshot
                ?: newRoutineDayId?.let { ctx.routineDayNameById[it] }

            val newWorkoutId = workoutDao.insertWorkout(
                Workout(
                    id = 0,
                    startTime = Date(w.startTimeMs),
                    endTime = resolvedEndTimeMs?.let { Date(it) },
                    name = w.name,
                    note = w.note,
                    rating = w.rating,
                    ratingNote = w.ratingNote,
                    routineDayId = newRoutineDayId,
                    routineId = resolvedRoutineId,
                    routineNameSnapshot = resolvedRoutineNameSnapshot,
                    routineDayNameSnapshot = resolvedRoutineDayNameSnapshot,
                    startedFromRoutine = resolvedStartedFromRoutine
                )
            )
            ctx.workoutIdMap[w.oldId] = newWorkoutId
            ctx.workoutsImported++
        }

        // Load existing set multisets only for matched existing workouts during MERGE.
        val existingSetMultisetByWorkout = mutableMapOf<Long, MutableMap<SetFingerprint, Int>>()
        for (workoutId in ctx.matchedExistingWorkoutIds) {
            val counts = mutableMapOf<SetFingerprint, Int>()
            for (existingSet in setDao.getSetsForWorkoutSync(workoutId)) {
                val fp = existingSet.toFingerprint()
                counts[fp] = (counts[fp] ?: 0) + 1
            }
            existingSetMultisetByWorkout[workoutId] = counts
        }

        for (s in parsedSets) {
            val newWorkoutId = ctx.workoutIdMap[s.oldWorkoutId]
            val newExerciseId = ctx.exerciseIdMap[s.oldExerciseId]
            if (newWorkoutId == null || newExerciseId == null) {
                ctx.warnings.add(
                    "sets.csv row ${s.recordNumber}: skipped set with unresolved workoutId (${s.oldWorkoutId}) or exerciseId (${s.oldExerciseId})"
                )
                continue
            }

            // Distance backfill & normalization (covers legacy pre-v11 and partial PWA backups).
            var distVal = s.distanceValue
            var distUnit = s.distanceUnit
            var distMeters = s.distanceMeters

            if (distVal != null && distUnit == null) {
                distUnit = ctx.exerciseDefaultDistanceUnit[newExerciseId] ?: DistanceUnit.MILES
                if (s.distanceUnitUnrecognized) {
                    ctx.warnings.add("sets.csv row ${s.recordNumber}: defaulted distanceUnit to ${distUnit.name}")
                }
            }
            if (distVal != null && distUnit != null && distMeters == null) {
                distMeters = TimeUtils.distanceToMeters(distVal, distUnit)
            } else if (distVal == null && distMeters != null && distMeters > 0f) {
                distUnit = distUnit ?: DistanceUnit.MILES
                distVal = TimeUtils.metersToDistance(distMeters, distUnit)
            }
            if (distVal == null) {
                distUnit = null
            }

            // Enforce Warmups library invariant in Kotlin as well as SQLite triggers.
            val effectiveIsWarmup = s.isWarmup || (ctx.exerciseIsWarmup[newExerciseId] == true)

            val isExistingWorkout = newWorkoutId in ctx.matchedExistingWorkoutIds
            val mappedSupersetId = if (ctx.mode == ImportMode.MERGE && !isExistingWorkout) {
                val supersetMap = ctx.workoutSupersetMaps.getOrPut(newWorkoutId) { mutableMapOf() }
                remapSupersetId(s.supersetGroupId, supersetMap)
            } else {
                s.supersetGroupId
            }

            val setEntity = WorkoutSet(
                id = 0,
                workoutId = newWorkoutId,
                exerciseId = newExerciseId,
                weight = s.weight,
                calories = s.calories,
                reps = s.reps,
                rpe = s.rpe,
                durationSeconds = s.durationSeconds,
                distanceValue = distVal,
                distanceUnit = distUnit,
                distanceMeters = distMeters,
                isWarmup = effectiveIsWarmup,
                isComplete = s.isComplete,
                timestamp = Date(s.timestampMs),
                note = s.note,
                supersetGroupId = mappedSupersetId,
                supersetOrderIndex = s.supersetOrderIndex
            )

            if (isExistingWorkout) {
                val counts = existingSetMultisetByWorkout[newWorkoutId]
                val fp = setEntity.toFingerprint()
                val remaining = counts?.get(fp) ?: 0
                if (remaining > 0) {
                    counts!![fp] = remaining - 1
                    ctx.duplicatesSkipped++
                    continue
                }
            }

            setDao.insertSet(setEntity)
            ctx.setsImported++
        }

        // Load existing plan instance multisets only for matched existing workouts during MERGE.
        val existingInstanceMultisetByWorkout = mutableMapOf<Long, MutableMap<InstanceFingerprint, Int>>()
        for (workoutId in ctx.matchedExistingWorkoutIds) {
            val counts = mutableMapOf<InstanceFingerprint, Int>()
            for (existingInst in workoutPlanDao.getInstancesForWorkoutSync(workoutId)) {
                val fp = existingInst.toFingerprint()
                counts[fp] = (counts[fp] ?: 0) + 1
            }
            existingInstanceMultisetByWorkout[workoutId] = counts
        }

        for (inst in parsedInstances) {
            val newWorkoutId = ctx.workoutIdMap[inst.oldWorkoutId]
            val newExerciseId = ctx.exerciseIdMap[inst.oldExerciseId]
            if (newWorkoutId == null || newExerciseId == null) {
                ctx.warnings.add(
                    "workout_exercise_instances.csv row ${inst.recordNumber}: skipped row with unresolved workoutId (${inst.oldWorkoutId}) or exerciseId (${inst.oldExerciseId})"
                )
                continue
            }

            val newRoutineExerciseId = inst.oldRoutineExerciseId?.let { ctx.routineExerciseIdMap[it] }
            val isExistingWorkout = newWorkoutId in ctx.matchedExistingWorkoutIds
            val mappedSupersetId = if (ctx.mode == ImportMode.MERGE && !isExistingWorkout) {
                val supersetMap = ctx.workoutSupersetMaps.getOrPut(newWorkoutId) { mutableMapOf() }
                remapSupersetId(inst.supersetGroupId, supersetMap)
            } else {
                inst.supersetGroupId
            }

            val instanceEntity = WorkoutExerciseInstance(
                id = 0,
                workoutId = newWorkoutId,
                exerciseId = newExerciseId,
                routineExerciseId = newRoutineExerciseId,
                orderIndex = inst.orderIndex,
                plannedSets = inst.plannedSets,
                repMin = inst.repMin,
                repMax = inst.repMax,
                restSeconds = inst.restSeconds,
                notes = inst.notes,
                supersetGroupId = mappedSupersetId,
                supersetOrderIndex = inst.supersetOrderIndex,
                isSkipped = inst.isSkipped,
                addedDuringWorkout = inst.addedDuringWorkout
            )

            if (isExistingWorkout) {
                val counts = existingInstanceMultisetByWorkout[newWorkoutId]
                val fp = instanceEntity.toFingerprint()
                val remaining = counts?.get(fp) ?: 0
                if (remaining > 0) {
                    counts!![fp] = remaining - 1
                    ctx.duplicatesSkipped++
                    continue
                }
            }

            workoutPlanDao.insertInstance(instanceEntity)
            ctx.workoutExerciseInstancesImported++
        }
    }

    private suspend fun recomputeBestStreakAfterImport(overrideRestDays: Int?) {
        val timestamps = workoutDao.getCompletedWorkingSetTimestamps()
        if (timestamps.isEmpty()) return
        val restDays = overrideRestDays ?: userPreferencesRepository.getRestDaysPerWeekSync()
        val historicalBest = StreakCalculator.calculateBestStreak(
            workoutDates = timestamps.map { Date(it) },
            allowedSkipsPerWeek = restDays
        )
        if (historicalBest > 0) {
            userPreferencesRepository.updateBestStreakIfNeeded(historicalBest)
        }
    }

    private fun parseRoutines(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedRoutine> {
        if (table == null) return emptyList()
        val result = mutableListOf<ParsedRoutine>()
        for ((recordNumber, values) in table.rows) {
            val oldId = values.field(table.headers, "id")?.trim()?.toLongOrNull()
            if (oldId == null) {
                warnings.add("routines.csv row $recordNumber: skipped row missing valid id")
                continue
            }
            val name = values.field(table.headers, "name")?.trim()?.takeIf { it.isNotEmpty() } ?: "Imported Routine"
            val wantsActive = parseCsvBoolean(
                raw = values.field(table.headers, "isActive"),
                default = false,
                tableName = "routines.csv",
                recordNumber = recordNumber,
                fieldName = "isActive",
                warnings = warnings
            )
            val nextDayOrderIndex = values.field(table.headers, "nextDayOrderIndex")?.trim()?.toIntOrNull() ?: 0
            result.add(ParsedRoutine(oldId, recordNumber, name, wantsActive, nextDayOrderIndex))
        }
        return result
    }

    private fun parseRoutineDays(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedRoutineDay> {
        if (table == null) return emptyList()
        val result = mutableListOf<ParsedRoutineDay>()
        for ((recordNumber, values) in table.rows) {
            val oldId = values.field(table.headers, "id")?.trim()?.toLongOrNull()
            val oldRoutineId = values.field(table.headers, "routineId")?.trim()?.toLongOrNull()
            if (oldId == null || oldRoutineId == null) {
                warnings.add("routine_days.csv row $recordNumber: skipped row missing valid id or routineId")
                continue
            }
            val name = values.field(table.headers, "name")?.trim()?.takeIf { it.isNotEmpty() } ?: "Day"
            val orderIndex = values.field(table.headers, "orderIndex")?.trim()?.toIntOrNull() ?: 0
            result.add(ParsedRoutineDay(oldId, oldRoutineId, recordNumber, name, orderIndex))
        }
        return result
    }

    private fun parseRoutineExercises(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedRoutineExercise> {
        if (table == null) return emptyList()
        val result = mutableListOf<ParsedRoutineExercise>()
        for ((recordNumber, values) in table.rows) {
            val oldId = values.field(table.headers, "id")?.trim()?.toLongOrNull()
            val oldRoutineDayId = values.field(table.headers, "routineDayId")?.trim()?.toLongOrNull()
            val oldExerciseId = values.field(table.headers, "exerciseId")?.trim()?.toLongOrNull()
            if (oldRoutineDayId == null || oldExerciseId == null) {
                warnings.add("routine_exercises.csv row $recordNumber: skipped row missing routineDayId or exerciseId")
                continue
            }
            val orderIndex = values.field(table.headers, "orderIndex")?.trim()?.toIntOrNull() ?: 0
            val targetSets = (values.field(table.headers, "targetSets")?.trim()?.toIntOrNull() ?: 3).coerceAtLeast(1)
            val targetRepsMin = values.field(table.headers, "targetRepsMin")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val targetRepsMax = values.field(table.headers, "targetRepsMax")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val targetRestSeconds = values.field(table.headers, "targetRestSeconds")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            val notes = values.field(table.headers, "notes")?.takeIf { it.isNotEmpty() }
            val supersetGroupId = values.field(table.headers, "supersetGroupId")?.trim()?.takeIf { it.isNotEmpty() }
            val supersetOrderIndex = values.field(table.headers, "supersetOrderIndex")?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            result.add(
                ParsedRoutineExercise(
                    oldId = oldId,
                    oldRoutineDayId = oldRoutineDayId,
                    oldExerciseId = oldExerciseId,
                    recordNumber = recordNumber,
                    orderIndex = orderIndex,
                    targetSets = targetSets,
                    targetRepsMin = targetRepsMin,
                    targetRepsMax = targetRepsMax,
                    targetRestSeconds = targetRestSeconds,
                    notes = notes,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
            )
        }
        return result
    }

    private fun parseWorkouts(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedWorkout> {
        if (table == null) return emptyList()
        val hasStartedFromRoutineColumn = "startedFromRoutine" in table.headers
        val result = mutableListOf<ParsedWorkout>()
        for ((recordNumber, values) in table.rows) {
            val oldId = values.field(table.headers, "id")?.trim()?.toLongOrNull()
            val startTimeMs = values.field(table.headers, "startTime")?.trim()?.toLongOrNull()
            if (oldId == null || startTimeMs == null) {
                warnings.add("workouts.csv row $recordNumber: skipped row missing valid id or startTime")
                continue
            }
            val rawEndTime = values.field(table.headers, "endTime")?.trim()?.takeIf { it.isNotEmpty() }
            val endTimeMs = if (rawEndTime == null) {
                null
            } else {
                rawEndTime.toLongOrNull() ?: run {
                    warnings.add("workouts.csv row $recordNumber: invalid endTime '$rawEndTime', treated as null")
                    null
                }
            }
            val rawRating = values.field(table.headers, "rating")?.trim()?.takeIf { it.isNotEmpty() }
            val rating = if (rawRating == null) {
                null
            } else {
                val parsed = rawRating.toIntOrNull()
                if (parsed != null && parsed in 1..5) {
                    parsed
                } else {
                    warnings.add("workouts.csv row $recordNumber: out-of-range rating '$rawRating', ignored")
                    null
                }
            }
            val startedFromRoutine = parseCsvBoolean(
                raw = values.field(table.headers, "startedFromRoutine"),
                default = false,
                tableName = "workouts.csv",
                recordNumber = recordNumber,
                fieldName = "startedFromRoutine",
                warnings = warnings
            )

            result.add(
                ParsedWorkout(
                    oldId = oldId,
                    recordNumber = recordNumber,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    name = values.field(table.headers, "name")?.takeIf { it.isNotEmpty() },
                    note = values.field(table.headers, "note")?.takeIf { it.isNotEmpty() },
                    rating = rating,
                    ratingNote = values.field(table.headers, "ratingNote")?.takeIf { it.isNotEmpty() },
                    oldRoutineDayId = values.field(table.headers, "routineDayId")?.trim()?.toLongOrNull(),
                    oldRoutineId = values.field(table.headers, "routineId")?.trim()?.toLongOrNull(),
                    routineNameSnapshot = values.field(table.headers, "routineNameSnapshot")?.takeIf { it.isNotEmpty() },
                    routineDayNameSnapshot = values.field(table.headers, "routineDayNameSnapshot")?.takeIf { it.isNotEmpty() },
                    startedFromRoutine = startedFromRoutine,
                    hasStartedFromRoutineColumn = hasStartedFromRoutineColumn
                )
            )
        }
        return result
    }

    private fun parseSets(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedSet> {
        if (table == null) return emptyList()
        val result = mutableListOf<ParsedSet>()
        for ((recordNumber, values) in table.rows) {
            val oldWorkoutId = values.field(table.headers, "workoutId")?.trim()?.toLongOrNull()
            val oldExerciseId = values.field(table.headers, "exerciseId")?.trim()?.toLongOrNull()
            val timestampMs = values.field(table.headers, "timestamp")?.trim()?.toLongOrNull()
            if (oldWorkoutId == null || oldExerciseId == null || timestampMs == null) {
                warnings.add("sets.csv row $recordNumber: skipped row missing valid workoutId, exerciseId, or timestamp")
                continue
            }

            val weight = values.field(table.headers, "weight").toNonNegativeFiniteFloatOrNull()
            val calories = values.field(table.headers, "calories").toNonNegativeFiniteFloatOrNull()
            val reps = values.field(table.headers, "reps")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            val rawRpe = values.field(table.headers, "rpe").toFiniteFloatOrNull()
            val rpe = if (rawRpe != null && rawRpe in 0f..10f) {
                rawRpe
            } else {
                if (rawRpe != null) {
                    warnings.add("sets.csv row $recordNumber: out-of-range rpe '$rawRpe', ignored")
                }
                null
            }
            val durationSeconds = values.field(table.headers, "durationSeconds")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            val distanceValue = values.field(table.headers, "distanceValue").toNonNegativeFiniteFloatOrNull()
            val rawDistanceUnit = values.field(table.headers, "distanceUnit")?.trim()?.takeIf { it.isNotEmpty() }
            var distanceUnitUnrecognized = false
            val distanceUnit = if (rawDistanceUnit == null) {
                null
            } else {
                runCatching { DistanceUnit.valueOf(rawDistanceUnit) }.getOrElse {
                    distanceUnitUnrecognized = true
                    warnings.add("sets.csv row $recordNumber: unrecognized distanceUnit '$rawDistanceUnit'")
                    null
                }
            }
            val distanceMeters = values.field(table.headers, "distanceMeters").toNonNegativeFiniteFloatOrNull()
            val isWarmup = parseCsvBoolean(
                raw = values.field(table.headers, "isWarmup"),
                default = false,
                tableName = "sets.csv",
                recordNumber = recordNumber,
                fieldName = "isWarmup",
                warnings = warnings
            )
            val isComplete = parseCsvBoolean(
                raw = values.field(table.headers, "isComplete"),
                default = true,
                tableName = "sets.csv",
                recordNumber = recordNumber,
                fieldName = "isComplete",
                warnings = warnings
            )
            val note = values.field(table.headers, "note")?.takeIf { it.isNotEmpty() }
            val supersetGroupId = values.field(table.headers, "supersetGroupId")?.trim()?.takeIf { it.isNotEmpty() }
            val supersetOrderIndex = values.field(table.headers, "supersetOrderIndex")?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            result.add(
                ParsedSet(
                    recordNumber = recordNumber,
                    oldWorkoutId = oldWorkoutId,
                    oldExerciseId = oldExerciseId,
                    weight = weight,
                    calories = calories,
                    reps = reps,
                    rpe = rpe,
                    durationSeconds = durationSeconds,
                    distanceValue = distanceValue,
                    distanceUnit = distanceUnit,
                    distanceUnitUnrecognized = distanceUnitUnrecognized,
                    distanceMeters = distanceMeters,
                    isWarmup = isWarmup,
                    isComplete = isComplete,
                    timestampMs = timestampMs,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
            )
        }
        return result
    }

    private fun parseWorkoutInstances(
        table: ParsedCsvTable?,
        warnings: MutableList<String>
    ): List<ParsedWorkoutInstance> {
        if (table == null) return emptyList()
        val result = mutableListOf<ParsedWorkoutInstance>()
        for ((recordNumber, values) in table.rows) {
            val oldWorkoutId = values.field(table.headers, "workoutId")?.trim()?.toLongOrNull()
            val oldExerciseId = values.field(table.headers, "exerciseId")?.trim()?.toLongOrNull()
            if (oldWorkoutId == null || oldExerciseId == null) {
                warnings.add("workout_exercise_instances.csv row $recordNumber: skipped row missing valid workoutId or exerciseId")
                continue
            }
            val oldRoutineExerciseId = values.field(table.headers, "routineExerciseId")?.trim()?.toLongOrNull()
            val orderIndex = values.field(table.headers, "orderIndex")?.trim()?.toIntOrNull() ?: 0
            val plannedSets = values.field(table.headers, "plannedSets")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val repMin = values.field(table.headers, "repMin")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val repMax = values.field(table.headers, "repMax")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val restSeconds = values.field(table.headers, "restSeconds")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            val notes = values.field(table.headers, "notes")?.takeIf { it.isNotEmpty() }
            val supersetGroupId = values.field(table.headers, "supersetGroupId")?.trim()?.takeIf { it.isNotEmpty() }
            val supersetOrderIndex = values.field(table.headers, "supersetOrderIndex")?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val isSkipped = parseCsvBoolean(
                raw = values.field(table.headers, "isSkipped"),
                default = false,
                tableName = "workout_exercise_instances.csv",
                recordNumber = recordNumber,
                fieldName = "isSkipped",
                warnings = warnings
            )
            val addedDuringWorkout = parseCsvBoolean(
                raw = values.field(table.headers, "addedDuringWorkout"),
                default = false,
                tableName = "workout_exercise_instances.csv",
                recordNumber = recordNumber,
                fieldName = "addedDuringWorkout",
                warnings = warnings
            )

            result.add(
                ParsedWorkoutInstance(
                    recordNumber = recordNumber,
                    oldWorkoutId = oldWorkoutId,
                    oldExerciseId = oldExerciseId,
                    oldRoutineExerciseId = oldRoutineExerciseId,
                    orderIndex = orderIndex,
                    plannedSets = plannedSets,
                    repMin = repMin,
                    repMax = repMax,
                    restSeconds = restSeconds,
                    notes = notes,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex,
                    isSkipped = isSkipped,
                    addedDuringWorkout = addedDuringWorkout
                )
            )
        }
        return result
    }

    private fun parseCsvBoolean(
        raw: String?,
        default: Boolean,
        tableName: String,
        recordNumber: Int,
        fieldName: String,
        warnings: MutableList<String>
    ): Boolean {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) return default
        return when (trimmed.lowercase(Locale.US)) {
            "true", "1" -> true
            "false", "0" -> false
            else -> {
                warnings.add("$tableName row $recordNumber: invalid boolean '$trimmed' for $fieldName, defaulted to $default")
                default
            }
        }
    }

    private fun remapSupersetId(
        rawSupersetId: String?,
        map: MutableMap<String, String>
    ): String? = rawSupersetId?.let { map.getOrPut(it) { UUID.randomUUID().toString() } }

    private fun WorkoutSet.toFingerprint(): SetFingerprint = SetFingerprint(
        exerciseId = exerciseId,
        timestampMs = timestamp.time,
        weightBits = weight?.toCanonicalFloatBits(),
        caloriesBits = calories?.toCanonicalFloatBits(),
        reps = reps,
        rpeBits = rpe?.toCanonicalFloatBits(),
        durationSeconds = durationSeconds,
        distanceValueBits = distanceValue?.toCanonicalFloatBits(),
        distanceUnit = distanceUnit,
        isWarmup = isWarmup,
        isComplete = isComplete,
        note = note
    )

    private fun WorkoutExerciseInstance.toFingerprint(): InstanceFingerprint = InstanceFingerprint(
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        plannedSets = plannedSets,
        repMin = repMin,
        repMax = repMax,
        restSeconds = restSeconds,
        notes = notes,
        isSkipped = isSkipped,
        addedDuringWorkout = addedDuringWorkout
    )

    private fun Float.toCanonicalFloatBits(): Int =
        if (this == 0f) 0f.toBits() else this.toBits()

    private fun List<String>.field(headers: Map<String, Int>, name: String): String? {
        val index = headers[name] ?: return null
        return getOrNull(index)
    }

    private fun String?.toFiniteFloatOrNull(): Float? =
        this?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }

    private fun String?.toNonNegativeFiniteFloatOrNull(): Float? =
        toFiniteFloatOrNull()?.takeIf { it >= 0f }

    private fun readFully(stream: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = stream.read(buffer, total, buffer.size - total)
            if (count == -1) break
            total += count
        }
        return total
    }

    private fun isZipSignature(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val isLocalFile = header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
        val isEmptyArchive = header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x05.toByte() &&
            header[3] == 0x06.toByte()
        return isLocalFile || isEmptyArchive
    }

    /**
     * Wraps the current ZIP entry stream without closing the parent ZipInputStream,
     * while enforcing strict uncompressed byte limits to guard against zip bombs.
     */
    private class BoundedEntryInputStream(
        delegate: InputStream,
        private val maxEntryBytes: Long,
        private val remainingTotalBytes: Long
    ) : FilterInputStream(delegate) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value != -1) {
                account(1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            if (count > 0) {
                account(count.toLong())
            }
            return count
        }

        override fun close() {
            // Do not close the underlying ZipInputStream; caller closes the entry.
        }

        private fun account(delta: Long) {
            bytesRead += delta
            if (bytesRead > maxEntryBytes || bytesRead > remainingTotalBytes) {
                throw InvalidBackupException("Backup ZIP exceeds maximum safe uncompressed size")
            }
        }
    }

    private companion object {
        private const val MAX_ENTRY_BYTES = 50L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 100L * 1024L * 1024L
        private const val MAX_ROWS_PER_TABLE = 500_000

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

        private val REQUIRED_COLUMNS = mapOf(
            "muscle_groups.csv" to listOf("name"),
            "exercises.csv" to listOf("id", "name"),
            "routines.csv" to listOf("id", "name"),
            "routine_days.csv" to listOf("id", "routineId", "name"),
            "workouts.csv" to listOf("id", "startTime"),
            "routine_exercises.csv" to listOf("routineDayId", "exerciseId"),
            "sets.csv" to listOf("workoutId", "exerciseId", "timestamp"),
            "workout_exercise_instances.csv" to listOf("workoutId", "exerciseId")
        )
    }
}
