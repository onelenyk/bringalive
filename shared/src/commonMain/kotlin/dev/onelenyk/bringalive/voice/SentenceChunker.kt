package dev.onelenyk.bringalive.voice

/**
 * Accumulates streaming text deltas and emits complete sentences for TTS.
 * Detects boundaries at: . ! ? ... followed by whitespace or end.
 * Also splits on double newlines.
 * Minimum sentence length prevents splitting on abbreviations (Dr. etc.)
 */
class SentenceChunker(
    private val minSentenceLength: Int = 15,
) {
    private val buffer = StringBuilder()

    /**
     * Feed a text delta. Returns list of complete sentences ready for TTS.
     */
    fun feed(delta: String): List<String> {
        buffer.append(delta)
        val sentences = mutableListOf<String>()
        val text = buffer.toString()

        var lastCut = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            // Check for sentence-ending punctuation
            if (ch == '.' || ch == '!' || ch == '?') {
                // Look ahead: must be followed by whitespace, newline, or end of buffer
                val next = text.getOrNull(i + 1)
                if (next == null || next.isWhitespace() || next == '\n') {
                    val candidate = text.substring(lastCut, i + 1).trim()
                    if (candidate.length >= minSentenceLength) {
                        sentences.add(candidate)
                        lastCut = i + 1
                    }
                }
            }
            // Double newline = paragraph break
            if (ch == '\n' && text.getOrNull(i + 1) == '\n') {
                val candidate = text.substring(lastCut, i).trim()
                if (candidate.isNotBlank()) {
                    sentences.add(candidate)
                    lastCut = i + 2
                    i++ // skip second newline
                }
            }
            i++
        }

        // Keep only the un-emitted remainder
        if (lastCut > 0) {
            buffer.delete(0, lastCut)
        }

        return sentences
    }

    /**
     * Flush remaining buffer content as a final sentence.
     */
    fun flush(): String? {
        val remaining = buffer.toString().trim()
        buffer.clear()
        return remaining.ifEmpty { null }
    }

    fun reset() {
        buffer.clear()
    }
}
