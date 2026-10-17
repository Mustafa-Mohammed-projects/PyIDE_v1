package com.pyide.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pyide.app.IdeViewModel

private enum class Dest(val label: String, val icon: ImageVector) {
    Code("Code", Icons.Filled.Code),
    Terminal("Terminal", Icons.Filled.Terminal),
    Packages("Packages", Icons.Filled.Extension),
    Web("Web", Icons.Filled.Language),
}

@Composable
fun AppRoot() {
    val vm: IdeViewModel = viewModel()   // created during the splash so Python warms up
    val cs = MaterialTheme.colorScheme
    var splash by rememberSaveable { mutableStateOf(true) }
    var dest by rememberSaveable { mutableStateOf(0) }

    if (splash) {
        SplashScreen { splash = false }
    } else {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Scaffold(
            containerColor = cs.background,
            bottomBar = {
                if (!imeVisible) {
                    NavigationBar(containerColor = cs.surface) {
                        Dest.values().forEachIndexed { i, d ->
                            NavigationBarItem(
                                selected = dest == i,
                                onClick = { dest = i },
                                icon = { Icon(d.icon, d.label) },
                                label = { Text(d.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = cs.primaryContainer,
                                    selectedIconColor = cs.onPrimaryContainer,
                                    selectedTextColor = cs.primary,
                                    unselectedIconColor = cs.onSurfaceVariant,
                                    unselectedTextColor = cs.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
                when (Dest.values()[dest]) {
                    Dest.Code -> IdeScreen(vm)
                    Dest.Terminal -> TerminalScreen(vm)
                    Dest.Packages -> PackagesScreen(vm)
                    Dest.Web -> WebScreen(vm)
                }
            }
        }
    }
}
