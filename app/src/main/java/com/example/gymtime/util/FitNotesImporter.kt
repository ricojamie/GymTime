package com.example.gymtime.util

import androidx.room.withTransaction
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.WARMUP_MUSCLE_GROUP
import com.example.gymtime.data.db.entity.Workout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
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
    private val database: GymTimeDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) {
    private var runInTransaction: suspend (suspend () -> ImportResult) -> ImportResult =
        { block -> database.withTransaction { block() } }

    internal constructor(
        database: GymTimeDatabase,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        setDao: SetDao,
        runInTransaction: suspend (suspend () -> ImportResult) -> ImportResult
    ) : this(database, exerciseDao, workoutDao, setDao) {
        this.runInTransaction = runInTransaction
    }

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

    private data class SetMatchKey(
        val exerciseId: Long,
        val weight: Float?,
        val reps: Int?,
        val durationSeconds: Int?,
        val distanceValue: Float?,
        val distanceUnit: DistanceUnit?,
        val calories: Float?
    )

    suspend fun importCsv(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val rows = mutableListOf<CsvRow>()

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            try {
                CsvParser.forEachRecord(reader) { recordNumber, fields ->
                    if (recordNumber == 1) {
                        return@forEachRecord
                    }
                    try {
                        val row = parseCsvFields(fields)
                        if (row != null) {
                            rows.add(row)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        errors.add("Record $recordNumber: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errors.add("CSV parse error: ${e.message}")
            }
        }

        if (rows.isEmpty()) {
            return@withContext ImportResult(0, 0, 0, 0, errors + "No valid data rows found")
        }

        val rowsByDate = rows.groupBy { it.date }
        val zoneId = ZoneId.systemDefault()

        runInTransaction {
            var workoutsImported = 0
            var setsImported = 0
            var exercisesCreated = 0
            var duplicatesSkipped = 0

            val exerciseCache = mutableMapOf<String, Long>()

            for ((dateStr, dateRows) in rowsByDate) {
                val localDate = try {
                    LocalDate.parse(dateStr, DATE_FORMATTER)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errors.add("Date error ($dateStr): ${e.message}")
                    continue
                }

                val workoutStartMs = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val workoutEndMs = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
                val workoutStartTime = Date(workoutStartMs)
                val workoutEndTime = Date(workoutEndMs)

                // Query pre-existing sets for this day once before inserting any rows so
                // identical straight sets within the same import all import, while re-imports
                // skip only the already-persisted multiplicity.
                val remainingExistingCounts = mutableMapOf<SetMatchKey, Int>()
                val existingSetsForDay = setDao.getSetsWithExerciseInRange(
                    startDate = workoutStartMs,
                    endDate = workoutEndMs
                )
                for (existing in existingSetsForDay) {
                    val s = existing.set
                    val key = SetMatchKey(
                        exerciseId = s.exerciseId,
                        weight = normalizeFloatKey(s.weight),
                        reps = s.reps,
                        durationSeconds = s.durationSeconds,
                        distanceValue = normalizeFloatKey(s.distanceValue),
                        distanceUnit = if (s.distanceValue != null) s.distanceUnit else null,
                        calories = normalizeFloatKey(s.calories)
                    )
                    remainingExistingCounts[key] = (remainingExistingCounts[key] ?: 0) + 1
                }

                val pendingSets = mutableListOf<Set>()

                for (row in dateRows) {
                    val exerciseId = getOrCreateExercise(
                        name = row.exerciseName,
                        category = row.category,
                        row = row,
                        cache = exerciseCache
                    ) { exercisesCreated++ }

                    val weight = convertWeight(row.weight, row.weightUnit)
                    val reps = row.reps
                    val durationSeconds = parseTime(row.time)
                    val distanceValue = row.distance
                    val distanceUnit = if (distanceValue != null || row.distanceUnit != null) {
                        parseDistanceUnit(row.distanceUnit)
                    } else {
                        null
                    }
                    val distanceMeters = convertDistance(distanceValue, row.distanceUnit)

                    val key = SetMatchKey(
                        exerciseId = exerciseId,
                        weight = normalizeFloatKey(weight),
                        reps = reps,
                        durationSeconds = durationSeconds,
                        distanceValue = normalizeFloatKey(distanceValue),
                        distanceUnit = if (distanceValue != null) distanceUnit else null,
                        calories = null
                    )

                    val remaining = remainingExistingCounts[key] ?: 0
                    if (remaining > 0) {
                        remainingExistingCounts[key] = remaining - 1
                        duplicatesSkipped++
                        continue
                    }

                    pendingSets.add(
                        Set(
                            workoutId = 0L,
                            exerciseId = exerciseId,
                            weight = weight,
                            reps = reps,
                            rpe = null,
                            durationSeconds = durationSeconds,
                            distanceValue = distanceValue,
                            distanceUnit = distanceUnit,
                            distanceMeters = distanceMeters,
                            isWarmup = false,
                            isComplete = true,
                            timestamp = workoutStartTime,
                            note = null,
                            supersetGroupId = null,
                            supersetOrderIndex = 0
                        )
                    )
                }

                if (pendingSets.isNotEmpty()) {
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

                    for (preparedSet in pendingSets) {
                        setDao.insertSet(preparedSet.copy(workoutId = workoutId))
                        setsImported++
                    }
                }
            }

            ImportResult(
                workoutsImported = workoutsImported,
                setsImported = setsImported,
                exercisesCreated = exercisesCreated,
                duplicatesSkipped = duplicatesSkipped,
                errors = errors
            )
        }
    }

    private fun parseCsvFields(parts: List<String>): CsvRow? {
        if (parts.size < 6) return null

        val date = parts.getOrNull(0)?.trim() ?: return null
        val exerciseName = parts.getOrNull(1)?.trim() ?: return null
        val category = parts.getOrNull(2)?.trim() ?: return null

        if (date.isEmpty() || exerciseName.isEmpty()) return null

        return CsvRow(
            date = date,
            exerciseName = exerciseName,
            category = category.ifEmpty { "Other" },
            weight = parts.getOrNull(3)?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() },
            weightUnit = parts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() },
            reps = parts.getOrNull(5)?.trim()?.toIntOrNull(),
            distance = parts.getOrNull(6)?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() },
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
        cache[name.lowercase()]?.let { return it }

        val existing = exerciseDao.getExerciseByName(name)
        if (existing != null) {
            cache[name.lowercase()] = existing.id
            return existing.id
        }

        val logType = determineLogType(row)
        val exercise = Exercise(
            name = name,
            targetMuscle = normalizeCategory(category),
            logType = logType,
            defaultDistanceUnit = parseDistanceUnit(row.distanceUnit),
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
            else -> LogType.WEIGHT_REPS
        }
    }

    private fun normalizeCategory(category: String): String {
        return when (category.lowercase()) {
            "chest" -> "Chest"
            "back" -> "Back"
            "biceps", "bicep" -> "Biceps"
            "triceps", "tricep" -> "Triceps"
            "shoulders", "shoulder", "delts" -> "Shoulders"
            "legs", "quads", "quadriceps", "hamstrings", "glutes", "calves" -> "Legs"
            "core", "abs", "abdominals" -> "Core"
            "cardio" -> "Cardio"
            "warmup", "warmups", "warm up", "warm-up" -> WARMUP_MUSCLE_GROUP
            else -> category.replaceFirstChar { it.uppercase() }
        }
    }

    private fun convertWeight(weight: Float?, unit: String?): Float? {
        if (weight == null || !weight.isFinite()) return null
        val converted = when (unit?.lowercase()) {
            "kg" -> weight * 2.20462f
            else -> weight
        }
        return converted.takeIf { it.isFinite() }
    }

    private fun convertDistance(distance: Float?, unit: String?): Float? {
        if (distance == null || !distance.isFinite()) return null
        return TimeUtils.distanceToMeters(distance, parseDistanceUnit(unit))?.takeIf { it.isFinite() }
    }

    private fun parseDistanceUnit(unit: String?): DistanceUnit {
        return when (unit?.lowercase()) {
            "km" -> DistanceUnit.KILOMETERS
            "mi", "mile", "miles" -> DistanceUnit.MILES
            "m", "meter", "meters" -> DistanceUnit.METERS
            "yd", "yard", "yards" -> DistanceUnit.YARDS
            "ft", "foot", "feet" -> DistanceUnit.FEET
            "steps", "step" -> DistanceUnit.STEPS
            "floors", "floor" -> DistanceUnit.FLOORS
            else -> DistanceUnit.MILES
        }
    }

    private fun parseTime(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        return TimeUtils.parseHMSToSeconds(time)
    }

    private fun normalizeFloatKey(value: Float?): Float? {
        if (value == null || !value.isFinite()) return null
        return if (value == 0f) 0f else value
    }

    private companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("uuuu-M-d", Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
