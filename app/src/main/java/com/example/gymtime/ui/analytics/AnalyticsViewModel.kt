package com.example.gymtime.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val consistencyUseCase: com.example.gymtime.domain.analytics.ConsistencyUseCase,
    private val balanceUseCase: com.example.gymtime.domain.analytics.BalanceUseCase
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

            _isLoading.value = false
        }
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
}