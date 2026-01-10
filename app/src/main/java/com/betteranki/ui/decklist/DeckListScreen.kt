package com.betteranki.ui.decklist

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.betteranki.data.model.Card
import com.betteranki.data.model.DeckWithStats
import com.betteranki.data.model.StudySettings
import com.betteranki.ui.theme.AppColors
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.util.AdHelper
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.PlayArrow
import android.app.Activity

private data class FabAnchor(val leftPx: Float, val topPx: Float, val rightPx: Float, val bottomPx: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    preferencesRepository: PreferencesRepository,
    repository: AnkiRepository,
    onDeckClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onDebugSkipDay: () -> Unit = {},
    debugDayOffset: Int = 0,
    onOcrScan: (Long) -> Unit = {}
) {
    val rawDecks by viewModel.decks.collectAsState()
    val settings by preferencesRepository.currentSettings.collectAsState(initial = StudySettings())
    val coroutineScope = rememberCoroutineScope()

    data class TodayCounts(val newCount: Int, val reviewCount: Int)
    var todayCountsByDeckId by remember { mutableStateOf<Map<Long, TodayCounts>>(emptyMap()) }

    LaunchedEffect(rawDecks, settings) {
        val counts = mutableMapOf<Long, TodayCounts>()
        rawDecks.forEach { deckWithStats ->
            val deckId = deckWithStats.deck.id
            val newCardsStudiedToday = preferencesRepository.getNewCardsStudiedToday(deckId)
                .stateIn(coroutineScope).value

            val (reviewCount, newCount) = repository.getStudyCountsForToday(
                deckId = deckId,
                settings = settings,
                newCardsAlreadyStudied = newCardsStudiedToday
            )
            counts[deckId] = TodayCounts(newCount = newCount, reviewCount = reviewCount)
        }
        todayCountsByDeckId = counts
    }

    val decks = rawDecks
    val importStatus by viewModel.importStatus.collectAsState()
    var showMainMenu by remember { mutableStateOf(false) }
    var showNewDeckDialog by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf<Long?>(null) }
    var fabAnchor by remember { mutableStateOf<FabAnchor?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val adHelper = remember { AdHelper(context) }
    var showAdPrompt by remember { mutableStateOf(false) }
    
    // Pre-load ad
    LaunchedEffect(Unit) {
        adHelper.loadRewardedAd()
    }
    
    // Show import status
    LaunchedEffect(importStatus) {
        when (val status = importStatus) {
            is DeckListViewModel.ImportStatus.Success -> {
                snackbarHostState.showSnackbar(
                    "Imported ${status.cardCount} cards into '${status.deckName}'"
                )
                viewModel.clearImportStatus()
            }
            is DeckListViewModel.ImportStatus.Error -> {
                snackbarHostState.showSnackbar("Import failed: ${status.message}")
                viewModel.clearImportStatus()
            }
            else -> {}
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importApkg(context, it)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            // Sharp-edged top bar with accent underline
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.DarkSurface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BETTER ANKI",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = AppColors.TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                                .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                                .clickable { onSettingsClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = AppColors.DarkBackground
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                items(decks) { deckWithStats ->
                    val today = todayCountsByDeckId[deckWithStats.deck.id] ?: TodayCounts(
                        newCount = deckWithStats.newCards,
                        reviewCount = deckWithStats.dueForReview
                    )
                    DeckCard(
                        deckWithStats = deckWithStats,
                        todayNewCount = today.newCount,
                        todayReviewCount = today.reviewCount,
                        onClick = { onDeckClick(deckWithStats.deck.id) },
                        onAddCard = { showAddCardDialog = deckWithStats.deck.id },
                        onRename = { newName -> viewModel.renameDeck(deckWithStats.deck.id, newName) }
                    )
                }
            }
        }

        // Anchored add-deck menu (sharp, no dimming scrim)
        if (showMainMenu) {
            MainMenuDialog(
                anchor = fabAnchor,
                onDismiss = { showMainMenu = false },
                onNewDeck = {
                    showMainMenu = false
                    showNewDeckDialog = true
                },
                onImportOnline = {
                    showMainMenu = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ankiweb.net/shared/decks"))
                    context.startActivity(intent)
                },
                onImportFile = {
                    showMainMenu = false
                    filePickerLauncher.launch("application/*")
                },
                onDebugSkipDay = {
                    showMainMenu = false
                    onDebugSkipDay()
                },
                debugDayOffset = debugDayOffset
            )
        }
    }
    
    // Floating Action Buttons - Ad button (left) and Add button (right)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Ad button on the left
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AppColors.Primary, RoundedCornerShape(4.dp))
                .clickable { showAdPrompt = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Watch Ad",
                tint = AppColors.DarkBackground,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Add button on the right
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AppColors.Primary, RoundedCornerShape(4.dp))
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val size = coords.size
                    fabAnchor = FabAnchor(
                        leftPx = pos.x,
                        topPx = pos.y,
                        rightPx = pos.x + size.width,
                        bottomPx = pos.y + size.height
                    )
                }
                .clickable { showMainMenu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = AppColors.DarkBackground,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
    
    // New deck dialog
    if (showNewDeckDialog) {
        NewDeckDialog(
            onDismiss = { showNewDeckDialog = false },
            onCreate = { name ->
                viewModel.createDeck(name)
                showNewDeckDialog = false
            }
        )
    }
    
    // Add card dialog
    showAddCardDialog?.let { deckId ->
        AddCardDialog(
            onDismiss = { showAddCardDialog = null },
            onAddManual = { front, back, frontDesc, backDesc, imageUri, showImageFront, showImageBack, example, showExampleFront, showExampleBack ->
                viewModel.addCard(
                    deckId = deckId,
                    front = front,
                    back = back,
                    frontDescription = frontDesc,
                    backDescription = backDesc,
                    imageUri = imageUri,
                    showImageOnFront = showImageFront,
                    showImageOnBack = showImageBack,
                    exampleSentence = example,
                    showExampleOnFront = showExampleFront,
                    showExampleOnBack = showExampleBack
                )
                showAddCardDialog = null
            },
            onAddByPhoto = {
                // Navigate to OCR camera screen for this deck
                showAddCardDialog = null
                onOcrScan(deckId)
            }
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
                                context as Activity,
                                onAdComplete = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Thanks for your support!")
                                        adHelper.loadRewardedAd()
                                    }
                                },
                                onAdFailed = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Ad failed to load")
                                    }
                                }
                            )
                        } else {
                            adHelper.loadRewardedAd(
                                onAdLoaded = {
                                    adHelper.showRewardedAd(
                                        context as Activity,
                                        onAdComplete = {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Thanks for your support!")
                                                adHelper.loadRewardedAd()
                                            }
                                        },
                                        onAdFailed = {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Ad failed to load")
                                            }
                                        }
                                    )
                                },
                                onAdFailed = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Ad not available right now")
                                    }
                                }
                            )
                        }
                    }
                ) { Text("Watch ad") }
            },
            dismissButton = {
                TextButton(onClick = { showAdPrompt = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(2.dp)
        )
    }
}

@Composable
fun DeckCard(
    deckWithStats: DeckWithStats,
    todayNewCount: Int,
    todayReviewCount: Int,
    onClick: () -> Unit,
    onAddCard: () -> Unit,
    onRename: (String) -> Unit = {}
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(deckWithStats.deck.name) }
    
    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { 
                Text(
                    "Rename deck",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Deck Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName)
                        }
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank(),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.DarkBackground
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRenameDialog = false },
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppColors.DarkSurfaceVariant,
                        contentColor = AppColors.TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("Cancel")
                }
            },
            containerColor = AppColors.DarkSurface,
            shape = RoundedCornerShape(2.dp)
        )
    }
    
    // Sharp-edged card with accent border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = AppColors.GradientCard,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = AppColors.Border,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Accent line on left edge
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(AppColors.Primary)
                .align(Alignment.CenterStart)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            // Deck name (clickable to rename) and add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deckWithStats.deck.name.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AppColors.TextPrimary,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            newName = deckWithStats.deck.name
                            showRenameDialog = true 
                        }
                )
                
                // Sharp-edged add button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(AppColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, AppColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .clickable { onAddCard() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Card",
                        tint = AppColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats row with sharp badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(
                    label = "TOTAL",
                    value = deckWithStats.totalCards.toString(),
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "NEW",
                    value = todayNewCount.toString(),
                    color = AppColors.CardNew,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "REVIEW",
                    value = todayReviewCount.toString(),
                    color = AppColors.CardReview,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sharp progress bar
            ProgressBar(deckWithStats)
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun StatBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ProgressBar(deckWithStats: DeckWithStats) {
    val total = deckWithStats.totalCards.toFloat()
    if (total == 0f) return
    
    val newPercent = deckWithStats.newCards / total
    val learningPercent = deckWithStats.learningCards / total
    val reviewPercent = deckWithStats.reviewCards / total
    val masteredPercent = deckWithStats.masteredCards / total
    
    Column {
        // Sharp-edged progress bar with gaps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // New (Neon Green)
            if (newPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(newPercent)
                        .background(AppColors.CardNew, RoundedCornerShape(2.dp))
                )
            }
            // Learning (Amber)
            if (learningPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(learningPercent)
                        .background(AppColors.CardLearning, RoundedCornerShape(2.dp))
                )
            }
            // Review (Indigo)
            if (reviewPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(reviewPercent)
                        .background(AppColors.CardReview, RoundedCornerShape(2.dp))
                )
            }
            // Mastered (Magenta)
            if (masteredPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(masteredPercent)
                        .background(AppColors.CardMastered, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp, 8.dp)
                .background(color, shape = RoundedCornerShape(1.dp))
        )
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = AppColors.TextTertiary
        )
    }
}

@Composable
private fun MainMenuDialog(
    anchor: FabAnchor?,
    onDismiss: () -> Unit,
    onNewDeck: () -> Unit,
    onImportOnline: () -> Unit,
    onImportFile: () -> Unit,
    onDebugSkipDay: () -> Unit = {},
    debugDayOffset: Int = 0
) {
    val density = LocalDensity.current
    var menuHeightPx by remember { mutableIntStateOf(0) }
    val edgePadding = 16.dp
    val gapFromFab = 16.dp

    // Transparent overlay that captures outside taps (no dimming)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        val gapPx = with(density) { gapFromFab.toPx() }
        val topPx = ((anchor?.topPx ?: 0f) - gapPx - menuHeightPx).toInt().coerceAtLeast(0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edgePadding)
                .offset { IntOffset(0, topPx) }
                .onSizeChanged { menuHeightPx = it.height }
                .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                .padding(12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume */ },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Add deck",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )

            MenuActionButton(text = "Create new deck", onClick = onNewDeck)
            MenuActionButton(text = "Download from AnkiWeb", onClick = onImportOnline)
            MenuActionButton(text = "Import .apkg file", onClick = onImportFile)

            Divider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Debug tools",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            Text(
                text = "Current day offset: +$debugDayOffset days",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
            MenuActionButton(
                text = "Skip to next day (+1)",
                onClick = onDebugSkipDay,
                containerColor = AppColors.DarkElevated,
                contentColor = AppColors.TextPrimary
            )
        }
    }
}

@Composable
private fun MenuActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = AppColors.DarkElevated,
    contentColor: Color = AppColors.TextPrimary
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text)
    }
}

@Composable
fun NewDeckDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var deckName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create new deck",
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Enter a name for your new deck:",
                    color = AppColors.TextSecondary
                )
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text("Deck Name") },
                    placeholder = { Text("e.g., Spanish Vocabulary") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (deckName.isNotBlank()) {
                        onCreate(deckName)
                    }
                },
                enabled = deckName.isNotBlank(),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.DarkBackground
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppColors.DarkSurfaceVariant,
                    contentColor = AppColors.TextPrimary
                )
            ) {
                Text("Cancel")
            }
        },
        containerColor = AppColors.DarkSurface,
        shape = RoundedCornerShape(2.dp)
    )
}

@Composable
fun AddCardDialog(
    onDismiss: () -> Unit,
    onAddManual: (String, String, String, String, String?, Boolean, Boolean, String, Boolean, Boolean) -> Unit,
    onAddByPhoto: () -> Unit
) {
    var showManualEntry by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var frontDesc by remember { mutableStateOf("") }
    var backDesc by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var showImageFront by remember { mutableStateOf(false) }
    var showImageBack by remember { mutableStateOf(false) }
    var exampleSentence by remember { mutableStateOf("") }
    var showExampleFront by remember { mutableStateOf(false) }
    var showExampleBack by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri?.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.DarkBackground.copy(alpha = 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center)
                    .background(AppColors.DarkSurfaceVariant, RoundedCornerShape(2.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(2.dp))
                    .padding(12.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume */ },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add card",
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                if (!showManualEntry) {
                    MenuActionButton(text = "Add card manually", onClick = { showManualEntry = true })
                    MenuActionButton(text = "Add card by photo", onClick = onAddByPhoto)
                    MenuActionButton(text = "Cancel", onClick = onDismiss, containerColor = AppColors.DarkElevated)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Front", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = front,
                            onValueChange = { front = it },
                            label = { Text("Main Text") },
                            placeholder = { Text("e.g., Hello") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                        OutlinedTextField(
                            value = frontDesc,
                            onValueChange = { frontDesc = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("Additional context") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 2
                        )

                        Divider(color = AppColors.Divider)

                        Text("Back", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = back,
                            onValueChange = { back = it },
                            label = { Text("Main Text") },
                            placeholder = { Text("e.g., Hola") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                        OutlinedTextField(
                            value = backDesc,
                            onValueChange = { backDesc = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("Additional context") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 2
                        )

                        Divider(color = AppColors.Divider)

                        Text("Image (Optional)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                        MenuActionButton(
                            text = if (imageUri != null) "Change image" else "Select image",
                            onClick = { imagePickerLauncher.launch("image/*") }
                        )
                        if (imageUri != null) {
                            Text("Image selected", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = showImageFront,
                                        onCheckedChange = { showImageFront = it }
                                    )
                                    Text("Show on Front", fontSize = 12.sp, color = AppColors.TextPrimary)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = showImageBack,
                                        onCheckedChange = { showImageBack = it }
                                    )
                                    Text("Show on Back", fontSize = 12.sp, color = AppColors.TextPrimary)
                                }
                            }
                        }

                        Divider(color = AppColors.Divider)

                        Text(
                            "Example Sentence (Optional)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppColors.TextPrimary
                        )
                        OutlinedTextField(
                            value = exampleSentence,
                            onValueChange = { exampleSentence = it },
                            label = { Text("Example") },
                            placeholder = { Text("e.g., Hello, how are you?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                        if (exampleSentence.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = showExampleFront,
                                        onCheckedChange = { showExampleFront = it }
                                    )
                                    Text("Show on Front", fontSize = 12.sp, color = AppColors.TextPrimary)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = showExampleBack,
                                        onCheckedChange = { showExampleBack = it }
                                    )
                                    Text("Show on Back", fontSize = 12.sp, color = AppColors.TextPrimary)
                                }
                            }
                        }
                    }

                    Divider(color = AppColors.Divider)

                    val canAdd = front.isNotBlank() && back.isNotBlank()
                    MenuActionButton(
                        text = "Add",
                        onClick = {
                            if (canAdd) {
                                onAddManual(
                                    front,
                                    back,
                                    frontDesc,
                                    backDesc,
                                    imageUri,
                                    showImageFront,
                                    showImageBack,
                                    exampleSentence,
                                    showExampleFront,
                                    showExampleBack
                                )
                            }
                        },
                        enabled = canAdd
                    )
                    MenuActionButton(text = "Back", onClick = { showManualEntry = false })
                    MenuActionButton(text = "Cancel", onClick = onDismiss)
                }
            }
        }
    }
}
