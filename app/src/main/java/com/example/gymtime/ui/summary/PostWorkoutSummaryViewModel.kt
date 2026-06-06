package com.example.gymtime.ui.summary

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutSummaryStats(
    val duration: String,
    val totalVolume: Float,
    val totalSets: Int,
    val exerciseCount: Int,
    val muscleGroups: List<String>
)

@HiltViewModel
class PostWorkoutSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutShareImageGenerator: WorkoutShareImageGenerator
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    private val _workoutStats = MutableStateFlow<WorkoutSummaryStats?>(null)
    val workoutStats: StateFlow<WorkoutSummaryStats?> = _workoutStats

    private val _selectedRating = MutableStateFlow<Int?>(null)
    val selectedRating: StateFlow<Int?> = _selectedRating

    private val _ratingNote = MutableStateFlow("")
    val ratingNote: StateFlow<String> = _ratingNote

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _navigationEvent = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _shareEvent = Channel<WorkoutSharePayload>(Channel.BUFFERED)
    val shareEvent = _shareEvent.receiveAsFlow()

    private val _copyEvent = Channel<String>(Channel.BUFFERED)
    val copyEvent = _copyEvent.receiveAsFlow()

    // Volume Orb state
    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    // Session contribution to weekly volume
    private val _sessionContribution = MutableStateFlow(0f)
    val sessionContribution: StateFlow<Float> = _sessionContribution

    init {
        loadWorkoutStats()
        loadVolumeOrbData()
    }

    private fun loadVolumeOrbData() {
        viewModelScope.launch {
            // Refresh orb state
            volumeOrbRepository.refresh()
            // Get this session's contribution
            _sessionContribution.value = volumeOrbRepository.getSessionContribution(workoutId)
        }
    }

    fun clearOrbOverflowAnimation() {
        volumeOrbRepository.clearOverflowAnimation()
    }

    private fun loadWorkoutStats() {
        viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val sets = setDao.getSetsForWorkout(workoutId).first()

                // Calculate duration
                val durationMs = (workout.endTime?.time ?: System.currentTimeMillis()) - workout.startTime.time
                val minutes = (durationMs / 1000 / 60).toInt()
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                val durationString = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                // Calculate total volume (exclude warmup sets)
                val workingSets = sets.filter { !it.isWarmup }
                val totalVolume = workingSets.sumOf { set ->
                    ((set.weight ?: 0f) * (set.reps ?: 0)).toDouble()
                }.toFloat()

                // Get unique exercises and muscle groups
                val exerciseIds = sets.map { it.exerciseId }.distinct()
                val exercises = exerciseIds.mapNotNull { id ->
                    exerciseDao.getExerciseByIdSync(id)
                }
                val muscleGroups = exercises.map { it.targetMuscle }.distinct().sorted()

                _workoutStats.value = WorkoutSummaryStats(
                    duration = durationString,
                    totalVolume = totalVolume,
                    totalSets = workingSets.size,
                    exerciseCount = exerciseIds.size,
                    muscleGroups = muscleGroups
                )

                Log.d("PostWorkoutSummaryVM", "Loaded stats: duration=$durationString, volume=$totalVolume, sets=${workingSets.size}")
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error loading workout stats", e)
            }
        }
    }

    fun updateRating(rating: Int) {
        _selectedRating.value = if (_selectedRating.value == rating) null else rating
    }

    fun updateRatingNote(note: String) {
        _ratingNote.value = note
    }

    fun saveAndFinish() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val updatedWorkout = workout.copy(
                    rating = _selectedRating.value,
                    ratingNote = _ratingNote.value.takeIf { it.isNotBlank() }
                )
                workoutDao.updateWorkout(updatedWorkout)
                Log.d("PostWorkoutSummaryVM", "Saved rating: ${_selectedRating.value}")
                _navigationEvent.send(Unit)
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error saving rating", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun skipAndFinish() {
        viewModelScope.launch {
            _navigationEvent.send(Unit)
        }
    }

    fun onShareClicked(palette: ShareImagePalette) {
        viewModelScope.launch {
            try {
                shareWorkoutUseCase.buildShareableWorkout(workoutId)?.let { workout ->
                    _shareEvent.send(
                        WorkoutSharePayload(
                            imageUri = workoutShareImageGenerator.generate(workout, palette)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
            }
        }
    }

    fun onCopyClicked() {
        viewModelScope.launch {
            try {
                shareWorkoutUseCase.buildShareableWorkout(workoutId)?.let { workout ->
                    _copyEvent.send(WorkoutShareFormatter.format(workout))
                }
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
            }
        }
    }
}
