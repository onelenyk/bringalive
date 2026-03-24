package dev.onelenyk.bringalive.presentation

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onelenyk.bringalive.di.PlatformToolSets
import dev.onelenyk.bringalive.tools.*
import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ==================== Tool metadata (drives the UI) ====================

data class ParamDef(
    val name: String,
    val hint: String,
    val default: String = "",
    val multiline: Boolean = false,
)

data class ToolDef(
    val name: String,
    val params: List<ParamDef> = emptyList(),
)

data class ToolCategory(
    val label: String,
    val tools: List<ToolDef>,
    val expanded: Boolean = true,
)

val TOOL_CATEGORIES: List<ToolCategory> = listOf(
    ToolCategory("Device", listOf(
        ToolDef("getDeviceInfo"),
        ToolDef("readClipboard"),
        ToolDef("setClipboard", listOf(ParamDef("text", "Text to copy", "Hello from bringalive!"))),
        ToolDef("openUrl", listOf(ParamDef("url", "URL to open", "https://example.com"))),
        ToolDef("shareText", listOf(
            ParamDef("text", "Text to share", "Hello!"),
            ParamDef("subject", "Subject (optional)", ""),
        )),
    )),
    ToolCategory("Accessibility", listOf(
        ToolDef("getScreenContent"),
        ToolDef("getActiveApp"),
        ToolDef("dumpScreen"),
        ToolDef("listInstalledApps"),
        ToolDef("tapElement", listOf(ParamDef("query", "Element text or desc", "OK"))),
        ToolDef("tapAtIndex", listOf(ParamDef("index", "Element index from getScreenContent", "0"))),
        ToolDef("tapAndWait", listOf(
            ParamDef("query", "Element text or desc", "OK"),
            ParamDef("waitMs", "Wait after tap (ms)", "1500"),
        )),
        ToolDef("longPress", listOf(ParamDef("query", "Element text or desc", ""))),
        ToolDef("typeText", listOf(ParamDef("text", "Text to type", "Hello"))),
        ToolDef("focusAndType", listOf(
            ParamDef("fieldQuery", "Field label or hint", "Search"),
            ParamDef("text", "Text to type", "Hello"),
        )),
        ToolDef("scrollScreen", listOf(ParamDef("direction", "up/down/left/right", "down"))),
        ToolDef("scrollToFind", listOf(
            ParamDef("query", "Text to scroll until found", ""),
            ParamDef("maxScrolls", "Max scroll attempts", "10"),
        )),
        ToolDef("swipe", listOf(ParamDef("direction", "up/down/left/right", "up"))),
        ToolDef("openApp", listOf(ParamDef("name", "App name", "Chrome"))),
        ToolDef("pressBack"),
        ToolDef("pressHome"),
        ToolDef("wait", listOf(ParamDef("ms", "Milliseconds", "1000"))),
    )),
    ToolCategory("Notifications", listOf(
        ToolDef("listNotifications"),
        ToolDef("dismissNotification", listOf(ParamDef("key", "Notification key from listNotifications", ""))),
        ToolDef("replyToNotification", listOf(
            ParamDef("key", "Notification key from listNotifications", ""),
            ParamDef("text", "Reply text", ""),
        )),
    )),
    ToolCategory("Screen / OCR", listOf(
        ToolDef("ocrScreen"),
        ToolDef("captureScreen"),
    )),
    ToolCategory("Shell (Shizuku)", listOf(
        ToolDef("shizukuStatus"),
        ToolDef("shellExecute", listOf(ParamDef("command", "Shell command", "ls /sdcard"))),
        ToolDef("installApp", listOf(ParamDef("apkPath", "APK path on device", "/sdcard/Download/app.apk"))),
        ToolDef("uninstallApp", listOf(ParamDef("packageName", "Package name", "com.example.app"))),
        ToolDef("disableApp", listOf(ParamDef("packageName", "Package name", "com.example.app"))),
        ToolDef("forceStopApp", listOf(ParamDef("packageName", "Package name", "com.example.app"))),
        ToolDef("grantPermission", listOf(
            ParamDef("packageName", "Package name", "com.example.app"),
            ParamDef("permission", "Permission", "android.permission.READ_CONTACTS"),
        )),
        ToolDef("revokePermission", listOf(
            ParamDef("packageName", "Package name", "com.example.app"),
            ParamDef("permission", "Permission", "android.permission.READ_CONTACTS"),
        )),
        ToolDef("clearAppData", listOf(ParamDef("packageName", "Package name", "com.example.app"))),
        ToolDef("runScript", listOf(ParamDef("script", "Shell script", "echo hello\ndate\nls /sdcard", multiline = true))),
    )),
    ToolCategory("Shell UI", listOf(
        ToolDef("uiAutomatorDump"),
        ToolDef("shellScreenshot"),
        ToolDef("inputTap", listOf(
            ParamDef("x", "X coordinate", "540"),
            ParamDef("y", "Y coordinate", "960"),
        )),
        ToolDef("inputSwipe", listOf(
            ParamDef("x1", "Start X", "540"),
            ParamDef("y1", "Start Y", "1500"),
            ParamDef("x2", "End X", "540"),
            ParamDef("y2", "End Y", "500"),
            ParamDef("durationMs", "Duration ms", "300"),
        )),
        ToolDef("inputKeyEvent", listOf(
            ParamDef("keycode", "Keycode", "KEYCODE_BACK"),
            ParamDef("longPress", "true/false", "false"),
        )),
    )),
    ToolCategory("Diagnostics", listOf(
        ToolDef("dumpsys", listOf(ParamDef("service", "battery/wifi/audio/notification/activity top", "battery"))),
        ToolDef("readLogs", listOf(
            ParamDef("tag", "Tag filter (optional)", ""),
            ParamDef("level", "V/D/I/W/E", "E"),
            ParamDef("lines", "Max lines", "50"),
        )),
        ToolDef("getprop", listOf(ParamDef("key", "Property key (blank=all)", "ro.product.model"))),
    )),
    ToolCategory("System", listOf(
        ToolDef("getSetting", listOf(
            ParamDef("namespace", "system / secure / global", "system"),
            ParamDef("key", "Setting key", "screen_brightness"),
        )),
        ToolDef("setSetting", listOf(
            ParamDef("namespace", "system / secure / global", "system"),
            ParamDef("key", "Setting key", "screen_brightness"),
            ParamDef("value", "New value", "128"),
        )),
        ToolDef("setScreenBrightness", listOf(ParamDef("level", "0–255", "128"))),
        ToolDef("toggleWifi", listOf(ParamDef("enable", "true / false", "true"))),
        ToolDef("toggleBluetooth", listOf(ParamDef("enable", "true / false", "true"))),
        ToolDef("toggleMobileData", listOf(ParamDef("enable", "true / false", "true"))),
        ToolDef("toggleAirplaneMode", listOf(ParamDef("enable", "true / false", "false"))),
        ToolDef("connectWifi", listOf(
            ParamDef("ssid", "Network name", "MyWifi"),
            ParamDef("password", "Password (blank=open)", ""),
            ParamDef("security", "open/wpa2/wpa3", "wpa2"),
        )),
    )),
    ToolCategory("Voice", listOf(
        ToolDef("speak", listOf(ParamDef("text", "Text to speak aloud", "Hello! Text to speech is working.", multiline = true))),
    )),
    ToolCategory("Workspace", listOf(
        ToolDef("getCurrentTime", listOf(ParamDef("timezone", "Timezone (optional)", ""))),
        ToolDef("listMemory", listOf(ParamDef("filter", "Filter string (optional)", ""))),
        ToolDef("saveMemory", listOf(
            ParamDef("category", "Category", "test"),
            ParamDef("content", "Content", "Test memory entry", multiline = true),
        )),
        ToolDef("forgetMemory", listOf(ParamDef("query", "Text to match and remove", ""))),
        ToolDef("updateUser", listOf(
            ParamDef("section", "Section (Profile / Preferences / Current Projects)", "Preferences"),
            ParamDef("content", "Content to add", "", multiline = true),
        )),
        ToolDef("addDailyNote", listOf(ParamDef("note", "Note text", "", multiline = true))),
        ToolDef("searchHistory", listOf(ParamDef("query", "Search query", ""))),
        ToolDef("readWorkspaceFile", listOf(ParamDef("filename", "Filename", "USER.md"))),
        ToolDef("writeWorkspaceFile", listOf(
            ParamDef("filename", "Filename", "test.md"),
            ParamDef("content", "Content", "test content", multiline = true),
        )),
    )),
)

// ==================== State ====================

data class ToolRunState(
    val loading: Boolean = false,
    val result: String? = null,
)

data class ToolsUiState(
    val categories: List<ToolCategory> = TOOL_CATEGORIES,
    val inputs: Map<String, Map<String, String>> = emptyMap(),
    val runStates: Map<String, ToolRunState> = emptyMap(),
)

// ==================== ViewModel ====================

class ToolsViewModel(
    private val platformToolSets: PlatformToolSets,
    private val workspace: WorkspaceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ToolsUiState(
        inputs = buildDefaultInputs(),
    ))
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

    private val toolMap: Map<String, Tool<*, *>> by lazy {
        (platformToolSets.tools + listOf(
            GetCurrentTimeTool(),
            SaveMemoryTool(workspace),
            ForgetMemoryTool(workspace),
            ListMemoryTool(workspace),
            UpdateUserTool(workspace),
            AddDailyNoteTool(workspace),
            SearchHistoryTool(workspace),
            ReadWorkspaceFileTool(workspace),
            WriteWorkspaceFileTool(workspace),
        )).associateBy { it.name }
    }

    fun onInputChange(toolName: String, paramName: String, value: String) {
        _state.update { s ->
            val current = s.inputs[toolName]?.toMutableMap() ?: mutableMapOf()
            current[paramName] = value
            s.copy(inputs = s.inputs + (toolName to current))
        }
    }

    fun onToggleCategory(label: String) {
        _state.update { s ->
            s.copy(categories = s.categories.map {
                if (it.label == label) it.copy(expanded = !it.expanded) else it
            })
        }
    }

    fun runTool(toolName: String) {
        val inputs = _state.value.inputs[toolName] ?: emptyMap()
        viewModelScope.launch {
            _state.update { s -> s.copy(runStates = s.runStates + (toolName to ToolRunState(loading = true))) }
            val result = try {
                dispatch(toolName, inputs)
            } catch (e: Exception) {
                "ERROR: ${e.message ?: e.javaClass.simpleName}"
            }
            _state.update { s -> s.copy(runStates = s.runStates + (toolName to ToolRunState(loading = false, result = result))) }
        }
    }

    // ==================== Dispatch ====================

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(name: String, p: Map<String, String>): String {
        fun <A> tool(n: String): SimpleTool<A> = toolMap[n] as? SimpleTool<A>
            ?: error("Tool '$n' not found in registry")

        return when (name) {
            // Device
            "getDeviceInfo"  -> tool<DeviceInfoArgs>("getDeviceInfo").execute(DeviceInfoArgs())
            "readClipboard"  -> tool<ReadClipboardArgs>("readClipboard").execute(ReadClipboardArgs())
            "setClipboard"   -> tool<SetClipboardArgs>("setClipboard").execute(SetClipboardArgs(text = p["text"] ?: ""))
            "openUrl"        -> tool<OpenUrlArgs>("openUrl").execute(OpenUrlArgs(url = p["url"] ?: ""))
            "shareText"      -> tool<ShareTextArgs>("shareText").execute(ShareTextArgs(
                text = p["text"] ?: "", subject = p["subject"] ?: ""))

            // Accessibility
            "getScreenContent"  -> tool<ScreenSummaryArgs>("getScreenContent").execute(ScreenSummaryArgs())
            "getActiveApp"      -> tool<ActiveAppArgs>("getActiveApp").execute(ActiveAppArgs())
            "dumpScreen"        -> tool<DumpArgs>("dumpScreen").execute(DumpArgs())
            "listInstalledApps" -> tool<ListAppsArgs>("listInstalledApps").execute(ListAppsArgs())
            "tapElement"        -> tool<TapElementArgs>("tapElement").execute(TapElementArgs(query = p["query"] ?: ""))
            "tapAtIndex"        -> tool<TapAtIndexArgs>("tapAtIndex").execute(TapAtIndexArgs(index = p["index"]?.toIntOrNull() ?: 0))
            "tapAndWait"        -> tool<TapAndWaitArgs>("tapAndWait").execute(TapAndWaitArgs(
                query = p["query"] ?: "", waitMs = p["waitMs"]?.toLongOrNull() ?: 1500))
            "longPress"         -> tool<LongPressArgs>("longPress").execute(LongPressArgs(query = p["query"] ?: ""))
            "typeText"          -> tool<TypeTextArgs>("typeText").execute(TypeTextArgs(text = p["text"] ?: ""))
            "focusAndType"      -> tool<FocusAndTypeArgs>("focusAndType").execute(FocusAndTypeArgs(
                fieldQuery = p["fieldQuery"] ?: "", text = p["text"] ?: ""))
            "scrollScreen"      -> tool<ScrollScreenArgs>("scrollScreen").execute(ScrollScreenArgs(direction = p["direction"] ?: "down"))
            "scrollToFind"      -> tool<ScrollToFindArgs>("scrollToFind").execute(ScrollToFindArgs(
                query = p["query"] ?: "", maxScrolls = p["maxScrolls"]?.toIntOrNull() ?: 10))
            "swipe"             -> tool<SwipeArgs>("swipe").execute(SwipeArgs(direction = p["direction"] ?: "up"))
            "openApp"           -> tool<OpenAppArgs>("openApp").execute(OpenAppArgs(name = p["name"] ?: ""))
            "pressBack"         -> tool<BackArgs>("pressBack").execute(BackArgs())
            "pressHome"         -> tool<HomeArgs>("pressHome").execute(HomeArgs())
            "wait"              -> tool<WaitArgs>("wait").execute(WaitArgs(ms = p["ms"]?.toLongOrNull() ?: 1000L))

            // Notifications
            "listNotifications"    -> tool<ListNotificationsArgs>("listNotifications").execute(ListNotificationsArgs())
            "dismissNotification"  -> tool<DismissNotificationArgs>("dismissNotification").execute(DismissNotificationArgs(key = p["key"] ?: ""))
            "replyToNotification"  -> tool<ReplyToNotificationArgs>("replyToNotification").execute(ReplyToNotificationArgs(
                key = p["key"] ?: "", text = p["text"] ?: ""))

            // Screen / OCR
            "ocrScreen"     -> tool<OcrScreenArgs>("ocrScreen").execute(OcrScreenArgs())
            "captureScreen" -> tool<CaptureScreenArgs>("captureScreen").execute(CaptureScreenArgs())

            // Shell
            "shizukuStatus"  -> tool<ShizukuStatusArgs>("shizukuStatus").execute(ShizukuStatusArgs())
            "shellExecute"   -> tool<ShellExecuteArgs>("shellExecute").execute(ShellExecuteArgs(command = p["command"] ?: "ls /sdcard"))
            "installApp"     -> tool<InstallAppArgs>("installApp").execute(InstallAppArgs(apkPath = p["apkPath"] ?: ""))
            "uninstallApp"   -> tool<UninstallAppArgs>("uninstallApp").execute(UninstallAppArgs(packageName = p["packageName"] ?: ""))
            "disableApp"     -> tool<DisableAppArgs>("disableApp").execute(DisableAppArgs(packageName = p["packageName"] ?: ""))
            "forceStopApp"   -> tool<ForceStopAppArgs>("forceStopApp").execute(ForceStopAppArgs(packageName = p["packageName"] ?: ""))
            "grantPermission"   -> tool<GrantPermissionArgs>("grantPermission").execute(GrantPermissionArgs(
                packageName = p["packageName"] ?: "", permission = p["permission"] ?: ""))
            "revokePermission"  -> tool<RevokePermissionArgs>("revokePermission").execute(RevokePermissionArgs(
                packageName = p["packageName"] ?: "", permission = p["permission"] ?: ""))
            "clearAppData"      -> tool<ClearAppDataArgs>("clearAppData").execute(ClearAppDataArgs(packageName = p["packageName"] ?: ""))
            "runScript"         -> tool<RunScriptArgs>("runScript").execute(RunScriptArgs(script = p["script"] ?: "echo hello"))

            // Shell UI
            "uiAutomatorDump" -> tool<UiDumpArgs>("uiAutomatorDump").execute(UiDumpArgs())
            "shellScreenshot" -> tool<ShellScreenshotArgs>("shellScreenshot").execute(ShellScreenshotArgs())
            "inputTap"       -> tool<InputTapArgs>("inputTap").execute(InputTapArgs(
                x = p["x"]?.toIntOrNull() ?: 540, y = p["y"]?.toIntOrNull() ?: 960))
            "inputSwipe"     -> tool<InputSwipeArgs>("inputSwipe").execute(InputSwipeArgs(
                x1 = p["x1"]?.toIntOrNull() ?: 540, y1 = p["y1"]?.toIntOrNull() ?: 1500,
                x2 = p["x2"]?.toIntOrNull() ?: 540, y2 = p["y2"]?.toIntOrNull() ?: 500,
                durationMs = p["durationMs"]?.toIntOrNull() ?: 300))
            "inputKeyEvent"  -> tool<InputKeyEventArgs>("inputKeyEvent").execute(InputKeyEventArgs(
                keycode = p["keycode"] ?: "KEYCODE_BACK",
                longPress = p["longPress"]?.toBooleanStrictOrNull() ?: false))

            // Diagnostics
            "dumpsys"  -> tool<DumpsysArgs>("dumpsys").execute(DumpsysArgs(service = p["service"] ?: "battery"))
            "readLogs" -> tool<ReadLogsArgs>("readLogs").execute(ReadLogsArgs(
                tag = p["tag"] ?: "", level = p["level"] ?: "E", lines = p["lines"]?.toIntOrNull() ?: 50))
            "getprop"  -> tool<GetPropArgs>("getprop").execute(GetPropArgs(key = p["key"] ?: ""))

            // System
            "getSetting"         -> tool<GetSettingArgs>("getSetting").execute(GetSettingArgs(
                namespace = p["namespace"] ?: "system", key = p["key"] ?: "screen_brightness"))
            "setSetting"         -> tool<SetSettingArgs>("setSetting").execute(SetSettingArgs(
                namespace = p["namespace"] ?: "system", key = p["key"] ?: "", value = p["value"] ?: ""))
            "setScreenBrightness" -> tool<SetBrightnessToolArgs>("setScreenBrightness").execute(
                SetBrightnessToolArgs(level = p["level"]?.toIntOrNull() ?: 128))
            "toggleWifi"         -> tool<ToggleWifiArgs>("toggleWifi").execute(ToggleWifiArgs(
                enable = p["enable"]?.toBooleanStrictOrNull() ?: true))
            "toggleBluetooth"    -> tool<ToggleBluetoothArgs>("toggleBluetooth").execute(ToggleBluetoothArgs(
                enable = p["enable"]?.toBooleanStrictOrNull() ?: true))
            "toggleMobileData"  -> tool<ToggleMobileDataArgs>("toggleMobileData").execute(ToggleMobileDataArgs(
                enable = p["enable"]?.toBooleanStrictOrNull() ?: true))
            "toggleAirplaneMode" -> tool<ToggleAirplaneModeArgs>("toggleAirplaneMode").execute(ToggleAirplaneModeArgs(
                enable = p["enable"]?.toBooleanStrictOrNull() ?: false))
            "connectWifi"       -> tool<ConnectWifiArgs>("connectWifi").execute(ConnectWifiArgs(
                ssid = p["ssid"] ?: "", password = p["password"] ?: "", security = p["security"] ?: "wpa2"))

            // Voice
            "speak" -> tool<SpeakArgs>("speak").execute(SpeakArgs(text = p["text"] ?: ""))

            // Workspace
            "getCurrentTime"     -> GetCurrentTimeTool().execute(GetCurrentTimeArgs(timezone = p["timezone"] ?: ""))
            "listMemory"         -> ListMemoryTool(workspace).execute(ListMemoryArgs(filter = p["filter"] ?: ""))
            "saveMemory"         -> SaveMemoryTool(workspace).execute(SaveMemoryArgs(
                category = p["category"] ?: "test", content = p["content"] ?: ""))
            "forgetMemory"       -> ForgetMemoryTool(workspace).execute(ForgetMemoryArgs(query = p["query"] ?: ""))
            "updateUser"         -> UpdateUserTool(workspace).execute(UpdateUserArgs(
                section = p["section"] ?: "Preferences", content = p["content"] ?: ""))
            "addDailyNote"       -> AddDailyNoteTool(workspace).execute(AddDailyNoteArgs(note = p["note"] ?: ""))
            "searchHistory"      -> SearchHistoryTool(workspace).execute(SearchHistoryArgs(query = p["query"] ?: ""))
            "readWorkspaceFile"  -> ReadWorkspaceFileTool(workspace).execute(ReadFileArgs(filename = p["filename"] ?: "USER.md"))
            "writeWorkspaceFile" -> WriteWorkspaceFileTool(workspace).execute(WriteFileArgs(
                filename = p["filename"] ?: "test.md", content = p["content"] ?: ""))

            else -> "Unknown tool: $name"
        }
    }

    // ==================== Helpers ====================

    private fun buildDefaultInputs(): Map<String, Map<String, String>> {
        return TOOL_CATEGORIES.flatMap { it.tools }.associate { tool ->
            tool.name to tool.params.associate { param -> param.name to param.default }
        }
    }
}
