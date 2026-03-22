package dev.onelenyk.bringalive.workspace

import java.io.File

internal actual fun readFile(path: String): String? {
    val file = File(path)
    return if (file.exists()) file.readText() else null
}

internal actual fun writeFile(path: String, content: String) {
    File(path).writeText(content)
}

internal actual fun fileExists(path: String): Boolean = File(path).exists()

internal actual fun ensureDir(path: String) {
    File(path).mkdirs()
}

internal actual fun listDir(path: String): List<String>? {
    val dir = File(path)
    return if (dir.isDirectory) dir.list()?.toList() else null
}
