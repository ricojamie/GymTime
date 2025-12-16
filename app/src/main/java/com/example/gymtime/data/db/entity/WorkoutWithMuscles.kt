package com.example.gymtime.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import java.util.Date

import androidx.room.ColumnInfo

data class WorkoutWithMuscles(
    @Embedded val workout: Workout,
    @ColumnInfo(name = "muscleGroups") val muscleGroupsString: String?,
    @ColumnInfo(name = "totalVolume") val totalVolume: Float?,
    @ColumnInfo(name = "workingSetCount") val workingSetCount: Int?
) {
    val muscleGroups: List<String>
        get() = muscleGroupsString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
