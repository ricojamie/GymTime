package com.example.gymtime.wear

import com.example.gymtime.service.RestTimerService
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearCommandListenerService : WearableListenerService() {

    @Inject lateinit var activeWearSessionRepository: ActiveWearSessionRepository

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearContract.MESSAGE_UPDATE_DRAFT -> {
                if (activeWearSessionRepository.hasActivePublisher()) {
                    activeWearSessionRepository.applyDraftPatch(messageEvent.toDraftPatch())
                } else {
                    activeWearSessionRepository.clear()
                }
            }
            WearContract.MESSAGE_LOG_SET -> {
                if (activeWearSessionRepository.hasActivePublisher()) {
                    activeWearSessionRepository.requestLogSet(messageEvent.toDraftPatch())
                } else {
                    activeWearSessionRepository.clear()
                }
            }
            WearContract.MESSAGE_ADJUST_TIMER -> {
                val dataMap = messageEvent.safeDataMap()
                val deltaSeconds = dataMap.getInt(WearContract.KEY_DELTA_SECONDS, 0)
                if (!RestTimerService.adjustActiveTimer(deltaSeconds) &&
                    !activeWearSessionRepository.hasActivePublisher()
                ) {
                    activeWearSessionRepository.clear()
                }
            }
            WearContract.MESSAGE_STOP_TIMER -> {
                if (!RestTimerService.stopActiveTimer() &&
                    !activeWearSessionRepository.hasActivePublisher()
                ) {
                    activeWearSessionRepository.clear()
                }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun MessageEvent.toDraftPatch(): WearDraftPatch {
        val dataMap = safeDataMap()
        return WearDraftPatch(
            workoutId = dataMap.getLong(WearContract.KEY_WORKOUT_ID, -1L),
            exerciseId = dataMap.getLong(WearContract.KEY_EXERCISE_ID, -1L),
            weight = dataMap.getOptionalString(WearContract.KEY_WEIGHT),
            reps = dataMap.getOptionalString(WearContract.KEY_REPS),
            rpe = dataMap.getOptionalString(WearContract.KEY_RPE),
            duration = dataMap.getOptionalString(WearContract.KEY_DURATION),
            distance = dataMap.getOptionalString(WearContract.KEY_DISTANCE),
            calories = dataMap.getOptionalString(WearContract.KEY_CALORIES),
            isWarmup = if (dataMap.containsKey(WearContract.KEY_IS_WARMUP)) {
                dataMap.getBoolean(WearContract.KEY_IS_WARMUP)
            } else {
                null
            }
        )
    }

    private fun MessageEvent.safeDataMap(): DataMap = runCatching {
        DataMap.fromByteArray(data)
    }.getOrDefault(DataMap())

    private fun DataMap.getOptionalString(key: String): String? =
        if (containsKey(key)) getString(key) else null
}
