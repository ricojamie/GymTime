package com.example.gymtime.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.gymtime.ui.theme.IronLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nestedNavigationExposesSeparateBackAndHomeActions() {
        var backCount = 0
        var homeCount = 0
        composeRule.setContent {
            IronLogTheme {
                Row {
                    BackNavigationIcon { backCount++ }
                    HomeNavigationAction { homeCount++ }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backCount)
            assertEquals(1, homeCount)
        }
    }

    @Test
    fun dirtyNavigationRequiresDiscardConfirmation() {
        var backCount = 0
        composeRule.setContent {
            IronLogTheme {
                val actions = rememberGuardedNavigationActions(
                    hasUnsavedChanges = true,
                    onBack = { backCount++ },
                    onHome = {}
                )
                Button(onClick = actions.back) { Text("Leave form") }
            }
        }

        composeRule.onNodeWithText("Leave form").performClick()
        composeRule.onNodeWithText("Discard changes?").fetchSemanticsNode()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.runOnIdle { assertEquals(0, backCount) }

        composeRule.onNodeWithText("Leave form").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun cleanNavigationLeavesImmediately() {
        var homeCount = 0
        composeRule.setContent {
            IronLogTheme {
                val actions = rememberGuardedNavigationActions(
                    hasUnsavedChanges = false,
                    onBack = {},
                    onHome = { homeCount++ }
                )
                Button(onClick = actions.home) { Text("Go Home") }
            }
        }

        composeRule.onNodeWithText("Go Home").performClick()
        composeRule.runOnIdle { assertEquals(1, homeCount) }
    }
}
