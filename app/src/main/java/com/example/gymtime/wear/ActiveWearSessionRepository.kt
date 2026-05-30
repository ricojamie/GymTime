package com.example.gymtime.wear

import android.content.Context
import android.util.Log
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class WearDraftPatch(
    val workoutId: Long,
    val exerciseId: Long,
    val weight: String? = null,
    val reps: String? = null,
    val rpe: String? = null,
    val duration: String? = null,
    val distance: String? = null,
    val calories: String? = null,
    val isWarmup: Boolean? = null
)

data class WearSessionSnapshot(
    val active: Boolean,
    val workoutId: Long?,
    val exerciseId: Long?,
    val exerciseName: String,
    val targetMuscle: String,
    val logType: LogType?,
    val distanceUnit: DistanceUnit?,
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val rpe: String,
    val duration: String,
    val distance: String,
    val calories: String,
    val isWarmup: Boolean,
    val restSeconds: Int,
    val timerRemainingSeconds: Int,
    val timerRunning: Boolean
) {
    companion object {
        fun inactive(): WearSessionSnapshot = WearSessionSnapshot(
            active = false,
            workoutId = null,
            exerciseId = null,
            exerciseName = "",
            targetMuscle = "",
            logType = null,
            distanceUnit = null,
            setNumber = 0,
            weight = "",
            reps = "",
            rpe = "",
            duration = "",
            distance = "",
            calories = "",
            isWarmup = false,
            restSeconds = 0,
            timerRemainingSeconds = 0,
            timerRunning = false
        )

        fun fromLogger(
            workoutId: Long?,
            exercise: Exercise?,
            loggedSets: List<Set>,
            weight: String,
            reps: String,
            rpe: String,
            duration: String,
            distance: String,
            calories: String,
            isWarmup: Boolean,
            selectedDistanceUnit: DistanceUnit,
            restSeconds: Int,
            timerRemainingSeconds: Int,
            timerRunning: Boolean
        ): WearSessionSnapshot {
            if (workoutId == null || exercise == null) return inactive()

            return WearSessionSnapshot(
                active = true,
                workoutId = workoutId,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                targetMuscle = exercise.targetMuscle,
                logType = exercise.logType,
                distanceUnit = selectedDistanceUnit,
                setNumber = loggedSets.size + 1,
                weight = weight,
                reps = reps,
                rpe = rpe,
                duration = duration,
                distance = distance,
                calories = calories,
                isWarmup = isWarmup,
                restSeconds = restSeconds,
                timerRemainingSeconds = timerRemainingSeconds,
                timerRunning = timerRunning
            )
        }
    }
}

@Singleton
class ActiveWearSessionRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val publisherCounter = AtomicLong(0)
    private val completionCounter = AtomicLong(0)
    private val saveConfirmationCounter = AtomicLong(0)
    private var activePublisherId: Long? = null
    private var lastSnapshot: WearSessionSnapshot = WearSessionSnapshot.inactive()
    private var lastCompletionId: Long = 0
    private var lastSaveConfirmationId: Long = 0

    val draftPatches = MutableSharedFlow<WearDraftPatch>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logRequests = MutableSharedFlow<WearDraftPatch?>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Synchronized
    fun beginPublishing(): Long {
        val publisherId = publisherCounter.incrementAndGet()
        activePublisherId = publisherId
        return publisherId
    }

    @Synchronized
    fun publish(publisherId: Long, snapshot: WearSessionSnapshot) {
        if (activePublisherId != publisherId) return
        publishSnapshotState(snapshot)
    }

    @Synchronized
    fun stopPublishing(publisherId: Long) {
        if (activePublisherId != publisherId) return
        activePublisherId = null
        publishSnapshotState(WearSessionSnapshot.inactive())
    }

    @Synchronized
    fun clear() {
        activePublisherId = null
        publishSnapshotState(WearSessionSnapshot.inactive())
    }

    private fun publishSnapshotState(snapshot: WearSessionSnapshot) {
        val completionId = if (
            lastSnapshot.timerRunning &&
            !snapshot.timerRunning &&
            snapshot.timerRemainingSeconds == 0 &&
            snapshot.active
        ) {
            completionCounter.incrementAndGet()
        } else {
            lastCompletionId
        }

        lastSnapshot = snapshot
        lastCompletionId = completionId

        publishSnapshot(snapshot, completionId)
    }

    @Synchronized
    fun confirmSetSaved() {
        lastSaveConfirmationId = saveConfirmationCounter.incrementAndGet()
        publishSnapshot(lastSnapshot, lastCompletionId)
    }

    private fun publishSnapshot(snapshot: WearSessionSnapshot, completionId: Long) {
        val request = PutDataMapRequest.create(WearContract.DATA_ACTIVE_SESSION).apply {
            dataMap.putSnapshot(snapshot, completionId, lastSaveConfirmationId)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to publish Wear session", error)
            }
    }

    fun applyDraftPatch(patch: WearDraftPatch) {
        draftPatches.tryEmit(patch)
    }

    fun requestLogSet(patch: WearDraftPatch?) {
        logRequests.tryEmit(patch)
    }

    private fun DataMap.putSnapshot(
        snapshot: WearSessionSnapshot,
        completionId: Long,
        saveConfirmationId: Long
    ) {
        putBoolean(WearContract.KEY_ACTIVE, snapshot.active)
        putLong(WearContract.KEY_WORKOUT_ID, snapshot.workoutId ?: -1L)
        putLong(WearContract.KEY_EXERCISE_ID, snapshot.exerciseId ?: -1L)
        putString(WearContract.KEY_EXERCISE_NAME, snapshot.exerciseName)
        putString(WearContract.KEY_TARGET_MUSCLE, snapshot.targetMuscle)
        putString(WearContract.KEY_LOG_TYPE, snapshot.logType?.name.orEmpty())
        putString(WearContract.KEY_DISTANCE_UNIT, snapshot.distanceUnit?.name.orEmpty())
        putInt(WearContract.KEY_SET_NUMBER, snapshot.setNumber)
        putString(WearContract.KEY_WEIGHT, snapshot.weight)
        putString(WearContract.KEY_REPS, snapshot.reps)
        putString(WearContract.KEY_RPE, snapshot.rpe)
        putString(WearContract.KEY_DURATION, snapshot.duration)
        putString(WearContract.KEY_DISTANCE, snapshot.distance)
        putString(WearContract.KEY_CALORIES, snapshot.calories)
        putBoolean(WearContract.KEY_IS_WARMUP, snapshot.isWarmup)
        putInt(WearContract.KEY_REST_SECONDS, snapshot.restSeconds)
        putInt(WearContract.KEY_TIMER_REMAINING_SECONDS, snapshot.timerRemainingSeconds)
        putBoolean(WearContract.KEY_TIMER_RUNNING, snapshot.timerRunning)
        putLong(WearContract.KEY_TIMER_COMPLETION_ID, completionId)
        putLong(WearContract.KEY_SET_SAVE_CONFIRMATION_ID, saveConfirmationId)
        putLong(WearContract.KEY_UPDATED_AT, System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "ActiveWearSession"
    }
}
