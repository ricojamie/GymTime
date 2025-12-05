package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDay::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("routineDayId")]
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,
    val name: String?,
    val note: String?,
    val rating: Int? = null,        // 1-5 flames rating
    val ratingNote: String? = null, // Optional note about workout
    val routineDayId: Long? = null  // NEW: Links to routine day if started from routine
)