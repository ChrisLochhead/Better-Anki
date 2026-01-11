package com.betteranki.ui.allcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.model.Card
import com.betteranki.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCardsScreen(
    deckName: String,
    cards: List<Card>,
    onBack: () -> Unit,
    onUpdateCard: (Card) -> Unit,
    onDeleteCard: (Card) -> Unit
) {
    var editedCards by remember { mutableStateOf<Map<Long, Card>>(emptyMap()) }
    val hasChanges = editedCards.isNotEmpty()
    
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
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
                                text = (if (deckName.length > 20) deckName.take(20) + "..." else deckName).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = AppColors.TextPrimary
                            )
                        }
                        
                        // Save button - highlighted when there are changes
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (hasChanges) AppColors.Primary else AppColors.DarkSurfaceVariant,
                                    RoundedCornerShape(2.dp)
                                )
                                .clickable(enabled = hasChanges) {
                                    editedCards.values.forEach { card ->
                                        onUpdateCard(card)
                                    }
                                    editedCards = emptyMap()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save",
                                tint = if (hasChanges) AppColors.DarkBackground else AppColors.TextTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (cards.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cards in this deck",
                            color = AppColors.TextTertiary,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                itemsIndexed(cards) { index, originalCard ->
                    val card = editedCards[originalCard.id] ?: originalCard
                    
                    EditableCardItem(
                        cardNumber = index + 1,
                        card = card,
                        onCardChanged = { updatedCard ->
                            editedCards = editedCards + (updatedCard.id to updatedCard)
                        },
                        onDeleteCard = onDeleteCard
                    )
                }
            }
        }
    }
}

@Composable
fun EditableCardItem(
    cardNumber: Int,
    card: Card,
    onCardChanged: (Card) -> Unit,
    onDeleteCard: (Card) -> Unit
) {
    var front by remember(card.id) { mutableStateOf(card.front) }
    var back by remember(card.id) { mutableStateOf(card.back) }
    var showMedia by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Update the card when fields change
    LaunchedEffect(front, back) {
        if (front != card.front || back != card.back) {
            onCardChanged(card.copy(front = front, back = back))
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.DarkSurface, RoundedCornerShape(4.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        // Card number and status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CARD $cardNumber",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = AppColors.Primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = card.status.name,
                    fontSize = 10.sp,
                    color = AppColors.TextTertiary
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AppColors.Error.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .clickable { onDeleteCard(card) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete card",
                        tint = AppColors.Error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Front field
        Column {
            Text(
                text = "FRONT:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = front,
                onValueChange = { front = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkBackground, RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                    .padding(8.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(AppColors.Primary)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Back field
        Column {
            Text(
                text = "BACK:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = back,
                onValueChange = { back = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkBackground, RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                    .padding(8.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(AppColors.Primary)
            )
        }
        
        // Media section (if card has media)
        if (card.hasMediaFiles && card.mediaFileNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkBackground, RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                    .clickable { showMedia = !showMedia }
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (card.imageUri != null) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (card.audioUri != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        if (card.audioUri != null) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MEDIA FILES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppColors.Primary
                        )
                    }
                    Icon(
                        imageVector = if (showMedia) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Expanded media content
            if (showMedia) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.DarkBackground, RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Display image if present
                    card.imageUri?.let { imageUri ->
                        if (File(imageUri).exists()) {
                            Text(
                                text = "Image:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = AppColors.TextSecondary
                            )
                            AsyncImage(
                                model = File(imageUri),
                                contentDescription = "Card image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .background(AppColors.DarkSurface, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    
                    // Display audio if present
                    card.audioUri?.let { audioUri ->
                        if (File(audioUri).exists()) {
                            Text(
                                text = "Audio:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = AppColors.TextSecondary
                            )
                            val fileName = File(audioUri).name
                            Text(
                                text = fileName,
                                fontSize = 12.sp,
                                color = AppColors.TextPrimary
                            )
                            Button(
                                onClick = {
                                    val mediaPlayer = android.media.MediaPlayer()
                                    try {
                                        mediaPlayer.setDataSource(audioUri)
                                        mediaPlayer.prepare()
                                        mediaPlayer.start()
                                        mediaPlayer.setOnCompletionListener { it.release() }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Primary,
                                    contentColor = AppColors.DarkBackground
                                ),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Play audio",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PLAY AUDIO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
