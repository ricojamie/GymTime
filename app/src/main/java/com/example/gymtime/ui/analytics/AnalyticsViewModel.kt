package com.example.gymtime.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.domain.analytics.AggregateInterval
import com.example.gymtime.domain.analytics.BalanceTimeRange
import com.example.gymtime.domain.analytics.BalanceUseCase
import com.example.gymtime.domain.analytics.ConsistencyStats
import com.example.gymtime.domain.analytics.ConsistencyUseCase
import com.example.gymtime.domain.analytics.HeatMapDay
import com.example.gymtime.domain.analytics.MuscleFreshnessStatus
import com.example.gymtime.domain.analytics.TimePeriod
import com.example.gymtime.domain.analytics.TrendMetric
import com.example.gymtime.domain.analytics.TrendPoint
import com.example.gymtime.domain.analytics.TrendUseCase
import com.example.gymtime.domain.analytics.TrophyPR
import com.example.gymtime.domain.analytics.WorkoutRatingStats
import com.example.gymtime.domain.analytics.WorkoutRatingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val consistencyUseCase: ConsistencyUseCase,
    private val balanceUseCase: BalanceUseCase,
    private val trendUseCase: TrendUseCase,
    private val workoutRatingUseCase: WorkoutRatingUseCase
) : ViewModel() {

    // --- State ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _heatMapData = MutableStateFlow<List<HeatMapDay>>(emptyList())
    val heatMapData: StateFlow<List<HeatMapDay>> = _heatMapData.asStateFlow()

    private val _consistencyStats = MutableStateFlow<ConsistencyStats?>(null)
    val consistencyStats: StateFlow<ConsistencyStats?> = _consistencyStats.asStateFlow()

    private val _muscleDistribution = MutableStateFlow<List<MuscleDistribution>>(emptyList())
    val muscleDistribution: StateFlow<List<MuscleDistribution>> = _muscleDistribution.asStateFlow()

    private val _radarDistribution = MutableStateFlow<List<MuscleDistribution>>(emptyList())
    val radarDistribution: StateFlow<List<MuscleDistribution>> = _radarDistribution.asStateFlow()

    private val _selectedBalanceRange = MutableStateFlow(BalanceTimeRange.THIRTY_DAYS)
    val selectedBalanceRange = _selectedBalanceRange.asStateFlow()

    private val _muscleFreshness = MutableStateFlow<List<MuscleFreshnessStatus>>(emptyList())
    val muscleFreshness: StateFlow<List<MuscleFreshnessStatus>> = _muscleFreshness.asStateFlow()

    // --- Trend State ---
    
    private val _selectedMetric = MutableStateFlow(TrendMetric.VOLUME)
    val selectedMetric = _selectedMetric.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TimePeriod.THREE_MONTHS)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _selectedInterval = MutableStateFlow(AggregateInterval.WEEKLY)
    val selectedInterval = _selectedInterval.asStateFlow()

    private val _selectedMuscleFilter = MutableStateFlow<String?>("All")
    val selectedMuscleFilter = _selectedMuscleFilter.asStateFlow()

    private val _selectedExerciseFilterId = MutableStateFlow<Long?>(null)
    val selectedExerciseFilterId = _selectedExerciseFilterId.asStateFlow()

    private val _trendData = MutableStateFlow<List<TrendPoint>>(emptyList())
    val trendData = _trendData.asStateFlow()

    private val _trophyCasePRs = MutableStateFlow<List<TrophyPR>>(emptyList())
    val trophyCasePRs = _trophyCasePRs.asStateFlow()

    private val _workoutRatingStats = MutableStateFlow<WorkoutRatingStats?>(null)
    val workoutRatingStats = _workoutRatingStats.asStateFlow()

    val allExercises = exerciseDao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMuscleGroups = muscleGroupDao.getAllMuscleGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshData()
    }

    /**
     * Call this to refresh data when navigating back to the analytics screen
     */
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            refreshHeatMap()
            refreshBalanceData()
            refreshTrendData()
            refreshTrophyCase()
            refreshWorkoutRatings()

            _isLoading.value = false
        }
    }

    fun updateMetric(metric: TrendMetric) {
        _selectedMetric.value = metric
        refreshTrendData()
    }

    fun updatePeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        refreshTrendData()
    }

    fun updateInterval(interval: AggregateInterval) {
        _selectedInterval.value = interval
        refreshTrendData()
    }

    fun updateMuscleFilter(muscle: String?) {
        _selectedMuscleFilter.value = muscle
        _selectedExerciseFilterId.value = null // Clear exercise if muscle changes
        refreshTrendData()
    }

    fun updateExerciseFilter(exerciseId: Long?) {
        _selectedExerciseFilterId.value = exerciseId
        if (exerciseId != null) {
            _selectedMuscleFilter.value = null
        }
        refreshTrendData()
    }

    fun updateBalanceRange(range: BalanceTimeRange) {
        _selectedBalanceRange.value = range
        refreshBalanceData()
    }

    private fun refreshHeatMap() {
        viewModelScope.launch {
            try {
                _heatMapData.value = consistencyUseCase.getHeatMapData()
                _consistencyStats.value = consistencyUseCase.getConsistencyStats()
            } catch (e: Exception) {
                // Handle error or leave empty
                _heatMapData.value = emptyList()
            }
        }
    }

    private fun refreshBalanceData() {
        viewModelScope.launch {
            try {
                _muscleDistribution.value = balanceUseCase.getMuscleDistribution(_selectedBalanceRange.value)
                _radarDistribution.value = balanceUseCase.getRadarDistribution(_selectedBalanceRange.value)
                _muscleFreshness.value = balanceUseCase.getMuscleFreshness()
            } catch (e: Exception) {
                 // Ignore for now
            }
        }
    }

    private fun refreshTrendData() {
        viewModelScope.launch {
            try {
                _trendData.value = trendUseCase.getTrendData(
                    metric = _selectedMetric.value,
                    period = _selectedPeriod.value,
                    interval = _selectedInterval.value,
                    muscleGroup = _selectedMuscleFilter.value,
                    exerciseId = _selectedExerciseFilterId.value
                )
            } catch (e: Exception) {
                _trendData.value = emptyList()
            }
        }
    }

    private fun refreshTrophyCase() {
        viewModelScope.launch {
            try {
                _trophyCasePRs.value = trendUseCase.getTrophyCasePRs()
            } catch (e: Exception) {
                _trophyCasePRs.value = emptyList()
            }
        }
    }

    private fun refreshWorkoutRatings() {
        viewModelScope.launch {
            try {
                _workoutRatingStats.value = workoutRatingUseCase.getRatingStats()
            } catch (e: Exception) {
                _workoutRatingStats.value = null
            }
        }
    }
}
