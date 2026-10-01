package com.example.gymtime.ui.exercise.preview

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, colors.primary)
    ) {
        // Normal set rows start at 52 dp; PRs add only 10%, with room to grow for notes or larger text.
        Row(Modifier.heightIn(min = 52.dp * 1.1f), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClickLabel = "Edit personal record set $number") { onEdit(set) }.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrophyMedallion()
                Column(Modifier.weight(1f)) {
                    Text("PERSONAL RECORD", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(previewSetSummary(set), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    set.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    set.rpe?.let { Text("RPE $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for set $number") }
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
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, colors.primary),
        shadowElevation = 12.dp
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrophyMedallion()
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("NEW PR!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(setSummary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(exerciseName, style = MaterialTheme.typography.bodyMedium)
                Text(recordLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss PR celebration") }
        }
    }
}

@Composable
private fun TrophyMedallion() {
    val accent = LocalLoggerActionColors.current
    Surface(shape = CircleShape, color = accent.fill, contentColor = accent.onFill) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp))
        }
    }
}

private data class ConfettiPiece(val x: Float, val drift: Float, val fall: Float, val spin: Float)

/** Decorative, finite animation. Compose's duration scale also honors disabled system animations. */
@Composable
fun PrConfettiBurst(eventId: Long, startedAt: Long, modifier: Modifier = Modifier) {
    val progress = remember(eventId) { Animatable(0f) }
    val pieces = remember(eventId) {
        val random = Random(eventId)
        List(28) { ConfettiPiece(random.nextFloat(), random.nextFloat() - .5f, .3f + random.nextFloat() * .4f, random.nextFloat() * 540f) }
    }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    LaunchedEffect(eventId) {
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val fraction = (elapsed / 1_100f).coerceIn(0f, 1f)
        progress.snapTo(fraction)
        if (fraction < 1f) progress.animateTo(1f, tween((1_100L - elapsed).toInt(), easing = LinearEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val phase = progress.value
        if (phase >= 1f) return@Canvas
        pieces.forEachIndexed { index, piece ->
            val center = Offset(
                size.width * (piece.x + piece.drift * phase * .4f),
                size.height * (piece.fall * phase * phase) + sin(phase * 3.14f) * 40.dp.toPx()
            )
            rotate(piece.spin * phase, center) {
                drawRect(colors[index % colors.size].copy(alpha = 1f - phase), center, Size(5.dp.toPx(), 10.dp.toPx()))
            }
        }
    }
}
