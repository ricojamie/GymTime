package com.example.gymtime.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.R
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.ThemeFontOption
import com.example.gymtime.ui.theme.ThemePreset
import kotlin.math.atan2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeColor by viewModel.themeColor.collectAsState(initial = ThemePreset.SUMMER_SHRED.storageKey)
    val customThemeColor by viewModel.customThemeColor.collectAsState(initial = null)
    val themeFont by viewModel.themeFont.collectAsState(initial = ThemeFontOption.BEBAS_NEUE.storageKey)
    val customFontUri by viewModel.customFontUri.collectAsState(initial = null)

    val context = LocalContext.current
    var showCustomColorDialog by remember { mutableStateOf(false) }

    val customFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.setCustomFontUri(uri.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme", color = LocalAppColors.current.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateHomeAndClearStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LocalAppColors.current.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                SectionHeader("Accent")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Choose one of the 3 presets or open the custom color wheel.",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 13.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ThemePreset.entries.forEach { preset ->
                                ThemePresetCard(
                                    modifier = Modifier.weight(1f),
                                    name = preset.name.replace("_", " "),
                                    scheme = ThemeColors.getScheme(preset.storageKey),
                                    isSelected = themeColor == preset.storageKey,
                                    onClick = { viewModel.setThemeColor(preset.storageKey) }
                                )
                            }
                        }

                        CustomThemeCard(
                            selected = themeColor == "custom",
                            customColor = customThemeColor?.let(::hexToColor),
                            onClick = { showCustomColorDialog = true }
                        )
                    }
                }
            }

            item {
                SectionHeader("Fonts")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeFontOption.entries.filter { it != ThemeFontOption.CUSTOM }.forEach { option ->
                            FontOptionRow(
                                option = option,
                                selected = themeFont == option.storageKey,
                                onClick = { viewModel.setThemeFont(option.storageKey) }
                            )
                        }

                        HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.12f))

                        Text(
                            text = "Custom font",
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalAppColors.current.textPrimary
                        )
                        Text(
                            text = customFontUri?.substringAfterLast('/') ?: "Upload a .ttf or .otf file",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    customFontLauncher.launch(
                                        arrayOf(
                                            "font/*",
                                            "application/x-font-ttf",
                                            "application/x-font-opentype",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Font")
                            }

                            if (customFontUri != null) {
                                OutlinedButton(
                                    onClick = { viewModel.setThemeFont(ThemeFontOption.CUSTOM.storageKey) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use Custom")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColor = customThemeColor?.let(::hexToColor) ?: ThemeColors.SummerShred.primaryAccent,
            onDismiss = { showCustomColorDialog = false },
            onSave = { color ->
                viewModel.setCustomThemeColor(colorToHex(color))
                showCustomColorDialog = false
            }
        )
    }
}

@Composable
private fun ThemePresetCard(
    modifier: Modifier = Modifier,
    name: String,
    scheme: com.example.gymtime.ui.theme.AppColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.inputBackground),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, scheme.primaryAccent)
        } else null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Brush.verticalGradient(listOf(scheme.gradientStart, scheme.gradientEnd)))
            ) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryAccent)
                )
            }
            Text(
                text = name.lowercase().replaceFirstChar { it.titlecase() },
                modifier = Modifier.padding(12.dp),
                color = LocalAppColors.current.textPrimary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun CustomThemeCard(
    selected: Boolean,
    customColor: Color?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.inputBackground),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, customColor ?: MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(customColor ?: Color.White)
                        .border(1.dp, LocalAppColors.current.textTertiary.copy(alpha = 0.2f), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Custom", color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        customColor?.let(::colorToHex) ?: "Open color wheel",
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "Edit",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun FontOptionRow(
    option: ThemeFontOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val previewFamily = when (option) {
        ThemeFontOption.BEBAS_NEUE -> FontFamily(Font(R.font.bebas_neue_regular))
        ThemeFontOption.OSWALD -> FontFamily(Font(R.font.oswald_variable))
        ThemeFontOption.RALEWAY -> FontFamily(Font(R.font.raleway_variable))
        ThemeFontOption.SPACE_GROTESK -> FontFamily(Font(R.font.space_grotesk_variable))
        ThemeFontOption.PACIFICO -> FontFamily(Font(R.font.pacifico_regular))
        ThemeFontOption.CUSTOM -> FontFamily.Default
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = option.displayName,
                color = LocalAppColors.current.textPrimary,
                fontFamily = previewFamily,
                fontSize = 20.sp
            )
            Text(
                text = "The quick brown fox",
                color = LocalAppColors.current.textSecondary,
                fontFamily = previewFamily,
                fontSize = 12.sp
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomColorDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onSave: (Color) -> Unit
) {
    var hue by remember { mutableFloatStateOf(initialColor.toHsv()[0]) }
    var saturation by remember { mutableFloatStateOf(initialColor.toHsv()[1]) }
    var value by remember { mutableFloatStateOf(initialColor.toHsv()[2]) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    val selectedColor = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }

    LaunchedEffect(selectedColor) {
        hexInput = colorToHex(selectedColor)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Accent", color = LocalAppColors.current.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HueWheel(
                    hue = hue,
                    onHueChange = { hue = it }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(selectedColor)
                )

                Column {
                    Text("Saturation", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                    Slider(value = saturation, onValueChange = { saturation = it })
                }

                Column {
                    Text("Brightness", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                    Slider(value = value, onValueChange = { value = it })
                }

                BasicTextField(
                    value = hexInput,
                    onValueChange = { candidate ->
                        hexInput = candidate
                        parseHexColor(candidate)?.let { parsed ->
                            val hsv = parsed.toHsv()
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                        }
                    },
                    textStyle = TextStyle(
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(LocalAppColors.current.cursor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.inputBackground, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedColor) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Text("Use Color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppColors.current.textSecondary)
            }
        },
        containerColor = LocalAppColors.current.surfaceCards
    )
}

@Composable
private fun HueWheel(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    val ringColors = remember {
        (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        onHueChange(angleForOffset(position, size.width, size.height))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onHueChange(angleForOffset(change.position, size.width, size.height))
                    }
                }
        ) {
            val strokeWidth = 28.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth / 2f
            drawCircle(
                brush = Brush.sweepGradient(ringColors),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val indicatorRadians = Math.toRadians(hue.toDouble())
            val indicator = Offset(
                x = center.x + kotlin.math.cos(indicatorRadians).toFloat() * radius,
                y = center.y + kotlin.math.sin(indicatorRadians).toFloat() * radius
            )
            drawCircle(Color.White, radius = 10.dp.toPx(), center = indicator)
            drawCircle(Color.Black, radius = 6.dp.toPx(), center = indicator)
        }
    }
}

private fun angleForOffset(position: Offset, width: Int, height: Int): Float {
    val center = Offset(width / 2f, height / 2f)
    val offset = position - center
    val angle = Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat()
    return (angle + 360f) % 360f
}

private fun Color.toHsv(): FloatArray {
    val red = red
    val green = green
    val blue = blue
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == red -> ((green - blue) / delta).mod(6f) * 60f
        max == green -> (((blue - red) / delta) + 2f) * 60f
        else -> (((red - green) / delta) + 4f) * 60f
    }
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(if (hue < 0f) hue + 360f else hue, saturation, max)
}

private fun colorToHex(color: Color): String {
    return "#%02X%02X%02X".format(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}

private fun parseHexColor(value: String): Color? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6) return null
    return runCatching {
        val red = normalized.substring(0, 2).toInt(16)
        val green = normalized.substring(2, 4).toInt(16)
        val blue = normalized.substring(4, 6).toInt(16)
        Color(red / 255f, green / 255f, blue / 255f)
    }.getOrNull()
}

private fun hexToColor(value: String): Color {
    return parseHexColor(value) ?: ThemeColors.SummerShred.primaryAccent
}
