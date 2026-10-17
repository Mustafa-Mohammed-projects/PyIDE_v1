package com.pyide.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pyide.app.core.ConsoleBuffer
import com.pyide.app.core.ConsoleIO
import com.pyide.app.core.PythonEngine
import com.pyide.app.core.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EditorTab(val file: File, initial: String) {
    var text by mutableStateOf(TextFieldValue(initial))
    var dirty by mutableStateOf(false)

    fun onChange(new: TextFieldValue) {
        val old = text
        var v = new
        // auto-indent after Enter
        if (new.text.length == old.text.length + 1 && new.selection.collapsed) {
            val pos = new.selection.start
            if (pos > 0 && new.text[pos - 1] == '\n') {
                val before = new.text.substring(0, pos - 1)
                val prev = before.substring(before.lastIndexOf('\n') + 1)
                var indent = prev.takeWhile { it == ' ' || it == '\t' }
                if (prev.trimEnd().endsWith(":")) indent += "    "
                if (indent.isNotEmpty()) {
                    val t = new.text.substring(0, pos) + indent + new.text.substring(pos)
                    v = new.copy(text = t, selection = TextRange(pos + indent.length))
                }
            }
        }
        if (v.text != old.text) dirty = true
        text = v
    }

    fun insert(s: String, back: Int = 0) {
        val sel = text.selection
        val t = text.text.replaceRange(sel.min, sel.max, s).toString()
        text = TextFieldValue(t, TextRange(sel.min + s.length - back))
        dirty = true
    }
}

class IdeViewModel(app: Application) : AndroidViewModel(app) {
    val workspace = Workspace(app)
    private val engine = PythonEngine(app)
    val abi: String get() = engine.abi

    // consoles
    val runConsole = ConsoleBuffer(viewModelScope)
    val termConsole = ConsoleBuffer(viewModelScope)
    val pipConsole = ConsoleBuffer(viewModelScope)
    val images = mutableStateListOf<String>()

    // editor
    val tabs = mutableStateListOf<EditorTab>()
    var current by mutableStateOf(0)
    var files by mutableStateOf<List<File>>(emptyList())
    var running by mutableStateOf(false)
    var consoleVisible by mutableStateOf(false)
    var runWaiting by mutableStateOf(false)

    // terminal
    var cwd by mutableStateOf(workspace.root)
    var termInput by mutableStateOf("")
    var termBusy by mutableStateOf(false)
    var termWaiting by mutableStateOf(false)
    var replMode by mutableStateOf(false)
    var replMore by mutableStateOf(false)
    private val history = mutableListOf<String>()
    private var histIdx = 0
    @Volatile private var shellProc: Process? = null

    // packages
    val installed = mutableStateListOf<String>()
    var pipBusy by mutableStateOf(false)

    // web
    var webUrl by mutableStateOf("http://127.0.0.1:8000")

    private val pyBusy = AtomicBoolean(false)

    val runIO = ConsoleIO(runConsole, { addImage(it) }, { runWaiting = it })
    val termIO = ConsoleIO(
        termConsole,
        { addImage(it); termConsole.append("[image shown in the Code tab output]\n", ConsoleBuffer.INFO) },
        { termWaiting = it },
    )
    private val pipIO = ConsoleIO(pipConsole, {}, {})

    init {
        viewModelScope.launch(Dispatchers.IO) {
            workspace.seed()
            withContext(Dispatchers.Main) {
                refreshFiles()
                File(workspace.root, "hello.py").takeIf { it.exists() }?.let { open(it) }
            }
            runCatching { engine.warmUp() }.onFailure {
                runConsole.append("Python failed to start: ${it.message}\n", ConsoleBuffer.ERR)
            }
            refreshInstalled()
        }
    }

    private fun addImage(path: String) {
        viewModelScope.launch { images.add(path); consoleVisible = true }
    }

    // ------------------------------------------------------------ files
    fun refreshFiles() { files = workspace.list() }

    fun open(f: File) {
        val i = tabs.indexOfFirst { it.file == f }
        if (i >= 0) { current = i; return }
        tabs.add(EditorTab(f, workspace.read(f)))
        current = tabs.lastIndex
    }

    fun closeTab(i: Int) {
        val t = tabs.getOrNull(i) ?: return
        if (t.dirty) workspace.write(t.file, t.text.text)
        tabs.removeAt(i)
        current = current.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
    }

    fun saveAll() {
        tabs.forEach { if (it.dirty) { workspace.write(it.file, it.text.text); it.dirty = false } }
    }

    fun createFile(raw: String) {
        var n = raw.trim().replace(Regex("[\\\\/]"), "_")
        if (n.isEmpty()) return
        if (!n.contains('.')) n += ".py"
        val f = File(workspace.root, n)
        if (!f.exists()) f.writeText(if (n.endsWith(".py")) "# $n\n\n" else "")
        refreshFiles()
        open(f)
    }

    fun deleteFile(f: File) {
        tabs.indexOfFirst { it.file == f }.takeIf { it >= 0 }?.let { tabs.removeAt(it) }
        current = current.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
        f.delete()
        refreshFiles()
    }

    // ------------------------------------------------------------ run
    private fun claim() = pyBusy.compareAndSet(false, true)

    fun run() {
        val tab = tabs.getOrNull(current) ?: return
        saveAll()
        consoleVisible = true
        if (!claim()) {
            runConsole.append("Another Python task is running. Press Stop first.\n", ConsoleBuffer.ERR)
            return
        }
        runConsole.clear(); images.clear(); runIO.reset()
        running = true
        runConsole.append("Running ${tab.file.name}\n", ConsoleBuffer.INFO)
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                val rc = engine.runFile(tab.file.absolutePath, runIO)
                val s = "%.1f".format((System.currentTimeMillis() - t0) / 1000.0)
                runConsole.append("\nFinished (exit code $rc) in ${s}s\n", ConsoleBuffer.INFO)
            } catch (e: Throwable) {
                runConsole.append("\n${e.message ?: e.toString()}\n", ConsoleBuffer.ERR)
            } finally {
                running = false; runWaiting = false; pyBusy.set(false)
            }
        }
    }

    fun stop() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine.stop() } }
        runIO.cancel(); termIO.cancel()
        shellProc?.destroy()
    }

    // ------------------------------------------------------------ terminal
    fun histPrev() {
        if (history.isNotEmpty() && histIdx > 0) { histIdx--; termInput = history[histIdx] }
    }

    fun histNext() {
        if (histIdx < history.size - 1) { histIdx++; termInput = history[histIdx] }
        else { histIdx = history.size; termInput = "" }
    }

    fun termSubmit(line: String) {
        termInput = ""
        if (termIO.waiting) {
            termConsole.append(line + "\n", ConsoleBuffer.INPUT)
            termIO.submit(line)
            return
        }
        if (termBusy) return
        val prompt = if (replMode) (if (replMore) "... " else ">>> ") else "${cwd.name} \$ "
        termConsole.append(prompt + line + "\n", ConsoleBuffer.INPUT)
        if (replMode) { replLine(line); return }
        if (line.isBlank()) return
        history.add(line); histIdx = history.size
        execCommand(line.trim())
    }

    private fun resolve(p: String): File = File(p).let { if (it.isAbsolute) it else File(cwd, p) }

    private fun execCommand(line: String) {
        val a = tokenize(line)
        if (a.isEmpty()) return
        when (a[0]) {
            "clear", "cls" -> termConsole.clear()
            "pwd" -> termConsole.append(cwd.absolutePath + "\n")
            "cd" -> {
                val t = if (a.size < 2 || a[1] == "~") workspace.root else resolve(a[1])
                if (t.isDirectory) cwd = t.canonicalFile
                else termConsole.append("cd: no such directory: ${a.getOrNull(1)}\n", ConsoleBuffer.ERR)
            }
            "help" -> termConsole.append(
                "Built-in: pip, python, cd, pwd, clear, help\n" +
                    "python            interactive REPL\n" +
                    "python file.py    run a script\n" +
                    "python -c \"...\"   run code\n" +
                    "python -m mod     run a module (e.g. http.server)\n" +
                    "pip install pkg   install a package (e.g. opencv-python)\n" +
                    "Anything else runs in the Android shell (ls, cat, mkdir...).\n",
                ConsoleBuffer.INFO,
            )
            "pip", "pip3" -> pyTask { engine.pip(a.drop(1), termIO) }
            "python", "python3", "py" -> pythonCmd(a.drop(1))
            else -> shell(line)
        }
    }

    private fun pythonCmd(a: List<String>) {
        when {
            a.isEmpty() -> {
                replMode = true; replMore = false
                termConsole.append("Python REPL - type exit() to leave\n", ConsoleBuffer.INFO)
                viewModelScope.launch(Dispatchers.IO) { runCatching { engine.replReset() } }
            }
            a[0] == "-m" && a.size >= 2 ->
                if (a[1] == "pip") pyTask { engine.pip(a.drop(2), termIO) }
                else pyTask { engine.runModule(a[1], cwd.absolutePath, termIO, a.drop(2)) }
            a[0] == "-c" && a.size >= 2 -> pyTask { engine.runCode(a[1], cwd.absolutePath, termIO) }
            else -> {
                val f = resolve(a[0])
                if (!f.isFile) termConsole.append("python: can't open file '${a[0]}'\n", ConsoleBuffer.ERR)
                else pyTask { engine.runFile(f.absolutePath, termIO, a.drop(1)) }
            }
        }
    }

    private fun replLine(line: String) {
        if (line.trim() == "exit()" || line.trim() == "quit()") {
            replMode = false; replMore = false; return
        }
        if (!claim()) { termConsole.append("Another Python task is running\n", ConsoleBuffer.ERR); return }
        termBusy = true; termIO.reset()
        viewModelScope.launch(Dispatchers.IO) {
            try { replMore = engine.replPush(line, termIO) }
            catch (e: Throwable) { termConsole.append((e.message ?: e.toString()) + "\n", ConsoleBuffer.ERR) }
            finally { termBusy = false; termWaiting = false; pyBusy.set(false) }
        }
    }

    private fun pyTask(block: () -> Int) {
        if (!claim()) { termConsole.append("Another Python task is running (tap Ctrl+C)\n", ConsoleBuffer.ERR); return }
        termBusy = true; termIO.reset()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rc = block()
                if (rc != 0) termConsole.append("[exit $rc]\n", ConsoleBuffer.INFO)
            } catch (e: Throwable) {
                termConsole.append((e.message ?: e.toString()) + "\n", ConsoleBuffer.ERR)
            } finally {
                termBusy = false; termWaiting = false; pyBusy.set(false)
                refreshInstalled()
            }
        }
    }

    private fun shell(line: String) {
        termBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", line).directory(cwd).redirectErrorStream(true)
                pb.environment()["HOME"] = workspace.root.absolutePath
                val p = pb.start()
                shellProc = p
                p.outputStream.close()
                p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(2048)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        termConsole.append(String(buf, 0, n))
                    }
                }
                p.waitFor()
            } catch (e: Throwable) {
                termConsole.append((e.message ?: e.toString()) + "\n", ConsoleBuffer.ERR)
            } finally {
                shellProc = null; termBusy = false
            }
        }
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var has = false
        for (c in s) {
            when {
                q != null -> if (c == q) q = null else sb.append(c)
                c == '"' || c == '\'' -> { q = c; has = true }
                c.isWhitespace() -> if (sb.isNotEmpty() || has) { out += sb.toString(); sb.clear(); has = false }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty() || has) out += sb.toString()
        return out
    }

    // ------------------------------------------------------------ packages
    fun pipInstall(spec: String) {
        val args = tokenize(spec)
        if (args.isNotEmpty()) runPip(listOf("install") + args)
    }

    fun pipUninstall(name: String) = runPip(listOf("uninstall", name))

    private fun runPip(args: List<String>) {
        if (!claim()) { pipConsole.append("Another Python task is running. Press Stop first.\n", ConsoleBuffer.ERR); return }
        pipBusy = true
        pipConsole.append("pip ${args.joinToString(" ")}\n", ConsoleBuffer.INFO)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.pip(args, pipIO)
            } catch (e: Throwable) {
                pipConsole.append((e.message ?: e.toString()) + "\n", ConsoleBuffer.ERR)
            } finally {
                pipBusy = false; pyBusy.set(false)
                refreshInstalled()
            }
        }
    }

    fun refreshInstalled() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching { engine.listPackages() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { installed.clear(); installed.addAll(list) }
        }
    }
}
