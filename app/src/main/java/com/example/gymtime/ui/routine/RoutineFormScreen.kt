package com.example.gymtime.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateToNewRoutineDetail
import com.example.gymtime.ui.components.BackNavigationIcon
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.components.HomeNavigationAction
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.theme.*
import com.example.gymtime.ui.routine.preview.PreviewRoutineFormContent
import com.example.gymtime.ui.routine.preview.RoutineFormUiState
import com.example.gymtime.ui.routine.preview.RoutineFormsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFormScreen(
    navController: NavController,
    viewModel: RoutineFormViewModel = hiltViewModel()
) {
    val newUiEnabled by viewModel.newUiEnabled.collectAsStateWithLifecycle()
    RoutineFormsTheme { RoutineFormScreenBody(navController, viewModel, newUiEnabled) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineFormScreenBody(navController: NavController, viewModel: RoutineFormViewModel, newUiEnabled: Boolean) {
    val routineName by viewModel.routineName.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val accentColor = MaterialTheme.colorScheme.primary
    val navigationActions = rememberGuardedNavigationActions(
        hasUnsavedChanges = hasUnsavedChanges,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack
    )

    BackHandler(enabled = isSaving) { /* Keep the draft on screen until its save finishes. */ }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestEditMode by rememberUpdatedState(isEditMode)
    LaunchedEffect(viewModel, navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.saveSuccessEvent.collect { routineId ->
            if (latestEditMode) {
                navController.navigateUp()
            } else {
                navController.navigateToNewRoutineDetail(routineId)
            }
        }
        }
    }

    if (newUiEnabled) {
            PreviewRoutineFormContent(
                state = RoutineFormUiState(routineName, isEditMode, isLoading, isSaving, isSaveEnabled, error),
                onName = viewModel::updateRoutineName, onSave = viewModel::saveRoutine,
                onRetry = viewModel::retryLoad, onBack = navigationActions.back, onHome = navigationActions.home
            )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Routine" else "New Routine",
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    BackNavigationIcon(navigationActions.back)
                },
                actions = {
                    HomeNavigationAction(navigationActions.home)
                    IconButton(
                        onClick = { viewModel.saveRoutine() },
                        enabled = isSaveEnabled
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isSaveEnabled) accentColor else LocalAppColors.current.textTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null && isEditMode && !isSaveEnabled) {
                TextButton(onClick = viewModel::retryLoad) { Text("Reload routine") }
            }
            Text(
                text = "ROUTINE NAME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = LocalAppColors.current.textTertiary
            )

            GlowCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = routineName,
                    onValueChange = { viewModel.updateRoutineName(it.titleCase()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 18.sp
                    ),
                    cursorBrush = SolidColor(LocalAppColors.current.cursor),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    decorationBox = { innerTextField ->
                        if (routineName.isEmpty()) {
                            Text(
                                text = "e.g., Push Pull Legs",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalAppColors.current.textTertiary,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    },
                    singleLine = true
                )
            }
        }
    }
}

// Extension to capitalize first letter of each word
private fun String.titleCase(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
