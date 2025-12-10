package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId"), Index("exerciseId"), Index("timestamp"), Index("supersetGroupId")]
)
data class Set(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val weight: Float?,
    val reps: Int?,
    val rpe: Float?,
    val durationSeconds: Int?,
    val distanceMeters: Float?,
    val isWarmup: Boolean,
    val isComplete: Boolean,
    val timestamp: Date,
    val note: String? = null,
    val supersetGroupId: String? = null,  // UUID linking all sets in a superset (null = not in superset)
    val supersetOrderIndex: Int = 0       // Position in rotation (0 = first exercise, 1 = second)
)
