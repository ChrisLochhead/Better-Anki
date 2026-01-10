package com.betteranki

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.betteranki.data.AnkiDatabase
import com.betteranki.data.DataInitializer
import com.betteranki.data.model.DeckSettings
import com.betteranki.data.model.StudySettings
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.ui.completion.CompletionScreen
import com.betteranki.ui.completion.CompletionViewModel
import com.betteranki.ui.deckdetails.DeckDetailsScreen
import com.betteranki.ui.decklist.DeckListScreen
import com.betteranki.ui.decklist.DeckListViewModel
import com.betteranki.ui.settings.SettingsScreen
import com.betteranki.ui.settings.SettingsViewModel
import java.util.concurrent.TimeUnit
import com.betteranki.ui.study.StudyScreen
import com.betteranki.ui.study.StudyViewModel
import com.betteranki.ui.theme.BetterAnkiTheme
import com.betteranki.ui.ocr.OcrViewModel
import com.betteranki.ui.ocr.OcrViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database and repository
        val database = AnkiDatabase.getDatabase(this)
        val repository = AnkiRepository(
            cardDao = database.cardDao(),
            deckDao = database.deckDao(),
            reviewHistoryDao = database.reviewHistoryDao()
        )
        val preferencesRepository = PreferencesRepository(this)
        
        setContent {
            BetterAnkiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Initialize dummy data
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            DataInitializer(repository, applicationContext).initializeDummyData()
                        }
                    }
                    
                    AnkiApp(repository, preferencesRepository, database, applicationContext)
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object DeckList : Screen("deck_list")
    object Settings : Screen("settings")
    object DeckDetails : Screen("deck_details/{deckId}") {
        fun createRoute(deckId: Long) = "deck_details/$deckId"
    }
    object OcrCamera : Screen("ocr_camera/{deckId}") {
        fun createRoute(deckId: Long) = "ocr_camera/$deckId"
    }
    object OcrCrop : Screen("ocr_crop/{deckId}/{imageUri}/{rotation}") {
        fun createRoute(deckId: Long, imageUri: String, rotation: Int) = "ocr_crop/$deckId/$imageUri/$rotation"
    }
    object OcrPreview : Screen("ocr_preview/{deckId}/{imageUri}/{rotation}/{cropLeft}/{cropTop}/{cropRight}/{cropBottom}") {
        fun createRoute(
            deckId: Long,
            imageUri: String,
            rotation: Int,
            cropLeft: Float,
            cropTop: Float,
            cropRight: Float,
            cropBottom: Float
        ): String {
            // Keep float formatting stable across locales.
            val l = String.format(java.util.Locale.US, "%.6f", cropLeft)
            val t = String.format(java.util.Locale.US, "%.6f", cropTop)
            val r = String.format(java.util.Locale.US, "%.6f", cropRight)
            val b = String.format(java.util.Locale.US, "%.6f", cropBottom)
            return "ocr_preview/$deckId/$imageUri/$rotation/$l/$t/$r/$b"
        }
    }
    object OcrResult : Screen("ocr_result/{deckId}") {
        fun createRoute(deckId: Long) = "ocr_result/$deckId"
    }
    object Study : Screen("study/{deckId}") {
        fun createRoute(deckId: Long) = "study/$deckId"
    }
    object Completion : Screen("completion/{deckId}/{reviewed}/{correct}") {
        fun createRoute(deckId: Long, reviewed: Int, correct: Int) = 
            "completion/$deckId/$reviewed/$correct"
    }
    object AllCards : Screen("all_cards/{deckId}") {
        fun createRoute(deckId: Long) = "all_cards/$deckId"
    }
}

@Composable
fun AnkiApp(
    repository: AnkiRepository,
    preferencesRepository: PreferencesRepository,
    database: AnkiDatabase,
    context: Context
) {
    val navController = rememberNavController()
    val debugDayOffset by preferencesRepository.debugDayOffset.collectAsState(initial = 0)
    val coroutineScope = rememberCoroutineScope()
    
    NavHost(
        navController = navController,
        startDestination = Screen.DeckList.route
    ) {
        composable(
            route = Screen.DeckList.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val viewModel: DeckListViewModel = viewModel(
                factory = DeckListViewModelFactory(repository)
            )
            DeckListScreen(
                viewModel = viewModel,
                preferencesRepository = preferencesRepository,
                repository = repository,
                onDeckClick = { deckId ->
                    navController.navigate(Screen.DeckDetails.createRoute(deckId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onDebugSkipDay = {
                    coroutineScope.launch {
                        preferencesRepository.incrementDebugDay()
                    }
                },
                debugDayOffset = debugDayOffset,
                onOcrScan = { deckId ->
                    navController.navigate(Screen.OcrCamera.createRoute(deckId))
                }
            )
        }
        
        composable(
            route = Screen.DeckDetails.route,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val viewModel: DeckListViewModel = viewModel(
                factory = DeckListViewModelFactory(repository)
            )
            
            // Ensure today's history snapshot is created/updated
            LaunchedEffect(deckId) {
                repository.ensureTodayHistorySnapshot(deckId)
            }
            
            val decks by viewModel.decks.collectAsState()
            val cards by viewModel.getCardsForDeck(deckId).collectAsState(initial = emptyList())
            val reviewHistory by repository.getReviewHistory(deckId).collectAsState(initial = emptyList())
            val settings by preferencesRepository.currentSettings.collectAsState(initial = StudySettings())
            val newCardsStudiedToday by preferencesRepository.getNewCardsStudiedToday(deckId).collectAsState(initial = 0)
            val deckSettings by preferencesRepository.getDeckSettings(deckId).collectAsState(initial = DeckSettings(deckId = deckId))
            val deckWithStats = decks.find { it.deck.id == deckId }
            
            // Calculate actual due count respecting daily limits
            var actualDueCount by remember { mutableIntStateOf(0) }
            var newCardsDueToday by remember { mutableIntStateOf(0) }
            LaunchedEffect(deckId, settings, newCardsStudiedToday, deckSettings) {
                val lastStudied = deckSettings.lastStudiedDate
                val effectiveNow = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(debugDayOffset.toLong())
                actualDueCount = repository.getDueCountForToday(
                    deckId = deckId,
                    settings = settings,
                    newCardsAlreadyStudied = newCardsStudiedToday,
                    lastStudiedDate = lastStudied,
                    currentTimeMillis = effectiveNow
                )
                // Calculate new cards due today
                val remainingNewToday = (settings.dailyNewCards - newCardsStudiedToday).coerceAtLeast(0)
                newCardsDueToday = remainingNewToday.coerceAtMost(deckWithStats?.newCards ?: 0)
            }
            
            if (deckWithStats != null) {
                DeckDetailsScreen(
                    deckWithStats = deckWithStats.copy(dueForReview = actualDueCount),
                    cards = cards,
                    reviewHistory = reviewHistory,
                    deckSettings = deckSettings,
                    newCardsDueToday = newCardsDueToday,
                    onBack = { navController.popBackStack() },
                    onStudy = {
                        navController.navigate(Screen.Study.createRoute(deckId))
                    },
                    onDeleteDeck = {
                        viewModel.deleteDeck(deckId)
                        navController.popBackStack()
                    },
                    onFreezeDeck = { days ->
                        coroutineScope.launch {
                            preferencesRepository.freezeDeck(deckId, days)
                        }
                    },
                    onUnfreezeDeck = {
                        coroutineScope.launch {
                            preferencesRepository.unfreezeDeck(deckId)
                        }
                    },
                    onOcrScan = {
                        navController.navigate(Screen.OcrCamera.createRoute(deckId))
                    },
                    onViewAllCards = {
                        navController.navigate(Screen.AllCards.createRoute(deckId))
                    }
                )
            }
        }
        
        composable(
            route = Screen.AllCards.route,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val viewModel: DeckListViewModel = viewModel(
                factory = DeckListViewModelFactory(repository)
            )
            
            val decks by viewModel.decks.collectAsState()
            val cards by viewModel.getCardsForDeck(deckId).collectAsState(initial = emptyList())
            val deckWithStats = decks.find { it.deck.id == deckId }
            
            if (deckWithStats != null) {
                com.betteranki.ui.allcards.AllCardsScreen(
                    deckName = deckWithStats.deck.name,
                    cards = cards,
                    onBack = { navController.popBackStack() },
                    onUpdateCard = { card ->
                        viewModel.updateCard(card)
                    }
                )
            }
        }
        
        composable(
            route = Screen.Settings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(repository, preferencesRepository, database.settingsPresetDao(), context)
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Study.route,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val viewModel: StudyViewModel = viewModel(
                factory = StudyViewModelFactory(repository, preferencesRepository, deckId)
            )
            StudyScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onComplete = { reviewed, correct ->
                    navController.navigate(
                        Screen.Completion.createRoute(deckId, reviewed, correct)
                    ) {
                        popUpTo(Screen.DeckList.route)
                    }
                }
            )
        }
        
        composable(
            route = Screen.Completion.route,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType },
                navArgument("reviewed") { type = NavType.IntType },
                navArgument("correct") { type = NavType.IntType }
            ),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val reviewed = backStackEntry.arguments?.getInt("reviewed") ?: 0
            val correct = backStackEntry.arguments?.getInt("correct") ?: 0
            
            val viewModel: CompletionViewModel = viewModel(
                factory = CompletionViewModelFactory(repository, deckId, reviewed, correct)
            )
            CompletionScreen(
                viewModel = viewModel,
                preferencesRepository = preferencesRepository,
                onContinue = {
                    navController.navigate(Screen.DeckList.route) {
                        popUpTo(Screen.DeckList.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.OcrCamera.route,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            
            com.betteranki.ui.ocr.OcrCameraScreen(
                onBack = { 
                    navController.popBackStack() 
                },
                onImageCaptured = { uri, rotation, _, _, _, _ ->
                    // Navigate to crop screen with the captured image
                    val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                    navController.navigate(Screen.OcrCrop.createRoute(deckId, encodedUri, rotation))
                },
                onImageSelected = { uri, rotation, _, _, _, _ ->
                    // Navigate to crop screen with the selected image
                    val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                    navController.navigate(Screen.OcrCrop.createRoute(deckId, encodedUri, rotation))
                }
            )
        }
        
        composable(
            route = Screen.OcrCrop.route,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType },
                navArgument("imageUri") { type = NavType.StringType },
                navArgument("rotation") { type = NavType.IntType }
            ),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: return@composable
            val imageUri = android.net.Uri.parse(java.net.URLDecoder.decode(encodedUri, "UTF-8"))
            val rotation = backStackEntry.arguments?.getInt("rotation") ?: android.view.Surface.ROTATION_0

            com.betteranki.ui.ocr.OcrCropScreen(
                imageUri = imageUri,
                captureRotation = rotation,
                onBack = { 
                    navController.popBackStack()
                },
                onCropConfirmed = { cropLeft, cropTop, cropRight, cropBottom ->
                    val encoded = java.net.URLEncoder.encode(imageUri.toString(), "UTF-8")
                    navController.navigate(
                        Screen.OcrPreview.createRoute(deckId, encoded, rotation, cropLeft, cropTop, cropRight, cropBottom)
                    )
                }
            )
        }

        composable(
            route = Screen.OcrPreview.route,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType },
                navArgument("imageUri") { type = NavType.StringType },
                navArgument("rotation") { type = NavType.IntType },
                navArgument("cropLeft") { type = NavType.FloatType },
                navArgument("cropTop") { type = NavType.FloatType },
                navArgument("cropRight") { type = NavType.FloatType },
                navArgument("cropBottom") { type = NavType.FloatType }
            ),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: return@composable
            val imageUri = android.net.Uri.parse(java.net.URLDecoder.decode(encodedUri, "UTF-8"))
            val rotation = backStackEntry.arguments?.getInt("rotation") ?: android.view.Surface.ROTATION_0
            val cropLeft = backStackEntry.arguments?.getFloat("cropLeft") ?: 0f
            val cropTop = backStackEntry.arguments?.getFloat("cropTop") ?: 0f
            val cropRight = backStackEntry.arguments?.getFloat("cropRight") ?: 1f
            val cropBottom = backStackEntry.arguments?.getFloat("cropBottom") ?: 1f

            val viewModel: OcrViewModel = viewModel(
                factory = OcrViewModelFactory(context)
            )
            val ocrState by viewModel.ocrState.collectAsState()
            val previewBitmap by viewModel.previewBitmap.collectAsState()
            val previewState by viewModel.previewState.collectAsState()

            com.betteranki.ui.ocr.OcrPreprocessPreviewScreen(
                imageUri = imageUri,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom,
                previewBitmap = previewBitmap,
                previewState = previewState,
                onPreparePreview = {
                    viewModel.prepareInputPreviewFromUri(imageUri, rotation, cropLeft, cropTop, cropRight, cropBottom)
                },
                onBack = { navController.popBackStack() },
                onRunOcr = { rotationDegrees ->
                    viewModel.processPreparedPreviewBitmap(rotationDegrees)
                }
            )

            LaunchedEffect(ocrState) {
                if (ocrState is com.betteranki.data.model.OcrState.Success) {
                    navController.navigate(Screen.OcrResult.createRoute(deckId)) {
                        popUpTo(Screen.OcrCamera.createRoute(deckId)) { inclusive = true }
                    }
                }
            }
        }
        
        composable(
            route = Screen.OcrResult.route,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
            
            // Create fresh ViewModel for result screen
            val viewModel: OcrViewModel = viewModel(
                factory = OcrViewModelFactory(context)
            )
            
            // Get shared text from companion object
            val recognizedText = OcrViewModel.sharedRecognizedText
            
            if (recognizedText != null) {
                com.betteranki.ui.ocr.OcrResultScreen(
                    deckId = deckId,
                    recognizedText = recognizedText!!.fullText,
                    repository = repository,
                    preferencesRepository = preferencesRepository,
                    onBack = {
                        viewModel.resetState()
                        navController.navigate(Screen.DeckList.route) {
                            popUpTo(Screen.DeckList.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onCardCreated = {
                        viewModel.resetState()
                        navController.navigate(Screen.DeckList.route) {
                            popUpTo(Screen.DeckList.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                // If no text available, go back
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.DeckList.route) {
                        popUpTo(Screen.DeckList.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
