package com.pyide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pyide.app.IdeViewModel

private val POPULAR = listOf(
    "numpy", "opencv-python", "pandas", "matplotlib", "pillow",
    "scipy", "scikit-learn", "requests", "flask", "beautifulsoup4",
)

@Composable
fun PackagesScreen(vm: IdeViewModel) {
    val cs = MaterialTheme.colorScheme
    var spec by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    fun install(s: String) { vm.pipInstall(s); tab = 1 }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Packages", style = MaterialTheme.typography.titleLarge)
            Text("pip - ${vm.abi}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = spec, onValueChange = { spec = it }, modifier = Modifier.weight(1f),
                singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text("numpy  or  opencv-python==4.9.0") },
                keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (spec.isNotBlank() && !vm.pipBusy) { install(spec); spec = "" } }),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { install(spec); spec = "" },
                enabled = spec.isNotBlank() && !vm.pipBusy,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) { Icon(Icons.Filled.Download, "Install") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(POPULAR) { p ->
                AssistChip(onClick = { if (!vm.pipBusy) install(p) }, label = { Text(p) })
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        if (vm.pipBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.secondary)
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Installed (${vm.installed.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Output") })
        }
        if (tab == 0) {
            if (vm.installed.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No packages yet. Tap a suggestion above to install one.", color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(vm.installed) { line ->
                        val name = line.substringBefore("==")
                        val ver = line.substringAfter("==", "")
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                Text(ver, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = { if (!vm.pipBusy) vm.pipUninstall(name) }) {
                                Icon(Icons.Filled.Delete, "Uninstall", tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            ConsoleText(vm.pipConsole, Modifier.weight(1f).fillMaxWidth().background(ConsoleBg))
        }
    }
}
