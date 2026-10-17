package com.pyide.app.core

import android.content.Context
import java.io.File

class Workspace(ctx: Context) {
    val root: File = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun list(): List<File> =
        root.listFiles()?.filter { it.isFile }
            ?.sortedWith(compareBy<File>({ !it.name.endsWith(".py") }, { it.name.lowercase() }))
            ?: emptyList()

    fun read(f: File): String = runCatching { f.readText() }.getOrDefault("")
    fun write(f: File, text: String) { f.writeText(text) }

    fun seed() {
        if (root.listFiles().isNullOrEmpty()) {
            Samples.all.forEach { (name, code) -> File(root, name).writeText(code) }
        }
    }
}
