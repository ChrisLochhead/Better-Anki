package com.betteranki.data.model

data class ReviewResult(
    val card: Card,
    val responseTime: Long, // milliseconds
    val correct: Boolean
)

enum class ReviewDifficulty {
    AGAIN,   // < 1 minute
    HARD,    // < 5 minutes
    GOOD,    // < 1 day
    EASY     // < 4 days
}
