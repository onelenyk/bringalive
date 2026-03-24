package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class GetSettingArgs(
    val namespace: String,
    val key: String,
) : ToolArgs

@Serializable
data class SetSettingArgs(
    val namespace: String,
    val key: String,
    val value: String,
) : ToolArgs

@Serializable
data class SetBrightnessToolArgs(val level: Int) : ToolArgs

@Serializable
data class ToggleWifiArgs(val enable: Boolean) : ToolArgs

@Serializable
data class ToggleBluetoothArgs(val enable: Boolean) : ToolArgs

@Serializable
data class ToggleMobileDataArgs(val enable: Boolean) : ToolArgs

@Serializable
data class ToggleAirplaneModeArgs(val enable: Boolean) : ToolArgs

@Serializable
data class ConnectWifiArgs(
    val ssid: String,
    val password: String = "",
    val security: String = "wpa2",
) : ToolArgs

// --- Tools ---

class GetSettingTool(private val shizukuManager: ShizukuManager) : SimpleTool<GetSettingArgs>(
    argsType = typeToken<GetSettingArgs>(),
    name = "getSetting",
    description = "Read an Android system setting. Namespace must be one of: system, secure, global. Example: namespace='secure', key='android_id'. Requires Shizuku.",
) {
    override suspend fun execute(args: GetSettingArgs): String {
        val ns = args.namespace.lowercase()
        if (ns !in listOf("system", "secure", "global")) {
            return "Invalid namespace '${args.namespace}'. Must be one of: system, secure, global."
        }
        val result = shizukuManager.executeCommand("settings get $ns ${args.key}")
        return if (result.isSuccess) {
            "$ns/${args.key} = ${result.stdout}"
        } else {
            "Failed to read setting: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class SetSettingTool(private val shizukuManager: ShizukuManager) : SimpleTool<SetSettingArgs>(
    argsType = typeToken<SetSettingArgs>(),
    name = "setSetting",
    description = "Write an Android system setting. Namespace must be one of: system, secure, global. Example: namespace='system', key='screen_brightness', value='128'. Requires Shizuku.",
) {
    override suspend fun execute(args: SetSettingArgs): String {
        val ns = args.namespace.lowercase()
        if (ns !in listOf("system", "secure", "global")) {
            return "Invalid namespace '${args.namespace}'. Must be one of: system, secure, global."
        }
        val result = shizukuManager.executeCommand("settings put $ns ${args.key} ${args.value}")
        return if (result.isSuccess) {
            "Set $ns/${args.key} = ${args.value}"
        } else {
            "Failed to set setting: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class SetScreenBrightnessTool(private val shizukuManager: ShizukuManager) : SimpleTool<SetBrightnessToolArgs>(
    argsType = typeToken<SetBrightnessToolArgs>(),
    name = "setScreenBrightness",
    description = "Set screen brightness level (0-255). Also disables auto-brightness. Requires Shizuku.",
) {
    override suspend fun execute(args: SetBrightnessToolArgs): String {
        val level = args.level.coerceIn(0, 255)

        // Disable auto-brightness first
        val autoResult = shizukuManager.executeCommand("settings put system screen_brightness_mode 0")
        if (!autoResult.isSuccess) {
            return "Failed to disable auto-brightness: ${autoResult.stderr}"
        }

        val result = shizukuManager.executeCommand("settings put system screen_brightness $level")
        return if (result.isSuccess) {
            "Screen brightness set to $level/255 (${level * 100 / 255}%)"
        } else {
            "Failed to set brightness: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ToggleWifiTool(private val shizukuManager: ShizukuManager) : SimpleTool<ToggleWifiArgs>(
    argsType = typeToken<ToggleWifiArgs>(),
    name = "toggleWifi",
    description = "Enable or disable WiFi. Set enable=true to turn on, enable=false to turn off. Requires Shizuku.",
) {
    override suspend fun execute(args: ToggleWifiArgs): String {
        val action = if (args.enable) "enable" else "disable"
        val result = shizukuManager.executeCommand("svc wifi $action")
        return if (result.isSuccess) {
            "WiFi ${action}d successfully"
        } else {
            "Failed to $action WiFi: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ToggleBluetoothTool(private val shizukuManager: ShizukuManager) : SimpleTool<ToggleBluetoothArgs>(
    argsType = typeToken<ToggleBluetoothArgs>(),
    name = "toggleBluetooth",
    description = "Enable or disable Bluetooth. Set enable=true to turn on, enable=false to turn off. Requires Shizuku.",
) {
    override suspend fun execute(args: ToggleBluetoothArgs): String {
        val action = if (args.enable) "enable" else "disable"
        val result = shizukuManager.executeCommand("svc bluetooth $action")
        return if (result.isSuccess) {
            "Bluetooth ${action}d successfully"
        } else {
            "Failed to $action Bluetooth: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ToggleMobileDataTool(private val shizukuManager: ShizukuManager) : SimpleTool<ToggleMobileDataArgs>(
    argsType = typeToken<ToggleMobileDataArgs>(),
    name = "toggleMobileData",
    description = "Enable or disable mobile data. Set enable=true to turn on, enable=false to turn off. Requires Shizuku.",
) {
    override suspend fun execute(args: ToggleMobileDataArgs): String {
        val action = if (args.enable) "enable" else "disable"
        val result = shizukuManager.executeCommand("svc data $action")
        return if (result.isSuccess) {
            "Mobile data ${action}d successfully"
        } else {
            "Failed to $action mobile data: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ToggleAirplaneModeTool(private val shizukuManager: ShizukuManager) : SimpleTool<ToggleAirplaneModeArgs>(
    argsType = typeToken<ToggleAirplaneModeArgs>(),
    name = "toggleAirplaneMode",
    description = "Enable or disable airplane mode. Set enable=true to turn on, enable=false to turn off. Requires Shizuku.",
) {
    override suspend fun execute(args: ToggleAirplaneModeArgs): String {
        val action = if (args.enable) "enable" else "disable"
        val result = shizukuManager.executeCommand("cmd connectivity airplane-mode $action")
        return if (result.isSuccess) {
            "Airplane mode ${action}d successfully"
        } else {
            "Failed to $action airplane mode: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

class ConnectWifiTool(private val shizukuManager: ShizukuManager) : SimpleTool<ConnectWifiArgs>(
    argsType = typeToken<ConnectWifiArgs>(),
    name = "connectWifi",
    description = "Connect to a WiFi network. security: open/wpa2/wpa3 (default wpa2). Leave password blank for open networks. Requires Shizuku and Android 12+.",
) {
    override suspend fun execute(args: ConnectWifiArgs): String {
        val result = if (args.password.isBlank()) {
            shizukuManager.executeCommand("cmd wifi connect-network \"${args.ssid}\" open")
        } else {
            shizukuManager.executeCommand("cmd wifi connect-network \"${args.ssid}\" ${args.security} \"${args.password}\"")
        }
        return if (result.isSuccess) {
            "Connecting to WiFi network: ${args.ssid}"
        } else {
            "Failed to connect to ${args.ssid}: ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}

/** Returns all system tools as a list for ToolRegistry. */
fun createSystemTools(shizukuManager: ShizukuManager): List<Tool<*, *>> = listOf(
    GetSettingTool(shizukuManager),
    SetSettingTool(shizukuManager),
    SetScreenBrightnessTool(shizukuManager),
    ToggleWifiTool(shizukuManager),
    ToggleBluetoothTool(shizukuManager),
    ToggleMobileDataTool(shizukuManager),
    ToggleAirplaneModeTool(shizukuManager),
    ConnectWifiTool(shizukuManager),
)
