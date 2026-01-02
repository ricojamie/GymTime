package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDay::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineDayId"), Index("exerciseId")]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineDayId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: String? = null,  // UUID linking all exercises in a routine superset
    val supersetOrderIndex: Int = 0       // Position in rotation (0, 1, etc.)
)
