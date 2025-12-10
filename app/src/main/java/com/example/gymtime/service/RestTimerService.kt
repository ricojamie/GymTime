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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gymtime.MainActivity
import com.example.gymtime.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RestTimerService : Service() {

    private val binder = TimerBinder()
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    inner class TimerBinder : Binder() {
        fun getService(): RestTimerService = this@RestTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 90)
                startTimer(seconds)
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
        _isRunning.value = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(seconds))

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value--
                updateNotification(_remainingSeconds.value)
            }

            // Timer finished
            _isRunning.value = false
            vibrateDevice()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        timerJob?.cancel()
        _isRunning.value = false
        _remainingSeconds.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText(formatTime(seconds))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using system icon for now
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun updateNotification(seconds: Int) {
        val notification = createNotification(seconds)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            String.format("%d:%02d", mins, secs)
        } else {
            String.format("0:%02d", secs)
        }
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
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
        const val ACTION_STOP_TIMER = "com.example.gymtime.action.STOP_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
    }
}
