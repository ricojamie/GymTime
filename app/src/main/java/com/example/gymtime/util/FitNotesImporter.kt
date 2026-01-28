package com.example.gymtime.util

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Imports workout data from FitNotes CSV exports.
 *
 * FitNotes CSV format:
 * Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time
 * 2025-01-20,Incline Bench Press,Chest,115.0,lbs,7,,,
 */
class FitNotesImporter @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {
    data class ImportResult(
        val workoutsImported: Int,
        val setsImported: Int,
        val exercisesCreated: Int,
        val duplicatesSkipped: Int,
        val errors: List<String>
    )

    private data class CsvRow(
        val date: String,
        val exerciseName: String,
        val category: String,
        val weight: Float?,
        val weightUnit: String?,
        val reps: Int?,
        val distance: Float?,
        val distanceUnit: String?,
        val time: String?
    )

    suspend fun importCsv(inputStream: InputStream): ImportResult {
        val errors = mutableListOf<String>()
        val rows = mutableListOf<CsvRow>()

        // Parse CSV
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var lineNumber = 0
            reader.forEachLine { line ->
                lineNumber++
                if (lineNumber == 1) {
                    // Skip header row
                    return@forEachLine
                }
                try {
                    val row = parseCsvLine(line)
                    if (row != null) {
                        rows.add(row)
                    }
                } catch (e: Exception) {
                    errors.add("Line $lineNumber: ${e.message}")
                }
            }
        }

        if (rows.isEmpty()) {
            return ImportResult(0, 0, 0, 0, errors + "No valid data rows found")
        }

        // Group rows by date to create one workout per day
        val rowsByDate = rows.groupBy { it.date }

        var workoutsImported = 0
        var setsImported = 0
        var exercisesCreated = 0
        var duplicatesSkipped = 0

        // Process each date group
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val exerciseCache = mutableMapOf<String, Long>() // name -> exerciseId

        for ((dateStr, dateRows) in rowsByDate) {
            try {
                val parsedDate = dateFormat.parse(dateStr) ?: continue

                // Create workout for this date
                val workoutStartTime = getStartOfDay(parsedDate)
                val workoutEndTime = getEndOfDay(parsedDate)

                val workout = Workout(
                    startTime = workoutStartTime,
                    endTime = workoutEndTime,
                    name = "FitNotes Import",
                    note = "Imported from FitNotes",
                    rating = null,
                    ratingNote = null,
                    routineDayId = null
                )
                val workoutId = workoutDao.insertWorkout(workout)
                workoutsImported++

                // Process each set in this workout
                for (row in dateRows) {
                    try {
                        // Get or create exercise
                        val exerciseId = getOrCreateExercise(
                            name = row.exerciseName,
                            category = row.category,
                            row = row,
                            cache = exerciseCache
                        ) { exercisesCreated++ }

                        // Check for duplicates
                        val dayStart = workoutStartTime.time
                        val dayEnd = workoutEndTime.time
                        val weight = convertWeight(row.weight, row.weightUnit)
                        val reps = row.reps

                        val existingCount = setDao.countMatchingSets(
                            exerciseId = exerciseId,
                            dayStart = dayStart,
                            dayEnd = dayEnd,
                            weight = weight,
                            reps = reps
                        )

                        if (existingCount > 0) {
                            duplicatesSkipped++
                            continue
                        }

                        // Create set
                        val set = Set(
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            weight = weight,
                            reps = reps,
                            rpe = null,
                            durationSeconds = parseTime(row.time),
                            distanceMeters = convertDistance(row.distance, row.distanceUnit),
                            isWarmup = false,
                            isComplete = true,
                            timestamp = workoutStartTime,
                            note = null,
                            supersetGroupId = null,
                            supersetOrderIndex = 0
                        )
                        setDao.insertSet(set)
                        setsImported++
                    } catch (e: Exception) {
                        errors.add("Set error (${row.exerciseName}): ${e.message}")
                    }
                }
            } catch (e: Exception) {
                errors.add("Date error ($dateStr): ${e.message}")
            }
        }

        return ImportResult(
            workoutsImported = workoutsImported,
            setsImported = setsImported,
            exercisesCreated = exercisesCreated,
            duplicatesSkipped = duplicatesSkipped,
            errors = errors
        )
    }

    private fun parseCsvLine(line: String): CsvRow? {
        val parts = line.split(",")
        if (parts.size < 6) return null

        val date = parts.getOrNull(0)?.trim() ?: return null
        val exerciseName = parts.getOrNull(1)?.trim() ?: return null
        val category = parts.getOrNull(2)?.trim() ?: return null

        if (date.isEmpty() || exerciseName.isEmpty()) return null

        return CsvRow(
            date = date,
            exerciseName = exerciseName,
            category = category.ifEmpty { "Other" },
            weight = parts.getOrNull(3)?.trim()?.toFloatOrNull(),
            weightUnit = parts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() },
            reps = parts.getOrNull(5)?.trim()?.toIntOrNull(),
            distance = parts.getOrNull(6)?.trim()?.toFloatOrNull(),
            distanceUnit = parts.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() },
            time = parts.getOrNull(8)?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun getOrCreateExercise(
        name: String,
        category: String,
        row: CsvRow,
        cache: MutableMap<String, Long>,
        onCreated: () -> Unit
    ): Long {
        // Check cache first
        cache[name.lowercase()]?.let { return it }

        // Check database
        val existing = exerciseDao.getExerciseByName(name)
        if (existing != null) {
            cache[name.lowercase()] = existing.id
            return existing.id
        }

        // Create new exercise
        val logType = determineLogType(row)
        val exercise = Exercise(
            name = name,
            targetMuscle = normalizeCategory(category),
            logType = logType,
            isCustom = true,
            notes = "Imported from FitNotes",
            defaultRestSeconds = 90,
            isStarred = false
        )
        val id = exerciseDao.insertExercise(exercise)
        cache[name.lowercase()] = id
        onCreated()
        return id
    }

    private fun determineLogType(row: CsvRow): LogType {
        return when {
            row.weight != null && row.reps != null -> LogType.WEIGHT_REPS
            row.reps != null && row.weight == null -> LogType.REPS_ONLY
            row.distance != null && row.time != null -> LogType.DISTANCE_TIME
            row.weight != null && row.distance != null -> LogType.WEIGHT_DISTANCE
            row.time != null -> LogType.DURATION
            else -> LogType.WEIGHT_REPS // Default
        }
    }

    private fun normalizeCategory(category: String): String {
        // Map FitNotes categories to IronLog muscle groups
        return when (category.lowercase()) {
            "chest" -> "Chest"
            "back" -> "Back"
            "biceps", "bicep" -> "Biceps"
            "triceps", "tricep" -> "Triceps"
            "shoulders", "shoulder", "delts" -> "Shoulders"
            "legs", "quads", "quadriceps", "hamstrings", "glutes", "calves" -> "Legs"
            "core", "abs", "abdominals" -> "Core"
            "cardio" -> "Cardio"
            else -> category.replaceFirstChar { it.uppercase() }
        }
    }

    private fun convertWeight(weight: Float?, unit: String?): Float? {
        if (weight == null) return null
        return when (unit?.lowercase()) {
            "kg" -> weight * 2.20462f // Convert kg to lbs
            else -> weight // Already in lbs or unknown
        }
    }

    private fun convertDistance(distance: Float?, unit: String?): Float? {
        if (distance == null) return null
        return when (unit?.lowercase()) {
            "km" -> distance * 1000f // Convert km to meters
            "mi", "miles" -> distance * 1609.34f // Convert miles to meters
            "m" -> distance // Already in meters
            else -> distance // Unknown unit, keep as-is
        }
    }

    private fun parseTime(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        // Try to parse as seconds
        time.toIntOrNull()?.let { return it }
        // Try to parse as mm:ss or hh:mm:ss
        val parts = time.split(":")
        return when (parts.size) {
            2 -> {
                val mins = parts[0].toIntOrNull() ?: 0
                val secs = parts[1].toIntOrNull() ?: 0
                mins * 60 + secs
            }
            3 -> {
                val hrs = parts[0].toIntOrNull() ?: 0
                val mins = parts[1].toIntOrNull() ?: 0
                val secs = parts[2].toIntOrNull() ?: 0
                hrs * 3600 + mins * 60 + secs
            }
            else -> null
        }
    }

    private fun getStartOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun getEndOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }
}
