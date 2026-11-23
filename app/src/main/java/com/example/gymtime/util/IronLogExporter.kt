package com.example.gymtime.util

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class IronLogExporter @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val workoutPlanDao: WorkoutPlanDao
) {
    data class ExportResult(
        val exerciseCount: Int,
        val workoutCount: Int,
        val setCount: Int,
        val routineCount: Int,
        val routineDayCount: Int,
        val routineExerciseCount: Int,
        val muscleGroupCount: Int,
        val workoutExerciseInstanceCount: Int = 0
    )

    suspend fun export(outputStream: OutputStream): ExportResult = withContext(Dispatchers.IO) {
        outputStream.use { stream ->
            ZipOutputStream(stream).use { zip ->
                val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))

                // Write entries in dependency order so streaming import requires zero buffering.
                val muscleGroups = muscleGroupDao.getAllMuscleGroupNames()
                writeCsvEntry(zip, writer, "muscle_groups.csv", "name", muscleGroups) { mg ->
                    listOf(mg)
                }

                val exercises = exerciseDao.getAllExercisesSync()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "exercises.csv",
                    header = "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget",
                    items = exercises
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

                val routines = routineDao.getAllRoutinesSync()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routines.csv",
                    header = "id,name,isActive,nextDayOrderIndex",
                    items = routines
                ) { r ->
                    listOf(r.id.toString(), r.name, r.isActive.toString(), r.nextDayOrderIndex.toString())
                }

                val routineDays = routineDao.getAllRoutineDays()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routine_days.csv",
                    header = "id,routineId,name,orderIndex",
                    items = routineDays
                ) { d ->
                    listOf(d.id.toString(), d.routineId.toString(), d.name, d.orderIndex.toString())
                }

                val workouts = workoutDao.getAllWorkoutsSync()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "workouts.csv",
                    header = "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine",
                    items = workouts
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

                val routineExercises = routineDao.getAllRoutineExercises()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "routine_exercises.csv",
                    header = "id,routineDayId,exerciseId,orderIndex,targetSets,targetRepsMin,targetRepsMax,targetRestSeconds,notes,supersetGroupId,supersetOrderIndex",
                    items = routineExercises
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

                val sets = setDao.getAllSets()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "sets.csv",
                    header = "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex",
                    items = sets
                ) { s ->
                    listOf(
                        s.id.toString(),
                        s.workoutId.toString(),
                        s.exerciseId.toString(),
                        s.weight?.toString() ?: "",
                        s.calories?.toString() ?: "",
                        s.reps?.toString() ?: "",
                        s.rpe?.toString() ?: "",
                        s.durationSeconds?.toString() ?: "",
                        s.distanceValue?.toString() ?: "",
                        s.distanceUnit?.name ?: "",
                        s.distanceMeters?.toString() ?: "",
                        s.isWarmup.toString(),
                        s.isComplete.toString(),
                        s.timestamp.time.toString(),
                        s.note ?: "",
                        s.supersetGroupId ?: "",
                        s.supersetOrderIndex.toString()
                    )
                }

                val workoutInstances = workoutPlanDao.getAllInstancesSync()
                writeCsvEntry(
                    zip = zip,
                    writer = writer,
                    entryName = "workout_exercise_instances.csv",
                    header = "id,workoutId,exerciseId,routineExerciseId,orderIndex,plannedSets,repMin,repMax,restSeconds,notes,supersetGroupId,supersetOrderIndex,isSkipped,addedDuringWorkout",
                    items = workoutInstances
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
                    exerciseCount = exercises.size,
                    workoutCount = workouts.size,
                    setCount = sets.size,
                    routineCount = routines.size,
                    routineDayCount = routineDays.size,
                    routineExerciseCount = routineExercises.size,
                    muscleGroupCount = muscleGroups.size,
                    workoutExerciseInstanceCount = workoutInstances.size
                )
            }
        }
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
