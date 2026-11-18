package dev.onelenyk.bringalive.di

import com.russhwolf.settings.Settings
import dev.onelenyk.bringalive.agent.BringaliveAgent
import dev.onelenyk.bringalive.conversation.ConversationManager
import dev.onelenyk.bringalive.data.AgentLogStore
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.listener.NoteAgent
import dev.onelenyk.bringalive.listener.PassiveListenerController
import dev.onelenyk.bringalive.listener.TranscriptRepository
import dev.onelenyk.bringalive.presentation.AssistantViewModel
import dev.onelenyk.bringalive.presentation.ChatViewModel
import dev.onelenyk.bringalive.presentation.PassiveListenerViewModel
import dev.onelenyk.bringalive.presentation.SettingsViewModel
import dev.onelenyk.bringalive.workspace.SystemPromptBuilder
import dev.onelenyk.bringalive.workspace.WorkspaceManager
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single { Settings() }
    singleOf(::SettingsRepository)
    singleOf(::AgentLogStore)
}

val workspaceModule = module {
    single { WorkspaceManager(get<WorkspacePath>().path).also { it.init() } }
    singleOf(::SystemPromptBuilder)
    single { ConversationManager(get()).also { it.init() } }
}

val agentModule = module {
    singleOf(::BringaliveAgent)
}

val listenerModule = module {
    singleOf(::TranscriptRepository)
    singleOf(::NoteAgent)
    singleOf(::PassiveListenerController)
}

val viewModelModule = module {
    viewModelOf(::ChatViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PassiveListenerViewModel)
    viewModelOf(::AssistantViewModel)
}

/** Provided by platform (Android/iOS) to supply the workspace base path. */
data class WorkspacePath(val path: String)

/**
 * Wrapper for platform-specific Tool instances (e.g. phone accessibility tools on Android).
 * Provided by the platform module via Koin.
 */
data class PlatformToolSets(val tools: List<ai.koog.agents.core.tools.Tool<*, *>>)

fun initKoin(config: KoinApplication.() -> Unit = {}) {
    startKoin {
        config()
        modules(dataModule, workspaceModule, agentModule, listenerModule, viewModelModule)
    }
}
