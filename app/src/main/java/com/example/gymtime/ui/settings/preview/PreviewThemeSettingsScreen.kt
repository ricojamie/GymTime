package com.example.gymtime.ui.settings.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.ui.settings.SettingsViewModel
import com.example.gymtime.ui.theme.LoggerPreviewTheme
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
    val context = LocalContext.current
    var fontImportError by rememberSaveable { mutableStateOf<String?>(null) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onSuccess {
                fontImportError = null
                viewModel.setCustomFontUri(uri.toString())
            }.onFailure {
                fontImportError = "Couldn't keep access to this font. Choose a file from your device storage."
            }
        }
    }

    LoggerPreviewTheme {
        PreviewThemeContent(
            state = PreviewThemeUiState(
                accentKey = themeColor,
                customAccentHex = customColor,
                fontKey = themeFont,
                customFontName = customFontUri?.substringAfterLast('/')?.let(android.net.Uri::decode),
                darkMode = darkMode,
                fontImportError = fontImportError
            ),
            onBack = onBack,
            onHome = onHome,
            onPreset = viewModel::setThemeColor,
            onCustomAccent = viewModel::setCustomThemeColor,
            onClearCustomAccent = {
                if (themeColor == "custom") viewModel.setThemeColor(ThemePreset.SUMMER_SHRED.storageKey)
                viewModel.clearCustomThemeColor()
            },
            onFont = viewModel::setThemeFont,
            onUploadFont = {
                fontImportError = null
                fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream"))
            },
            onClearCustomFont = {
                if (themeFont == ThemeFontOption.CUSTOM.storageKey) viewModel.setThemeFont(ThemeFontOption.BEBAS_NEUE.storageKey)
                viewModel.setCustomFontUri(null)
                fontImportError = null
            },
            onDarkMode = viewModel::setDarkMode
        )
    }
}
