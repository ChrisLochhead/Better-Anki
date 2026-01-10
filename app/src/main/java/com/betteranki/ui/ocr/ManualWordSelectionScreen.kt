package com.betteranki.ui.ocr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWordSelectionScreen(
    fullText: String,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // Split text into words and remove empty/whitespace
    val words = remember(fullText) {
        fullText.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.replace(Regex("[^\\p{L}'-]"), "") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    
    // Track selected words
    val selectedWords = remember { mutableStateListOf<String>() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SELECT WORDS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "${selectedWords.size} SELECTED",
                            fontSize = 12.sp,
                            color = AppColors.TextPrimary.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = AppColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Primary,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        },
        containerColor = AppColors.DarkBackground,
        bottomBar = {
            Surface(
                color = AppColors.DarkSurface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { onConfirm(selectedWords.toList()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = selectedWords.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        disabledContainerColor = AppColors.Primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "GENERATE ${selectedWords.size} CARDS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        if (words.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No words found",
                    color = AppColors.TextSecondary
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words) { word ->
                    val isSelected = selectedWords.contains(word)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable {
                                if (isSelected) {
                                    selectedWords.remove(word)
                                } else {
                                    selectedWords.add(word)
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AppColors.Primary else AppColors.DarkSurface,
                        border = if (isSelected) null else BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = word,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = AppColors.TextPrimary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
