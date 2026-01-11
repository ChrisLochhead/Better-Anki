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

    fun getDeckFlow(deckId: Long): Flow<Deck?> {
        return deckDao.getDeckByIdFlow(deckId)
    }

    fun getDeckWithStatsFlow(deckId: Long): Flow<DeckWithStats?> {
        return combine(
            deckDao.getDeckByIdFlow(deckId),
            cardDao.getCardsForDeck(deckId)
        ) { deck, cards ->
            deck ?: return@combine null
            val currentTime = System.currentTimeMillis()

            val newCount = cards.count { it.status == CardStatus.NEW }
            val hardCount = cards.count { it.status == CardStatus.HARD }
            val easyCount = cards.count { it.status == CardStatus.EASY }
            val masteredCount = cards.count { it.status == CardStatus.MASTERED }
            val dueCount = cards.count {
                it.status == CardStatus.NEW ||
                    (it.status != CardStatus.NEW && (it.nextReviewDate == null || it.nextReviewDate <= currentTime))
            }

            DeckWithStats(
                deck = deck,
                totalCards = cards.size,
                newCards = newCount,
                hardCards = hardCount,
                easyCards = easyCount,
                masteredCards = masteredCount,
                dueForReview = dueCount
            )
        }
    }
    
    fun getAllDecksWithStats(): Flow<List<DeckWithStats>> {
        return combine(
            deckDao.getAllDecks(),
            cardDao.getAllCards()
        ) { decks, allCards ->
            val currentTime = System.currentTimeMillis()
            decks.map { deck ->
                val cards = allCards.filter { it.deckId == deck.id }
                val newCount = cards.count { it.status == CardStatus.NEW }
                val hardCount = cards.count { it.status == CardStatus.HARD }
                val easyCount = cards.count { it.status == CardStatus.EASY }
                val masteredCount = cards.count { it.status == CardStatus.MASTERED }
                val dueCount = cards.count {
                    it.status == CardStatus.NEW ||
                        (it.status != CardStatus.NEW && (it.nextReviewDate == null || it.nextReviewDate <= currentTime))
                }

                DeckWithStats(
                    deck = deck,
                    totalCards = cards.size,
                    newCards = newCount,
                    hardCards = hardCount,
                    easyCards = easyCount,
                    masteredCards = masteredCount,
                    dueForReview = dueCount
                )
            }
        }
    }
    
    private suspend fun getTotalCards(deckId: Long): Int {
        return cardDao.getCardCountByStatus(deckId, CardStatus.NEW) +
                cardDao.getCardCountByStatus(deckId, CardStatus.HARD) +
                cardDao.getCardCountByStatus(deckId, CardStatus.EASY) +
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
            hardCards = cardDao.getCardCountByStatus(deck.id, CardStatus.HARD),
            easyCards = cardDao.getCardCountByStatus(deck.id, CardStatus.EASY),
            masteredCards = cardDao.getCardCountByStatus(deck.id, CardStatus.MASTERED),
            dueForReview = cardDao.getDueCardCount(deck.id, currentTime)
        )
    }
    
    suspend fun getDueCards(deckId: Long, currentTimeMillis: Long = System.currentTimeMillis()): List<Card> {
        return cardDao.getDueCards(deckId, currentTimeMillis)
    }
    
    suspend fun getCardsToStudy(
        deckId: Long, 
        settings: StudySettings, 
        newCardsAlreadyStudied: Int = 0,
        lastStudiedDate: Long? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<Card> {
        val currentTime = currentTimeMillis
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
        return interleaveCards(reviewCards, newCards, currentTime)
    }
    
    private fun interleaveCards(reviewCards: List<Card>, newCards: List<Card>, currentTimeMillis: Long): List<Card> {
        if (reviewCards.isEmpty()) return newCards
        if (newCards.isEmpty()) return reviewCards
        
        val result = mutableListOf<Card>()
        var reviewIndex = 0
        var newIndex = 0
        
        // Sort review cards by next review date (closest to needing review first)
        val sortedReviewCards = reviewCards.sortedBy { it.nextReviewDate ?: Long.MAX_VALUE }
        
        // Interleave: prioritize review cards, insert 1 new card after every 2-3 review cards.
        // This ensures review cards are shown early and often, with new cards mixed in.
        var cardsSinceLastNew = 0
        val newCardInterval = 3 // Insert a new card after every 3 cards (if available)
        
        while (reviewIndex < sortedReviewCards.size || newIndex < newCards.size) {
            val canShowNew = newIndex < newCards.size
            val canShowReview = reviewIndex < sortedReviewCards.size
            
            // Time to insert a new card?
            val shouldShowNew = canShowNew && (cardsSinceLastNew >= newCardInterval || !canShowReview)
            
            if (shouldShowNew) {
                result.add(newCards[newIndex])
                newIndex++
                cardsSinceLastNew = 0
            } else if (canShowReview) {
                result.add(sortedReviewCards[reviewIndex])
                reviewIndex++
                cardsSinceLastNew++
            }
        }
        
        return result
    }
    
    suspend fun getDueCountForToday(
        deckId: Long, 
        settings: StudySettings, 
        newCardsAlreadyStudied: Int = 0,
        lastStudiedDate: Long? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Int {
        val (reviewCount, newCount) = getStudyCountsForToday(
            deckId = deckId,
            settings = settings,
            newCardsAlreadyStudied = newCardsAlreadyStudied,
            lastStudiedDate = lastStudiedDate,
            currentTimeMillis = currentTimeMillis
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
        lastStudiedDate: Long? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Pair<Int, Int> {
        val currentTime = currentTimeMillis
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
    
    suspend fun getCardsForDeckSync(deckId: Long): List<Card> {
        return cardDao.getCardsForDeckSync(deckId)
    }
    
    suspend fun updateCardAfterReview(
        result: ReviewResult,
        settings: StudySettings,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Card {
        val card = result.card
        val difficulty = calculateDifficulty(result.responseTime, result.correct, settings)
        
        val updatedCard = when (difficulty) {
            ReviewDifficulty.AGAIN -> {
                // Failed - schedule soon (minutes), not "tomorrow".
                // The UI already re-queues failed cards within the same session.
                val nextTime = currentTimeMillis + (settings.againIntervalMinutes.coerceAtLeast(1) * 60 * 1000L)
                card.copy(
                    status = CardStatus.HARD,
                    repetitions = 0,
                    interval = settings.againIntervalMinutes.coerceAtLeast(1),
                    easeFactor = maxOf(1.3f, card.easeFactor - 0.2f),
                    lastReviewed = currentTimeMillis,
                    nextReviewDate = nextTime
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
                    status = CardStatus.HARD,
                    repetitions = card.repetitions + 1,
                    interval = maxOf(1, newInterval),
                    easeFactor = maxOf(1.3f, card.easeFactor - 0.15f),
                    lastReviewed = currentTimeMillis,
                    nextReviewDate = currentTimeMillis + (newInterval * 60 * 1000L)
                )
            }
            ReviewDifficulty.GOOD -> {
                // Good
                val newInterval = when {
                    card.status == CardStatus.NEW -> settings.goodIntervalMinutes
                    card.interval == 0 -> settings.goodIntervalMinutes
                    else -> (card.interval * card.easeFactor).toInt()
                }
                val newStatus = if (newInterval >= settings.goodIntervalMinutes) CardStatus.EASY else CardStatus.HARD
                
                card.copy(
                    status = newStatus,
                    repetitions = card.repetitions + 1,
                    interval = newInterval,
                    lastReviewed = currentTimeMillis,
                    nextReviewDate = currentTimeMillis + (newInterval * 60 * 1000L)
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
                    lastReviewed = currentTimeMillis,
                    nextReviewDate = currentTimeMillis + (newInterval * 60 * 1000L)
                )
            }
        }
        
        cardDao.updateCard(updatedCard)
        updateReviewHistory(card.deckId, updatedCard.status)

        return updatedCard
    }
    
    private fun calculateDifficulty(responseTime: Long, correct: Boolean, settings: StudySettings): ReviewDifficulty {
        if (!correct) return ReviewDifficulty.AGAIN
        
        val seconds = responseTime / 1000
        return when {
            seconds < settings.easyThresholdSeconds -> ReviewDifficulty.EASY
            seconds < settings.goodThresholdSeconds -> ReviewDifficulty.GOOD
            else -> ReviewDifficulty.HARD
        }
    }
    
    private suspend fun updateReviewHistory(deckId: Long, newStatus: CardStatus) {
        val today = getTodayTimestamp()
        val existing = reviewHistoryDao.getHistoryForDate(deckId, today)
        
        val newCount = cardDao.getCardCountByStatus(deckId, CardStatus.NEW)
        val hardCount = cardDao.getCardCountByStatus(deckId, CardStatus.HARD)
        val easyCount = cardDao.getCardCountByStatus(deckId, CardStatus.EASY)
        val masteredCount = cardDao.getCardCountByStatus(deckId, CardStatus.MASTERED)
        
        if (existing != null) {
            reviewHistoryDao.updateHistory(
                existing.copy(
                    cardsReviewed = existing.cardsReviewed + 1,
                    newCards = newCount,
                    learningCards = hardCount,
                    reviewCards = easyCount,
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
                    learningCards = hardCount,
                    reviewCards = easyCount,
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
        val hardCount = cardDao.getCardCountByStatus(deckId, CardStatus.HARD)
        val easyCount = cardDao.getCardCountByStatus(deckId, CardStatus.EASY)
        val masteredCount = cardDao.getCardCountByStatus(deckId, CardStatus.MASTERED)
        
        if (existing != null) {
            // Update counts without incrementing cardsReviewed
            reviewHistoryDao.updateHistory(
                existing.copy(
                    newCards = newCount,
                    learningCards = hardCount,
                    reviewCards = easyCount,
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
                    learningCards = hardCount,
                    reviewCards = easyCount,
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
    
    suspend fun updateDeck(deck: Deck) {
        deckDao.updateDeck(deck)
    }
    
    suspend fun insertCards(cards: List<Card>) {
        cardDao.insertCards(cards)
    }
    
    suspend fun updateCard(card: Card) {
        cardDao.updateCard(card)
    }
    
    suspend fun deleteCard(card: Card) {
        cardDao.deleteCard(card)
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
