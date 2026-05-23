package com.example.gymtime.util

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class IronLogExporter @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao
) {
    data class ExportResult(
        val exerciseCount: Int,
        val workoutCount: Int,
        val setCount: Int,
        val routineCount: Int,
        val routineDayCount: Int,
        val routineExerciseCount: Int,
        val muscleGroupCount: Int
    )

    suspend fun export(outputStream: OutputStream): ExportResult {
        val exercises = exerciseDao.getAllExercisesSync()
        val workouts = workoutDao.getAllWorkoutsSync()
        val sets = setDao.getAllSets()
        val routines = routineDao.getAllRoutinesSync()
        val routineDays = routineDao.getAllRoutineDays()
        val routineExercises = routineDao.getAllRoutineExercises()
        val muscleGroups = muscleGroupDao.getAllMuscleGroupNames()

        ZipOutputStream(outputStream).use { zip ->
            // muscle_groups.csv
            zip.putNextEntry(ZipEntry("muscle_groups.csv"))
            zip.write(buildCsv(
                header = "name",
                rows = muscleGroups.map { listOf(it) }
            ))
            zip.closeEntry()

            // exercises.csv
            zip.putNextEntry(ZipEntry("exercises.csv"))
            zip.write(buildCsv(
                header = "id,name,targetMuscle,logType,defaultDistanceUnit,isCustom,notes,defaultRestSeconds,isStarred,repTarget",
                rows = exercises.map { e ->
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
            ))
            zip.closeEntry()

            // workouts.csv
            zip.putNextEntry(ZipEntry("workouts.csv"))
            zip.write(buildCsv(
                header = "id,startTime,endTime,name,note,rating,ratingNote,routineDayId,routineId,routineNameSnapshot,routineDayNameSnapshot,startedFromRoutine",
                rows = workouts.map { w ->
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
            ))
            zip.closeEntry()

            // sets.csv
            zip.putNextEntry(ZipEntry("sets.csv"))
            zip.write(buildCsv(
                header = "id,workoutId,exerciseId,weight,calories,reps,rpe,durationSeconds,distanceValue,distanceUnit,distanceMeters,isWarmup,isComplete,timestamp,note,supersetGroupId,supersetOrderIndex",
                rows = sets.map { s ->
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
            ))
            zip.closeEntry()

            // routines.csv
            zip.putNextEntry(ZipEntry("routines.csv"))
            zip.write(buildCsv(
                header = "id,name,isActive,nextDayOrderIndex",
                rows = routines.map { r ->
                    listOf(r.id.toString(), r.name, r.isActive.toString(), r.nextDayOrderIndex.toString())
                }
            ))
            zip.closeEntry()

            // routine_days.csv
            zip.putNextEntry(ZipEntry("routine_days.csv"))
            zip.write(buildCsv(
                header = "id,routineId,name,orderIndex",
                rows = routineDays.map { d ->
                    listOf(d.id.toString(), d.routineId.toString(), d.name, d.orderIndex.toString())
                }
            ))
            zip.closeEntry()

            // routine_exercises.csv
            zip.putNextEntry(ZipEntry("routine_exercises.csv"))
            zip.write(buildCsv(
                header = "id,routineDayId,exerciseId,orderIndex,targetSets,targetRepsMin,targetRepsMax,targetRestSeconds,notes,supersetGroupId,supersetOrderIndex",
                rows = routineExercises.map { re ->
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
            ))
            zip.closeEntry()
        }

        return ExportResult(
            exerciseCount = exercises.size,
            workoutCount = workouts.size,
            setCount = sets.size,
            routineCount = routines.size,
            routineDayCount = routineDays.size,
            routineExerciseCount = routineExercises.size,
            muscleGroupCount = muscleGroups.size
        )
    }

    private fun buildCsv(header: String, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine(header)
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsvField(it) })
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
