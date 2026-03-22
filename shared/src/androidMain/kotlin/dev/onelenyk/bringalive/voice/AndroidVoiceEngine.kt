package dev.onelenyk.bringalive.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import dev.onelenyk.bringalive.data.AgentLogStore
import dev.onelenyk.bringalive.data.SettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "VoiceEngine"

/**
 * Android voice engine using:
 * - AudioRecord for mic input + VAD
 * - Groq Whisper API for STT
 * - Groq PlayAI API for TTS
 * - MediaPlayer for audio playback
 *
 * VAD algorithm ported from aisist voice-engine.html.
 */
class AndroidVoiceEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val logStore: AgentLogStore,
    private val httpClient: HttpClient,
) : VoiceEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sttClient = GroqSttClient(httpClient)
    private val ttsClient = GroqTtsClient(httpClient)
    private val sentenceChunker = SentenceChunker()

    // --- State ---
    private val _state = MutableStateFlow(VoiceState.IDLE)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f) // 0..1 normalized
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _transcribedText = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val transcribedText: SharedFlow<String> = _transcribedText.asSharedFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val error: SharedFlow<String> = _error.asSharedFlow()

    // --- Audio ---
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsJob: Job? = null

    // --- VAD Config ---
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val vadThresholdDb = -35.0  // dB below which is "silence"
    private val silenceTimeoutMs = 1500L // 1.5s of silence = end of speech
    private val minSpeechMs = 500L       // min recording duration before accepting
    private val noiseGateDb = -50.0      // below this = ignore entirely

    // --- VAD State ---
    @Volatile private var isSpeechDetected = false
    @Volatile private var speechStartTime = 0L
    @Volatile private var lastSpeechTime = 0L

    // ========== Public API ==========

    override fun startListening() {
        if (_state.value != VoiceState.IDLE) {
            Log.w(TAG, "startListening: already in state ${_state.value}")
            return
        }
        Log.i(TAG, "Starting voice listening")
        logStore.info("VOICE", "Listening started")
        _state.value = VoiceState.LISTENING
        startAudioCapture()
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping voice listening")
        stopAudioCapture()
        _state.value = VoiceState.IDLE
        _amplitude.value = 0f
    }

    override fun stopSpeaking() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        ttsJob?.cancel()
        sentenceChunker.reset()
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.IDLE
        }
    }

    /** Feed streaming text from agent for TTS. */
    override fun onAgentTextDelta(delta: String) {
        if (_state.value != VoiceState.SPEAKING && _state.value != VoiceState.THINKING) return
        val sentences = sentenceChunker.feed(delta)
        for (sentence in sentences) {
            enqueueTts(sentence)
        }
    }

    /** Flush remaining text when agent response is complete. */
    override fun onAgentResponseComplete() {
        sentenceChunker.flush()?.let { enqueueTts(it) }
    }

    /** Signal that agent is now processing (after STT). */
    override fun setThinking() {
        _state.value = VoiceState.THINKING
    }

    override fun destroy() {
        stopListening()
        stopSpeaking()
        scope.cancel()
    }

    // ========== Audio Capture + VAD ==========

    private fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            logStore.error("VOICE", "Mic permission denied")
            _state.value = VoiceState.IDLE
            scope.launch { _error.emit("Microphone permission not granted") }
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            logStore.error("VOICE", "AudioRecord init failed")
            _state.value = VoiceState.IDLE
            return
        }

        audioRecord?.startRecording()
        isSpeechDetected = false
        speechStartTime = 0L
        lastSpeechTime = 0L

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            val audioData = ByteArrayOutputStream()
            var silenceStartTime = System.currentTimeMillis()

            Log.d(TAG, "VAD loop started, bufferSize=$bufferSize")

            while (isActive && _state.value in listOf(VoiceState.LISTENING, VoiceState.RECORDING)) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) continue

                // Calculate RMS amplitude
                val rms = calculateRms(buffer, read)
                val db = if (rms > 0) 20 * log10(rms / 32768.0) else -100.0

                // Normalize to 0..1 for UI
                val normalized = ((db + 60) / 60.0).coerceIn(0.0, 1.0).toFloat()
                _amplitude.value = normalized

                val now = System.currentTimeMillis()

                // Noise gate: ignore very quiet audio
                if (db < noiseGateDb) {
                    // Check silence timeout
                    if (isSpeechDetected && (now - lastSpeechTime > silenceTimeoutMs)) {
                        val duration = now - speechStartTime
                        if (duration >= minSpeechMs) {
                            Log.d(TAG, "VAD: silence detected after ${duration}ms of speech")
                            // Speech ended — transcribe
                            finishRecording(audioData.toByteArray())
                            return@launch
                        } else {
                            // Too short, reset
                            Log.d(TAG, "VAD: speech too short (${duration}ms), resetting")
                            isSpeechDetected = false
                            audioData.reset()
                        }
                    }
                    continue
                }

                if (db > vadThresholdDb) {
                    // Speech detected
                    if (!isSpeechDetected) {
                        isSpeechDetected = true
                        speechStartTime = now
                        audioData.reset()
                        withContext(Dispatchers.Main) {
                            _state.value = VoiceState.RECORDING
                        }
                        Log.d(TAG, "VAD: speech started (db=${"%.1f".format(db)})")
                        logStore.info("VOICE", "Speech detected")
                    }
                    lastSpeechTime = now
                }

                // Write PCM to buffer (only when speech detected or around speech)
                if (isSpeechDetected) {
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    audioData.write(bytes)
                }

                // Silence timeout check during recording
                if (isSpeechDetected && db <= vadThresholdDb) {
                    if (now - lastSpeechTime > silenceTimeoutMs) {
                        val duration = now - speechStartTime
                        if (duration >= minSpeechMs) {
                            Log.d(TAG, "VAD: silence timeout after ${duration}ms of speech")
                            finishRecording(audioData.toByteArray())
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun stopAudioCapture() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
    }

    private fun calculateRms(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / size)
    }

    // ========== STT ==========

    private fun finishRecording(pcmData: ByteArray) {
        stopAudioCapture()

        if (pcmData.isEmpty()) {
            Log.w(TAG, "No audio data to transcribe")
            scope.launch(Dispatchers.Main) { _state.value = VoiceState.IDLE }
            return
        }

        scope.launch(Dispatchers.Main) {
            _state.value = VoiceState.TRANSCRIBING
            _amplitude.value = 0f
        }

        scope.launch {
            logStore.info("VOICE", "Transcribing ${pcmData.size / 1024}KB of audio...")
            logStore.startTimer("stt")

            // Convert PCM to WAV for Groq API
            val wavBytes = pcmToWav(pcmData, sampleRate, 1, 16)

            val apiKey = settings.groqApiKey
            if (apiKey.isBlank()) {
                logStore.error("VOICE", "Groq API key not set")
                scope.launch(Dispatchers.Main) {
                    _error.emit("Groq API key not configured. Set it in Settings → Voice.")
                    _state.value = VoiceState.IDLE
                }
                return@launch
            }

            val result = sttClient.transcribe(
                audioBytes = wavBytes,
                apiKey = apiKey,
                model = settings.sttModel,
                language = settings.voiceLanguage,
                filename = "audio.wav",
                mimeType = "audio/wav",
            )

            val ms = logStore.stopTimer("stt")
            if (result.error != null) {
                logStore.error("VOICE", "STT error: ${result.error}")
                withContext(Dispatchers.Main) {
                    _error.emit("STT error: ${result.error}")
                    _state.value = VoiceState.IDLE
                }
            } else if (result.text.isBlank()) {
                logStore.warn("VOICE", "STT returned empty text")
                withContext(Dispatchers.Main) { _state.value = VoiceState.IDLE }
            } else {
                logStore.info("VOICE", "STT: \"${result.text}\" (${ms}ms)")
                withContext(Dispatchers.Main) {
                    _transcribedText.emit(result.text)
                    // State will be set to THINKING by the ViewModel
                }
            }
        }
    }

    // ========== TTS ==========

    private val ttsQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var isTtsPlaying = false

    private fun enqueueTts(sentence: String) {
        if (settings.groqApiKey.isBlank()) return
        ttsQueue.add(sentence)
        if (!isTtsPlaying) {
            processTtsQueue()
        }
    }

    private fun processTtsQueue() {
        val sentence = ttsQueue.poll() ?: run {
            isTtsPlaying = false
            if (_state.value == VoiceState.SPEAKING) {
                _state.value = VoiceState.IDLE
                logStore.info("VOICE", "TTS finished")
            }
            return
        }

        isTtsPlaying = true
        scope.launch(Dispatchers.Main) { _state.value = VoiceState.SPEAKING }

        ttsJob = scope.launch {
            logStore.startTimer("tts")
            val result = ttsClient.synthesize(
                text = sentence,
                apiKey = settings.groqApiKey,
                voice = settings.ttsVoice,
            )
            val ms = logStore.stopTimer("tts")

            if (result.error != null) {
                logStore.error("VOICE", "TTS error: ${result.error}")
                isTtsPlaying = false
                return@launch
            }

            logStore.info("VOICE", "TTS: \"${sentence.take(50)}\" → ${result.audioBytes!!.size / 1024}KB (${ms}ms)")

            // Play the audio
            withContext(Dispatchers.IO) {
                try {
                    val tempFile = File.createTempFile("tts", ".wav", context.cacheDir)
                    tempFile.writeBytes(result.audioBytes)

                    withContext(Dispatchers.Main) {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                tempFile.delete()
                                processTtsQueue() // play next sentence
                            }
                            setOnErrorListener { _, _, _ ->
                                tempFile.delete()
                                isTtsPlaying = false
                                false
                            }
                            start()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TTS playback error", e)
                    logStore.error("VOICE", "TTS playback: ${e.message}")
                    isTtsPlaying = false
                }
            }
        }
    }

    // ========== PCM → WAV ==========

    private fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val dataLength = pcmData.size
        val totalLength = 36 + dataLength
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalLength)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // chunk size
        writeShort(header, 20, 1) // PCM format
        writeShort(header, 22, channels)
        writeInt(header, 24, sampleRate)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, blockAlign)
        writeShort(header, 34, bitsPerSample)
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, dataLength)

        return header + pcmData
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = (value shr 8 and 0xFF).toByte()
        data[offset + 2] = (value shr 16 and 0xFF).toByte()
        data[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}
