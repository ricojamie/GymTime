package com.example.gymtime.util

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Workout
import java.io.InputStream
import java.util.Date
import java.util.zip.ZipInputStream
import javax.inject.Inject

class IronLogImporter @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val muscleGroupDao: MuscleGroupDao
) {
    data class ImportResult(
        val exercisesImported: Int,
        val workoutsImported: Int,
        val setsImported: Int,
        val routinesImported: Int,
        val routineDaysImported: Int,
        val routineExercisesImported: Int,
        val muscleGroupsImported: Int,
        val errors: List<String>
    )

    suspend fun import(inputStream: InputStream): ImportResult {
        val errors = mutableListOf<String>()
        val csvFiles = mutableMapOf<String, List<Map<String, String>>>()

        // Read all CSVs from ZIP
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    val name = entry.name.substringAfterLast('/')
                    csvFiles[name] = parseCsv(content)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (csvFiles.isEmpty()) {
            return ImportResult(0, 0, 0, 0, 0, 0, 0, listOf("No CSV files found in ZIP"))
        }

        // ID remapping tables
        val exerciseIdMap = mutableMapOf<Long, Long>() // old -> new
        val workoutIdMap = mutableMapOf<Long, Long>()
        val routineIdMap = mutableMapOf<Long, Long>()
        val routineDayIdMap = mutableMapOf<Long, Long>()

        // 1. Import muscle groups
        var muscleGroupsImported = 0
        csvFiles["muscle_groups.csv"]?.forEach { row ->
            val name = row["name"] ?: return@forEach
            try {
                muscleGroupDao.insertMuscleGroup(MuscleGroup(name = name))
                muscleGroupsImported++
            } catch (e: Exception) {
                // IGNORE conflict - muscle group already exists
            }
        }

        // 2. Import exercises (match by name to avoid duplicates)
        var exercisesImported = 0
        csvFiles["exercises.csv"]?.forEach { row ->
            try {
                val oldId = row["id"]?.toLongOrNull() ?: return@forEach
                val name = row["name"] ?: return@forEach

                // Check if exercise already exists by name
                val existing = exerciseDao.getExerciseByName(name)
                if (existing != null) {
                    exerciseIdMap[oldId] = existing.id
                    return@forEach
                }

                val exercise = Exercise(
                    id = 0,
                    name = name,
                    targetMuscle = row["targetMuscle"] ?: "Other",
                    logType = row["logType"]?.let { runCatching { LogType.valueOf(it) }.getOrNull() } ?: LogType.WEIGHT_REPS,
                    defaultDistanceUnit = row["defaultDistanceUnit"]
                        ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                        ?: DistanceUnit.MILES,
                    isCustom = row["isCustom"]?.toBooleanStrictOrNull() ?: true,
                    notes = row["notes"]?.takeIf { it.isNotEmpty() },
                    defaultRestSeconds = row["defaultRestSeconds"]?.toIntOrNull() ?: 90,
                    isStarred = row["isStarred"]?.toBooleanStrictOrNull() ?: false,
                    repTarget = row["repTarget"]?.toIntOrNull()
                )
                val newId = exerciseDao.insertExercise(exercise)
                exerciseIdMap[oldId] = newId
                exercisesImported++
            } catch (e: Exception) {
                errors.add("Exercise error: ${e.message}")
            }
        }

        // 3. Import routines
        var routinesImported = 0
        csvFiles["routines.csv"]?.forEach { row ->
            try {
                val oldId = row["id"]?.toLongOrNull() ?: return@forEach
                val routine = Routine(
                    id = 0,
                    name = row["name"] ?: "Imported Routine",
                    isActive = row["isActive"]?.toBooleanStrictOrNull() ?: false,
                    nextDayOrderIndex = row["nextDayOrderIndex"]?.toIntOrNull() ?: 0
                )
                val newId = routineDao.insertRoutine(routine)
                routineIdMap[oldId] = newId
                routinesImported++
            } catch (e: Exception) {
                errors.add("Routine error: ${e.message}")
            }
        }

        // 4. Import routine days
        var routineDaysImported = 0
        csvFiles["routine_days.csv"]?.forEach { row ->
            try {
                val oldId = row["id"]?.toLongOrNull() ?: return@forEach
                val oldRoutineId = row["routineId"]?.toLongOrNull() ?: return@forEach
                val newRoutineId = routineIdMap[oldRoutineId] ?: return@forEach

                val day = RoutineDay(
                    id = 0,
                    routineId = newRoutineId,
                    name = row["name"] ?: "Day",
                    orderIndex = row["orderIndex"]?.toIntOrNull() ?: 0
                )
                val newId = routineDao.insertRoutineDay(day)
                routineDayIdMap[oldId] = newId
                routineDaysImported++
            } catch (e: Exception) {
                errors.add("Routine day error: ${e.message}")
            }
        }

        // 5. Import workouts
        var workoutsImported = 0
        csvFiles["workouts.csv"]?.forEach { row ->
            try {
                val oldId = row["id"]?.toLongOrNull() ?: return@forEach
                val oldRoutineDayId = row["routineDayId"]?.toLongOrNull()
                val newRoutineDayId = oldRoutineDayId?.let { routineDayIdMap[it] }

                val workout = Workout(
                    id = 0,
                    startTime = Date(row["startTime"]?.toLongOrNull() ?: return@forEach),
                    endTime = row["endTime"]?.toLongOrNull()?.let { Date(it) },
                    name = row["name"]?.takeIf { it.isNotEmpty() },
                    note = row["note"]?.takeIf { it.isNotEmpty() },
                    rating = row["rating"]?.toIntOrNull(),
                    ratingNote = row["ratingNote"]?.takeIf { it.isNotEmpty() },
                    routineDayId = newRoutineDayId,
                    routineId = row["routineId"]?.toLongOrNull()?.let { routineIdMap[it] },
                    routineNameSnapshot = row["routineNameSnapshot"]?.takeIf { it.isNotEmpty() },
                    routineDayNameSnapshot = row["routineDayNameSnapshot"]?.takeIf { it.isNotEmpty() },
                    startedFromRoutine = row["startedFromRoutine"]?.toBooleanStrictOrNull() ?: false
                )
                val newId = workoutDao.insertWorkout(workout)
                workoutIdMap[oldId] = newId
                workoutsImported++
            } catch (e: Exception) {
                errors.add("Workout error: ${e.message}")
            }
        }

        // 6. Import routine exercises
        var routineExercisesImported = 0
        csvFiles["routine_exercises.csv"]?.forEach { row ->
            try {
                val oldRoutineDayId = row["routineDayId"]?.toLongOrNull() ?: return@forEach
                val oldExerciseId = row["exerciseId"]?.toLongOrNull() ?: return@forEach
                val newRoutineDayId = routineDayIdMap[oldRoutineDayId] ?: return@forEach
                val newExerciseId = exerciseIdMap[oldExerciseId] ?: return@forEach

                val re = RoutineExercise(
                    id = 0,
                    routineDayId = newRoutineDayId,
                    exerciseId = newExerciseId,
                    orderIndex = row["orderIndex"]?.toIntOrNull() ?: 0,
                    targetSets = row["targetSets"]?.toIntOrNull() ?: 3,
                    targetRepsMin = row["targetRepsMin"]?.toIntOrNull(),
                    targetRepsMax = row["targetRepsMax"]?.toIntOrNull(),
                    targetRestSeconds = row["targetRestSeconds"]?.toIntOrNull(),
                    notes = row["notes"]?.takeIf { it.isNotEmpty() },
                    supersetGroupId = row["supersetGroupId"]?.takeIf { it.isNotEmpty() },
                    supersetOrderIndex = row["supersetOrderIndex"]?.toIntOrNull() ?: 0
                )
                routineDao.insertRoutineExercise(re)
                routineExercisesImported++
            } catch (e: Exception) {
                errors.add("Routine exercise error: ${e.message}")
            }
        }

        // 7. Import sets
        var setsImported = 0
        csvFiles["sets.csv"]?.forEach { row ->
            try {
                val oldWorkoutId = row["workoutId"]?.toLongOrNull() ?: return@forEach
                val oldExerciseId = row["exerciseId"]?.toLongOrNull() ?: return@forEach
                val newWorkoutId = workoutIdMap[oldWorkoutId] ?: return@forEach
                val newExerciseId = exerciseIdMap[oldExerciseId] ?: return@forEach

                val set = com.example.gymtime.data.db.entity.Set(
                    id = 0,
                    workoutId = newWorkoutId,
                    exerciseId = newExerciseId,
                    weight = row["weight"]?.toFloatOrNull(),
                    calories = row["calories"]?.toFloatOrNull(),
                    reps = row["reps"]?.toIntOrNull(),
                    rpe = row["rpe"]?.toFloatOrNull(),
                    durationSeconds = row["durationSeconds"]?.toIntOrNull(),
                    distanceValue = row["distanceValue"]?.toFloatOrNull(),
                    distanceUnit = row["distanceUnit"]
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() },
                    distanceMeters = row["distanceMeters"]?.toFloatOrNull(),
                    isWarmup = row["isWarmup"]?.toBooleanStrictOrNull() ?: false,
                    isComplete = row["isComplete"]?.toBooleanStrictOrNull() ?: true,
                    timestamp = Date(row["timestamp"]?.toLongOrNull() ?: return@forEach),
                    note = row["note"]?.takeIf { it.isNotEmpty() },
                    supersetGroupId = row["supersetGroupId"]?.takeIf { it.isNotEmpty() },
                    supersetOrderIndex = row["supersetOrderIndex"]?.toIntOrNull() ?: 0
                )
                setDao.insertSet(set)
                setsImported++
            } catch (e: Exception) {
                errors.add("Set error: ${e.message}")
            }
        }

        return ImportResult(
            exercisesImported = exercisesImported,
            workoutsImported = workoutsImported,
            setsImported = setsImported,
            routinesImported = routinesImported,
            routineDaysImported = routineDaysImported,
            routineExercisesImported = routineExercisesImported,
            muscleGroupsImported = muscleGroupsImported,
            errors = errors
        )
    }

    private fun parseCsv(content: String): List<Map<String, String>> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val headers = parseCsvLine(lines[0])
        return lines.drop(1).mapNotNull { line ->
            try {
                val values = parseCsvLine(line)
                headers.zip(values).toMap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var i = 0
        while (i <= line.length) {
            if (i == line.length) {
                fields.add("")
                break
            }
            if (line[i] == '"') {
                // Quoted field
                val sb = StringBuilder()
                i++ // skip opening quote
                while (i < line.length) {
                    if (line[i] == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            i++ // skip closing quote
                            break
                        }
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                fields.add(sb.toString())
                if (i < line.length && line[i] == ',') i++ // skip comma
            } else {
                // Unquoted field
                val commaIndex = line.indexOf(',', i)
                if (commaIndex == -1) {
                    fields.add(line.substring(i))
                    break
                } else {
                    fields.add(line.substring(i, commaIndex))
                    i = commaIndex + 1
                }
            }
        }
        return fields
    }
}
