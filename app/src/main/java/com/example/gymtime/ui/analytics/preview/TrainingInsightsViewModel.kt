package com.example.gymtime.ui.analytics.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.domain.analytics.TrainingInsightsDashboard
import com.example.gymtime.domain.analytics.TrainingInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TrainingInsightsUiState(
    val dashboard: TrainingInsightsDashboard? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val windowDays: Int = 28,
    val selectedExerciseId: Long? = null,
    val exerciseQuery: String = "",
    val muscleFilter: String? = null
)

private data class InsightLoadState(val dashboard: TrainingInsightsDashboard?, val loading: Boolean, val error: String? = null)
private data class InsightSelection(val exerciseId: Long?, val query: String, val muscle: String?)

/** The legacy gate has no data subscription. Room and projection work run only with visible content. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingInsightsViewModel @Inject constructor(
    private val insights: TrainingInsightsUseCase,
    preferences: UserPreferencesRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val newUiEnabled: StateFlow<Boolean?> = preferences.newUiEnabled.map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    private val windowDays = savedState.getStateFlow("insightWindowDays", 28)
        .map { if (it == 84) 84 else 28 }
    private val selectedExerciseId = savedState.getStateFlow<Long?>("insightExerciseId", null)
    private val query = savedState.getStateFlow("insightExerciseQuery", "")
    private val muscle = savedState.getStateFlow<String?>("insightMuscleFilter", null)
    private val refresh = MutableStateFlow(0)
    private var retainedDashboard: TrainingInsightsDashboard? = null

    private val dayChanges = flow {
        while (currentCoroutineContext().isActive) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone))
            val nextDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(Instant.now(), nextDay).toMillis().coerceAtLeast(1_000L) + 100L)
        }
    }

    private val loaded = refresh.flatMapLatest {
        combine(windowDays, insights.sourceChanges, dayChanges) { days, _, _ -> days }
            .transformLatest { days ->
                emit(InsightLoadState(retainedDashboard, loading = true))
                try {
                    val result = insights.load(windowDays = days)
                    retainedDashboard = result
                    emit(InsightLoadState(result, loading = false))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emit(InsightLoadState(retainedDashboard, loading = false,
                        error = "Couldn't refresh your training history. Your saved workouts are still there."))
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(InsightLoadState(retainedDashboard, loading = false,
                    error = "Couldn't read your training history. Try again."))
            }
    }

    private val selection = combine(selectedExerciseId, query, muscle) { id, text, group -> InsightSelection(id, text, group) }
    val uiState: StateFlow<TrainingInsightsUiState> = combine(loaded, selection, windowDays) { result, choices, days ->
        val exercises = result.dashboard?.exercises.orEmpty()
        val selected = exercises.firstOrNull { it.id == choices.exerciseId } ?: exercises.firstOrNull()
        TrainingInsightsUiState(result.dashboard, result.loading, result.error, days, selected?.id, choices.query, choices.muscle)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), TrainingInsightsUiState())

    fun selectWindow(days: Int) { savedState["insightWindowDays"] = if (days == 84) 84 else 28 }
    fun selectExercise(id: Long) { savedState["insightExerciseId"] = id }
    fun setExerciseQuery(text: String) { savedState["insightExerciseQuery"] = text }
    fun setMuscleFilter(name: String?) { savedState["insightMuscleFilter"] = name }
    fun refresh() { refresh.value += 1 }
}
