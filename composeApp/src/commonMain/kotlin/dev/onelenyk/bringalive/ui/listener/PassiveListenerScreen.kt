package dev.onelenyk.bringalive.ui.listener

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.onelenyk.bringalive.listener.ControllerState
import dev.onelenyk.bringalive.listener.ListenerState
import dev.onelenyk.bringalive.listener.TranscriptEntry
import dev.onelenyk.bringalive.presentation.PassiveListenerViewModel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

class PassiveListenerScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinViewModel<PassiveListenerViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        val listState = rememberLazyListState()

        // Auto-scroll to newest transcript
        LaunchedEffect(uiState.transcripts.size) {
            if (uiState.transcripts.isNotEmpty()) {
                listState.animateScrollToItem(uiState.transcripts.size - 1)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Listener Mode") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
            ) {
                // Status row
                StatusRow(
                    state = uiState.listenerState,
                    amplitude = uiState.amplitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                HorizontalDivider()

                // Live transcript feed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = "Live Transcript",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (uiState.transcripts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (uiState.isActive) "Waiting for speech..." else "Start listening to see transcripts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 4.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(uiState.transcripts, key = { it.id }) { entry ->
                                TranscriptItem(entry = entry)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Notes section
                uiState.lastNote?.let { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        val lastRunTime = (uiState.controllerState as? ControllerState.LastRun)?.timestamp
                        val timeLabel = lastRunTime?.let { formatTime(it) } ?: ""
                        Text(
                            text = "Notes${if (timeLabel.isNotEmpty()) " — last processed $timeLabel" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = note.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    HorizontalDivider()
                }

                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.toggleListening() },
                        modifier = Modifier.weight(1f),
                        colors = if (uiState.isActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Icon(
                            if (uiState.isActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isActive) "Stop" else "Start")
                    }

                    OutlinedButton(
                        onClick = { viewModel.processNow() },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isActive &&
                            uiState.controllerState !is ControllerState.Processing &&
                            uiState.transcripts.any { !it.processed },
                    ) {
                        Text(
                            when (uiState.controllerState) {
                                is ControllerState.Processing -> "Processing..."
                                else -> "Process Now"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    state: ListenerState,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status dot
        val dotColor = when (state) {
            ListenerState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ListenerState.LISTENING -> MaterialTheme.colorScheme.primary
            ListenerState.RECORDING -> MaterialTheme.colorScheme.error
            ListenerState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiary
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )

        Text(
            text = when (state) {
                ListenerState.IDLE -> "Idle"
                ListenerState.LISTENING -> "Listening"
                ListenerState.RECORDING -> "Recording"
                ListenerState.TRANSCRIBING -> "Transcribing"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Amplitude bar
        if (state != ListenerState.IDLE) {
            LinearProgressIndicator(
                progress = { amplitude },
                modifier = Modifier
                    .width(80.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when (state) {
                    ListenerState.RECORDING -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun TranscriptItem(entry: TranscriptEntry) {
    val timeStr = formatTime(entry.timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.processed) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val local = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
