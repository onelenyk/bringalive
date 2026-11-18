package dev.onelenyk.bringalive.listener

import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class TranscriptRepository(private val workspace: WorkspaceManager) {

    private val _transcripts = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcripts: StateFlow<List<TranscriptEntry>> = _transcripts.asStateFlow()

    fun addTranscript(entry: TranscriptEntry) {
        _transcripts.update { current ->
            (current + entry).takeLast(200)
        }
        persistEntry(entry)
    }

    fun getUnprocessed(): List<TranscriptEntry> =
        _transcripts.value.filter { !it.processed }

    fun markProcessed(ids: List<String>) {
        _transcripts.update { current ->
            current.map { if (it.id in ids) it.copy(processed = true) else it }
        }
    }

    fun clearAll() {
        _transcripts.value = emptyList()
    }

    private fun persistEntry(entry: TranscriptEntry) {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val line = "${entry.id}\t${entry.timestamp}\t${entry.text.replace("\n", " ")}"
        workspace.append("transcripts/$today.jsonl", line)
    }
}
