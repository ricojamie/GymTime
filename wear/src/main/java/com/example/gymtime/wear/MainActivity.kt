package com.example.gymtime.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ScreenScaffold
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val LOG_REQUEST_TIMEOUT_MILLIS = 4_000L
private const val UNINITIALIZED_COMPLETION_ID = Long.MIN_VALUE

private val IronLogGreen = Color(0xFFB6FF3B)
private val IronLogBackground = Color(0xFF090B08)
private val IronLogSurface = Color(0xFF121611)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IronLogWearApp()
        }
    }
}

@Composable
private fun IronLogWearApp() {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val client = remember(applicationContext) { WearSessionClient(applicationContext) }
    val session by client.session.collectAsStateWithLifecycle()
    val deliveryState by client.deliveryState.collectAsStateWithLifecycle()
    var notificationsEnabled by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
    }
    var logRequestPending by remember { mutableStateOf(false) }
    var lastSeenTimerCompletionId by rememberSaveable {
        mutableLongStateOf(UNINITIALIZED_COMPLETION_ID)
    }
    var showTimerCompletion by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(client) {
        client.start()
        onDispose { client.stop() }
    }

    // Ignore the initial hydrated completion ID. It may represent a timer that ended while the
    // activity was not running and must not produce a stale haptic when the user opens the app.
    LaunchedEffect(session.timerCompletionId, session.updatedAt, session.active) {
        if (session.updatedAt <= 0L) return@LaunchedEffect

        if (lastSeenTimerCompletionId == UNINITIALIZED_COMPLETION_ID) {
            lastSeenTimerCompletionId = session.timerCompletionId
            showTimerCompletion = false
        } else if (
            session.active &&
            session.timerCompletionId > 0L &&
            session.timerCompletionId != lastSeenTimerCompletionId
        ) {
            lastSeenTimerCompletionId = session.timerCompletionId
            showTimerCompletion = true
            vibrateWatch(applicationContext)
        } else {
            lastSeenTimerCompletionId = session.timerCompletionId
            if (!session.active || session.timerRunning || session.timerRemainingSeconds > 0) {
                showTimerCompletion = false
            }
        }
    }

    LaunchedEffect(client) {
        client.setSavedEvents.collectLatest {
            logRequestPending = false
            Toast.makeText(applicationContext, "Set saved", Toast.LENGTH_SHORT).show()
            vibrateWatch(applicationContext, durationMillis = 120)
        }
    }

    // A failed/disconnected send must become retryable even when no acknowledgement arrives.
    LaunchedEffect(logRequestPending) {
        if (logRequestPending) {
            delay(LOG_REQUEST_TIMEOUT_MILLIS)
            logRequestPending = false
        }
    }

    // A phone-side session transition also acknowledges that the previous logging UI is obsolete.
    LaunchedEffect(session.workoutId, session.exerciseId, session.setNumber) {
        logRequestPending = false
        showTimerCompletion = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IronLogGreen,
            background = IronLogBackground,
            surface = IronLogSurface,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (session.active) {
                ActiveSessionScreen(
                    session = session,
                    showTimerCompletion = showTimerCompletion,
                    deliveryState = deliveryState,
                    logRequestPending = logRequestPending || deliveryState.isSetLogPending,
                    onSessionChange = { transform ->
                        // Always transform the latest optimistic draft. This prevents fast repeated
                        // taps in the same frame from applying to the same stale captured value.
                        client.updateDraft(transform(client.session.value))
                    },
                    onLogSet = {
                        val latestSession = client.session.value
                        if (
                            !logRequestPending &&
                            !deliveryState.isSetLogPending &&
                            latestSession.canLog
                        ) {
                            logRequestPending = true
                            client.logSet(latestSession)
                        }
                    },
                    onAdjustTimer = client::adjustTimer,
                    onStopTimer = client::stopTimer
                )
            } else {
                EmptySessionScreen(
                    deliveryState = deliveryState,
                    showNotificationOptIn = !notificationsEnabled,
                    onEnableNotifications = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptySessionScreen(
    deliveryState: WearDeliveryState,
    showNotificationOptIn: Boolean,
    onEnableNotifications: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.isScreenRound) 26.dp else 18.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = horizontalPadding)
            .semantics { paneTitle = "IronLog watch" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "IronLog",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start or open a workout on your phone. Your exercise will appear here.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            if (deliveryState.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phone unavailable",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            if (showNotificationOptIn) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onEnableNotifications) {
                    Text(
                        text = "ENABLE WORKOUT SHORTCUT",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionScreen(
    session: WearSession,
    showTimerCompletion: Boolean,
    deliveryState: WearDeliveryState,
    logRequestPending: Boolean,
    onSessionChange: ((WearSession) -> WearSession) -> Unit,
    onLogSet: () -> Unit,
    onAdjustTimer: (Int) -> Unit,
    onStopTimer: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp <= 200
    val horizontalPadding = if (configuration.isScreenRound) 18.dp else 10.dp
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollState)
    val displayedTimerSeconds = rememberDisplayedTimerSeconds(session)

    LaunchedEffect(session.workoutId, session.exerciseId) {
        scrollState.scrollTo(0)
        focusRequester.requestFocus()
    }

    ScreenScaffold(
        scrollState = scrollState,
        contentPadding = PaddingValues(0.dp),
        timeText = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = rotaryBehavior,
                    focusRequester = focusRequester
                )
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = if (isCompact) 8.dp else 10.dp,
                    bottom = if (configuration.isScreenRound) 18.dp else 10.dp
                )
                .semantics { paneTitle = "Log ${session.exerciseName}" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = session.exerciseName.ifBlank { "Exercise" },
                color = Color.White,
                fontSize = if (isCompact) 15.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = if (isCompact) 17.sp else 19.sp,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = sessionSubtitle(session),
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (isCompact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DeliveryStatus(deliveryState)

            Spacer(modifier = Modifier.height(6.dp))

            TimerControls(
                session = session,
                displayedSeconds = displayedTimerSeconds,
                showCompletion = showTimerCompletion,
                compact = isCompact,
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
                onToggle = {
                    onSessionChange { latest ->
                        latest.copy(isWarmup = !latest.isWarmup)
                    }
                }
            )

            Button(
                onClick = onLogSet,
                enabled = session.canLog && !logRequestPending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        contentDescription = if (logRequestPending) {
                            "Saving set"
                        } else {
                            "Log set ${session.setNumber}"
                        }
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = when {
                        deliveryState.isSetLogPending && !deliveryState.isPhoneConnected -> "QUEUED"
                        logRequestPending -> "SAVING..."
                        else -> "LOG SET"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeliveryStatus(deliveryState: WearDeliveryState) {
    val status = when {
        deliveryState.lastError != null -> "PHONE UNAVAILABLE - WILL RETRY"
        !deliveryState.isPhoneConnected -> "CONNECTING TO PHONE..."
        deliveryState.isDraftPending -> "SYNCING CHANGES..."
        else -> null
    }
    if (status != null) {
        Text(
            text = status,
            color = if (deliveryState.lastError != null) {
                Color(0xFFFFB4AB)
            } else {
                Color.White.copy(alpha = 0.62f)
            },
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = status.lowercase(Locale.US)
                }
        )
    }
}

@Composable
private fun TimerControls(
    session: WearSession,
    displayedSeconds: Int,
    showCompletion: Boolean,
    compact: Boolean,
    onAdjustTimer: (Int) -> Unit,
    onStopTimer: () -> Unit
) {
    val timerCanBeControlled = session.timerRunning || session.timerRemainingSeconds > 0
    val timerText = when {
        displayedSeconds > 0 -> formatSeconds(displayedSeconds)
        session.timerRunning || showCompletion -> "DONE"
        else -> formatSeconds(session.restSeconds.coerceAtLeast(0))
    }
    val timerDescription = when {
        displayedSeconds > 0 -> "Rest timer, ${spokenDuration(displayedSeconds)} remaining"
        timerText == "DONE" -> "Rest timer complete"
        else -> "Rest after set, ${spokenDuration(session.restSeconds.coerceAtLeast(0))}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 7.dp, vertical = if (compact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timerText,
            color = MaterialTheme.colorScheme.primary,
            fontSize = if (compact) 20.sp else 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics {
                contentDescription = timerDescription
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            SmallPill(
                text = "-30",
                contentDescription = "Subtract 30 seconds from rest timer",
                enabled = timerCanBeControlled
            ) { onAdjustTimer(-30) }
            SmallPill(
                text = "+30",
                contentDescription = "Add 30 seconds to rest timer",
                enabled = timerCanBeControlled
            ) { onAdjustTimer(30) }
            SmallPill(
                text = "Stop",
                contentDescription = "Stop rest timer",
                enabled = timerCanBeControlled
            ) { onStopTimer() }
        }
    }
}

@Composable
private fun LogTypeFields(
    session: WearSession,
    onSessionChange: ((WearSession) -> WearSession) -> Unit
) {
    when (session.logType) {
        "WEIGHT_REPS" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) { delta ->
                onSessionChange { latest ->
                    latest.copy(weight = adjustNumber(latest.weight, delta, wholeNumber = false))
                }
            }
            NumberStepper("Reps", session.reps, "", 1f, wholeNumber = true) { delta ->
                onSessionChange { latest ->
                    latest.copy(reps = adjustNumber(latest.reps, delta, wholeNumber = true))
                }
            }
            RpeStepper(session.rpe) { delta ->
                onSessionChange { latest ->
                    latest.copy(rpe = adjustRpe(latest.rpe, delta))
                }
            }
        }
        "REPS_ONLY" -> {
            NumberStepper("Reps", session.reps, "", 1f, wholeNumber = true) { delta ->
                onSessionChange { latest ->
                    latest.copy(reps = adjustNumber(latest.reps, delta, wholeNumber = true))
                }
            }
            RpeStepper(session.rpe) { delta ->
                onSessionChange { latest ->
                    latest.copy(rpe = adjustRpe(latest.rpe, delta))
                }
            }
        }
        "DURATION" -> {
            DurationStepper("Time", session.duration) { deltaSeconds ->
                onSessionChange { latest ->
                    latest.copy(duration = adjustDuration(latest.duration, deltaSeconds))
                }
            }
        }
        "WEIGHT_DISTANCE" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) { delta ->
                onSessionChange { latest ->
                    latest.copy(weight = adjustNumber(latest.weight, delta, wholeNumber = false))
                }
            }
            NumberStepper(
                label = "Distance",
                value = session.distance,
                suffix = displayDistanceUnit(session.distanceUnit),
                step = 0.1f
            ) { delta ->
                onSessionChange { latest ->
                    latest.copy(distance = adjustNumber(latest.distance, delta, wholeNumber = false))
                }
            }
        }
        "DISTANCE_TIME" -> {
            NumberStepper(
                label = "Distance",
                value = session.distance,
                suffix = displayDistanceUnit(session.distanceUnit),
                step = 0.1f
            ) { delta ->
                onSessionChange { latest ->
                    latest.copy(distance = adjustNumber(latest.distance, delta, wholeNumber = false))
                }
            }
            DurationStepper("Time", session.duration) { deltaSeconds ->
                onSessionChange { latest ->
                    latest.copy(duration = adjustDuration(latest.duration, deltaSeconds))
                }
            }
        }
        "WEIGHT_TIME" -> {
            NumberStepper("Weight", session.weight, "lb", 5f) { delta ->
                onSessionChange { latest ->
                    latest.copy(weight = adjustNumber(latest.weight, delta, wholeNumber = false))
                }
            }
            DurationStepper("Time", session.duration) { deltaSeconds ->
                onSessionChange { latest ->
                    latest.copy(duration = adjustDuration(latest.duration, deltaSeconds))
                }
            }
        }
        "CALORIES_TIME" -> {
            NumberStepper("Calories", session.calories, "", 5f, wholeNumber = true) { delta ->
                onSessionChange { latest ->
                    latest.copy(calories = adjustNumber(latest.calories, delta, wholeNumber = true))
                }
            }
            DurationStepper("Time", session.duration) { deltaSeconds ->
                onSessionChange { latest ->
                    latest.copy(duration = adjustDuration(latest.duration, deltaSeconds))
                }
            }
        }
        else -> {
            Text(
                text = "This exercise type is not supported on the watch yet.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
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
    onAdjust: (Float) -> Unit
) {
    val displayValue = buildString {
        append(value.ifBlank { "0" })
        if (suffix.isNotBlank()) append(" ").append(suffix)
    }
    val spokenSuffix = when (suffix.lowercase(Locale.US)) {
        "lb" -> "pounds"
        "km" -> "kilometers"
        "m" -> "meters"
        "mi" -> "miles"
        "yd" -> "yards"
        "ft" -> "feet"
        else -> suffix
    }
    val stepDescription = if (wholeNumber || step % 1f == 0f) {
        step.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", step)
    }

    FieldRow(
        label = label,
        value = displayValue,
        valueDescription = listOf(value.ifBlank { "0" }, spokenSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        minusDescription = "Decrease $label by $stepDescription",
        plusDescription = "Increase $label by $stepDescription",
        onMinus = { onAdjust(-step) },
        onPlus = { onAdjust(step) }
    )
}

@Composable
private fun DurationStepper(
    label: String,
    value: String,
    onAdjust: (Int) -> Unit
) {
    val seconds = parseDuration(value)
    FieldRow(
        label = label,
        value = value.ifBlank { "00:00" },
        valueDescription = spokenDuration(seconds),
        minusDescription = "Decrease $label by 15 seconds",
        plusDescription = "Increase $label by 15 seconds",
        onMinus = { onAdjust(-15) },
        onPlus = { onAdjust(15) }
    )
}

@Composable
private fun RpeStepper(
    value: String,
    onAdjust: (Float) -> Unit
) {
    val parsedValue = value.toFloatOrNull()
    FieldRow(
        label = "RPE (optional)",
        value = parsedValue?.let(::formatRpe) ?: "-",
        valueDescription = parsedValue?.let { "${formatRpe(it)} out of 10" } ?: "Not set",
        minusDescription = "Decrease RPE by 0.5",
        plusDescription = "Increase RPE by 0.5",
        onMinus = { onAdjust(-0.5f) },
        onPlus = { onAdjust(0.5f) }
    )
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    valueDescription: String,
    minusDescription: String,
    plusDescription: String,
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
        SmallRoundButton(
            text = "−",
            contentDescription = minusDescription,
            onClick = onMinus
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    stateDescription = "$label, $valueDescription"
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 10.sp,
                maxLines = 1
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SmallRoundButton(
            text = "+",
            contentDescription = plusDescription,
            onClick = onPlus
        )
    }
}

@Composable
private fun WarmupToggle(
    checked: Boolean,
    onToggle: () -> Unit
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
                contentDescription = "Warmup set"
            },
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
private fun SmallRoundButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}

@Composable
private fun SmallPill(
    text: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(40.dp)
            .semantics { this.contentDescription = contentDescription },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun rememberDisplayedTimerSeconds(session: WearSession): Int {
    var displayedSeconds by remember(
        session.workoutId,
        session.exerciseId
    ) {
        mutableIntStateOf(session.timerRemainingSeconds.coerceAtLeast(0))
    }

    LaunchedEffect(
        session.timerRunning,
        session.timerRemainingSeconds,
        session.updatedAt
    ) {
        val snapshotAgeSeconds = if (session.timerRunning && session.updatedAt > 0L) {
            ((System.currentTimeMillis() - session.updatedAt).coerceAtLeast(0L) / 1_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else {
            0
        }
        val anchorSeconds = (
            session.timerRemainingSeconds.coerceAtLeast(0) - snapshotAgeSeconds
        ).coerceAtLeast(0)
        displayedSeconds = anchorSeconds

        if (!session.timerRunning || anchorSeconds == 0) return@LaunchedEffect

        val anchorElapsedRealtime = SystemClock.elapsedRealtime()
        while (displayedSeconds > 0) {
            val elapsedMillis = SystemClock.elapsedRealtime() - anchorElapsedRealtime
            val remaining = (anchorSeconds - (elapsedMillis / 1_000L).toInt()).coerceAtLeast(0)
            if (displayedSeconds != remaining) displayedSeconds = remaining
            if (remaining == 0) break

            val delayUntilNextSecond = 1_000L - (elapsedMillis % 1_000L)
            delay(delayUntilNextSecond.coerceAtLeast(50L))
        }
    }

    return displayedSeconds
}

private fun sessionSubtitle(session: WearSession): String = buildString {
    append("SET ").append(session.setNumber.coerceAtLeast(1))
    if (session.targetMuscle.isNotBlank()) {
        append("  ")
        append(session.targetMuscle.uppercase(Locale.US))
    }
}

private fun displayDistanceUnit(unit: String): String = when (unit.uppercase(Locale.US)) {
    "METERS" -> "m"
    "KILOMETERS" -> "km"
    "YARDS" -> "yd"
    "FEET" -> "ft"
    "MILES" -> "mi"
    "STEPS" -> "steps"
    "FLOORS" -> "floors"
    else -> unit.lowercase(Locale.US)
}

private fun adjustNumber(value: String, delta: Float, wholeNumber: Boolean): String {
    val updated = ((value.toFloatOrNull() ?: 0f) + delta).coerceAtLeast(0f)
    return if (wholeNumber || updated % 1f == 0f) {
        updated.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", updated)
    }
}

private fun adjustRpe(value: String, delta: Float): String {
    val current = value.toFloatOrNull()
    if (current == null) {
        return if (delta > 0f) formatRpe(8f) else ""
    }
    val updated = (current + delta).coerceIn(0f, 10f)
    // Zero represents the optional/unset state rather than forcing an RPE onto every set.
    return if (updated == 0f) "" else formatRpe(updated)
}

private fun formatRpe(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun adjustDuration(value: String, deltaSeconds: Int): String =
    formatSeconds((parseDuration(value) + deltaSeconds).coerceAtLeast(0))

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
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val remainder = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }
}

private fun spokenDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val remainder = safeSeconds % 60
    return buildList {
        if (hours > 0) add("$hours ${if (hours == 1) "hour" else "hours"}")
        if (minutes > 0) add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
        if (remainder > 0 || isEmpty()) {
            add("$remainder ${if (remainder == 1) "second" else "seconds"}")
        }
    }.joinToString(" ")
}

private fun vibrateWatch(context: Context, durationMillis: Long = 500) {
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}
