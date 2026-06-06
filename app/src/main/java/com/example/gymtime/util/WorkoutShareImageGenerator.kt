package com.example.gymtime.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.gymtime.domain.share.ShareableExercise
import com.example.gymtime.domain.share.ShareableSet
import com.example.gymtime.domain.share.ShareableWorkout
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
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
    @ApplicationContext private val context: Context
) {
    fun generate(workout: ShareableWorkout, palette: ShareImagePalette): Uri {
        val width = 1080
        val layout = measureLayout(workout, width)
        val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawWorkout(canvas, workout, width, layout.height, palette)

        val dir = File(context.cacheDir, "shared_workouts").apply { mkdirs() }
        val file = File(dir, "ironlog-workout-${workout.date.time}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun measureLayout(workout: ShareableWorkout, width: Int): ShareImageLayout {
        val contentWidth = width - (OUTER_PADDING * 2)
        var height = OUTER_PADDING + 148 + 40 + 160 + 32
        workout.exercises.forEach { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) return@forEach
            height += 84 + (workingSets.size * 58) + 32
        }
        height += 92 + OUTER_PADDING
        return ShareImageLayout(contentWidth = contentWidth, height = height)
    }

    private fun drawWorkout(
        canvas: Canvas,
        workout: ShareableWorkout,
        width: Int,
        height: Int,
        palette: ShareImagePalette
    ) {
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

        canvas.drawText("Iron Log", contentLeft, y + 56f, titlePaint)
        canvas.drawText(
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(workout.date),
            contentLeft,
            y + 98f,
            labelPaint
        )
        y += 148f

        val summaryTop = y
        drawRoundRect(canvas, contentLeft, summaryTop, contentRight, summaryTop + 160f, palette.card, 28f)
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

        workout.exercises.forEach { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) return@forEach

            val cardHeight = 84f + (workingSets.size * 58f)
            drawRoundRect(canvas, contentLeft, y, contentRight, y + cardHeight, palette.card, 24f)
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
                        contentLeft + 22f,
                        setY - 36f,
                        contentRight - 22f,
                        setY + 16f,
                        palette.accentSoft,
                        12f
                    )
                }
                canvas.drawText("${index + 1}", contentLeft + 40f, setY, labelPaint)
                canvas.drawText(formatSet(set), contentLeft + 96f, setY, bodyPaint)
                if (set.isPersonalRecord) {
                    drawRoundRect(canvas, contentRight - 112f, setY - 34f, contentRight - 42f, setY + 8f, palette.accent, 9f)
                    canvas.drawText("PR", contentRight - 93f, setY - 5f, textPaint(24f, palette.onAccent, Typeface.BOLD))
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

    private fun drawRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
    }

    private fun textPaint(size: Float, color: Int, typefaceStyle: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
        }
    }

    private fun formatSet(set: ShareableSet): String {
        val weight = set.weight
        val reps = set.reps
        return when {
            weight != null && reps != null -> "${trim(weight)} lbs x $reps"
            weight != null -> "${trim(weight)} lbs"
            reps != null -> "$reps reps"
            set.durationSeconds != null -> formatDurationSeconds(set.durationSeconds)
            set.distanceMeters != null -> "${trim(set.distanceMeters)} m"
            set.calories != null -> "${trim(set.calories)} cal"
            else -> "-"
        }
    }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatDurationSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val remaining = seconds % 60
        return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
    }

    private fun trim(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var candidate = text
        while (candidate.length > 1 && paint.measureText("$candidate...") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return "$candidate..."
    }

    private data class ShareImageLayout(
        val contentWidth: Int,
        val height: Int
    )

    private companion object {
        private const val OUTER_PADDING = 64
    }
}
