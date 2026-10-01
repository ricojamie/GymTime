package com.example.gymtime.ui.exercise

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.domain.progression.LoggerProgressPoint
import com.example.gymtime.domain.progression.LoggerProgressState
import com.example.gymtime.domain.progression.LoggerRecord
import com.example.gymtime.domain.progression.LoggerSession
import com.example.gymtime.ui.components.PlateCalculatorTestActivity
import com.example.gymtime.ui.exercise.preview.LoggerAction
import com.example.gymtime.ui.exercise.preview.LoggerField
import com.example.gymtime.ui.exercise.preview.LoggerFields
import com.example.gymtime.ui.exercise.preview.LoggerProgressSheet
import com.example.gymtime.ui.exercise.preview.PreviewLoggerContent
import com.example.gymtime.ui.exercise.preview.PreviewLoggerState
import com.example.gymtime.ui.home.NewUiPreviewToggle
import com.example.gymtime.ui.theme.AppColorScheme
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LoggerPreviewTheme
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.ThemeFontOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

/** Pure rendering tests: no Room, Hilt, active workout, or user data is needed. */
@RunWith(AndroidJUnit4::class)
class PreviewLoggerContentTest {
    private val activityRule = ActivityScenarioRule<PlateCalculatorTestActivity>(
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName("com.example.gymtime", PlateCalculatorTestActivity::class.java.name)
        }
    )

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule ->
        var activity: PlateCalculatorTestActivity? = null
        rule.scenario.onActivity { activity = it }
        requireNotNull(activity)
    }

    @Test
    fun inputsKeepCurrentValuesAndLogTheChangedWeightAndReps() {
        val state = mutableStateOf(sampleState())
        var loggedFields: LoggerFields? = null
        setLogger(state, onAction = { if (it == LoggerAction.LOG) loggedFields = state.value.fields })

        reveal(hasContentDescription("Increase WEIGHT / lb by 5"))
        composeRule.onNodeWithContentDescription("Increase WEIGHT / lb by 5").performClick()
        reveal(hasContentDescription("Increase REPS by 1"))
        composeRule.onNodeWithContentDescription("Increase REPS by 1").performClick()
        reveal(hasText("Log set"))
        composeRule.onNodeWithText("Log set").performClick()

        composeRule.runOnIdle {
            assertEquals("140", loggedFields?.weight)
            assertEquals("9", loggedFields?.reps)
            assertEquals("140", state.value.fields.weight)
            assertEquals("9", state.value.fields.reps)
        }
    }

    @Test
    fun recordsAndLastWorkoutStayDistinctAndToolsHaveTheirOwnActions() {
        val actions = mutableListOf<LoggerAction>()
        setLogger(mutableStateOf(sampleState()), onAction = actions::add)

        composeRule.onNodeWithText("ALL-TIME BEST").assertIsDisplayed()
        composeRule.onNodeWithText("185 lb × 5 reps").assertIsDisplayed()
        composeRule.onNodeWithText("LAST WORKOUT").assertIsDisplayed()
        composeRule.onNodeWithText("175 lb × 6 reps").assertIsDisplayed()
        listOf("History", "Plates", "Notes").forEach { label ->
            reveal(hasText(label))
            composeRule.onNodeWithText(label).performClick()
        }
        composeRule.runOnIdle {
            assertEquals(listOf(LoggerAction.HISTORY, LoggerAction.PLATES, LoggerAction.NOTES), actions)
        }
    }

    @Test
    fun sessionSetCanBeEditedAnnotatedAndDeleted() {
        val loggedSet = sampleSet()
        var edited: WorkoutSet? = null
        var annotated: WorkoutSet? = null
        var deleted: WorkoutSet? = null
        setLogger(
            mutableStateOf(sampleState().copy(sets = listOf(loggedSet))),
            onEditSet = { edited = it },
            onDeleteSet = { deleted = it },
            onSetNote = { annotated = it }
        )

        reveal(hasContentDescription("Edit set 1"))
        composeRule.onNodeWithContentDescription("Edit set 1").performClick()
        composeRule.onNodeWithContentDescription("Options for set 1").performClick()
        composeRule.onNodeWithText("Set note").performClick()
        composeRule.onNodeWithContentDescription("Options for set 1").performClick()
        composeRule.onNodeWithText("Delete set").performClick()

        composeRule.runOnIdle {
            assertEquals(loggedSet, edited)
            assertEquals(loggedSet, annotated)
            assertEquals(loggedSet, deleted)
        }
    }

    @Test
    fun supersetHasVisibleExitAndExerciseSelection() {
        val actions = mutableListOf<LoggerAction>()
        var selected: Long? = null
        val row = sampleExercise().copy(id = 2L, name = "Cable row")
        setLogger(
            mutableStateOf(sampleState().copy(superset = listOf(sampleExercise(), row))),
            onAction = actions::add,
            onSelectExercise = { selected = it }
        )

        reveal(hasText("Exit superset"))
        composeRule.onNodeWithText("Exit superset").assertIsDisplayed().performClick()
        reveal(hasText("Cable row"))
        composeRule.onNodeWithText("Cable row").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(LoggerAction.EXIT_SUPERSET), actions)
            assertEquals(2L, selected)
        }
    }

    @Test
    fun narrowScreenAndLargeFontStackInputsAndKeepTouchTargetsUsable() {
        val state = mutableStateOf(sampleState())
        setLogger(state, width = 300, fontScale = 1.5f)

        reveal(hasContentDescription("Increase REPS by 1"))
        val weight = composeRule.onNodeWithContentDescription("Increase WEIGHT / lb by 5").fetchSemanticsNode().boundsInRoot
        val reps = composeRule.onNodeWithContentDescription("Increase REPS by 1").fetchSemanticsNode().boundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("Weight input must precede reps vertically", weight.bottom <= reps.top)
        assertTrue("Weight nudge must retain a 48dp touch target", weight.width >= minimum - 1f && weight.height >= minimum - 1f)
        assertTrue("Reps nudge must retain a 48dp touch target", reps.width >= minimum - 1f && reps.height >= minimum - 1f)
        composeRule.onNodeWithContentDescription("Increase REPS by 1").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("9", state.value.fields.reps) }
        saveScreenshot("preview-logger-narrow-large-font")
    }

    @Test
    fun everyLogTypeExposesItsRequiredEditableFields() {
        val state = mutableStateOf(sampleState())
        setLogger(state)
        val expectedFields = mapOf(
            LogType.WEIGHT_REPS to listOf(hasContentDescription("WEIGHT / lb"), hasContentDescription("REPS")),
            LogType.REPS_ONLY to listOf(hasContentDescription("REPS")),
            LogType.DURATION to listOf(hasText("Time (mm:ss)")),
            LogType.WEIGHT_DISTANCE to listOf(hasContentDescription("WEIGHT / lb"), hasText("Distance (m)")),
            LogType.DISTANCE_TIME to listOf(hasText("Distance (m)"), hasText("Time (mm:ss)")),
            LogType.WEIGHT_TIME to listOf(hasContentDescription("WEIGHT / lb"), hasText("Time (mm:ss)")),
            LogType.CALORIES_TIME to listOf(hasText("Calories"), hasText("Time (mm:ss)"))
        )

        expectedFields.forEach { (type, fields) ->
            composeRule.runOnIdle { state.value = state.value.copy(exercise = sampleExercise(type).copy(id = type.ordinal + 1L)) }
            fields.forEach { field ->
                reveal(field)
                composeRule.onNode(field and hasSetTextAction()).assertExists()
            }
            if (type == LogType.WEIGHT_DISTANCE || type == LogType.DISTANCE_TIME) {
                reveal(hasText("Distance: Meters"))
                composeRule.onNodeWithText("Distance: Meters").performClick()
                composeRule.onNodeWithText("Kilometers").performClick()
                composeRule.runOnIdle { assertEquals(DistanceUnit.KILOMETERS, state.value.fields.distanceUnit) }
                composeRule.runOnIdle { state.value = state.value.copy(fields = state.value.fields.copy(distanceUnit = DistanceUnit.METERS)) }
            }
        }
    }

    @Test
    fun savingAndInvalidInputCannotSubmitAnotherSet() {
        val state = mutableStateOf(sampleState().copy(canLog = false))
        setLogger(state)
        reveal(hasText("Log set"))
        composeRule.onNodeWithText("Log set").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(canLog = true, saving = true) }
        composeRule.onNodeWithText("Saving…").assertIsNotEnabled()
        composeRule.onNodeWithText("Finish").assertIsNotEnabled()
    }

    @Test
    fun lightThemeScreenshot() {
        setLogger(
            mutableStateOf(sampleState().copy(sets = listOf(sampleSet()))),
            dark = false,
            appColorScheme = ThemeColors.getScheme("custom", "#FF7F6B"),
            themeFontKey = ThemeFontOption.SPACE_GROTESK.storageKey
        )
        composeRule.onNodeWithText("ALL-TIME BEST").assertIsDisplayed()
        saveScreenshot("preview-logger-light")
    }

    @Test
    fun darkThemeScreenshot() {
        setLogger(mutableStateOf(sampleState().copy(sets = listOf(sampleSet()))), dark = true)
        composeRule.onNodeWithText("ALL-TIME BEST").assertIsDisplayed()
        saveScreenshot("preview-logger-dark")
    }

    @Test
    fun homePreviewToggleChangesOncePerTap() {
        val enabled = mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            IronLogTheme(darkMode = false) {
                NewUiPreviewToggle(
                    enabled = enabled.value,
                    onEnabledChange = { changes += it; enabled.value = it }
                )
            }
        }

        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("New UI").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Try our new UI!").performClick()
        composeRule.onNode(isToggleable()).assertIsOn()
        composeRule.runOnIdle { assertEquals(listOf(true), changes) }
        composeRule.onNodeWithContentDescription("Try our new UI!").performClick()
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.runOnIdle { assertEquals(listOf(true, false), changes) }
    }

    @Test
    fun progressSheetTabsExposeRecordsAndHistoryBeyondFifteenSessions() {
        setProgressSheet(
            mutableStateOf(sampleProgress()),
            appColorScheme = ThemeColors.getScheme("custom", "#FF7F6B"),
            themeFontKey = ThemeFontOption.SPACE_GROTESK.storageKey
        )

        composeRule.onNodeWithText("Progress").assertIsSelected()
        reveal(hasText("18 workouts"))
        composeRule.onNodeWithText("18 workouts").assertIsDisplayed()
        saveScreenshot("preview-logger-progression", includeModalWindow = true)
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Records").assertIsSelected()
        reveal(hasText("Heaviest set"))
        composeRule.onNodeWithText("185 lb × 8 reps").assertIsDisplayed()
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("History").assertIsSelected()
        reveal(hasText("History session 18"))
        composeRule.onNodeWithText("History session 18").assertIsDisplayed()
        composeRule.onNodeWithText("100 lb × 8 reps").assertIsDisplayed()
    }

    @Test
    fun openProgressSheetUpdatesRecordsWithoutLosingSelectedTab() {
        val progress = mutableStateOf(sampleProgress())
        setProgressSheet(progress)
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("185 lb × 8 reps").assertIsDisplayed()

        composeRule.runOnIdle {
            val improved = sampleSet().copy(weight = 205f)
            progress.value = progress.value.copy(
                allTimeBest = improved,
                records = listOf(LoggerRecord("Heaviest set", improved))
            )
        }
        composeRule.onNodeWithText("Records").assertIsSelected()
        composeRule.onNodeWithText("205 lb × 8 reps").assertIsDisplayed()
        composeRule.onNodeWithText("185 lb × 8 reps").assertDoesNotExist()
        composeRule.onNodeWithText("Progress").performClick()
        composeRule.onNodeWithText("205 lb × 8 reps").assertIsDisplayed()
    }

    private fun reveal(matcher: SemanticsMatcher) {
        val verticalLazyList = hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        composeRule.onNode(verticalLazyList).performScrollToNode(matcher)
    }

    private fun saveScreenshot(name: String, includeModalWindow: Boolean = false) {
        composeRule.waitForIdle()
        val bitmap = if (includeModalWindow) {
            captureVisibleModalScreenshot(name)
        } else {
            composeRule.onRoot().captureToImage().asAndroidBitmap()
        }
        val output = File(composeRule.activity.getExternalFilesDir(null), "$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Screenshot was written: $output", output.length() > 0)
    }

    private fun captureVisibleModalScreenshot(name: String): Bitmap {
        // Compose semantics can settle before the dialog's separate Android window is committed.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var sampledColorCount = 0
        var attempts = 0
        do {
            attempts++
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            if (screenshot != null) {
                // Ignore system bars: they must not make an otherwise blank capture pass.
                val sampledColors = buildSet {
                    for (row in 0 until 32) {
                        for (column in 0 until 32) {
                            val x = screenshot.width / 10 + column * screenshot.width * 8 / 320
                            val y = screenshot.height / 5 + row * screenshot.height * 3 / 160
                            add(screenshot.getPixel(x, y))
                        }
                    }
                }
                sampledColorCount = sampledColors.size
                if (sampledColorCount > 8) return screenshot
                screenshot.recycle()
            }
            // Give SurfaceFlinger real time to present a committed dialog frame.
            SystemClock.sleep(100L)
            instrumentation.waitForIdleSync()
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(
            "Modal capture $name stayed blank after $attempts attempts in 5 seconds " +
                "(last interior color count: $sampledColorCount)"
        )
    }

    private fun setProgressSheet(
        state: MutableState<LoggerProgressState>,
        appColorScheme: AppColorScheme = ThemeColors.LimeGreen,
        themeFontKey: String = ThemeFontOption.BEBAS_NEUE.storageKey
    ) {
        composeRule.setContent {
            IronLogTheme(darkMode = false, appColorScheme = appColorScheme, themeFontKey = themeFontKey) {
                LoggerPreviewTheme {
                    LoggerProgressSheet(
                        exerciseName = "Bench press", logType = LogType.WEIGHT_REPS,
                        state = state.value, onDismiss = {}
                    )
                }
            }
        }
    }

    private fun sampleProgress(): LoggerProgressState {
        val sessions = List(18) { index ->
            val date = Date(System.currentTimeMillis() - index * 7L * 24L * 60L * 60L * 1000L)
            val set = sampleSet().copy(
                id = index + 1L, workoutId = index + 1L, weight = 185f - index * 5f,
                timestamp = date, note = "History session ${index + 1}"
            )
            LoggerSession(workoutId = index + 1L, date = date, isCurrent = index == 0, sets = listOf(set))
        }
        val best = sessions.first().sets.first()
        return LoggerProgressState(
            isLoading = false,
            allTimeBest = best,
            lastWorkoutBest = sessions[1].sets.first(),
            sessions = sessions,
            points = sessions.asReversed().map { session ->
                val set = session.sets.first()
                LoggerProgressPoint(session.workoutId, session.date, requireNotNull(set.weight), set)
            },
            records = listOf(LoggerRecord("Heaviest set", best))
        )
    }

    private fun setLogger(
        state: MutableState<PreviewLoggerState>,
        dark: Boolean = false,
        width: Int? = null,
        fontScale: Float = 1f,
        appColorScheme: AppColorScheme = ThemeColors.LimeGreen,
        themeFontKey: String = ThemeFontOption.BEBAS_NEUE.storageKey,
        onAction: (LoggerAction) -> Unit = {},
        onSelectExercise: (Long) -> Unit = {},
        onEditSet: (WorkoutSet) -> Unit = {},
        onDeleteSet: (WorkoutSet) -> Unit = {},
        onSetNote: (WorkoutSet) -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                IronLogTheme(darkMode = dark, appColorScheme = appColorScheme, themeFontKey = themeFontKey) {
                    LoggerPreviewTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            PreviewLoggerContent(
                                state = state.value,
                                onAction = onAction,
                                onFieldChange = { field, value ->
                                    val fields = state.value.fields
                                    state.value = state.value.copy(fields = when (field) {
                                        LoggerField.WEIGHT -> fields.copy(weight = value)
                                        LoggerField.REPS -> fields.copy(reps = value)
                                        LoggerField.DURATION -> fields.copy(duration = value)
                                        LoggerField.DISTANCE -> fields.copy(distance = value)
                                        LoggerField.CALORIES -> fields.copy(calories = value)
                                        LoggerField.RPE -> fields.copy(rpe = value)
                                        LoggerField.NOTE -> fields.copy(note = value)
                                    })
                                },
                                onDistanceUnitChange = { state.value = state.value.copy(fields = state.value.fields.copy(distanceUnit = it)) },
                                onSelectExercise = onSelectExercise,
                                onEditSet = onEditSet,
                                onDeleteSet = onDeleteSet,
                                onSetNote = onSetNote,
                                modifier = if (width != null) Modifier.width(width.dp) else Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sampleExercise(type: LogType = LogType.WEIGHT_REPS) = Exercise(
        id = 1L, name = "Bench press", targetMuscle = "Chest", logType = type,
        isCustom = false, notes = "Feet planted. Pause on the chest.", defaultRestSeconds = 90
    )

    private fun sampleState() = PreviewLoggerState(
        exercise = sampleExercise(),
        fields = LoggerFields(weight = "135", reps = "8", duration = "01:00", distance = "100", calories = "50"),
        best = "185 lb × 5 reps", last = "175 lb × 6 reps", canLog = true
    )

    private fun sampleSet() = WorkoutSet(
        id = 1L, workoutId = 1L, exerciseId = 1L, weight = 135f, reps = 8,
        rpe = 8f, durationSeconds = null, distanceMeters = null,
        isWarmup = false, isComplete = true, timestamp = Date(0L), note = "Controlled reps"
    )
}
