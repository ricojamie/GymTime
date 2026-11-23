package com.example.gymtime.ui.analytics.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.domain.analytics.TrainingInsightsDashboard
import com.example.gymtime.domain.analytics.TrainingInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import java.time.Clock
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

private data class InsightLoadState(
    val dashboard: TrainingInsightsDashboard?,
    val loading: Boolean,
    val error: String? = null
)

private data class InsightSelection(
    val exerciseId: Long?,
    val query: String,
    val muscle: String?
)

/** Room and projection work run only while visible content collects [uiState]. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingInsightsViewModel internal constructor(
    private val insights: TrainingInsightsUseCase,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId
) : ViewModel() {

    @Inject
    constructor(
        insights: TrainingInsightsUseCase,
        savedState: SavedStateHandle
    ) : this(
        insights = insights,
        savedState = savedState,
        clock = Clock.systemDefaultZone(),
        zoneProvider = { ZoneId.systemDefault() }
    )

    private val windowDays = savedState.getStateFlow("insightWindowDays", 28)
        .map { if (it == 84) 84 else 28 }
    private val selectedExerciseId = savedState.getStateFlow<Long?>("insightExerciseId", null)
    private val query = savedState.getStateFlow("insightExerciseQuery", "")
    private val muscle = savedState.getStateFlow<String?>("insightMuscleFilter", null)
    private val refresh = MutableStateFlow(0)
    private var retainedDashboard: TrainingInsightsDashboard? = null
    private var lastLoadedDate: LocalDate? = null
    private var lastLoadedZone: ZoneId? = null

    private val dayChanges = flow {
        while (currentCoroutineContext().isActive) {
            val zone = zoneProvider()
            val zonedNow = Instant.ofEpochMilli(clock.millis()).atZone(zone)
            emit(zonedNow.toLocalDate())
            val nextDay = zonedNow.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(zonedNow.toInstant(), nextDay).toMillis().coerceAtLeast(1_000L) + 100L)
        }
    }

    private val loaded = refresh.flatMapLatest {
        combine(windowDays, insights.sourceChanges, dayChanges) { days, _, _ -> days }
            .transformLatest { days ->
                val zone = zoneProvider()
                val instant = clock.instant()
                lastLoadedDate = instant.atZone(zone).toLocalDate()
                lastLoadedZone = zone
                emit(InsightLoadState(retainedDashboard, loading = true))
                try {
                    val result = insights.load(windowDays = days, now = instant, zone = zone)
                    retainedDashboard = result
                    emit(InsightLoadState(result, loading = false))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emit(
                        InsightLoadState(
                            dashboard = retainedDashboard,
                            loading = false,
                            error = "Couldn't refresh your training history. Your saved workouts are still there."
                        )
                    )
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(
                    InsightLoadState(
                        dashboard = retainedDashboard,
                        loading = false,
                        error = "Couldn't read your training history. Try again."
                    )
                )
            }
    }

    private val selection = combine(selectedExerciseId, query, muscle) { id, text, group ->
        InsightSelection(id, text, group)
    }

    val uiState: StateFlow<TrainingInsightsUiState> = combine(loaded, selection, windowDays) { result, choices, days ->
        val exercises = result.dashboard?.exercises.orEmpty()
        val selected = exercises.firstOrNull { it.id == choices.exerciseId } ?: exercises.firstOrNull()
        TrainingInsightsUiState(
            dashboard = result.dashboard,
            isLoading = result.loading,
            error = result.error,
            windowDays = days,
            selectedExerciseId = selected?.id,
            exerciseQuery = choices.query,
            muscleFilter = choices.muscle
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), TrainingInsightsUiState())

    fun selectWindow(days: Int) { savedState["insightWindowDays"] = if (days == 84) 84 else 28 }
    fun selectExercise(id: Long) { savedState["insightExerciseId"] = id }
    fun setExerciseQuery(text: String) { savedState["insightExerciseQuery"] = text }
    fun setMuscleFilter(name: String?) { savedState["insightMuscleFilter"] = name }

    /**
     * Avoids cancelling and restarting the initial ON_START collection on first ON_RESUME,
     * refreshing only if the calendar date or device timezone changed while inactive.
     */
    fun refreshOnResume() {
        val prevDate = lastLoadedDate ?: return
        val prevZone = lastLoadedZone ?: return
        val currentZone = zoneProvider()
        val currentDate = clock.instant().atZone(currentZone).toLocalDate()
        if (prevDate != currentDate || prevZone != currentZone) {
            refresh()
        }
    }

    fun refresh() { refresh.value += 1 }
}
