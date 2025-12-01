package com.example.gymtime.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.PersonalRecordData
import com.example.gymtime.data.db.dao.SetDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao
) : ViewModel() {

    // Selected time range
    private val _selectedTimeRange = MutableStateFlow(TimeRange.TWELVE_WEEKS)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    // Hero card data
    private val _frequencyData = MutableStateFlow<FrequencyData?>(null)
    val frequencyData: StateFlow<FrequencyData?> = _frequencyData.asStateFlow()

    private val _volumeData = MutableStateFlow<VolumeData?>(null)
    val volumeData: StateFlow<VolumeData?> = _volumeData.asStateFlow()

    private val _bestE1RMData = MutableStateFlow<BestE1RMData?>(null)
    val bestE1RMData: StateFlow<BestE1RMData?> = _bestE1RMData.asStateFlow()

    // Personal records
    private val _personalRecords = MutableStateFlow<List<PersonalRecordData>>(emptyList())
    val personalRecords: StateFlow<List<PersonalRecordData>> = _personalRecords.asStateFlow()

    // Volume by muscle
    private val _volumeByMuscle = MutableStateFlow<Map<String, List<VolumePoint>>>(emptyMap())
    val volumeByMuscle: StateFlow<Map<String, List<VolumePoint>>> = _volumeByMuscle.asStateFlow()

    // Selected muscles for volume chart
    private val _selectedMuscles = MutableStateFlow(
        setOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")
    )
    val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Load data on initialization
        // Wrapped in try-catch to prevent crashes during development
        viewModelScope.launch {
            try {
                loadAllData()
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("AnalyticsViewModel", "Error loading analytics data", e)
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        loadAllData()
    }

    fun toggleMuscle(muscle: String) {
        _selectedMuscles.value = if (_selectedMuscles.value.contains(muscle)) {
            _selectedMuscles.value - muscle
        } else {
            _selectedMuscles.value + muscle
        }
    }

    private fun loadAllData() {
        viewModelScope.launch {
            _isLoading.value = true

            val (startDate, endDate) = selectedTimeRange.value.getDateRange()
            val (prevStart, prevEnd) = selectedTimeRange.value.getPreviousPeriod()

            // Launch all data loading in parallel
            launch { loadTrainingFrequency(startDate, endDate, prevStart, prevEnd) }
            launch { loadVolumeData(startDate, endDate, prevStart, prevEnd) }
            launch { loadBestE1RM(startDate, endDate, prevStart, prevEnd) }
            launch { loadPersonalRecords() }
            launch { loadVolumeByMuscle(startDate, endDate) }

            _isLoading.value = false
        }
    }

    private suspend fun loadTrainingFrequency(
        startDate: Long,
        endDate: Long,
        prevStart: Long,
        prevEnd: Long
    ) {
        try {
            val currentDays = setDao.getTrainingDaysCount(startDate, endDate)
            val previousDays = setDao.getTrainingDaysCount(prevStart, prevEnd)

            val weeks = if (selectedTimeRange.value == TimeRange.ALL_TIME) {
                // For all-time, calculate weeks from first workout to now
                val daysSpan = ((endDate - startDate) / (24 * 60 * 60 * 1000)).toFloat()
                (daysSpan / 7).coerceAtLeast(1f)
            } else {
                selectedTimeRange.value.weeks.toFloat()
            }

            val sessionsPerWeek = if (weeks > 0) currentDays / weeks else 0f
            val previousSessions = if (weeks > 0) previousDays / weeks else 0f
            val delta = sessionsPerWeek - previousSessions

            // Calculate sparkline (last 8 weeks)
            val sparkline = calculateFrequencySparkline(endDate)

            _frequencyData.value = FrequencyData(
                sessionsPerWeek = String.format("%.1f", sessionsPerWeek).toFloat(),
                delta = String.format("%.1f", delta).toFloat(),
                sparklineData = sparkline
            )
        } catch (e: Exception) {
            // Handle error - set to null or default values
            _frequencyData.value = null
        }
    }

    private suspend fun loadVolumeData(
        startDate: Long,
        endDate: Long,
        prevStart: Long,
        prevEnd: Long
    ) {
        try {
            val currentVolume = setDao.getTotalVolume(startDate, endDate) ?: 0f
            val previousVolume = setDao.getTotalVolume(prevStart, prevEnd) ?: 1f

            val percentChange = if (previousVolume > 0) {
                ((currentVolume - previousVolume) / previousVolume) * 100
            } else {
                0f
            }

            // Calculate sparkline (last 8 weeks)
            val sparkline = calculateVolumeSparkline(endDate)

            _volumeData.value = VolumeData(
                totalVolume = currentVolume,
                percentChange = String.format("%.1f", percentChange).toFloat(),
                sparklineData = sparkline
            )
        } catch (e: Exception) {
            _volumeData.value = null
        }
    }

    private suspend fun loadBestE1RM(
        startDate: Long,
        endDate: Long,
        prevStart: Long,
        prevEnd: Long
    ) {
        try {
            val currentSets = setDao.getTopSetsForE1RM(startDate, endDate)
            val previousSets = setDao.getTopSetsForE1RM(prevStart, prevEnd)

            // Find best estimated 1RM in current period
            val bestCurrent = currentSets.maxByOrNull {
                calculateEstimated1RM(it.weight ?: 0f, it.reps ?: 1)
            }

            // Find best estimated 1RM in previous period
            val bestPrevious = previousSets.maxByOrNull {
                calculateEstimated1RM(it.weight ?: 0f, it.reps ?: 1)
            }

            if (bestCurrent != null) {
                val currentE1RM = calculateEstimated1RM(bestCurrent.weight!!, bestCurrent.reps!!)
                val previousE1RM = bestPrevious?.let {
                    calculateEstimated1RM(it.weight!!, it.reps!!)
                } ?: currentE1RM

                val delta = currentE1RM - previousE1RM

                // Get exercise name
                val exercise = exerciseDao.getExerciseByIdSync(bestCurrent.exerciseId)

                // Calculate sparkline (best 1RM per week for last 8 weeks)
                val sparkline = calculateE1RMSparkline(endDate)

                _bestE1RMData.value = BestE1RMData(
                    exerciseName = exercise?.name ?: "Unknown",
                    estimatedMax = currentE1RM,
                    delta = delta,
                    sparklineData = sparkline
                )
            } else {
                _bestE1RMData.value = null
            }
        } catch (e: Exception) {
            _bestE1RMData.value = null
        }
    }

    private suspend fun loadPersonalRecords() {
        try {
            _personalRecords.value = setDao.getAllPersonalRecords()
        } catch (e: Exception) {
            _personalRecords.value = emptyList()
        }
    }

    private suspend fun loadVolumeByMuscle(startDate: Long, endDate: Long) {
        try {
            val rawData = setDao.getVolumeByMuscleAndWeek(startDate, endDate)

            // Transform to Map<Muscle, List<VolumePoint>>
            val grouped = rawData.groupBy { it.muscle }
                .mapValues { (_, data) ->
                    data.map { VolumePoint(it.week, it.volume) }
                }

            _volumeByMuscle.value = grouped
        } catch (e: Exception) {
            _volumeByMuscle.value = emptyMap()
        }
    }

    /**
     * Calculate estimated 1RM using Brzycki formula
     * Formula: 1RM = weight / (1.0278 - 0.0278 × reps)
     * Valid for reps 1-12
     */
    fun calculateEstimated1RM(weight: Float, reps: Int): Float {
        if (reps == 1) return weight
        if (reps > 12) return weight // Formula breaks down above 12 reps
        return weight / (1.0278f - 0.0278f * reps)
    }

    private suspend fun calculateFrequencySparkline(endDate: Long): List<Float> {
        val sparklineData = mutableListOf<Float>()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L

        try {
            for (i in 7 downTo 0) {
                val weekEnd = endDate - (i * oneWeek)
                val weekStart = weekEnd - oneWeek
                val days = setDao.getTrainingDaysCount(weekStart, weekEnd)
                sparklineData.add(days.toFloat())
            }
        } catch (e: Exception) {
            // Return empty list on error
            return emptyList()
        }

        return sparklineData
    }

    private suspend fun calculateVolumeSparkline(endDate: Long): List<Float> {
        val sparklineData = mutableListOf<Float>()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L

        try {
            for (i in 7 downTo 0) {
                val weekEnd = endDate - (i * oneWeek)
                val weekStart = weekEnd - oneWeek
                val volume = setDao.getTotalVolume(weekStart, weekEnd) ?: 0f
                sparklineData.add(volume)
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return sparklineData
    }

    private suspend fun calculateE1RMSparkline(endDate: Long): List<Float> {
        val sparklineData = mutableListOf<Float>()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L

        try {
            for (i in 7 downTo 0) {
                val weekEnd = endDate - (i * oneWeek)
                val weekStart = weekEnd - oneWeek

                // Get best set for this week and calculate 1RM
                val sets = setDao.getTopSetsForE1RM(weekStart, weekEnd)
                val bestSet = sets.maxByOrNull {
                    calculateEstimated1RM(it.weight ?: 0f, it.reps ?: 1)
                }

                val e1rm = bestSet?.let {
                    calculateEstimated1RM(it.weight!!, it.reps!!)
                } ?: 0f

                sparklineData.add(e1rm)
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return sparklineData
    }
}
