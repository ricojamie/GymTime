package com.example.gymtime.ui.summary.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.summary.PostWorkoutSummaryUiState
import com.example.gymtime.ui.summary.WorkoutSummaryStats
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
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
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    val canExport = state.stats != null && !state.isLoading && !state.isSaving && !state.isExporting

    // The app shell consumes system/IME insets. This scaffold owns only its footer space.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        bottomBar = {
            Surface(
                color = colors.surfaceContainerLow,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IronLogDivider(insetStart = 0.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = IronLogSpace.ScreenHorizontal,
                                vertical = IronLogSpace.ItemGap
                            ),
                        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        state.saveError?.let { error ->
                            Text(
                                text = error,
                                color = colors.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IronLogPrimaryButton(
                            text = if (state.isSaving) "Saving…" else "Done",
                            onClick = onDone,
                            enabled = !state.isSaving && state.stats != null,
                            loading = state.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = onSkip,
                            enabled = !state.isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Skip feedback")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(IronLogSpace.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CardPadding)
        ) {
            Text(
                text = "Session recap",
                style = IronLogType.sectionEyebrow(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            IronLogCard(
                highlighted = true,
                showBorder = true,
                shape = IronLogRadius.CardShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(colors.primaryContainer.copy(alpha = .55f), colors.surface)
                            )
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Workout complete",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = state.stats?.workoutName ?: "Your workout is saved",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Another session in the books.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            when {
                state.isLoading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(IronLogSpace.CardPadding),
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Loading your session…")
                }
                state.loadError != null -> IronLogCard(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                    borderColor = colors.error.copy(alpha = 0.35f),
                    showBorder = true,
                    shape = IronLogRadius.CardShape,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(state.loadError, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Try again", color = colors.onErrorContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> state.stats?.let { stats ->
                    IronLogGroupedCard(showBorder = true) {
                        IronLogMetricRow(
                            metrics = listOf(
                                IronLogMetric(
                                    label = "Working sets",
                                    value = stats.totalSets.toString(),
                                    emphasized = true
                                ),
                                IronLogMetric(
                                    label = "Exercises",
                                    value = stats.exerciseCount.toString()
                                )
                            ),
                            modifier = Modifier.padding(IronLogSpace.CardPadding)
                        )
                        IronLogDivider(
                            insetStart = IronLogSpace.CardPadding,
                            insetEnd = IronLogSpace.CardPadding
                        )
                        IronLogMetricRow(
                            metrics = listOf(
                                IronLogMetric(
                                    label = "Duration",
                                    value = stats.duration
                                ),
                                IronLogMetric(
                                    label = "Volume",
                                    value = "${numberFormat.format(stats.totalVolume.toLong())} lb"
                                )
                            ),
                            modifier = Modifier.padding(IronLogSpace.CardPadding)
                        )
                    }
                    if (stats.muscleGroups.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IronLogSectionHeader(title = "Muscles trained")
                            Text(
                                text = stats.muscleGroups.joinToString(" · "),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = "No completed working sets this session.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            state.recap?.takeIf { it.isNotBlank() }?.let { recap ->
                IronLogCard(
                    showBorder = true,
                    shape = IronLogRadius.CardShape,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IronLogSectionHeader(title = "Highlight")
                    Text(
                        text = recap,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface
                    )
                }
            }
            IronLogCard(
                showBorder = true,
                shape = IronLogRadius.CardShape,
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "How did it feel?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Optional · 1 = tough day, 5 = felt great",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    (1..5).forEach { rating ->
                        FilterChip(
                            selected = state.selectedRating == rating,
                            onClick = { onRating(rating) },
                            enabled = !state.isSaving,
                            shape = IronLogRadius.ControlShape,
                            label = {
                                Text(
                                    text = rating.toString(),
                                    style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = IronLogSpace.TouchTargetMin)
                                .widthIn(min = IronLogSpace.TouchTargetMin)
                                .semantics { contentDescription = "Workout rating $rating out of 5" }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNote,
                    enabled = !state.isSaving,
                    shape = IronLogRadius.ControlShape,
                    label = { Text("Session note (optional)") },
                    placeholder = { Text("Energy, a cue to remember, or anything else…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    supportingText = {
                        Text(
                            text = "${state.note.length}/2000",
                            style = IronLogType.tabular(MaterialTheme.typography.bodySmall)
                        )
                    }
                )
            }
            TextButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text(if (showDetails) "Hide details & sharing" else "Details & sharing")
            }
            if (showDetails) {
                Text(
                    text = "Totals include completed working sets. Warmups and unfinished sets stay in your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                state.weeklyVolume?.let { volume ->
                    IronLogGroupedCard(showBorder = true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(IronLogSpace.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                        ) {
                            IronLogSectionHeader(title = "Weekly volume")
                            IronLogMetricRow(
                                metrics = listOf(
                                    IronLogMetric(
                                        label = "This week",
                                        value = "${numberFormat.format(volume.thisWeek.toLong())} lb",
                                        emphasized = true
                                    ),
                                    IronLogMetric(
                                        label = "This session",
                                        value = "${numberFormat.format(volume.sessionContribution.toLong())} lb"
                                    )
                                )
                            )
                            Text(
                                text = if (volume.lastWeek > 0f) {
                                    "${numberFormat.format(volume.lastWeek.toLong())} lb last week"
                                } else {
                                    "No previous week to compare yet."
                                },
                                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
                state.stats?.workoutNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IronLogSectionHeader(title = "Workout note")
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                ) {
                    IronLogSecondaryButton(
                        text = "Share workout",
                        onClick = onShare,
                        enabled = canExport,
                        outlined = true,
                        leadingIcon = Icons.Default.Share,
                        modifier = Modifier.weight(1f)
                    )
                    IronLogSecondaryButton(
                        text = "Copy text",
                        onClick = onCopy,
                        enabled = canExport,
                        outlined = true,
                        leadingIcon = Icons.Default.ContentCopy,
                        modifier = Modifier.weight(1f)
                    )
                }
                optionalSetup()
            }
        }
    }
}

@Preview(name = "Summary dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Summary light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SummaryPreview() {
    val darkMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    IronLogTheme(darkMode = darkMode) {
        PreviewPostWorkoutSummaryContent(
            state = PostWorkoutSummaryUiState(
                stats = WorkoutSummaryStats(
                    duration = "48m",
                    totalVolume = 12_450f,
                    totalSets = 14,
                    exerciseCount = 5,
                    muscleGroups = listOf("Chest", "Shoulders", "Triceps"),
                    workoutName = "Push day"
                ),
                isLoading = false,
                selectedRating = 4,
                recap = "Logged 14 working sets across 5 exercises."
            ),
            onRating = {},
            onNote = {},
            onDone = {},
            onSkip = {},
            onRetry = {},
            onShare = {},
            onCopy = {}
        )
    }
}
