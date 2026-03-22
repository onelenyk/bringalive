package dev.onelenyk.bringalive.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.onelenyk.bringalive.data.AgentLogStore
import dev.onelenyk.bringalive.data.LogEntry
import dev.onelenyk.bringalive.data.LogLevel
import org.koin.compose.koinInject

class LogsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val logStore: AgentLogStore = koinInject()
        val allLogs by logStore.logs.collectAsState()
        val listState = rememberLazyListState()
        var showErrorsOnly by remember { mutableStateOf(false) }

        val logs = if (showErrorsOnly) {
            allLogs.filter { it.level == LogLevel.ERROR || it.level == LogLevel.WARN }
        } else {
            allLogs
        }

        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Logs (${allLogs.size})") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { logStore.clear() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    FilterChip(
                        selected = !showErrorsOnly,
                        onClick = { showErrorsOnly = false },
                        label = { Text("All", fontSize = 12.sp) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = showErrorsOnly,
                        onClick = { showErrorsOnly = true },
                        label = {
                            val errorCount = allLogs.count { it.level == LogLevel.ERROR || it.level == LogLevel.WARN }
                            Text("Errors ($errorCount)", fontSize = 12.sp)
                        },
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E)),
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                if (showErrorsOnly) "No errors. Nice!" else "No logs yet. Send a message to see agent activity.",
                                color = Color(0xFF888888),
                                modifier = Modifier.padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    items(logs) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        // Timestamp
        Text(
            text = entry.timestamp,
            color = Color(0xFF555555),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(4.dp))

        // Level indicator
        Text(
            text = entry.level.name.first().toString(),
            color = levelColor(entry.level),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(4.dp))

        // Tag
        Text(
            text = entry.tag,
            color = tagColor(entry.tag),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(6.dp))

        // Message
        Text(
            text = entry.message,
            color = messageColor(entry.level),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 15,
        )

        // Duration
        if (entry.durationMs != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "(${entry.durationMs}ms)",
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> Color(0xFF666666)
    LogLevel.INFO -> Color(0xFF4CAF50)
    LogLevel.WARN -> Color(0xFFFF9800)
    LogLevel.ERROR -> Color(0xFFF44336)
}

private fun messageColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> Color(0xFFEF9A9A)
    LogLevel.WARN -> Color(0xFFFFCC80)
    else -> Color(0xFFCCCCCC)
}

private fun tagColor(tag: String): Color = when (tag) {
    "USER" -> Color(0xFF4FC3F7)
    "AGENT" -> Color(0xFF81C784)
    "TOOL" -> Color(0xFFFFB74D)
    "LLM" -> Color(0xFFCE93D8)
    "NODE" -> Color(0xFF90A4AE)
    "ERROR" -> Color(0xFFE57373)
    else -> Color(0xFFAAAAAA)
}
