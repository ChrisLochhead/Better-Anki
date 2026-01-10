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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        showExampleOnBack: Boolean = false
    ) {
        viewModelScope.launch {
            repository.insertCards(
                listOf(
                    Card(
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
                        status = CardStatus.NEW
                    )
                )
            )
        }
    }
    
    private val _importStatus = MutableStateFlow<ImportStatus?>(null)
    val importStatus: StateFlow<ImportStatus?> = _importStatus.asStateFlow()
    
    fun importApkg(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Loading
            try {
                val importer = ApkgImporter(context)
                val result = importer.importApkg(uri)
                
                if (result.success) {
                    repository.importDeck(result.deck.name, result.cards)
                    _importStatus.value = ImportStatus.Success(result.deck.name, result.cards.size)
                } else {
                    _importStatus.value = ImportStatus.Error(result.error ?: "Unknown error")
                }
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.Error(e.message ?: "Import failed")
            }
        }
    }
    
    fun clearImportStatus() {
        _importStatus.value = null
    }
    
    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
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
    
    sealed class ImportStatus {
        object Loading : ImportStatus()
        data class Success(val deckName: String, val cardCount: Int) : ImportStatus()
        data class Error(val message: String) : ImportStatus()
    }
}
