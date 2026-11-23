package com.example.gymtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.example.gymtime.MainActivity
import com.example.gymtime.R
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.util.TimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val binder = TimerBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val stateLock = Any()

    private var timerJob: Job? = null
    private var completionJob: Job? = null
    private var audioTimeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var notificationMediaPlayer: MediaPlayer? = null

    private var timerGeneration = 0L
    private var endElapsedRealtimeMs = 0L
    private var hasCompleted = false
    private var _totalSeconds = 0

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    @Volatile
    private var cachedNotificationColor: Int = ThemeColors.LimeGreen.primaryAccent.toArgb()

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }
    private val tapPendingIntent: PendingIntent by lazy {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    private val stopPendingIntent: PendingIntent by lazy {
        val stopIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    private val addTimePendingIntent: PendingIntent by lazy {
        val addTimeIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_ADJUST_TIME
            putExtra(EXTRA_DELTA_SECONDS, 5)
        }
        PendingIntent.getService(
            this,
            1,
            addTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class TimerStartTicket(
        val generation: Long,
        val remainingSeconds: Int,
        val totalSeconds: Int
    )

    inner class TimerBinder : Binder() {
        fun getService(): RestTimerService = this@RestTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        activeInstance = this

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymTime:RestTimerWakeLock").apply {
            setReferenceCounted(false)
        }
        createNotificationChannel()

        serviceScope.launch {
            combine(
                userPreferencesRepository.themeColor,
                userPreferencesRepository.customThemeColor
            ) { colorName, customColorHex ->
                getNotificationColor(colorName, customColorHex)
            }.collect { color ->
                cachedNotificationColor = color
                val snapshot = synchronized(stateLock) {
                    if (_isRunning.value) _remainingSeconds.value to _totalSeconds else null
                }
                if (snapshot != null) {
                    updateNotification(snapshot.first, snapshot.second)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val isTimerStart = action == ACTION_START_TIMER
        val isTimerControl = action == ACTION_ADD_TIME ||
            action == ACTION_ADJUST_TIME ||
            action == ACTION_STOP_TIMER

        val canRunControl = synchronized(stateLock) { _isRunning.value }
        if ((!isTimerStart && !isTimerControl) || (isTimerControl && !canRunControl)) {
            // A stale notification or Wear command must not create an idle foreground
            // service. Only ACTION_START_TIMER is launched via startForegroundService.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START_TIMER -> {
                val safeSeconds = (intent.getIntExtra(EXTRA_SECONDS, 90)).coerceAtLeast(0)
                createNotificationChannel()
                val ticket = prepareTimerStartLocked(safeSeconds)
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(ticket.remainingSeconds, ticket.totalSeconds),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                beginPreparedTimer(ticket)
            }
            ACTION_ADD_TIME -> adjustTime(30)
            ACTION_ADJUST_TIME -> adjustTime(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
            ACTION_STOP_TIMER -> stopTimer()
        }
        return START_NOT_STICKY
    }

    fun startTimer(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(0)
        Log.d(TAG, "Starting timer: $safeSeconds seconds")
        val ticket = prepareTimerStartLocked(safeSeconds)
        updateNotification(ticket.remainingSeconds, ticket.totalSeconds)
        beginPreparedTimer(ticket)
    }

    private fun prepareTimerStartLocked(seconds: Int): TimerStartTicket {
        return synchronized(stateLock) {
            val generation = ++timerGeneration
            timerJob?.cancel()
            timerJob = null
            completionJob?.cancel()
            completionJob = null
            releaseNotificationMediaPlayerLocked()
            abandonAudioFocusLocked()

            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = seconds * 1000L
            endElapsedRealtimeMs = nowMs + remainingMs
            _totalSeconds = seconds
            _remainingSeconds.value = seconds
            _isRunning.value = true
            hasCompleted = false

            acquireWakeLockLocked(remainingMs)
            TimerStartTicket(
                generation = generation,
                remainingSeconds = seconds,
                totalSeconds = seconds
            )
        }
    }

    private fun beginPreparedTimer(ticket: TimerStartTicket) {
        synchronized(stateLock) {
            if (ticket.generation != timerGeneration || !_isRunning.value || hasCompleted) {
                return
            }
            if (ticket.remainingSeconds <= 0) {
                completeTimerLocked(ticket.generation)
            } else {
                launchTimerLoopLocked(ticket.generation)
            }
        }
    }

    private fun launchTimerLoopLocked(generation: Long) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                var shouldUpdateNotification = false
                var notificationRemaining = 0
                var notificationTotal = 1
                var nextDelayMs = 1000L

                val shouldContinue = synchronized(stateLock) {
                    if (generation != timerGeneration || !_isRunning.value || hasCompleted) {
                        false
                    } else {
                        val remainingMs = (endElapsedRealtimeMs - SystemClock.elapsedRealtime())
                            .coerceAtLeast(0L)
                        if (remainingMs == 0L) {
                            notificationRemaining = 0
                            notificationTotal = _totalSeconds
                            shouldUpdateNotification = true
                            completeTimerLocked(generation)
                            false
                        } else {
                            val updatedSeconds = ((remainingMs + 999L) / 1000L).toInt()
                            if (_remainingSeconds.value != updatedSeconds) {
                                _remainingSeconds.value = updatedSeconds
                            }
                            shouldUpdateNotification = true
                            notificationRemaining = updatedSeconds
                            notificationTotal = _totalSeconds
                            nextDelayMs = (((remainingMs - 1L) % 1000L) + 1L).coerceAtLeast(16L)
                            true
                        }
                    }
                }

                if (!shouldContinue) break
                if (shouldUpdateNotification) {
                    updateNotification(notificationRemaining, notificationTotal)
                }
                delay(nextDelayMs)
            }
        }
    }

    private fun adjustTime(deltaSeconds: Int) {
        if (deltaSeconds == 0) return

        var notificationSnapshot: Pair<Int, Int>? = null
        synchronized(stateLock) {
            if (!_isRunning.value || hasCompleted) return

            val nowMs = SystemClock.elapsedRealtime()
            val newEndMs = (endElapsedRealtimeMs + deltaSeconds * 1000L).coerceAtLeast(nowMs)
            endElapsedRealtimeMs = newEndMs
            val remainingMs = (newEndMs - nowMs).coerceAtLeast(0L)
            val updatedSeconds = ((remainingMs + 999L) / 1000L).toInt()

            if (deltaSeconds > 0) {
                _totalSeconds = maxOf(_totalSeconds + deltaSeconds, updatedSeconds)
                acquireWakeLockLocked(remainingMs)
            }

            _remainingSeconds.value = updatedSeconds

            if (remainingMs == 0L) {
                timerJob?.cancel()
                timerJob = null
                completeTimerLocked(timerGeneration)
            } else {
                notificationSnapshot = updatedSeconds to _totalSeconds
                launchTimerLoopLocked(timerGeneration)
            }
        }

        notificationSnapshot?.let { (remaining, total) ->
            updateNotification(remaining, total)
        }
    }

    private fun completeTimerLocked(generation: Long) {
        if (hasCompleted || generation != timerGeneration) return
        hasCompleted = true

        timerJob?.cancel()
        timerJob = null
        _isRunning.value = false
        _remainingSeconds.value = 0

        // Run feedback while holding WakeLock, then tear down only after playback completes.
        completionJob?.cancel()
        completionJob = serviceScope.launch {
            val audioEnabled = userPreferencesRepository.timerAudioEnabled.first()
            val vibrateEnabled = userPreferencesRepository.timerVibrateEnabled.first()

            val stillActiveGeneration = synchronized(stateLock) {
                generation == timerGeneration && isActive
            }
            if (!stillActiveGeneration) return@launch

            if (vibrateEnabled) {
                vibrateDevice()
            }
            if (audioEnabled) {
                playNotificationTone(generation) { completedGeneration ->
                    finishService(completedGeneration)
                }
            } else {
                finishService(generation)
            }
        }
    }

    private fun finishService(expectedGeneration: Long) {
        val shouldStop = synchronized(stateLock) {
            if (expectedGeneration != timerGeneration) {
                false
            } else {
                releaseWakeLockLocked()
                true
            }
        }
        if (shouldStop) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        synchronized(stateLock) {
            timerGeneration++
            timerJob?.cancel()
            timerJob = null
            completionJob?.cancel()
            completionJob = null
            hasCompleted = false
            endElapsedRealtimeMs = 0L
            _isRunning.value = false
            _remainingSeconds.value = 0
            releaseNotificationMediaPlayerLocked()
            abandonAudioFocusLocked()
            releaseWakeLockLocked()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Rest timer countdown notifications"
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(seconds: Int, totalSeconds: Int): Notification {
        val safeSeconds = seconds.coerceAtLeast(0)
        val formattedTime = formatTime(safeSeconds)
        val progressMax = if (totalSeconds > 0) totalSeconds else 1

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("$formattedTime remaining")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(cachedNotificationColor)
            .setColorized(true)
            .setProgress(progressMax, safeSeconds.coerceAtMost(progressMax), false)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_timer, "+5s", addTimePendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun getNotificationColor(colorName: String, customColorHex: String?): Int {
        return ThemeColors.getScheme(colorName, customColorHex).primaryAccent.toArgb()
    }

    private fun updateNotification(seconds: Int, totalSeconds: Int) {
        val notification = createNotification(seconds, totalSeconds)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked while running.
        }
    }

    private fun formatTime(seconds: Int): String {
        return TimeFormatter.formatSecondsToMMSS(seconds.coerceAtLeast(0))
    }

    private fun playNotificationTone(generation: Long, onFinished: (Long) -> Unit) {
        // Single completion gate so we only call onFinished + cleanup once,
        // whether playback ends normally, errors out, or hits the timeout.
        val finished = AtomicBoolean(false)
        val complete: () -> Unit = {
            if (finished.compareAndSet(false, true)) {
                synchronized(stateLock) {
                    if (generation == timerGeneration) {
                        releaseNotificationMediaPlayerLocked()
                        abandonAudioFocusLocked()
                    }
                }
                onFinished(generation)
            }
        }

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .build()

            val startedPlayback = synchronized(stateLock) {
                if (generation != timerGeneration) {
                    false
                } else {
                    val focusResult = audioManager.requestAudioFocus(focusRequest)
                    if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        audioFocusRequest = focusRequest
                    } else {
                        Log.w(TAG, "Audio focus not granted for rest timer tone; playing without ducking")
                    }

                    val player = MediaPlayer()
                    notificationMediaPlayer = player
                    player.setAudioAttributes(audioAttributes)
                    val headphoneDevice = findHeadphoneOutputDevice(audioManager)
                    resources.openRawResourceFd(R.raw.boxing_bell).use { assetFileDescriptor ->
                        player.setDataSource(
                            assetFileDescriptor.fileDescriptor,
                            assetFileDescriptor.startOffset,
                            assetFileDescriptor.length
                        )
                    }
                    // Wire listeners BEFORE starting playback so we never miss completion.
                    player.setOnCompletionListener { complete() }
                    player.setOnErrorListener { _, _, _ ->
                        complete()
                        true
                    }
                    player.setOnPreparedListener { preparedPlayer ->
                        var routedAwayFromHeadphones = false
                        var startFailed = false
                        synchronized(stateLock) {
                            if (
                                !finished.get() &&
                                generation == timerGeneration &&
                                notificationMediaPlayer === preparedPlayer
                            ) {
                                try {
                                    if (headphoneDevice != null && !preparedPlayer.setPreferredDevice(headphoneDevice)) {
                                        Log.w(TAG, "Unable to prefer headphone output; using Android's active media route")
                                    }
                                    preparedPlayer.start()
                                    if (headphoneDevice != null && preparedPlayer.routedDevice?.isHeadphoneOutput() == false) {
                                        Log.w(TAG, "Rest timer tone routed away from headphones; stopping playback")
                                        routedAwayFromHeadphones = true
                                    }
                                } catch (e: IllegalStateException) {
                                    Log.w(TAG, "MediaPlayer state invalid on prepare", e)
                                    startFailed = true
                                }
                            }
                        }
                        if (routedAwayFromHeadphones || startFailed) {
                            complete()
                        }
                    }
                    player.prepareAsync()

                    // Hard cap the tone at MAX_TONE_DURATION_MS so we never duck the
                    // user's music for longer than that, fading the last
                    // FADE_OUT_DURATION_MS so the cutoff isn't abrupt. Also doubles as
                    // a safety net if neither completion nor error ever fires.
                    audioTimeoutJob?.cancel()
                    audioTimeoutJob = serviceScope.launch {
                        delay(MAX_TONE_DURATION_MS - FADE_OUT_DURATION_MS)
                        fadeOut(player, generation)
                        complete()
                    }
                    true
                }
            }
            if (!startedPlayback) {
                finished.set(true)
            }
        } catch (ce: CancellationException) {
            synchronized(stateLock) {
                if (generation == timerGeneration) {
                    releaseNotificationMediaPlayerLocked()
                    abandonAudioFocusLocked()
                }
            }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification tone", e)
            complete()
        }
    }

    private fun findHeadphoneOutputDevice(audioManager: AudioManager): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .sortedBy { it.headphoneRoutingPriority() }
            .firstOrNull { it.isHeadphoneOutput() }
    }

    private fun AudioDeviceInfo.isHeadphoneOutput(): Boolean {
        return headphoneRoutingPriority() != Int.MAX_VALUE
    }

    private fun AudioDeviceInfo.headphoneRoutingPriority(): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> 0
            AudioDeviceInfo.TYPE_USB_HEADSET -> 1
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> 2
            else -> Int.MAX_VALUE
        }
    }

    private fun abandonAudioFocusLocked() {
        val request = audioFocusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private suspend fun fadeOut(player: MediaPlayer, generation: Long) {
        val steps = 10
        val stepMs = FADE_OUT_DURATION_MS / steps
        for (i in 1..steps) {
            val volume = ((steps - i).toFloat() / steps).coerceIn(0f, 1f)
            val shouldContinue = synchronized(stateLock) {
                if (generation != timerGeneration || notificationMediaPlayer !== player) {
                    false
                } else {
                    try {
                        player.setVolume(volume, volume)
                        true
                    } catch (_: IllegalStateException) {
                        false
                    }
                }
            }
            if (!shouldContinue) return
            delay(stepMs)
        }
    }

    private fun releaseNotificationMediaPlayerLocked() {
        audioTimeoutJob?.cancel()
        audioTimeoutJob = null
        notificationMediaPlayer?.let { player ->
            notificationMediaPlayer = null
            try {
                if (player.isPlaying) player.stop()
            } catch (_: IllegalStateException) {
                // Player wasn't in a state where stop is valid; release will still work.
            }
            try {
                player.reset()
            } catch (_: IllegalStateException) {
            }
            try {
                player.release()
            } catch (_: IllegalStateException) {
            }
        }
    }

    private fun acquireWakeLockLocked(remainingMs: Long) {
        try {
            wakeLock?.acquire(remainingMs.coerceAtLeast(0L) + WAKE_LOCK_BUFFER_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wakelock", e)
        }
    }

    private fun releaseWakeLockLocked() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }
    }

    private fun vibrateDevice() {
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        synchronized(stateLock) {
            timerGeneration++
            timerJob?.cancel()
            timerJob = null
            completionJob?.cancel()
            completionJob = null
            releaseNotificationMediaPlayerLocked()
            abandonAudioFocusLocked()
            releaseWakeLockLocked()
        }
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "RestTimerService"
        private const val CHANNEL_ID = "rest_timer_channel"
        private const val NOTIFICATION_ID = 1
        private const val MAX_TONE_DURATION_MS = 4_000L
        private const val FADE_OUT_DURATION_MS = 500L
        private const val WAKE_LOCK_BUFFER_MS = 10_000L

        @Volatile
        private var activeInstance: RestTimerService? = null

        internal fun adjustActiveTimer(deltaSeconds: Int): Boolean {
            val service = activeInstance ?: return false
            val isActive = synchronized(service.stateLock) { service._isRunning.value && !service.hasCompleted }
            if (!isActive) return false
            service.adjustTime(deltaSeconds)
            return true
        }

        internal fun stopActiveTimer(): Boolean {
            val service = activeInstance ?: return false
            val hasWork = synchronized(service.stateLock) {
                service._isRunning.value || service.completionJob != null || service.notificationMediaPlayer != null
            }
            if (!hasWork) return false
            service.stopTimer()
            return true
        }

        const val ACTION_START_TIMER = "com.example.gymtime.action.START_TIMER"
        const val ACTION_ADD_TIME = "com.example.gymtime.action.ADD_TIME"
        const val ACTION_ADJUST_TIME = "com.example.gymtime.action.ADJUST_TIME"
        const val ACTION_STOP_TIMER = "com.example.gymtime.action.STOP_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_DELTA_SECONDS = "extra_delta_seconds"
    }
}
