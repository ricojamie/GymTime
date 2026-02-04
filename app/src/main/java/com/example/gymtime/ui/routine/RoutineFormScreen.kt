package com.example.gymtime.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFormScreen(
    navController: NavController,
    viewModel: RoutineFormViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect { routineId ->
            if (isEditMode) {
                navController.navigateUp()
            } else {
                navController.navigate(Screen.RoutineDayList.createRoute(routineId)) {
                    popUpTo(Screen.RoutineList.route)
                }
            }
        }
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
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LocalAppColors.current.textPrimary)
                    }
                },
                actions = {
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
