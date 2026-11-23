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
     * @return Estimated 1RM, or null if invalid or non-finite input
     */
    fun calculateE1RM(weight: Float, reps: Int): Float? {
        if (!weight.isFinite() || weight <= 0f || reps <= 0 || reps > 15) return null

        // Epley formula: E1RM = weight × (1 + reps / 30)
        return (weight * (1 + reps / 30f)).takeIf { it.isFinite() }
    }

    /**
     * Calculate estimated 10RM (weight for 10 reps)
     * @param weight Weight lifted in pounds
     * @param reps Number of reps performed
     * @return Estimated 10RM, or null if invalid or non-finite input
     */
    fun calculateE10RM(weight: Float, reps: Int): Float? {
        val e1rm = calculateE1RM(weight, reps) ?: return null

        // Reverse Epley: E10RM = E1RM / (1 + 10 / 30)
        return (e1rm / (1 + 10 / 30f)).takeIf { it.isFinite() }
    }

    /**
     * Format weight to display (round to nearest 2.5 lbs for barbell exercises)
     * @param weight Raw calculated weight
     * @return Formatted weight rounded to nearest 2.5 lbs, or 0f for non-finite inputs
     */
    fun formatWeight(weight: Float): Float {
        if (!weight.isFinite()) return 0f
        val steps = weight / 2.5f
        if (!steps.isFinite() || steps >= Int.MAX_VALUE || steps <= Int.MIN_VALUE) return weight
        return steps.roundToInt() * 2.5f
    }
}
