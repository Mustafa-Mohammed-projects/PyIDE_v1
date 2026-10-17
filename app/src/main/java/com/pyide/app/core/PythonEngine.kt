package com.pyide.app.core

import android.content.Context
import android.os.Build
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/** Callbacks Python uses to talk to the UI (must stay a public interface). */
interface PyIO {
    fun write(s: String, isErr: Boolean)
    fun readLine(): String?
    fun image(path: String)
}

class PythonEngine(private val ctx: Context) {
    val abi: String get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    private val bridge: PyObject by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val m = Python.getInstance().getModule("ide_bridge")
        m.callAttr(
            "setup",
            File(ctx.filesDir, "site-packages").absolutePath,
            File(ctx.cacheDir, "pyide").absolutePath,
            abi,
        )
        m
    }

    fun warmUp() { bridge.toString() }

    fun runFile(path: String, io: PyIO, argv: List<String> = emptyList()): Int =
        bridge.callAttr("run_file", path, io, argv.joinToString("\u0001")).toInt()

    fun runModule(name: String, cwd: String, io: PyIO, argv: List<String>): Int =
        bridge.callAttr("run_module", name, io, argv.joinToString("\u0001"), cwd).toInt()

    fun runCode(code: String, cwd: String, io: PyIO): Int =
        bridge.callAttr("run_code", code, io, cwd).toInt()

    fun pip(args: List<String>, io: PyIO): Int =
        bridge.callAttr("pip", args.joinToString("\u0001"), io).toInt()

    fun replPush(line: String, io: PyIO): Boolean =
        bridge.callAttr("repl_push", line, io).toBoolean()

    fun replReset() { bridge.callAttr("repl_reset") }

    fun stop() { bridge.callAttr("stop") }

    fun listPackages(): List<String> =
        bridge.callAttr("list_packages").toString().lines().filter { it.isNotBlank() }
}
