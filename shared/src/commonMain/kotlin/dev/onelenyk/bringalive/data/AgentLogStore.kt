package dev.onelenyk.bringalive.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.TimeSource

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val durationMs: Long? = null,
)

class AgentLogStore {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timers = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    /** Start a timer for a keyed operation. */
    fun startTimer(key: String) {
        timers[key] = TimeSource.Monotonic.markNow()
    }

    /** Stop a timer and return elapsed ms. */
    fun stopTimer(key: String): Long? {
        val mark = timers.remove(key) ?: return null
        return mark.elapsedNow().inWholeMilliseconds
    }

    /** Log with explicit level. */
    fun log(level: LogLevel, tag: String, message: String, durationMs: Long? = null) {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val ts = "${local.hour.pad()}:${local.minute.pad()}:${local.second.pad()}.${(local.nanosecond / 1_000_000).pad3()}"
        _logs.update { entries ->
            (entries + LogEntry(ts, level, tag, message, durationMs)).takeLast(MAX_ENTRIES)
        }
    }

    /** Backwards-compatible: log(tag, message) defaults to INFO. */
    fun log(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun clear() {
        _logs.update { emptyList() }
        timers.clear()
    }

    private fun Int.pad() = toString().padStart(2, '0')
    private fun Int.pad3() = toString().padStart(3, '0')

    companion object {
        private const val MAX_ENTRIES = 1000
    }
}
