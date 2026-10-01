package com.example.gymtime.domain.progression

import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.OneRepMaxCalculator

/** Shared by Home's performance trend and the logger; never substitutes zero for missing data. */
object StrengthPerformanceCalculator {
    fun estimatedOneRepMax(set: Set): Float? {
        if (!set.isComplete || set.isWarmup) return null
        val weight = set.weight?.takeIf { it.isFinite() && it > 0f } ?: return null
        val reps = set.reps ?: return null
        // Keep the app's established Epley eligibility. Higher-rep sets keep their raw records.
        return OneRepMaxCalculator.calculateE1RM(weight, reps)?.takeIf { it.isFinite() && it > 0f }
    }

    fun strengthValue(set: Set, logType: LogType): Float? {
        if (!set.isComplete || set.isWarmup) return null
        return when (logType) {
            LogType.WEIGHT_REPS -> estimatedOneRepMax(set)
            LogType.REPS_ONLY -> set.reps?.takeIf { it > 0 }?.toFloat()
            else -> null
        }
    }
}
