package com.example.model

import android.graphics.Rect

data class VoiceOption(
    val id: String,
    val name: String,
    val displayName: String,
    val languageTag: String,
    val languageCode: String,
    val languageDisplay: String,
    val isNetworkRequired: Boolean,
    val isHighQuality: Boolean
)

data class TranslationLanguage(
    val code: String,
    val bcp47Prefix: String,
    val frenchName: String,
    val nativeName: String,
    val badge: String,
    val sampleGreeting: String
)

object SupportedLanguages {
    val all: List<TranslationLanguage> = listOf(
        TranslationLanguage("fr", "fr", "Français", "Français", "FR", "Bonjour, VoxLect lit votre écran avec fluidité."),
        TranslationLanguage("en", "en", "Anglais", "English", "EN", "Hello, VoxLect reads your screen naturally."),
        TranslationLanguage("es", "es", "Espagnol", "Español", "ES", "Hola, VoxLect lee tu pantalla en voz alta."),
        TranslationLanguage("de", "de", "Allemand", "Deutsch", "DE", "Hallo, VoxLect liest Ihren Bildschirm vor."),
        TranslationLanguage("it", "it", "Italien", "Italiano", "IT", "Ciao, VoxLect legge il tuo schermo."),
        TranslationLanguage("pt", "pt", "Portugais", "Português", "PT", "Olá, o VoxLect lê o seu ecrã."),
        TranslationLanguage("ar", "ar", "Arabe", "العربية", "AR", "مرحباً، يقرأ فوكس ليكت شاشتك."),
        TranslationLanguage("ja", "ja", "Japonais", "日本語", "JA", "こんにちは、VoxLectが画面を読み上げます。"),
        TranslationLanguage("zh", "zh", "Chinois", "中文", "ZH", "你好，VoxLect 正在为您朗读屏幕内容。"),
        TranslationLanguage("ko", "ko", "Coréen", "한국어", "KO", "안녕하세요, VoxLect가 화면을 읽어드립니다.")
    )

    fun byCode(code: String): TranslationLanguage =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: all.first()
}

data class ScreenTextNode(
    val id: String,
    val text: String,
    val boundsInScreen: Rect,
    val className: String
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Translating(val partialText: String) : PlaybackState
    data class Speaking(val currentChunk: Int, val totalChunks: Int) : PlaybackState
    data class Paused(val currentChunk: Int, val totalChunks: Int) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

data class ReadingHistoryItem(
    val id: String,
    val originalText: String,
    val spokenText: String,
    val translatedToLanguage: String?,
    val sourceLabel: String,
    val timestampMs: Long
)

data class VoxLectSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val selectedVoiceId: String? = null,
    val translationEnabled: Boolean = false,
    val targetLanguageCode: String = "fr",
    val autoMatchVoiceToTranslation: Boolean = true,
    val autoReadSelection: Boolean = false
)
