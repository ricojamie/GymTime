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
import com.example.gymtime.data.db.dao.WorkoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveRoutineStatus(
    val routine: Routine,
    val nextDay: RoutineDay?,
    val dayCount: Int,
    val nextDayPosition: Int? = null // 1-based position of nextDay within the routine
)

@Singleton
class RoutineRepository @Inject constructor(
    private val database: GymTimeDatabase,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val workoutPlanDao: WorkoutPlanDao
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
        // Regenerate superset group ids so the copy is independent of the original.
        val groupIdMap = mutableMapOf<String, String>()
        template.exercises.sortedBy { it.routineExercise.orderIndex }.forEach { item ->
            val re = item.routineExercise
            val newGroupId = re.supersetGroupId?.let { old ->
                groupIdMap.getOrPut(old) { java.util.UUID.randomUUID().toString() }
            }
            routineDao.insertRoutineExercise(
                re.copy(id = 0, routineDayId = newDayId, supersetGroupId = newGroupId)
            )
        }
        return newDayId
    }

    private suspend fun startRoutineDayInternal(
        routine: Routine,
        day: RoutineDay,
        dayWithExercises: RoutineDayWithExercises? = null
    ): WorkoutStartResult? {
        val template = dayWithExercises ?: routineDao.getRoutineDayWithExercisesSync(day.id) ?: return null
        val exercises = template.exercises.sortedBy { it.routineExercise.orderIndex }
        if (exercises.isEmpty()) return null

        val workoutId = workoutDao.insertWorkout(
            Workout(
                startTime = Date(),
                endTime = null,
                name = day.name,
                note = null,
                routineDayId = day.id,
                routineId = routine.id,
                routineNameSnapshot = routine.name,
                routineDayNameSnapshot = day.name,
                startedFromRoutine = true
            )
        )

        workoutPlanDao.insertInstances(
            exercises.map { templateExercise ->
                WorkoutExerciseInstance(
                    workoutId = workoutId,
                    exerciseId = templateExercise.exercise.id,
                    routineExerciseId = templateExercise.routineExercise.id,
                    orderIndex = templateExercise.routineExercise.orderIndex,
                    plannedSets = templateExercise.routineExercise.targetSets,
                    repMin = templateExercise.routineExercise.targetRepsMin,
                    repMax = templateExercise.routineExercise.targetRepsMax,
                    restSeconds = templateExercise.routineExercise.targetRestSeconds
                        ?: templateExercise.exercise.defaultRestSeconds,
                    notes = templateExercise.routineExercise.notes,
                    supersetGroupId = templateExercise.routineExercise.supersetGroupId,
                    supersetOrderIndex = templateExercise.routineExercise.supersetOrderIndex
                )
            }
        )

        val firstExerciseId = exercises.first().exercise.id
        return WorkoutStartResult(workoutId = workoutId, firstExerciseId = firstExerciseId)
    }
}
