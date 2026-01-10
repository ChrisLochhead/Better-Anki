package com.betteranki.data.repository

import com.betteranki.data.dao.CardDao
import com.betteranki.data.dao.DeckDao
import com.betteranki.data.dao.ReviewHistoryDao
import com.betteranki.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AnkiRepository(
    private val cardDao: CardDao,
    private val deckDao: DeckDao,
    private val reviewHistoryDao: ReviewHistoryDao
) {
    
    fun getAllDecksWithStats(): Flow<List<DeckWithStats>> {
        return combine(
            deckDao.getAllDecks(),
            cardDao.getCardsForDeck(-1) // Get all cards
        ) { decks, _ ->
            decks.map { deck ->
                val currentTime = System.currentTimeMillis()
                DeckWithStats(
                    deck = deck,
                    totalCards = getTotalCards(deck.id),
                    newCards = cardDao.getCardCountByStatus(deck.id, CardStatus.NEW),
                    learningCards = cardDao.getCardCountByStatus(deck.id, CardStatus.LEARNING),
                    reviewCards = cardDao.getCardCountByStatus(deck.id, CardStatus.REVIEW),
                    masteredCards = cardDao.getCardCountByStatus(deck.id, CardStatus.MASTERED),
                    dueForReview = cardDao.getDueCardCount(deck.id, currentTime)
                )
            }
        }
    }
    
    private suspend fun getTotalCards(deckId: Long): Int {
        return cardDao.getCardCountByStatus(deckId, CardStatus.NEW) +
                cardDao.getCardCountByStatus(deckId, CardStatus.LEARNING) +
                cardDao.getCardCountByStatus(deckId, CardStatus.REVIEW) +
                cardDao.getCardCountByStatus(deckId, CardStatus.MASTERED)
    }
    
    suspend fun getDeckWithStats(deckId: Long): DeckWithStats? {
        val deck = deckDao.getDeckById(deckId) ?: return null
        val currentTime = System.currentTimeMillis()
        
        // Ensure today's history snapshot exists
        ensureTodayHistorySnapshot(deckId)
        
        return DeckWithStats(
            deck = deck,
            totalCards = getTotalCards(deck.id),
            newCards = cardDao.getCardCountByStatus(deck.id, CardStatus.NEW),
            learningCards = cardDao.getCardCountByStatus(deck.id, CardStatus.LEARNING),
            reviewCards = cardDao.getCardCountByStatus(deck.id, CardStatus.REVIEW),
            masteredCards = cardDao.getCardCountByStatus(deck.id, CardStatus.MASTERED),
            dueForReview = cardDao.getDueCardCount(deck.id, currentTime)
        )
    }
    
    suspend fun getDueCards(deckId: Long): List<Card> {
        return cardDao.getDueCards(deckId, System.currentTimeMillis())
    }
    
    suspend fun getCardsToStudy(
        deckId: Long, 
        settings: StudySettings, 
        newCardsAlreadyStudied: Int = 0,
        lastStudiedDate: Long? = null
    ): List<Card> {
        val currentTime = System.currentTimeMillis()
        var reviewCards = cardDao.getReviewDueCards(deckId, currentTime)
        
        // Calculate days skipped since last study
        val daysSkipped = if (lastStudiedDate != null) {
            val diffMillis = currentTime - lastStudiedDate
            TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() - 1 // -1 because 1 day gap = 0 skipped
        } else {
            0
        }
        
        // Apply leniency mode caps if enabled and days were skipped
        var newCardsLimit = settings.dailyNewCards
        var reviewCardsLimit = settings.dailyReviewLimit
        
        if (settings.leniencyModeEnabled) {
            // Cap review cards at maxReviewCards
            if (reviewCards.size > settings.maxReviewCards) {
                reviewCards = reviewCards.take(settings.maxReviewCards)
            }
            
            // If days were skipped, cap new cards
            if (daysSkipped > 0 && settings.maxNewCardsAfterSkip < newCardsLimit) {
                newCardsLimit = settings.maxNewCardsAfterSkip
            }
            
            // Apply dailyReviewsAddable cap - limit how many review cards can pile up
            // This prevents review avalanche after skipped days
            val baseReviewsPerDay = settings.dailyReviewLimit / 5 // Baseline daily reviews
            val maxAllowedReviews = baseReviewsPerDay + (settings.dailyReviewsAddable * (daysSkipped + 1))
            if (reviewCards.size > maxAllowedReviews) {
                reviewCards = reviewCards.take(maxAllowedReviews.coerceAtMost(settings.maxReviewCards))
            }
        }
        
        // Apply decay mode - gradually reduce cards after extended inactivity
        if (settings.decayModeEnabled && daysSkipped >= settings.decayStartDays) {
            // Calculate how many days past the decay threshold
            val decayDays = daysSkipped - settings.decayStartDays + 1
            val decayAmount = decayDays * settings.decayRatePerDay
            
            // Calculate decayed new cards limit (minimum is decayMinCards)
            val decayedNewLimit = (settings.dailyNewCards - decayAmount).coerceAtLeast(settings.decayMinCards)
            newCardsLimit = minOf(newCardsLimit, decayedNewLimit)
            
            // Calculate decayed review cards limit
            // Review cards stop decaying when new cards hit the floor OR when reviews hit the floor
            val newCardsHitFloor = decayedNewLimit <= settings.decayMinCards
            val decayedReviewLimit = if (newCardsHitFloor) {
                // New cards hit the floor, stop decaying reviews
                (settings.dailyReviewLimit - decayAmount).coerceAtLeast(settings.decayMinCards)
            } else {
                // Still decaying both
                (settings.dailyReviewLimit - decayAmount).coerceAtLeast(settings.decayMinCards)
            }
            reviewCardsLimit = minOf(reviewCardsLimit, decayedReviewLimit)
            
            // Apply the decayed review limit
            if (reviewCards.size > reviewCardsLimit) {
                reviewCards = reviewCards.take(reviewCardsLimit)
            }
        }
        
        // Only get remaining new cards for today
        val remainingNewCards = (newCardsLimit - newCardsAlreadyStudied).coerceAtLeast(0)
        val newCards = cardDao.getNewCards(deckId, remainingNewCards)
        
        // Interleave new cards with review cards using Anki's algorithm
        // Show review cards first, then interleave new cards at intervals
        // Anki typically shows 1 new card for every 3-4 review cards (or immediately if no reviews)
        return interleaveCards(reviewCards, newCards)
    }
    
    private fun interleaveCards(reviewCards: List<Card>, newCards: List<Card>): List<Card> {
        if (reviewCards.isEmpty()) return newCards
        if (newCards.isEmpty()) return reviewCards
        
        val result = mutableListOf<Card>()
        var reviewIndex = 0
        var newIndex = 0
        
        // Anki's algorithm: show new cards at intervals between reviews
        // Ratio: typically 1 new card per 3-4 review cards (we'll use 3)
        val reviewsBeforeNewCard = 3
        
        while (reviewIndex < reviewCards.size || newIndex < newCards.size) {
            // Add review cards (up to reviewsBeforeNewCard at a time)
            var addedReviews = 0
            while (reviewIndex < reviewCards.size && addedReviews < reviewsBeforeNewCard) {
                result.add(reviewCards[reviewIndex])
                reviewIndex++
                addedReviews++
            }
            
            // Add one new card after the review cards
            if (newIndex < newCards.size && addedReviews > 0) {
                result.add(newCards[newIndex])
                newIndex++
            }
        }
        
        // Add any remaining cards
        while (reviewIndex < reviewCards.size) {
            result.add(reviewCards[reviewIndex])
            reviewIndex++
        }
        while (newIndex < newCards.size) {
            result.add(newCards[newIndex])
            newIndex++
        }
        
        return result
    }
    
    suspend fun getDueCountForToday(
        deckId: Long, 
        settings: StudySettings, 
        newCardsAlreadyStudied: Int = 0,
        lastStudiedDate: Long? = null
    ): Int {
        val (reviewCount, newCount) = getStudyCountsForToday(
            deckId = deckId,
            settings = settings,
            newCardsAlreadyStudied = newCardsAlreadyStudied,
            lastStudiedDate = lastStudiedDate
        )
        return reviewCount + newCount
    }

    /**
     * Returns a pair of counts for the next study session:
     * - first: review cards due (previously seen cards)
     * - second: new cards to introduce (capped by daily limits and already-studied)
     */
    suspend fun getStudyCountsForToday(
        deckId: Long,
        settings: StudySettings,
        newCardsAlreadyStudied: Int = 0,
        lastStudiedDate: Long? = null
    ): Pair<Int, Int> {
        val currentTime = System.currentTimeMillis()
        var reviewCount = cardDao.getReviewDueCards(deckId, currentTime).size

        // Calculate days skipped
        val daysSkipped = if (lastStudiedDate != null) {
            val diffMillis = currentTime - lastStudiedDate
            TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() - 1
        } else {
            0
        }

        var newCardsLimit = settings.dailyNewCards
        var reviewCardsLimit = settings.dailyReviewLimit

        if (settings.leniencyModeEnabled) {
            // Cap review count
            if (reviewCount > settings.maxReviewCards) {
                reviewCount = settings.maxReviewCards
            }

            // Cap new cards after skip
            if (daysSkipped > 0 && settings.maxNewCardsAfterSkip < newCardsLimit) {
                newCardsLimit = settings.maxNewCardsAfterSkip
            }

            // Apply dailyReviewsAddable cap
            val baseReviewsPerDay = settings.dailyReviewLimit / 5
            val maxAllowedReviews = baseReviewsPerDay + (settings.dailyReviewsAddable * (daysSkipped + 1))
            if (reviewCount > maxAllowedReviews) {
                reviewCount = maxAllowedReviews.coerceAtMost(settings.maxReviewCards)
            }
        }

        // Apply decay mode - gradually reduce cards after extended inactivity
        if (settings.decayModeEnabled && daysSkipped >= settings.decayStartDays) {
            val decayDays = daysSkipped - settings.decayStartDays + 1
            val decayAmount = decayDays * settings.decayRatePerDay

            // Calculate decayed new cards limit
            val decayedNewLimit = (settings.dailyNewCards - decayAmount).coerceAtLeast(settings.decayMinCards)
            newCardsLimit = minOf(newCardsLimit, decayedNewLimit)

            // Calculate decayed review cards limit
            val decayedReviewLimit = (settings.dailyReviewLimit - decayAmount).coerceAtLeast(settings.decayMinCards)
            reviewCardsLimit = minOf(reviewCardsLimit, decayedReviewLimit)

            if (reviewCount > reviewCardsLimit) {
                reviewCount = reviewCardsLimit
            }
        }

        val remainingNewCards = (newCardsLimit - newCardsAlreadyStudied).coerceAtLeast(0)
        val newCount = minOf(cardDao.getCardCountByStatus(deckId, CardStatus.NEW), remainingNewCards)

        return reviewCount to newCount
    }
    
    fun getCardsForDeck(deckId: Long): Flow<List<Card>> {
        return cardDao.getCardsForDeck(deckId)
    }
    
    suspend fun updateCardAfterReview(result: ReviewResult, settings: StudySettings) {
        val card = result.card
        val difficulty = calculateDifficulty(result.responseTime, result.correct, settings)
        
        val updatedCard = when (difficulty) {
            ReviewDifficulty.AGAIN -> {
                // Failed - schedule for next day to avoid immediate re-showing in same session
                // Calculate tomorrow at the same time
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val nextDayTime = calendar.timeInMillis
                
                card.copy(
                    status = CardStatus.LEARNING,
                    repetitions = 0,
                    interval = 0,
                    easeFactor = maxOf(1.3f, card.easeFactor - 0.2f),
                    lastReviewed = System.currentTimeMillis(),
                    nextReviewDate = nextDayTime
                )
            }
            ReviewDifficulty.HARD -> {
                // Hard
                val newInterval = if (card.status == CardStatus.NEW) {
                    settings.hardIntervalMinutes
                } else {
                    (card.interval * 1.2).toInt()
                }
                card.copy(
                    status = CardStatus.LEARNING,
                    repetitions = card.repetitions + 1,
                    interval = maxOf(1, newInterval),
                    easeFactor = maxOf(1.3f, card.easeFactor - 0.15f),
                    lastReviewed = System.currentTimeMillis(),
                    nextReviewDate = System.currentTimeMillis() + (newInterval * 60 * 1000L)
                )
            }
            ReviewDifficulty.GOOD -> {
                // Good
                val newInterval = when {
                    card.status == CardStatus.NEW -> settings.goodIntervalMinutes
                    card.interval == 0 -> settings.goodIntervalMinutes
                    else -> (card.interval * card.easeFactor).toInt()
                }
                val newStatus = if (newInterval >= settings.goodIntervalMinutes) CardStatus.REVIEW else CardStatus.LEARNING
                
                card.copy(
                    status = newStatus,
                    repetitions = card.repetitions + 1,
                    interval = newInterval,
                    lastReviewed = System.currentTimeMillis(),
                    nextReviewDate = System.currentTimeMillis() + (newInterval * 60 * 1000L)
                )
            }
            ReviewDifficulty.EASY -> {
                // Easy - mastered
                val newInterval = if (card.interval < settings.goodIntervalMinutes) {
                    settings.easyIntervalMinutes
                } else {
                    card.interval * 2
                }
                card.copy(
                    status = CardStatus.MASTERED,
                    repetitions = card.repetitions + 1,
                    interval = newInterval,
                    easeFactor = card.easeFactor + 0.15f,
                    lastReviewed = System.currentTimeMillis(),
                    nextReviewDate = System.currentTimeMillis() + (newInterval * 60 * 1000L)
                )
            }
        }
        
        cardDao.updateCard(updatedCard)
        updateReviewHistory(card.deckId, updatedCard.status)
    }
    
    private fun calculateDifficulty(responseTime: Long, correct: Boolean, settings: StudySettings): ReviewDifficulty {
        if (!correct) return ReviewDifficulty.AGAIN
        
        val seconds = responseTime / 1000
        return when {
            seconds < settings.easyThresholdSeconds -> ReviewDifficulty.EASY
            seconds < settings.goodThresholdSeconds -> ReviewDifficulty.GOOD
            seconds < settings.hardThresholdSeconds -> ReviewDifficulty.HARD
            else -> ReviewDifficulty.AGAIN // Too long, treat as failed
        }
    }
    
    private suspend fun updateReviewHistory(deckId: Long, newStatus: CardStatus) {
        val today = getTodayTimestamp()
        val existing = reviewHistoryDao.getHistoryForDate(deckId, today)
        
        val newCount = cardDao.getCardCountByStatus(deckId, CardStatus.NEW)
        val learningCount = cardDao.getCardCountByStatus(deckId, CardStatus.LEARNING)
        val reviewCount = cardDao.getCardCountByStatus(deckId, CardStatus.REVIEW)
        val masteredCount = cardDao.getCardCountByStatus(deckId, CardStatus.MASTERED)
        
        if (existing != null) {
            reviewHistoryDao.updateHistory(
                existing.copy(
                    cardsReviewed = existing.cardsReviewed + 1,
                    newCards = newCount,
                    learningCards = learningCount,
                    reviewCards = reviewCount,
                    masteredCards = masteredCount
                )
            )
        } else {
            reviewHistoryDao.insertOrUpdateHistory(
                ReviewHistory(
                    deckId = deckId,
                    date = today,
                    cardsReviewed = 1,
                    newCards = newCount,
                    learningCards = learningCount,
                    reviewCards = reviewCount,
                    masteredCards = masteredCount
                )
            )
        }
    }
    
    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    suspend fun ensureTodayHistorySnapshot(deckId: Long) {
        val today = getTodayTimestamp()
        val existing = reviewHistoryDao.getHistoryForDate(deckId, today)
        
        val newCount = cardDao.getCardCountByStatus(deckId, CardStatus.NEW)
        val learningCount = cardDao.getCardCountByStatus(deckId, CardStatus.LEARNING)
        val reviewCount = cardDao.getCardCountByStatus(deckId, CardStatus.REVIEW)
        val masteredCount = cardDao.getCardCountByStatus(deckId, CardStatus.MASTERED)
        
        if (existing != null) {
            // Update counts without incrementing cardsReviewed
            reviewHistoryDao.updateHistory(
                existing.copy(
                    newCards = newCount,
                    learningCards = learningCount,
                    reviewCards = reviewCount,
                    masteredCards = masteredCount
                )
            )
        } else {
            // Create today's snapshot
            reviewHistoryDao.insertOrUpdateHistory(
                ReviewHistory(
                    deckId = deckId,
                    date = today,
                    cardsReviewed = 0,
                    newCards = newCount,
                    learningCards = learningCount,
                    reviewCards = reviewCount,
                    masteredCards = masteredCount
                )
            )
        }
    }
    
    fun getReviewHistory(deckId: Long): Flow<List<ReviewHistory>> {
        return reviewHistoryDao.getReviewHistory(deckId)
    }
    
    suspend fun insertDeck(deck: Deck): Long {
        return deckDao.insertDeck(deck)
    }
    
    suspend fun insertCards(cards: List<Card>) {
        cardDao.insertCards(cards)
    }
    
    suspend fun updateCard(card: Card) {
        cardDao.updateCard(card)
    }
    
    suspend fun importDeck(deckName: String, cards: List<Card>): Long {
        // Create new deck
        val deckId = deckDao.insertDeck(
            Deck(name = deckName)
        )
        
        // Insert cards with the new deck ID
        val cardsWithDeckId = cards.map { card ->
            card.copy(deckId = deckId)
        }
        cardDao.insertCards(cardsWithDeckId)
        
        // Create initial history snapshot
        ensureTodayHistorySnapshot(deckId)
        
        return deckId
    }
    
    suspend fun deleteDeck(deckId: Long) {
        val deck = deckDao.getDeckById(deckId)
        if (deck != null) {
            deckDao.deleteDeck(deck)
        }
    }
    
    suspend fun renameDeck(deckId: Long, newName: String) {
        val deck = deckDao.getDeckById(deckId)
        if (deck != null) {
            deckDao.updateDeck(deck.copy(name = newName))
        }
    }
}
