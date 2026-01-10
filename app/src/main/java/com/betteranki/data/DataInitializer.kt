package com.betteranki.data

import android.content.Context
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.Deck
import com.betteranki.data.repository.AnkiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataInitializer(
    private val repository: AnkiRepository,
    private val context: Context
) {
    suspend fun initializeDummyData() = withContext(Dispatchers.IO) {
        // Check if we already have data
        val db = AnkiDatabase.getDatabase(context)
        val deckCount = db.deckDao().getDeckCount()
        
        if (deckCount == 0) {
            // Create a dummy deck
            val deckId = repository.insertDeck(
                Deck(
                    name = "Spanish Vocabulary",
                    description = "Basic Spanish words for beginners"
                )
            )
            
            // Create 5 sample cards
            val cards = listOf(
                Card(
                    deckId = deckId,
                    front = "Hello",
                    back = "Hola",
                    status = CardStatus.NEW
                ),
                Card(
                    deckId = deckId,
                    front = "Goodbye",
                    back = "Adiós",
                    status = CardStatus.NEW
                ),
                Card(
                    deckId = deckId,
                    front = "Thank you",
                    back = "Gracias",
                    status = CardStatus.NEW
                ),
                Card(
                    deckId = deckId,
                    front = "Please",
                    back = "Por favor",
                    status = CardStatus.NEW
                ),
                Card(
                    deckId = deckId,
                    front = "Good morning",
                    back = "Buenos días",
                    status = CardStatus.NEW
                )
            )
            
            repository.insertCards(cards)
        }
    }
}
