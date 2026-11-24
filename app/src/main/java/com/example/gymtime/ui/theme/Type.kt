package com.example.gymtime.ui.theme

import android.graphics.Typeface as AndroidTypeface
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.gymtime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BebasNeueFontFamily = FontFamily(Font(R.font.bebas_neue_regular))
private val OswaldFontFamily = FontFamily(Font(R.font.oswald_variable))
private val RalewayFontFamily = FontFamily(Font(R.font.raleway_variable))
private val SpaceGroteskFontFamily = FontFamily(Font(R.font.space_grotesk_variable))
private val PacificoFontFamily = FontFamily(Font(R.font.pacifico_regular))

/** Returns the cached built-in [FontFamily] for a [ThemeFontOption]. */
fun fontFamilyForOption(option: ThemeFontOption): FontFamily = when (option) {
    ThemeFontOption.BEBAS_NEUE -> BebasNeueFontFamily
    ThemeFontOption.OSWALD -> OswaldFontFamily
    ThemeFontOption.RALEWAY -> RalewayFontFamily
    ThemeFontOption.SPACE_GROTESK -> SpaceGroteskFontFamily
    ThemeFontOption.PACIFICO -> PacificoFontFamily
    ThemeFontOption.CUSTOM -> FontFamily.Default
}

@Composable
fun rememberAppTypography(
    themeFontKey: String,
    customFontUri: String?
): Typography {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val fontOption = remember(themeFontKey) { ThemeFontOption.fromStorageKey(themeFontKey) }

    val customFontState by produceState<Pair<String?, FontFamily?>?>(
        initialValue = null,
        key1 = fontOption,
        key2 = customFontUri,
        key3 = appContext
    ) {
        if (fontOption != ThemeFontOption.CUSTOM || customFontUri.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val loadedFamily = withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(customFontUri)
                appContext.contentResolver.openFileDescriptor(parsedUri, "r")?.use { pfd ->
                    AndroidTypeface.Builder(pfd.fileDescriptor).build()?.let { typeface ->
                        FontFamily(Typeface(typeface))
                    }
                }
            }.getOrNull()
        }
        value = customFontUri to loadedFamily
    }

    val displayFamily = remember(fontOption, customFontUri, customFontState) {
        when (fontOption) {
            ThemeFontOption.BEBAS_NEUE -> BebasNeueFontFamily
            ThemeFontOption.OSWALD -> OswaldFontFamily
            ThemeFontOption.RALEWAY -> RalewayFontFamily
            ThemeFontOption.SPACE_GROTESK -> SpaceGroteskFontFamily
            ThemeFontOption.PACIFICO -> PacificoFontFamily
            ThemeFontOption.CUSTOM -> {
                val loaded = customFontState?.takeIf { it.first == customFontUri }?.second
                loaded ?: BebasNeueFontFamily
            }
        }
    }

    return remember(displayFamily) { appTypography(displayFamily) }
}

/**
 * Dual-voice typography hierarchy:
 * - Display, headline, and title slots honor the user's selected [displayFamily] for hero names
 *   and sculptural numerals.
 * - Dense body and telemetry label slots use crisp [FontFamily.SansSerif] for razor-sharp
 *   tabular legibility across sets, charts, and controls.
 */
private fun appTypography(displayFamily: FontFamily): Typography {
    val technicalFamily = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            letterSpacing = (-0.01).em
        ),
        displayMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.01).em
        ),
        displaySmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        titleSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.35.sp
        ),
        labelSmall = TextStyle(
            fontFamily = technicalFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp
        )
    )
}
