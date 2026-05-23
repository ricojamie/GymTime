package com.example.gymtime.wear

import android.content.Intent
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
                activeWearSessionRepository.applyDraftPatch(messageEvent.toDraftPatch())
            }
            WearContract.MESSAGE_LOG_SET -> {
                activeWearSessionRepository.requestLogSet(messageEvent.toDraftPatch())
            }
            WearContract.MESSAGE_ADJUST_TIMER -> {
                val dataMap = messageEvent.safeDataMap()
                val deltaSeconds = dataMap.getInt(WearContract.KEY_DELTA_SECONDS, 0)
                val intent = Intent(this, RestTimerService::class.java).apply {
                    action = RestTimerService.ACTION_ADJUST_TIME
                    putExtra(RestTimerService.EXTRA_DELTA_SECONDS, deltaSeconds)
                }
                startService(intent)
            }
            WearContract.MESSAGE_STOP_TIMER -> {
                val intent = Intent(this, RestTimerService::class.java).apply {
                    action = RestTimerService.ACTION_STOP_TIMER
                }
                startService(intent)
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
