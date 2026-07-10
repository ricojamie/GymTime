package com.example.gymtime.domain.analytics

/**
 * Least-squares line fitted over x = 0..n-1.
 * y(x) = intercept + slope * x
 */
data class RegressionLine(val slope: Float, val intercept: Float)

object LinearRegression {

    /** Fits a least-squares line over x = 0..n-1. Returns null when fewer than 2 points. */
    fun fit(values: List<Float>): RegressionLine? {
        val n = values.size
        if (n < 2) return null
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        values.forEachIndexed { i, y ->
            val x = i.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }
        // With x = 0..n-1 and n >= 2 the denominator is strictly positive.
        val slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        return RegressionLine(slope.toFloat(), intercept.toFloat())
    }

    /** Predicted value for each index of [values]; empty when a line can't be fitted. */
    fun trendValues(values: List<Float>): List<Float> {
        val line = fit(values) ?: return emptyList()
        return List(values.size) { i -> line.intercept + line.slope * i }
    }
}
