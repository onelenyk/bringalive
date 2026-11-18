package dev.onelenyk.bringalive

import android.app.Application
import dev.onelenyk.bringalive.di.PlatformToolSets
import dev.onelenyk.bringalive.di.WorkspacePath
import dev.onelenyk.bringalive.di.initKoin
import dev.onelenyk.bringalive.screen.ScreenCaptureManager
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import dev.onelenyk.bringalive.tools.createAppManagementTools
import dev.onelenyk.bringalive.tools.createDeviceTools
import dev.onelenyk.bringalive.tools.createNotificationTools
import dev.onelenyk.bringalive.tools.createPhoneAgentTools
import dev.onelenyk.bringalive.tools.createScreenTools
import dev.onelenyk.bringalive.tools.createDiagnosticsTools
import dev.onelenyk.bringalive.tools.createShellTools
import dev.onelenyk.bringalive.tools.createShellUiTools
import dev.onelenyk.bringalive.tools.createSystemTools
import dev.onelenyk.bringalive.tools.createVoiceTools
import dev.onelenyk.bringalive.presentation.ToolsViewModel
import dev.onelenyk.bringalive.assistant.AndroidAssistantEngine
import dev.onelenyk.bringalive.assistant.AssistantEngine
import dev.onelenyk.bringalive.listener.AndroidPassiveListenerEngine
import dev.onelenyk.bringalive.listener.PassiveListenerEngine
import dev.onelenyk.bringalive.voice.AndroidVoiceEngine
import dev.onelenyk.bringalive.voice.VoiceEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class BringaliveApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val shizukuManager = ShizukuManager()
        ScreenCaptureManager.instance.setShizukuManager(shizukuManager)

        initKoin {
            androidContext(this@BringaliveApplication)
            modules(
                module {
                    single { WorkspacePath(filesDir.resolve("workspace").absolutePath) }
                    single { shizukuManager }
                    single {
                        PlatformToolSets(
                            createPhoneAgentTools(this@BringaliveApplication) +
                            createDeviceTools(this@BringaliveApplication) +
                            createNotificationTools() +
                            createScreenTools(this@BringaliveApplication) +
                            createShellTools(shizukuManager) +
                            createAppManagementTools(shizukuManager) +
                            createSystemTools(shizukuManager) +
                            createDiagnosticsTools(shizukuManager) +
                            createShellUiTools(shizukuManager) +
                            createVoiceTools(get())
                        )
                    }
                    single { HttpClient(OkHttp) }
                    viewModel { ToolsViewModel(get(), get()) }
                    single<VoiceEngine> {
                        AndroidVoiceEngine(
                            context = this@BringaliveApplication,
                            settings = get(),
                            logStore = get(),
                            httpClient = get(),
                        )
                    }
                    single<PassiveListenerEngine> {
                        AndroidPassiveListenerEngine(
                            context = this@BringaliveApplication,
                            settings = get(),
                            transcriptRepository = get(),
                            httpClient = get(),
                        )
                    }
                    single<AssistantEngine> {
                        AndroidAssistantEngine(
                            context = this@BringaliveApplication,
                            settings = get(),
                            logStore = get(),
                            httpClient = get(),
                        )
                    }
                }
            )
        }
    }
}
