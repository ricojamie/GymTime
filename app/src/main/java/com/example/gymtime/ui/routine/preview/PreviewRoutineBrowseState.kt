package com.example.gymtime.ui.routine.preview

import com.example.gymtime.data.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** One local-data projection for both browse entry points. No database calls happen in composition. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun routineBrowseState(repository: RoutineRepository, retry: Flow<Int>): Flow<RoutineBrowseUiState> =
    retry.flatMapLatest {
        repository.getAllRoutines().flatMapLatest { routines ->
            if (routines.isEmpty()) flowOf(RoutineBrowseUiState(isLoading = false))
            else combine(routines.map { routine ->
                repository.getDaysWithExercisesForRoutine(routine.id).map { days ->
                    val next = days.firstOrNull { it.day.orderIndex == routine.nextDayOrderIndex } ?: days.firstOrNull()
                    RoutineBrowseItem(routine.id, routine.name, routine.isActive, days.size,
                        days.sumOf { it.exercises.size }, next?.day?.name)
                }
            }) { entries -> RoutineBrowseUiState(routines = entries.toList(), isLoading = false) }
        }.onStart { emit(RoutineBrowseUiState()) }
            .catch { emit(RoutineBrowseUiState(isLoading = false, loadError = "Couldn't load routines. Try again.")) }
    }
