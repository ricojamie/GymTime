package com.example.gymtime.data

import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao
) {
    fun getAllRoutines(): Flow<List<Routine>> = routineDao.getAllRoutines()

    fun getActiveRoutines(): Flow<List<Routine>> = routineDao.getActiveRoutines()

    fun getRoutineById(id: Long): Flow<Routine?> = routineDao.getRoutineById(id)

    fun getDaysForRoutine(routineId: Long): Flow<List<RoutineDay>> = 
        routineDao.getDaysForRoutine(routineId)

    fun getRoutineDayWithExercises(dayId: Long): Flow<RoutineDayWithExercises?> =
        routineDao.getRoutineDayWithExercises(dayId)

    suspend fun insertRoutine(routine: Routine): Long = routineDao.insertRoutine(routine)

    suspend fun updateRoutine(routine: Routine) = routineDao.updateRoutine(routine)

    suspend fun deleteRoutine(routine: Routine) = routineDao.deleteRoutine(routine)

    suspend fun insertRoutineDay(day: RoutineDay): Long = routineDao.insertRoutineDay(day)

    suspend fun updateRoutineDay(day: RoutineDay) = routineDao.updateRoutineDay(day)

    suspend fun deleteRoutineDay(day: RoutineDay) = routineDao.deleteRoutineDay(day)

    suspend fun insertRoutineExercise(routineExercise: RoutineExercise) =
        routineDao.insertRoutineExercise(routineExercise)

    fun getExerciseListForDay(dayId: Long) = routineDao.getExerciseListForDay(dayId)

    suspend fun deleteRoutineExercise(routineExercise: RoutineExercise) =
        routineDao.deleteRoutineExercise(routineExercise)

    suspend fun deleteAllExercisesForDay(dayId: Long) =
        routineDao.deleteAllExercisesForDay(dayId)
}
