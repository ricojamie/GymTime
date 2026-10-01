package com.example.gymtime.ui.exercise

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
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
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.ui.components.PlateCalculatorTestActivity
import com.example.gymtime.ui.exercise.preview.PickerAction
import com.example.gymtime.ui.exercise.preview.PickerMode
import com.example.gymtime.ui.exercise.preview.PickerUiState
import com.example.gymtime.ui.exercise.preview.PreviewExercisePicker
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

/** Exercises the rendering contract without a database, active workout, or user preferences. */
@RunWith(AndroidJUnit4::class)
class PreviewExercisePickerTest {
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
    fun rowTapImmediatelySelectsOnceAndShowsLastWorkout() {
        val selections = mutableListOf<Exercise>()
        setPicker(mutableStateOf(sampleState()), onSelect = selections::add)

        composeRule.onNodeWithText("Chest · Last: 175 lb × 6 reps").assertIsDisplayed()
        exerciseRow(bench).assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(listOf(bench), selections) }
        composeRule.onNodeWithText("Start superset", substring = true).assertDoesNotExist()
    }

    @Test
    fun searchAndClearPublishChangesWithoutHidingTheMuscleFilters() {
        val state = mutableStateOf(sampleState())
        val queries = mutableListOf<String>()
        setPicker(state, onQuery = queries::add)

        composeRule.onNodeWithText("All").assertIsSelected()
        composeRule.onNodeWithText("Chest", substring = false).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("bench")
        composeRule.onNode(hasSetTextAction()).assertTextContains("bench")
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        composeRule.onNodeWithText("Chest", substring = false).assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(listOf("bench", ""), queries)
            assertEquals("", state.value.query)
        }
    }

    @Test
    fun oneTapSelectsBodyPartsAndAllClearsTheSelection() {
        val state = mutableStateOf(sampleState())
        val toggles = mutableListOf<String>()
        val actions = mutableListOf<PickerAction>()
        setPicker(state, onMuscle = toggles::add, onAction = actions::add)

        composeRule.onNodeWithText("All").assertIsSelected()
        composeRule.onNodeWithText("Chest", substring = false).assertIsDisplayed().performClick().assertIsSelected()
        composeRule.onNodeWithText("All").assertIsNotSelected()
        composeRule.runOnIdle {
            assertEquals(listOf("Chest"), toggles)
            assertEquals(setOf("Chest"), state.value.selectedMuscles)
        }
        composeRule.onNodeWithText("Back", substring = false).performClick().assertIsSelected()
        composeRule.onNodeWithText("Chest", substring = false).assertIsSelected()
        composeRule.onNodeWithText("All").performClick().assertIsSelected()
        composeRule.onNodeWithText("Chest", substring = false).assertIsNotSelected()
        composeRule.onNodeWithText("Back", substring = false).assertIsNotSelected()
        composeRule.runOnIdle {
            assertEquals(listOf("Chest", "Back"), toggles)
            assertEquals(emptySet<String>(), state.value.selectedMuscles)
            assertEquals(listOf(PickerAction.ClearFilters), actions)
        }
    }

    @Test
    fun horizontalBodyPartFiltersReachTheLastMuscleOnANarrowScreen() {
        val state = mutableStateOf(sampleState())
        val toggles = mutableListOf<String>()
        setPicker(state, width = 300, fontScale = 1.5f, onMuscle = toggles::add)

        composeRule.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
        ).performScrollToNode(hasText("Calves", substring = false))
        composeRule.onNodeWithText("Calves", substring = false).assertIsDisplayed().performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(listOf("Calves"), toggles)
            assertEquals(setOf("Calves"), state.value.selectedMuscles)
        }
    }

    @Test
    fun sortingLivesInTheOptionsMenuAndKeepsTheSelectedMuscle() {
        val state = mutableStateOf(sampleState())
        setPicker(state)

        composeRule.onNodeWithText("Chest", substring = false).performClick().assertIsSelected()
        composeRule.onNodeWithText("Recent", substring = false).assertDoesNotExist()
        composeRule.onNodeWithText("Muscle", substring = false).assertDoesNotExist()
        composeRule.onNodeWithText("Sort exercises").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Picker options").performClick()
        composeRule.onNodeWithText("Sort exercises").performClick()
        composeRule.onNodeWithText("Sort exercises").assertIsDisplayed()
        composeRule.onNodeWithText("A-Z").performScrollTo().performClick()
        composeRule.onNodeWithText("A-Z").assertIsSelected()
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        composeRule.onNodeWithText("Sort exercises").assertDoesNotExist()
        composeRule.onNodeWithText("Chest", substring = false).assertIsSelected()

        composeRule.runOnIdle {
            assertEquals(setOf("Chest"), state.value.selectedMuscles)
            assertEquals(ExerciseSortMode.ALPHABETICAL, state.value.sortMode)
        }
    }

    @Test
    fun rowMenuActionsNeverSelectTheExercise() {
        val actions = mutableListOf<PickerAction>()
        val selections = mutableListOf<Exercise>()
        setPicker(mutableStateOf(sampleState()), onAction = actions::add, onSelect = selections::add)

        listOf("Edit exercise", "Track PR", "Delete exercise").forEach { label ->
            composeRule.onNodeWithContentDescription("Bench press options").performClick()
            composeRule.onNodeWithText(label).performClick()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(PickerAction.Edit(bench), PickerAction.TogglePr(bench), PickerAction.Delete(bench)), actions)
            assertTrue("Opening a row menu must not start a workout", selections.isEmpty())
        }
    }

    @Test
    fun emptySearchCanCreateTheTrimmedName() {
        val actions = mutableListOf<PickerAction>()
        setPicker(
            mutableStateOf(sampleState().copy(rows = emptyList(), query = "  Landmine press  ")),
            onAction = actions::add
        )

        composeRule.onNodeWithText("No exercises found").assertIsDisplayed()
        composeRule.onNodeWithText("Create “Landmine press”").performClick()
        composeRule.runOnIdle { assertEquals(listOf(PickerAction.Create("Landmine press")), actions) }
    }

    @Test
    fun supersetSelectionIsOrderedRemovableAndRequiresTwoExercises() {
        val state = mutableStateOf(sampleState().copy(mode = PickerMode.BUILD_SUPERSET))
        val actions = mutableListOf<PickerAction>()
        setPicker(state, onAction = actions::add)

        composeRule.onNodeWithText("Select at least 2 exercises").assertIsNotEnabled()
        exerciseRow(bench).performClick()
        exerciseRow(bench).assertIsSelected()
        composeRule.onNode(hasText(bench.name) and hasText("1")).assertIsDisplayed()
        composeRule.onNodeWithText("Select at least 2 exercises").assertIsNotEnabled()
        exerciseRow(row).performClick()
        composeRule.onNode(hasText(row.name) and hasText("2")).assertIsDisplayed()
        composeRule.onNodeWithText("Start superset (2)").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Remove Bench press").performClick()
        exerciseRow(bench).assertIsNotSelected()
        composeRule.onNode(hasText(row.name) and hasText("1")).assertIsDisplayed()
        composeRule.onNodeWithText("Select at least 2 exercises").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(row), state.value.selectedExercises)
            assertEquals(listOf(PickerAction.StartSuperset, PickerAction.ToggleSuperset), actions)
        }
    }

    @Test
    fun addAndSwapSelectImmediatelyWithoutASeparateStartAction() {
        val state = mutableStateOf(sampleState().copy(mode = PickerMode.ADD_TO_SUPERSET))
        val selections = mutableListOf<Exercise>()
        val actions = mutableListOf<PickerAction>()
        setPicker(state, onSelect = selections::add, onAction = actions::add)

        composeRule.onNodeWithText("Add to superset").assertIsDisplayed()
        exerciseRow(bench).performClick()
        composeRule.onNodeWithText("Select at least 2 exercises").assertDoesNotExist()
        composeRule.onNodeWithText("Start superset", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { state.value = state.value.copy(mode = PickerMode.SWAP) }
        composeRule.onNodeWithText("Swap exercise").assertIsDisplayed()
        exerciseRow(row).performClick()
        composeRule.onNodeWithText("Start superset", substring = true).assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(listOf(bench, row), selections)
            assertEquals(listOf(PickerAction.Back), actions)
        }
    }

    @Test
    fun narrowLargeFontKeepsCancelAndStartInsideThePicker() {
        val state = mutableStateOf(sampleState().copy(mode = PickerMode.BUILD_SUPERSET, selectedExercises = listOf(bench, row)))
        val actions = mutableListOf<PickerAction>()
        setPicker(state, width = 300, fontScale = 1.5f, onAction = actions::add)

        val search = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val inset = with(composeRule.density) { 16.dp.toPx() }
        val minimumTarget = with(composeRule.density) { 48.dp.toPx() }
        listOf(
            "Cancel" to hasText("Cancel"),
            "Picker options" to hasContentDescription("Picker options"),
            "Start superset" to hasText("Start superset (2)")
        ).forEach { (name, labelMatcher) ->
            // Assert the clickable target, even if a component leaves its label unmerged.
            val targetMatcher = hasClickAction() and (labelMatcher or hasAnyDescendant(labelMatcher))
            val target = composeRule.onNode(targetMatcher).assertIsDisplayed()
            val bounds = target.fetchSemanticsNode().boundsInRoot
            assertTrue("$name must stay within the 300dp picker; bounds=$bounds, search=$search, matcher=$targetMatcher", bounds.left >= search.left - inset - 1f && bounds.right <= search.right + inset + 1f)
            assertTrue("$name must retain a 48dp touch target; bounds=$bounds, minimumPx=$minimumTarget, matcher=$targetMatcher", bounds.height >= minimumTarget - 1f && bounds.width >= minimumTarget - 1f)
        }
        composeRule.onNodeWithText("Start superset (2)").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(listOf(PickerAction.StartSuperset, PickerAction.ToggleSuperset), actions) }
        saveScreenshot("preview-picker-narrow-large-font")
    }

    @Test
    fun lightCustomThemeScreenshot() {
        setPicker(
            mutableStateOf(sampleState()),
            colors = ThemeColors.getScheme("custom", "#FF7F6B"),
            font = ThemeFontOption.SPACE_GROTESK.storageKey
        )
        exerciseRow(bench).assertIsDisplayed()
        saveScreenshot("preview-picker-light")
    }

    @Test
    fun darkThemeScreenshot() {
        setPicker(mutableStateOf(sampleState()), dark = true)
        exerciseRow(bench).assertIsDisplayed()
        saveScreenshot("preview-picker-dark")
    }

    private fun exerciseRow(exercise: Exercise) = composeRule.onNode(
        hasText(exercise.name) and hasText(exercise.targetMuscle, substring = true) and hasClickAction()
    )

    private fun setPicker(
        state: MutableState<PickerUiState>,
        width: Int? = null,
        fontScale: Float = 1f,
        dark: Boolean = false,
        colors: AppColorScheme = ThemeColors.LimeGreen,
        font: String = ThemeFontOption.BEBAS_NEUE.storageKey,
        onAction: (PickerAction) -> Unit = {},
        onSelect: (Exercise) -> Unit = {},
        onQuery: (String) -> Unit = {},
        onMuscle: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                IronLogTheme(darkMode = dark, appColorScheme = colors, themeFontKey = font) {
                    LoggerPreviewTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            PreviewExercisePicker(
                                state = state.value,
                                onAction = { action ->
                                    onAction(action)
                                    if (action == PickerAction.ClearFilters) {
                                        state.value = state.value.copy(selectedMuscles = emptySet())
                                    }
                                },
                                onSelectExercise = { exercise ->
                                    onSelect(exercise)
                                    if (state.value.mode == PickerMode.BUILD_SUPERSET) {
                                        val selected = state.value.selectedExercises
                                        state.value = state.value.copy(selectedExercises =
                                            if (selected.any { it.id == exercise.id }) selected.filterNot { it.id == exercise.id }
                                            else selected + exercise
                                        )
                                    }
                                },
                                onQueryChange = { onQuery(it); state.value = state.value.copy(query = it) },
                                onMuscleToggle = { muscle ->
                                    onMuscle(muscle)
                                    val selected = state.value.selectedMuscles
                                    state.value = state.value.copy(selectedMuscles = if (muscle in selected) selected - muscle else selected + muscle)
                                },
                                onSortChange = { state.value = state.value.copy(sortMode = it) },
                                modifier = if (width == null) Modifier.fillMaxSize() else Modifier.width(width.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val output = File(composeRule.activity.getExternalFilesDir(null), "$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Screenshot was written: $output", output.length() > 0)
    }

    private val bench = exercise(1L, "Bench press", "Chest")
    private val row = exercise(2L, "Cable row", "Back")

    private fun exercise(id: Long, name: String, muscle: String) = Exercise(
        id = id, name = name, targetMuscle = muscle, logType = LogType.WEIGHT_REPS,
        isCustom = false, notes = null, defaultRestSeconds = 90
    )

    private fun sampleState(): PickerUiState {
        val exercises = listOf(
            bench, row, exercise(3, "Romanian deadlift", "Hamstrings"),
            exercise(4, "Dumbbell shoulder press", "Shoulders"), exercise(5, "Lat pulldown", "Back"),
            exercise(6, "Leg press", "Quads"), exercise(7, "Cable curl", "Biceps"),
            exercise(8, "Triceps pushdown", "Triceps"), exercise(9, "Standing calf raise", "Calves")
        )
        return PickerUiState(
            rows = exercises.map { ExerciseUsageRow(it, 24, 12, 1_000L) },
            muscles = listOf("Chest", "Back", "Shoulders", "Quads", "Hamstrings", "Biceps", "Triceps", "Calves"),
            lastSets = exercises.associate { exercise ->
                exercise.id to ExerciseLastSetRow(
                    WorkoutSet(
                        id = exercise.id, workoutId = 1L, exerciseId = exercise.id,
                        weight = if (exercise.id == 1L) 175f else 80f, reps = if (exercise.id == 1L) 6 else 10,
                        rpe = null, durationSeconds = null, distanceMeters = null,
                        isWarmup = false, isComplete = true, timestamp = Date(1_000L)
                    ),
                    workoutStartTime = Date(1_000L)
                )
            }
        )
    }
}
