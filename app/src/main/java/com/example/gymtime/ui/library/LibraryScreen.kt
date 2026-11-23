package com.example.gymtime.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.example.gymtime.ui.exercise.ExerciseSelectionContent
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.LocalLoggerActionColors

/**
 * Library screen with tabs for Exercises and Routines.
 * Defaults to Exercises tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Exercises", "Routines")
    val scheme = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = IronLogSpace.ScreenHorizontal,
                    vertical = IronLogSpace.CompactGap
                )
        ) {
            tabs.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = tabs.size,
                        baseShape = IronLogRadius.ControlShape
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = action.fill,
                        activeContentColor = action.onFill,
                        activeBorderColor = action.fill,
                        inactiveContainerColor = scheme.surfaceContainerLow,
                        inactiveContentColor = scheme.onSurfaceVariant,
                        inactiveBorderColor = scheme.outlineVariant
                    ),
                    label = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ExerciseSelectionContent(navController = navController)
            1 -> RoutineLibraryContent(navController = navController)
        }
    }
}
