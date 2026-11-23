package com.example.gymtime.ui.exercise.preview

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PrSetRow(
    set: WorkoutSet,
    number: Int,
    recordLabels: List<String>,
    onEdit: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onNote: (WorkoutSet) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    IronLogCard(
        modifier = Modifier.fillMaxWidth(),
        highlighted = true,
        showBorder = true,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        borderColor = colors.primary,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Normal set rows start at 52 dp; PRs add only 10%, with room to grow for notes or larger text.
        Row(
            modifier = Modifier.heightIn(min = 52.dp * 1.1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Edit personal record set $number") { onEdit(set) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                TrophyMedallion()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogStatusChip(
                            label = "PR",
                            tone = IronLogStatusTone.PR
                        )
                        Text(
                            text = "PERSONAL RECORD",
                            style = IronLogType.sectionEyebrow(),
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = previewSetSummary(set),
                        style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    set.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    set.rpe?.let {
                        Text(
                            text = "RPE $it",
                            style = IronLogType.tabular(MaterialTheme.typography.bodySmall)
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(Icons.Default.MoreVert, "Options for set $number")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text(
                        "Set $number${recordLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ").orEmpty()}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    DropdownMenuItem(text = { Text("Edit set") }, onClick = { menu = false; onEdit(set) })
                    DropdownMenuItem(text = { Text("Set note") }, onClick = { menu = false; onNote(set) })
                    DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete(set) })
                }
            }
        }
    }
}

@Composable
fun PrCelebrationBanner(
    exerciseName: String,
    setSummary: String,
    recordLabels: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = IronLogRadius.CardShape,
        border = BorderStroke(IronLogBorder.Hairline, colors.primary),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrophyMedallion()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IronLogStatusChip(label = "PR", tone = IronLogStatusTone.PR)
                    Text("NEW PR!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Text(
                    text = setSummary,
                    style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(exerciseName, style = MaterialTheme.typography.bodyMedium)
                if (recordLabels.isNotEmpty()) {
                    Text(recordLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(Icons.Default.Close, "Dismiss PR celebration")
            }
        }
    }
}

@Composable
private fun TrophyMedallion() {
    val accent = LocalLoggerActionColors.current
    Surface(shape = CircleShape, color = accent.fill, contentColor = accent.onFill) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(26.dp))
        }
    }
}

private data class ConfettiPiece(val x: Float, val drift: Float, val fall: Float, val spin: Float)

/** Decorative, finite animation. Compose's duration scale also honors disabled system animations. */
@Composable
fun PrConfettiBurst(eventId: Long, startedAt: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember(eventId) { Animatable(0f) }
    val pieces = remember(eventId) {
        val random = Random(eventId)
        List(28) {
            ConfettiPiece(
                x = random.nextFloat(),
                drift = random.nextFloat() - 0.5f,
                fall = 0.3f + random.nextFloat() * 0.4f,
                spin = random.nextFloat() * 540f
            )
        }
    }
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary)
    }
    LaunchedEffect(eventId) {
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val fraction = (elapsed / 1_100f).coerceIn(0f, 1f)
        progress.snapTo(fraction)
        if (fraction < 1f) progress.animateTo(1f, tween((1_100L - elapsed).toInt(), easing = LinearEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val phase = progress.value
        if (phase >= 1f) return@Canvas
        val bouncePx = 40.dp.toPx()
        val rectWidthPx = 5.dp.toPx()
        val rectHeightPx = 10.dp.toPx()
        val halfWidthPx = rectWidthPx / 2f
        val halfHeightPx = rectHeightPx / 2f
        val confettiSize = Size(rectWidthPx, rectHeightPx)
        val wave = sin(phase * 3.14f) * bouncePx
        val alpha = 1f - phase
        pieces.forEachIndexed { index, piece ->
            val center = Offset(
                size.width * (piece.x + piece.drift * phase * 0.4f),
                size.height * (piece.fall * phase * phase) + wave
            )
            rotate(piece.spin * phase, center) {
                drawRect(
                    color = colors[index % colors.size].copy(alpha = alpha),
                    topLeft = Offset(center.x - halfWidthPx, center.y - halfHeightPx),
                    size = confettiSize
                )
            }
        }
    }
}
