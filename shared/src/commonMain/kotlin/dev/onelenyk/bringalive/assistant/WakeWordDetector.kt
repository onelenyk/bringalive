package dev.onelenyk.bringalive.assistant

sealed class WakeWordResult {
    data object NotFound : WakeWordResult()
    data object WakeWordOnly : WakeWordResult()
    data class WithCommand(val command: String) : WakeWordResult()
}

fun detectWakeWord(transcript: String, wakePhrase: String): WakeWordResult {
    val normalized = transcript.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
    val phrase = wakePhrase.lowercase().trim()

    if (!normalized.contains(phrase)) return WakeWordResult.NotFound

    val idx = normalized.indexOf(phrase)
    val afterWake = normalized.substring(idx + phrase.length).trim()

    if (afterWake.isEmpty()) return WakeWordResult.WakeWordOnly

    // Extract from original transcript preserving case
    val origIdx = transcript.lowercase().indexOf(phrase)
    val originalAfter = transcript.substring(origIdx + phrase.length)
        .trim().trimStart(',', '.', '!', '?', ' ')

    return if (originalAfter.isBlank()) {
        WakeWordResult.WakeWordOnly
    } else {
        WakeWordResult.WithCommand(originalAfter)
    }
}
