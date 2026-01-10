package com.betteranki.ui.ocr

import android.content.Context
import com.betteranki.data.model.TranslationResult
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationManager(private val context: Context) {
    
    private val modelManager = RemoteModelManager.getInstance()
    
    /**
     * Map language codes to ML Kit language constants
     */
    private fun mapLanguageCode(code: String): String {
        return when (code.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            "zh" -> TranslateLanguage.CHINESE
            "ko" -> TranslateLanguage.KOREAN
            "ja" -> TranslateLanguage.JAPANESE
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            else -> TranslateLanguage.ENGLISH
        }
    }
    
    /**
     * Translate text from source language to target language
     * @param text Text to translate
     * @param targetLanguageCode Target language code (e.g., "en", "es", "fr")
     * @param sourceLanguageCode Source language code (default: auto-detect from OCR)
     * @return Result with translated text or error
     */
    suspend fun translateText(
        text: String,
        targetLanguageCode: String,
        sourceLanguageCode: String = "ja" // Default source
    ): Result<TranslationResult> {
        return try {
            android.util.Log.d("TranslationManager", "translateText called: text='$text', source='$sourceLanguageCode', target='$targetLanguageCode'")
            
            val sourceLang = mapLanguageCode(sourceLanguageCode)
            val targetLang = mapLanguageCode(targetLanguageCode)
            
            android.util.Log.d("TranslationManager", "Mapped languages: source=$sourceLang, target=$targetLang")
            
            // If source and target are the same, just return the original text
            if (sourceLang == targetLang) {
                android.util.Log.d("TranslationManager", "Source and target are same, returning original text")
                return Result.success(
                    TranslationResult(
                        originalText = text,
                        translatedText = text,
                        sourceLang = sourceLanguageCode,
                        targetLang = targetLanguageCode
                    )
                )
            }
            
            android.util.Log.d("TranslationManager", "Translating '$text' from $sourceLang to $targetLang")
            
            // Create translator options
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            
            val translator = Translation.getClient(options)
            
            // Download model if needed (allow cellular data)
            val conditions = DownloadConditions.Builder()
                .build()  // Removed requireWifi() to allow downloads over cellular
            
            android.util.Log.d("TranslationManager", "Checking/downloading models...")
            translator.downloadModelIfNeeded(conditions).await()
            
            // Translate
            android.util.Log.d("TranslationManager", "Translating text...")
            val translatedText = translator.translate(text).await()
            
            android.util.Log.d("TranslationManager", "Translation result: $translatedText")
            
            translator.close()
            
            Result.success(
                TranslationResult(
                    originalText = text,
                    translatedText = translatedText,
                    sourceLang = sourceLanguageCode,
                    targetLang = targetLanguageCode
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "Translation error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if a language model is downloaded
     */
    suspend fun isModelDownloaded(languageCode: String): Boolean {
        return try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val downloadedModels = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            downloadedModels.any { it.language == languageCode }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Download a specific language model
     */
    suspend fun downloadModel(languageCode: String): Result<Unit> {
        return try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            modelManager.download(model, conditions).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a downloaded language model to free up space
     */
    suspend fun deleteModel(languageCode: String): Result<Unit> {
        return try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            modelManager.deleteDownloadedModel(model).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of all downloaded language models
     */
    suspend fun getDownloadedModels(): Set<String> {
        return try {
            val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            models.map { it.language }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
