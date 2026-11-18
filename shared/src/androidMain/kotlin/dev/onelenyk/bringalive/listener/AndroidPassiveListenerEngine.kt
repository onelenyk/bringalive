package dev.onelenyk.bringalive.listener

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.voice.GroqSttClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "PassiveListenerEngine"

class AndroidPassiveListenerEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val transcriptRepository: TranscriptRepository,
    private val httpClient: HttpClient,
) : PassiveListenerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sttClient = GroqSttClient(httpClient)

    private val _state = MutableStateFlow(ListenerState.IDLE)
    override val state: StateFlow<ListenerState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    override var onPermissionNeeded: (() -> Unit)? = null
    override var onServiceStart: (() -> Unit)? = null
    override var onServiceStop: (() -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // VAD config (mirrored from AndroidVoiceEngine)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val vadThresholdDb = -35.0
    private val silenceTimeoutMs = 1500L
    private val minSpeechMs = 500L
    private val noiseGateDb = -50.0

    // VAD state
    @Volatile private var isSpeechDetected = false
    @Volatile private var speechStartTime = 0L
    @Volatile private var lastSpeechTime = 0L

    override fun startListening() {
        if (_state.value != ListenerState.IDLE) {
            Log.w(TAG, "Already in state ${_state.value}")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            onPermissionNeeded?.invoke()
            return
        }

        Log.i(TAG, "Starting passive listener")
        _state.value = ListenerState.LISTENING
        onServiceStart?.invoke()
        startAudioCapture()
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping passive listener")
        stopAudioCapture()
        _state.value = ListenerState.IDLE
        _amplitude.value = 0f
        onServiceStop?.invoke()
    }

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
            Log.e(TAG, "Mic permission denied", e)
            _state.value = ListenerState.IDLE
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            _state.value = ListenerState.IDLE
            return
        }

        audioRecord?.startRecording()
        isSpeechDetected = false
        speechStartTime = 0L
        lastSpeechTime = 0L

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            val audioData = ByteArrayOutputStream()

            Log.d(TAG, "VAD loop started")

            while (isActive) {
                // Re-check state — stays alive through LISTENING/RECORDING/TRANSCRIBING
                val currentState = _state.value
                if (currentState == ListenerState.IDLE) break

                if (currentState == ListenerState.TRANSCRIBING) {
                    // Wait until transcription done, then resume
                    delay(100)
                    continue
                }

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) continue

                val rms = calculateRms(buffer, read)
                val db = if (rms > 0) 20 * log10(rms / 32768.0) else -100.0

                val normalized = ((db + 60) / 60.0).coerceIn(0.0, 1.0).toFloat()
                withContext(Dispatchers.Main) { _amplitude.value = normalized }

                val now = System.currentTimeMillis()

                if (db < noiseGateDb) {
                    if (isSpeechDetected && (now - lastSpeechTime > silenceTimeoutMs)) {
                        val duration = now - speechStartTime
                        if (duration >= minSpeechMs) {
                            Log.d(TAG, "VAD: silence after ${duration}ms — transcribing")
                            val pcm = audioData.toByteArray()
                            audioData.reset()
                            isSpeechDetected = false
                            withContext(Dispatchers.Main) {
                                _state.value = ListenerState.TRANSCRIBING
                                _amplitude.value = 0f
                            }
                            transcribeAndSave(pcm)
                            withContext(Dispatchers.Main) {
                                if (_state.value == ListenerState.TRANSCRIBING) {
                                    _state.value = ListenerState.LISTENING
                                }
                            }
                        } else {
                            Log.d(TAG, "VAD: speech too short (${duration}ms), resetting")
                            isSpeechDetected = false
                            audioData.reset()
                        }
                    }
                    continue
                }

                if (db > vadThresholdDb) {
                    if (!isSpeechDetected) {
                        isSpeechDetected = true
                        speechStartTime = now
                        audioData.reset()
                        withContext(Dispatchers.Main) { _state.value = ListenerState.RECORDING }
                        Log.d(TAG, "VAD: speech started (db=${"%.1f".format(db)})")
                    }
                    lastSpeechTime = now
                }

                if (isSpeechDetected) {
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    audioData.write(bytes)
                }

                if (isSpeechDetected && db <= vadThresholdDb) {
                    if (now - lastSpeechTime > silenceTimeoutMs) {
                        val duration = now - speechStartTime
                        if (duration >= minSpeechMs) {
                            Log.d(TAG, "VAD: silence timeout after ${duration}ms")
                            val pcm = audioData.toByteArray()
                            audioData.reset()
                            isSpeechDetected = false
                            withContext(Dispatchers.Main) {
                                _state.value = ListenerState.TRANSCRIBING
                                _amplitude.value = 0f
                            }
                            transcribeAndSave(pcm)
                            withContext(Dispatchers.Main) {
                                if (_state.value == ListenerState.TRANSCRIBING) {
                                    _state.value = ListenerState.LISTENING
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "VAD loop exited")
        }
    }

    private suspend fun transcribeAndSave(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        val apiKey = settings.groqApiKey
        if (apiKey.isBlank()) {
            Log.w(TAG, "Groq API key not set — skipping transcription")
            return
        }

        val wavBytes = pcmToWav(pcmData, sampleRate, 1, 16)

        val result = try {
            sttClient.transcribe(
                audioBytes = wavBytes,
                apiKey = apiKey,
                model = settings.sttModel,
                language = settings.voiceLanguage,
                filename = "audio.wav",
                mimeType = "audio/wav",
            )
        } catch (e: Exception) {
            Log.e(TAG, "STT request failed: ${e.message}")
            return
        }

        if (result.error != null) {
            Log.e(TAG, "STT error: ${result.error}")
            return
        }

        if (result.text.isBlank()) {
            Log.d(TAG, "STT returned empty text — skipping")
            return
        }

        Log.i(TAG, "STT: \"${result.text.take(80)}\"")

        val entry = TranscriptEntry(
            id = java.util.UUID.randomUUID().toString(),
            text = result.text,
            timestamp = System.currentTimeMillis(),
        )
        withContext(Dispatchers.Main) {
            transcriptRepository.addTranscript(entry)
        }
    }

    private fun stopAudioCapture() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop() } catch (_: Exception) {}
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

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcm)
        return buffer.array()
    }

    fun destroy() {
        stopListening()
        scope.cancel()
    }
}
