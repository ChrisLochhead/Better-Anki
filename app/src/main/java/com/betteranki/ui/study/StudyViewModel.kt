package com.betteranki.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.ReviewResult
import com.betteranki.data.model.StudySettings
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StudyState(
    val currentCard: Card? = null,
    val remainingCards: List<Card> = emptyList(),
    val failedCards: List<Card> = emptyList(), // Cards to re-show
    val isFlipped: Boolean = false,
    val cardStartTime: Long = 0,
    val reviewedCount: Int = 0,
    val correctCount: Int = 0,
    val isComplete: Boolean = false,
    val currentSettings: StudySettings = StudySettings(),
    // Track separate counts for UI
    val newCardsRemaining: Int = 0,
    val reviewCardsRemaining: Int = 0,
    val doneCount: Int = 0,
    // Track which cards are originally NEW (not yet seen)
    val unseenNewCardIds: Set<Long> = emptySet()
)

class StudyViewModel(
    private val repository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deckId: Long
) : ViewModel() {
    
    private val _state = MutableStateFlow(StudyState())
    val state: StateFlow<StudyState> = _state.asStateFlow()
    
    init {
        loadCards()
    }
    
    private fun loadCards() {
        viewModelScope.launch {
            val settings = preferencesRepository.currentSettings.first()
            val newCardsStudied = preferencesRepository.getNewCardsStudiedToday(deckId).first()
            val deckSettings = preferencesRepository.getDeckSettings(deckId).first()
            val lastStudiedDate = deckSettings.lastStudiedDate
            
            val cards = repository.getCardsToStudy(deckId, settings, newCardsStudied, lastStudiedDate)
            if (cards.isNotEmpty()) {
                // Count new vs review cards
                val newCount = cards.count { it.status == CardStatus.NEW }
                val reviewCount = cards.size - newCount
                val newCardIds = cards.filter { it.status == CardStatus.NEW }.map { it.id }.toSet()
                
                _state.value = StudyState(
                    currentCard = cards.first(),
                    remainingCards = cards.drop(1),
                    cardStartTime = System.currentTimeMillis(),
                    currentSettings = settings,
                    newCardsRemaining = newCount,
                    reviewCardsRemaining = reviewCount,
                    doneCount = 0,
                    unseenNewCardIds = newCardIds
                )
            } else {
                _state.value = StudyState(isComplete = true, currentSettings = settings)
            }
            // Mark that we've studied today and update last studied date
            preferencesRepository.markStudiedToday(deckId)
            preferencesRepository.updateLastStudiedDate(deckId)
        }
    }
    
    fun flipCard() {
        _state.value = _state.value.copy(isFlipped = true)
    }
    
    fun onSwipeRight() {
        handleAnswer(correct = true)
    }
    
    fun onSwipeLeft() {
        handleAnswer(correct = false)
    }
    
    private fun handleAnswer(correct: Boolean) {
        val currentState = _state.value
        val card = currentState.currentCard ?: return
        
        if (!currentState.isFlipped) return // Can't swipe before flipping
        
        val responseTime = System.currentTimeMillis() - currentState.cardStartTime
        val settings = currentState.currentSettings
        
        viewModelScope.launch {
            // Check if this is the first time seeing this card (it's in unseenNewCardIds)
            val isFirstTimeNewCard = currentState.unseenNewCardIds.contains(card.id)
            
            // Track if this was a new card being studied
            if (isFirstTimeNewCard && correct) {
                preferencesRepository.incrementNewCardsStudied(deckId)
            }
            
            // Update card in database
            repository.updateCardAfterReview(
                ReviewResult(
                    card = card,
                    responseTime = responseTime,
                    correct = correct
                ),
                settings
            )
            
            // Determine if card should be re-queued
            // Re-queue if: failed OR answered but took too long (not confident)
            val responseTimeSeconds = responseTime / 1000
            val shouldRequeue = !correct || 
                (correct && responseTimeSeconds > settings.goodThresholdSeconds)
            
            val updatedFailedCards = if (shouldRequeue) {
                currentState.failedCards + card
            } else {
                currentState.failedCards
            }
            
            // Remove from unseenNewCardIds if this is first time seeing it
            val updatedUnseenNewCardIds = if (isFirstTimeNewCard) {
                currentState.unseenNewCardIds - card.id
            } else {
                currentState.unseenNewCardIds
            }
            
            // Update counts
            val newNew: Int
            val newReview: Int
            
            if (isFirstTimeNewCard) {
                // First time seeing this NEW card
                // Always decrement new count
                newNew = currentState.newCardsRemaining - 1
                // If it fails, it becomes a review card (increment review)
                newReview = if (shouldRequeue) {
                    currentState.reviewCardsRemaining + 1
                } else {
                    currentState.reviewCardsRemaining
                }
            } else {
                // Either a review card from the start, or a re-queued card
                newNew = currentState.newCardsRemaining
                // Decrement review only if done (not re-queuing)
                newReview = if (!shouldRequeue) {
                    currentState.reviewCardsRemaining - 1
                } else {
                    currentState.reviewCardsRemaining
                }
            }
            
            // Increase done count only if card is not being re-queued
            val newDone = if (!shouldRequeue) currentState.doneCount + 1 else currentState.doneCount
            
            // Get next card: from remaining first, then from failed cards
            val remaining = currentState.remainingCards
            if (remaining.isNotEmpty()) {
                _state.value = currentState.copy(
                    currentCard = remaining.first(),
                    remainingCards = remaining.drop(1),
                    failedCards = updatedFailedCards,
                    isFlipped = false,
                    cardStartTime = System.currentTimeMillis(),
                    reviewedCount = currentState.reviewedCount + 1,
                    correctCount = if (correct) currentState.correctCount + 1 else currentState.correctCount,
                    newCardsRemaining = newNew,
                    reviewCardsRemaining = newReview,
                    doneCount = newDone,
                    unseenNewCardIds = updatedUnseenNewCardIds
                )
            } else if (updatedFailedCards.isNotEmpty()) {
                // Show failed cards again
                _state.value = currentState.copy(
                    currentCard = updatedFailedCards.first(),
                    remainingCards = updatedFailedCards.drop(1),
                    failedCards = emptyList(),
                    isFlipped = false,
                    cardStartTime = System.currentTimeMillis(),
                    reviewedCount = currentState.reviewedCount + 1,
                    correctCount = if (correct) currentState.correctCount + 1 else currentState.correctCount,
                    newCardsRemaining = newNew,
                    reviewCardsRemaining = newReview,
                    doneCount = newDone,
                    unseenNewCardIds = updatedUnseenNewCardIds
                )
            } else {
                _state.value = currentState.copy(
                    currentCard = null,
                    isComplete = true,
                    reviewedCount = currentState.reviewedCount + 1,
                    correctCount = if (correct) currentState.correctCount + 1 else currentState.correctCount,
                    newCardsRemaining = newNew,
                    reviewCardsRemaining = newReview,
                    doneCount = newDone,
                    unseenNewCardIds = updatedUnseenNewCardIds
                )
            }
        }
    }
}
