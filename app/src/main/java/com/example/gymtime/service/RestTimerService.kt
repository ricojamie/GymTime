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
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.gymtime.MainActivity
import com.example.gymtime.R
import com.example.gymtime.data.UserPreferencesRepository
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

    private val _remainingSeconds = MutableStateFlow(0)
    private var _totalSeconds = 0 // Track total duration for progress bar
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _currentThemeColor = MutableStateFlow("lime")

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
                addTime(30)
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

        // Acquire WakeLock to keep CPU running
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(seconds * 1000L + 5000L) // Acquire for duration + buffer
            }
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value--
                updateNotification(_remainingSeconds.value)
            }

            // Timer finished
            _isRunning.value = false
            
            // Trigger feedback based on preferences
            viewModelScope_launch_feedback()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun addTime(secondsToAdd: Int) {
        _remainingSeconds.value += secondsToAdd
        _totalSeconds += secondsToAdd // Increase scale so progress bar doesn't jump weirdly
        updateNotification(_remainingSeconds.value)
    }

    private fun viewModelScope_launch_feedback() {
        serviceScope.launch {
            val audioEnabled = userPreferencesRepository.timerAudioEnabled.first()
            val vibrateEnabled = userPreferencesRepository.timerVibrateEnabled.first()
            
            if (audioEnabled) {
                playNotificationTone()
            }
            if (vibrateEnabled) {
                vibrateDevice()
            }
        }
    }

    fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        timerJob?.cancel()
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }

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
            action = ACTION_ADD_TIME
        }
        val addTimePendingIntent = PendingIntent.getService(
            this,
            1, // Different RequestCode
            addTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = formatTime(seconds)
        val progressMax = if (_totalSeconds > 0) _totalSeconds else 1
        
        val themeColor = getNotificationColor(_currentThemeColor.value)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("$formattedTime remaining")
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(themeColor)
            .setColorized(true)
            .setProgress(progressMax, seconds, false)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_timer, "+30s", addTimePendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true) 
            .build()
    }

    private fun getNotificationColor(colorName: String): Int {
        return when (colorName) {
            "lime" -> 0xFFA3E635.toInt()
            "blue" -> 0xFF3B82F6.toInt()
            "purple" -> 0xFFA855F7.toInt()
            "pink" -> 0xFFEC4899.toInt()
            "gold" -> 0xFFF59E0B.toInt()
            "red" -> 0xFFEF4444.toInt()
            "orange" -> 0xFFF97316.toInt()
            "mint" -> 0xFF10B981.toInt()
            "slate" -> 0xFF64748B.toInt()
            "lavender" -> 0xFF8B5CF6.toInt()
            else -> 0xFFA3E635.toInt()
        }
    }

    private fun updateNotification(seconds: Int) {
        val notification = createNotification(seconds)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(seconds: Int): String {
        return com.example.gymtime.util.TimeFormatter.formatSecondsToMMSS(seconds)
    }

    private fun playNotificationTone() {
        try {
            val validNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, validNotificationUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
                setOnCompletionListener { 
                    it.release() 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification tone", e)
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
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "RestTimerService"
        private const val CHANNEL_ID = "rest_timer_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START_TIMER = "com.example.gymtime.action.START_TIMER"
        const val ACTION_ADD_TIME = "com.example.gymtime.action.ADD_TIME"
        const val ACTION_STOP_TIMER = "com.example.gymtime.action.STOP_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
    }
}
