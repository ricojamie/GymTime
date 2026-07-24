package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "generated_narratives",
    primaryKeys = ["kind", "subjectKey"],
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workoutId"),
        Index(value = ["kind", "subjectStartEpochMs"])
    ]
)
data class GeneratedNarrative(
    val kind: String,
    val subjectKey: String,
    val workoutId: Long? = null,
    val subjectStartEpochMs: Long? = null,
    val sourceFingerprint: String,
    val promptVersion: Int,
    val modelName: String? = null,
    val generatorType: String,
    val text: String,
    val generatedAtEpochMs: Long
)
