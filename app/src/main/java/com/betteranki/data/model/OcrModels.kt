package com.betteranki.data.model

import android.graphics.Rect

/**
 * Represents the full result of OCR text recognition
 */
data class RecognizedText(
    val fullText: String,
    val blocks: List<TextBlock>,
    val language: String? = null
)

/**
 * Represents a block of recognized text with metadata
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val language: String?
)

/**
 * Represents a translation result
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val confidence: Float? = null
)

/**
 * Represents context extracted around selected text
 */
data class TextContext(
    val selectedText: String,
    val contextSentence: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

/**
 * State for OCR processing
 */
sealed class OcrState {
    object Idle : OcrState()
    object Processing : OcrState()
    data class Success(val recognizedText: RecognizedText) : OcrState()
    data class Error(val message: String) : OcrState()
}

/**
 * State for translation
 */
sealed class TranslationState {
    object Idle : TranslationState()
    object Downloading : TranslationState()
    object Translating : TranslationState()
    data class Success(val result: TranslationResult) : TranslationState()
    data class Error(val message: String) : TranslationState()
}
