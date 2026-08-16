package com.example.gymtime.ui.components

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import com.example.gymtime.ui.theme.IronLogTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlateCalculatorSheetTest {

    private val activityRule = ActivityScenarioRule<PlateCalculatorTestActivity>(
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                "com.example.gymtime",
                PlateCalculatorTestActivity::class.java.name
            )
        }
    )

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule ->
        var activity: PlateCalculatorTestActivity? = null
        rule.scenario.onActivity { activity = it }
        requireNotNull(activity)
    }

    @Test
    fun buildMode_addPlate_updatesTotalAndReturnsLoadedWeight() {
        var usedWeight: Float? = null
        setCalculator(initialWeight = 45f, onUseWeight = { usedWeight = it })

        composeRule.onNodeWithTag("plate_mode_build").performClick()
        composeRule.onNodeWithTag("plate_add_45").performClick()
        composeRule.onNodeWithTag("plate_total").assertTextEquals("135")
        composeRule.onNodeWithTag("plate_use_weight").performClick()

        composeRule.runOnIdle { assertEquals(135f, usedWeight) }
    }

    @Test
    fun switchingToBuildMode_preservesCalculatedTargetLoadout() {
        setCalculator(initialWeight = 185f)

        composeRule.onNodeWithTag("plate_total").assertTextEquals("185")
        composeRule.onNodeWithTag("plate_mode_build").performClick()
        composeRule.onNodeWithTag("plate_total").assertTextEquals("185")
        composeRule.onNodeWithTag("plate_remove_45").performScrollTo().performClick()
        composeRule.onNodeWithTag("plate_total").assertTextEquals("95")
    }

    @Test
    fun targetMode_usesNearestAchievableLoadWhenTargetIsInexact() {
        var usedWeight: Float? = null
        setCalculator(initialWeight = 138f, onUseWeight = { usedWeight = it })

        composeRule.onNodeWithTag("plate_total").assertTextEquals("140")
        composeRule.onNodeWithTag("plate_use_weight").performClick()

        composeRule.runOnIdle { assertEquals(140f, usedWeight) }
    }

    @Test
    fun invalidTarget_disablesUseUntilBuildModeProvidesAValidTotal() {
        setCalculator(initialWeight = 135f)

        composeRule.onNodeWithTag("target_weight_input").performTextClearance()
        composeRule.onNodeWithTag("plate_use_weight").assertIsNotEnabled()
        composeRule.onNodeWithTag("plate_mode_build").performClick()
        composeRule.onNodeWithTag("plate_use_weight").performClick()
    }

    @Test
    fun manualChanges_surviveRoundTripBetweenModes() {
        setCalculator(initialWeight = 45f)

        composeRule.onNodeWithTag("plate_mode_build").performClick()
        composeRule.onNodeWithTag("plate_add_25").performClick()
        composeRule.onNodeWithTag("plate_mode_target").performClick()
        composeRule.onNodeWithTag("plate_total").assertTextEquals("95")
        composeRule.onNodeWithTag("plate_mode_build").performClick()
        composeRule.onNodeWithTag("plate_total").assertTextEquals("95")
    }

    private fun setCalculator(
        initialWeight: Float,
        onUseWeight: (Float) -> Unit = {}
    ) {
        composeRule.setContent {
            IronLogTheme {
                PlateCalculatorContent(
                    initialWeight = initialWeight,
                    barWeight = 45f,
                    availablePlates = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
                    loadingSides = 2,
                    onDismiss = {},
                    onNavigateToSettings = {},
                    onUseWeight = onUseWeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
