package com.example.gymtime.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.components.GlowCard
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.overlayingComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.DashedShape
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import kotlinx.coroutines.launch

@Composable
fun MetricSelector(
    selected: AnalyticsMetric,
    onSelect: (AnalyticsMetric) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalyticsMetric.values().forEach { metric ->
            val isSelected = selected == metric
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) accentColor else Color(0xFF0D0D0D))
                    .clickable { onSelect(metric) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = metric.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.Black else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSelector(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D0D0D))
                .clickable { showSheet = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selected.ifEmpty { "Select Target" },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = accentColor
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            TargetSelectionContent(
                options = options,
                onSelect = {
                    onSelect(it)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showSheet = false
                    }
                },
                onDismiss = {
                     scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showSheet = false
                    }
                }
            )
        }
    }
}

@Composable
fun TargetSelectionContent(
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isEmpty()) options
        else options.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp) // Fixed height for the sheet content
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Target",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(filteredOptions) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
                // Divider
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333333)))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MainLineChart(data: ChartData) {
    val accentColor = MaterialTheme.colorScheme.primary
    val markerLabelMap = remember(data) {
        data.actuals.associate { it.date.toFloat() to it.label }
    }

    GlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        onClick = {}
    ) {
        if (data.actuals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data available", color = Color.Gray)
            }
        } else {
            val actualEntries = data.actuals.map { FloatEntry(it.date.toFloat(), it.value) }
            val trendEntries = data.trend.map { FloatEntry(it.date.toFloat(), it.value) }
            
            // Create model with multiple series if trend exists
            val chartEntryModel = if (trendEntries.isNotEmpty()) {
                entryModelOf(actualEntries, trendEntries)
            } else {
                entryModelOf(actualEntries)
            }
            
            val bottomAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                data.actuals.getOrNull(value.toInt())?.label ?: ""
            }
            
            val startAxisFormatter = AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
                String.format("%,.0f", value)
            }

            // Define lines
            val actualLine = LineChart.LineSpec(
                lineColor = accentColor.toArgb(),
                lineThicknessDp = 3f
            )
            
            val trendLine = LineChart.LineSpec(
                lineColor = Color.White.copy(alpha = 0.5f).toArgb(),
                lineThicknessDp = 2f
            )

            val lines = if (trendEntries.isNotEmpty()) listOf(actualLine, trendLine) else listOf(actualLine)
            
            val marker = rememberMarker(markerLabelMap)
            
            Chart(
                chart = lineChart(
                    lines = lines,
                    spacing = 40.dp
                ),
                model = chartEntryModel,
                startAxis = rememberStartAxis(
                    valueFormatter = startAxisFormatter
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = bottomAxisFormatter
                ),
                marker = marker,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
internal fun rememberMarker(labelMap: Map<Float, String>): Marker {
    val accentColor = MaterialTheme.colorScheme.primary
    val labelBackgroundColor = Color(0xFF1E1E1E)
    val labelBackground = shapeComponent(
        shape = Shapes.pillShape,
        color = labelBackgroundColor
    )
    val label = textComponent(
        background = labelBackground,
        lineCount = 2,
        padding = dimensionsOf(8.dp, 4.dp),
        color = Color.White,
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    )
    val indicatorInner = shapeComponent(
        shape = Shapes.pillShape,
        color = accentColor
    )
    val indicatorCenter = shapeComponent(
        shape = Shapes.pillShape,
        color = Color.White
    )
    val indicatorOuter = shapeComponent(
        shape = Shapes.pillShape,
        color = Color.White
    )
    val indicator = overlayingComponent(
        outer = indicatorOuter,
        inner = overlayingComponent(
            outer = indicatorCenter,
            inner = indicatorInner,
            innerPaddingAll = 3.dp
        ),
        innerPaddingAll = 2.dp
    )
    val guideline = lineComponent(
        color = Color.White.copy(alpha = 0.2f),
        thickness = 2.dp,
        shape = DashedShape(Shapes.pillShape, 10f, 5f)
    )

    return remember(label, indicator, guideline) {
        object : MarkerComponent(label, indicator, guideline) {
            init {
                labelFormatter = MarkerLabelFormatter { markedEntries, _ ->
                    val x = markedEntries.firstOrNull()?.entry?.x ?: return@MarkerLabelFormatter ""
                    val y = markedEntries.firstOrNull()?.entry?.y ?: return@MarkerLabelFormatter ""
                    val dateLabel = labelMap[x] ?: ""
                    val formattedValue = String.format("%,.0f", y)
                    "$dateLabel\n$formattedValue"
                }
            }
        }
    }
}

@Composable
fun StatsSummaryRow(current: String, max: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(label = "CURRENT", value = current)
        StatItem(label = "MAX", value = max)
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.values().forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text = range.displayName(),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor,
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF0D0D0D),
                    labelColor = accentColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = accentColor,
                    selectedBorderColor = accentColor,
                    enabled = true,
                    selected = selectedRange == range
                )
            )
        }
    }
}