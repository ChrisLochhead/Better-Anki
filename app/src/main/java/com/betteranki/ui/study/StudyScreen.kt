package com.betteranki.ui.study

import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.betteranki.data.model.StudySettings
import com.betteranki.ui.theme.AppColors
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit,
    onComplete: (Int, Int) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showTimer by remember { mutableStateOf(false) }
    
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onComplete(state.reviewedCount, state.correctCount)
        }
    }
    
    Scaffold(
        topBar = {
            // Sharp-edged top bar
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
                        // Sharp back button
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
                            text = "STUDY SESSION",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = AppColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Timer toggle button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (showTimer) AppColors.Primary.copy(alpha = 0.2f) else AppColors.DarkSurfaceVariant, 
                                    RoundedCornerShape(2.dp)
                                )
                                .border(
                                    1.dp, 
                                    if (showTimer) AppColors.Primary else AppColors.Border, 
                                    RoundedCornerShape(2.dp)
                                )
                                .clickable { showTimer = !showTimer },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⏱",
                                fontSize = 18.sp,
                                color = if (showTimer) AppColors.Primary else AppColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Accent line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(AppColors.Primary)
                    )
                }
            }
        },
        bottomBar = {
            // Timer bar (shown only when toggled on)
            if (state.currentCard != null && !state.isFlipped && showTimer) {
                DebugTimerBar(
                    cardStartTime = state.cardStartTime,
                    settings = state.currentSettings
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            state.currentCard?.let { card ->
                SwipeableCard(
                    card = card,
                    nextCard = state.remainingCards.firstOrNull(),
                    isFlipped = state.isFlipped,
                    newCount = state.newCardsRemaining,
                    reviewCount = state.reviewCardsRemaining,
                    doneCount = state.doneCount,
                    onFlip = { viewModel.flipCard() },
                    onSwipeLeft = { viewModel.onSwipeLeft() },
                    onSwipeRight = { viewModel.onSwipeRight() }
                )
            }
        }
    }
}

@Composable
fun DebugTimerBar(
    cardStartTime: Long,
    settings: StudySettings
) {
    var elapsedSeconds by remember { mutableStateOf(0L) }
    
    LaunchedEffect(cardStartTime) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - cardStartTime) / 1000
            kotlinx.coroutines.delay(100)
        }
    }
    
    // Sharp-edged timer bar
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.DarkSurface
    ) {
        Column {
            // Top accent line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AppColors.Primary)
            )
            
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppColors.TextTertiary
                    )
                    Text(
                        text = "${elapsedSeconds}s",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = AppColors.Primary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThresholdIndicator(
                        label = "EASY",
                        threshold = settings.easyThresholdSeconds,
                        elapsed = elapsedSeconds.toInt(),
                        color = AppColors.CardNew,
                        modifier = Modifier.weight(1f)
                    )
                    ThresholdIndicator(
                        label = "GOOD",
                        threshold = settings.goodThresholdSeconds,
                        elapsed = elapsedSeconds.toInt(),
                        color = AppColors.CardReview,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Current rating
                val currentRating = when {
                    elapsedSeconds < settings.easyThresholdSeconds -> "EASY"
                    elapsedSeconds < settings.goodThresholdSeconds -> "GOOD"
                    else -> "HARD"
                }
                val ratingColor = when {
                    elapsedSeconds < settings.easyThresholdSeconds -> AppColors.Success
                    elapsedSeconds < settings.goodThresholdSeconds -> AppColors.CardReview
                    else -> AppColors.Warning
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ratingColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, ratingColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentRating,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = ratingColor
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdIndicator(
    label: String,
    threshold: Int,
    elapsed: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val remaining = threshold - elapsed
    val isPassed = remaining <= 0
    val displayColor = if (isPassed) AppColors.TextTertiary else color
    
    Box(
        modifier = modifier
            .background(displayColor.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
            .border(1.dp, displayColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = displayColor.copy(alpha = 0.7f)
            )
            Text(
                text = if (isPassed) "—" else "${remaining}s",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = displayColor
            )
        }
    }
}

@Composable
fun SwipeableCard(
    card: com.betteranki.data.model.Card,
    nextCard: com.betteranki.data.model.Card?,
    isFlipped: Boolean,
    newCount: Int,
    reviewCount: Int,
    doneCount: Int,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val offsetX = remember { Animatable(0f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }

    // Make swipe a bit more sensitive.
    val baseThreshold = with(density) { 72.dp.toPx() }
    val swipeThreshold = maxOf(baseThreshold, cardWidthPx * 0.18f)

    // Avoid "flip-through" when a new card replaces a previously-flipped one.
    val flipRotation = remember { Animatable(0f) }
    var lastCardId by remember { mutableStateOf(card.id) }
    LaunchedEffect(card.id, isFlipped) {
        if (card.id != lastCardId) {
            lastCardId = card.id
            flipRotation.snapTo(0f)
            return@LaunchedEffect
        }

        if (isFlipped) {
            flipRotation.animateTo(
                180f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        } else {
            // Snap back instantly so we never show both sides during transitions.
            flipRotation.snapTo(0f)
        }
    }

    val showingBack = flipRotation.value > 90f
    val canSwipe = isFlipped && showingBack

    var isFirstCard by remember { mutableStateOf(true) }
    var hasShownHint by remember { mutableStateOf(false) }

    val hintOffsetX = remember { Animatable(0f) }
    val hintBlocking = isFirstCard && isFlipped && !hasShownHint
    val canInteractForAnswer = canSwipe && !hintBlocking

    LaunchedEffect(isFlipped, isFirstCard, hasShownHint) {
        if (!isFirstCard || !isFlipped || hasShownHint) return@LaunchedEffect

        hintOffsetX.snapTo(0f)
        hintOffsetX.animateTo(40f, animationSpec = tween(225, easing = EaseInOutSine))
        hintOffsetX.animateTo(0f, animationSpec = tween(175, easing = EaseInOutSine))
        hintOffsetX.animateTo(-40f, animationSpec = tween(225, easing = EaseInOutSine))
        hintOffsetX.animateTo(0f, animationSpec = tween(175, easing = EaseInOutSine))

        hasShownHint = true
        isFirstCard = false
    }

    val hintX = hintOffsetX.value
    val hintTintColor = when {
        hintX > 6f -> AppColors.Primary
        hintX < -6f -> AppColors.Error
        else -> null
    }

    val cardBorderColor = when {
        offsetX.value > swipeThreshold / 2 -> AppColors.Primary
        offsetX.value < -swipeThreshold / 2 -> AppColors.Error
        (offsetX.value == 0f && hintTintColor != null) -> hintTintColor
        else -> AppColors.Border
    }
    val cardBgColor = when {
        offsetX.value > swipeThreshold / 2 -> AppColors.Primary.copy(alpha = 0.1f)
        offsetX.value < -swipeThreshold / 2 -> AppColors.Error.copy(alpha = 0.1f)
        else -> AppColors.DarkSurface
    }

    val shape = RoundedCornerShape(4.dp)
    val dismissDistance = if (cardWidthPx > 0f) {
        cardWidthPx * 1.25f
    } else {
        with(density) { config.screenWidthDp.dp.toPx() * 1.25f }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(AppColors.CardNew.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.CardNew.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$newCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.CardNew
                        )
                        Text(
                            text = "NEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppColors.CardNew.copy(alpha = 0.7f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(AppColors.CardReview.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.CardReview.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$reviewCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.CardReview
                        )
                        Text(
                            text = "REVIEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppColors.CardReview.copy(alpha = 0.7f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(AppColors.CardMastered.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.CardMastered.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$doneCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.CardMastered
                        )
                        Text(
                            text = "DONE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppColors.CardMastered.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                if (nextCard != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(AppColors.DarkSurface, shape)
                            .border(2.dp, AppColors.Border, shape)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "QUESTION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                color = AppColors.Primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = nextCard.front,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = AppColors.TextPrimary
                            )
                            if (nextCard.frontDescription.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = nextCard.frontDescription,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .onSizeChanged { cardWidthPx = it.width.toFloat() }
                        .graphicsLayer {
                            translationX = offsetX.value + hintX
                            rotationZ = (offsetX.value + hintX) / 20f
                        }
                        .background(cardBgColor, shape)
                        .border(2.dp, cardBorderColor, shape)
                        .pointerInput(canInteractForAnswer) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (!canInteractForAnswer) {
                                        scope.launch { offsetX.animateTo(0f, tween(120)) }
                                        return@detectDragGestures
                                    }

                                    val x = offsetX.value
                                    when {
                                        x > swipeThreshold -> {
                                            scope.launch {
                                                offsetX.animateTo(
                                                    dismissDistance,
                                                    tween(150, easing = FastOutLinearInEasing)
                                                )
                                                offsetX.snapTo(0f)
                                                onSwipeRight()
                                            }
                                        }
                                        x < -swipeThreshold -> {
                                            scope.launch {
                                                offsetX.animateTo(
                                                    -dismissDistance,
                                                    tween(150, easing = FastOutLinearInEasing)
                                                )
                                                offsetX.snapTo(0f)
                                                onSwipeLeft()
                                            }
                                        }
                                        else -> {
                                            scope.launch {
                                                offsetX.animateTo(
                                                    0f,
                                                    tween(160, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (canInteractForAnswer) {
                                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                                    }
                                }
                            )
                        }
                        .clickable(enabled = !isFlipped) { onFlip() }
                ) {
                    if (offsetX.value == 0f && hintTintColor != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(hintTintColor.copy(alpha = 0.10f), shape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .graphicsLayer {
                                rotationY = flipRotation.value
                                cameraDistance = 12f * density.density
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = if (showingBack) 180f else 0f }
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (showingBack) "ANSWER" else "QUESTION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                color = if (showingBack) AppColors.Secondary else AppColors.Primary
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (showingBack) card.back else card.front,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = AppColors.TextPrimary
                            )

                            val description = if (showingBack) card.backDescription else card.frontDescription
                            if (description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = description,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = AppColors.TextSecondary
                                )
                            }

                            val showImage = if (showingBack) card.showImageOnBack else card.showImageOnFront
                            if (showImage && card.imageUri != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                AsyncImage(
                                    model = Uri.parse(card.imageUri),
                                    contentDescription = "Card image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            val showAudio = if (showingBack) card.audioOnBack else card.audioOnFront
                            if (showAudio && card.audioUri != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                var isPlaying by remember { mutableStateOf(false) }

                                Button(
                                    onClick = {
                                        if (!isPlaying) {
                                            isPlaying = true
                                            try {
                                                val mediaPlayer = MediaPlayer()
                                                mediaPlayer.setDataSource(card.audioUri)
                                                mediaPlayer.prepare()
                                                mediaPlayer.setOnCompletionListener {
                                                    isPlaying = false
                                                    it.release()
                                                }
                                                mediaPlayer.start()
                                            } catch (e: Exception) {
                                                isPlaying = false
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    enabled = !isPlaying,
                                    modifier = Modifier
                                        .background(
                                            AppColors.Primary.copy(alpha = 0.15f),
                                            RoundedCornerShape(2.dp)
                                        )
                                        .border(2.dp, AppColors.Primary, RoundedCornerShape(2.dp)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = AppColors.Primary
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Play audio",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPlaying) "PLAYING..." else "PLAY AUDIO",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            val showExample = if (showingBack) card.showExampleOnBack else card.showExampleOnFront
                            if (showExample && card.exampleSentence.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                                        .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "EXAMPLE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp,
                                            color = AppColors.Primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = card.exampleSentence,
                                            fontSize = 14.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isFlipped) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(56.dp)
                            .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(2.dp, AppColors.Primary, RoundedCornerShape(2.dp))
                            .clickable { onFlip() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "REVEAL ANSWER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = AppColors.Primary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .background(AppColors.Error.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .border(2.dp, AppColors.Error, RoundedCornerShape(2.dp))
                                .clickable(enabled = !hintBlocking) { onSwipeLeft() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "←",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AppColors.Error
                                )
                                Text(
                                    text = "AGAIN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = AppColors.Error
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .border(2.dp, AppColors.Primary, RoundedCornerShape(2.dp))
                                .clickable(enabled = !hintBlocking) { onSwipeRight() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "→",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AppColors.Primary
                                )
                                Text(
                                    text = "KNOW IT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = AppColors.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
