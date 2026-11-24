package com.example.gymtime.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.anodizedMicroGrain
import kotlin.math.abs

private val BottomNavItems = listOf(
    Screen.Home to "Home",
    Screen.History to "History",
    Screen.Library to "Library",
    Screen.Analytics to "Insights"
)

@Composable
fun BottomNavigationBar(
    navController: NavController,
    currentRoute: String? = navController.currentBackStackEntryAsState().value?.destination?.route
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current

    val selectedIndex = BottomNavItems.indexOfFirst { it.first.route == currentRoute }
    val activeSlotIndex = selectedIndex.coerceAtLeast(0)

    val dockBorder = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = false,
            width = IronLogBorder.Thin
        )
    }
    val activePillBrush = remember(surfaces) {
        Brush.verticalGradient(
            colors = listOf(
                lerp(surfaces.plateElevatedTop, surfaces.accent, if (surfaces.isDark) 0.16f else 0.10f),
                surfaces.plateElevatedBottom
            )
        )
    }
    val activePillBorder = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = true,
            width = IronLogBorder.Thin
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = IronLogRadius.CapsuleShape,
                    ambientColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.72f else 0.16f),
                    spotColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.78f else 0.22f)
                )
                .clip(IronLogRadius.CapsuleShape)
                .background(surfaces.glassDockFill)
                .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.022f else 0.012f)
                .border(border = dockBorder, shape = IronLogRadius.CapsuleShape)
                .padding(4.dp)
        ) {
            val slotCount = BottomNavItems.size
            val slotWidth = maxWidth / slotCount
            val slotWidthPx = with(density) { slotWidth.toPx() }
            val pillX = remember { Animatable(0f) }
            var hasInitializedPill by remember { mutableStateOf(false) }

            val pillSpringPx = remember { IronMotion.magneticDetent<Float>(visibilityThreshold = 0.5f) }

            LaunchedEffect(selectedIndex, slotWidthPx, reducedMotion) {
                if (selectedIndex < 0 || slotWidthPx <= 0f) return@LaunchedEffect
                val targetX = selectedIndex * slotWidthPx
                if (!hasInitializedPill || reducedMotion) {
                    pillX.snapTo(targetX)
                    hasInitializedPill = true
                } else {
                    pillX.animateTo(targetX, pillSpringPx)
                }
            }

            if (selectedIndex >= 0 && slotWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = if (hasInitializedPill) pillX.value else activeSlotIndex * slotWidthPx
                            val stretch = if (reducedMotion) {
                                0f
                            } else {
                                (abs(pillX.velocity) * 0.000035f).coerceAtMost(0.08f)
                            }
                            scaleX = 1f + stretch
                            scaleY = 1f / (1f + stretch * 0.45f)
                        }
                        .clip(IronLogRadius.CapsuleShape)
                        .background(brush = activePillBrush)
                        .drawWithCache {
                            val centerOffset = Offset(size.width * 0.5f, size.height * 0.25f)
                            val glowRadius = (size.width * 0.55f).coerceAtLeast(1f)
                            val glowBrush = Brush.radialGradient(
                                colors = listOf(
                                    surfaces.accent.copy(alpha = if (surfaces.isDark) 0.22f else 0.14f),
                                    Color.Transparent
                                ),
                                center = centerOffset,
                                radius = glowRadius
                            )
                            onDrawBehind {
                                drawCircle(
                                    brush = glowBrush,
                                    radius = glowRadius,
                                    center = centerOffset
                                )
                            }
                        }
                        .border(border = activePillBorder, shape = IronLogRadius.CapsuleShape)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItems.forEach { (screen, label) ->
                    val selected = currentRoute == screen.route
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressIndication = remember {
                        MachinedPressIndication(
                            pressedScale = 0.94f,
                            highlightAlpha = 0.06f,
                            cornerRadius = IronLogRadius.Capsule
                        )
                    }

                    val iconScaleState = animateFloatAsState(
                        targetValue = if (selected) 1.10f else 1f,
                        animationSpec = IronMotion.orSnap(reducedMotion, IronMotion.RewardPop),
                        label = "navIconScale_$label"
                    )
                    val iconTintState = animateColorAsState(
                        targetValue = if (selected) surfaces.accent else scheme.onSurfaceVariant,
                        animationSpec = IronMotion.orSnap(reducedMotion, IronMotion.effectFast()),
                        label = "navIconTint_$label"
                    )
                    val labelColorState = animateColorAsState(
                        targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                        animationSpec = IronMotion.orSnap(reducedMotion, IronMotion.effectFast()),
                        label = "navLabelColor_$label"
                    )
                    val labelStyle = remember(selected) {
                        IronLogType.telemetryLabel().copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.05.em
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = IronLogSpace.TouchTargetMin)
                            .clip(IronLogRadius.CapsuleShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = pressIndication
                            ) {
                                if (selected) return@selectable
                                haptics.segmentTick()
                                navController.navigateToBottomTab(screen.route)
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = rememberVectorPainter(screen.icon),
                            tint = { iconTintState.value },
                            contentDescription = null,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    val s = iconScaleState.value
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        BasicText(
                            text = label,
                            style = labelStyle,
                            color = { labelColorState.value },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
