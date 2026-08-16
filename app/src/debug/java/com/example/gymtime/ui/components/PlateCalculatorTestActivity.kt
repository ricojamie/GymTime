package com.example.gymtime.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.gymtime.ui.theme.IronLogTheme

/** Debug-only host for isolated Compose tests and local screenshot QA. */
class PlateCalculatorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_VISUAL_QA, false)) {
            setContent {
                IronLogTheme {
                    PlateCalculatorContent(
                        initialWeight = 185f,
                        barWeight = 45f,
                        availablePlates = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
                        loadingSides = 2,
                        onDismiss = { finish() },
                        onNavigateToSettings = {},
                        onUseWeight = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private companion object {
        const val EXTRA_VISUAL_QA = "visualQa"
    }
}
