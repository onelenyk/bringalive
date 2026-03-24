package dev.onelenyk.bringalive.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    private val _callMode = MutableStateFlow(false)
    override val callMode: StateFlow<Boolean> = _callMode.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(settings.autoSpeak)
    override val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    // --- Audio ---
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var ttsJob: Job? = null
    private var bargeInJob: Job? = null

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

    /** Set this from MainActivity to handle permission requests. */
    var onPermissionNeeded: (() -> Unit)? = null

    override fun startListening() {
        if (_state.value != VoiceState.IDLE) {
            Log.w(TAG, "startListening: already in state ${_state.value}")
            return
        }

        // Check runtime permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, requesting...")
            logStore.warn("VOICE", "Mic permission not granted — requesting")
            onPermissionNeeded?.invoke()
            return
        }

        Log.i(TAG, "Starting voice listening")
        logStore.info("VOICE", "Listening started")
        _state.value = VoiceState.LISTENING
        onServiceStart?.invoke()
        startAudioCapture()
    }

    /** Called by platform to start/stop foreground service. */
    var onServiceStart: (() -> Unit)? = null
    var onServiceStop: (() -> Unit)? = null

    override fun stopListening() {
        Log.i(TAG, "Stopping voice listening")
        stopAudioCapture()
        _state.value = VoiceState.IDLE
        _amplitude.value = 0f
    }

    override fun stopSpeaking() {
        bargeInJob?.cancel()
        bargeInJob = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        audioTrack?.release()
        audioTrack = null
        ttsJob?.cancel()
        ttsQueue.clear()
        isTtsPlaying = false
        sentenceChunker.reset()
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.IDLE
        }
    }

    /** Feed streaming text from agent for TTS. */
    override fun onAgentTextDelta(delta: String) {
        val inVoiceFlow = _state.value == VoiceState.SPEAKING || _state.value == VoiceState.THINKING
        if (!inVoiceFlow && !_ttsEnabled.value) return
        if (settings.groqApiKey.isBlank()) return // no TTS without key

        val sentences = sentenceChunker.feed(delta)
        for (sentence in sentences) {
            if (_state.value == VoiceState.IDLE || _state.value == VoiceState.THINKING) {
                _state.value = VoiceState.SPEAKING
            }
            enqueueTts(sentence)
        }
    }

    /** Flush remaining text when agent response is complete. */
    override fun onAgentResponseComplete() {
        val inVoiceFlow = _state.value == VoiceState.THINKING || _state.value == VoiceState.SPEAKING
        val shouldTts = inVoiceFlow || _ttsEnabled.value

        val remaining = sentenceChunker.flush()
        if (remaining != null && settings.groqApiKey.isNotBlank() && shouldTts) {
            if (_state.value == VoiceState.IDLE || _state.value == VoiceState.THINKING) {
                _state.value = VoiceState.SPEAKING
            }
            enqueueTts(remaining)
        } else {
            // No TTS to play
            if (_state.value == VoiceState.THINKING || _state.value == VoiceState.SPEAKING) {
                if (_callMode.value) {
                    Log.d(TAG, "Agent done, no TTS — call mode, restarting listening")
                    _state.value = VoiceState.IDLE
                    scope.launch {
                        delay(300)
                        if (_callMode.value) startListening()
                    }
                } else {
                    Log.d(TAG, "Agent response complete, no TTS — IDLE")
                    _state.value = VoiceState.IDLE
                    onServiceStop?.invoke()
                }
            }
        }
    }

    /** Signal that agent is now processing (after STT). */
    override fun setThinking() {
        _state.value = VoiceState.THINKING
    }

    override fun toggleTts() {
        val newVal = !_ttsEnabled.value
        _ttsEnabled.value = newVal
        settings.autoSpeak = newVal
        Log.i(TAG, "TTS toggle: $newVal")
        logStore.info("VOICE", "TTS ${if (newVal) "enabled" else "disabled"}")
        if (!newVal) {
            stopSpeaking()
        }
    }

    override fun testTts() {
        val apiKey = settings.groqApiKey
        if (apiKey.isBlank()) {
            scope.launch { _error.emit("Groq API key not set. Configure it above.") }
            return
        }
        Log.i(TAG, "Testing TTS: model=${settings.ttsModel}, voice=${settings.ttsVoice}")
        logStore.info("VOICE", "TTS test started")
        stopSpeaking()
        sentenceChunker.reset()
        ttsQueue.clear()
        _state.value = VoiceState.SPEAKING
        enqueueTts("Hello! I am your personal assistant. Text to speech is working correctly.")
    }

    override suspend fun speak(text: String) {
        if (settings.groqApiKey.isBlank()) return
        stopSpeaking()
        sentenceChunker.reset()
        val sentences = (sentenceChunker.feed(text) + listOfNotNull(sentenceChunker.flush()))
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) return
        _state.value = VoiceState.SPEAKING
        for (sentence in sentences) enqueueTts(sentence)
        if (!isTtsPlaying) processTtsQueue()
        // Suspend until playback finishes or gets interrupted
        state.first { it != VoiceState.SPEAKING }
    }

    override fun toggleCallMode() {
        if (_callMode.value) {
            // Exit call mode
            Log.i(TAG, "Call mode OFF")
            logStore.info("VOICE", "Call mode ended")
            _callMode.value = false
            stopListening()
            stopSpeaking()
            onServiceStop?.invoke()
        } else {
            // Enter call mode
            Log.i(TAG, "Call mode ON")
            logStore.info("VOICE", "Call mode started")
            _callMode.value = true
            startListening()
        }
    }

    override fun destroy() {
        _callMode.value = false
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

    private fun startBargeInListener() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return
        bargeInJob?.cancel()

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(4096)

        Log.i(TAG, "Barge-in: starting listener (bufferSize=$bufferSize)")

        bargeInJob = scope.launch(Dispatchers.IO) {
            // Try VOICE_RECOGNITION first (has echo cancellation); fall back to MIC
            val recorder = createBargeInRecorder(bufferSize) ?: run {
                Log.e(TAG, "Barge-in: failed to create AudioRecord")
                return@launch
            }

            recorder.startRecording()
            Log.i(TAG, "Barge-in: recorder started, state=${recorder.state}")

            val buffer = ShortArray(bufferSize / 2)
            var consecutiveSpeechFrames = 0
            var frameCount = 0
            try {
                while (isActive && _state.value == VoiceState.SPEAKING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    val rms = calculateRms(buffer, read)
                    val db = if (rms > 0) 20 * log10(rms / 32768.0) else -100.0
                    frameCount++

                    // Log audio levels every 40 frames to help tune threshold
                    if (frameCount % 40 == 0) {
                        Log.d(TAG, "Barge-in: db=${"%.1f".format(db)} threshold=$vadThresholdDb state=${_state.value}")
                    }

                    if (db > vadThresholdDb) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) {
                            Log.i(TAG, "Barge-in TRIGGERED (db=${"%.1f".format(db)}, frames=$consecutiveSpeechFrames)")
                            logStore.info("VOICE", "Barge-in: user interrupted TTS")
                            withContext(Dispatchers.Main) {
                                if (_state.value == VoiceState.SPEAKING) {
                                    stopSpeaking()
                                    startListening()
                                }
                            }
                            return@launch
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                }
                Log.d(TAG, "Barge-in: loop ended (state=${_state.value})")
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
                Log.i(TAG, "Barge-in: listener stopped")
            }
        }
    }

    private fun createBargeInRecorder(bufferSize: Int): AudioRecord? {
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val rec = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "Barge-in: AudioRecord created with source=$source")
                    return rec
                }
                Log.w(TAG, "Barge-in: source=$source state=${rec.state}, trying next")
                rec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Barge-in: source=$source threw ${e.message}, trying next")
            }
        }
        return null
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

    private data class TtsQueueItem(val text: String, val model: String, val voice: String)

    private val ttsQueue = java.util.concurrent.ConcurrentLinkedQueue<TtsQueueItem>()
    @Volatile private var isTtsPlaying = false

    private fun enqueueTts(sentence: String) {
        if (settings.groqApiKey.isBlank()) return
        // Validate voice for current model, fallback to default
        val model = GroqTtsVoices.modelById(settings.ttsModel) ?: GroqTtsModel.ORPHEUS_ENGLISH
        val validVoices = GroqTtsVoices.voicesForModel(model)
        val voice = if (validVoices.any { it.id == settings.ttsVoice }) {
            settings.ttsVoice
        } else {
            GroqTtsVoices.defaultVoiceForModel(model).id
        }
        ttsQueue.add(TtsQueueItem(sentence, model.id, voice))
        if (!isTtsPlaying) {
            startBargeInListener()
            processTtsQueue()
        }
    }

    private fun processTtsQueue() {
        val item = ttsQueue.poll() ?: run {
            isTtsPlaying = false
            if (_state.value == VoiceState.SPEAKING || _state.value == VoiceState.THINKING) {
                if (_callMode.value) {
                    Log.d(TAG, "TTS done — call mode, restarting listening")
                    logStore.info("VOICE", "TTS done → listening again (call mode)")
                    _state.value = VoiceState.IDLE
                    scope.launch {
                        delay(300)
                        if (_callMode.value) startListening()
                    }
                } else {
                    _state.value = VoiceState.IDLE
                    logStore.info("VOICE", "TTS finished — IDLE")
                    onServiceStop?.invoke()
                }
            }
            return
        }

        isTtsPlaying = true
        scope.launch(Dispatchers.Main) { _state.value = VoiceState.SPEAKING }

        ttsJob = scope.launch {
            logStore.startTimer("tts")
            val result = ttsClient.synthesize(
                text = item.text,
                apiKey = settings.groqApiKey,
                model = item.model,
                voice = item.voice,
            )
            val ms = logStore.stopTimer("tts")

            if (result.error != null) {
                logStore.error("VOICE", "TTS error: ${result.error}")
                withContext(Dispatchers.Main) {
                    _error.emit("TTS error: ${result.error}")
                }
                isTtsPlaying = false
                // Try next item — one failure shouldn't stop all
                withContext(Dispatchers.Main) { processTtsQueue() }
                return@launch
            }

            val audioBytes = result.audioBytes!!
            val header = audioBytes.take(4).map { "%02X".format(it) }.joinToString(" ")
            logStore.info("VOICE", "TTS: \"${item.text.take(50)}\" → ${audioBytes.size / 1024}KB (${ms}ms) header=[$header]")

            // Play the WAV audio via AudioTrack (more robust than MediaPlayer for raw WAV)
            withContext(Dispatchers.IO) {
                try {
                    playWavBytes(audioBytes)
                    // Done playing, process next
                    withContext(Dispatchers.Main) { processTtsQueue() }
                } catch (e: Exception) {
                    Log.e(TAG, "TTS playback error", e)
                    logStore.error("VOICE", "TTS playback: ${e.message}")
                    scope.launch { _error.emit("TTS playback: ${e.message}") }
                    isTtsPlaying = false
                }
            }
        }
    }

    /** Parse WAV header and play PCM data via AudioTrack. Blocking call. */
    private fun playWavBytes(wavBytes: ByteArray) {
        if (wavBytes.size < 44) throw IllegalArgumentException("WAV too small: ${wavBytes.size} bytes")

        // Parse WAV header
        val wavSampleRate = readIntLE(wavBytes, 24)
        val wavChannels = readShortLE(wavBytes, 22)
        val wavBitsPerSample = readShortLE(wavBytes, 34)

        Log.d(TAG, "WAV: rate=$wavSampleRate ch=$wavChannels bits=$wavBitsPerSample size=${wavBytes.size}")

        val channelConfig = if (wavChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val audioEncoding = when (wavBitsPerSample) {
            16 -> AudioFormat.ENCODING_PCM_16BIT
            8 -> AudioFormat.ENCODING_PCM_8BIT
            32 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }

        // Find "data" chunk — skip any extra chunks (Groq puts extra metadata before data)
        var dataOffset = 12 // skip RIFF header
        var dataSize = 0
        while (dataOffset < wavBytes.size - 8) {
            val chunkId = String(wavBytes, dataOffset, 4)
            val chunkSize = readIntLE(wavBytes, dataOffset + 4)
            if (chunkId == "data") {
                dataOffset += 8
                // chunkSize can be -1 (0xFFFFFFFF) meaning "rest of file"
                dataSize = if (chunkSize <= 0) {
                    wavBytes.size - dataOffset
                } else {
                    chunkSize.coerceAtMost(wavBytes.size - dataOffset)
                }
                break
            }
            // Skip unknown chunks; protect against bad chunkSize
            if (chunkSize <= 0) break
            dataOffset += 8 + chunkSize
        }

        if (dataSize <= 0) throw IllegalArgumentException("No data chunk in WAV (offset=$dataOffset)")

        val minBuf = AudioTrack.getMinBufferSize(wavSampleRate, channelConfig, audioEncoding)
            .coerceAtLeast(4096)
        Log.d(TAG, "AudioTrack: dataOffset=$dataOffset dataSize=$dataSize minBuf=$minBuf")

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(wavSampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioEncoding)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialize, state=${track.state}")
            track.release()
            throw IllegalStateException("AudioTrack init failed")
        }

        audioTrack = track
        track.play()

        // Stream PCM data in chunks
        var offset = dataOffset
        val end = dataOffset + dataSize
        while (offset < end) {
            val chunkSize = minOf(minBuf, end - offset)
            val written = track.write(wavBytes, offset, chunkSize)
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error: $written")
                break
            }
            offset += written
        }

        // Wait for remaining buffered audio to finish
        track.stop()
        track.release()
        audioTrack = null
    }

    private fun readIntLE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8)

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
