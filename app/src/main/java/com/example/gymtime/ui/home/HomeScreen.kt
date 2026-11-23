package com.example.gymtime.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.ui.home.preview.PreviewHomeScreen

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController
) {
    PreviewHomeScreen(
        modifier = modifier,
        viewModel = viewModel,
        navController = navController
    )
}
