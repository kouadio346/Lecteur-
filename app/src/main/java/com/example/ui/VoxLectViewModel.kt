package com.example.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.example.model.PlaybackState
import com.example.model.ReadingHistoryItem
import com.example.model.VoiceOption
import com.example.model.VoxLectSettings
import com.example.service.VoxLectAccessibilityService
import com.example.service.VoxLectEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoxLectViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = VoxLectEngine.getInstance(application)

    val settings: StateFlow<VoxLectSettings> = engine.settings
    val availableVoices: StateFlow<List<VoiceOption>> = engine.availableVoices
    val isTtsReady: StateFlow<Boolean> = engine.isTtsReady
    val playbackState: StateFlow<PlaybackState> = engine.playbackState
    val isOverlayRunning: StateFlow<Boolean> = engine.isOverlayRunning
    val accessibilityConnected: StateFlow<Boolean> = VoxLectAccessibilityService.isConnected
    val lastOriginalText: StateFlow<String> = engine.lastOriginalText
    val lastTranslatedText: StateFlow<String?> = engine.lastTranslatedText
    val lastSourceLabel: StateFlow<String> = engine.lastSourceLabel
    val history: StateFlow<List<ReadingHistoryItem>> = engine.history

    private val _hasOverlayPermission = MutableStateFlow(
        Settings.canDrawOverlays(application)
    )
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    fun refreshPermissions() {
        _hasOverlayPermission.value = Settings.canDrawOverlays(getApplication())
    }

    fun startOverlay(): Boolean {
        refreshPermissions()
        if (!_hasOverlayPermission.value) return false
        engine.startOverlayService()
        return true
    }

    fun stopOverlay() {
        engine.stopOverlayService()
    }

    fun speakText(text: String, sourceLabel: String = "Studio VoxLect") {
        engine.processAndSpeak(text, sourceLabel)
    }

    fun readClipboard(): Boolean {
        return engine.readClipboard()
    }

    fun togglePlayPause() {
        engine.togglePlayPause()
    }

    fun stopPlayback() {
        engine.stopPlayback()
    }

    fun setSpeechRate(rate: Float) {
        engine.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        engine.setPitch(pitch)
    }

    fun selectVoice(voiceId: String?) {
        engine.selectVoice(voiceId)
    }

    fun previewVoice(voice: VoiceOption) {
        engine.previewVoice(voice)
    }

    fun refreshVoices() {
        engine.ttsManager.refreshVoices()
    }

    fun setTranslationEnabled(enabled: Boolean) {
        engine.setTranslationEnabled(enabled)
    }

    fun setTargetLanguage(code: String) {
        engine.setTargetLanguage(code)
    }

    fun setAutoMatchVoice(enabled: Boolean) {
        engine.setAutoMatchVoice(enabled)
    }

    fun setAutoReadSelection(enabled: Boolean) {
        engine.setAutoReadSelection(enabled)
    }

    fun replayHistoryItem(item: ReadingHistoryItem) {
        engine.replayHistoryItem(item)
    }

    fun deleteHistoryItem(id: String) {
        engine.deleteHistoryItem(id)
    }

    fun clearHistory() {
        engine.clearHistory()
    }
}
