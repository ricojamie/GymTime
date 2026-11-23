package com.example.gymtime.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.gymtime.domain.share.ShareableWorkout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ShareImagePalette(
    val background: Int,
    val card: Int,
    val accent: Int,
    val accentSoft: Int,
    val textPrimary: Int,
    val textMuted: Int,
    val onAccent: Int
)

@Singleton
class WorkoutShareImageGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun generate(workout: ShareableWorkout, palette: ShareImagePalette): Uri = withContext(Dispatchers.IO) {
        val width = 1080
        val layout = measureLayout(workout, width)
        val dir = File(context.cacheDir, "shared_workouts").apply { mkdirs() }
        val file = File(dir, "ironlog-workout-${workout.date.time}.png")
        pruneSharedImages(dir, currentFileName = file.name)

        val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            drawWorkout(canvas, workout, width, layout, palette)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun pruneSharedImages(
        dir: File,
        currentFileName: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val files = dir.listFiles()?.filter { it.isFile && it.name != currentFileName } ?: return
        val cutoffMs = nowMs - MAX_CACHE_AGE_MS
        val recent = mutableListOf<File>()
        for (file in files) {
            if (file.lastModified() < cutoffMs) {
                file.delete()
            } else {
                recent += file
            }
        }
        if (recent.size >= MAX_CACHED_IMAGES) {
            recent.sortedByDescending { it.lastModified() }
                .drop(MAX_CACHED_IMAGES - 1)
                .forEach { it.delete() }
        }
    }

    private fun measureLayout(workout: ShareableWorkout, width: Int): ShareImageLayout {
        val contentWidth = width - (OUTER_PADDING * 2)
        var height = OUTER_PADDING + 148 + 40 + 160 + 32
        var wrappedRecapLines: List<String> = emptyList()
        workout.recap?.takeIf { it.isNotBlank() }?.let { recap ->
            wrappedRecapLines = wrapText(
                text = recap,
                paint = textPaint(30f, 0, Typeface.NORMAL),
                maxWidth = (contentWidth - 64).toFloat()
            )
            height += 64 + (wrappedRecapLines.size * 42) + 32
        }
        workout.exercises.forEach { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) return@forEach
            height += 84 + (workingSets.size * 58) + 32
        }
        height += 92 + OUTER_PADDING
        return ShareImageLayout(
            contentWidth = contentWidth,
            height = height,
            wrappedRecapLines = wrappedRecapLines
        )
    }

    private fun drawWorkout(
        canvas: Canvas,
        workout: ShareableWorkout,
        width: Int,
        layout: ShareImageLayout,
        palette: ShareImagePalette
    ) {
        val height = layout.height
        canvas.drawColor(palette.background)
        val contentLeft = OUTER_PADDING.toFloat()
        val contentRight = (width - OUTER_PADDING).toFloat()
        val contentWidth = contentRight - contentLeft
        var y = OUTER_PADDING.toFloat()

        val titlePaint = textPaint(54f, palette.textPrimary, Typeface.BOLD)
        val labelPaint = textPaint(24f, palette.textMuted, Typeface.BOLD)
        val bodyPaint = textPaint(34f, palette.textPrimary, Typeface.NORMAL)
        val strongPaint = textPaint(38f, palette.textPrimary, Typeface.BOLD)
        val accentPaint = textPaint(36f, palette.accent, Typeface.BOLD)
        val recapPaint = textPaint(30f, palette.textPrimary, Typeface.NORMAL)
        val prTextPaint = textPaint(24f, palette.onAccent, Typeface.BOLD)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val tempRect = RectF()

        canvas.drawText("Iron Log", contentLeft, y + 56f, titlePaint)
        canvas.drawText(
            WorkoutShareFormatter.formatShareDate(workout.date),
            contentLeft,
            y + 98f,
            labelPaint
        )
        y += 148f

        val summaryTop = y
        drawRoundRect(canvas, fillPaint, tempRect, contentLeft, summaryTop, contentRight, summaryTop + 160f, palette.card, 28f)
        val numberFormat = NumberFormat.getNumberInstance(Locale.US)
        val duration = workout.durationMinutes?.let { formatDuration(it) } ?: "--"
        drawStat(canvas, "DURATION", duration, contentLeft + 36f, summaryTop + 48f, labelPaint, accentPaint)
        drawStat(
            canvas,
            "VOLUME",
            "${numberFormat.format(workout.totalVolume.toLong())} lbs",
            contentLeft + contentWidth * 0.36f,
            summaryTop + 48f,
            labelPaint,
            strongPaint
        )
        drawStat(
            canvas,
            "SETS",
            "${workout.totalWorkingSets}",
            contentLeft + contentWidth * 0.72f,
            summaryTop + 48f,
            labelPaint,
            strongPaint
        )
        y += 200f

        if (layout.wrappedRecapLines.isNotEmpty()) {
            val recapHeight = 64f + (layout.wrappedRecapLines.size * 42f)
            drawRoundRect(canvas, fillPaint, tempRect, contentLeft, y, contentRight, y + recapHeight, palette.card, 24f)
            canvas.drawText("WORKOUT RECAP", contentLeft + 32f, y + 36f, labelPaint)
            layout.wrappedRecapLines.forEachIndexed { index, line ->
                canvas.drawText(line, contentLeft + 32f, y + 80f + (index * 42f), recapPaint)
            }
            y += recapHeight + 32f
        }

        workout.exercises.forEach { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) return@forEach

            val cardHeight = 84f + (workingSets.size * 58f)
            drawRoundRect(canvas, fillPaint, tempRect, contentLeft, y, contentRight, y + cardHeight, palette.card, 24f)
            canvas.drawText(
                ellipsize(exercise.name, strongPaint, contentWidth - 220f),
                contentLeft + 32f,
                y + 50f,
                strongPaint
            )
            canvas.drawText(
                ellipsize(exercise.targetMuscle, labelPaint, 180f),
                contentRight - 190f,
                y + 48f,
                labelPaint
            )

            var setY = y + 104f
            workingSets.forEachIndexed { index, set ->
                if (set.isPersonalRecord) {
                    drawRoundRect(
                        canvas,
                        fillPaint,
                        tempRect,
                        contentLeft + 22f,
                        setY - 36f,
                        contentRight - 22f,
                        setY + 16f,
                        palette.accentSoft,
                        12f
                    )
                }
                canvas.drawText("${index + 1}", contentLeft + 40f, setY, labelPaint)
                canvas.drawText(
                    WorkoutShareFormatter.formatSet(set, multiplicationSymbol = "x", emptyFallback = "-"),
                    contentLeft + 96f,
                    setY,
                    bodyPaint
                )
                if (set.isPersonalRecord) {
                    drawRoundRect(
                        canvas,
                        fillPaint,
                        tempRect,
                        contentRight - 112f,
                        setY - 34f,
                        contentRight - 42f,
                        setY + 8f,
                        palette.accent,
                        9f
                    )
                    canvas.drawText("PR", contentRight - 93f, setY - 5f, prTextPaint)
                }
                setY += 58f
            }
            y += cardHeight + 32f
        }

        canvas.drawText("Shared from Iron Log", contentLeft, height - OUTER_PADDING.toFloat(), labelPaint)
    }

    private fun drawStat(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value, x, y + 58f, valuePaint)
    }

    private fun drawRoundRect(
        canvas: Canvas,
        paint: Paint,
        rect: RectF,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        radius: Float
    ) {
        paint.color = color
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun textPaint(size: Float, color: Int, typefaceStyle: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
        }
    }

    private fun formatDuration(minutes: Int): String {
        val safeMinutes = minutes.coerceAtLeast(0)
        val h = safeMinutes / 60
        val m = safeMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var candidate = text
        while (candidate.length > 1 && paint.measureText("$candidate...") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return "$candidate..."
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.take(MAX_RECAP_LINES).mapIndexed { index, line ->
            if (index == MAX_RECAP_LINES - 1 && lines.size > MAX_RECAP_LINES) {
                ellipsize("$line...", paint, maxWidth)
            } else {
                line
            }
        }
    }

    private data class ShareImageLayout(
        val contentWidth: Int,
        val height: Int,
        val wrappedRecapLines: List<String> = emptyList()
    )

    private companion object {
        private const val OUTER_PADDING = 64
        private const val MAX_RECAP_LINES = 3
        private const val MAX_CACHED_IMAGES = 5
        private const val MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
