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
    private var isEnglishSelected: Boolean = false

    suspend fun downloadModel(
        languageCode: String,
        onProgress: (String) -> Unit
    ): Boolean {
        return try {
            // If English is selected, no translation needed
            if (languageCode == "en") {
                isEnglishSelected = true
                currentTranslator?.close()
                currentTranslator = null
                onProgress("English selected - no translation needed")
                return true
            }

            isEnglishSelected = false
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
            if (isEnglishSelected) {
                // No translation needed for English
                return text
            }
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
                "English" -> "en"
                "Spanish" -> TranslateLanguage.SPANISH
                "French" -> TranslateLanguage.FRENCH
                "German" -> TranslateLanguage.GERMAN
                "Italian" -> TranslateLanguage.ITALIAN
                "Portuguese" -> TranslateLanguage.PORTUGUESE
                "Polish" -> TranslateLanguage.POLISH
                "Dutch" -> TranslateLanguage.DUTCH
                "Japanese" -> TranslateLanguage.JAPANESE
                "Chinese" -> TranslateLanguage.CHINESE
                "Korean" -> TranslateLanguage.KOREAN
                "Russian" -> TranslateLanguage.RUSSIAN
                "Arabic" -> TranslateLanguage.ARABIC
                else -> TranslateLanguage.SPANISH
            }
        }
    }
}