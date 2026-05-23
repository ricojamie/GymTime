package com.example.gymtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import com.example.gymtime.MainActivity
import com.example.gymtime.R
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.ui.theme.ThemeColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RestTimerService : Service() {
    
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val binder = TimerBinder()
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var notificationMediaPlayer: MediaPlayer? = null
    private var audioTimeoutJob: Job? = null
    private var hasCompleted = false

    private val _remainingSeconds = MutableStateFlow(0)
    private var _totalSeconds = 0 // Track total duration for progress bar
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _currentThemeColor = MutableStateFlow("lime")
    private val _currentCustomThemeColor = MutableStateFlow<String?>(null)

    inner class TimerBinder : Binder() {
        fun getService(): RestTimerService = this@RestTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymTime:RestTimerWakeLock")

        // Collect theme color updates
        serviceScope.launch {
            userPreferencesRepository.themeColor.collect { colorName ->
                _currentThemeColor.value = colorName
                if (_isRunning.value) {
                    updateNotification(_remainingSeconds.value)
                }
            }
        }
        serviceScope.launch {
            userPreferencesRepository.customThemeColor.collect { colorHex ->
                _currentCustomThemeColor.value = colorHex
                if (_isRunning.value) {
                    updateNotification(_remainingSeconds.value)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 90) ?: 90
        
        // Android 14 requirement: call startForeground immediately for EVERY path when started via startForegroundService
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID, 
            createNotification(seconds), 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        when (intent?.action) {
            ACTION_START_TIMER -> {
                startTimer(seconds)
            }
            ACTION_ADD_TIME -> {
                adjustTime(30)
            }
            ACTION_ADJUST_TIME -> {
                adjustTime(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
            }
            ACTION_STOP_TIMER -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    fun startTimer(seconds: Int) {
        Log.d(TAG, "Starting timer: $seconds seconds")
        _remainingSeconds.value = seconds
        _totalSeconds = seconds
        _isRunning.value = true
        hasCompleted = false

        // Acquire WakeLock to keep CPU running
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(seconds * 1000L + 5000L) // Acquire for duration + buffer
            }
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isRunning.value && _remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
                updateNotification(_remainingSeconds.value)
            }

            if (_isRunning.value) {
                completeTimer(cancelJob = false)
            }
        }
    }

    private fun adjustTime(deltaSeconds: Int) {
        if (!_isRunning.value) return

        val updatedSeconds = (_remainingSeconds.value + deltaSeconds).coerceAtLeast(0)
        _remainingSeconds.value = updatedSeconds

        if (deltaSeconds > 0) {
            _totalSeconds += deltaSeconds // Increase scale so progress bar doesn't jump weirdly
        }
        updateNotification(_remainingSeconds.value)

        if (updatedSeconds == 0) {
            completeTimer()
        }
    }

    private fun completeTimer(cancelJob: Boolean = true) {
        if (hasCompleted) return
        hasCompleted = true

        if (cancelJob) {
            timerJob?.cancel()
        }

        releaseWakeLock()
        _isRunning.value = false
        _remainingSeconds.value = 0

        // Run feedback, then tear down the service only once playback finishes
        // so we don't destroy ourselves mid-tone (which left audio focus held).
        serviceScope.launch {
            val audioEnabled = userPreferencesRepository.timerAudioEnabled.first()
            val vibrateEnabled = userPreferencesRepository.timerVibrateEnabled.first()

            if (vibrateEnabled) {
                vibrateDevice()
            }
            if (audioEnabled) {
                playNotificationTone {
                    finishService()
                }
            } else {
                finishService()
            }
        }
    }

    private fun finishService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        timerJob?.cancel()
        releaseWakeLock()

        _isRunning.value = false
        _remainingSeconds.value = 0
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

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(seconds: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addTimeIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_ADJUST_TIME
            putExtra(EXTRA_DELTA_SECONDS, 5)
        }
        val addTimePendingIntent = PendingIntent.getService(
            this,
            1, // Different RequestCode
            addTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = formatTime(seconds)
        val progressMax = if (_totalSeconds > 0) _totalSeconds else 1
        
        val themeColor = getNotificationColor(
            colorName = _currentThemeColor.value,
            customColorHex = _currentCustomThemeColor.value
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("$formattedTime remaining")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(themeColor)
            .setColorized(true)
            .setProgress(progressMax, seconds, false)
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

    private fun updateNotification(seconds: Int) {
        val notification = createNotification(seconds)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(seconds: Int): String {
        return com.example.gymtime.util.TimeFormatter.formatSecondsToMMSS(seconds)
    }

    private fun playNotificationTone(onFinished: () -> Unit) {
        // Single completion gate so we only call onFinished + cleanup once,
        // whether playback ends normally, errors out, or hits the timeout.
        var finished = false
        val complete: () -> Unit = {
            if (!finished) {
                finished = true
                releaseNotificationMediaPlayer()
                abandonAudioFocus()
                onFinished()
            }
        }

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .build()

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
                if (headphoneDevice != null && !preparedPlayer.setPreferredDevice(headphoneDevice)) {
                    Log.w(TAG, "Unable to prefer headphone output; using Android's active media route")
                }
                preparedPlayer.start()
                if (headphoneDevice != null && preparedPlayer.routedDevice?.isHeadphoneOutput() == false) {
                    Log.w(TAG, "Rest timer tone routed away from headphones; stopping playback")
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
                fadeOut(player)
                complete()
            }
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

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private suspend fun fadeOut(player: MediaPlayer) {
        val steps = 10
        val stepMs = FADE_OUT_DURATION_MS / steps
        for (i in 1..steps) {
            val volume = ((steps - i).toFloat() / steps).coerceIn(0f, 1f)
            try {
                if (notificationMediaPlayer === player) player.setVolume(volume, volume)
            } catch (_: IllegalStateException) {
                return
            }
            delay(stepMs)
        }
    }

    private fun releaseNotificationMediaPlayer() {
        audioTimeoutJob?.cancel()
        audioTimeoutJob = null
        notificationMediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: IllegalStateException) {
                // Player wasn't in a state where stop is valid; release will still work.
            }
            try {
                player.reset()
            } catch (_: IllegalStateException) { }
            player.release()
        }
        notificationMediaPlayer = null
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }
    }

    private fun vibrateDevice() {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        releaseWakeLock()
        releaseNotificationMediaPlayer()
        abandonAudioFocus()
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "RestTimerService"
        private const val CHANNEL_ID = "rest_timer_channel"
        private const val NOTIFICATION_ID = 1
        private const val MAX_TONE_DURATION_MS = 4_000L
        private const val FADE_OUT_DURATION_MS = 500L

        const val ACTION_START_TIMER = "com.example.gymtime.action.START_TIMER"
        const val ACTION_ADD_TIME = "com.example.gymtime.action.ADD_TIME"
        const val ACTION_ADJUST_TIME = "com.example.gymtime.action.ADJUST_TIME"
        const val ACTION_STOP_TIMER = "com.example.gymtime.action.STOP_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_DELTA_SECONDS = "extra_delta_seconds"
    }
}
