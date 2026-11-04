package com.example.gymtime.ui.history

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.ui.components.PlateCalculatorTestActivity
import com.example.gymtime.ui.history.preview.PreviewWorkoutDetailsSheet
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LoggerPreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

class WorkoutHistoryFlingTest {
    private val activity = ActivityScenarioRule<PlateCalculatorTestActivity>(
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName("com.example.gymtime", PlateCalculatorTestActivity::class.java.name)
        }
    )
    @get:Rule val rule = AndroidComposeTestRule(activity) { scenario ->
        var host: PlateCalculatorTestActivity? = null
        scenario.scenario.onActivity { host = it }
        requireNotNull(host)
    }

    @Test fun fastFlingAtBottomSettlesWithoutAnotherTouch() {
        val workout = WorkoutWithMuscles(Workout(1, Date(0), Date(600000), "Long workout", null), "Chest", null, 24)
        val sets = (1L..24L).map { id ->
            SetWithExerciseInfo(
                Set(id = id, workoutId = 1, exerciseId = id, weight = 100f, reps = 8,
                    rpe = null, durationSeconds = null, distanceMeters = null,
                    isWarmup = false, isComplete = true, timestamp = Date(id)),
                "Exercise $id", "Chest"
            )
        }
        rule.setContent {
            IronLogTheme {
                LoggerPreviewTheme {
                    PreviewWorkoutDetailsSheet(workout, sets, null, {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Exercise 23"))
        list.performTouchInput { swipeUp(durationMillis = 60) }
        rule.onNodeWithText("Exercise 24").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        try {
            val header = rule.onNodeWithContentDescription("Close workout details")
            val last = rule.onNodeWithText("Exercise 24")
            val headerTop = header.fetchSemanticsNode().boundsInWindow.top
            val lastTop = last.fetchSemanticsNode().boundsInWindow.top
            repeat(24) {
                rule.mainClock.advanceTimeByFrame()
                assertEquals(headerTop, header.fetchSemanticsNode().boundsInWindow.top, 1f)
                assertEquals(lastTop, last.fetchSemanticsNode().boundsInWindow.top, 1f)
            }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }
}
