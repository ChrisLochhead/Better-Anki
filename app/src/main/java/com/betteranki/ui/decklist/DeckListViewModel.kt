package com.betteranki.ui.decklist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.Deck
import com.betteranki.data.model.DeckWithStats
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.util.ApkgImporter
import com.betteranki.util.ApkgExporter
import com.betteranki.util.MediaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeckListViewModel(
    private val repository: AnkiRepository
) : ViewModel() {
    
    val decks: StateFlow<List<DeckWithStats>> = repository.getAllDecksWithStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun getCardsForDeck(deckId: Long): Flow<List<Card>> {
        return repository.getCardsForDeck(deckId)
    }
    
    fun createDeck(name: String, description: String = "") {
        viewModelScope.launch {
            repository.insertDeck(Deck(name = name, description = description))
        }
    }
    
    fun addCard(
        context: Context,
        deckId: Long,
        front: String,
        back: String,
        frontDescription: String = "",
        backDescription: String = "",
        imageUri: String? = null,
        showImageOnFront: Boolean = false,
        showImageOnBack: Boolean = false,
        exampleSentence: String = "",
        showExampleOnFront: Boolean = false,
        showExampleOnBack: Boolean = false,
        audioUri: String? = null,
        audioOnFront: Boolean = false,
        audioOnBack: Boolean = false
    ) {
        viewModelScope.launch {
            addCardSync(
                context = context,
                deckId = deckId,
                front = front,
                back = back,
                frontDescription = frontDescription,
                backDescription = backDescription,
                imageUri = imageUri,
                showImageOnFront = showImageOnFront,
                showImageOnBack = showImageOnBack,
                exampleSentence = exampleSentence,
                showExampleOnFront = showExampleOnFront,
                showExampleOnBack = showExampleOnBack,
                audioUri = audioUri,
                audioOnFront = audioOnFront,
                audioOnBack = audioOnBack
            )
        }
    }

    suspend fun addCardSync(
        context: Context,
        deckId: Long,
        front: String,
        back: String,
        frontDescription: String = "",
        backDescription: String = "",
        imageUri: String? = null,
        showImageOnFront: Boolean = false,
        showImageOnBack: Boolean = false,
        exampleSentence: String = "",
        showExampleOnFront: Boolean = false,
        showExampleOnBack: Boolean = false,
        audioUri: String? = null,
        audioOnFront: Boolean = false,
        audioOnBack: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            var finalImagePath: String? = null
            var finalAudioPath: String? = null
            val mediaFileNames = mutableListOf<String>()

            if (imageUri != null) {
                val copiedFile = copyMediaToDeck(context, Uri.parse(imageUri), deckId, "image")
                if (copiedFile != null) {
                    finalImagePath = copiedFile.absolutePath
                    mediaFileNames.add(copiedFile.name)
                }
            }

            if (audioUri != null) {
                val copiedFile = if (File(audioUri).exists()) {
                    copyFileToMediaDeck(context, File(audioUri), deckId, "audio")
                } else {
                    copyMediaToDeck(context, Uri.parse(audioUri), deckId, "audio")
                }
                if (copiedFile != null) {
                    finalAudioPath = copiedFile.absolutePath
                    mediaFileNames.add(copiedFile.name)
                }
            }

            val hasMedia = finalImagePath != null || finalAudioPath != null

            repository.insertCards(
                listOf(
                    Card(
                        deckId = deckId,
                        front = front,
                        back = back,
                        frontDescription = frontDescription,
                        backDescription = backDescription,
                        imageUri = finalImagePath,
                        showImageOnFront = showImageOnFront,
                        showImageOnBack = showImageOnBack,
                        exampleSentence = exampleSentence,
                        showExampleOnFront = showExampleOnFront,
                        showExampleOnBack = showExampleOnBack,
                        audioUri = finalAudioPath,
                        audioOnFront = audioOnFront,
                        audioOnBack = audioOnBack,
                        hasMediaFiles = hasMedia,
                        mediaFileNames = mediaFileNames.joinToString(","),
                        status = CardStatus.NEW
                    )
                )
            )
        }
    }
    
    private suspend fun copyMediaToDeck(context: Context, uri: Uri, deckId: Long, type: String): File? {
        return try {
            val mediaDir = MediaHelper.getMediaDir(context, deckId)
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            // Get the file extension from the URI
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val extension = when {
                mimeType?.startsWith("image/") == true -> when (mimeType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                mimeType?.startsWith("audio/") == true -> when (mimeType) {
                    "audio/mpeg" -> "mp3"
                    "audio/mp4" -> "m4a"
                    "audio/x-m4a" -> "m4a"
                    "audio/wav" -> "wav"
                    else -> "mp3"
                }
                else -> if (type == "image") "jpg" else "mp3"
            }
            
            // Generate a unique filename
            val filename = "${type}_${UUID.randomUUID()}.${extension}"
            val outputFile = File(mediaDir, filename)
            
            // Copy the file
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun copyFileToMediaDeck(context: Context, sourceFile: File, deckId: Long, type: String): File? {
        return try {
            val mediaDir = MediaHelper.getMediaDir(context, deckId)
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            // Get the file extension
            val extension = sourceFile.extension.ifEmpty { if (type == "image") "jpg" else "mp3" }
            
            // Generate a unique filename
            val filename = "${type}_${UUID.randomUUID()}.${extension}"
            val outputFile = File(mediaDir, filename)
            
            // Copy the file
            sourceFile.inputStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private val _importStatus = MutableStateFlow<ImportStatus?>(null)
    val importStatus: StateFlow<ImportStatus?> = _importStatus.asStateFlow()
    
    fun importApkg(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Loading("Analyzing deck...")
            try {
                val importer = ApkgImporter(context)
                
                // Set up progress callback
                importer.onProgress = { phase, progress ->
                    _importStatus.value = ImportStatus.Loading(phase, progress)
                }
                
                // First pass on IO thread
                _importStatus.value = ImportStatus.Loading("Reading deck structure...")
                val tempResult = withContext(Dispatchers.IO) {
                    importer.importApkg(uri, 0)
                }
                android.util.Log.d("DeckImport", "First pass: success=${tempResult.success}, error=${tempResult.error}, cards=${tempResult.cards.size}")
                
                if (!tempResult.success) {
                    _importStatus.value = ImportStatus.Error(tempResult.error ?: "Unknown error reading deck")
                    return@launch
                }
                
                // Insert the deck
                _importStatus.value = ImportStatus.Loading("Creating deck...")
                val deckId = withContext(Dispatchers.IO) {
                    repository.insertDeck(tempResult.deck)
                }
                android.util.Log.d("DeckImport", "Deck inserted with ID: $deckId")
                
                // Second pass with media extraction
                _importStatus.value = ImportStatus.Loading("Extracting media files...", "This may take a while")
                val finalResult = withContext(Dispatchers.IO) {
                    importer.importApkg(uri, deckId)
                }
                android.util.Log.d("DeckImport", "Second pass: success=${finalResult.success}, error=${finalResult.error}, cards=${finalResult.cards.size}, media=${finalResult.mediaFiles.size}")
                
                if (!finalResult.success) {
                    _importStatus.value = ImportStatus.Error(finalResult.error ?: "Unknown error extracting media")
                    return@launch
                }
                
                // Insert cards
                _importStatus.value = ImportStatus.Loading("Saving cards...", "${finalResult.cards.size} cards")
                withContext(Dispatchers.IO) {
                    val cardsWithDeckId = finalResult.cards.map { it.copy(deckId = deckId) }
                    repository.insertCards(cardsWithDeckId)
                }
                android.util.Log.d("DeckImport", "Cards inserted successfully")
                
                val warningsText = if (finalResult.warnings.isNotEmpty()) {
                    " (${finalResult.warnings.joinToString(", ")})"
                } else ""
                
                _importStatus.value = ImportStatus.Success(
                    finalResult.deck.name, 
                    finalResult.cards.size,
                    finalResult.mediaFiles.size,
                    warningsText
                )
            } catch (e: Exception) {
                android.util.Log.e("DeckImport", "Import failed with exception", e)
                _importStatus.value = ImportStatus.Error(e.message ?: "Import failed")
            }
        }
    }
    
    fun clearImportStatus() {
        _importStatus.value = null
    }
    
    fun deleteDeck(context: Context, deckId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clean up media files first
            MediaHelper.deleteMediaForDeck(context, deckId)
            // Then delete the deck from database
            repository.deleteDeck(deckId)
        }
    }
    
    fun renameDeck(deckId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameDeck(deckId, newName)
        }
    }
    
    fun updateCard(card: Card) {
        viewModelScope.launch {
            repository.updateCard(card)
        }
    }
    
    fun deleteCard(card: Card) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }
    
    // Export status flow
    private val _exportStatus = MutableStateFlow<ExportStatus?>(null)
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus.asStateFlow()
    
    fun exportApkg(context: Context, deckId: Long, outputUri: Uri) {
        viewModelScope.launch {
            try {
                _exportStatus.value = ExportStatus.Loading("Preparing export...", "")
                
                // Get deck with stats
                val deck = withContext(Dispatchers.IO) {
                    repository.getDeckWithStats(deckId)
                } ?: run {
                    _exportStatus.value = ExportStatus.Error("Deck not found")
                    return@launch
                }
                
                // Get all cards
                val cards = withContext(Dispatchers.IO) {
                    repository.getCardsForDeckSync(deckId)
                }
                
                if (cards.isEmpty()) {
                    _exportStatus.value = ExportStatus.Error("No cards to export")
                    return@launch
                }
                
                // Export with progress callback
                val exporter = ApkgExporter(context)
                exporter.onProgress = { phase, progress ->
                    _exportStatus.value = ExportStatus.Loading(phase, progress)
                }
                exporter.exportApkg(
                    deck = deck.deck,
                    cards = cards,
                    outputUri = outputUri
                )
                
                // Count media files
                val mediaCount = cards.count { it.hasMediaFiles }
                _exportStatus.value = ExportStatus.Success(
                    deck.deck.name,
                    cards.size,
                    mediaCount
                )
            } catch (e: Exception) {
                android.util.Log.e("DeckExport", "Export failed with exception", e)
                _exportStatus.value = ExportStatus.Error(e.message ?: "Export failed")
            }
        }
    }
    
    fun clearExportStatus() {
        _exportStatus.value = null
    }
    
    sealed class ExportStatus {
        data class Loading(val phase: String = "Starting...", val progress: String = "") : ExportStatus()
        data class Success(val deckName: String, val cardCount: Int, val mediaCount: Int) : ExportStatus()
        data class Error(val message: String) : ExportStatus()
    }
    
    sealed class ImportStatus {
        data class Loading(val phase: String = "Starting...", val progress: String = "") : ImportStatus()
        data class Success(
            val deckName: String, 
            val cardCount: Int,
            val mediaCount: Int = 0,
            val warnings: String = ""
        ) : ImportStatus()
        data class Error(val message: String) : ImportStatus()
    }
}
