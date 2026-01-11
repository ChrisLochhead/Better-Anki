package com.betteranki.sync

import android.content.Context
import com.betteranki.data.dao.CardDao
import com.betteranki.data.dao.DeckDao
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.Deck
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseProgressSync private constructor(
    private val appContext: Context,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun dispose() {
        auth.removeAuthStateListener(authListener)
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        syncAllDecksBidirectional()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        syncAllDecksBidirectional()
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Upload just the scheduling/progress fields for a single card.
     * No deck content/media is uploaded.
     */
    suspend fun uploadCardProgress(deckId: Long, card: Card, updatedAtMillis: Long = System.currentTimeMillis()) {
        val user = auth.currentUser ?: return

        val deck = deckDao.getDeckById(deckId) ?: return
        val deckKey = deckKey(deck)
        val cardKey = cardKey(card)

        val deckDoc = firestore
            .collection("users")
            .document(user.uid)
            .collection("decks")
            .document(deckKey)

        // Keep deck metadata around for human debugging.
        deckDoc.set(
            mapOf(
                "deckName" to deck.name,
                "updatedAt" to updatedAtMillis
            ),
            SetOptions.merge()
        ).await()

        val cardDoc = deckDoc.collection("cards").document(cardKey)
        val payload = mapOf(
            "status" to card.status.name,
            "easeFactor" to card.easeFactor.toDouble(),
            "interval" to card.interval.toLong(),
            "repetitions" to card.repetitions.toLong(),
            "lastReviewed" to card.lastReviewed,
            "nextReviewDate" to card.nextReviewDate,
            "updatedAt" to updatedAtMillis,
            // Optional: helps debugging collisions
            "front" to card.front.take(200),
            "back" to card.back.take(200)
        )

        cardDoc.set(payload, SetOptions.merge()).await()
    }

    /**
     * Full bidirectional sync: merge remote -> local, then upload local progress.
     * Only syncs cards that are not purely-new (or that have any scheduling fields set).
     */
    suspend fun syncAllDecksBidirectional() = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext

        val decks = deckDao.getAllDecksSync()
        for (deck in decks) {
            mergeRemoteDeckIntoLocal(user.uid, deck)
            uploadLocalDeckProgress(user.uid, deck)
        }
    }

    private suspend fun mergeRemoteDeckIntoLocal(uid: String, deck: Deck) {
        val deckKey = deckKey(deck)
        val deckDoc = firestore
            .collection("users")
            .document(uid)
            .collection("decks")
            .document(deckKey)

        val remoteCardsSnapshot = deckDoc.collection("cards").get().await()
        if (remoteCardsSnapshot.isEmpty) return

        val localCards = cardDao.getCardsForDeckSync(deck.id)
        if (localCards.isEmpty()) return

        val localByKey = localCards.associateBy { cardKey(it) }

        for (doc in remoteCardsSnapshot.documents) {
            val local = localByKey[doc.id] ?: continue

            val remoteUpdatedAt = (doc.getLong("updatedAt") ?: 0L)
            val localUpdatedAt = localLocalUpdatedAt(local)

            if (remoteUpdatedAt <= localUpdatedAt) continue

            val statusName = doc.getString("status") ?: continue
            val remoteStatus = runCatching { CardStatus.valueOf(statusName) }.getOrNull() ?: continue

            val remoteEase = (doc.getDouble("easeFactor") ?: local.easeFactor.toDouble()).toFloat()
            val remoteInterval = (doc.getLong("interval") ?: local.interval.toLong()).toInt()
            val remoteReps = (doc.getLong("repetitions") ?: local.repetitions.toLong()).toInt()
            val remoteLastReviewed = doc.getLong("lastReviewed")
            val remoteNextReviewDate = doc.getLong("nextReviewDate")

            val merged = local.copy(
                status = remoteStatus,
                easeFactor = remoteEase,
                interval = remoteInterval,
                repetitions = remoteReps,
                lastReviewed = remoteLastReviewed,
                nextReviewDate = remoteNextReviewDate
            )

            cardDao.updateCard(merged)
        }
    }

    private suspend fun uploadLocalDeckProgress(uid: String, deck: Deck) {
        val cards = cardDao.getCardsForDeckSync(deck.id)
        if (cards.isEmpty()) return

        for (card in cards) {
            if (!shouldSyncCard(card)) continue
            // Use lastReviewed if available so timestamps remain stable.
            val updatedAt = localLocalUpdatedAt(card)
            uploadCardProgress(deck.id, card, updatedAtMillis = updatedAt)
        }
    }

    private fun shouldSyncCard(card: Card): Boolean {
        return card.status != CardStatus.NEW ||
            card.lastReviewed != null ||
            card.nextReviewDate != null ||
            card.repetitions != 0 ||
            card.interval != 0 ||
            card.easeFactor != 2.5f
    }

    private fun localLocalUpdatedAt(card: Card): Long {
        return maxOf(
            card.createdAt,
            card.lastReviewed ?: 0L,
            card.nextReviewDate ?: 0L
        )
    }

    private fun deckKey(deck: Deck): String {
        // Hash to avoid path/length issues and keep ids stable across devices.
        return HashUtils.sha256Hex(HashUtils.normalizeForKey(deck.name))
    }

    private fun cardKey(card: Card): String {
        val front = HashUtils.normalizeForKey(card.front)
        val back = HashUtils.normalizeForKey(card.back)
        return HashUtils.sha256Hex("$front\u0000$back")
    }

    companion object {
        fun createOrNull(context: Context, deckDao: DeckDao, cardDao: CardDao): FirebaseProgressSync? {
            val appContext = context.applicationContext
            if (!FirebaseAvailability.isConfigured(appContext)) return null

            return try {
                FirebaseProgressSync(
                    appContext = appContext,
                    deckDao = deckDao,
                    cardDao = cardDao,
                    auth = FirebaseAuth.getInstance(),
                    firestore = FirebaseFirestore.getInstance()
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
