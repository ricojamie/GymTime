package com.example.gymtime.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao
) : ViewModel() {

    // --- State ---

    private val _selectedTimeRange = MutableStateFlow(TimeRange.TWELVE_WEEKS)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedMetric = MutableStateFlow(AnalyticsMetric.VOLUME)
    val selectedMetric: StateFlow<AnalyticsMetric> = _selectedMetric.asStateFlow()

    private val _selectedTarget = MutableStateFlow("Total")
    val selectedTarget: StateFlow<String> = _selectedTarget.asStateFlow()

    private val _availableTargets = MutableStateFlow<List<String>>(emptyList())
    val availableTargets: StateFlow<List<String>> = _availableTargets.asStateFlow()

    // Using ChartData wrapper for multi-line support
    private val _chartData = MutableStateFlow(ChartData())
    val chartData: StateFlow<ChartData> = _chartData.asStateFlow()

    private val _currentValue = MutableStateFlow("0")
    val currentValue: StateFlow<String> = _currentValue.asStateFlow()

    private val _maxValue = MutableStateFlow("0")
    val maxValue: StateFlow<String> = _maxValue.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var allExercises: List<Exercise> = emptyList()
    private val muscleGroups = listOf("Total", "Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                allExercises = exerciseDao.getAllExercises().first()
            } catch (e: Exception) {
                allExercises = emptyList()
            }

            updateAvailableTargets()
            refreshChart()

            _isLoading.value = false
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        refreshChart()
    }

    fun setMetric(metric: AnalyticsMetric) {
        _selectedMetric.value = metric
        
        if (metric == AnalyticsMetric.VOLUME) {
            _selectedTarget.value = "Total"
        } else {
            _selectedTarget.value = allExercises.firstOrNull()?.name ?: ""
        }
        
        updateAvailableTargets()
        refreshChart()
    }

    fun setTarget(target: String) {
        _selectedTarget.value = target
        refreshChart()
    }

    private fun updateAvailableTargets() {
        if (_selectedMetric.value == AnalyticsMetric.VOLUME) {
            _availableTargets.value = muscleGroups
        } else {
            _availableTargets.value = allExercises.map { it.name }
        }
    }

    private fun refreshChart() {
        viewModelScope.launch {
            _isLoading.value = true
            val (startDate, endDate) = selectedTimeRange.value.getDateRange()
            
            val actualPoints = if (_selectedMetric.value == AnalyticsMetric.VOLUME) {
                getVolumeChartData(startDate, endDate)
            } else {
                getE1RMChartData(startDate, endDate)
            }
            
            val trendPoints = calculateTrendLine(actualPoints)

            _chartData.value = ChartData(actuals = actualPoints, trend = trendPoints)
            calculateStats(actualPoints)
            
            _isLoading.value = false
        }
    }

    private suspend fun getVolumeChartData(startDate: Long, endDate: Long): List<ChartDataPoint> {
        val target = _selectedTarget.value
        val isTotal = target == "Total"

        val rawData = try {
            setDao.getVolumeByMuscleAndWeek(startDate, endDate)
        } catch (e: Exception) {
            emptyList()
        }

        val weekMap = mutableMapOf<String, Float>()
        
        rawData.forEach { item ->
            if (isTotal || item.muscle == target) {
                val current = weekMap.getOrDefault(item.week, 0f)
                weekMap[item.week] = current + item.volume
            }
        }

        return weekMap.keys.sorted().mapIndexed { index, key ->
            ChartDataPoint(
                date = index.toLong(),
                label = formatWeekLabel(key),
                value = weekMap[key] ?: 0f
            )
        }
    }

    private suspend fun getE1RMChartData(startDate: Long, endDate: Long): List<ChartDataPoint> {
        val targetExerciseName = _selectedTarget.value
        val exercise = allExercises.find { it.name == targetExerciseName } ?: return emptyList()

        val rawSets = try {
            setDao.getTopSetsForE1RM(startDate, endDate)
        } catch (e: Exception) {
            emptyList()
        }

        val filteredSets = rawSets.filter { it.exerciseId == exercise.id }
        val weekMap = mutableMapOf<String, Float>()
        
        filteredSets.forEach { set ->
            val weekKey = getWeekKey(set.timestamp)
            val e1rm = calculateEstimated1RM(set.weight ?: 0f, set.reps ?: 1)
            
            val currentMax = weekMap.getOrDefault(weekKey, 0f)
            if (e1rm > currentMax) {
                weekMap[weekKey] = e1rm
            }
        }

        return weekMap.keys.sorted().mapIndexed { index, key ->
            ChartDataPoint(
                date = index.toLong(),
                label = formatWeekLabel(key),
                value = weekMap[key] ?: 0f
            )
        }
    }
    
    private fun calculateTrendLine(points: List<ChartDataPoint>): List<ChartDataPoint> {
        if (points.size < 2) return emptyList()
        
        val n = points.size.toFloat()
        val sumX = points.map { it.date.toFloat() }.sum()
        val sumY = points.map { it.value }.sum()
        val sumXY = points.map { it.date.toFloat() * it.value }.sum()
        val sumXX = points.map { it.date.toFloat() * it.date.toFloat() }.sum()
        
        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0f) return emptyList()
        
        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n
        
        return points.map { point ->
            val trendValue = slope * point.date.toFloat() + intercept
            point.copy(value = trendValue.coerceAtLeast(0f))
        }
    }

    private fun calculateStats(points: List<ChartDataPoint>) {
        if (points.isEmpty()) {
            _currentValue.value = "0"
            _maxValue.value = "0"
            return
        }

        val values = points.map { it.value }
        val current = values.last()
        val max = values.maxOrNull() ?: 0f
        
        _currentValue.value = String.format("%,.0f", current)
        _maxValue.value = String.format("%,.0f", max)
    }

    private fun calculateEstimated1RM(weight: Float, reps: Int): Float {
        if (reps == 1) return weight
        if (reps > 12) return weight 
        return weight / (1.0278f - 0.0278f * reps)
    }
    
    private fun getWeekKey(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format("%d-%02d", year, week)
    }

    private fun formatWeekLabel(weekKey: String): String {
        try {
            val parts = weekKey.split("-")
            if (parts.size != 2) return weekKey
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            
            val cal = Calendar.getInstance()
            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.WEEK_OF_YEAR, week)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            
            val formatter = SimpleDateFormat("d MMM", Locale.getDefault())
            return formatter.format(cal.time)
        } catch (e: Exception) {
            return weekKey
        }
    }
}