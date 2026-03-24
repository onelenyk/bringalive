package dev.onelenyk.bringalive.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import dev.onelenyk.bringalive.IShellService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuManager"

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0

    fun toDisplayString(): String = buildString {
        if (stdout.isNotBlank()) appendLine(stdout)
        if (stderr.isNotBlank()) appendLine("stderr: $stderr")
        appendLine("exit code: $exitCode")
    }.trimEnd()
}

class ShizukuManager {

    private val requestCode = 1001

    @Volatile private var shellService: IShellService? = null
    @Volatile private var serviceBinding = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("dev.onelenyk.bringalive", ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R)
        .version(1)
        .tag("bringalive_shell")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShellService.Stub.asInterface(service)
            serviceBinding = false
            Log.i(TAG, "ShellService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            serviceBinding = false
            Log.w(TAG, "ShellService disconnected")
        }
    }

    init {
        try {
            // Fires immediately if Shizuku is already running, and again when it starts.
            Shizuku.addBinderReceivedListenerSticky {
                Log.i(TAG, "Shizuku binder received — auto-binding ShellService")
                if (hasPermission()) bindShellService()
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder died — clearing ShellService")
                shellService = null
                serviceBinding = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Shizuku lifecycle listeners", e)
        }
    }

    // ==================== Status ====================

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun isServiceConnected(): Boolean = shellService != null

    fun requestPermission() {
        if (!isAvailable()) return
        try { Shizuku.requestPermission(requestCode) } catch (e: Exception) {
            Log.e(TAG, "Failed to request permission", e)
        }
    }

    // ==================== Service binding ====================

    fun bindShellService() {
        if (shellService != null || serviceBinding) return
        if (!isAvailable() || !hasPermission()) {
            Log.w(TAG, "Cannot bind: Shizuku not available or no permission")
            return
        }
        serviceBinding = true
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            Log.i(TAG, "Binding ShellService…")
        } catch (e: Exception) {
            serviceBinding = false
            Log.e(TAG, "Failed to bind ShellService", e)
        }
    }

    fun unbindShellService() {
        try { Shizuku.unbindUserService(userServiceArgs, serviceConnection, true) } catch (_: Exception) {}
        shellService = null
        serviceBinding = false
    }

    // ==================== Command execution ====================

    /**
     * Execute a shell command with ADB-level privileges.
     * Suspends until the ShellService is connected (up to 3 s), then runs the command.
     */
    suspend fun executeCommand(command: String): ShellResult {
        if (!isAvailable()) {
            return ShellResult(-1, "", "Shizuku is not running. Start the Shizuku app and enable it via Wireless Debugging.")
        }
        if (!hasPermission()) {
            return ShellResult(-1, "", "Shizuku permission not granted. Open the Shizuku app and grant permission to Bringalive.")
        }

        // Trigger bind if not connected yet
        if (shellService == null) {
            bindShellService()
            // Suspend (not block) waiting for the service to appear
            withTimeoutOrNull(3_000L) {
                while (shellService == null) delay(50)
            }
            if (shellService == null) {
                return ShellResult(-1, "", "ShellService did not connect in time. Try again in a moment.")
            }
        }

        return try {
            parseResult(shellService!!.exec(command))
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}", e)
            shellService = null  // force re-bind next call
            ShellResult(-1, "", "Execution failed: ${e.message}")
        }
    }

    // ==================== Status string ====================

    fun getStatusString(): String = buildString {
        appendLine("Shizuku available:   ${isAvailable()}")
        if (isAvailable()) {
            appendLine("Permission granted:  ${hasPermission()}")
            appendLine("ShellService bound:  ${isServiceConnected()}")
            try { appendLine("Shizuku version:     ${Shizuku.getVersion()}") }
            catch (_: Exception) { appendLine("Shizuku version:     unknown") }
        }
    }.trimEnd()

    // ==================== Listener helpers ====================

    fun addPermissionResultListener(l: Shizuku.OnRequestPermissionResultListener) = Shizuku.addRequestPermissionResultListener(l)
    fun removePermissionResultListener(l: Shizuku.OnRequestPermissionResultListener) = Shizuku.removeRequestPermissionResultListener(l)
    fun addBinderListener(l: Shizuku.OnBinderReceivedListener) = Shizuku.addBinderReceivedListener(l)
    fun removeBinderListener(l: Shizuku.OnBinderReceivedListener) = Shizuku.removeBinderReceivedListener(l)
    fun addBinderDeadListener(l: Shizuku.OnBinderDeadListener) = Shizuku.addBinderDeadListener(l)
    fun removeBinderDeadListener(l: Shizuku.OnBinderDeadListener) = Shizuku.removeBinderDeadListener(l)

    // ==================== Parsing ====================

    companion object {
        /** Format from ShellService.exec(): "exitCode\nstdout\0stderr" */
        internal fun parseResult(raw: String): ShellResult {
            val nl = raw.indexOf('\n')
            if (nl < 0) return ShellResult(-1, "", "Malformed result: $raw")
            val exitCode = raw.substring(0, nl).toIntOrNull() ?: -1
            val rest = raw.substring(nl + 1)
            val null0 = rest.indexOf('\u0000')
            return if (null0 >= 0) {
                ShellResult(exitCode, rest.substring(0, null0).trim(), rest.substring(null0 + 1).trim())
            } else {
                ShellResult(exitCode, rest.trim(), "")
            }
        }
    }
}
