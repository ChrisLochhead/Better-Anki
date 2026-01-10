package com.betteranki.ui.ocr

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.ml.ImagePreprocessor
import com.betteranki.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CropParams(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrCropScreen(
    imageUri: Uri,
    captureRotation: Int = android.view.Surface.ROTATION_0,
    onBack: () -> Unit,
    onCropConfirmed: (Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val preprocessor = remember { ImagePreprocessor(context) }

    var displayBitmap by remember(imageUri, captureRotation) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val currentDisplayBitmap by rememberUpdatedState(displayBitmap)

    // Crop bounds state (as fractions 0.0 to 1.0)
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.2f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.8f) }

    var imageWidthPx by remember(imageUri, captureRotation) { mutableIntStateOf(0) }
    var imageHeightPx by remember(imageUri, captureRotation) { mutableIntStateOf(0) }

    LaunchedEffect(imageUri, captureRotation) {
        val bmp = withContext(Dispatchers.IO) {
            preprocessor.preprocessImage(imageUri, captureRotation)
        }
        val old = displayBitmap
        displayBitmap = bmp
        if (old != null && old !== bmp) old.recycle()
        if (bmp != null) {
            imageWidthPx = bmp.width
            imageHeightPx = bmp.height
        }
    }

    DisposableEffect(imageUri, captureRotation) {
        onDispose {
            currentDisplayBitmap?.recycle()
        }
    }

    LaunchedEffect(imageUri) {
        val (w, h) = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, opts)
                    opts.outWidth to opts.outHeight
                } ?: (0 to 0)
            } catch (_: Exception) {
                0 to 0
            }
        }
        // Fallback only; prefer Coil's onSuccess dimensions (handles EXIF rotation)
        if (imageWidthPx <= 0 || imageHeightPx <= 0) {
            imageWidthPx = w
            imageHeightPx = h
        }
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.DarkSurface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = AppColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "CROP TEXT AREA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = AppColors.TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(AppColors.Divider)
                    )
                }
            }
        },
        containerColor = AppColors.DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Image with crop overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Display the exact bitmap we will later pass into the OCR pipeline (orientation-normalized).
                val bmp = displayBitmap
                if (bmp == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    }
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Crop overlay
                CropOverlay(
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    imageWidthPx = imageWidthPx,
                    imageHeightPx = imageHeightPx,
                    onCropChange = { left, top, right, bottom ->
                        cropLeft = left
                        cropTop = top
                        cropRight = right
                        cropBottom = bottom
                    }
                )
            }
            
            // Confirm button at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
            ) {
                Button(
                    onClick = {
                        val left = cropLeft.coerceIn(0f, 1f)
                        val right = cropRight.coerceIn(0f, 1f)
                        val top = cropTop.coerceIn(0f, 1f)
                        val bottom = cropBottom.coerceIn(0f, 1f)

                        val safeLeft = minOf(left, right)
                        val safeRight = maxOf(left, right)
                        val safeTop = minOf(top, bottom)
                        val safeBottom = maxOf(top, bottom)

                        onCropConfirmed(safeLeft, safeTop, safeRight, safeBottom)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.DarkSurfaceVariant,
                        contentColor = AppColors.TextPrimary
                    ),
                    border = BorderStroke(1.dp, AppColors.Border),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "PROCESS TEXT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            // Instructions at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    color = AppColors.DarkSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Drag corners to resize. Drag inside to move.",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CropOverlay(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    imageWidthPx: Int,
    imageHeightPx: Int,
    onCropChange: (Float, Float, Float, Float) -> Unit
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val currentCrop by rememberUpdatedState(CropParams(cropLeft, cropTop, cropRight, cropBottom))
    val minCropSize = 0.1f
    // Allow crop to extend beyond the image bounds so it can reach into letterboxed areas.
    // (We clamp back to valid [0..1] image bounds when confirming.)
    val extendedMin = -0.5f
    val extendedMax = 1.5f
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width
                containerHeight = coordinates.size.height
            }
    ) {
        if (containerWidth > 0 && containerHeight > 0) {
            val containerW = containerWidth.toFloat()
            val containerH = containerHeight.toFloat()
            val imgW = (if (imageWidthPx > 0) imageWidthPx else containerWidth).toFloat().coerceAtLeast(1f)
            val imgH = (if (imageHeightPx > 0) imageHeightPx else containerHeight).toFloat().coerceAtLeast(1f)
            val scale = minOf(containerW / imgW, containerH / imgH)
            val displayedW = (imgW * scale).coerceAtLeast(1f)
            val displayedH = (imgH * scale).coerceAtLeast(1f)
            val imageOffsetX = (containerW - displayedW) / 2f
            val imageOffsetY = (containerH - displayedH) / 2f

            val crop = currentCrop
            val cropLeftPx = imageOffsetX + displayedW * crop.left
            val cropTopPx = imageOffsetY + displayedH * crop.top
            val cropRightPx = imageOffsetX + displayedW * crop.right
            val cropBottomPx = imageOffsetY + displayedH * crop.bottom

            // Semi-transparent overlay outside crop area
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                
                // Draw semi-transparent overlay
                val overlayColor = Color.Black.copy(alpha = 0.6f)

                // Draw overlay ONLY outside crop area (keeps inside see-through)
                // Top
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(width, cropTopPx.coerceIn(0f, height))
                )
                // Bottom
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, cropBottomPx.coerceIn(0f, height)),
                    size = Size(width, (height - cropBottomPx).coerceAtLeast(0f))
                )
                // Left
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, cropTopPx.coerceIn(0f, height)),
                    size = Size(cropLeftPx.coerceIn(0f, width), (cropBottomPx - cropTopPx).coerceAtLeast(0f))
                )
                // Right
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(cropRightPx.coerceIn(0f, width), cropTopPx.coerceIn(0f, height)),
                    size = Size((width - cropRightPx).coerceAtLeast(0f), (cropBottomPx - cropTopPx).coerceAtLeast(0f))
                )
                
                // Draw crop frame
                val frameColor = AppColors.TextPrimary
                val frameStrokeWidth = 3f
                
                drawRect(
                    color = frameColor,
                    topLeft = Offset(cropLeftPx, cropTopPx),
                    size = Size(
                        (cropRightPx - cropLeftPx).coerceAtLeast(1f),
                        (cropBottomPx - cropTopPx).coerceAtLeast(1f)
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = frameStrokeWidth)
                )
                
                // Draw grid lines
                val gridColor = frameColor.copy(alpha = 0.5f)
                val gridStrokeWidth = 1f
                
                // Vertical thirds
                for (i in 1..2) {
                    val x = cropLeftPx + ((cropRightPx - cropLeftPx) * i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, cropTopPx),
                        end = Offset(x, cropBottomPx),
                        strokeWidth = gridStrokeWidth
                    )
                }
                
                // Horizontal thirds
                for (i in 1..2) {
                    val y = cropTopPx + ((cropBottomPx - cropTopPx) * i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(cropLeftPx, y),
                        end = Offset(cropRightPx, y),
                        strokeWidth = gridStrokeWidth
                    )
                }
            }

            // Drag inside crop area to move the whole rectangle
            var draggingRect by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val cropNow = currentCrop
                                val l = imageOffsetX + displayedW * cropNow.left
                                val t = imageOffsetY + displayedH * cropNow.top
                                val r = imageOffsetX + displayedW * cropNow.right
                                val b = imageOffsetY + displayedH * cropNow.bottom

                                val handleRadiusPx = with(density) { 28.dp.toPx() }
                                fun near(p: Offset, hx: Float, hy: Float): Boolean {
                                    val dx = p.x - hx
                                    val dy = p.y - hy
                                    return (dx * dx + dy * dy) <= handleRadiusPx * handleRadiusPx
                                }

                                val inRect = start.x in l..r && start.y in t..b
                                val nearHandle =
                                    near(start, l, t) || near(start, r, t) || near(start, l, b) || near(start, r, b)

                                draggingRect = inRect && !nearHandle
                            },
                            onDragEnd = { draggingRect = false },
                            onDragCancel = { draggingRect = false },
                            onDrag = { change, dragAmount ->
                                if (!draggingRect) return@detectDragGestures
                                change.consume()

                                val cropNow = currentCrop
                                val widthF = (cropNow.right - cropNow.left).coerceIn(minCropSize, 1f)
                                val heightF = (cropNow.bottom - cropNow.top).coerceIn(minCropSize, 1f)
                                val dxF = dragAmount.x / displayedW
                                val dyF = dragAmount.y / displayedH

                                val newLeft = (cropNow.left + dxF).coerceIn(0f, 1f - widthF)
                                val newTop = (cropNow.top + dyF).coerceIn(extendedMin, extendedMax - heightF)
                                onCropChange(newLeft, newTop, newLeft + widthF, newTop + heightF)
                            }
                        )
                    }
            )
            
            // Corner handles for resizing
            val handleSize = 40.dp
            
            // Top-left handle
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { cropLeftPx.toDp() - handleSize / 2 },
                        y = with(density) { cropTopPx.toDp() - handleSize / 2 }
                    )
                    .size(handleSize)
                    .background(AppColors.DarkSurfaceVariant, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cropNow = currentCrop
                            onCropChange(
                                (cropNow.left + dragAmount.x / displayedW).coerceIn(0f, cropNow.right - minCropSize),
                                (cropNow.top + dragAmount.y / displayedH).coerceIn(extendedMin, cropNow.bottom - minCropSize),
                                cropNow.right,
                                cropNow.bottom
                            )
                        }
                    }
            )
            
            // Top-right handle
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { cropRightPx.toDp() - handleSize / 2 },
                        y = with(density) { cropTopPx.toDp() - handleSize / 2 }
                    )
                    .size(handleSize)
                    .background(AppColors.DarkSurfaceVariant, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cropNow = currentCrop
                            onCropChange(
                                cropNow.left,
                                (cropNow.top + dragAmount.y / displayedH).coerceIn(extendedMin, cropNow.bottom - minCropSize),
                                (cropNow.right + dragAmount.x / displayedW).coerceIn(cropNow.left + minCropSize, 1f),
                                cropNow.bottom
                            )
                        }
                    }
            )
            
            // Bottom-left handle
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { cropLeftPx.toDp() - handleSize / 2 },
                        y = with(density) { cropBottomPx.toDp() - handleSize / 2 }
                    )
                    .size(handleSize)
                    .background(AppColors.DarkSurfaceVariant, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cropNow = currentCrop
                            onCropChange(
                                (cropNow.left + dragAmount.x / displayedW).coerceIn(0f, cropNow.right - minCropSize),
                                cropNow.top,
                                cropNow.right,
                                (cropNow.bottom + dragAmount.y / displayedH).coerceIn(cropNow.top + minCropSize, extendedMax)
                            )
                        }
                    }
            )
            
            // Bottom-right handle
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { cropRightPx.toDp() - handleSize / 2 },
                        y = with(density) { cropBottomPx.toDp() - handleSize / 2 }
                    )
                    .size(handleSize)
                    .background(AppColors.DarkSurfaceVariant, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cropNow = currentCrop
                            onCropChange(
                                cropNow.left,
                                cropNow.top,
                                (cropNow.right + dragAmount.x / displayedW).coerceIn(cropNow.left + minCropSize, 1f),
                                (cropNow.bottom + dragAmount.y / displayedH).coerceIn(cropNow.top + minCropSize, extendedMax)
                            )
                        }
                    }
            )
        }
    }
}
