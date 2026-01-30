package com.example.gymtime.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
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
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: com.example.gymtime.data.db.dao.MuscleGroupDao,
    private val consistencyUseCase: com.example.gymtime.domain.analytics.ConsistencyUseCase,
    private val balanceUseCase: com.example.gymtime.domain.analytics.BalanceUseCase,
    private val trendUseCase: com.example.gymtime.domain.analytics.TrendUseCase
) : ViewModel() {

    // --- State ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _heatMapData = MutableStateFlow<List<com.example.gymtime.domain.analytics.HeatMapDay>>(emptyList())
    val heatMapData: StateFlow<List<com.example.gymtime.domain.analytics.HeatMapDay>> = _heatMapData.asStateFlow()

    private val _consistencyStats = MutableStateFlow<com.example.gymtime.domain.analytics.ConsistencyStats?>(null)
    val consistencyStats: StateFlow<com.example.gymtime.domain.analytics.ConsistencyStats?> = _consistencyStats.asStateFlow()

    private val _muscleDistribution = MutableStateFlow<List<com.example.gymtime.data.db.entity.MuscleDistribution>>(emptyList())
    val muscleDistribution: StateFlow<List<com.example.gymtime.data.db.entity.MuscleDistribution>> = _muscleDistribution.asStateFlow()

    private val _muscleFreshness = MutableStateFlow<List<com.example.gymtime.domain.analytics.MuscleFreshnessStatus>>(emptyList())
    val muscleFreshness: StateFlow<List<com.example.gymtime.domain.analytics.MuscleFreshnessStatus>> = _muscleFreshness.asStateFlow()

    // --- Trend State ---
    
    private val _selectedMetric = MutableStateFlow(com.example.gymtime.domain.analytics.TrendMetric.VOLUME)
    val selectedMetric = _selectedMetric.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(com.example.gymtime.domain.analytics.TimePeriod.THREE_MONTHS)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _selectedInterval = MutableStateFlow(com.example.gymtime.domain.analytics.AggregateInterval.WEEKLY)
    val selectedInterval = _selectedInterval.asStateFlow()

    private val _selectedMuscleFilter = MutableStateFlow<String?>("All")
    val selectedMuscleFilter = _selectedMuscleFilter.asStateFlow()

    private val _selectedExerciseFilterId = MutableStateFlow<Long?>(null)
    val selectedExerciseFilterId = _selectedExerciseFilterId.asStateFlow()

    private val _trendData = MutableStateFlow<List<com.example.gymtime.domain.analytics.TrendPoint>>(emptyList())
    val trendData = _trendData.asStateFlow()

    private val _trophyCasePRs = MutableStateFlow<List<com.example.gymtime.domain.analytics.TrophyPR>>(emptyList())
    val trophyCasePRs = _trophyCasePRs.asStateFlow()

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

            _isLoading.value = false
        }
    }

    fun updateMetric(metric: com.example.gymtime.domain.analytics.TrendMetric) {
        _selectedMetric.value = metric
        refreshTrendData()
    }

    fun updatePeriod(period: com.example.gymtime.domain.analytics.TimePeriod) {
        _selectedPeriod.value = period
        refreshTrendData()
    }

    fun updateInterval(interval: com.example.gymtime.domain.analytics.AggregateInterval) {
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
                _muscleDistribution.value = balanceUseCase.getMuscleDistribution()
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
}
