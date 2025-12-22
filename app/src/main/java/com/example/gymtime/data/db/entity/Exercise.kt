package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMuscle: String,
    val logType: LogType,
    val isCustom: Boolean,
    val notes: String?,
    val defaultRestSeconds: Int,
    val isStarred: Boolean = false
)
