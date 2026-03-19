package dev.onelenyk.bringalive.di

import com.russhwolf.settings.Settings
import dev.onelenyk.bringalive.agent.BringaliveAgent
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.presentation.ChatViewModel
import dev.onelenyk.bringalive.presentation.SettingsViewModel
import dev.onelenyk.bringalive.tools.MemoryStore
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dataModule = module {
    single { Settings() }
    singleOf(::SettingsRepository)
    singleOf(::MemoryStore)
}

val agentModule = module {
    singleOf(::BringaliveAgent)
}

val viewModelModule = module {
    viewModelOf(::ChatViewModel)
    viewModelOf(::SettingsViewModel)
}

fun initKoin(config: KoinApplication.() -> Unit = {}) {
    startKoin {
        config()
        modules(dataModule, agentModule, viewModelModule)
    }
}
