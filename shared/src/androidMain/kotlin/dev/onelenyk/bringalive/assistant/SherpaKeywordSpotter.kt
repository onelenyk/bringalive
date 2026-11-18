package dev.onelenyk.bringalive.assistant

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

private const val TAG = "SherpaKWS"

class SherpaKeywordSpotter(
    private val context: Context,
) {
    private var spotter: KeywordSpotter? = null
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
    private var vocabulary: Map<String, Int> = emptyMap()
    private var modelDir: String = ""

    /**
     * Initialize with a plain-text wake phrase (e.g. "hey note").
     * Automatically tokenizes it to BPE format for the model.
     *
     * @param threshold Detection threshold (0-1). Lower = easier to trigger. Default 0.25.
     * @param score Boost score for keywords in beam search. Higher = favored more. Default 1.5.
     * @param trailingBlanks Blank frames before finalizing. Higher = more conservative. Default 1.
     */
    fun init(
        wakePhrase: String = "hey note",
        threshold: Float = 0.25f,
        score: Float = 1.5f,
        trailingBlanks: Int = 1,
    ) {
        modelDir = copyAssetsToCache()
        vocabulary = loadVocabulary("$modelDir/tokens.txt")

        val tokenized = tokenize(wakePhrase)
        Log.i(TAG, "Wake phrase: \"$wakePhrase\" → tokens: \"$tokenized\"")
        Log.i(TAG, "KWS config: threshold=$threshold, score=$score, trailingBlanks=$trailingBlanks")

        // Write dynamic keywords file with per-keyword score/threshold
        val kwFile = File(modelDir, "keywords_dynamic.txt")
        kwFile.writeText("$tokenized :$score #$threshold\n")

        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder.int8.onnx",
                    decoder = "$modelDir/decoder.int8.onnx",
                    joiner = "$modelDir/joiner.int8.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 2,
            ),
            keywordsFile = kwFile.absolutePath,
            keywordsThreshold = threshold,
            keywordsScore = score,
            maxActivePaths = 4,
            numTrailingBlanks = trailingBlanks,
        )

        spotter = KeywordSpotter(assetManager = null, config = config)
        stream = spotter?.createStream()
        Log.i(TAG, "Initialized with wake phrase: \"$wakePhrase\"")
    }

    fun acceptWaveform(samples: ShortArray, size: Int): String? {
        val s = stream ?: return null
        val kws = spotter ?: return null

        val floatSamples = FloatArray(size) { samples[it].toFloat() / 32768.0f }
        s.acceptWaveform(floatSamples, sampleRate = 16000)

        while (kws.isReady(s)) {
            kws.decode(s)
        }

        val result = kws.getResult(s)
        if (result.keyword.isNotBlank()) {
            Log.i(TAG, "Keyword detected: '${result.keyword}'")
            kws.reset(s)
            return result.keyword.trim()
        }

        return null
    }

    fun reset() {
        val s = stream
        val kws = spotter
        if (s != null && kws != null) {
            kws.reset(s)
        }
    }

    fun release() {
        stream?.release()
        stream = null
        spotter?.release()
        spotter = null
        Log.i(TAG, "Released")
    }

    // ========== BPE Tokenizer ==========

    /**
     * Convert plain English text to BPE token sequence for the model.
     * e.g. "hey note" → "▁HE Y ▁NO TE"
     */
    private fun tokenize(text: String): String {
        val words = text.trim().uppercase().split(Regex("\\s+"))
        val tokens = mutableListOf<String>()

        for (word in words) {
            val wordWithPrefix = "▁$word"
            tokenizeWord(wordWithPrefix, tokens)
        }

        return tokens.joinToString(" ")
    }

    /** Greedy longest-match tokenization of a single word. */
    private fun tokenizeWord(word: String, tokens: MutableList<String>) {
        var pos = 0
        while (pos < word.length) {
            var bestLen = 0
            var bestToken = ""

            // Try longest match first
            for (len in (word.length - pos) downTo 1) {
                val candidate = word.substring(pos, pos + len)
                if (vocabulary.containsKey(candidate)) {
                    bestLen = len
                    bestToken = candidate
                    break
                }
            }

            if (bestLen == 0) {
                // Single character fallback
                val ch = word[pos].toString()
                tokens.add(ch)
                pos++
            } else {
                tokens.add(bestToken)
                pos += bestLen
            }
        }
    }

    private fun loadVocabulary(path: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        File(path).forEachLine { line ->
            val parts = line.split(" ")
            if (parts.size >= 2) {
                val token = parts[0]
                val id = parts[1].toIntOrNull()
                if (id != null) {
                    map[token] = id
                }
            }
        }
        Log.d(TAG, "Loaded ${map.size} tokens from vocabulary")
        return map
    }

    private fun copyAssetsToCache(): String {
        val dir = File(context.cacheDir, "kws")
        if (dir.exists() && File(dir, "encoder.int8.onnx").exists()) {
            return dir.absolutePath
        }
        dir.mkdirs()

        val files = listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "joiner.int8.onnx",
            "tokens.txt",
            "keywords.txt",
        )

        for (filename in files) {
            context.assets.open("kws/$filename").use { input ->
                File(dir, filename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        Log.i(TAG, "Model files copied to ${dir.absolutePath}")
        return dir.absolutePath
    }
}
