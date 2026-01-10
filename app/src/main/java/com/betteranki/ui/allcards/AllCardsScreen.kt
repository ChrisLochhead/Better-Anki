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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
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
    onUpdateCard: (Card) -> Unit
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
                        }
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
    onCardChanged: (Card) -> Unit
) {
    var front by remember(card.id) { mutableStateOf(card.front) }
    var back by remember(card.id) { mutableStateOf(card.back) }
    
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
            Text(
                text = card.status.name,
                fontSize = 10.sp,
                color = AppColors.TextTertiary
            )
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
    }
}
