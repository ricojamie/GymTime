package com.example.gymtime.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class WearSessionClient(context: Context) : DataClient.OnDataChangedListener {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    private val _session = MutableStateFlow(WearSession())
    val session: StateFlow<WearSession> = _session
    private val _setSavedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setSavedEvents: SharedFlow<Unit> = _setSavedEvents

    private var lastSeenSaveConfirmationId = 0L

    fun start() {
        dataClient.addListener(this)
        dataClient.dataItems
            .addOnSuccessListener { items ->
                try {
                    for (item in items) {
                        if (item.uri.path == WearContract.DATA_ACTIVE_SESSION) {
                            handleSessionSnapshot(
                                session = WearSession.fromDataMap(DataMapItem.fromDataItem(item).dataMap),
                                emitSaveConfirmation = false
                            )
                        }
                    }
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { error -> Log.w(TAG, "Unable to read Wear data", error) }
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearContract.DATA_ACTIVE_SESSION
            ) {
                handleSessionSnapshot(
                    session = WearSession.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap),
                    emitSaveConfirmation = true
                )
            }
        }
    }

    fun updateDraft(session: WearSession) {
        _session.value = session
        sendMessage(WearContract.MESSAGE_UPDATE_DRAFT, session.toDataMap())
    }

    fun logSet(session: WearSession) {
        sendMessage(WearContract.MESSAGE_LOG_SET, session.toDataMap())
    }

    fun adjustTimer(deltaSeconds: Int) {
        sendMessage(
            WearContract.MESSAGE_ADJUST_TIMER,
            DataMap().apply { putInt(WearContract.KEY_DELTA_SECONDS, deltaSeconds) }
        )
    }

    fun stopTimer() {
        sendMessage(WearContract.MESSAGE_STOP_TIMER, DataMap())
    }

    private fun handleSessionSnapshot(session: WearSession, emitSaveConfirmation: Boolean) {
        _session.value = session
        val confirmationId = session.setSaveConfirmationId
        if (confirmationId > 0 && confirmationId != lastSeenSaveConfirmationId) {
            if (emitSaveConfirmation) {
                _setSavedEvents.tryEmit(Unit)
            }
            lastSeenSaveConfirmationId = confirmationId
        }
    }

    private fun sendMessage(path: String, dataMap: DataMap) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, dataMap.toByteArray())
                        .addOnFailureListener { error -> Log.w(TAG, "Unable to send Wear command $path", error) }
                }
            }
            .addOnFailureListener { error -> Log.w(TAG, "Unable to find connected phone", error) }
    }

    companion object {
        private const val TAG = "WearSessionClient"
    }
}
