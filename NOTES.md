# Shizuku API — On-Device MCP Server Research Summary

## What Is Shizuku

Shizuku-API is an Android framework (by RikkaApps) that lets normal apps perform privileged operations (root or ADB shell level) without the device being rooted. It works via two backends: **Shizuku** (standard Android app, works on non-rooted devices via ADB/wireless debugging) and **Sui** (Magisk module for rooted devices). Requires Android 6.0+. Licensed under MIT.

The core mechanism: Shizuku starts a Java process via `app_process` with ADB (UID 2000) or root (UID 0) privileges. It acts as a Binder middleman — receiving requests from apps, forwarding them to system server, and returning results. To the app, it's almost identical to calling system APIs directly.

## Latest Versions

- **Shizuku app**: v13.6.0 — supports Android 16 QPR1, auto-start without root on Android 13+ on trusted WLAN.
- **Shizuku API library**: v13.1.5 on Maven Central (`dev.rikka.shizuku:api:13.1.5`).

## Gradle Setup

```gradle
def shizuku_version = '13.1.5'
implementation "dev.rikka.shizuku:api:$shizuku_version"
implementation "dev.rikka.shizuku:provider:$shizuku_version"
```

Plus `ShizukuProvider` in AndroidManifest.xml:
```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

## Two API Approaches

1. **ShizukuBinderWrapper** (simple) — wraps system service Binder calls to execute with shell/root privilege. Limited to Binder calls only.
2. **User Service** (powerful) — like Android Bound Services but runs in a separate process as UID 0 (root) or UID 2000 (shell). Can run Java and native code (JNI). Added in API v11, stable from v12.

## Key API Flow

1. Listen for binder: `Shizuku.addBinderReceivedListener()` / `addBinderDeadListener()`
2. Check availability: `Shizuku.pingBinder()` returns true if running
3. Request permission: runtime permission model (same as standard Android permissions)
4. Make calls: via `ShizukuBinderWrapper` for system services, or `bindUserService`/`unbindUserService` for custom services
5. For Sui: `Sui.init()` returns true if available (no ContentProvider needed)

## Non-Root (ADB Level) Capabilities

### What UID 2000 (shell) CAN Do

**System Settings & Configuration:**
- `WRITE_SECURE_SETTINGS` — toggle system settings programmatically
- Read/write `Settings.Global`, `Settings.System`, `Settings.Secure`
- Toggle WiFi, mobile data, Bluetooth, NFC, airplane mode

**Package Management:**
- Silent install/uninstall apps (`pm install`, `pm uninstall`)
- Grant/revoke runtime permissions for any app
- Enable/disable/freeze app components
- Access `PACKAGE_USAGE_STATS`

**App Operations (AppOps):**
- Fine-grained per-app permission control beyond normal Settings UI
- Override individual op modes (camera, mic, location per-app)

**Process & Activity Management:**
- List all running processes, force-stop any app
- Launch unexported activities, start services, send broadcasts
- Monitor process lifecycle via `IActivityManager.registerProcessObserver()`

**Audio & Display:**
- System-wide audio processing (e.g., RootlessJamesDSP)
- Per-app volume control
- Change display resolution, DPI, refresh rate

**Automation:**
- Full input injection, shell command execution
- Integration with automation apps (MacroDroid, Tasker, AutoJs6)
- Terminal access via `rish`

**Connectivity:**
- Toggle WiFi/data/Bluetooth/NFC
- Connect to WiFi networks programmatically (Android 12+)
- View network interfaces, open ports, routing

### What UID 2000 CANNOT Do

- Cannot access `/data/user/0/<package>/` (other apps' private data)
- Cannot change SELinux context
- Cannot do kernel-level modifications
- Cannot escalate to UID 0 (root)
- Cannot intercept/modify network traffic (no iptables)
- Cannot access hardware-level features (modem, baseband)
- `FLAG_SECURE` screenshot protection blocks screenshots by default (workaround: LSPatch + Disable Flag Secure via Shizuku)
- Permission set varies by Android version

## On-Device MCP Server — Full Capability Map

### Shell Environment Details

- Shell is `sh` (mksh), NOT bash — use POSIX sh syntax
- Commands come from **toybox** (Android's coreutils equivalent): `ls`, `cat`, `cp`, `mv`, `rm`, `mkdir`, `chmod`, `grep`, `find`, `sed`, `awk`, `wc`, `sort`, `head`, `tail`, `ps`, `kill`, `netstat`, `curl`, `ping`, etc.
- Android-specific commands: `am`, `pm`, `cmd`, `settings`, `dumpsys`, `input`, `screencap`, `screenrecord`, `getprop`, `logcat`, `svc`
- Missing: `apt`, `python`, `node`, `bash`, `wget`, `docker`, `gcc`
- Can bundle static ARM binaries (busybox, bash) inside APK if needed

### Executing Scripts & Commands

Via Shizuku UserService (UID 2000):
```kotlin
// Single command
Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p /sdcard/screen.png"))

// Script file
Runtime.getRuntime().exec(arrayOf("sh", "/sdcard/my_script.sh"))

// Programmatic process with output capture
val process = Shizuku.newProcess(arrayOf("sh", "-c", "ls -la /data/local/tmp"), null, null)
val output = process.inputStream.bufferedReader().readText()
val exitCode = process.waitFor()
```

For frequent calls, keep a persistent shell session open via `Shizuku.newProcess(arrayOf("sh"))` and write commands to stdin.

### MCP Tool Categories

**Screen Capture & Reading:**
```shell
screencap -p /sdcard/screen.png           # screenshot PNG
screencap /sdcard/screen.raw              # raw RGBA buffer
screenrecord /sdcard/video.mp4            # video recording
screenrecord --time-limit 10 --size 720x1280 /sdcard/vid.mp4
uiautomator dump /sdcard/ui.xml           # full UI tree as XML (elements, text, bounds, resource-ids)
```

**Input Injection:**
```shell
input tap <x> <y>
input swipe <x1> <y1> <x2> <y2> [duration_ms]
input text "string"
input keyevent KEYCODE_BACK / KEYCODE_HOME / KEYCODE_ENTER / KEYCODE_POWER / etc.
input keyevent --longpress KEYCODE_POWER
input draganddrop 100 200 400 600         # Android 13+
```

**App Lifecycle:**
```shell
am start -n com.app/.Activity
am startservice com.app/.MyService
am broadcast -a com.app.MY_ACTION
am force-stop com.app
am kill-all
pm install -r -g /sdcard/app.apk         # install + auto-grant all permissions
pm uninstall com.app
pm disable-user --user 0 com.app          # freeze
pm enable com.app                         # unfreeze
pm clear com.app                          # wipe data
pm list packages -3                       # list 3rd party
pm grant/revoke com.app android.permission.X
```

**System Settings:**
```shell
settings get/put secure|system|global <key> <value>
# Examples: screen_brightness, screen_off_timeout, airplane_mode_on, location_mode, wifi_on, enabled_accessibility_services
```

**System Info & Diagnostics:**
```shell
dumpsys activity top                      # current top Activity
dumpsys window windows | grep mCurrentFocus
dumpsys battery / cpuinfo / meminfo / diskstats
dumpsys notification                      # ALL active notifications
dumpsys clipboard                         # clipboard contents
dumpsys audio                             # audio state, volumes
dumpsys location                          # location providers, last known
dumpsys sensorservice                     # all sensors + recent readings
dumpsys wifi / telephony.registry
dumpsys alarm / jobscheduler
logcat -d / logcat -d -b crash            # system and crash logs
cat /proc/cpuinfo / /proc/meminfo
ps -A / top -n 1 / uptime / df
getprop ro.build.version.sdk / ro.product.model
```

**Connectivity & Network:**
```shell
svc wifi enable/disable
svc data enable/disable
svc bluetooth enable
cmd connectivity airplane-mode enable
cmd wifi connect-network "SSID" wpa2 "password"  # Android 12+
ifconfig / ip addr / ip route / netstat -tlnp
ping / curl                               # HTTP requests from shell!
```

**File Operations:**
```shell
ls / cat / cp / mv / rm / mkdir -p / find / du / tar / md5sum
# Writable: /sdcard/, /data/local/tmp/
# NOT accessible: /data/user/0/<other_package>/
```

**Content Providers (read device databases):**
```shell
content query --uri content://sms          # SMS messages
content query --uri content://contacts/phones  # contacts
content query --uri content://call_log/calls   # call history
content query --uri content://media/external/images/media  # photos
```

**Clipboard:**
```shell
dumpsys clipboard                          # read
service call clipboard 2 s16 "text"        # write (varies by API level)
```

**Logs:**
```shell
logcat -d                                  # dump full log
logcat -d -s MyTag                         # filter by tag
logcat -d *:E                              # errors only
logcat -d --pid=12345                      # specific process
logcat -c                                  # clear buffer
logcat -d -b crash                         # crash logs
```

### Proposed MCP Server Architecture

```
┌─────────────────────────────────────────────┐
│  Claude / AI Agent (MCP Client)             │
│  connects via HTTP/SSE to local server      │
└────────────────┬────────────────────────────┘
                 │ MCP Protocol (JSON-RPC)
┌────────────────▼────────────────────────────┐
│  MCP Server (Kotlin Android app)            │
│  Runs as foreground service                 │
│  Local HTTP/SSE server (Ktor or NanoHTTPD)  │
│                                             │
│  Exposed MCP Tools:                         │
│    • screenshot()      → base64 PNG         │
│    • get_ui_tree()     → parsed XML/JSON    │
│    • tap(x, y)                              │
│    • swipe(x1,y1,x2,y2,duration)            │
│    • type_text(str)                         │
│    • press_key(keycode)                     │
│    • launch_app(package, activity?)         │
│    • install_apk(path, flags?)              │
│    • uninstall_app(package)                 │
│    • get_notifications()                    │
│    • read_clipboard()                       │
│    • get_current_activity()                 │
│    • run_shell(cmd) → stdout/stderr/exit    │
│    • run_script(script_content) → output    │
│    • get_system_info(category)              │
│    • read_logs(tag?, level?, pid?)          │
│    • settings_get/put(namespace, key, val)  │
│    • file_ops(read/write/list/find)         │
│                                             │
│  Implementation: Shizuku UserService        │
│  (runs as UID 2000, shell privileges)       │
│  Binder calls preferred over shell exec     │
│  for speed (ShizukuBinderWrapper)           │
└─────────────────────────────────────────────┘
```

### Example MCP Agent Scenarios

- **"Open Telegram, find chat with X, read last message"** → am start → sleep → uiautomator dump → parse → input tap → dump → extract text
- **"Download APK from URL, install it, verify it works"** → curl → pm install -r -g → am start → sleep → screencap → return image
- **"Read latest notifications and summarize"** → dumpsys notification → parse → structured data
- **"Toggle WiFi, enable airplane mode, set brightness to min"** → svc wifi disable → settings put → cmd connectivity
- **"Debug why app X is crashing"** → logcat -d -b crash → logcat -d -s AppTag → dumpsys meminfo → report
- **"Find today's photos and back them up"** → find /sdcard/DCIM -name "*.jpg" -mtime 0 → tar czf

### Key Technical Notes

- `uiautomator dump` is slow (~1-2 sec) — not suitable for real-time automation
- Binder calls via `ShizukuBinderWrapper` are much faster than `Runtime.exec()` shell commands
- For frequent shell calls, maintain a persistent shell session via `Shizuku.newProcess(arrayOf("sh"))` and pipe commands to stdin
- `UserServiceArgs` should always have an explicit `tag` set (class names are unstable after ProGuard/R8)
- User Service process is NOT killed automatically on unbind — implement a "destroy" method
- Context passed to v13 UserService constructor does not behave like normal Android Context
- Hidden API restrictions from Android 9+ require workarounds like `AndroidHiddenApiBypass` (LSPosed)
- On API 26 (Android 8), ADB lacks `registerUidObserver` permission — use transparent Activity to trigger binder
- Non-root setup requires re-start after every reboot (Android 11+ wireless debugging makes this on-device; Android 13+ with trusted WLAN can auto-start)