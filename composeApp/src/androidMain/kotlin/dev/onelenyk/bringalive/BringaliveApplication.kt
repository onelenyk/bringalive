package dev.onelenyk.bringalive

import android.app.Application
import dev.onelenyk.bringalive.di.PlatformToolSets
import dev.onelenyk.bringalive.di.WorkspacePath
import dev.onelenyk.bringalive.di.initKoin
import dev.onelenyk.bringalive.tools.createDeviceTools
import dev.onelenyk.bringalive.tools.createPhoneAgentTools
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
                }
            )
        }
    }
}
