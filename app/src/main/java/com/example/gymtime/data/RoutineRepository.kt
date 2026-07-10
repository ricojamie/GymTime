package com.example.gymtime.data

import androidx.room.withTransaction
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.dao.WorkoutPlanDao
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutExerciseInstance
import com.example.gymtime.data.repository.WorkoutStartResult
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.example.gymtime.util.TimeFormatter
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveRoutineStatus(
    val routine: Routine,
    val nextDay: RoutineDay?,
    val dayCount: Int,
    val nextDayPosition: Int? = null // 1-based position of nextDay within the routine
)

sealed class RepeatWorkoutResult {
    data class Started(val result: WorkoutStartResult) : RepeatWorkoutResult()
    data object OngoingWorkoutExists : RepeatWorkoutResult()
    data object NothingToRepeat : RepeatWorkoutResult()
}

sealed class AddToRoutineResult {
    data class Added(val dayId: Long, val replaced: Boolean) : AddToRoutineResult()
    data object NothingToAdd : AddToRoutineResult()
    data object RoutineFull : AddToRoutineResult()
}

/** Neutral per-exercise plan row derived from a past workout. */
private data class WorkoutBlueprintItem(
    val exerciseId: Long,
    val routineExerciseId: Long? = null,
    val orderIndex: Int,
    val targetSets: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val restSeconds: Int?,
    val notes: String?,
    val sourceSupersetKey: String?,
    val supersetOrderIndex: Int
)

@Singleton
class RoutineRepository @Inject constructor(
    private val database: GymTimeDatabase,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val workoutPlanDao: WorkoutPlanDao,
    private val setDao: SetDao
) {
    companion object {
        const val MAX_ROUTINES = 10
        const val MAX_DAYS_PER_ROUTINE = 10
    }

    fun getAllRoutines(): Flow<List<Routine>> = routineDao.getAllRoutines()

    fun getRoutineById(id: Long): Flow<Routine?> = routineDao.getRoutineById(id)

    fun getDaysForRoutine(routineId: Long): Flow<List<RoutineDay>> =
        routineDao.getDaysForRoutine(routineId)

    fun getRoutineDayWithExercises(dayId: Long): Flow<RoutineDayWithExercises?> =
        routineDao.getRoutineDayWithExercises(dayId)

    fun getActiveRoutineStatus(): Flow<ActiveRoutineStatus?> {
        val activeRoutineFlow = routineDao.getActiveRoutine().distinctUntilChanged()
        return activeRoutineFlow.combine(
            activeRoutineFlow.flatMapLatest { routine ->
                if (routine == null) flowOf(emptyList()) else routineDao.getDaysForRoutine(routine.id)
            }
        ) { routine, days ->
            if (routine == null) {
                null
            } else {
                val nextDay = days.firstOrNull { it.orderIndex == routine.nextDayOrderIndex } ?: days.firstOrNull()
                ActiveRoutineStatus(
                    routine = routine,
                    nextDay = nextDay,
                    dayCount = days.size,
                    nextDayPosition = nextDay?.let { days.indexOf(it) + 1 }
                )
            }
        }
    }

    suspend fun insertRoutine(routine: Routine): Long = routineDao.insertRoutine(routine)

    suspend fun updateRoutine(routine: Routine) = routineDao.updateRoutine(routine)

    suspend fun deleteRoutine(routine: Routine) {
        database.withTransaction {
            routineDao.deleteRoutine(routine)
            val active = routineDao.getActiveRoutineSync()
            if (active == null) return@withTransaction
            val remainingDays = routineDao.getDaysForRoutineSync(active.id)
            val normalizedNext = remainingDays.firstOrNull()?.orderIndex ?: 0
            routineDao.updateNextDayOrderIndex(active.id, normalizedNext)
        }
    }

    suspend fun setActiveRoutine(routineId: Long?) {
        database.withTransaction {
            routineDao.clearActiveRoutine()
            if (routineId == null) return@withTransaction
            routineDao.setRoutineActive(routineId, true)
            val days = routineDao.getDaysForRoutineSync(routineId)
            val nextOrder = days.firstOrNull()?.orderIndex ?: 0
            routineDao.updateNextDayOrderIndex(routineId, nextOrder)
        }
    }

    suspend fun insertRoutineDay(day: RoutineDay): Long = routineDao.insertRoutineDay(day)

    suspend fun updateRoutineDay(day: RoutineDay) = routineDao.updateRoutineDay(day)

    suspend fun deleteRoutineDay(day: RoutineDay) {
        database.withTransaction {
            routineDao.deleteRoutineDay(day)
            val active = routineDao.getActiveRoutineSync()
            if (active?.id == day.routineId) {
                val days = routineDao.getDaysForRoutineSync(day.routineId)
                val next = days.firstOrNull { it.orderIndex >= active.nextDayOrderIndex } ?: days.firstOrNull()
                routineDao.updateNextDayOrderIndex(day.routineId, next?.orderIndex ?: 0)
            }
        }
    }

    suspend fun insertRoutineExercise(routineExercise: RoutineExercise) =
        routineDao.insertRoutineExercise(routineExercise)

    fun getExerciseListForDay(dayId: Long) = routineDao.getExerciseListForDay(dayId)

    suspend fun deleteRoutineExercise(routineExercise: RoutineExercise) =
        routineDao.deleteRoutineExercise(routineExercise)

    suspend fun deleteAllExercisesForDay(dayId: Long) =
        routineDao.deleteAllExercisesForDay(dayId)

    suspend fun startNextRoutineWorkout(): WorkoutStartResult? {
        return database.withTransaction {
            val active = routineDao.getActiveRoutineSync() ?: return@withTransaction null
            val days = routineDao.getDaysForRoutineSync(active.id)
            if (days.isEmpty()) return@withTransaction null

            val nextDay = days.firstOrNull { it.orderIndex == active.nextDayOrderIndex } ?: days.first()
            startRoutineDayInternal(active, nextDay)
        }
    }

    suspend fun startRoutineDay(dayId: Long): WorkoutStartResult? {
        return database.withTransaction {
            val dayWithExercises = routineDao.getRoutineDayWithExercisesSync(dayId) ?: return@withTransaction null
            val routine = routineDao.getRoutineByIdSync(dayWithExercises.day.routineId) ?: return@withTransaction null
            startRoutineDayInternal(routine, dayWithExercises.day, dayWithExercises)
        }
    }

    fun getDaysWithExercisesForRoutine(routineId: Long): Flow<List<RoutineDayWithExercises>> =
        routineDao.getDaysWithExercisesForRoutine(routineId)

    fun getRoutineDayStats(routineId: Long): Flow<Map<Long, RoutineDayStat>> =
        workoutDao.getRoutineDayStats(routineId).map { stats ->
            stats.associateBy { it.routineDayId }
        }

    /** Manually point the routine's "up next" marker at the given day. */
    suspend fun setNextDay(routineId: Long, dayId: Long) {
        database.withTransaction {
            val days = routineDao.getDaysForRoutineSync(routineId)
            val day = days.firstOrNull { it.id == dayId } ?: return@withTransaction
            routineDao.updateNextDayOrderIndex(routineId, day.orderIndex)
        }
    }

    /**
     * Swap a day with its neighbor (direction -1 = up, +1 = down), keeping the
     * "up next" pointer on the same day it pointed at before the move.
     */
    suspend fun moveDay(routineId: Long, dayId: Long, direction: Int) {
        database.withTransaction {
            val routine = routineDao.getRoutineByIdSync(routineId) ?: return@withTransaction
            val days = routineDao.getDaysForRoutineSync(routineId)
            val index = days.indexOfFirst { it.id == dayId }
            val targetIndex = index + direction
            if (index == -1 || targetIndex !in days.indices) return@withTransaction

            val day = days[index]
            val neighbor = days[targetIndex]
            val nextDayId = days.firstOrNull { it.orderIndex == routine.nextDayOrderIndex }?.id

            routineDao.updateDayOrderIndex(day.id, neighbor.orderIndex)
            routineDao.updateDayOrderIndex(neighbor.id, day.orderIndex)

            // Keep "up next" pinned to the same day after the swap.
            when (nextDayId) {
                day.id -> routineDao.updateNextDayOrderIndex(routineId, neighbor.orderIndex)
                neighbor.id -> routineDao.updateNextDayOrderIndex(routineId, day.orderIndex)
            }
        }
    }

    /** Copy a routine with all days and exercises. Returns the new routine id. */
    suspend fun duplicateRoutine(routineId: Long): Long? {
        return database.withTransaction {
            val routine = routineDao.getRoutineByIdSync(routineId) ?: return@withTransaction null
            val days = routineDao.getDaysForRoutineSync(routineId)
            val newRoutineId = routineDao.insertRoutine(
                Routine(name = "${routine.name} (copy)", isActive = false, nextDayOrderIndex = 0)
            )
            days.forEach { day ->
                copyDayInternal(day, newRoutineId, day.name, day.orderIndex)
            }
            newRoutineId
        }
    }

    /** Copy a day (with exercises) to the end of the same routine. Returns the new day id. */
    suspend fun duplicateDay(dayId: Long): Long? {
        return database.withTransaction {
            val template = routineDao.getRoutineDayWithExercisesSync(dayId) ?: return@withTransaction null
            val days = routineDao.getDaysForRoutineSync(template.day.routineId)
            val nextOrderIndex = (days.maxOfOrNull { it.orderIndex } ?: -1) + 1
            copyDayInternal(template.day, template.day.routineId, "${template.day.name} (copy)", nextOrderIndex)
        }
    }

    private suspend fun copyDayInternal(
        sourceDay: RoutineDay,
        targetRoutineId: Long,
        newName: String,
        newOrderIndex: Int
    ): Long? {
        val template = routineDao.getRoutineDayWithExercisesSync(sourceDay.id) ?: return null
        val newDayId = routineDao.insertRoutineDay(
            RoutineDay(routineId = targetRoutineId, name = newName, orderIndex = newOrderIndex)
        )
        val groupIdMap = mutableMapOf<String, String>()
        routineDao.insertRoutineExercises(
            template.exercises.sortedBy { it.routineExercise.orderIndex }.map { item ->
                val re = item.routineExercise
                re.copy(
                    id = 0,
                    routineDayId = newDayId,
                    supersetGroupId = remapSupersetKey(re.supersetGroupId, groupIdMap)
                )
            }
        )
        return newDayId
    }

    /**
     * Regenerates a superset group id so the copy is independent of the source;
     * sets sharing a source key keep sharing the regenerated one.
     */
    private fun remapSupersetKey(sourceKey: String?, groupIdMap: MutableMap<String, String>): String? =
        sourceKey?.let { groupIdMap.getOrPut(it) { UUID.randomUUID().toString() } }

    /** Inserts the common Workout + plan-instance scaffold used by every plan-backed start. */
    private suspend fun createPlanBackedWorkout(
        name: String?,
        routineDayId: Long?,
        routineId: Long?,
        routineNameSnapshot: String?,
        routineDayNameSnapshot: String?,
        planItems: List<WorkoutBlueprintItem>,
        regenerateSupersetIds: Boolean
    ): WorkoutStartResult {
        require(planItems.isNotEmpty()) { "A plan-backed workout requires at least one exercise" }

        val workoutId = workoutDao.insertWorkout(
            Workout(
                startTime = Date(),
                endTime = null,
                name = name,
                note = null,
                routineDayId = routineDayId,
                routineId = routineId,
                routineNameSnapshot = routineNameSnapshot,
                routineDayNameSnapshot = routineDayNameSnapshot,
                startedFromRoutine = true
            )
        )

        val groupIdMap = mutableMapOf<String, String>()
        workoutPlanDao.insertInstances(
            planItems.map { item ->
                WorkoutExerciseInstance(
                    workoutId = workoutId,
                    exerciseId = item.exerciseId,
                    routineExerciseId = item.routineExerciseId,
                    orderIndex = item.orderIndex,
                    plannedSets = item.targetSets,
                    repMin = item.repMin,
                    repMax = item.repMax,
                    // Null deliberately means "inherit Exercise.defaultRestSeconds"
                    // when the plan is queried for display.
                    restSeconds = item.restSeconds,
                    notes = item.notes,
                    supersetGroupId = if (regenerateSupersetIds) {
                        remapSupersetKey(item.sourceSupersetKey, groupIdMap)
                    } else {
                        item.sourceSupersetKey
                    },
                    supersetOrderIndex = item.supersetOrderIndex
                )
            }
        )

        return WorkoutStartResult(
            workoutId = workoutId,
            firstExerciseId = planItems.first().exerciseId
        )
    }

    private suspend fun startRoutineDayInternal(
        routine: Routine,
        day: RoutineDay,
        dayWithExercises: RoutineDayWithExercises? = null
    ): WorkoutStartResult? {
        val template = dayWithExercises ?: routineDao.getRoutineDayWithExercisesSync(day.id) ?: return null
        val exercises = template.exercises.sortedBy { it.routineExercise.orderIndex }
        if (exercises.isEmpty()) return null

        val planItems = exercises.map { templateExercise ->
            WorkoutBlueprintItem(
                exerciseId = templateExercise.exercise.id,
                routineExerciseId = templateExercise.routineExercise.id,
                orderIndex = templateExercise.routineExercise.orderIndex,
                targetSets = templateExercise.routineExercise.targetSets,
                repMin = templateExercise.routineExercise.targetRepsMin,
                repMax = templateExercise.routineExercise.targetRepsMax,
                // Keep inherited defaults nullable so later exercise-default edits
                // resolve consistently instead of being frozen into this snapshot.
                restSeconds = templateExercise.routineExercise.targetRestSeconds,
                notes = templateExercise.routineExercise.notes,
                sourceSupersetKey = templateExercise.routineExercise.supersetGroupId,
                supersetOrderIndex = templateExercise.routineExercise.supersetOrderIndex
            )
        }

        return createPlanBackedWorkout(
            name = day.name,
            routineDayId = day.id,
            routineId = routine.id,
            routineNameSnapshot = routine.name,
            routineDayNameSnapshot = day.name,
            planItems = planItems,
            regenerateSupersetIds = false
        )
    }

    /**
     * Turns a past workout into a neutral plan description. Prefers the workout's
     * plan instances (routine-started workouts); otherwise derives targets from the
     * logged sets.
     */
    private suspend fun buildBlueprintFromWorkout(workoutId: Long): List<WorkoutBlueprintItem> {
        val instances = workoutPlanDao.getInstancesForWorkoutSync(workoutId)
        if (instances.isNotEmpty()) {
            return instances.map { instance ->
                WorkoutBlueprintItem(
                    exerciseId = instance.exerciseId,
                    orderIndex = instance.orderIndex,
                    targetSets = instance.plannedSets,
                    repMin = instance.repMin,
                    repMax = instance.repMax,
                    restSeconds = instance.restSeconds,
                    notes = instance.notes,
                    sourceSupersetKey = instance.supersetGroupId,
                    supersetOrderIndex = instance.supersetOrderIndex
                )
            }
        }

        // Ad-hoc workout: derive targets from what was actually logged.
        // getWorkoutSetsWithExercises orders by timestamp ASC, so groupBy keeps
        // exercises in the order they were first performed.
        val sets = setDao.getWorkoutSetsWithExercises(workoutId)
        return sets.groupBy { it.set.exerciseId }.entries.mapIndexed { index, (exerciseId, exerciseSets) ->
            val workingSets = exerciseSets.filter { !it.set.isWarmup }
            val workingReps = workingSets.mapNotNull { it.set.reps }
            val firstSet = exerciseSets.first().set
            WorkoutBlueprintItem(
                exerciseId = exerciseId,
                orderIndex = index,
                targetSets = workingSets.size.coerceAtLeast(1),
                repMin = workingReps.minOrNull(),
                repMax = workingReps.maxOrNull(),
                restSeconds = null,
                notes = null,
                sourceSupersetKey = firstSet.supersetGroupId,
                supersetOrderIndex = firstSet.supersetOrderIndex
            )
        }
    }

    /**
     * Starts a fresh workout pre-loaded with the same exercises and targets as a
     * past workout. Nothing is saved to any routine.
     */
    suspend fun repeatWorkout(sourceWorkoutId: Long): RepeatWorkoutResult {
        return database.withTransaction {
            if (workoutDao.getOngoingWorkout().first() != null) {
                return@withTransaction RepeatWorkoutResult.OngoingWorkoutExists
            }
            val source = workoutDao.getWorkoutByIdSync(sourceWorkoutId)
                ?: return@withTransaction RepeatWorkoutResult.NothingToRepeat
            val blueprint = buildBlueprintFromWorkout(sourceWorkoutId)
            if (blueprint.isEmpty()) return@withTransaction RepeatWorkoutResult.NothingToRepeat

            RepeatWorkoutResult.Started(
                createPlanBackedWorkout(
                    name = source.name,
                    // No routine linkage: a repeat must not advance the routine's
                    // "up next" pointer or count in routine stats. Snapshots remain
                    // available for display, and the shared helper keeps it plan-backed.
                    routineDayId = null,
                    routineId = null,
                    routineNameSnapshot = source.routineNameSnapshot,
                    routineDayNameSnapshot = source.routineDayNameSnapshot,
                    planItems = blueprint,
                    regenerateSupersetIds = true
                )
            )
        }
    }

    /**
     * Writes a past workout into a routine, either as a new day appended at the end
     * or by replacing the exercises of an existing day (the day row itself — id,
     * name, orderIndex — is preserved so the routine's "up next" pointer and past
     * workout references stay valid).
     */
    suspend fun createRoutineDayFromWorkout(
        workoutId: Long,
        routineId: Long,
        replaceDayId: Long? = null
    ): AddToRoutineResult {
        return database.withTransaction {
            val blueprint = buildBlueprintFromWorkout(workoutId)
            if (blueprint.isEmpty()) return@withTransaction AddToRoutineResult.NothingToAdd

            val targetDayId: Long
            val replaced: Boolean
            if (replaceDayId != null) {
                val days = routineDao.getDaysForRoutineSync(routineId)
                if (days.none { it.id == replaceDayId }) {
                    return@withTransaction AddToRoutineResult.NothingToAdd
                }
                routineDao.deleteAllExercisesForDay(replaceDayId)
                targetDayId = replaceDayId
                replaced = true
            } else {
                val days = routineDao.getDaysForRoutineSync(routineId)
                if (days.size >= MAX_DAYS_PER_ROUTINE) {
                    return@withTransaction AddToRoutineResult.RoutineFull
                }
                val workout = workoutDao.getWorkoutByIdSync(workoutId)
                val dayName = workout?.routineDayNameSnapshot
                    ?: workout?.name
                    ?: "Workout ${TimeFormatter.formatShortDate(workout?.startTime ?: Date())}"
                targetDayId = routineDao.insertRoutineDay(
                    RoutineDay(
                        routineId = routineId,
                        name = dayName,
                        orderIndex = (days.maxOfOrNull { it.orderIndex } ?: -1) + 1
                    )
                )
                replaced = false
            }

            val groupIdMap = mutableMapOf<String, String>()
            routineDao.insertRoutineExercises(
                blueprint.map { item ->
                    RoutineExercise(
                        routineDayId = targetDayId,
                        exerciseId = item.exerciseId,
                        orderIndex = item.orderIndex,
                        targetSets = item.targetSets ?: 3,
                        targetRepsMin = item.repMin,
                        targetRepsMax = item.repMax,
                        targetRestSeconds = item.restSeconds,
                        notes = item.notes,
                        supersetGroupId = remapSupersetKey(item.sourceSupersetKey, groupIdMap),
                        supersetOrderIndex = item.supersetOrderIndex
                    )
                }
            )

            AddToRoutineResult.Added(dayId = targetDayId, replaced = replaced)
        }
    }
}
