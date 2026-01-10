package com.betteranki.ui.completion

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.model.ReviewHistory
import com.betteranki.data.model.StudySettings
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.ui.theme.AppColors
import com.betteranki.util.NotificationHelper
import androidx.core.app.NotificationManagerCompat
import com.betteranki.util.AdHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Composable
fun CompletionScreen(
    viewModel: CompletionViewModel,
    preferencesRepository: PreferencesRepository,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    val settings by preferencesRepository.currentSettings.collectAsState(initial = StudySettings())

    var showEnableNotificationsPrompt by remember { mutableStateOf(false) }
    var showAdPrompt by remember { mutableStateOf(false) }
    
    val adHelper = remember { AdHelper(context) }
    
    // Pre-load ad
    LaunchedEffect(Unit) {
        adHelper.loadRewardedAd()
    }

    fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val updated = settings.copy(notificationsEnabled = true)
            // Persist and schedule
            scope.launch {
                preferencesRepository.saveSettings(updated)
                NotificationHelper(context).scheduleNotifications(updated)
            }
            showEnableNotificationsPrompt = false
        } else {
            // Permission denied; guide user to system settings.
            openAppNotificationSettings()
        }
    }

    LaunchedEffect(settings.notificationsEnabled, settings.suppressEnableNotificationsPrompt) {
        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val shouldPrompt = !settings.suppressEnableNotificationsPrompt && (!settings.notificationsEnabled || !systemEnabled)
        if (shouldPrompt) {
            showEnableNotificationsPrompt = true
        } else {
            // Show ad prompt after notifications if not shown
            kotlinx.coroutines.delay(500)
            showAdPrompt = true
        }
    }

    if (showEnableNotificationsPrompt) {
        AlertDialog(
            onDismissRequest = { showEnableNotificationsPrompt = false },
            containerColor = AppColors.DarkSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("Enable notifications?") },
            text = {
                Text("Turn on reminders so you don’t forget to study.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                        if (!systemEnabled) {
                            openAppNotificationSettings()
                            return@TextButton
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val updated = settings.copy(notificationsEnabled = true)
                                scope.launch {
                                    preferencesRepository.saveSettings(updated)
                                    NotificationHelper(context).scheduleNotifications(updated)
                                }
                                showEnableNotificationsPrompt = false
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            val updated = settings.copy(notificationsEnabled = true)
                            scope.launch {
                                preferencesRepository.saveSettings(updated)
                                NotificationHelper(context).scheduleNotifications(updated)
                            }
                            showEnableNotificationsPrompt = false
                        }
                    }
                ) { Text("Enable") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val updated = settings.copy(suppressEnableNotificationsPrompt = true)
                            scope.launch {
                                preferencesRepository.saveSettings(updated)
                            }
                            showEnableNotificationsPrompt = false
                        }
                    ) { Text("Don't ask again") }

                    TextButton(onClick = { showEnableNotificationsPrompt = false }) { Text("Not now") }
                }
            },
            shape = RoundedCornerShape(2.dp)
        )
    }
    
    // Ad prompt dialog
    if (showAdPrompt) {
        AlertDialog(
            onDismissRequest = { showAdPrompt = false },
            containerColor = AppColors.DarkSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("Support the app", fontWeight = FontWeight.Bold) },
            text = {
                Text("This app and all its functionality will always remain free, but if you could watch an advert when you get a chance, it would be greatly appreciated!")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdPrompt = false
                        if (adHelper.isAdReady()) {
                            adHelper.showRewardedAd(
                                context as android.app.Activity,
                                onAdComplete = {
                                    scope.launch {
                                        adHelper.loadRewardedAd()
                                    }
                                },
                                onAdFailed = {}
                            )
                        } else {
                            adHelper.loadRewardedAd(
                                onAdLoaded = {
                                    adHelper.showRewardedAd(
                                        context as android.app.Activity,
                                        onAdComplete = {
                                            scope.launch {
                                                adHelper.loadRewardedAd()
                                            }
                                        },
                                        onAdFailed = {}
                                    )
                                },
                                onAdFailed = {}
                            )
                        }
                    }
                ) { Text("Watch ad") }
            },
            dismissButton = {
                TextButton(onClick = { showAdPrompt = false }) { Text("Not now") }
            },
            shape = RoundedCornerShape(2.dp)
        )
    }
    
    // Celebration animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "scale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Sharp-edged celebration badge
        Box(
            modifier = Modifier
                .size((100 * scale).dp)
                .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(2.dp, AppColors.Primary, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier.size((60 * scale).dp),
                tint = AppColors.Primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title - Bold and sharp
        Text(
            text = "SESSION COMPLETE",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = AppColors.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Great work!",
            fontSize = 16.sp,
            color = AppColors.TextSecondary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Stats cards - sharp-edged
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "REVIEWED",
                value = state.cardsReviewed.toString(),
                color = AppColors.CardReview,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "CORRECT",
                value = state.correctCount.toString(),
                color = AppColors.CardMastered,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "ACCURACY",
                value = if (state.cardsReviewed > 0) {
                    "${((state.correctCount.toFloat() / state.cardsReviewed) * 100).toInt()}%"
                } else "0%",
                color = AppColors.CardLearning,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Deck progress - sharp card
        state.deckStats?.let { stats ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
            ) {
                // Accent line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(AppColors.Primary)
                )
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "DECK PROGRESS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppColors.TextTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress percentage
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${state.percentLearned.toInt()}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.Primary
                        )
                        Text(
                            text = "%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MASTERED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppColors.TextTertiary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Sharp progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.percentLearned / 100f)
                                .height(8.dp)
                                .background(AppColors.Primary, RoundedCornerShape(2.dp))
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "HARD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = AppColors.CardHard.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${stats.hardCards}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = AppColors.CardHard
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "MASTERED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = AppColors.CardMastered.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${stats.masteredCards}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = AppColors.CardMastered
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress chart - sharp card
        if (state.reviewHistory.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "PROGRESS HISTORY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppColors.TextTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    StackedAreaChart(
                        reviewHistory = state.reviewHistory,
                        totalCards = state.deckStats?.totalCards ?: 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StackedAreaChartLegend(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Continue button - sharp, fill + outline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(2.dp, AppColors.Primary, RoundedCornerShape(4.dp))
                .clickable { onContinue() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONTINUE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = AppColors.Primary
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ProgressChart(history: List<ReviewHistory>) {
    val maxValue = history.maxOfOrNull { max(it.learningCards, it.masteredCards) } ?: 1
    val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(8.dp)
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
    
    // Legend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(AppColors.CardLearning, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Learning", fontSize = 12.sp, color = Color.Gray)
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(AppColors.CardMastered, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Mastered", fontSize = 12.sp, color = Color.Gray)
        }
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
        val maxLabels = 7
        val actualSessionCount = rawHistoryData.size
        val labelIndices = if (historyData.size <= maxLabels) {
            historyData.indices.toList()
        } else {
            val step = (historyData.size - 1).toFloat() / (maxLabels - 1)
            (0 until maxLabels).map { (it * step).toInt() }
        }
        
        drawIntoCanvas { canvas ->
            labelIndices.forEach { index ->
                val sessionNumber = if (actualSessionCount == 1 && historyData.size == 2) {
                    if (index == 0) 0 else 1
                } else {
                    index + 1
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
            
            val masteredHeight = (history.masteredCards.toFloat() / maxValue) * chartHeight
            val easyHeight = (history.reviewCards.toFloat() / maxValue) * chartHeight
            val hardHeight = (history.learningCards.toFloat() / maxValue) * chartHeight
            val newHeight = (history.newCards.toFloat() / maxValue) * chartHeight
            
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
private fun StackedAreaChartLegend(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
