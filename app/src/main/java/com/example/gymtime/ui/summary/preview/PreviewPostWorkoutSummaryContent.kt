package com.example.gymtime.ui.summary.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.summary.PostWorkoutSummaryUiState
import com.example.gymtime.ui.summary.WorkoutSummaryStats
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LoggerPreviewTheme
import java.text.NumberFormat

/** Plain rendering: no navigation, persistence, lifecycle, or model setup dependencies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewPostWorkoutSummaryContent(
    state: PostWorkoutSummaryUiState,
    onRating: (Int) -> Unit,
    onNote: (String) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    optionalSetup: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getNumberInstance() }

    // The app shell consumes system/IME insets. This scaffold owns only its footer space.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        bottomBar = {
            Surface(color = colors.surface, contentColor = colors.onSurface, shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    state.saveError?.let { error ->
                        Text(error, color = colors.error, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = onDone, enabled = !state.isSaving && state.stats != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = action.onFill, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isSaving) "Saving…" else "Done", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onSkip, enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Skip feedback")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Session recap", style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            Surface(
                color = colors.surface, contentColor = colors.onSurface,
                shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp,
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(
                        listOf(colors.primaryContainer.copy(alpha = .65f), colors.surface)
                    )).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Workout complete", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                    Text(state.stats?.workoutName ?: "Your workout is saved", style = MaterialTheme.typography.titleMedium)
                    Text("Another session in the books.", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            when {
                state.isLoading -> Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Loading your session…")
                }
                state.loadError != null -> Surface(
                    color = colors.errorContainer, contentColor = colors.onErrorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(state.loadError, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    }
                }
                else -> state.stats?.let { stats ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric(stats.totalSets.toString(), "Working sets", Modifier.weight(1f))
                        SummaryMetric(stats.exerciseCount.toString(), "Exercises", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric(stats.duration, "Duration", Modifier.weight(1f))
                        SummaryMetric("${numberFormat.format(stats.totalVolume.toLong())} lb", "Volume", Modifier.weight(1f))
                    }
                    if (stats.muscleGroups.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.muscleGroups.forEach { muscle ->
                                Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer,
                                    shape = RoundedCornerShape(12.dp)) {
                                    Text(muscle, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    } else {
                        Text("No completed working sets this session.", color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.recap?.takeIf { it.isNotBlank() }?.let { recap ->
                Text(recap, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
            Surface(color = colors.surface, contentColor = colors.onSurface, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("How did it feel?", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text("Optional · 1 = tough day, 5 = felt great", style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { rating ->
                            FilterChip(
                                selected = state.selectedRating == rating,
                                onClick = { onRating(rating) }, enabled = !state.isSaving,
                                label = { Text(rating.toString(), fontWeight = FontWeight.Bold) },
                                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                                    .semantics { contentDescription = "Workout rating $rating out of 5" }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.note, onValueChange = onNote, enabled = !state.isSaving,
                        label = { Text("Session note (optional)") },
                        placeholder = { Text("Energy, a cue to remember, or anything else…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        supportingText = { Text("${state.note.length}/2000") }
                    )
                }
            }
            TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (showDetails) "Hide details & sharing" else "Details & sharing")
            }
            if (showDetails) {
                Text("Totals include completed working sets. Warmups and unfinished sets stay in your history.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                state.weeklyVolume?.let { volume ->
                    Surface(color = colors.surfaceContainerLow, contentColor = colors.onSurface,
                        shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Weekly volume", style = MaterialTheme.typography.titleSmall)
                            Text("${numberFormat.format(volume.thisWeek.toLong())} lb this week",
                                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("${numberFormat.format(volume.sessionContribution.toLong())} lb session volume",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Text(if (volume.lastWeek > 0f) {
                                "${numberFormat.format(volume.lastWeek.toLong())} lb last week"
                            } else "No previous week to compare yet.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                state.stats?.workoutNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text("Workout note", style = MaterialTheme.typography.titleSmall)
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShare, enabled = state.stats != null && !state.isSaving,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Share workout") }
                    OutlinedButton(onClick = onCopy, enabled = state.stats != null && !state.isSaving,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy text") }
                }
                optionalSetup()
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, color = colors.surfaceContainerLow, contentColor = colors.onSurface,
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Preview(name = "Summary dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Summary light", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SummaryPreview() {
    val darkMode = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    IronLogTheme(darkMode = darkMode) {
        LoggerPreviewTheme {
            PreviewPostWorkoutSummaryContent(
                state = PostWorkoutSummaryUiState(
                    stats = WorkoutSummaryStats("48m", 12_450f, 14, 5, listOf("Chest", "Shoulders", "Triceps"), "Push day"),
                    isLoading = false, selectedRating = 4, recap = "Logged 14 working sets across 5 exercises."
                ), onRating = {}, onNote = {}, onDone = {}, onSkip = {}, onRetry = {}, onShare = {}, onCopy = {}
            )
        }
    }
}
