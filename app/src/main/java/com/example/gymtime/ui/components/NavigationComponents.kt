package com.example.gymtime.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.anodizedMicroGrain

@Composable
fun BackNavigationIcon(onClick: () -> Unit) {
    MachinedNavIconButton(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = MaterialTheme.colorScheme.onSurface,
        highlighted = false,
        onClick = onClick
    )
}

@Composable
fun HomeNavigationAction(onClick: () -> Unit) {
    val surfaces = LocalIronLogSurfaces.current
    MachinedNavIconButton(
        imageVector = Icons.Default.Home,
        contentDescription = "Home",
        tint = surfaces.accent,
        highlighted = true,
        onClick = onClick
    )
}

@Composable
private fun MachinedNavIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.92f,
            highlightAlpha = 0.10f,
            cornerRadius = 24.dp
        )
    }
    val wellFill = remember(surfaces, highlighted) {
        if (highlighted) {
            Brush.verticalGradient(
                listOf(
                    lerp(surfaces.keycapTop, surfaces.accent, if (surfaces.isDark) 0.10f else 0.06f),
                    surfaces.keycapBottom
                )
            )
        } else {
            Brush.verticalGradient(listOf(surfaces.keycapTop, surfaces.keycapBottom))
        }
    }
    val wellBorder = remember(surfaces, highlighted) {
        if (highlighted) {
            IronLogBorder.specularRimStroke(
                surfaces = surfaces,
                highlighted = true,
                width = IronLogBorder.Thin
            )
        } else {
            BorderStroke(
                width = IronLogBorder.Thin,
                brush = Brush.verticalGradient(listOf(surfaces.keycapRimTop, surfaces.keycapShadowLip))
            )
        }
    }

    Box(
        modifier = Modifier
            .size(IronLogSpace.TouchTargetMin)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                role = Role.Button,
                onClick = {
                    haptics.keyTap()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(brush = wellFill)
                .border(border = wellBorder, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
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
        MachinedDiscardChangesDialog(
            title = dialogTitle,
            message = dialogMessage,
            onDismiss = { pendingNavigation = null },
            onDiscard = {
                val destination = pendingNavigation
                pendingNavigation = null
                when (destination) {
                    PendingNavigation.BACK -> onBack()
                    PendingNavigation.HOME -> onHome()
                    null -> Unit
                }
            }
        )
    }

    return GuardedNavigationActions(
        back = { request(PendingNavigation.BACK) },
        home = { request(PendingNavigation.HOME) }
    )
}

@Composable
private fun MachinedDiscardChangesDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current

    val plateBrush = remember(surfaces) {
        Brush.verticalGradient(
            listOf(surfaces.plateElevatedTop, surfaces.plateElevatedBottom)
        )
    }
    val plateBorder = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = false,
            width = IronLogBorder.Hairline
        )
    }
    val discardBrush = remember(surfaces, scheme.error) {
        Brush.verticalGradient(
            listOf(
                lerp(surfaces.keycapTop, scheme.error, if (surfaces.isDark) 0.24f else 0.14f),
                lerp(surfaces.keycapBottom, scheme.error, if (surfaces.isDark) 0.16f else 0.08f)
            )
        )
    }
    val discardBorder = remember(surfaces, scheme.error) {
        BorderStroke(
            width = IronLogBorder.Thin,
            brush = Brush.verticalGradient(
                listOf(
                    scheme.error.copy(alpha = 0.65f),
                    scheme.error.copy(alpha = 0.25f)
                )
            )
        )
    }
    val discardInteraction = remember { MutableInteractionSource() }
    val discardPress = remember {
        MachinedPressIndication(
            pressedScale = 0.95f,
            highlightAlpha = 0.10f,
            cornerRadius = IronLogRadius.Control
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = IronLogRadius.PlateShape,
                    ambientColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.70f else 0.18f),
                    spotColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.75f else 0.22f)
                )
                .clip(IronLogRadius.PlateShape)
                .background(brush = plateBrush)
                .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
                .border(border = plateBorder, shape = IronLogRadius.PlateShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IronLogLedPip(color = scheme.error, size = 6.dp)
                Text(
                    text = "UNSAVED CHANGES",
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.error
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IronLogSculptedKeycap(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = IronLogSpace.TouchTargetMin),
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.ControlShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Keep editing",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .clickable(
                            interactionSource = discardInteraction,
                            indication = discardPress,
                            role = Role.Button,
                            onClick = {
                                haptics.reject()
                                onDiscard()
                            }
                        )
                        .clip(IronLogRadius.ControlShape)
                        .background(brush = discardBrush)
                        .border(border = discardBorder, shape = IronLogRadius.ControlShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Discard",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.error,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
