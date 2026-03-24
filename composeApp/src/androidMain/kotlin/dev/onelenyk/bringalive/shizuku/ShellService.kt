package dev.onelenyk.bringalive.shizuku

import android.util.Log
import dev.onelenyk.bringalive.IShellService

private const val TAG = "ShellService"

/**
 * Shizuku UserService that runs in a privileged process with ADB-level permissions.
 * Runtime.exec() calls from within this process inherit those privileges.
 *
 * Uses a persistent shell session to avoid the ~200ms per-command spawn overhead.
 */
class ShellService() : IShellService.Stub() {

    @Volatile private var shellProcess: Process? = null
    private val shellLock = Any()

    private fun getOrStartShell(): Process {
        shellProcess?.let { if (it.isAlive) return it }
        return synchronized(shellLock) {
            shellProcess?.let { if (it.isAlive) return it }
            Runtime.getRuntime().exec(arrayOf("sh")).also { shellProcess = it }
        }
    }

    override fun destroy() {
        Log.i(TAG, "ShellService destroy called")
        shellProcess?.destroy()
        shellProcess = null
        System.exit(0)
    }

    override fun exec(command: String): String {
        return try {
            execInShell(command)
        } catch (e: Exception) {
            // Shell may have died — restart once
            shellProcess?.destroy()
            shellProcess = null
            try { execInShell(command) }
            catch (e2: Exception) {
                Log.e(TAG, "exec failed after restart: ${e2.message}", e2)
                "-1\n\u0000${e2.message ?: "Unknown error"}"
            }
        }
    }

    private fun execInShell(command: String): String {
        val shell = getOrStartShell()
        val writer = shell.outputStream.bufferedWriter()
        val reader = shell.inputStream.bufferedReader()

        val sentinel = "___EXIT___"
        writer.write("$command; echo $sentinel:\$?\n")
        writer.flush()

        val stdout = StringBuilder()
        var exitCode = -1
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line!!
            if (l.startsWith("$sentinel:")) {
                exitCode = l.removePrefix("$sentinel:").trim().toIntOrNull() ?: -1
                break
            }
            stdout.appendLine(l)
        }
        // Pack result as "exitCode\nstdout\0stderr" (stderr empty for persistent session)
        return "$exitCode\n${stdout.toString().trimEnd()}\u0000"
    }
}
