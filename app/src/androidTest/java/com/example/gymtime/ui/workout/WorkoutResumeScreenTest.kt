package com.example.gymtime.ui.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.gymtime.ui.theme.IronLogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutResumeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pendingPlanExerciseOffersSwapAndRemoveActions() {
        var swapped = false
        var removed = false
        val exercise = ResumeExerciseItem(
            instanceId = 11L,
            exerciseId = 4L,
            exerciseName = "Incline Bench Press",
            targetMuscle = "Chest",
            setCount = 0,
            bestWeight = null,
            supersetGroupId = null,
            orderIndex = 1,
            plannedSets = 3
        )

        composeRule.setContent {
            IronLogTheme {
                ExerciseSummaryCard(
                    exercise = exercise,
                    onClick = {},
                    onSwap = { swapped = true },
                    onRemove = { removed = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Exercise actions for Incline Bench Press")
            .performClick()
        composeRule.onNodeWithText("Swap exercise").assertIsDisplayed().performClick()
        assertTrue(swapped)

        composeRule.onNodeWithContentDescription("Exercise actions for Incline Bench Press")
            .performClick()
        composeRule.onNodeWithText("Remove from today").assertIsDisplayed().performClick()
        assertTrue(removed)
    }

    @Test
    fun startedExerciseDoesNotExposePlanEditingActions() {
        composeRule.setContent {
            IronLogTheme {
                ExerciseSummaryCard(
                    exercise = ResumeExerciseItem(
                        instanceId = 11L,
                        exerciseId = 4L,
                        exerciseName = "Incline Bench Press",
                        targetMuscle = "Chest",
                        setCount = 1,
                        bestWeight = 135f,
                        supersetGroupId = null,
                        orderIndex = 1
                    ),
                    onClick = {},
                    onSwap = {},
                    onRemove = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Exercise actions for Incline Bench Press")
            .assertDoesNotExist()
    }
}
