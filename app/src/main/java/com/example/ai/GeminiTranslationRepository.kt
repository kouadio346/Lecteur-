package com.example.ai

import android.util.Log
import com.example.model.TranslationLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiTranslationRepo"

sealed interface TranslationStreamEvent {
    data class Chunk(val accumulatedText: String) : TranslationStreamEvent
    data class Completed(val finalText: String) : TranslationStreamEvent
}

class GeminiTranslationRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
            },
            systemInstruction = content {
                text(
                    "You are a fast, accurate real-time translator for a screen-reader app. " +
                        "Translate the user's text faithfully into the requested target language. " +
                        "Output ONLY the translated text suitable for direct text-to-speech playback. " +
                        "Do not add quotes, markdown formatting, explanations, notes, or preambles. " +
                        "If the text is already in the target language, return it naturally refined or unchanged."
                )
            }
        )
    }

    fun translateStream(
        sourceText: String,
        targetLanguage: TranslationLanguage
    ): Flow<TranslationStreamEvent> = flow {
        val cleaned = sourceText.trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Le texte à traduire est vide.")
        }

        val prompt = buildString {
            append("Translate the following text into ")
            append(targetLanguage.nativeName)
            append(" (")
            append(targetLanguage.frenchName)
            append(", code ")
            append(targetLanguage.code)
            append("):\n\n")
            append(cleaned)
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(45_000L) {
                model.generateContentStream(prompt).collect { response ->
                    val part = response.text.orEmpty()
                    if (part.isNotEmpty()) {
                        accumulated.append(part)
                        emit(TranslationStreamEvent.Chunk(accumulated.toString().trim()))
                    }
                }
            }
            val result = accumulated.toString().trim()
            if (result.isEmpty()) {
                throw IllegalStateException("La traduction n'a retourné aucun texte.")
            }
            emit(TranslationStreamEvent.Completed(result))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Translation timed out", e)
            throw IllegalStateException("La traduction a expiré après 45 secondes. Réessayez.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini translation failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Échec de la traduction en temps réel.",
                e
            )
        }
    }
}
