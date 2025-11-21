package com.example.gymtime.util

import kotlin.math.roundToInt

/**
 * Calculator for estimated one-rep max (E1RM) and related metrics
 * using the Epley formula: weight × (1 + reps / 30)
 */
object OneRepMaxCalculator {

    /**
     * Calculate estimated 1RM using Epley formula
     * @param weight Weight lifted in pounds
     * @param reps Number of reps performed
     * @return Estimated 1RM, or null if invalid input
     */
    fun calculateE1RM(weight: Float, reps: Int): Float? {
        if (weight <= 0 || reps <= 0 || reps > 15) return null

        // Epley formula: E1RM = weight × (1 + reps / 30)
        return weight * (1 + reps / 30f)
    }

    /**
     * Calculate estimated 10RM (weight for 10 reps)
     * @param weight Weight lifted in pounds
     * @param reps Number of reps performed
     * @return Estimated 10RM, or null if invalid input
     */
    fun calculateE10RM(weight: Float, reps: Int): Float? {
        val e1rm = calculateE1RM(weight, reps) ?: return null

        // Reverse Epley: E10RM = E1RM / (1 + 10 / 30)
        return e1rm / (1 + 10 / 30f)
    }

    /**
     * Format weight to display (round to nearest 2.5 lbs for barbell exercises)
     * @param weight Raw calculated weight
     * @return Formatted weight rounded to nearest 2.5 lbs
     */
    fun formatWeight(weight: Float): Float {
        return (weight / 2.5f).roundToInt() * 2.5f
    }
}
