package com.betteranki.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val frontDescription: String = "",
    val backDescription: String = "",
    val imageUri: String? = null,
    val showImageOnFront: Boolean = false,
    val showImageOnBack: Boolean = false,
    val exampleSentence: String = "",
    val showExampleOnFront: Boolean = false,
    val showExampleOnBack: Boolean = false,
    val status: CardStatus = CardStatus.NEW,
    val easeFactor: Float = 2.5f,
    val interval: Int = 0, // days
    val repetitions: Int = 0,
    val lastReviewed: Long? = null,
    val nextReviewDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class CardStatus {
    NEW,        // Never seen
    LEARNING,   // Currently being learned
    REVIEW,     // In review rotation
    MASTERED    // Committed to memory
}
