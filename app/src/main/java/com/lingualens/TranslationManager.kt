package com.lingualens

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationManager {
    private var currentTranslator: Translator? = null
    private var currentLanguageCode: String = TranslateLanguage.SPANISH

    suspend fun downloadModel(
        languageCode: String,
        onProgress: (String) -> Unit
    ): Boolean {
        return try {
            onProgress("Downloading $languageCode model...")

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(languageCode)
                .build()

            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions).await()

            currentTranslator?.close()
            currentTranslator = translator
            currentLanguageCode = languageCode

            onProgress("Model ready!")
            true
        } catch (e: Exception) {
            onProgress("Failed to download model: ${e.message}")
            false
        }
    }

    suspend fun translate(text: String): String {
        return try {
            currentTranslator?.translate(text)?.await() ?: text
        } catch (e: Exception) {
            text
        }
    }

    fun close() {
        currentTranslator?.close()
    }

    companion object {
        fun getLanguageCode(languageName: String): String {
            return when (languageName) {
                "Spanish" -> TranslateLanguage.SPANISH
                "French" -> TranslateLanguage.FRENCH
                "German" -> TranslateLanguage.GERMAN
                "Italian" -> TranslateLanguage.ITALIAN
                "Portuguese" -> TranslateLanguage.PORTUGUESE
                "Polish" -> TranslateLanguage.POLISH
                else -> TranslateLanguage.SPANISH
            }
        }
    }
}