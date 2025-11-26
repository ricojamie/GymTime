package com.example.gymtime

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) : ViewModel() {

    fun checkActiveSession() {
        viewModelScope.launch {
            try {
                val ongoing = workoutDao.getOngoingWorkout().firstOrNull() ?: return@launch

                val lastSetTime = setDao.getLastSetTimestamp(ongoing.id)
                // Use last set time, or workout start time if no sets
                val lastActivityTime = lastSetTime ?: ongoing.startTime
                
                val currentTime = Date()
                val diffInMillis = currentTime.time - lastActivityTime.time
                val hoursElapsed = TimeUnit.MILLISECONDS.toHours(diffInMillis)

                if (hoursElapsed >= 4) {
                    if (lastSetTime == null) {
                        // Case: Empty workout > 4 hours -> Delete
                        Log.d("MainViewModel", "Deleting stale empty workout ${ongoing.id}")
                        workoutDao.deleteWorkout(ongoing)
                    } else {
                        // Case: Active workout > 4 hours since last set -> Finish
                        Log.d("MainViewModel", "Auto-finishing stale workout ${ongoing.id} at $lastSetTime")
                        val finishedWorkout = ongoing.copy(endTime = lastSetTime)
                        workoutDao.updateWorkout(finishedWorkout)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error checking active session", e)
            }
        }
    }
}
