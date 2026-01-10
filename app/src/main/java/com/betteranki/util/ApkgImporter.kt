package com.betteranki.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.Deck
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Helper class to import Anki .apkg files
 * .apkg files are ZIP archives containing a SQLite database
 */
class ApkgImporter(private val context: Context) {
    
    data class ImportResult(
        val deck: Deck,
        val cards: List<Card>,
        val success: Boolean = true,
        val error: String? = null
    )
    
    suspend fun importApkg(uri: Uri): ImportResult {
        var tempDbFile: File? = null
        var database: SQLiteDatabase? = null
        
        try {
            // Extract the SQLite database from the ZIP
            tempDbFile = extractDatabase(uri)
            if (tempDbFile == null || !tempDbFile.exists()) {
                return ImportResult(
                    deck = Deck(name = ""),
                    cards = emptyList(),
                    success = false,
                    error = "Failed to extract database from .apkg file"
                )
            }
            
            // Open the SQLite database
            database = SQLiteDatabase.openDatabase(
                tempDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            // Parse the deck and cards
            val deckName = extractDeckName(database)
            val cards = extractCards(database)
            
            if (cards.isEmpty()) {
                return ImportResult(
                    deck = Deck(name = deckName),
                    cards = emptyList(),
                    success = false,
                    error = "No cards found in .apkg file"
                )
            }
            
            return ImportResult(
                deck = Deck(name = deckName),
                cards = cards
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(
                deck = Deck(name = ""),
                cards = emptyList(),
                success = false,
                error = "Error importing .apkg: ${e.message}"
            )
        } finally {
            database?.close()
            tempDbFile?.delete()
        }
    }
    
    private fun extractDatabase(uri: Uri): File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zipInputStream = ZipInputStream(inputStream)
            val tempFile = File.createTempFile("anki", ".db", context.cacheDir)
            
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                // Look for the collection.anki2 or collection.anki21 database
                if (entry.name.contains("collection")) {
                    val outputStream = FileOutputStream(tempFile)
                    zipInputStream.copyTo(outputStream)
                    outputStream.close()
                    zipInputStream.closeEntry()
                    zipInputStream.close()
                    return tempFile
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            
            zipInputStream.close()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun extractDeckName(database: SQLiteDatabase): String {
        return try {
            // Get the decks JSON from col table
            val cursor = database.rawQuery(
                "SELECT decks FROM col LIMIT 1",
                null
            )
            
            if (cursor.moveToFirst()) {
                val decksJson = cursor.getString(0)
                cursor.close()
                
                // Parse JSON to find the first non-default deck name
                // Format: {"deck_id": {"name": "Deck Name", ...}, ...}
                // Look for "name" field in the JSON
                val nameMatches = """"name"\s*:\s*"([^"]+)"""".toRegex().findAll(decksJson)
                
                // Find the first deck that's not "Default"
                for (match in nameMatches) {
                    val deckName = match.groupValues[1]
                    if (deckName != "Default") {
                        return deckName
                    }
                }
                
                // If only Default exists, use it
                nameMatches.firstOrNull()?.groupValues?.get(1) ?: "Imported Deck"
            } else {
                cursor.close()
                "Imported Deck"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Imported Deck"
        }
    }
    
    private fun extractCards(database: SQLiteDatabase): List<Card> {
        val cards = mutableListOf<Card>()
        
        try {
            // Anki stores cards in 'cards' table and note content in 'notes' table
            // Join them to get the actual card text
            val cursor = database.rawQuery(
                """
                SELECT n.flds, c.ord
                FROM cards c 
                JOIN notes n ON c.nid = n.id
                ORDER BY c.id
                """,
                null
            )
            
            while (cursor.moveToNext()) {
                // Fields are stored as a single string separated by \x1f character
                val fields = cursor.getString(0)
                val ord = cursor.getInt(1)
                val parts = fields.split("\u001f")
                
                // For basic cards (ord 0): front is field 0, back is field 1
                // For multiple card types, ord determines which fields to use
                // Most basic decks have at least 2 fields
                if (parts.size >= 2) {
                    val front = parts.getOrNull(0)?.stripHtml() ?: ""
                    val back = parts.getOrNull(1)?.stripHtml() ?: ""
                    
                    // Only add cards with both front and back content
                    if (front.isNotBlank() && back.isNotBlank()) {
                        cards.add(
                            Card(
                                deckId = 0, // Will be set when inserting
                                front = front,
                                back = back,
                                status = CardStatus.NEW
                            )
                        )
                    }
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return cards
    }
    
    // Simple HTML tag stripper
    private fun String.stripHtml(): String {
        return this
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("""<[^>]*>""".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }
}
