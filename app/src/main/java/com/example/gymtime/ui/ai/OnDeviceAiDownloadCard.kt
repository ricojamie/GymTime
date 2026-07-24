package com.example.gymtime.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ai.AiCapability
import com.example.gymtime.ai.AiDownloadState
import com.example.gymtime.ui.theme.LocalAppColors

/**
 * Optional first-use setup UI. Host screens continue showing deterministic
 * content when this card is dismissed or the model is unavailable.
 */
@Composable
fun OnDeviceAiDownloadCard(
    modifier: Modifier = Modifier,
    viewModel: OnDeviceAiSetupViewModel = hiltViewModel()
) {
    val capability by viewModel.capability.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    if (!viewModel.shouldShow(capability, downloadState, dismissed)) return

    val isDownloading = capability == AiCapability.Downloading ||
        downloadState is AiDownloadState.Started ||
        downloadState is AiDownloadState.Progress
    val failed = downloadState as? AiDownloadState.Failed

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Enable on-device AI", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    failed != null -> "The model couldn't download. Your local summaries and logging still work without it."
                    isDownloading -> downloadProgressText(downloadState)
                    else -> "Download Gemini Nano for smarter local summaries and Smart Log. Your training data stays on this phone."
                },
                color = LocalAppColors.current.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    OutlinedButton(onClick = viewModel::dismiss) { Text("Not now") }
                    Button(onClick = viewModel::download) {
                        Text(if (failed != null) "Retry" else "Download")
                    }
                }
            }
        }
    }
}

private fun downloadProgressText(state: AiDownloadState): String = when (state) {
    is AiDownloadState.Started -> "Downloading the on-device model..."
    is AiDownloadState.Progress -> {
        val total = state.totalBytes
        if (total != null && total > 0) {
            val percent = ((state.bytesDownloaded * 100L) / total).coerceIn(0L, 100L)
            "Downloading the on-device model... $percent%"
        } else {
            "Downloading the on-device model..."
        }
    }
    else -> "Downloading the on-device model..."
}
