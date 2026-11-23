package com.example.gymtime.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace

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
    val border = remember(scheme.outlineVariant) {
        IronLogBorder.stroke(scheme.outlineVariant)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = IronLogSpace.ItemGap)
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = IronLogRadius.SheetShape,
            color = scheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = border
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItems.forEach { (screen, label) ->
                    val selected = currentRoute == screen.route
                    val contentColor = if (selected) {
                        scheme.onPrimaryContainer
                    } else {
                        scheme.onSurfaceVariant
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(IronLogRadius.CardShape)
                            .background(if (selected) scheme.primaryContainer else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab) {
                                if (selected) return@selectable
                                if (screen == Screen.Home) {
                                    navController.navigateHomeAndClearStack()
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            inclusive = false
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
