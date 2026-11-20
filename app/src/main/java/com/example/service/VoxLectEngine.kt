package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.ai.GeminiTranslationRepository
import com.example.ai.TranslationStreamEvent
import com.example.data.SettingsRepository
import com.example.model.PlaybackState
import com.example.model.ReadingHistoryItem
import com.example.model.ScreenTextNode
import com.example.model.SupportedLanguages
import com.example.model.VoiceOption
import com.example.model.VoxLectSettings
import com.example.tts.VoxLectTtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "VoxLectEngine"

class VoxLectEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val settingsRepository = SettingsRepository(appContext)
    private val translationRepository = GeminiTranslationRepository()
    val ttsManager = VoxLectTtsManager(appContext)

    val settings: StateFlow<VoxLectSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VoxLectSettings()
        )

    val availableVoices: StateFlow<List<VoiceOption>> = ttsManager.availableVoices
    val isTtsReady: StateFlow<Boolean> = ttsManager.isReady
    val playbackState: StateFlow<PlaybackState> = ttsManager.playbackState

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _isOverlayExpanded = MutableStateFlow(false)
    val isOverlayExpanded: StateFlow<Boolean> = _isOverlayExpanded.asStateFlow()

    private val _isScreenPickerActive = MutableStateFlow(false)
    val isScreenPickerActive: StateFlow<Boolean> = _isScreenPickerActive.asStateFlow()

    private val _capturedScreenNodes = MutableStateFlow<List<ScreenTextNode>>(emptyList())
    val capturedScreenNodes: StateFlow<List<ScreenTextNode>> = _capturedScreenNodes.asStateFlow()

    private val _lastOriginalText = MutableStateFlow("")
    val lastOriginalText: StateFlow<String> = _lastOriginalText.asStateFlow()

    private val _lastTranslatedText = MutableStateFlow<String?>(null)
    val lastTranslatedText: StateFlow<String?> = _lastTranslatedText.asStateFlow()

    private val _lastSourceLabel = MutableStateFlow("Prêt")
    val lastSourceLabel: StateFlow<String> = _lastSourceLabel.asStateFlow()

    private val _history = MutableStateFlow<List<ReadingHistoryItem>>(emptyList())
    val history: StateFlow<List<ReadingHistoryItem>> = _history.asStateFlow()

    private var activeProcessingJob: Job? = null

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
        if (!running) {
            _isOverlayExpanded.value = false
            _isScreenPickerActive.value = false
        }
    }

    fun setOverlayExpanded(expanded: Boolean) {
        _isOverlayExpanded.value = expanded
    }

    fun openScreenSelector() {
        val accessibilityService = VoxLectAccessibilityService.getActiveInstance()
        val nodes = accessibilityService?.captureVisibleTextNodes().orEmpty()
        _capturedScreenNodes.value = nodes
        _isOverlayExpanded.value = false
        _isScreenPickerActive.value = true
    }

    fun closeScreenSelector() {
        _isScreenPickerActive.value = false
        _capturedScreenNodes.value = emptyList()
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }

    fun startOverlayService() {
        if (!hasOverlayPermission()) return
        val intent = Intent(appContext, VoxLectOverlayService::class.java).apply {
            action = VoxLectOverlayService.ACTION_SHOW
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopOverlayService() {
        val intent = Intent(appContext, VoxLectOverlayService::class.java).apply {
            action = VoxLectOverlayService.ACTION_STOP
        }
        appContext.startService(intent)
        _isOverlayRunning.value = false
        _isOverlayExpanded.value = false
        _isScreenPickerActive.value = false
    }

    fun readClipboard(sourceLabel: String = "Presse-papiers"): Boolean {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip: ClipData? = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim()
        } else {
            null
        }
        if (text.isNullOrEmpty()) {
            ttsManager.setErrorState("Le presse-papiers ne contient aucun texte lisible.")
            return false
        }
        processAndSpeak(text, sourceLabel)
        return true
    }

    fun processAndSpeak(text: String, sourceLabel: String = "Sélection") {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        activeProcessingJob?.cancel()
        ttsManager.stop()

        _lastOriginalText.value = cleaned
        _lastTranslatedText.value = null
        _lastSourceLabel.value = sourceLabel

        val currentSettings = settings.value
        if (!currentSettings.translationEnabled) {
            val voiceId = resolveEffectiveVoiceId(currentSettings, languageHint = null)
            addHistoryItem(
                original = cleaned,
                spoken = cleaned,
                translatedLang = null,
                sourceLabel = sourceLabel
            )
            ttsManager.speak(
                text = cleaned,
                rate = currentSettings.speechRate,
                pitch = currentSettings.pitch,
                voiceId = voiceId,
                languageHint = null
            )
            return
        }

        val targetLang = SupportedLanguages.byCode(currentSettings.targetLanguageCode)
        activeProcessingJob = scope.launch {
            ttsManager.setTranslatingState("")
            try {
                var finalTranslation = ""
                translationRepository.translateStream(cleaned, targetLang).collect { event ->
                    when (event) {
                        is TranslationStreamEvent.Chunk -> {
                            _lastTranslatedText.value = event.accumulatedText
                            ttsManager.setTranslatingState(event.accumulatedText)
                        }
                        is TranslationStreamEvent.Completed -> {
                            finalTranslation = event.finalText
                            _lastTranslatedText.value = finalTranslation
                        }
                    }
                }
                if (finalTranslation.isNotBlank()) {
                    val latestSettings = settings.value
                    val voiceId = resolveEffectiveVoiceId(
                        settings = latestSettings,
                        languageHint = targetLang.code
                    )
                    addHistoryItem(
                        original = cleaned,
                        spoken = finalTranslation,
                        translatedLang = targetLang.frenchName,
                        sourceLabel = sourceLabel
                    )
                    ttsManager.speak(
                        text = finalTranslation,
                        rate = latestSettings.speechRate,
                        pitch = latestSettings.pitch,
                        voiceId = voiceId,
                        languageHint = targetLang.code
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to translate and speak", e)
                ttsManager.setErrorState(
                    e.localizedMessage ?: "Impossible de traduire le texte sélectionné."
                )
            }
        }
    }

    private fun resolveEffectiveVoiceId(
        settings: VoxLectSettings,
        languageHint: String?
    ): String? {
        val chosenId = settings.selectedVoiceId
        if (languageHint == null) return chosenId

        val chosenVoice = chosenId?.let { id ->
            availableVoices.value.firstOrNull { it.id == id }
        }
        if (chosenVoice != null && chosenVoice.languageCode == languageHint.lowercase()) {
            return chosenVoice.id
        }
        if (settings.autoMatchVoiceToTranslation) {
            return ttsManager.findBestVoiceForLanguage(languageHint)?.id ?: chosenId
        }
        return chosenId
    }

    fun togglePlayPause() {
        when (playbackState.value) {
            is PlaybackState.Speaking -> ttsManager.pause()
            is PlaybackState.Paused -> ttsManager.resume()
            is PlaybackState.Idle, is PlaybackState.Error -> {
                val toRead = _lastTranslatedText.value ?: _lastOriginalText.value
                if (toRead.isNotBlank()) {
                    val current = settings.value
                    val hint = if (_lastTranslatedText.value != null && current.translationEnabled) {
                        current.targetLanguageCode
                    } else {
                        null
                    }
                    ttsManager.speak(
                        text = toRead,
                        rate = current.speechRate,
                        pitch = current.pitch,
                        voiceId = resolveEffectiveVoiceId(current, hint),
                        languageHint = hint
                    )
                }
            }
            is PlaybackState.Translating -> Unit
        }
    }

    fun stopPlayback() {
        activeProcessingJob?.cancel()
        ttsManager.stop()
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        scope.launch {
            settingsRepository.setSpeechRate(clamped)
            val s = settings.value
            val hint = if (s.translationEnabled) s.targetLanguageCode else null
            ttsManager.applyLiveSettings(
                rate = clamped,
                pitch = s.pitch,
                voiceId = resolveEffectiveVoiceId(s, hint),
                languageHint = hint
            )
        }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.7f, 1.4f)
        scope.launch {
            settingsRepository.setPitch(clamped)
            val s = settings.value
            val hint = if (s.translationEnabled) s.targetLanguageCode else null
            ttsManager.applyLiveSettings(
                rate = s.speechRate,
                pitch = clamped,
                voiceId = resolveEffectiveVoiceId(s, hint),
                languageHint = hint
            )
        }
    }

    fun selectVoice(voiceId: String?) {
        scope.launch {
            settingsRepository.setSelectedVoiceId(voiceId)
            val s = settings.value
            ttsManager.applyLiveSettings(
                rate = s.speechRate,
                pitch = s.pitch,
                voiceId = voiceId,
                languageHint = null
            )
        }
    }

    fun previewVoice(voice: VoiceOption) {
        val s = settings.value
        ttsManager.previewVoice(voice, s.speechRate, s.pitch)
    }

    fun setTranslationEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setTranslationEnabled(enabled)
        }
    }

    fun setTargetLanguage(code: String) {
        scope.launch {
            settingsRepository.setTargetLanguageCode(code)
        }
    }

    fun setAutoMatchVoice(enabled: Boolean) {
        scope.launch {
            settingsRepository.setAutoMatchVoice(enabled)
        }
    }

    fun setAutoReadSelection(enabled: Boolean) {
        scope.launch {
            settingsRepository.setAutoReadSelection(enabled)
        }
    }

    fun replayHistoryItem(item: ReadingHistoryItem) {
        activeProcessingJob?.cancel()
        _lastOriginalText.value = item.originalText
        _lastTranslatedText.value = if (item.translatedToLanguage != null) item.spokenText else null
        _lastSourceLabel.value = item.sourceLabel
        val s = settings.value
        val hint = if (item.translatedToLanguage != null) {
            SupportedLanguages.all.firstOrNull { it.frenchName == item.translatedToLanguage }?.code
        } else {
            null
        }
        ttsManager.speak(
            text = item.spokenText,
            rate = s.speechRate,
            pitch = s.pitch,
            voiceId = resolveEffectiveVoiceId(s, hint),
            languageHint = hint
        )
    }

    fun deleteHistoryItem(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    private fun addHistoryItem(
        original: String,
        spoken: String,
        translatedLang: String?,
        sourceLabel: String
    ) {
        val item = ReadingHistoryItem(
            id = UUID.randomUUID().toString(),
            originalText = original,
            spokenText = spoken,
            translatedToLanguage = translatedLang,
            sourceLabel = sourceLabel,
            timestampMs = System.currentTimeMillis()
        )
        _history.value = (listOf(item) + _history.value).take(30)
    }

    companion object {
        @Volatile
        private var instance: VoxLectEngine? = null

        fun getInstance(context: Context): VoxLectEngine {
            return instance ?: synchronized(this) {
                instance ?: VoxLectEngine(context).also { instance = it }
            }
        }
    }
}
