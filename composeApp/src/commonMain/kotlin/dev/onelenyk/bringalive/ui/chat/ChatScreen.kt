package dev.onelenyk.bringalive.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.onelenyk.bringalive.model.ChatMessage
import dev.onelenyk.bringalive.model.MessageRole
import dev.onelenyk.bringalive.model.ThinkingBlock
import dev.onelenyk.bringalive.presentation.ChatViewModel
import dev.onelenyk.bringalive.ui.logs.LogsScreen
import dev.onelenyk.bringalive.ui.tools.createToolsScreen
import dev.onelenyk.bringalive.voice.VoiceState
import dev.onelenyk.bringalive.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class ChatScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: ChatViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        val listState = rememberLazyListState()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Scroll to bottom on new messages or streaming
        val scrollTarget = state.messages.size +
            (if (state.pendingThinking != null) 1 else 0) +
            (if (state.activeTools.isNotEmpty()) state.activeTools.size else 0) +
            (if (state.streamingText.isNotEmpty()) 1 else 0)
        LaunchedEffect(scrollTarget, state.streamingText) {
            if (scrollTarget > 0) {
                listState.animateScrollToItem(scrollTarget - 1)
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    ConversationDrawer(
                        conversations = state.conversations,
                        activeId = state.activeConversationId,
                        onSelect = { id ->
                            viewModel.onSwitchConversation(id)
                            scope.launch { drawerState.close() }
                        },
                        onNew = {
                            viewModel.onNewConversation()
                            scope.launch { drawerState.close() }
                        },
                        onDelete = { id ->
                            viewModel.onDeleteConversation(id)
                        },
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            // Show active conversation title
                            val title = state.conversations
                                .find { it.id == state.activeConversationId }
                                ?.title ?: "bringalive"
                            Text(title, maxLines = 1)
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Conversations")
                            }
                        },
                        actions = {
                            // Call mode toggle
                            IconButton(onClick = { viewModel.onCallModeToggle() }) {
                                Icon(
                                    imageVector = if (state.callMode) Icons.Default.CallEnd else Icons.Default.Call,
                                    contentDescription = if (state.callMode) "End call" else "Call mode",
                                    tint = if (state.callMode) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            IconButton(onClick = { viewModel.onNewConversation() }) {
                                Icon(Icons.Default.Add, contentDescription = "New chat")
                            }
                            IconButton(onClick = { navigator.push(createToolsScreen()) }) {
                                Icon(Icons.Default.Build, contentDescription = "Tools")
                            }
                            IconButton(onClick = { navigator.push(LogsScreen()) }) {
                                Icon(Icons.Default.Info, contentDescription = "Logs")
                            }
                            IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        modifier = Modifier.statusBarsPadding(),
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                ) {
                    // Messages
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.messages.isEmpty()) {
                            item {
                                EmptyState()
                            }
                        }
                        items(state.messages, key = { it.id }) { message ->
                            when (message.role) {
                                MessageRole.THINKING -> ThinkingCard(block = message.thinkingBlock!!)
                                MessageRole.TOOL -> ToolExecutionCard(
                                    toolName = message.toolName ?: "tool",
                                    result = message.content,
                                )
                                else -> ChatBubble(message)
                            }
                        }

                        // Pending thinking card (shown during generation)
                        state.pendingThinking?.let { pending ->
                            item(key = "thinking_pending") {
                                ThinkingCard(block = pending, isPending = true)
                            }
                        }

                        // Active tool cards
                        if (state.activeTools.isNotEmpty()) {
                            items(state.activeTools, key = { it.name }) { tool ->
                                ToolExecutionCard(
                                    toolName = tool.name,
                                    status = tool.status,
                                    result = tool.result,
                                )
                            }
                        }

                        // Streaming bubble
                        if (state.streamingText.isNotEmpty()) {
                            item(key = "streaming") {
                                ChatBubble(
                                    ChatMessage(
                                        id = "streaming",
                                        role = MessageRole.ASSISTANT,
                                        content = state.streamingText,
                                        isStreaming = true,
                                    )
                                )
                            }
                        }
                    }

                    // Agent status panel
                    AgentStatusPanel(
                        isGenerating = state.isGenerating,
                        streamingText = state.streamingText,
                        currentAction = state.currentAction,
                        activeTools = state.activeTools,
                    )

                    // Error banner
                    if (state.error != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    // Stop speaking chip — shown when TTS is playing (non-call mode)
                    AnimatedVisibility(
                        visible = state.voiceState == VoiceState.SPEAKING && !state.callMode,
                        enter = fadeIn(tween(150)) + slideInVertically { it },
                        exit = fadeOut(tween(150)) + slideOutVertically { it },
                    ) {
                        StopSpeakingChip(
                            onClick = { viewModel.onStopSpeaking() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    // Input bar
                    ChatInputBar(
                        text = state.inputText,
                        onTextChanged = viewModel::onInputChanged,
                        onSend = viewModel::onSendMessage,
                        onMicToggle = viewModel::onMicToggle,
                        isGenerating = state.isGenerating,
                        voiceState = state.voiceState,
                        voiceAmplitude = state.voiceAmplitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "bringalive",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Your personal AI assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StopSpeakingChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Stop speaking",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER

    if (isUser) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "B",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Markdown(
                content = message.content,
                colors = markdownColor(
                    text = MaterialTheme.colorScheme.onSurface,
                    codeText = MaterialTheme.colorScheme.onSurfaceVariant,
                    codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                    dividerColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                typography = markdownTypography(
                    text = MaterialTheme.typography.bodyMedium,
                    code = MaterialTheme.typography.bodySmall,
                    h1 = MaterialTheme.typography.titleLarge,
                    h2 = MaterialTheme.typography.titleMedium,
                    h3 = MaterialTheme.typography.titleSmall,
                    h4 = MaterialTheme.typography.labelLarge,
                    h5 = MaterialTheme.typography.labelMedium,
                    h6 = MaterialTheme.typography.labelSmall,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToolExecutionCard(
    toolName: String,
    status: String = "done",
    result: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = toolName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result != null) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ThinkingCard(block: ThinkingBlock, isPending: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
    ) {
        Row {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "Plan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = block.goal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                block.steps.forEachIndexed { i, step ->
                    Text(
                        text = "${i + 1}. $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                if (block.reasoning.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = block.reasoning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isPending) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    isGenerating: Boolean,
    voiceState: VoiceState,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isVoiceActive = voiceState != VoiceState.IDLE

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Animated swap: TextField ↔ Voice indicator
            AnimatedContent(
                targetState = isVoiceActive,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "inputMode",
            ) { voiceActive ->
                if (voiceActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        when (voiceState) {
                            VoiceState.LISTENING, VoiceState.RECORDING -> {
                                VoiceWaveform(
                                    amplitude = voiceAmplitude,
                                    isActive = voiceState == VoiceState.RECORDING,
                                    barColor = if (voiceState == VoiceState.RECORDING) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            VoiceState.SPEAKING -> {
                                SpeakingBars(modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    "Speaking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            VoiceState.TRANSCRIBING -> {
                                Text(
                                    "Transcribing...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            VoiceState.THINKING -> {
                                Text(
                                    "Thinking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            else -> {}
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        placeholder = { Text("Message bringalive...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Right side: Mic button (always) + Send button (when text present)
            AnimatedContent(
                targetState = !isVoiceActive && text.isNotBlank(),
                transitionSpec = {
                    scaleIn(tween(150)) togetherWith scaleOut(tween(150))
                },
                label = "actionBtn",
            ) { showSend ->
                if (showSend) {
                    IconButton(
                        onClick = onSend,
                        enabled = !isGenerating,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (!isGenerating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    MicButton(
                        voiceState = voiceState,
                        amplitude = voiceAmplitude,
                        onClick = onMicToggle,
                    )
                }
            }
        }
    }
}
