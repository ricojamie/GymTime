package com.example.gymtime.ui.exercise

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

internal data class PreviewPickerPreferences(
    val muscles: Set<String> = emptySet(),
    val sortMode: ExerciseSortMode = ExerciseSortMode.RECENTLY_USED
)

internal data class WorkoutCompletionMarker(val workoutId: Long, val completedAtMs: Long?)

internal class ExercisePickerSession {
    private val mutablePreferences = MutableStateFlow(PreviewPickerPreferences())
    val preferences = mutablePreferences.asStateFlow()

    fun toggleMuscle(muscle: String) = mutablePreferences.update { current ->
        current.copy(muscles = if (muscle in current.muscles) current.muscles - muscle else current.muscles + muscle)
    }

    fun clearMuscles() = mutablePreferences.update { it.copy(muscles = emptySet()) }

    fun changeSort(mode: ExerciseSortMode) = mutablePreferences.update { it.copy(sortMode = mode) }
}

/** Keeps only the current workout's picker choices when navigation replaces its ViewModel. */
@Singleton
class ExercisePickerSessionState @Inject constructor() {
    private var initialized = false
    private var activeWorkoutId: Long? = null
    private var completedWorkout: WorkoutCompletionMarker? = null
    private var session = ExercisePickerSession()

    @Synchronized
    internal fun bind(workoutId: Long?, lastCompletedWorkout: WorkoutCompletionMarker?): ExercisePickerSession {
        // A picker can open before its first set creates a workout. Carry those choices
        // into that workout, but not across a workout that finished while the picker
        // was absent. ID plus completion time also detects a reopened workout that
        // finished again. This distinguishes those cases without an
        // application-wide database observer or permanently sticky preferences.
        val startingFromPicker = activeWorkoutId == null && workoutId != null &&
            completedWorkout == lastCompletedWorkout
        val changedWorkout = activeWorkoutId != workoutId && !startingFromPicker
        val endedWhileAbsent = activeWorkoutId == null && completedWorkout != lastCompletedWorkout
        if (!initialized || changedWorkout || endedWhileAbsent) session = ExercisePickerSession()
        initialized = true
        activeWorkoutId = workoutId
        completedWorkout = lastCompletedWorkout
        return session
    }
}
