package com.betteranki.data.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.net.Uri
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import android.content.Context
import java.io.InputStream

/**
 * Handles image preprocessing for OCR
 * - Resizes images to optimal size
 * - Corrects rotation based on EXIF data
 * - Enhances image quality for better OCR results
 */
class ImagePreprocessor(private val context: Context) {
    
    companion object {
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val MIN_IMAGE_DIMENSION = 320
    }
    
    /**
     * Preprocess an image from URI
     * Returns a bitmap ready for OCR processing
     */
    suspend fun preprocessImage(
        uri: Uri,
        captureRotation: Int? = null
    ): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // First, get image dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                
                // Calculate sample size for downscaling
                val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
                
                // Load the bitmap with sampling
                val bitmap = context.contentResolver.openInputStream(uri)?.use { newStream ->
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }.let { opts ->
                        BitmapFactory.decodeStream(newStream, null, opts)
                    }
                }
                
                // Correct rotation based on EXIF
                bitmap
                    ?.let { correctRotation(it, uri) }
                    ?.let { maybeRotateToPortraitFromCaptureRotation(it, captureRotation) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * If the user captured while the device was in a landscape rotation, rotate the bitmap back
     * so that the crop/OCR flow sees an upright portrait-oriented image.
     *
     * We only apply this when the decoded bitmap is landscape (width > height) to avoid
     * double-rotating already-portrait images.
     */
    private fun maybeRotateToPortraitFromCaptureRotation(bitmap: Bitmap, captureRotation: Int?): Bitmap {
        if (captureRotation == null) return bitmap
        if (bitmap.width <= bitmap.height) return bitmap

        val degrees = when (captureRotation) {
            Surface.ROTATION_90 -> 270f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 90f
            else -> 0f
        }
        return if (degrees != 0f) rotateBitmap(bitmap, degrees) else bitmap
    }
    
    /**
     * Preprocess a bitmap directly
     */
    fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // Resize if needed
        return if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
            resizeBitmap(bitmap, MAX_IMAGE_DIMENSION)
        } else if (bitmap.width < MIN_IMAGE_DIMENSION && bitmap.height < MIN_IMAGE_DIMENSION) {
            // Don't upscale too small images - OCR might fail
            bitmap
        } else {
            bitmap
        }
    }
    
    /**
     * Calculate sample size for efficient image loading
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            // Calculate the largest inSampleSize that keeps dimensions above max
            while ((halfWidth / inSampleSize) >= MAX_IMAGE_DIMENSION &&
                   (halfHeight / inSampleSize) >= MAX_IMAGE_DIMENSION) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Resize bitmap to fit within max dimension while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    }
    
    /**
     * Correct image rotation based on EXIF data
     */
    private fun correctRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                
                if (rotation != 0f) {
                    rotateBitmap(bitmap, rotation)
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
    
    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        ).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    }
    
    /**
     * Enhance image contrast for better OCR (optional)
     */
    fun enhanceContrast(bitmap: Bitmap, factor: Float = 1.2f): Bitmap {
        // Simple contrast enhancement
        // Could be expanded with more sophisticated algorithms if needed
        return bitmap // Placeholder - ML Kit handles this well enough
    }
    
    /**
     * Crop bitmap to specified region (values between 0.0 and 1.0)
     * @param bitmap Source bitmap
     * @param cropLeft Left boundary as fraction of width (0.0 to 1.0)
     * @param cropTop Top boundary as fraction of height (0.0 to 1.0)
     * @param cropRight Right boundary as fraction of width (0.0 to 1.0)
     * @param cropBottom Bottom boundary as fraction of height (0.0 to 1.0)
     */
    fun cropBitmap(
        bitmap: Bitmap,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Convert fractions to pixel coordinates
        val x = (width * cropLeft).toInt().coerceIn(0, width)
        val y = (height * cropTop).toInt().coerceIn(0, height)
        val cropWidth = (width * (cropRight - cropLeft)).toInt().coerceAtLeast(1)
        val cropHeight = (height * (cropBottom - cropTop)).toInt().coerceAtLeast(1)
        
        return try {
            Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight).also {
                if (it != bitmap) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Masks everything outside the crop region by drawing opaque black rectangles.
     * Keeps the bitmap dimensions unchanged (preserves aspect ratio for OCR).
     */
    fun maskOutsideCrop(
        bitmap: Bitmap,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val leftF = cropLeft.coerceIn(0f, 1f)
        val topF = cropTop.coerceIn(0f, 1f)
        val rightF = cropRight.coerceIn(0f, 1f)
        val bottomF = cropBottom.coerceIn(0f, 1f)

        val leftPx = (width * minOf(leftF, rightF)).toInt().coerceIn(0, width)
        val rightPx = (width * maxOf(leftF, rightF)).toInt().coerceIn(0, width)
        val topPx = (height * minOf(topF, bottomF)).toInt().coerceIn(0, height)
        val bottomPx = (height * maxOf(topF, bottomF)).toInt().coerceIn(0, height)

        // Ensure mutable bitmap
        val out = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        // Top
        if (topPx > 0) canvas.drawRect(0f, 0f, width.toFloat(), topPx.toFloat(), paint)
        // Bottom
        if (bottomPx < height) canvas.drawRect(0f, bottomPx.toFloat(), width.toFloat(), height.toFloat(), paint)
        // Left
        if (leftPx > 0 && bottomPx > topPx) canvas.drawRect(0f, topPx.toFloat(), leftPx.toFloat(), bottomPx.toFloat(), paint)
        // Right
        if (rightPx < width && bottomPx > topPx) canvas.drawRect(rightPx.toFloat(), topPx.toFloat(), width.toFloat(), bottomPx.toFloat(), paint)

        // If we created a copy, recycle the original to keep memory bounded.
        if (out !== bitmap) {
            bitmap.recycle()
        }
        return out
    }
}
