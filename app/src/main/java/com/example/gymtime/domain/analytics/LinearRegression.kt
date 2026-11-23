package com.example.gymtime.domain.analytics

/**
 * Least-squares line fitted over x = 0..n-1.
 * y(x) = intercept + slope * x
 */
data class RegressionLine(val slope: Float, val intercept: Float)

object LinearRegression {

    /** Fits a least-squares line over x = 0..n-1. Returns null when fewer than 2 points or non-finite values exist. */
    fun fit(values: List<Float>): RegressionLine? {
        val n = values.size
        if (n < 2) return null
        if (values.any { !it.isFinite() }) return null

        val xMean = (n - 1) / 2.0
        var sumY = 0.0
        for (y in values) {
            sumY += y.toDouble()
        }
        val yMean = sumY / n
        if (!yMean.isFinite()) return null

        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = i - xMean
            val dy = values[i].toDouble() - yMean
            sxx += dx * dx
            sxy += dx * dy
        }
        if (!sxx.isFinite() || sxx <= 0.0 || !sxy.isFinite()) return null

        val slope = sxy / sxx
        val intercept = yMean - slope * xMean
        val slopeF = slope.toFloat()
        val interceptF = intercept.toFloat()
        if (!slopeF.isFinite() || !interceptF.isFinite()) return null
        return RegressionLine(slopeF, interceptF)
    }

    /** Predicted value for each index of [values]; empty when a line can't be fitted. */
    fun trendValues(values: List<Float>): List<Float> {
        val line = fit(values) ?: return emptyList()
        val interceptD = line.intercept.toDouble()
        val slopeD = line.slope.toDouble()
        val predicted = List(values.size) { i -> (interceptD + slopeD * i).toFloat() }
        return if (predicted.all { it.isFinite() }) predicted else emptyList()
    }
}
