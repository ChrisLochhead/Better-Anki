package com.betteranki.ui.ocr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
fun CardBatchReviewScreen(
    cards: List<GeneratedCard>,
    onBack: () -> Unit,
    onUpdateCard: (Int, GeneratedCard) -> Unit,
    onDeleteCard: (Int) -> Unit,
    onAddAll: (List<GeneratedCard>) -> Unit
) {
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "REVIEW CARDS (${cards.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkSurface,
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
                    onClick = { onAddAll(cards) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = cards.isNotEmpty() && cards.none { it.isTranslating },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.DarkSurfaceVariant,
                        contentColor = AppColors.TextPrimary,
                        disabledContainerColor = AppColors.DarkSurfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = AppColors.TextPrimary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ADD ${cards.size} CARDS TO DECK",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No cards generated",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(cards) { index, card ->
                    CardReviewItem(
                        card = card,
                        index = index,
                        onEdit = {
                            editingIndex = index
                            showEditDialog = true
                        },
                        onDelete = { onDeleteCard(index) }
                    )
                }
            }
        }
    }

    if (showEditDialog && editingIndex in cards.indices) {
        val card = cards[editingIndex]
        com.betteranki.ui.decklist.AddCardDialog(
            onDismiss = {
                showEditDialog = false
                editingIndex = -1
            },
            startWithManualEntry = true,
            initialFront = card.front,
            initialBack = card.back,
            initialFrontDescription = card.frontDescription,
            initialBackDescription = card.backDescription,
            initialImageUri = card.imageUri,
            initialShowImageFront = card.showImageOnFront,
            initialShowImageBack = card.showImageOnBack,
            initialExampleSentence = card.example,
            initialShowExampleFront = card.showExampleOnFront,
            initialShowExampleBack = card.showExampleOnBack,
            initialAudioUri = card.audioUri,
            initialShowAudioFront = card.audioOnFront,
            initialShowAudioBack = card.audioOnBack,
            onAddManual = { front, back, frontDesc, backDesc, imageUri, showImageFront, showImageBack, exampleSentence, showExampleFront, showExampleBack, audioUri, showAudioFront, showAudioBack ->
                onUpdateCard(
                    editingIndex,
                    card.copy(
                        front = front,
                        back = back,
                        frontDescription = frontDesc,
                        backDescription = backDesc,
                        imageUri = imageUri,
                        showImageOnFront = showImageFront,
                        showImageOnBack = showImageBack,
                        example = exampleSentence,
                        showExampleOnFront = showExampleFront,
                        showExampleOnBack = showExampleBack,
                        audioUri = audioUri,
                        audioOnFront = showAudioFront,
                        audioOnBack = showAudioBack
                    )
                )
                showEditDialog = false
                editingIndex = -1
            },
            onAddByPhoto = { }
        )
    }
}

@Composable
fun CardReviewItem(
    card: GeneratedCard,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.DarkSurface
        ),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CARD ${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = AppColors.Error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Front: ${card.front}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                if (card.example.isNotBlank()) {
                    Text(
                        text = "Example: ${card.example}",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }

                if (card.isTranslating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.Secondary
                        )
                        Text(
                            text = "Translating...",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                } else if (card.back.isNotBlank()) {
                    Text(
                        text = "Back: ${card.back}",
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary
                    )
                } else {
                    Text(
                        text = "Back: (no translation)",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
