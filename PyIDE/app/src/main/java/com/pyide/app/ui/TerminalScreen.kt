package com.pyide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pyide.app.IdeViewModel

@Composable
fun TerminalScreen(vm: IdeViewModel) {
    val cs = MaterialTheme.colorScheme
    val prompt = when {
        vm.termWaiting -> "input >"
        vm.replMode -> if (vm.replMore) "..." else ">>>"
        else -> "${vm.cwd.name} \$"
    }
    Column(Modifier.fillMaxSize().background(ConsoleBg)) {
        Row(Modifier.fillMaxWidth().background(cs.surface).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Terminal, null, tint = cs.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.titleMedium)
                Text(vm.cwd.absolutePath, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.termConsole.clear() }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.DeleteSweep, "Clear") }
        }
        ConsoleText(vm.termConsole, Modifier.weight(1f).fillMaxWidth())
        LazyRow(
            Modifier.fillMaxWidth().background(cs.surface).padding(top = 6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val keys: List<Pair<String, () -> Unit>> = listOf(
                "Ctrl+C" to { vm.stop() },
                "up" to { vm.histPrev() },
                "down" to { vm.histNext() },
                "pip install " to { vm.termInput = "pip install " },
                "python " to { vm.termInput = "python " },
                "ls" to { vm.termInput = "ls" },
                "help" to { vm.termInput = "help" },
            )
            items(keys) { (label, action) -> KeyChip(label, action) }
        }
        PromptRow(
            prompt = prompt, hint = "command",
            value = vm.termInput, onValueChange = { vm.termInput = it },
            onSubmit = { vm.termSubmit(it) },
        )
    }
}
