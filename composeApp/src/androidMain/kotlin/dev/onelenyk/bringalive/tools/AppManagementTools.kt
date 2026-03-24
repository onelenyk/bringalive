package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class InstallAppArgs(val apkPath: String) : ToolArgs

@Serializable
data class UninstallAppArgs(val packageName: String) : ToolArgs

@Serializable
data class DisableAppArgs(val packageName: String) : ToolArgs

@Serializable
data class ForceStopAppArgs(val packageName: String) : ToolArgs

@Serializable
data class GrantPermissionArgs(val packageName: String, val permission: String) : ToolArgs

@Serializable
data class RevokePermissionArgs(val packageName: String, val permission: String) : ToolArgs

@Serializable
data class ClearAppDataArgs(val packageName: String) : ToolArgs

@Serializable
data class RunScriptArgs(val script: String) : ToolArgs

// --- Tools ---

class InstallAppTool(private val shizukuManager: ShizukuManager) : SimpleTool<InstallAppArgs>(
    argsType = typeToken<InstallAppArgs>(),
    name = "installApp",
    description = "Install an APK from a file path using ADB-level privileges. Requires Shizuku. Example path: /sdcard/Download/app.apk",
) {
    override suspend fun execute(args: InstallAppArgs): String {
        val result = shizukuManager.executeCommand("pm install -r \"${args.apkPath}\"")
        return if (result.isSuccess) {
            "Successfully installed APK: ${args.apkPath}"
        } else {
            "Failed to install APK: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class UninstallAppTool(private val shizukuManager: ShizukuManager) : SimpleTool<UninstallAppArgs>(
    argsType = typeToken<UninstallAppArgs>(),
    name = "uninstallApp",
    description = "Uninstall an app by package name using ADB-level privileges. Requires Shizuku. Example: com.example.app",
) {
    override suspend fun execute(args: UninstallAppArgs): String {
        val result = shizukuManager.executeCommand("pm uninstall ${args.packageName}")
        return if (result.isSuccess) {
            "Successfully uninstalled: ${args.packageName}"
        } else {
            "Failed to uninstall ${args.packageName}: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class DisableAppTool(private val shizukuManager: ShizukuManager) : SimpleTool<DisableAppArgs>(
    argsType = typeToken<DisableAppArgs>(),
    name = "disableApp",
    description = "Disable an app for the current user using ADB-level privileges. Useful for bloatware removal. Requires Shizuku.",
) {
    override suspend fun execute(args: DisableAppArgs): String {
        val result = shizukuManager.executeCommand("pm disable-user --user 0 ${args.packageName}")
        return if (result.isSuccess) {
            "Successfully disabled: ${args.packageName}"
        } else {
            "Failed to disable ${args.packageName}: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ForceStopAppTool(private val shizukuManager: ShizukuManager) : SimpleTool<ForceStopAppArgs>(
    argsType = typeToken<ForceStopAppArgs>(),
    name = "forceStopApp",
    description = "Force stop a running app by package name. Requires Shizuku.",
) {
    override suspend fun execute(args: ForceStopAppArgs): String {
        val result = shizukuManager.executeCommand("am force-stop ${args.packageName}")
        return if (result.isSuccess) {
            "Force stopped: ${args.packageName}"
        } else {
            "Failed to force stop ${args.packageName}: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class GrantPermissionTool(private val shizukuManager: ShizukuManager) : SimpleTool<GrantPermissionArgs>(
    argsType = typeToken<GrantPermissionArgs>(),
    name = "grantPermission",
    description = "Grant a runtime permission to an app using ADB-level pm grant. Example: packageName='com.example.app', permission='android.permission.READ_CONTACTS'. Requires Shizuku.",
) {
    override suspend fun execute(args: GrantPermissionArgs): String {
        val result = shizukuManager.executeCommand("pm grant ${args.packageName} ${args.permission}")
        return result.toDisplayString()
    }
}

class RevokePermissionTool(private val shizukuManager: ShizukuManager) : SimpleTool<RevokePermissionArgs>(
    argsType = typeToken<RevokePermissionArgs>(),
    name = "revokePermission",
    description = "Revoke a runtime permission from an app. Requires Shizuku.",
) {
    override suspend fun execute(args: RevokePermissionArgs): String {
        val result = shizukuManager.executeCommand("pm revoke ${args.packageName} ${args.permission}")
        return result.toDisplayString()
    }
}

class ClearAppDataTool(private val shizukuManager: ShizukuManager) : SimpleTool<ClearAppDataArgs>(
    argsType = typeToken<ClearAppDataArgs>(),
    name = "clearAppData",
    description = "Clear all data (cache, databases, preferences) for an app. Equivalent to Settings → Apps → Clear Data. Requires Shizuku.",
) {
    override suspend fun execute(args: ClearAppDataArgs): String {
        val result = shizukuManager.executeCommand("pm clear ${args.packageName}")
        return result.toDisplayString()
    }
}

class RunScriptTool(private val shizukuManager: ShizukuManager) : SimpleTool<RunScriptArgs>(
    argsType = typeToken<RunScriptArgs>(),
    name = "runScript",
    description = "Execute a multi-line shell script with ADB-level privileges. Write your script as a string — it will be saved to a temp file and executed. Use POSIX sh syntax (not bash). Requires Shizuku.",
) {
    override suspend fun execute(args: RunScriptArgs): String {
        shizukuManager.executeCommand("cat > /data/local/tmp/bringalive_script.sh << 'BRINGALIVE_EOF'\n${args.script}\nBRINGALIVE_EOF")
        val result = shizukuManager.executeCommand("sh /data/local/tmp/bringalive_script.sh")
        return result.toDisplayString()
    }
}

/** Returns all app management tools as a list for ToolRegistry. */
fun createAppManagementTools(shizukuManager: ShizukuManager): List<Tool<*, *>> = listOf(
    InstallAppTool(shizukuManager),
    UninstallAppTool(shizukuManager),
    DisableAppTool(shizukuManager),
    ForceStopAppTool(shizukuManager),
    GrantPermissionTool(shizukuManager),
    RevokePermissionTool(shizukuManager),
    ClearAppDataTool(shizukuManager),
    RunScriptTool(shizukuManager),
)
