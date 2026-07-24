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
    val lastError: String? = null
)

/**
 * Owns the watch side of the short-lived Wear Data Layer session.
 *
 * Drafts are coalesced and retried while this instance is alive. A set-log request is sent at
 * most once after MessageClient reports success, then remains pending until the phone publishes a
 * save confirmation or advances away from that set. This avoids the most common duplicate-set case
 * without pretending that MessageClient provides a durable, exactly-once queue.
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
        val generation = synchronized(stateLock) {
            if (started) return
            started = true
            connectionStateKnown = false
            liveDataEventGeneration = 0L
            connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            ++startGeneration
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
            connectionStateKnown = false
            connectedNodes = emptyList()
            connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            connectionRetryScheduled = false
            ++startGeneration
        }

        dataClient.removeListener(this)
        retryHandler.removeCallbacks(connectionRetry)
        updateDeliveryState(isPhoneConnected = false)
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
        _session.value = session
        val command = OutboundCommand(
            path = WearContract.MESSAGE_UPDATE_DRAFT,
            payload = session.toDataMap().toByteArray(),
            type = CommandType.DRAFT
        )
        synchronized(stateLock) {
            latestLocalDraft = session
            queuedDraft = command
        }
        updateDeliveryState()
        flushOutbox()
    }

    fun logSet(session: WearSession) {
        if (!session.active || session.workoutId < 0L || session.exerciseId < 0L || !session.canLog) {
            updateDeliveryState(lastError = "This set is not ready to log")
            return
        }

        val setKey = session.setKey()
        val shouldSend = synchronized(stateLock) {
            if (pendingSetKey != null) {
                false
            } else {
                pendingSetKey = setKey
                setLogTransportAccepted = false
                queuedSetLog = OutboundCommand(
                    path = WearContract.MESSAGE_LOG_SET,
                    payload = session.toDataMap().toByteArray(),
                    type = CommandType.LOG_SET
                )
                // LOG_SET carries the complete draft, so an unsent draft would only add latency.
                if (inFlight?.type != CommandType.DRAFT) queuedDraft = null
                true
            }
        }

        updateDeliveryState()
        if (shouldSend) {
            flushOutbox()
        } else {
            // A failed transport remains queued. A second tap is an explicit retry, while a request
            // already accepted by MessageClient is deliberately never resent before confirmation.
            val retryQueuedTransport = synchronized(stateLock) { queuedSetLog != null }
            if (retryQueuedTransport) flushOutbox()
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
        var resolvedSession = session

        synchronized(stateLock) {
            val confirmationId = session.setSaveConfirmationId
            if (!hasSeenSaveConfirmation) {
                lastSeenSaveConfirmationId = confirmationId
                hasSeenSaveConfirmation = true
            } else if (confirmationId > 0L && confirmationId != lastSeenSaveConfirmationId) {
                lastSeenSaveConfirmationId = confirmationId
                shouldEmitConfirmation = emitSaveConfirmation
                if (setLogTransportAccepted) clearPendingSetLocked()
            }

            val pendingKey = pendingSetKey
            if (pendingKey != null && (!session.active || session.setKey() != pendingKey)) {
                clearPendingSetLocked()
            }

            val localDraft = latestLocalDraft
            resolvedSession = when {
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
        }

        _session.value = resolvedSession
        updateDeliveryState(lastError = null)
        if (shouldEmitConfirmation) _setSavedEvents.tryEmit(Unit)
    }

    private fun clearPendingSetLocked() {
        pendingSetKey = null
        setLogTransportAccepted = false
        if (inFlight?.type != CommandType.LOG_SET) queuedSetLog = null
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
                node to command
            }
        }

        if (selection == null) {
            if (shouldRefreshConnection) scheduleConnectionRefresh()
            return
        }

        val (node, command) = selection
        messageClient.sendMessage(node.id, command.path, command.payload)
            .addOnSuccessListener { finishDelivery(command, succeeded = true) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to send Wear command ${command.path}", error)
                finishDelivery(command, succeeded = false)
            }
    }

    private fun finishDelivery(command: OutboundCommand, succeeded: Boolean) {
        synchronized(stateLock) {
            if (inFlight !== command) return
            inFlight = null
            if (succeeded) {
                when (command.type) {
                    CommandType.DRAFT -> if (queuedDraft === command) queuedDraft = null
                    CommandType.LOG_SET -> {
                        if (pendingSetKey != null) setLogTransportAccepted = true
                        if (queuedSetLog === command) queuedSetLog = null
                    }
                }
            } else {
                connectedNodes = emptyList()
                connectionStateKnown = false
            }
        }

        updateDeliveryState(
            lastError = if (succeeded) null else "Could not reach the phone; tap again to retry"
        )
        if (succeeded) {
            flushOutbox()
        } else {
            scheduleConnectionRefresh()
        }
    }

    private fun sendTransient(path: String, dataMap: DataMap) {
        val cachedNode = synchronized(stateLock) {
            connectedNodes
                .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id })
                .firstOrNull()
        }
        if (cachedNode != null) {
            sendTransientToNode(cachedNode, path, dataMap)
            return
        }

        val generation = synchronized(stateLock) { startGeneration }
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (!isCurrentGeneration(generation)) return@addOnSuccessListener
                updateConnectedNodes(nodes, generation)
                val node = nodes
                    .sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id })
                    .firstOrNull()
                if (node == null) {
                    updateDeliveryState(lastError = "Phone is not connected")
                } else {
                    sendTransientToNode(node, path, dataMap)
                }
            }
            .addOnFailureListener { error ->
                updateDeliveryState(lastError = "Unable to check phone connection")
                Log.w(TAG, "Unable to find connected phone", error)
            }
    }

    private fun sendTransientToNode(node: Node, path: String, dataMap: DataMap) {
        messageClient.sendMessage(node.id, path, dataMap.toByteArray())
            .addOnSuccessListener { updateDeliveryState(lastError = null) }
            .addOnFailureListener { error ->
                updateDeliveryState(lastError = "Could not reach the phone")
                Log.w(TAG, "Unable to send Wear command $path", error)
            }
    }

    private fun updateConnectedNodes(nodes: List<Node>, generation: Long) {
        val isCurrent = synchronized(stateLock) {
            if (!started || generation != startGeneration) return
            connectedNodes = nodes
            connectionStateKnown = true
            if (nodes.isNotEmpty()) {
                connectionRetryDelayMillis = INITIAL_CONNECTION_RETRY_DELAY_MILLIS
            }
            connectionRetryScheduled = false
            true
        }
        if (!isCurrent) return

        retryHandler.removeCallbacks(connectionRetry)
        updateDeliveryState(
            isPhoneConnected = nodes.isNotEmpty(),
            lastError = if (nodes.isEmpty()) "Phone is not connected" else null
        )
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
                if (!isCurrentGeneration(generation)) return@addOnFailureListener
                synchronized(stateLock) {
                    connectedNodes = emptyList()
                    connectionStateKnown = false
                }
                updateDeliveryState(
                    isPhoneConnected = false,
                    lastError = "Unable to check phone connection"
                )
                scheduleConnectionRefresh()
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

    private fun updateDeliveryState(
        isPhoneConnected: Boolean? = null,
        lastError: String? = _deliveryState.value.lastError
    ) {
        val (draftPending, setPending) = synchronized(stateLock) {
            Pair(
                queuedDraft != null || inFlight?.type == CommandType.DRAFT,
                pendingSetKey != null
            )
        }
        val previous = _deliveryState.value
        _deliveryState.value = WearDeliveryState(
            isPhoneConnected = isPhoneConnected ?: previous.isPhoneConnected,
            isDraftPending = draftPending,
            isSetLogPending = setPending,
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
    }
}
