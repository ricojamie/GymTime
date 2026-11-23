package com.example.gymtime.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.ai.AiCapability
import com.example.gymtime.ai.AiDownloadState
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType

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

    val scheme = MaterialTheme.colorScheme
    val isDownloading = capability == AiCapability.Downloading ||
        downloadState is AiDownloadState.Started ||
        downloadState is AiDownloadState.Progress
    val failed = downloadState as? AiDownloadState.Failed

    IronLogCard(
        modifier = modifier.fillMaxWidth(),
        showBorder = true,
        containerColor = scheme.surfaceContainerLow,
        borderColor = IronLogBorder.borderColor(scheme),
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Text(
            text = "Enable on-device AI",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
        Text(
            text = when {
                failed != null -> "The model couldn't download. Your local summaries and logging still work without it."
                isDownloading -> downloadProgressText(downloadState)
                else -> "Download Gemini Nano for smarter local summaries and Smart Log. Your training data stays on this phone."
            },
            color = scheme.onSurfaceVariant,
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(IronLogSpace.CompactGap)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IronLogSecondaryButton(
                    text = "Not now",
                    onClick = viewModel::dismiss,
                    outlined = true,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                )
                IronLogPrimaryButton(
                    text = if (failed != null) "Retry" else "Download",
                    onClick = viewModel::download,
                    modifier = Modifier.weight(1f)
                )
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
