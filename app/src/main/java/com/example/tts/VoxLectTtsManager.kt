package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.PlaybackState
import com.example.model.SupportedLanguages
import com.example.model.VoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "VoxLectTtsManager"

class VoxLectTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var rawVoices: Map<String, Voice> = emptyMap()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentChunks: List<String> = emptyList()
    private var currentChunkIndex: Int = 0
    private var activeSessionToken: String = ""
    private var activeRate: Float = 1.0f
    private var activePitch: Float = 1.0f
    private var activeVoiceId: String? = null
    private var activeLanguageHint: String? = null
    private var isPreviewing: Boolean = false

    private var pendingSpeakRequest: PendingSpeak? = null

    private data class PendingSpeak(
        val text: String,
        val rate: Float,
        val pitch: Float,
        val voiceId: String?,
        val languageHint: String?
    )

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val defaultLocale = Locale.getDefault()
                    val langResult = engine.setLanguage(defaultLocale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.FRENCH)
                    }
                    setupListener(engine)
                    loadVoices(engine)
                    _isReady.value = true

                    pendingSpeakRequest?.let { pending ->
                        pendingSpeakRequest = null
                        speak(
                            text = pending.text,
                            rate = pending.rate,
                            pitch = pending.pitch,
                            voiceId = pending.voiceId,
                            languageHint = pending.languageHint
                        )
                    }
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed with status=$status")
                _isReady.value = false
                _playbackState.value = PlaybackState.Error(
                    "Moteur de synthèse vocale indisponible sur cet appareil."
                )
            }
        }
    }

    fun refreshVoices() {
        tts?.let { loadVoices(it) }
    }

    private fun loadVoices(engine: TextToSpeech) {
        val voiceSet = runCatching { engine.voices }.getOrNull() ?: emptySet()
        val voiceMap = mutableMapOf<String, Voice>()
        val perLocaleCounter = mutableMapOf<String, Int>()

        val sortedRaw = voiceSet.sortedWith(
            compareBy<Voice>(
                { it.locale.language != Locale.getDefault().language },
                { it.locale.displayLanguage },
                { it.isNetworkConnectionRequired },
                { -it.quality },
                { it.name }
            )
        )

        val options = sortedRaw.map { voice ->
            voiceMap[voice.name] = voice
            val tag = voice.locale.toLanguageTag()
            val langCode = voice.locale.language.lowercase(Locale.ROOT)
            val index = (perLocaleCounter[tag] ?: 0) + 1
            perLocaleCounter[tag] = index

            val langDisplay = voice.locale.getDisplayName(Locale.FRENCH)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
            val qualityTag = if (voice.quality >= Voice.QUALITY_HIGH) "HD" else "Std"
            val modeTag = if (voice.isNetworkConnectionRequired) "Réseau" else "Hors ligne"
            val friendlyName = "$langDisplay · Voix $index ($qualityTag · $modeTag)"

            VoiceOption(
                id = voice.name,
                name = voice.name,
                displayName = friendlyName,
                languageTag = tag,
                languageCode = langCode,
                languageDisplay = langDisplay,
                isNetworkRequired = voice.isNetworkConnectionRequired,
                isHighQuality = voice.quality >= Voice.QUALITY_HIGH
            )
        }

        rawVoices = voiceMap
        _availableVoices.value = options
    }

    private fun setupListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != activeSessionToken) return
                currentChunkIndex = parsed.second
                if (!isPreviewing) {
                    _playbackState.value = PlaybackState.Speaking(
                        currentChunk = parsed.second + 1,
                        totalChunks = currentChunks.size.coerceAtLeast(1)
                    )
                }
            }

            override fun onDone(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != activeSessionToken) return
                if (isPreviewing || parsed.second >= currentChunks.lastIndex) {
                    isPreviewing = false
                    _playbackState.value = PlaybackState.Idle
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != activeSessionToken) return
                isPreviewing = false
                _playbackState.value = PlaybackState.Error("Erreur lors de la lecture audio.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != activeSessionToken) return
                isPreviewing = false
                _playbackState.value = PlaybackState.Error(
                    "Erreur de synthèse vocale (code $errorCode)."
                )
            }
        })
    }

    fun speak(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        languageHint: String? = null
    ) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        val engine = tts
        if (engine == null || !_isReady.value) {
            pendingSpeakRequest = PendingSpeak(cleaned, rate, pitch, voiceId, languageHint)
            return
        }

        isPreviewing = false
        activeRate = rate.coerceIn(0.5f, 2.5f)
        activePitch = pitch.coerceIn(0.7f, 1.4f)
        activeVoiceId = voiceId
        activeLanguageHint = languageHint

        currentChunks = splitIntoChunks(cleaned)
        currentChunkIndex = 0
        speakFromIndex(engine, 0)
    }

    fun previewVoice(voiceOption: VoiceOption, rate: Float, pitch: Float) {
        val engine = tts
        if (engine == null || !_isReady.value) return

        val sample = SupportedLanguages.all
            .firstOrNull { it.code == voiceOption.languageCode }
            ?.sampleGreeting
            ?: "Bonjour, ceci est un aperçu de cette voix sur VoxLect."

        isPreviewing = true
        activeSessionToken = UUID.randomUUID().toString()
        activeRate = rate.coerceIn(0.5f, 2.5f)
        activePitch = pitch.coerceIn(0.7f, 1.4f)
        currentChunks = listOf(sample)
        currentChunkIndex = 0

        applyVoiceAndParams(engine, activeRate, activePitch, voiceOption.id, voiceOption.languageCode)
        engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "${activeSessionToken}:0")
    }

    fun pause() {
        val state = _playbackState.value
        if (state is PlaybackState.Speaking) {
            activeSessionToken = UUID.randomUUID().toString()
            tts?.stop()
            _playbackState.value = PlaybackState.Paused(
                currentChunk = (currentChunkIndex + 1).coerceAtMost(currentChunks.size.coerceAtLeast(1)),
                totalChunks = currentChunks.size.coerceAtLeast(1)
            )
        }
    }

    fun resume() {
        val engine = tts ?: return
        if (!_isReady.value) return
        if (_playbackState.value is PlaybackState.Paused && currentChunks.isNotEmpty()) {
            speakFromIndex(engine, currentChunkIndex.coerceIn(0, currentChunks.lastIndex))
        }
    }

    fun stop() {
        activeSessionToken = UUID.randomUUID().toString()
        isPreviewing = false
        tts?.stop()
        currentChunks = emptyList()
        currentChunkIndex = 0
        _playbackState.value = PlaybackState.Idle
    }

    fun applyLiveSettings(rate: Float, pitch: Float, voiceId: String?, languageHint: String? = activeLanguageHint) {
        activeRate = rate.coerceIn(0.5f, 2.5f)
        activePitch = pitch.coerceIn(0.7f, 1.4f)
        activeVoiceId = voiceId
        activeLanguageHint = languageHint

        val engine = tts ?: return
        if (!_isReady.value) return
        if (_playbackState.value is PlaybackState.Speaking && currentChunks.isNotEmpty() && !isPreviewing) {
            speakFromIndex(engine, currentChunkIndex.coerceIn(0, currentChunks.lastIndex))
        }
    }

    fun setTranslatingState(partialText: String) {
        activeSessionToken = UUID.randomUUID().toString()
        tts?.stop()
        _playbackState.value = PlaybackState.Translating(partialText)
    }

    fun setErrorState(message: String) {
        activeSessionToken = UUID.randomUUID().toString()
        tts?.stop()
        _playbackState.value = PlaybackState.Error(message)
    }

    fun findBestVoiceForLanguage(languageCode: String): VoiceOption? {
        val normalized = languageCode.lowercase(Locale.ROOT)
        return _availableVoices.value.firstOrNull {
            it.languageCode == normalized && !it.isNetworkRequired && it.isHighQuality
        } ?: _availableVoices.value.firstOrNull {
            it.languageCode == normalized && !it.isNetworkRequired
        } ?: _availableVoices.value.firstOrNull {
            it.languageCode == normalized
        }
    }

    private fun speakFromIndex(engine: TextToSpeech, startIndex: Int) {
        if (currentChunks.isEmpty()) {
            _playbackState.value = PlaybackState.Idle
            return
        }
        val safeStart = startIndex.coerceIn(0, currentChunks.lastIndex)
        activeSessionToken = UUID.randomUUID().toString()
        applyVoiceAndParams(engine, activeRate, activePitch, activeVoiceId, activeLanguageHint)

        _playbackState.value = PlaybackState.Speaking(
            currentChunk = safeStart + 1,
            totalChunks = currentChunks.size
        )

        for (i in safeStart until currentChunks.size) {
            val mode = if (i == safeStart) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "${activeSessionToken}:$i"
            engine.speak(currentChunks[i], mode, null, utteranceId)
        }
    }

    private fun applyVoiceAndParams(
        engine: TextToSpeech,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        languageHint: String?
    ) {
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)

        val selectedRaw = voiceId?.let { rawVoices[it] }
        if (selectedRaw != null) {
            engine.voice = selectedRaw
            return
        }

        if (!languageHint.isNullOrBlank()) {
            val matched = findBestVoiceForLanguage(languageHint)?.let { rawVoices[it.id] }
            if (matched != null) {
                engine.voice = matched
            } else {
                engine.language = Locale.forLanguageTag(languageHint)
            }
        }
    }

    private fun parseUtteranceId(utteranceId: String?): Pair<String, Int>? {
        if (utteranceId.isNullOrBlank()) return null
        val idx = utteranceId.lastIndexOf(':')
        if (idx <= 0) return null
        val token = utteranceId.substring(0, idx)
        val chunkIdx = utteranceId.substring(idx + 1).toIntOrNull() ?: return null
        return token to chunkIdx
    }

    private fun splitIntoChunks(text: String): List<String> {
        val maxLen = 350
        val sentences = text.split(Regex("(?<=[.!?\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) return listOf(text)

        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > maxLen) {
                if (buffer.isNotEmpty()) {
                    chunks.add(buffer.toString().trim())
                    buffer.clear()
                }
                var start = 0
                while (start < sentence.length) {
                    val end = (start + maxLen).coerceAtMost(sentence.length)
                    chunks.add(sentence.substring(start, end).trim())
                    start = end
                }
            } else if (buffer.length + sentence.length + 1 > maxLen) {
                chunks.add(buffer.toString().trim())
                buffer.clear()
                buffer.append(sentence)
            } else {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
        }
        if (buffer.isNotEmpty()) {
            chunks.add(buffer.toString().trim())
        }
        return chunks
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
