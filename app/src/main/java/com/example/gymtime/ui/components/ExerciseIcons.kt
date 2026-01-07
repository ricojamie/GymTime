package com.example.gymtime.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps muscle groups to appropriate Material icons for visual representation
 * in bottom sheets and exercise lists.
 */
object ExerciseIcons {

    fun getIconForMuscle(muscleGroup: String): ImageVector {
        return when (muscleGroup.lowercase()) {
            "chest" -> Icons.Filled.FitnessCenter
            "back" -> Icons.Filled.Accessibility
            "legs" -> Icons.AutoMirrored.Filled.DirectionsRun
            "shoulders" -> Icons.Filled.Sports
            "biceps" -> Icons.Filled.SportsMartialArts
            "triceps" -> Icons.Filled.SportsMartialArts
            "core" -> Icons.Filled.SelfImprovement
            "cardio" -> Icons.Filled.Favorite
            else -> Icons.Filled.FitnessCenter
        }
    }
}
