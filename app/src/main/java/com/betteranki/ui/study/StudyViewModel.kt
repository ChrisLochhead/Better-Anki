package com.betteranki.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.ReviewResult
import com.betteranki.data.model.StudySettings
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.sync.FirebaseProgressSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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
    private val deckId: Long,
    private val progressSync: FirebaseProgressSync?
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

            val debugOffsetDays = preferencesRepository.debugDayOffset.first()
            val effectiveNow = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(debugOffsetDays.toLong())
            
            val cards = repository.getCardsToStudy(
                deckId = deckId,
                settings = settings,
                newCardsAlreadyStudied = newCardsStudied,
                lastStudiedDate = lastStudiedDate,
                currentTimeMillis = effectiveNow
            )
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
            preferencesRepository.updateLastStudiedDate(deckId, effectiveNow)
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
            val debugOffsetDays = preferencesRepository.debugDayOffset.first()
            val effectiveNow = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(debugOffsetDays.toLong())

            val autoSyncAfterReview = preferencesRepository.autoSyncAfterReview.first()

            // Check if this is the first time seeing this card (it's in unseenNewCardIds)
            val isFirstTimeNewCard = currentState.unseenNewCardIds.contains(card.id)
            
            // Track if this was a new card being studied
            if (isFirstTimeNewCard && correct) {
                preferencesRepository.incrementNewCardsStudied(deckId)
            }

            // Update card scheduling + history and optionally sync in the background.
            // This keeps the UI snappy when advancing to the next card.
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val updatedCard = repository.updateCardAfterReview(
                        ReviewResult(
                            card = card,
                            responseTime = responseTime,
                            correct = correct
                        ),
                        settings,
                        currentTimeMillis = effectiveNow
                    )

                    if (autoSyncAfterReview) {
                        progressSync?.uploadCardProgress(
                            deckId = deckId,
                            card = updatedCard,
                            updatedAtMillis = effectiveNow
                        )
                    }
                }
            }
            
            // Determine if card should be re-queued
            // Re-queue if: failed OR answered but took too long (not confident)
            val responseTimeSeconds = responseTime / 1000
            val shouldRequeue = !correct || 
                (correct && responseTimeSeconds > settings.goodThresholdSeconds)

            // Re-queue within the session (Anki-like): show again soon, not only after finishing everything.
            val requeueGap = 3
            val updatedRemainingCards: List<Card> = if (shouldRequeue) {
                val list = currentState.remainingCards.toMutableList()
                val insertAt = minOf(requeueGap, list.size)
                list.add(insertAt, card)
                list
            } else {
                currentState.remainingCards
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
            
            // Get next card from the (possibly updated) remaining queue
            if (updatedRemainingCards.isNotEmpty()) {
                _state.value = currentState.copy(
                    currentCard = updatedRemainingCards.first(),
                    remainingCards = updatedRemainingCards.drop(1),
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
