package com.example.gymtime.ui.routine.preview

import com.example.gymtime.data.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** One single-query local projection for both browse entry points. No database calls happen in composition. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun routineBrowseState(repository: RoutineRepository, retry: Flow<Int>): Flow<RoutineBrowseUiState> =
    retry.flatMapLatest {
        repository.getRoutineBrowseSummaries()
            .map { summaries ->
                RoutineBrowseUiState(
                    routines = summaries.map { summary ->
                        RoutineBrowseItem(
                            id = summary.id,
                            name = summary.name,
                            isActive = summary.isActive,
                            dayCount = summary.dayCount,
                            exerciseCount = summary.exerciseCount,
                            nextDayName = summary.nextDayName
                        )
                    },
                    isLoading = false
                )
            }
            .onStart { emit(RoutineBrowseUiState()) }
            .catch { emit(RoutineBrowseUiState(isLoading = false, loadError = "Couldn't load routines. Try again.")) }
    }
