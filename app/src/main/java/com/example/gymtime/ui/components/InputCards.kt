package com.example.gymtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.SurfaceCards
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary

@Composable
fun InputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lastValue: String? = null,
    lastLabel: String = "BEST"
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header row with label and last value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )

                lastValue?.let {
                    Text(
                        text = "$lastLabel: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Input Area - Using BasicTextField for better control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Fill remaining space
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (value.isBlank()) {
                    val placeholder = if (label.contains("HH:MM:SS")) "00:00:00" else if (label.contains("MI")) "0.00" else "0"
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontSize = 34.sp,
                            color = TextTertiary.copy(alpha = 0.3f)
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 34.sp,
                        color = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TimeInputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lastValue: String? = null,
    lastLabel: String = "LAST"
) {
    // Parse current value (Format: HH:MM:SS)
    // If empty or invalid, default to empty strings
    val parts = value.split(":")
    val hh = parts.getOrNull(0) ?: ""
    val mm = parts.getOrNull(1) ?: ""
    val ss = parts.getOrNull(2) ?: ""

    val focusHH = remember { FocusRequester() }
    val focusMM = remember { FocusRequester() }
    val focusSS = remember { FocusRequester() }

    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
                lastValue?.let {
                    Text(
                        text = "$lastLabel: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // HH
                TimeSegmentField(
                    value = hh,
                    placeholder = "00",
                    focusRequester = focusHH,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }.take(2)
                        onValueChange("$filtered:$mm:$ss")
                        if (filtered.length == 2) focusMM.requestFocus()
                    },
                    modifier = Modifier.onKeyEvent { 
                        false 
                    }
                )
                
                Text(":", style = MaterialTheme.typography.displaySmall, color = TextTertiary.copy(alpha = 0.5f))

                // MM
                TimeSegmentField(
                    value = mm,
                    placeholder = "00",
                    focusRequester = focusMM,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }.take(2)
                        val capped = if (filtered.isNotEmpty()) filtered.toInt().coerceAtMost(59).toString().padStart(filtered.length, '0') else ""
                        onValueChange("$hh:$capped:$ss")
                        if (capped.length == 2 || (capped.length == 1 && capped.toInt() > 5)) focusSS.requestFocus()
                    },
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Backspace && mm.isEmpty()) {
                            focusHH.requestFocus()
                            true
                        } else false
                    }
                )

                Text(":", style = MaterialTheme.typography.displaySmall, color = TextTertiary.copy(alpha = 0.5f))

                // SS
                TimeSegmentField(
                    value = ss,
                    placeholder = "00",
                    focusRequester = focusSS,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }.take(2)
                        val capped = if (filtered.isNotEmpty()) filtered.toInt().coerceAtMost(59).toString().padStart(filtered.length, '0') else ""
                        onValueChange("$hh:$mm:$capped")
                    },
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Backspace && ss.isEmpty()) {
                            focusMM.requestFocus()
                            true
                        } else false
                    }
                )
            }
        }
    }
}

@Composable
fun TimeSegmentField(
    value: String,
    placeholder: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.width(50.dp)) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 30.sp,
                    color = TextTertiary.copy(alpha = 0.2f)
                )
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 30.sp,
                color = TextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.focusRequester(focusRequester)
        )
    }
}
