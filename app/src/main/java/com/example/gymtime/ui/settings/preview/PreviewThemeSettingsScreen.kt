package com.example.gymtime.ui.settings.preview

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.ui.settings.SettingsViewModel
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset

/** Document access and preferences belong here; the content only renders values and intents. */
@Composable
fun PreviewThemeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle(ThemePreset.SUMMER_SHRED.storageKey)
    val customColor by viewModel.customThemeColor.collectAsStateWithLifecycle(null)
    val themeFont by viewModel.themeFont.collectAsStateWithLifecycle(ThemeFontOption.BEBAS_NEUE.storageKey)
    val customFontUri by viewModel.customFontUri.collectAsStateWithLifecycle(null)
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle(true)
    val fontImportError by viewModel.fontImportError.collectAsStateWithLifecycle()

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importCustomFont(uri)
        }
    }

    PreviewThemeContent(
        state = PreviewThemeUiState(
            accentKey = themeColor,
            customAccentHex = customColor,
            fontKey = themeFont,
            customFontName = customFontUri?.substringAfterLast('/')?.let(Uri::decode),
            darkMode = darkMode,
            fontImportError = fontImportError
        ),
        onBack = onBack,
        onHome = onHome,
        onPreset = viewModel::setThemeColor,
        onCustomAccent = viewModel::setCustomThemeColor,
        onClearCustomAccent = { viewModel.clearCustomThemeColor(themeColor) },
        onFont = viewModel::setThemeFont,
        onUploadFont = {
            viewModel.clearFontImportError()
            fontPicker.launch(
                arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream")
            )
        },
        onClearCustomFont = { viewModel.clearCustomFont(themeFont) },
        onDarkMode = viewModel::setDarkMode
    )
}
