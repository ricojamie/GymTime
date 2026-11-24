package com.example.gymtime.ui.summary.preview

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.summary.PostWorkoutSummaryUiState
import com.example.gymtime.ui.summary.WorkoutSummaryStats
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import kotlinx.coroutines.delay
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getNumberInstance() }
    val canExport = state.stats != null && !state.isLoading && !state.isSaving && !state.isExporting

    // The app shell consumes system/IME insets. This scaffold owns only its footer space.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        bottomBar = {
            Surface(
                color = surfaces.glassDockFill,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IronLogDivider(insetStart = 0.dp, color = surfaces.glassDockRimTop)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
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
                text = "SESSION RECAP",
                style = IronLogType.technicalEyebrow(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            IronLogCard(
                highlighted = true,
                spotlight = true,
                showBorder = true,
                shape = IronLogRadius.PlateShape,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.staggeredEntranceOnce("summary", 0)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IronLogLedPip(color = surfaces.ledGlow, size = 6.dp, pulsing = true)
                            Text(
                                text = "SESSION COMPLETE",
                                style = IronLogType.technicalEyebrow(),
                                color = surfaces.accent
                            )
                        }
                        Text(
                            text = "Workout complete",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    IronLogMascotBadge(
                        size = 44.dp,
                        style = IronLogMascotStyle.HERO_CREST,
                        showGlow = true
                    )
                }
                Text(
                    text = state.stats?.workoutName ?: "Your workout is saved",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                state.stats?.let { stats ->
                    IronLogRecessedWell(
                        active = true,
                        accentColor = surfaces.accent,
                        shape = IronLogRadius.ControlShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "TOTAL VOLUME",
                                    style = IronLogType.technicalEyebrow(),
                                    color = colors.onSurfaceVariant
                                )
                                OdometerText(
                                    text = "${numberFormat.format(stats.totalVolume.toLong())} lb",
                                    style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge).copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = surfaces.accent
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "DURATION",
                                    style = IronLogType.technicalEyebrow(),
                                    color = colors.onSurfaceVariant
                                )
                                OdometerText(
                                    text = stats.duration,
                                    style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                } ?: Text(
                    text = "Another session in the books.",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .staggeredEntranceOnce("summary", 1),
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        SummaryStatPlate(
                            label = "WORKING SETS",
                            value = stats.totalSets.toString(),
                            accented = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        SummaryStatPlate(
                            label = "EXERCISES",
                            value = stats.exerciseCount.toString(),
                            accented = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    if (stats.muscleGroups.isNotEmpty()) {
                        Column(
                            modifier = Modifier.staggeredEntranceOnce("summary", 1),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                    shape = IronLogRadius.PlateShape,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.staggeredEntranceOnce("summary", 2)
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
                shape = IronLogRadius.PlateShape,
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
                modifier = Modifier.staggeredEntranceOnce("summary", 3)
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
                        RatingStarKeycap(
                            rating = rating,
                            selectedRating = state.selectedRating,
                            enabled = !state.isSaving,
                            onSelect = {
                                haptics.tick()
                                onRating(rating)
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNote,
                    enabled = !state.isSaving,
                    shape = IronLogRadius.KeycapShape,
                    label = { Text("Session note (optional)") },
                    placeholder = { Text("Energy, a cue to remember, or anything else…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = surfaces.wellFill,
                        unfocusedContainerColor = surfaces.wellFill,
                        focusedBorderColor = surfaces.accent,
                        unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.65f)
                    ),
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
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(IronMotion.orSnap(reducedMotion, IronMotion.EffectDefault)) +
                    expandVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize)),
                exit = fadeOut(IronMotion.orSnap(reducedMotion, IronMotion.EffectFast)) +
                    shrinkVertically(IronMotion.orSnap(reducedMotion, IronMotion.SpatialLayoutSize))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CardPadding)) {
                    Text(
                        text = "Totals include completed working sets. Warmups and unfinished sets stay in your history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    state.weeklyVolume?.let { volume ->
                        IronLogGroupedCard(showBorder = true, shape = IronLogRadius.PlateShape) {
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
}

@Composable
private fun SummaryStatPlate(
    label: String,
    value: String,
    accented: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    IronLogCard(
        modifier = modifier,
        highlighted = accented,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = IronLogType.technicalEyebrow(),
            color = colors.onSurfaceVariant
        )
        OdometerText(
            text = value,
            style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineMedium).copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (accented) surfaces.accent else colors.onSurface
        )
    }
}

@Composable
private fun RatingStarKeycap(
    rating: Int,
    selectedRating: Int?,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
    val isFilled = (selectedRating ?: 0) >= rating
    val isSelected = selectedRating == rating
    val starScale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(pressedScale = 0.92f, highlightAlpha = 0.10f, cornerRadius = IronLogRadius.Keycap)
    }

    LaunchedEffect(selectedRating, reducedMotion) {
        if (!reducedMotion && (selectedRating ?: 0) >= rating) {
            if (rating > 1) delay((rating - 1) * 32L)
            starScale.snapTo(1.24f)
            starScale.animateTo(1f, IronMotion.RewardPop)
        } else {
            starScale.snapTo(1f)
        }
    }

    val fillBrush = remember(isFilled, surfaces) {
        if (isFilled) {
            Brush.verticalGradient(listOf(surfaces.plateElevatedTop, surfaces.plateElevatedBottom))
        } else {
            Brush.verticalGradient(listOf(surfaces.keycapTop, surfaces.keycapBottom))
        }
    }
    val borderStroke = remember(isFilled, surfaces) {
        IronLogBorder.specularRimStroke(surfaces, highlighted = isFilled, width = IronLogBorder.Hairline)
    }

    Box(
        modifier = Modifier
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .widthIn(min = 56.dp)
            .graphicsLayer {
                val s = starScale.value
                scaleX = s
                scaleY = s
            }
            .clip(IronLogRadius.KeycapShape)
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = pressIndication,
                onClick = onSelect
            )
            .background(fillBrush)
            .border(borderStroke, IronLogRadius.KeycapShape)
            .semantics { contentDescription = "Workout rating $rating out of 5" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isFilled) surfaces.accent else colors.onSurfaceVariant
            )
            Text(
                text = rating.toString(),
                style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                fontWeight = if (isFilled) FontWeight.Bold else FontWeight.Medium,
                color = if (isFilled) colors.onSurface else colors.onSurfaceVariant
            )
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
