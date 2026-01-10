package com.betteranki.ui.ocr

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betteranki.ui.theme.AppColors

sealed class PreviewState {
    data object Idle : PreviewState()
    data object Loading : PreviewState()
    data class Error(val message: String) : PreviewState()
    data object Ready : PreviewState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPreprocessPreviewScreen(
    imageUri: Uri,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    previewBitmap: Bitmap?,
    previewState: PreviewState,
    onPreparePreview: () -> Unit,
    onBack: () -> Unit,
    onRunOcr: (rotationDegrees: Int) -> Unit
) {
    LaunchedEffect(imageUri, cropLeft, cropTop, cropRight, cropBottom) {
        onPreparePreview()
    }

    var rotationDegrees by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AppColors.DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Input preview", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkSurface,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Rotate the image so the text is upright. This helps recognition work correctly.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { rotationDegrees = (rotationDegrees + 270) % 360 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    border = BorderStroke(1.dp, AppColors.Border),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.RotateLeft, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Rotate left")
                }

                OutlinedButton(
                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    border = BorderStroke(1.dp, AppColors.Border),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Rotate right")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AppColors.DarkSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (previewState) {
                    is PreviewState.Loading -> {
                        CircularProgressIndicator(
                            color = AppColors.Primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    is PreviewState.Error -> {
                        Text(
                            text = previewState.message,
                            color = AppColors.Error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "OCR input preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .graphicsLayer {
                                        rotationZ = rotationDegrees.toFloat()
                                    }
                            )
                        } else {
                            Text(
                                text = "No preview available",
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.DarkSurfaceVariant,
                        contentColor = AppColors.TextPrimary
                    )
                ) {
                    Text("Back")
                }

                Button(
                    onClick = { onRunOcr(rotationDegrees) },
                    enabled = previewBitmap != null && previewState !is PreviewState.Loading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.DarkBackground
                    )
                ) {
                    Text("Run recognition")
                }
            }
        }
    }
}
