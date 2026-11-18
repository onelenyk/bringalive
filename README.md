# Bringalive

An on-device AI agent for Android. Not a chat app with a system prompt — an agent with
**37 tools** that can actually act on the phone: open apps, change settings, tap the
screen, read notifications, take screenshots, run shell commands through Shizuku.

Built with Kotlin Multiplatform, Compose and [Koog](https://github.com/JetBrains/koog)
(JetBrains' agent framework). Voice in, voice out: Whisper for speech-to-text, Orpheus
for speech synthesis, barge-in while the answer is still playing.

```
"turn off Wi-Fi"                  → ToggleWifi
"open Telegram and message Ann"   → OpenApp → InputTap → InputKeyEvent → InputTap
"what's on my screen?"            → CaptureScreen → OcrScreen
"block this app"                  → DisableApp (Shizuku, no root)
```

## Why

Most "AI on mobile" is a chat window calling an API. The interesting part of a phone is
that it can *do* things — and an agent that can't touch the device is just a text field.

Bringalive gives the model a scoped toolkit and a permission model, then lets it work.

## Architecture

```
Compose UI (chat, agent status, logs, tools playground)
        │
   ChatViewModel ──────────────► Koog agent (LLM + tool calling)
        │                              │
   VoiceEngine                   ToolRegistry (37 tools)
   mic → Whisper STT                   │
   → LLM → Orpheus TTS          ├── Shizuku → shell UID 2000 (no root)
   (barge-in, AudioTrack)       ├── AccessibilityService → tap / swipe / key events
                                ├── NotificationListenerService → read / dismiss
                                └── System APIs → settings, clipboard, intents
```

State lives in `shared/` (KMP), Android-specific tools in `composeApp/androidMain/`,
iOS has a scaffold only.

## Tools (37)

| Group | Count | Examples |
|---|---|---|
| Phone agent | 18 | `InputTap`, `InputSwipe`, `InputKeyEvent`, `PerformAction`, `UiAutomatorDump`, `OpenApp` |
| System | 8 | `SetSetting`, `GetSetting`, `ToggleWifi`, `ToggleBluetooth`, `ToggleAirplaneMode` |
| App management | 8 | `ListInstalledApps`, `InstallApp`, `UninstallApp`, `DisableApp`, `GrantPermission` |
| Shell UI | 5 | `InputTap` by text, long-press by query, back, recents |
| Device | 5 | `GetDeviceInfo`, `SetScreenBrightness`, `ReadClipboard`, `SetClipboard`, `OpenUrl` |
| Diagnostics | 3 | `GetProp`, `Dumpsys`, `ReadLogs` |
| Notifications | 3 | list, read, dismiss |
| Screen | 2 | `CaptureScreen`, `OcrScreen` |
| Shell | 2 | `ShellExecute`, `ShellScreenshot` (Shizuku) |
| Voice | 1 | `Speak` |

## Privilege model

Three levels, each opt-in, each visible in the agent log:

1. **Normal app permissions** — intents, clipboard, brightness, settings.
2. **Accessibility service** — tap, swipe, key events, reading the screen tree.
   Enabled by the user in system settings.
3. **Shizuku** — shell (UID 2000) level: silent install, permission grants, `pm`,
   `dumpsys`, screenshots. Works on a **non-rooted** device via ADB or wireless debugging.
   Every privileged call shows up in the tools playground and the log screen.

`ShizukuStatus` tells the agent what it is allowed to do before it plans anything.

## Models

LLM: Anthropic, OpenAI or OpenRouter — key entered in Settings, stored on device.
Voice: Groq — `whisper-large-v3-turbo` / `whisper-large-v3` / `distil-whisper-large-v3-en`
for STT, `canopylabs/orpheus-*` for TTS.

## Build

Requirements: JDK 17+, Android SDK, Android Studio.

```bash
# the wake-word runtime is a local AAR (45 MB, not in the repo)
mkdir -p libs
# download the sherpa-onnx AAR from https://github.com/k2-fsa/sherpa-onnx/releases
# and place it at libs/sherpa-onnx.aar

./gradlew :composeApp:assembleDebug
```

`libs/sherpa-onnx.aar` is referenced by `shared/build.gradle.kts`.

Then in the app: Settings → paste an LLM API key and a Groq key → grant Accessibility →
install Shizuku and authorise → talk.

## Status

Personal project, not a product, and not actively maintained — left public as a working
example. Android-first; iOS is a UI scaffold. Everything runs on
the phone except the LLM and STT/TTS calls.

Known limits: no always-on wake word yet, tool schemas are hand-written, and a long agent
run can still surprise you — which is exactly why every action is logged.

## Notes

`NOTES.md` is the original research dump on Shizuku (permissions, user service, what
UID 2000 can and cannot do) — useful if you build something similar.
