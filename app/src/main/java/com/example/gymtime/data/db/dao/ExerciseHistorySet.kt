package com.example.gymtime.data.db.dao

import androidx.room.Embedded
import com.example.gymtime.data.db.entity.Set
import java.util.Date

/** The workout dates keep exercise history ordered by sessions, including imported sets. */
data class ExerciseHistorySet(
    @Embedded val set: Set,
    val workoutStartTime: Date,
    val workoutEndTime: Date?
)
