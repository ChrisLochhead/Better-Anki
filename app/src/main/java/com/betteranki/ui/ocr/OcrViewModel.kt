package com.betteranki.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.ml.TextRecognitionManager
import com.betteranki.data.model.OcrState
import com.betteranki.data.model.RecognizedText
import com.betteranki.data.model.TranslationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for OCR processing
 * Manages the state of image capture, selection, and text recognition
 */
class OcrViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        // Temporary holder for OCR result to share between screens
        var sharedRecognizedText: RecognizedText? = null
    }
    
    private val textRecognitionManager = TextRecognitionManager(context)
    private val translationManager = TranslationManager(context)
    
    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()
    
    private val _recognizedText = MutableStateFlow<RecognizedText?>(null)
    val recognizedText: StateFlow<RecognizedText?> = _recognizedText.asStateFlow()
    
    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()
    
    /**
     * Process an image from URI (from gallery or camera)
     */
    fun processImageFromUri(
        uri: Uri,
        captureRotation: Int = Surface.ROTATION_0,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ) {
        viewModelScope.launch {
            _ocrState.value = OcrState.Processing
            
            val result = textRecognitionManager.recognizeTextFromUri(
                uri,
                captureRotation,
                cropLeft,
                cropTop,
                cropRight,
                cropBottom
            )
            
            result.fold(
                onSuccess = { recognizedText ->
                    if (recognizedText.fullText.isBlank()) {
                        _ocrState.value = OcrState.Error("No text found in image")
                        _recognizedText.value = null
                        sharedRecognizedText = null
                    } else {
                        _recognizedText.value = recognizedText
                        sharedRecognizedText = recognizedText
                        _ocrState.value = OcrState.Success(recognizedText)
                    }
                },
                onFailure = { error ->
                    _ocrState.value = OcrState.Error(
                        error.message ?: "Failed to process image"
                    )
                    _recognizedText.value = null
                    sharedRecognizedText = null
                }
            )
        }
    }

    fun prepareInputPreviewFromUri(
        uri: Uri,
        captureRotation: Int = Surface.ROTATION_0,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ) {
        viewModelScope.launch {
            _previewState.value = PreviewState.Loading
            // Clear any previous preview reference. Avoid recycling here because Compose
            // may still be rendering it on the UI thread.
            _previewBitmap.value = null

            val result = textRecognitionManager.prepareInputBitmapFromUri(
                uri = uri,
                captureRotation = captureRotation,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom
            )

            result.fold(
                onSuccess = { bmp ->
                    _previewBitmap.value = bmp
                    _previewState.value = PreviewState.Ready
                },
                onFailure = { err ->
                    _previewBitmap.value = null
                    _previewState.value = PreviewState.Error(err.message ?: "Failed to prepare preview")
                }
            )
        }
    }

    /**
     * Runs OCR using the prepared preview bitmap to guarantee the model sees what the user saw.
     */
    fun processPreparedPreviewBitmap(rotationDegrees: Int = 0) {
        val bitmap = _previewBitmap.value ?: run {
            _ocrState.value = OcrState.Error("No preview bitmap available")
            return
        }

        viewModelScope.launch {
            _ocrState.value = OcrState.Processing

            val result = textRecognitionManager.recognizeTextFromPreparedBitmap(
                bitmap = bitmap,
                rotationDegrees = rotationDegrees
            )

            // Don't recycle the bitmap here; navigation/teardown will clear it.
            _previewState.value = PreviewState.Idle

            result.fold(
                onSuccess = { recognizedText ->
                    if (recognizedText.fullText.isBlank()) {
                        _ocrState.value = OcrState.Error("No text found in image")
                        _recognizedText.value = null
                        sharedRecognizedText = null
                    } else {
                        _recognizedText.value = recognizedText
                        sharedRecognizedText = recognizedText
                        _ocrState.value = OcrState.Success(recognizedText)
                    }
                },
                onFailure = { error ->
                    _ocrState.value = OcrState.Error(error.message ?: "Failed to process image")
                    _recognizedText.value = null
                    sharedRecognizedText = null
                }
            )
        }
    }
    
    /**
     * Process a bitmap directly (from camera capture)
     */
    fun processImageFromBitmap(
        bitmap: Bitmap,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ) {
        viewModelScope.launch {
            _ocrState.value = OcrState.Processing
            
            val result = textRecognitionManager.recognizeTextFromBitmap(
                bitmap,
                cropLeft,
                cropTop,
                cropRight,
                cropBottom
            )
            
            result.fold(
                onSuccess = { recognizedText ->
                    if (recognizedText.fullText.isBlank()) {
                        _ocrState.value = OcrState.Error("No text found in image")
                        _recognizedText.value = null
                        sharedRecognizedText = null
                    } else {
                        _recognizedText.value = recognizedText
                        sharedRecognizedText = recognizedText
                        _ocrState.value = OcrState.Success(recognizedText)
                    }
                },
                onFailure = { error ->
                    _ocrState.value = OcrState.Error(
                        error.message ?: "Failed to process image"
                    )
                    _recognizedText.value = null
                    sharedRecognizedText = null
                }
            )
        }
    }
    
    /**
     * Reset state to idle
     */
    fun resetState() {
        _ocrState.value = OcrState.Idle
        _recognizedText.value = null
    }
    
    /**
     * Translate text to target language
     */
    fun translateText(text: String, targetLanguage: String, sourceLanguage: String = "ja") {
        android.util.Log.d("OcrViewModel", "translateText called: text='$text', target='$targetLanguage', source='$sourceLanguage'")
        viewModelScope.launch {
            _translationState.value = TranslationState.Translating
            
            val result = translationManager.translateText(text, targetLanguage, sourceLanguage)
            
            _translationState.value = result.fold(
                onSuccess = { translationResult ->
                    android.util.Log.d("OcrViewModel", "Translation success: '${translationResult.translatedText}'")
                    TranslationState.Success(translationResult)
                },
                onFailure = { error ->
                    TranslationState.Error(error.message ?: "Translation failed")
                }
            )
        }
    }
    
    /**
     * Reset translation state
     */
    fun resetTranslationState() {
        _translationState.value = TranslationState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        _previewBitmap.value?.recycle()
        textRecognitionManager.close()
    }
}
