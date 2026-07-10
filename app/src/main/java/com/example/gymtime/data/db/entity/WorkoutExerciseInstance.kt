package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_exercise_instances",
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
    indices = [Index("workoutId"), Index("exerciseId"), Index("routineExerciseId"), Index("supersetGroupId")]
)
data class WorkoutExerciseInstance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val routineExerciseId: Long? = null,
    val orderIndex: Int,
    val plannedSets: Int? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    // Explicit routine/session override. Null inherits Exercise.defaultRestSeconds
    // when plan data is queried for display.
    val restSeconds: Int? = null,
    val notes: String? = null,
    val supersetGroupId: String? = null,
    val supersetOrderIndex: Int = 0,
    val isSkipped: Boolean = false,
    val addedDuringWorkout: Boolean = false
)
