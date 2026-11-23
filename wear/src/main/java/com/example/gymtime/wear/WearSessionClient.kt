package com.example.gymtime.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class WearDeliveryState(
    val isPhoneConnected: Boolean = false,
    val isDraftPending: Boolean = false,
    val isSetLogPending: Boolean = false,
    val canRetrySetLog: Boolean = false,
    val lastError: String? = null
)

/**
 * Owns the watch side of the short-lived Wear Data Layer session.
 *
 * Drafts are coalesced and retried while this instance is alive. A set-log request is sent at
 * most once after MessageClient reports success, then remains pending until the phone publishes a
 * save confirmation, advances away from that set, or times out waiting for confirmation.
 */
class WearSessionClient(context: Context) : DataClient.OnDataChangedListener {

    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val retryHandler = Handler(Looper.getMainLooper())
    private val connectionRetry = Runnable {
        val generation = synchronized(stateLock) {
            connectionRetryScheduled = false
            startGeneration
        }
        refreshConnectedNodes(generation)
    }
    private val setLogConfirmationTimeout = Runnable {
        val timedOut = synchronized(stateLock) {
            if (!started || !setLogTransportAccepted || pendingSetKey == null) {
                false
            } else {
                clearPendingSetLocked()
                lastError = "Phone did not confirm save; tap again to retry"
                publishDeliveryStateLocked()
                true
            }
        }
        if (timedOut) {
            Log.w(TAG, "Timed out waiting for phone set save confirmation")
        }
    }

    private val _session = MutableStateFlow(WearSession())
    val session: StateFlow<WearSession> = _session

    private val _setSavedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setSavedEvents: SharedFlow<Unit> = _setSavedEvents

    private val _deliveryState = MutableStateFlow(WearDeliveryState())
    val deliveryState: StateFlow<WearDeliveryState> = _deliveryState

    private val stateLock = Any()
    private var started = false
    private var startGeneration = 0L
    private var liveDataEventGeneration = 0L
    private var connectedNodes: List<Node> = emptyList()
    private var connectionStateKnown = false
    private var isPhoneConnected = false
    private var lastError: String? = null

    private var lastSeenSaveConfirmationId = 0L
    private var hasSeenSaveConfirmation = false
    private var latestLocalDraft: WearSession? = null
    private var queuedDraft: OutboundCommand? = null
    private var queuedSetLog: OutboundCommand? = null
    private var inFlight: OutboundCommand? = null
    private var pendingSetKey: SetKey? = null
    private var setLogTransportAccepted = false
    private var connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
    private var connectionRetryScheduled = false

    fun start() {
        var shouldArmConfirmationTimeout = false
        val generation = synchronized(stateLock) {
            if (started) return
            started = true
            connectionStateKnown = false
            isPhoneConnected = false
            liveDataEventGeneration = 0L
            connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            shouldArmConfirmationTimeout = setLogTransportAccepted && pendingSetKey != null
            publishDeliveryStateLocked()
            ++startGeneration
        }

        if (shouldArmConfirmationTimeout) {
            scheduleConfirmationTimeout()
        }

        dataClient.addListener(this)
            .addOnFailureListener { error -> Log.w(TAG, "Unable to observe Wear data", error) }

        dataClient.dataItems
            .addOnSuccessListener { items ->
                try {
                    val shouldApplyInitialSnapshot = synchronized(stateLock) {
                        started && generation == startGeneration && liveDataEventGeneration == 0L
                    }
                    if (shouldApplyInitialSnapshot) {
                        val snapshots = mutableListOf<WearSession>()
                        for (item in items) {
                            if (item.uri.path != WearContract.DATA_ACTIVE_SESSION) continue
                            decodeSession(item)?.let(snapshots::add)
                        }
                        snapshots.maxByOrNull(WearSession::updatedAt)?.let { snapshot ->
                            handleSessionSnapshot(snapshot, emitSaveConfirmation = false)
                        }
                    }
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { error -> Log.w(TAG, "Unable to read Wear data", error) }

        refreshConnectedNodes(generation)
    }

    fun stop() {
        synchronized(stateLock) {
            if (!started) return
            started = false
            inFlight = null
            connectionStateKnown = false
            isPhoneConnected = false
            connectedNodes = emptyList()
            connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            connectionRetryScheduled = false
            ++startGeneration
            publishDeliveryStateLocked()
        }

        dataClient.removeListener(this)
        retryHandler.removeCallbacks(connectionRetry)
        retryHandler.removeCallbacks(setLogConfirmationTimeout)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        synchronized(stateLock) { if (!started) return }
        var sawSessionEvent = false

        for (event in events) {
            if (event.dataItem.uri.path != WearContract.DATA_ACTIVE_SESSION) continue
            if (!sawSessionEvent) {
                synchronized(stateLock) {
                    if (!started) return
                    liveDataEventGeneration++
                }
                sawSessionEvent = true
            }

            when (event.type) {
                DataEvent.TYPE_CHANGED -> decodeSession(event.dataItem)?.let { snapshot ->
                    handleSessionSnapshot(snapshot, emitSaveConfirmation = true)
                }

                DataEvent.TYPE_DELETED -> handleSessionSnapshot(
                    session = WearSession(),
                    emitSaveConfirmation = false
                )
            }
        }
    }

    fun updateDraft(session: WearSession) {
        val command = OutboundCommand(
            path = WearContract.MESSAGE_UPDATE_DRAFT,
            payload = session.toDataMap().toByteArray(),
            type = CommandType.DRAFT
        )
        synchronized(stateLock) {
            _session.value = session
            latestLocalDraft = session
            queuedDraft = command
            publishDeliveryStateLocked()
        }
        flushOutbox()
    }

    fun logSet(session: WearSession) {
        if (!session.active || session.workoutId < 0L || session.exerciseId < 0L || !session.canLog) {
            synchronized(stateLock) {
                lastError = "This set is not ready to log"
                publishDeliveryStateLocked()
            }
            return
        }

        val setKey = session.setKey()
        val shouldFlush = synchronized(stateLock) {
            when {
                pendingSetKey == null -> {
                    pendingSetKey = setKey
                    setLogTransportAccepted = false
                    queuedSetLog = OutboundCommand(
                        path = WearContract.MESSAGE_LOG_SET,
                        payload = session.toDataMap().toByteArray(),
                        type = CommandType.LOG_SET
                    )
                    // LOG_SET carries the complete draft, so an unsent draft would only add latency.
                    if (inFlight?.type != CommandType.DRAFT) queuedDraft = null
                    lastError = null
                    publishDeliveryStateLocked()
                    true
                }
                // A failed or queued transport remains retryable with the latest draft payload,
                // whereas a request already accepted by MessageClient is never resent before ack/timeout.
                !setLogTransportAccepted && queuedSetLog != null && inFlight == null -> {
                    queuedSetLog = OutboundCommand(
                        path = WearContract.MESSAGE_LOG_SET,
                        payload = session.toDataMap().toByteArray(),
                        type = CommandType.LOG_SET
                    )
                    if (inFlight?.type != CommandType.DRAFT) queuedDraft = null
                    lastError = null
                    publishDeliveryStateLocked()
                    true
                }
                else -> false
            }
        }

        if (shouldFlush) {
            flushOutbox()
        }
    }

    fun adjustTimer(deltaSeconds: Int) {
        if (deltaSeconds == 0) return
        sendTransient(
            WearContract.MESSAGE_ADJUST_TIMER,
            DataMap().apply { putInt(WearContract.KEY_DELTA_SECONDS, deltaSeconds) }
        )
    }

    fun stopTimer() {
        sendTransient(WearContract.MESSAGE_STOP_TIMER, DataMap())
    }

    private fun handleSessionSnapshot(session: WearSession, emitSaveConfirmation: Boolean) {
        var shouldEmitConfirmation = false
        var shouldCancelTimeout = false

        synchronized(stateLock) {
            val confirmationId = session.setSaveConfirmationId
            if (!hasSeenSaveConfirmation) {
                lastSeenSaveConfirmationId = confirmationId
                hasSeenSaveConfirmation = true
            } else if (confirmationId > 0L && confirmationId != lastSeenSaveConfirmationId) {
                lastSeenSaveConfirmationId = confirmationId
                shouldEmitConfirmation = emitSaveConfirmation
                if (setLogTransportAccepted) {
                    clearPendingSetLocked()
                    shouldCancelTimeout = true
                }
            }

            val pendingKey = pendingSetKey
            if (pendingKey != null && (!session.active || session.setKey() != pendingKey)) {
                clearPendingSetLocked()
                shouldCancelTimeout = true
            }

            val localDraft = latestLocalDraft
            val resolvedSession = when {
                localDraft == null -> session
                !session.active || session.setKey() != localDraft.setKey() -> {
                    latestLocalDraft = null
                    queuedDraft = null
                    session
                }
                session.hasSameDraft(localDraft) -> {
                    latestLocalDraft = null
                    session
                }
                else -> session.withDraftFrom(localDraft)
            }

            _session.value = resolvedSession
            lastError = null
            publishDeliveryStateLocked()
        }

        if (shouldCancelTimeout) {
            retryHandler.removeCallbacks(setLogConfirmationTimeout)
        }
        if (shouldEmitConfirmation) _setSavedEvents.tryEmit(Unit)
    }

    private fun clearPendingSetLocked() {
        pendingSetKey = null
        setLogTransportAccepted = false
        queuedSetLog = null
    }

    private fun flushOutbox() {
        var shouldRefreshConnection = false
        val selection = synchronized(stateLock) {
            if (!started || inFlight != null) return
            val command = queuedSetLog ?: queuedDraft ?: return
            if (!connectionStateKnown || connectedNodes.isEmpty()) {
                shouldRefreshConnection = true
                null
            } else {
                val node = connectedNodes
                    .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id })
                    .first()
                inFlight = command
                publishDeliveryStateLocked()
                Triple(node, command, startGeneration)
            }
        }

        if (selection == null) {
            if (shouldRefreshConnection) scheduleConnectionRefresh()
            return
        }

        val (node, command, generation) = selection
        messageClient.sendMessage(node.id, command.path, command.payload)
            .addOnSuccessListener { finishDelivery(command, generation, succeeded = true) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to send Wear command ${command.path}", error)
                finishDelivery(command, generation, succeeded = false)
            }
    }

    private fun finishDelivery(command: OutboundCommand, generation: Long, succeeded: Boolean) {
        var shouldScheduleTimeout = false
        val accepted = synchronized(stateLock) {
            if (!started || generation != startGeneration || inFlight !== command) return
            inFlight = null
            if (succeeded) {
                when (command.type) {
                    CommandType.DRAFT -> if (queuedDraft === command) queuedDraft = null
                    CommandType.LOG_SET -> {
                        if (pendingSetKey != null) {
                            setLogTransportAccepted = true
                            shouldScheduleTimeout = true
                        }
                        if (queuedSetLog === command) queuedSetLog = null
                    }
                }
                lastError = null
            } else {
                connectedNodes = emptyList()
                connectionStateKnown = false
                isPhoneConnected = false
                lastError = "Could not reach the phone; tap again to retry"
            }
            publishDeliveryStateLocked()
            succeeded
        }

        if (shouldScheduleTimeout) {
            scheduleConfirmationTimeout()
        }
        if (accepted) {
            flushOutbox()
        } else {
            scheduleConnectionRefresh()
        }
    }

    private fun scheduleConfirmationTimeout() {
        retryHandler.removeCallbacks(setLogConfirmationTimeout)
        retryHandler.postDelayed(setLogConfirmationTimeout, SET_SAVE_ACK_TIMEOUT_MILLIS)
    }

    private fun sendTransient(path: String, dataMap: DataMap) {
        val (cachedNode, generation) = synchronized(stateLock) {
            if (!started) return
            val node = connectedNodes
                .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id })
                .firstOrNull()
            node to startGeneration
        }
        if (cachedNode != null) {
            sendTransientToNode(cachedNode, generation, path, dataMap)
            return
        }

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (!isCurrentGeneration(generation)) return@addOnSuccessListener
                updateConnectedNodes(nodes, generation)
                val node = nodes
                    .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id })
                    .firstOrNull()
                if (node == null) {
                    synchronized(stateLock) {
                        if (started && generation == startGeneration) {
                            lastError = "Phone is not connected"
                            publishDeliveryStateLocked()
                        }
                    }
                } else {
                    sendTransientToNode(node, generation, path, dataMap)
                }
            }
            .addOnFailureListener { error ->
                synchronized(stateLock) {
                    if (started && generation == startGeneration) {
                        lastError = "Unable to check phone connection"
                        publishDeliveryStateLocked()
                    }
                }
                Log.w(TAG, "Unable to find connected phone", error)
            }
    }

    private fun sendTransientToNode(node: Node, generation: Long, path: String, dataMap: DataMap) {
        messageClient.sendMessage(node.id, path, dataMap.toByteArray())
            .addOnSuccessListener {
                synchronized(stateLock) {
                    if (!started || generation != startGeneration) return@addOnSuccessListener
                    lastError = null
                    publishDeliveryStateLocked()
                }
            }
            .addOnFailureListener { error ->
                synchronized(stateLock) {
                    if (!started || generation != startGeneration) return@addOnFailureListener
                    lastError = "Could not reach the phone"
                    publishDeliveryStateLocked()
                }
                Log.w(TAG, "Unable to send Wear command $path", error)
            }
    }

    private fun updateConnectedNodes(nodes: List<Node>, generation: Long) {
        val isCurrent = synchronized(stateLock) {
            if (!started || generation != startGeneration) return
            connectedNodes = nodes
            connectionStateKnown = true
            isPhoneConnected = nodes.isNotEmpty()
            lastError = if (nodes.isEmpty()) "Phone is not connected" else null
            if (nodes.isNotEmpty()) {
                connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            }
            connectionRetryScheduled = false
            publishDeliveryStateLocked()
            true
        }
        if (!isCurrent) return

        retryHandler.removeCallbacks(connectionRetry)
        if (nodes.isNotEmpty()) {
            flushOutbox()
        } else {
            scheduleConnectionRefresh()
        }
    }

    private fun refreshConnectedNodes(generation: Long) {
        if (!isCurrentGeneration(generation)) return
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes -> updateConnectedNodes(nodes, generation) }
            .addOnFailureListener { error ->
                val handled = synchronized(stateLock) {
                    if (!started || generation != startGeneration) {
                        false
                    } else {
                        connectedNodes = emptyList()
                        connectionStateKnown = false
                        isPhoneConnected = false
                        lastError = "Unable to check phone connection"
                        publishDeliveryStateLocked()
                        true
                    }
                }
                if (handled) {
                    scheduleConnectionRefresh()
                }
                Log.w(TAG, "Unable to find connected phone", error)
            }
    }

    private fun scheduleConnectionRefresh() {
        val retryDelayMillis = synchronized(stateLock) {
            if (!started || (queuedSetLog == null && queuedDraft == null)) return
            if (connectionRetryScheduled) return
            val delay = connectionRetryDelayMillis
            connectionRetryDelayMillis = (delay * 2).coerceAtMost(MAX_CONNECTION_RETRY_DELAY_MILLIS)
            connectionRetryScheduled = true
            delay
        }
        retryHandler.removeCallbacks(connectionRetry)
        retryHandler.postDelayed(connectionRetry, retryDelayMillis)
    }

    private fun publishDeliveryStateLocked() {
        val canRetry = pendingSetKey != null &&
            !setLogTransportAccepted &&
            queuedSetLog != null &&
            inFlight == null
        _deliveryState.value = WearDeliveryState(
            isPhoneConnected = isPhoneConnected,
            isDraftPending = queuedDraft != null || inFlight?.type == CommandType.DRAFT,
            isSetLogPending = pendingSetKey != null,
            canRetrySetLog = canRetry,
            lastError = lastError
        )
    }

    private fun decodeSession(dataItem: com.google.android.gms.wearable.DataItem): WearSession? =
        runCatching {
            WearSession.fromDataMap(DataMapItem.fromDataItem(dataItem).dataMap)
        }.onFailure { error ->
            Log.w(TAG, "Ignoring malformed Wear session data", error)
        }.getOrNull()

    private fun isCurrentGeneration(generation: Long): Boolean = synchronized(stateLock) {
        started && generation == startGeneration
    }

    private fun WearSession.setKey() = SetKey(workoutId, exerciseId, setNumber)

    private fun WearSession.hasSameDraft(other: WearSession): Boolean =
        weight == other.weight &&
            reps == other.reps &&
            rpe == other.rpe &&
            duration == other.duration &&
            distance == other.distance &&
            calories == other.calories &&
            isWarmup == other.isWarmup

    private fun WearSession.withDraftFrom(draft: WearSession): WearSession = copy(
        weight = draft.weight,
        reps = draft.reps,
        rpe = draft.rpe,
        duration = draft.duration,
        distance = draft.distance,
        calories = draft.calories,
        isWarmup = draft.isWarmup
    )

    private data class SetKey(
        val workoutId: Long,
        val exerciseId: Long,
        val setNumber: Int
    )

    private data class OutboundCommand(
        val path: String,
        val payload: ByteArray,
        val type: CommandType
    )

    private enum class CommandType {
        DRAFT,
        LOG_SET
    }

    companion object {
        private const val TAG = "WearSessionClient"
        private const val INITIAL_CONNECTION_RETRY_DELAY_MILLIS = 5_000L
        private const val MAX_CONNECTION_RETRY_DELAY_MILLIS = 60_000L
        private const val SET_SAVE_ACK_TIMEOUT_MILLIS = 6_000L
    }
}
