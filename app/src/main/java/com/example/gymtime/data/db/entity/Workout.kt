package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,
    val name: String?,
    val note: String?,
    val rating: Int? = null,        // 1-5 flames rating
    val ratingNote: String? = null  // Optional note about workout
)
