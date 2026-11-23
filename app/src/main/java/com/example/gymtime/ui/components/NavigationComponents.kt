package com.example.gymtime.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace

@Composable
fun BackNavigationIcon(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HomeNavigationAction(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

data class GuardedNavigationActions(
    val back: () -> Unit,
    val home: () -> Unit
)

private enum class PendingNavigation {
    BACK,
    HOME
}

/**
 * Intercepts toolbar and system Back, plus the explicit Home action, only when
 * leaving would discard meaningful unsaved state.
 */
@Composable
fun rememberGuardedNavigationActions(
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    dialogTitle: String = "Discard changes?",
    dialogMessage: String = "Your unsaved changes will be lost."
): GuardedNavigationActions {
    var pendingNavigation by remember { mutableStateOf<PendingNavigation?>(null) }

    fun request(destination: PendingNavigation) {
        if (hasUnsavedChanges) {
            pendingNavigation = destination
        } else {
            when (destination) {
                PendingNavigation.BACK -> onBack()
                PendingNavigation.HOME -> onHome()
            }
        }
    }

    BackHandler { request(PendingNavigation.BACK) }

    if (pendingNavigation != null) {
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            shape = IronLogRadius.CardShape,
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val destination = pendingNavigation
                        pendingNavigation = null
                        when (destination) {
                            PendingNavigation.BACK -> onBack()
                            PendingNavigation.HOME -> onHome()
                            null -> Unit
                        }
                    },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Discard", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingNavigation = null },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Keep editing")
                }
            },
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant
        )
    }

    return GuardedNavigationActions(
        back = { request(PendingNavigation.BACK) },
        home = { request(PendingNavigation.HOME) }
    )
}
