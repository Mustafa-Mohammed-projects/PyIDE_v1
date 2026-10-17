package com.pyide.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

/** Thread-safe, throttled text buffer shared by the console, terminal and pip log. */
class ConsoleBuffer(private val scope: CoroutineScope) {
    companion object {
        const val OUT = 0
        const val ERR = 1
        const val INFO = 2
        const val INPUT = 3
        private const val MAX = 200_000
        private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    }

    private class Seg(val kind: Int) { val sb = StringBuilder() }

    private val segs = ArrayList<Seg>()
    private var total = 0
    private var scheduled = false
    private val _state = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val state: StateFlow<List<Pair<String, Int>>> = _state

    @Synchronized
    fun append(raw: String, kind: Int = OUT) {
        val text = ANSI.replace(raw, "")
        if (text.isEmpty()) return
        val last = segs.lastOrNull()
        if (last != null && last.kind == kind) last.sb.append(text)
        else segs.add(Seg(kind).also { it.sb.append(text) })
        total += text.length
        while (total > MAX && segs.size > 1) total -= segs.removeAt(0).sb.length
        if (!scheduled) {
            scheduled = true
            scope.launch { delay(60); publish() }
        }
    }

    @Synchronized
    private fun publish() {
        scheduled = false
        _state.value = segs.map { it.sb.toString() to it.kind }
    }

    @Synchronized
    fun clear() {
        segs.clear(); total = 0
        _state.value = emptyList()
    }
}

/** Bridges Python stdout/stderr/stdin/images to the UI. */
class ConsoleIO(
    private val console: ConsoleBuffer,
    private val onImage: (String) -> Unit,
    private val onWaiting: (Boolean) -> Unit,
) : PyIO {
    private val queue = LinkedBlockingQueue<String>()

    @Volatile
    var waiting = false
        private set

    override fun write(s: String, isErr: Boolean) {
        console.append(s, if (isErr) ConsoleBuffer.ERR else ConsoleBuffer.OUT)
    }

    override fun readLine(): String? {
        waiting = true
        onWaiting(true)
        return try {
            val l = queue.take()
            if (l == EOF) null else l
        } finally {
            waiting = false
            onWaiting(false)
        }
    }

    override fun image(path: String) = onImage(path)

    fun submit(line: String) { queue.put(line) }
    fun cancel() { if (waiting) queue.put(EOF) }
    fun reset() { queue.clear() }

    companion object {
        private const val EOF = "\u0000__EOF__"
    }
}
