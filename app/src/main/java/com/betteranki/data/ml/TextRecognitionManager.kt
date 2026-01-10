package com.betteranki.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.betteranki.data.model.RecognizedText
import com.betteranki.data.model.TextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages ML Kit text recognition
 * Handles OCR processing and text extraction
 */
class TextRecognitionManager(private val context: Context) {
    
    private val imagePreprocessor = ImagePreprocessor(context)
    
    // Initialize ML Kit text recognizer with Latin script
    // For Asian languages, use different recognizer options
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Builds the exact input bitmap that would be passed into ML Kit for OCR.
     * Caller owns the returned bitmap and must recycle it when no longer needed.
     */
    suspend fun prepareInputBitmapFromUri(
        uri: Uri,
        captureRotation: Int? = null,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            var bitmap = imagePreprocessor.preprocessImage(uri, captureRotation)
                ?: return@withContext Result.failure(Exception("Failed to load image"))

            if (cropLeft != 0f || cropTop != 0f || cropRight != 1f || cropBottom != 1f) {
                bitmap = imagePreprocessor.maskOutsideCrop(bitmap, cropLeft, cropTop, cropRight, cropBottom)
            }

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs OCR on a bitmap that has already been preprocessed exactly as desired.
     * Caller may recycle the bitmap after this returns.
     */
    suspend fun recognizeTextFromPreparedBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): Result<RecognizedText> =
        withContext(Dispatchers.IO) {
            try {
                val degrees = when (rotationDegrees) {
                    0, 90, 180, 270 -> rotationDegrees
                    else -> 0
                }
                val inputImage = InputImage.fromBitmap(bitmap, degrees)
                recognizeText(inputImage)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Process an image from URI and extract text
     */
    suspend fun recognizeTextFromUri(
        uri: Uri,
        captureRotation: Int? = null,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ): Result<RecognizedText> = withContext(Dispatchers.IO) {
        try {
            // Preprocess the image
            var bitmap = imagePreprocessor.preprocessImage(uri, captureRotation)
                ?: return@withContext Result.failure(Exception("Failed to load image"))
            
            // Crop if needed
            if (cropLeft != 0f || cropTop != 0f || cropRight != 1f || cropBottom != 1f) {
                bitmap = imagePreprocessor.maskOutsideCrop(bitmap, cropLeft, cropTop, cropRight, cropBottom)
            }
            
            // Convert to InputImage
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Perform OCR
            val result = recognizeText(inputImage)
            
            // Clean up bitmap
            bitmap.recycle()
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Process a bitmap directly and extract text
     */
    suspend fun recognizeTextFromBitmap(
        bitmap: Bitmap,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ): Result<RecognizedText> = withContext(Dispatchers.IO) {
        try {
            // Preprocess bitmap
            var processedBitmap = imagePreprocessor.preprocessBitmap(bitmap)
            
            // Crop if needed
            if (cropLeft != 0f || cropTop != 0f || cropRight != 1f || cropBottom != 1f) {
                processedBitmap = imagePreprocessor.maskOutsideCrop(processedBitmap, cropLeft, cropTop, cropRight, cropBottom)
            }
            
            // Convert to InputImage
            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            
            // Perform OCR
            val result = recognizeText(inputImage)
            
            // Clean up if we created a new bitmap
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Core OCR processing using ML Kit
     */
    private suspend fun recognizeText(inputImage: InputImage): Result<RecognizedText> {
        return try {
            val visionText = recognizer.process(inputImage).await()

            fun rectSortKey(rect: Rect?): Pair<Int, Int> {
                if (rect == null) return Int.MAX_VALUE to Int.MAX_VALUE
                return rect.top to rect.left
            }

            // Sort blocks (and later lines/elements) to preserve top-left -> bottom-right reading order.
            val sortedMlBlocks = visionText.textBlocks.sortedWith(
                compareBy<Text.TextBlock> { rectSortKey(it.boundingBox).first }
                    .thenBy { rectSortKey(it.boundingBox).second }
            )

            val blocks = sortedMlBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    boundingBox = block.boundingBox,
                    confidence = 1.0f, // ML Kit Text Recognition doesn't provide confidence
                    language = block.recognizedLanguage
                )
            }

            // Build full text in reading order.
            // NOTE: Avoid re-ordering elements/words (can create gibberish on some layouts).
            // Instead, sort blocks+lines by bounding boxes, then append each line's own text.
            val fullText = buildString {
                sortedMlBlocks.forEach { block ->
                    val sortedLines = block.lines.sortedWith(
                        compareBy<Text.Line> { rectSortKey(it.boundingBox).first }
                            .thenBy { rectSortKey(it.boundingBox).second }
                    )

                    sortedLines.forEach { line ->
                        val lineText = line.text
                        if (lineText.isNotBlank()) {
                            append(lineText.trim())
                            append(' ')
                        }
                    }
                }
            }
                .replace(Regex("\\s+"), " ")
                .trim()
            
            // Detect primary language (from first block if available)
            val primaryLanguage = blocks.firstOrNull()?.language
            
            Result.success(
                RecognizedText(
                    fullText = fullText,
                    blocks = blocks,
                    language = primaryLanguage
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        recognizer.close()
    }
}
