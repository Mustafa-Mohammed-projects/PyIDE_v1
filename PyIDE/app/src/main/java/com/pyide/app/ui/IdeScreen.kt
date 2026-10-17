package com.pyide.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pyide.app.EditorTab
import com.pyide.app.IdeViewModel
import com.pyide.app.core.ConsoleBuffer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun IdeScreen(vm: IdeViewModel) {
    val cs = MaterialTheme.colorScheme
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = cs.surface, modifier = Modifier.width(300.dp)) {
                FilesPanel(vm, onNew = { showNew = true }, onOpen = { vm.open(it); scope.launch { drawer.close() } })
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(cs.background)) {
            IdeTopBar(vm, onMenu = { scope.launch { drawer.open() } })
            if (vm.tabs.isNotEmpty()) TabStrip(vm)
            val tab = vm.tabs.getOrNull(vm.current)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab != null) CodeEditor(tab, Modifier.fillMaxSize())
                else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Code, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Open a file from the menu or create a new one", color = cs.onSurfaceVariant)
                }
            }
            AnimatedVisibility(vm.consoleVisible, modifier = Modifier.weight(0.8f)) {
                ConsolePanel(vm, Modifier.fillMaxSize())
            }
            if (tab != null) ExtraKeys(tab)
        }
    }
    if (showNew) NewFileDialog(onDismiss = { showNew = false }, onCreate = { vm.createFile(it); showNew = false })
}

@Composable
private fun IdeTopBar(vm: IdeViewModel, onMenu: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Files") }
        Column(Modifier.weight(1f)) {
            Text(
                vm.tabs.getOrNull(vm.current)?.file?.name ?: "PyIDE",
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (vm.running) "Running..." else "Python 3.12 - ready",
                style = MaterialTheme.typography.labelSmall,
                color = if (vm.running) cs.tertiary else cs.onSurfaceVariant,
            )
        }
        IconButton(onClick = { vm.saveAll() }) { Icon(Icons.Filled.Save, "Save") }
        IconButton(onClick = { vm.consoleVisible = !vm.consoleVisible }) { Icon(Icons.Filled.Subject, "Output") }
        RunPill(vm.running, onRun = { vm.run() }, onStop = { vm.stop() })
    }
}

@Composable
private fun RunPill(running: Boolean, onRun: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = if (running) Brush.horizontalGradient(listOf(Color(0xFFFF6B7A), Color(0xFFFF9472)))
    else Brush.horizontalGradient(listOf(cs.primary, cs.secondary))
    Row(
        Modifier.padding(start = 4.dp, end = 6.dp).clip(RoundedCornerShape(50)).background(brush)
            .clickable { if (running) onStop() else onRun() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (running) "Stop" else "Run", color = cs.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TabStrip(vm: IdeViewModel) {
    val cs = MaterialTheme.colorScheme
    LazyRow(
        Modifier.fillMaxWidth().background(cs.surface),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(vm.tabs) { i, t ->
            val sel = i == vm.current
            Row(
                Modifier.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (sel) cs.background else Color.Transparent)
                    .clickable { vm.current = i }
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.file.name + if (t.dirty) " *" else "",
                    color = if (sel) cs.onBackground else cs.onSurfaceVariant, fontSize = 13.sp,
                )
                IconButton(onClick = { vm.closeTab(i) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CodeEditor(tab: EditorTab, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lines = tab.text.text.count { it == '\n' } + 1
    val gutter = remember(lines) { (1..lines).joinToString("\n") }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, color = cs.onBackground)
    BoxWithConstraints(modifier.background(cs.background)) {
        val h = maxHeight
        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Text(
                gutter,
                style = style.copy(color = cs.onSurfaceVariant.copy(alpha = 0.55f), textAlign = TextAlign.End),
                modifier = Modifier.width(44.dp).padding(top = 12.dp, end = 8.dp),
            )
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                BasicTextField(
                    value = tab.text,
                    onValueChange = tab::onChange,
                    textStyle = style,
                    cursorBrush = SolidColor(cs.primary),
                    visualTransformation = PythonHighlighter,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.padding(top = 12.dp, bottom = 120.dp, end = 24.dp)
                        .widthIn(min = 320.dp).heightIn(min = h),
                )
            }
        }
    }
}

private val KEYS = listOf(
    Triple("TAB", "    ", 0), Triple("(", "()", 1), Triple(")", ")", 0), Triple("[", "[]", 1),
    Triple("{", "{}", 1), Triple("\"", "\"\"", 1), Triple("'", "''", 1), Triple(":", ":", 0),
    Triple("=", "=", 0), Triple("#", "#", 0), Triple("_", "_", 0), Triple(",", ",", 0),
    Triple(".", ".", 0), Triple("+", "+", 0), Triple("-", "-", 0), Triple("*", "*", 0), Triple("/", "/", 0),
)

@Composable
private fun ExtraKeys(tab: EditorTab) {
    val cs = MaterialTheme.colorScheme
    LazyRow(
        Modifier.fillMaxWidth().background(cs.surface).padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(KEYS) { (label, text, back) -> KeyChip(label) { tab.insert(text, back) } }
    }
}

@Composable
private fun ConsolePanel(vm: IdeViewModel, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    Column(modifier.background(ConsoleBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (vm.running) cs.tertiary else cs.onSurfaceVariant))
            Spacer(Modifier.width(8.dp))
            Text("Output", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.runConsole.clear(); vm.images.clear() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.DeleteSweep, "Clear", Modifier.size(20.dp))
            }
            IconButton(onClick = { vm.consoleVisible = false }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Hide")
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
        ConsoleText(vm.runConsole, Modifier.weight(1f).fillMaxWidth(), vm.images)
        if (vm.runWaiting) {
            PromptRow(
                prompt = "input >", hint = "type, then send",
                value = input, onValueChange = { input = it },
                onSubmit = {
                    vm.runConsole.append(it + "\n", ConsoleBuffer.INPUT)
                    vm.runIO.submit(it); input = ""
                },
            )
        }
    }
}

@Composable
private fun FilesPanel(vm: IdeViewModel, onNew: () -> Unit, onOpen: (File) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var toDelete by remember { mutableStateOf<File?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 6.dp))
            IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "New file", tint = cs.primary) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(vm.files, key = { it.absolutePath }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(f) }.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (f.name.endsWith(".py")) Icons.Filled.Code else Icons.Filled.Description, null,
                        tint = if (f.name.endsWith(".py")) cs.primary else cs.onSurfaceVariant,
                    )
                    Text(f.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { toDelete = f }) { Icon(Icons.Filled.Delete, "Delete", tint = cs.onSurfaceVariant) }
                }
            }
        }
    }
    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${f.name}?") },
            text = { Text("This file will be removed permanently.") },
            confirmButton = { TextButton(onClick = { vm.deleteFile(f); toDelete = null }) { Text("Delete", color = cs.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New file") },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true,
                placeholder = { Text("script.py") },
                keyboardOptions = KeyboardOptions(autoCorrect = false),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
