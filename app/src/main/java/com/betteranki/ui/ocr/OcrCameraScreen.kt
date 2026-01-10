package com.betteranki.ui.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.betteranki.ui.theme.AppColors
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrCameraScreen(
    onBack: () -> Unit,
    onImageCaptured: (Uri, Int, Float, Float, Float, Float) -> Unit,
    onImageSelected: (Uri, Int, Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it, Surface.ROTATION_0, 0f, 0f, 1f, 1f) }
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "SCAN TEXT",
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
                            .background(AppColors.Primary)
                    )
                }
            }
        },
        containerColor = AppColors.DarkBackground
    ) { paddingValues ->
        if (hasCameraPermission) {
            CameraPreviewWithControls(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onImageCaptured = { uri, rotation -> 
                    onImageCaptured(uri, rotation, 0f, 0f, 1f, 1f)
                },
                onOpenGallery = { 
                    galleryLauncher.launch("image/*")
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Camera permission required",
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary
                        )
                    ) {
                        Text("Grant Permission")
                    }
                    
                    Text(
                        text = "or",
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.Secondary
                        )
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select from Gallery")
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewWithControls(
    modifier: Modifier = Modifier,
    onImageCaptured: (Uri, Int) -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    val cameraExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }
    
    var isCapturing by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, cameraExecutor)
    }
    
    Box(modifier = modifier) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            update = { view ->
                // Keep capture rotation aligned to display orientation.
                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                if (imageCapture.targetRotation != rotation) {
                    imageCapture.targetRotation = rotation
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Hint text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = AppColors.DarkSurface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Frame your text and capture",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        
        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(AppColors.DarkSurface, CircleShape)
                    .border(2.dp, AppColors.Secondary, CircleShape)
                    .clickable { onOpenGallery() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = "Gallery",
                    tint = AppColors.Secondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Capture button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        if (isCapturing) AppColors.Primary.copy(alpha = 0.5f) else AppColors.Primary,
                        CircleShape
                    )
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        val captureRotation = imageCapture.targetRotation
                        val photoFile = File(
                            context.cacheDir,
                            "ocr_${System.currentTimeMillis()}.jpg"
                        )
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        
                        imageCapture.takePicture(
                            outputOptions,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    onImageCaptured(Uri.fromFile(photoFile), captureRotation)
                                }
                                
                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Capture",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            // Spacer to balance layout
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}
