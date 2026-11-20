package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.VoxLectSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.voxLectDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxlect_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
        val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")
        val AUTO_MATCH_VOICE = booleanPreferencesKey("auto_match_voice")
        val AUTO_READ_SELECTION = booleanPreferencesKey("auto_read_selection")
    }

    val settingsFlow: Flow<VoxLectSettings> = context.voxLectDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            VoxLectSettings(
                speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.5f, 2.5f),
                pitch = (prefs[Keys.PITCH] ?: 1.0f).coerceIn(0.7f, 1.4f),
                selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID],
                translationEnabled = prefs[Keys.TRANSLATION_ENABLED] ?: false,
                targetLanguageCode = prefs[Keys.TARGET_LANGUAGE_CODE] ?: "fr",
                autoMatchVoiceToTranslation = prefs[Keys.AUTO_MATCH_VOICE] ?: true,
                autoReadSelection = prefs[Keys.AUTO_READ_SELECTION] ?: false
            )
        }

    suspend fun setSpeechRate(rate: Float) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.5f)
        }
    }

    suspend fun setPitch(pitch: Float) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.PITCH] = pitch.coerceIn(0.7f, 1.4f)
        }
    }

    suspend fun setSelectedVoiceId(voiceId: String?) {
        context.voxLectDataStore.edit { prefs ->
            if (voiceId == null) {
                prefs.remove(Keys.SELECTED_VOICE_ID)
            } else {
                prefs[Keys.SELECTED_VOICE_ID] = voiceId
            }
        }
    }

    suspend fun setTranslationEnabled(enabled: Boolean) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.TRANSLATION_ENABLED] = enabled
        }
    }

    suspend fun setTargetLanguageCode(code: String) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.TARGET_LANGUAGE_CODE] = code
        }
    }

    suspend fun setAutoMatchVoice(enabled: Boolean) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.AUTO_MATCH_VOICE] = enabled
        }
    }

    suspend fun setAutoReadSelection(enabled: Boolean) {
        context.voxLectDataStore.edit { prefs ->
            prefs[Keys.AUTO_READ_SELECTION] = enabled
        }
    }
}
