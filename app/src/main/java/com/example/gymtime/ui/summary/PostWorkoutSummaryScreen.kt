package com.example.gymtime.ui.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.ai.OnDeviceAiDownloadCard
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.summary.preview.PreviewPostWorkoutSummaryContent
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.util.ShareImagePalette
import kotlinx.coroutines.launch

@Composable
fun PostWorkoutSummaryScreen(
    navController: NavController,
    viewModel: PostWorkoutSummaryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestNavigateHome by rememberUpdatedState<() -> Unit>({ navController.navigateHomeAndClearStack() })
    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.navigationEvent.collect { latestNavigateHome() } }
            launch {
                viewModel.shareEvent.collect { payload ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, payload.imageUri)
                        putExtra(Intent.EXTRA_TEXT, payload.text)
                        clipData = ClipData.newUri(context.contentResolver, "Workout summary", payload.imageUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share workout")) }
                        .onFailure { Toast.makeText(context, "Couldn't open sharing. Try again.", Toast.LENGTH_SHORT).show() }
                }
            }
            launch {
                viewModel.copyEvent.collect { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Workout summary", text))
                    Toast.makeText(context, "Workout copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    val sharePalette = remember(colors, action) {
        ShareImagePalette(
            background = colors.background.toArgb(),
            card = colors.surface.toArgb(),
            accent = action.fill.toArgb(),
            accentSoft = colors.primaryContainer.toArgb(),
            textPrimary = colors.onSurface.toArgb(),
            textMuted = colors.onSurfaceVariant.toArgb(),
            onAccent = action.onFill.toArgb()
        )
    }
    val navigation = rememberGuardedNavigationActions(
        hasUnsavedChanges = state.hasUnsavedFeedback,
        onBack = viewModel::skipAndFinish,
        onHome = viewModel::skipAndFinish,
        dialogTitle = "Leave without saving feedback?",
        dialogMessage = "Your workout is already saved. Only your unsaved rating and session note will be discarded."
    )
    // Prevent system back from cancelling an active Room feedback write.
    BackHandler(enabled = state.isSaving) {}

    PreviewPostWorkoutSummaryContent(
        state = state,
        onRating = viewModel::updateRating,
        onNote = viewModel::updateRatingNote,
        onDone = viewModel::saveAndFinish,
        onSkip = navigation.home,
        onRetry = viewModel::retryLoadStats,
        onShare = { viewModel.onShareClicked(sharePalette) },
        onCopy = viewModel::onCopyClicked,
        optionalSetup = { OnDeviceAiDownloadCard() }
    )
}
