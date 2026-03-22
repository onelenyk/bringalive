package dev.onelenyk.bringalive

import android.app.Application
import dev.onelenyk.bringalive.di.PlatformToolSets
import dev.onelenyk.bringalive.di.WorkspacePath
import dev.onelenyk.bringalive.di.initKoin
import dev.onelenyk.bringalive.tools.createDeviceTools
import dev.onelenyk.bringalive.tools.createPhoneAgentTools
import dev.onelenyk.bringalive.voice.AndroidVoiceEngine
import dev.onelenyk.bringalive.voice.VoiceEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class BringaliveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@BringaliveApplication)
            modules(
                module {
                    single { WorkspacePath(filesDir.resolve("workspace").absolutePath) }
                    single {
                        PlatformToolSets(
                            createPhoneAgentTools(this@BringaliveApplication) +
                            createDeviceTools(this@BringaliveApplication)
                        )
                    }
                    single { HttpClient(OkHttp) }
                    single<VoiceEngine> {
                        AndroidVoiceEngine(
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
