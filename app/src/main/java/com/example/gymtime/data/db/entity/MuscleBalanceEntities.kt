package com.example.gymtime.data.db.entity

import androidx.room.ColumnInfo

data class MuscleDistribution(
    @ColumnInfo(name = "muscle") val muscle: String,
    @ColumnInfo(name = "setVolume") val setVolume: Int
)

data class MuscleFreshness(
    @ColumnInfo(name = "muscle") val muscle: String,
    @ColumnInfo(name = "lastTrained") val lastTrained: Long
)
