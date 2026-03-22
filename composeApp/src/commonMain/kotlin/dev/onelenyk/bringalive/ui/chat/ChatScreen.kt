package dev.onelenyk.bringalive.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.onelenyk.bringalive.model.ChatMessage
import dev.onelenyk.bringalive.model.MessageRole
import dev.onelenyk.bringalive.presentation.ChatViewModel
import dev.onelenyk.bringalive.ui.logs.LogsScreen
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
        val scrollTarget = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
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
                            IconButton(onClick = { viewModel.onNewConversation() }) {
                                Icon(Icons.Default.Add, contentDescription = "New chat")
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
                            if (message.role == MessageRole.TOOL) {
                                ToolExecutionCard(
                                    toolName = message.toolName ?: "tool",
                                    result = message.content,
                                )
                            } else {
                                ChatBubble(message)
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

                    // Input bar
                    ChatInputBar(
                        text = state.inputText,
                        onTextChanged = viewModel::onInputChanged,
                        onSend = viewModel::onSendMessage,
                        onMicToggle = viewModel::onMicToggle,
                        isGenerating = state.isGenerating,
                        voiceState = state.voiceState,
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
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (!isUser) {
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
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                color = backgroundColor,
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
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
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    isGenerating: Boolean,
    voiceState: dev.onelenyk.bringalive.voice.VoiceState,
    modifier: Modifier = Modifier,
) {
    val isVoiceActive = voiceState != dev.onelenyk.bringalive.voice.VoiceState.IDLE

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isVoiceActive) {
                // Voice mode: show state text instead of TextField
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = when (voiceState) {
                            dev.onelenyk.bringalive.voice.VoiceState.LISTENING -> "Listening..."
                            dev.onelenyk.bringalive.voice.VoiceState.RECORDING -> "Recording..."
                            dev.onelenyk.bringalive.voice.VoiceState.TRANSCRIBING -> "Transcribing..."
                            dev.onelenyk.bringalive.voice.VoiceState.THINKING -> "Thinking..."
                            dev.onelenyk.bringalive.voice.VoiceState.SPEAKING -> "Speaking..."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = { Text("Message bringalive...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // Mic button
            IconButton(onClick = onMicToggle) {
                Icon(
                    imageVector = if (isVoiceActive) Icons.Default.Close else Icons.Default.Phone,
                    contentDescription = if (isVoiceActive) "Stop" else "Voice",
                    tint = when {
                        voiceState == dev.onelenyk.bringalive.voice.VoiceState.RECORDING -> MaterialTheme.colorScheme.error
                        isVoiceActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Send button (only in text mode with text)
            if (!isVoiceActive && text.isNotBlank()) {
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
            }
        }
    }
}
