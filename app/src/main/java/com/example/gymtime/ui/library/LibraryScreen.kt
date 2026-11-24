package com.example.gymtime.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.exercise.ExerciseSelectionContent
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalReducedMotion

/**
 * Library screen with tabs for Exercises and Routines.
 * Defaults to Exercises tab.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Exercises", "Routines")
    val reducedMotion = LocalReducedMotion.current

    Column(modifier = Modifier.fillMaxSize()) {
        IronLogSegmentedControl(
            options = tabs,
            selectedIndex = selectedTabIndex,
            onSelect = { selectedTabIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = IronLogSpace.ScreenHorizontal,
                    vertical = IronLogSpace.CompactGap
                )
        )

        AnimatedContent(
            targetState = selectedTabIndex,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                } else {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { width ->
                        if (forward) width / 6 else -width / 6
                    } + fadeIn(animationSpec = IronMotion.EffectDefault)
                    val exit = slideOutHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { width ->
                        if (forward) -width / 8 else width / 8
                    } + fadeOut(animationSpec = IronMotion.EffectFast)
                    enter togetherWith exit
                }
            },
            label = "libraryTabContent"
        ) { tab ->
            when (tab) {
                0 -> ExerciseSelectionContent(navController = navController)
                else -> RoutineLibraryContent(navController = navController)
            }
        }
    }
}
