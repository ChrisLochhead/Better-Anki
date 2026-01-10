package com.betteranki.ui.deckdetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.DeckSettings
import com.betteranki.data.model.DeckWithStats
import com.betteranki.data.model.ReviewHistory
import com.betteranki.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailsScreen(
    deckWithStats: DeckWithStats,
    cards: List<Card>,
    reviewHistory: List<ReviewHistory>,
    deckSettings: DeckSettings,
    newCardsDueToday: Int, // New cards available to study today
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onDeleteDeck: () -> Unit,
    onFreezeDeck: (Int) -> Unit,
    onUnfreezeDeck: () -> Unit,
    onOcrScan: () -> Unit,
    onViewAllCards: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFreezeDialog by remember { mutableStateOf(false) }
    var freezeDays by remember { mutableStateOf("7") }

    Scaffold(
        topBar = {
            // Sharp top bar
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
                            text = deckWithStats.deck.name.uppercase(),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sharp View Cards Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Primary, RoundedCornerShape(2.dp))
                    .clickable { onViewAllCards() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VIEW ALL CARDS (${cards.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = AppColors.Primary
                )
            }

            // OCR Scan Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Primary, RoundedCornerShape(2.dp))
                    .clickable { onOcrScan() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SCAN TEXT FROM PHOTO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppColors.Primary
                    )
                }
            }

            // Freeze Deck Section
            val isFrozen = deckSettings.isFrozen && deckSettings.freezeUntilDate != null && 
                           deckSettings.freezeUntilDate > System.currentTimeMillis()
            
            if (isFrozen) {
                // Show frozen status and unfreeze button
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val freezeUntilStr = dateFormat.format(Date(deckSettings.freezeUntilDate!!))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.CardReview.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.CardReview, RoundedCornerShape(2.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AppColors.CardReview,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DECK FROZEN",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = AppColors.CardReview
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Until $freezeUntilStr",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(AppColors.Secondary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .border(1.dp, AppColors.Secondary, RoundedCornerShape(2.dp))
                                .clickable { onUnfreezeDeck() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = AppColors.Secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UNFREEZE NOW",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = AppColors.Secondary
                                )
                            }
                        }
                    }
                }
            } else {
                // Show freeze button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.Primary, RoundedCornerShape(2.dp))
                        .clickable { showFreezeDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FREEZE DECK",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = AppColors.Primary
                        )
                    }
                }
            }

            // Sharp Delete Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(AppColors.Error.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Error.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .clickable { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = AppColors.Error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DELETE DECK",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppColors.Error
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.Border)
            )

            // Progress Chart
            Text(
                text = "PROGRESS OVERVIEW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = AppColors.TextTertiary
            )

            // Sharp progress card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stats bars
                    ProgressBarItem(
                        label = "New",
                        count = newCardsDueToday, // Show new cards due today, not total new cards
                        total = deckWithStats.totalCards,
                        color = AppColors.CardNew
                    )
                    ProgressBarItem(
                        label = "Hard",
                        count = deckWithStats.hardCards,
                        total = deckWithStats.totalCards,
                        color = AppColors.CardHard
                    )
                    ProgressBarItem(
                        label = "Easy",
                        count = deckWithStats.easyCards,
                        total = deckWithStats.totalCards,
                        color = AppColors.CardEasy
                    )
                    ProgressBarItem(
                        label = "Mastered",
                        count = deckWithStats.masteredCards,
                        total = deckWithStats.totalCards,
                        color = AppColors.CardMastered
                    )
                }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stacked Line Chart
            Text(
                text = "Progress Over Time",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                StackedAreaChart(
                    reviewHistory = reviewHistory,
                    totalCards = deckWithStats.totalCards,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    ProgressOverviewLegend()
                }
            }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Study Now Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        if (deckWithStats.dueForReview > 0) AppColors.Primary.copy(alpha = 0.15f) else AppColors.DarkSurfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        1.dp,
                        if (deckWithStats.dueForReview > 0) AppColors.Primary else AppColors.Border.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
                    .clickable(enabled = deckWithStats.dueForReview > 0) { onStudy() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (deckWithStats.dueForReview > 0)
                        "STUDY NOW (${deckWithStats.dueForReview})"
                    else
                        "STUDY NOW",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = if (deckWithStats.dueForReview > 0) AppColors.Primary else AppColors.TextTertiary
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AppColors.DarkSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            shape = RoundedCornerShape(2.dp),
            title = { Text("Delete deck?") },
            text = { Text("Are you sure you want to delete '${deckWithStats.deck.name}'? This will delete all ${cards.size} cards and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteDeck()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Freeze Deck Dialog
    if (showFreezeDialog) {
        AlertDialog(
            onDismissRequest = { showFreezeDialog = false },
            containerColor = AppColors.DarkSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            shape = RoundedCornerShape(2.dp),
            title = { 
                Text(
                    text = "Freeze deck",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            },
            text = { 
                Column {
                    Text("Freezing the deck will pause all reviews for the specified number of days. Cards won't pile up during this time.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = freezeDays,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) {
                                freezeDays = it
                            }
                        },
                        label = { Text("Days to freeze") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.CardReview,
                            unfocusedBorderColor = AppColors.Border,
                            focusedLabelColor = AppColors.CardReview,
                            cursorColor = AppColors.CardReview,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = freezeDays.toIntOrNull() ?: 7
                        if (days > 0) {
                            onFreezeDeck(days)
                            showFreezeDialog = false
                            freezeDays = "7"
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppColors.CardReview
                    )
                ) {
                    Text(
                        text = "Freeze",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showFreezeDialog = false
                        freezeDays = "7"
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProgressBarItem(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$count / $total",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = if (total > 0) count.toFloat() / total.toFloat() else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ProgressChart(history: List<ReviewHistory>, modifier: Modifier = Modifier) {
    val maxValue = history.maxOfOrNull { max(it.learningCards, it.masteredCards) } ?: 1
    
    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height
        val spacing = width / (history.size + 1)
        val padding = 40f
        
        // Draw axes
        drawLine(
            color = Color.Gray,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Gray,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2f
        )
        
        if (history.isEmpty()) return@Canvas
        
        // Draw learning cards line
        val learningPath = Path()
        history.forEachIndexed { index, item ->
            val x = padding + (index + 1) * spacing
            val y = height - padding - (item.learningCards.toFloat() / maxValue) * (height - 2 * padding)
            
            if (index == 0) {
                learningPath.moveTo(x, y)
            } else {
                learningPath.lineTo(x, y)
            }
            
            // Draw point
            drawCircle(
                color = AppColors.CardLearning,
                radius = 6f,
                center = Offset(x, y)
            )
        }
        
        drawPath(
            path = learningPath,
            color = AppColors.CardLearning,
            style = Stroke(width = 4f)
        )
        
        // Draw mastered cards line
        val masteredPath = Path()
        history.forEachIndexed { index, item ->
            val x = padding + (index + 1) * spacing
            val y = height - padding - (item.masteredCards.toFloat() / maxValue) * (height - 2 * padding)
            
            if (index == 0) {
                masteredPath.moveTo(x, y)
            } else {
                masteredPath.lineTo(x, y)
            }
            
            // Draw point
            drawCircle(
                color = AppColors.CardMastered,
                radius = 6f,
                center = Offset(x, y)
            )
        }
        
        drawPath(
            path = masteredPath,
            color = AppColors.CardMastered,
            style = Stroke(width = 4f)
        )
    }
}

@Composable
fun StackedAreaChart(
    reviewHistory: List<ReviewHistory>,
    totalCards: Int,
    modifier: Modifier = Modifier
) {
    if (reviewHistory.isEmpty() || totalCards == 0) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No history data yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }
    
    // Take last 14 days of history
    val rawHistoryData = reviewHistory.takeLast(14)
    
    // If there's only 1 data point, add a synthetic starting point to fill the chart
    val historyData = if (rawHistoryData.size == 1) {
        val firstPoint = rawHistoryData.first()
        val startingPoint = ReviewHistory(
            id = 0,
            deckId = firstPoint.deckId,
            date = firstPoint.date - 86400000, // 1 day before
            cardsReviewed = 0,
            newCards = totalCards, // All cards start as new
            learningCards = 0,
            reviewCards = 0,
            masteredCards = 0
        )
        listOf(startingPoint, firstPoint)
    } else {
        rawHistoryData
    }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 50f
        val paddingRight = 20f
        val paddingTop = 20f
        val paddingBottom = 40f
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        
        if (historyData.isEmpty()) return@Canvas
        
        val maxValue = totalCards.toFloat()
        if (maxValue == 0f) return@Canvas
        
        // Draw axes
        val axisColor = Color.Gray
        // Y-axis
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, paddingTop + chartHeight),
            strokeWidth = 2f
        )
        // X-axis
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, paddingTop + chartHeight),
            end = Offset(paddingLeft + chartWidth, paddingTop + chartHeight),
            strokeWidth = 2f
        )
        
        // Y-axis labels
        val yLabelCount = 5
        drawIntoCanvas { canvas ->
            for (i in 0..yLabelCount) {
                val value = (maxValue / yLabelCount * i).toInt()
                val y = paddingTop + chartHeight - (chartHeight / yLabelCount * i)
                canvas.nativeCanvas.drawText(
                    value.toString(),
                    paddingLeft - 10f,
                    y + 5f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                )
            }
        }
        
        // Calculate x step
        val xStep = if (historyData.size > 1) chartWidth / (historyData.size - 1).toFloat() else chartWidth
        
        // X-axis labels showing session numbers
        // Dynamically choose which sessions to label to keep them within bounds
        val maxLabels = 7  // Maximum number of labels to show
        val actualSessionCount = rawHistoryData.size  // Use original count for session numbers
        val labelIndices = if (historyData.size <= maxLabels) {
            historyData.indices.toList()
        } else {
            // Evenly distribute labels across all sessions
            val step = (historyData.size - 1).toFloat() / (maxLabels - 1)
            (0 until maxLabels).map { (it * step).toInt() }
        }
        
        drawIntoCanvas { canvas ->
            labelIndices.forEach { index ->
                // Adjust session number if we added a synthetic starting point
                val sessionNumber = if (actualSessionCount == 1 && historyData.size == 2) {
                    if (index == 0) 0 else 1  // Show "0" for start, "1" for actual session
                } else {
                    index + 1  // Normal: session numbers start at 1
                }
                val x = paddingLeft + index * xStep
                canvas.nativeCanvas.drawText(
                    "$sessionNumber",
                    x,
                    paddingTop + chartHeight + 30f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
            
            // X-axis label
            canvas.nativeCanvas.drawText(
                "Review Sessions",
                paddingLeft + chartWidth / 2,
                paddingTop + chartHeight + 55f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
            )
        }
        
        // Calculate stacked Y positions for each category
        val points = historyData.mapIndexed { index, history ->
            val x = paddingLeft + index * xStep
            
            // Stack from bottom to top: Mastered -> Easy -> Hard -> New
            val masteredHeight = (history.masteredCards.toFloat() / maxValue) * chartHeight
            val easyHeight = (history.reviewCards.toFloat() / maxValue) * chartHeight
            val hardHeight = (history.learningCards.toFloat() / maxValue) * chartHeight
            val newHeight = (history.newCards.toFloat() / maxValue) * chartHeight
            
            // Y coordinates (from bottom)
            val bottom = paddingTop + chartHeight
            val masteredY = bottom - masteredHeight
            val easyY = masteredY - easyHeight
            val hardY = easyY - hardHeight
            val newY = hardY - newHeight
            
            mapOf(
                "x" to x,
                "bottom" to bottom,
                "mastered" to masteredY,
                "easy" to easyY,
                "hard" to hardY,
                "new" to newY
            )
        }
        
        if (points.isEmpty()) return@Canvas
        
        // Draw mastered area (sky blue, bottom layer)
        if (historyData.any { it.masteredCards > 0 }) {
            val masteredPath = Path().apply {
                moveTo(points.first()["x"]!!, points.first()["bottom"]!!)
                points.forEach { lineTo(it["x"]!!, it["mastered"]!!) }
                lineTo(points.last()["x"]!!, points.last()["bottom"]!!)
                close()
            }
            drawPath(masteredPath, AppColors.CardMastered, alpha = 0.8f)
        }
        
        // Draw easy area (blue-purple)
        if (historyData.any { it.reviewCards > 0 }) {
            val easyPath = Path().apply {
                moveTo(points.first()["x"]!!, points.first()["mastered"]!!)
                points.forEach { lineTo(it["x"]!!, it["easy"]!!) }
                for (i in points.size - 1 downTo 0) {
                    lineTo(points[i]["x"]!!, points[i]["mastered"]!!)
                }
                close()
            }
            drawPath(easyPath, AppColors.CardEasy, alpha = 0.8f)
        }
        
        // Draw hard area (purple-red)
        if (historyData.any { it.learningCards > 0 }) {
            val hardPath = Path().apply {
                moveTo(points.first()["x"]!!, points.first()["easy"]!!)
                points.forEach { lineTo(it["x"]!!, it["hard"]!!) }
                for (i in points.size - 1 downTo 0) {
                    lineTo(points[i]["x"]!!, points[i]["easy"]!!)
                }
                close()
            }
            drawPath(hardPath, AppColors.CardHard, alpha = 0.8f)
        }
        
        // Draw new/unseen area (crimson, top layer)
        if (historyData.any { it.newCards > 0 }) {
            val newPath = Path().apply {
                moveTo(points.first()["x"]!!, points.first()["hard"]!!)
                points.forEach { lineTo(it["x"]!!, it["new"]!!) }
                for (i in points.size - 1 downTo 0) {
                    lineTo(points[i]["x"]!!, points[i]["hard"]!!)
                }
                close()
            }
            drawPath(newPath, AppColors.CardNew, alpha = 0.8f)
        }
    }
}

@Composable
private fun ProgressOverviewLegend() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(label = "New", color = AppColors.CardNew)
            LegendItem(label = "Easy", color = AppColors.CardEasy)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(label = "Hard", color = AppColors.CardHard)
            LegendItem(label = "Mastered", color = AppColors.CardMastered)
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppColors.TextTertiary
        )
    }
}
