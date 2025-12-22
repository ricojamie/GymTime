package com.example.gymtime.data.db.entity

import androidx.room.ColumnInfo

data class DailyVolume(
    @ColumnInfo(name = "dailyVol") val dailyVol: Float,
    @ColumnInfo(name = "date") val date: Long
)
