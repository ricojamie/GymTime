package com.example.gymtime.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            IronLogWearApp()
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001
        )
    }
}

@Composable
private fun IronLogWearApp() {
    val context = LocalContext.current
    val client = remember { WearSessionClient(context) }
    val session by client.session.collectAsState()

    DisposableEffect(client) {
        client.start()
        onDispose { client.stop() }
    }

    LaunchedEffect(session.timerCompletionId) {
        if (session.timerCompletionId > 0) {
            vibrateWatch(context)
        }
    }

    LaunchedEffect(client) {
        client.setSavedEvents.collectLatest {
            Toast.makeText(context, "Set saved", Toast.LENGTH_SHORT).show()
            vibrateWatch(context, durationMillis = 120)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB6FF3B),
            background = Color(0xFF090B08),
            surface = Color(0xFF121611),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (session.active) {
                ActiveSessionScreen(
                    session = session,
                    onSessionChange = client::updateDraft,
                    onLogSet = client::logSet,
                    onAdjustTimer = client::adjustTimer,
                    onStopTimer = client::stopTimer
                )
            } else {
                EmptySessionScreen()
            }
        }
    }
}

@Composable
private fun EmptySessionScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "IronLog",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Open an exercise logger on your phone",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ActiveSessionScreen(
    session: WearSession,
    onSessionChange: (WearSession) -> Unit,
    onLogSet: (WearSession) -> Unit,
    onAdjustTimer: (Int) -> Unit,
    onStopTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = session.exerciseName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )
        Text(
            text = "SET ${session.setNumber}  ${session.targetMuscle.uppercase(Locale.US)}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        TimerControls(
            session = session,
            onAdjustTimer = onAdjustTimer,
            onStopTimer = onStopTimer
        )

        Spacer(modifier = Modifier.height(6.dp))

        LogTypeFields(
            session = session,
            onSessionChange = onSessionChange
        )

        WarmupToggle(
            checked = session.isWarmup,
            onCheckedChange = { onSessionChange(session.copy(isWarmup = it)) }
        )

        Button(
            onClick = { onLogSet(session) },
            enabled = session.canLog,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(21.dp)
        ) {
            Text("LOG SET", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TimerControls(
    session: WearSession,
    onAdjustTimer: (Int) -> Unit,
    onStopTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                session.timerRunning -> formatSeconds(session.timerRemainingSeconds)
                session.timerRemainingSeconds == 0 && session.timerCompletionId > 0L -> "DONE"
                else -> formatSeconds(session.restSeconds)
            },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            SmallPill(text = "-30") { onAdjustTimer(-30) }
            SmallPill(text = "+30") { onAdjustTimer(30) }
            SmallPill(text = "Stop") { onStopTimer() }
        }
    }
}

@Composable
private fun LogTypeFields(
    session: WearSession,
    onSessionChange: (WearSession) -> Unit
) {
    when (session.logType) {
        "WEIGHT_REPS" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) {
                onSessionChange(session.copy(weight = it))
            }
            NumberStepper("Reps", session.reps, "", 1f, wholeNumber = true) {
                onSessionChange(session.copy(reps = it))
            }
        }
        "REPS_ONLY" -> {
            NumberStepper("Reps", session.reps, "", 1f, wholeNumber = true) {
                onSessionChange(session.copy(reps = it))
            }
        }
        "DURATION" -> {
            DurationStepper("Time", session.duration) {
                onSessionChange(session.copy(duration = it))
            }
        }
        "WEIGHT_DISTANCE" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) {
                onSessionChange(session.copy(weight = it))
            }
            NumberStepper("Distance", session.distance, session.distanceUnit.lowercase(Locale.US), 0.1f) {
                onSessionChange(session.copy(distance = it))
            }
        }
        "DISTANCE_TIME" -> {
            NumberStepper("Distance", session.distance, session.distanceUnit.lowercase(Locale.US), 0.1f) {
                onSessionChange(session.copy(distance = it))
            }
            DurationStepper("Time", session.duration) {
                onSessionChange(session.copy(duration = it))
            }
        }
        "WEIGHT_TIME" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) {
                onSessionChange(session.copy(weight = it))
            }
            DurationStepper("Time", session.duration) {
                onSessionChange(session.copy(duration = it))
            }
        }
        "CALORIES_TIME" -> {
            NumberStepper("Calories", session.calories, "", 5f, wholeNumber = true) {
                onSessionChange(session.copy(calories = it))
            }
            DurationStepper("Time", session.duration) {
                onSessionChange(session.copy(duration = it))
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: String,
    suffix: String,
    step: Float,
    wholeNumber: Boolean = false,
    onValueChange: (String) -> Unit
) {
    FieldRow(
        label = label,
        value = buildString {
            append(value.ifBlank { "0" })
            if (suffix.isNotBlank()) append(" ").append(suffix)
        },
        onMinus = {
            onValueChange(adjustNumber(value, -step, wholeNumber))
        },
        onPlus = {
            onValueChange(adjustNumber(value, step, wholeNumber))
        }
    )
}

@Composable
private fun DurationStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    FieldRow(
        label = label,
        value = value.ifBlank { "00:00" },
        onMinus = {
            onValueChange(formatSeconds((parseDuration(value) - 15).coerceAtLeast(0)))
        },
        onPlus = {
            onValueChange(formatSeconds(parseDuration(value) + 15))
        }
    )
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SmallRoundButton("-") { onMinus() }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.White.copy(alpha = 0.64f), fontSize = 9.sp)
            Text(
                value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SmallRoundButton("+") { onPlus() }
    }
}

@Composable
private fun WarmupToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TextButton(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (checked) MaterialTheme.colorScheme.primary else Color.White
        )
    ) {
        Text(
            text = if (checked) "WARMUP ON" else "WARMUP OFF",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SmallRoundButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun adjustNumber(value: String, delta: Float, wholeNumber: Boolean): String {
    val updated = ((value.toFloatOrNull() ?: 0f) + delta).coerceAtLeast(0f)
    return if (wholeNumber || updated % 1f == 0f) {
        updated.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", updated)
    }
}

private fun parseDuration(value: String): Int {
    val parts = value.filter { it.isDigit() || it == ':' }
        .split(":")
        .filter { it.isNotBlank() }
        .mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, remainder)
}

private fun vibrateWatch(context: Context, durationMillis: Long = 500) {
    val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
}
