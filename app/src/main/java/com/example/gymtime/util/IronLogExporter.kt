package com.example.gymtime.util

import androidx.room.withTransaction
import com.example.gymtime.BuildConfig
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class IronLogExporter @Inject constructor(
    private val database: GymTimeDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val workoutPlanDao: WorkoutPlanDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var runInTransaction: suspend (suspend () -> DatabaseSnapshot) -> DatabaseSnapshot =
        { block -> database.withTransaction { block() } }
    private var appVersionProvider: () -> String = { BuildConfig.VERSION_NAME }
    private var clockMsProvider: () -> Long = { System.currentTimeMillis() }

    internal constructor(
        database: GymTimeDatabase,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        setDao: SetDao,
        routineDao: RoutineDao,
        muscleGroupDao: MuscleGroupDao,
        workoutPlanDao: WorkoutPlanDao,
        userPreferencesRepository: UserPreferencesRepository,
        runInTransaction: suspend (suspend () -> DatabaseSnapshot) -> DatabaseSnapshot,
        appVersionProvider: () -> String = { "test" },
        clockMsProvider: () -> Long = { 1_700_000_000_000L }
    ) : this(
        database = database,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        setDao = setDao,
        routineDao = routineDao,
        muscleGroupDao = muscleGroupDao,
        workoutPlanDao = workoutPlanDao,
        userPreferencesRepository = userPreferencesRepository
    ) {
        this.runInTransaction = runInTransaction
        this.appVersionProvider = appVersionProvider
        this.clockMsProvider = clockMsProvider
    }

    internal data class DatabaseSnapshot(
        val muscleGroups: List<String>,
        val exercises: List<Exercise>,
        val routines: List<Routine>,
        val routineDays: List<RoutineDay>,
        val workouts: List<Workout>,
        val routineExercises: List<RoutineExercise>,
        val sets: List<Set>,
        val workoutInstances: List<WorkoutExerciseInstance>
    )

    data class ExportResult(
        val exerciseCount: Int,
        val workoutCount: Int,
        val setCount: Int,
        val routineCount: Int,
        val routineDayCount: Int,
        val routineExerciseCount: Int,
        val muscleGroupCount: Int,
        val workoutExerciseInstanceCount: Int = 0,
        val preferencesIncluded: Boolean = true
    )

    suspend fun export(outputStream: OutputStream): ExportResult = withContext(Dispatchers.IO) {
        outputStream.use { stream ->
            // Capture a single point-in-time transactional snapshot across all tables
            // so concurrent writes under WAL mode cannot emit orphaned foreign keys.
            val snapshot = runInTransaction {
                DatabaseSnapshot(
                    muscleGroups = muscleGroupDao.getAllMuscleGroupNames(),
                    exercises = exerciseDao.getAllExercisesSync(),
                    routines = routineDao.getAllRoutinesSync(),
                    routineDays = routineDao.getAllRoutineDays(),
                    workouts = workoutDao.getAllWorkoutsSync(),
                    routineExercises = routineDao.getAllRoutineExercises(),
                    sets = setDao.getAllSets(),
                    workoutInstances = workoutPlanDao.getAllInstancesSync()
                )
            }
            val preferences = userPreferencesRepository.exportPortablePreferences()
            val manifest = IronLogBackupFormat.BackupManifest(
                formatVersion = IronLogBackupFormat.CURRENT_FORMAT_VERSION,
                dbSchemaVersion = IronLogBackupFormat.DB_SCHEMA_VERSION,
                appVersion = appVersionProvider(),
                source = IronLogBackupFormat.SOURCE_ANDROID,
                exportedAtEpochMs = clockMsProvider(),
                weightUnit = IronLogBackupFormat.CANONICAL_WEIGHT_UNIT,
                counts = IronLogBackupFormat.BackupTableCounts(
                    muscleGroups = snapshot.muscleGroups.size,
                    exercises = snapshot.exercises.size,
                    routines = snapshot.routines.size,
                    routineDays = snapshot.routineDays.size,
                    routineExercises = snapshot.routineExercises.size,
                    workouts = snapshot.workouts.size,
                    sets = snapshot.sets.size,
                    workoutExerciseInstances = snapshot.workoutInstances.size
                )
            )

            ZipOutputStream(stream).use { zip ->
                val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))

                writeTextEntry(
                    zip = zip,
                    writer = writer,
                    entryName = IronLogBackupFormat.MANIFEST_ENTRY_NAME,
                    content = IronLogBackupFormat.encodeManifest(manifest)
                )
                writeTextEntry(
                    zip = zip,
                    writer = writer,
                    entryName = IronLogBackupFormat.PREFERENCES_ENTRY_NAME,
                    content = IronLogBackupFormat.encodePreferences(preferences)
                )

                // Write CSV entries in dependency order for clean streaming compatibility.
                writeCsvEntry(zip, writer, "muscle_groups.csv", "name", snapshot.muscleGroups) { mg ->
                    listOf(mg)
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "exercises.csv",
                    header = "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget",
                    items = snapshot.exercises
                ) { e ->
                    listOf(
                        e.id.toString(),
                        e.name,
                        e.targetMuscle,
                        e.logType.name,
                        e.defaultDistanceUnit.name,
                        e.isCustom.toString(),
                        e.notes ?: "",
                        e.defaultRestSeconds.toString(),
                        e.isStarred.toString(),
                        e.repTarget?.toString() ?: ""
                    )
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routines.csv",
                    header = "id,name,isActive,nextDayOrderIndex",
                    items = snapshot.routines
                ) { r ->
                    listOf(r.id.toString(), r.name, r.isActive.toString(), r.nextDayOrderIndex.toString())
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routine_days.csv",
                    header = "id,routineId,name,orderIndex",
                    items = snapshot.routineDays
                ) { d ->
                    listOf(d.id.toString(), d.routineId.toString(), d.name, d.orderIndex.toString())
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "workouts.csv",
                    header = "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine",
                    items = snapshot.workouts
                ) { w ->
                    listOf(
                        w.id.toString(),
                        w.startTime.time.toString(),
                        w.endTime?.time?.toString() ?: "",
                        w.name ?: "",
                        w.note ?: "",
                        w.rating?.toString() ?: "",
                        w.ratingNote ?: "",
                        w.routineDayId?.toString() ?: "",
                        w.routineId?.toString() ?: "",
                        w.routineNameSnapshot ?: "",
                        w.routineDayNameSnapshot ?: "",
                        w.startedFromRoutine.toString()
                    )
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routine_exercises.csv",
                    header = "id,routineDayId,exerciseId,orderIndex,targetSets,targetRepsMin,targetRepsMax,targetRestSeconds,notes,supersetGroupId,supersetOrderIndex",
                    items = snapshot.routineExercises
                ) { re ->
                    listOf(
                        re.id.toString(),
                        re.routineDayId.toString(),
                        re.exerciseId.toString(),
                        re.orderIndex.toString(),
                        re.targetSets.toString(),
                        re.targetRepsMin?.toString() ?: "",
                        re.targetRepsMax?.toString() ?: "",
                        re.targetRestSeconds?.toString() ?: "",
                        re.notes ?: "",
                        re.supersetGroupId ?: "",
                        re.supersetOrderIndex.toString()
                    )
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "sets.csv",
                    header = "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex",
                    items = snapshot.sets
                ) { s ->
                    listOf(
                        s.id.toString(),
                        s.workoutId.toString(),
                        s.exerciseId.toString(),
                        s.weight?.takeIf(Float::isFinite)?.toString() ?: "",
                        s.calories?.takeIf(Float::isFinite)?.toString() ?: "",
                        s.reps?.toString() ?: "",
                        s.rpe?.takeIf(Float::isFinite)?.toString() ?: "",
                        s.durationSeconds?.toString() ?: "",
                        s.distanceValue?.takeIf(Float::isFinite)?.toString() ?: "",
                        s.distanceUnit?.name ?: "",
                        s.distanceMeters?.takeIf(Float::isFinite)?.toString() ?: "",
                        s.isWarmup.toString(),
                        s.isComplete.toString(),
                        s.timestamp.time.toString(),
                        s.note ?: "",
                        s.supersetGroupId ?: "",
                        s.supersetOrderIndex.toString()
                    )
                }

                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "workout_exercise_instances.csv",
                    header = "id,workoutId,exerciseId,routineExerciseId,orderIndex,plannedSets,repMin,repMax,restSeconds,notes,supersetGroupId,supersetOrderIndex,isSkipped,addedDuringWorkout",
                    items = snapshot.workoutInstances
                ) { inst ->
                    listOf(
                        inst.id.toString(),
                        inst.workoutId.toString(),
                        inst.exerciseId.toString(),
                        inst.routineExerciseId?.toString() ?: "",
                        inst.orderIndex.toString(),
                        inst.plannedSets?.toString() ?: "",
                        inst.repMin?.toString() ?: "",
                        inst.repMax?.toString() ?: "",
                        inst.restSeconds?.toString() ?: "",
                        inst.notes ?: "",
                        inst.supersetGroupId ?: "",
                        inst.supersetOrderIndex.toString(),
                        inst.isSkipped.toString(),
                        inst.addedDuringWorkout.toString()
                    )
                }

                ExportResult(
                    exerciseCount = snapshot.exercises.size,
                    workoutCount = snapshot.workouts.size,
                    setCount = snapshot.sets.size,
                    routineCount = snapshot.routines.size,
                    routineDayCount = snapshot.routineDays.size,
                    routineExerciseCount = snapshot.routineExercises.size,
                    muscleGroupCount = snapshot.muscleGroups.size,
                    workoutExerciseInstanceCount = snapshot.workoutInstances.size,
                    preferencesIncluded = true
                )
            }
        }
    }

    private fun writeTextEntry(
        zip: ZipOutputStream,
        writer: BufferedWriter,
        entryName: String,
        content: String
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        writer.write(content)
        writer.flush()
        zip.closeEntry()
    }

    private fun <T> writeCsvEntry(
        zip: ZipOutputStream,
        writer: BufferedWriter,
        entryName: String,
        header: String,
        items: List<T>,
        rowMapper: (T) -> List<String>
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        writer.write(header)
        writer.write("\n")
        for (item in items) {
            val fields = rowMapper(item)
            for (i in fields.indices) {
                if (i > 0) writer.write(",")
                writer.write(escapeCsvField(fields[i]))
            }
            writer.write("\n")
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun escapeCsvField(field: String): String {
        return if (
            field.contains(',') ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r')
        ) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
