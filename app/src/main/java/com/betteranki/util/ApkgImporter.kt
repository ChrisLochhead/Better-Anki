package com.betteranki.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import com.betteranki.data.model.Deck
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Helper class to import Anki .apkg files
 * .apkg files are ZIP archives containing a SQLite database
 */
class ApkgImporter(private val context: Context) {
    
    // Callback for progress updates
    var onProgress: ((String, String) -> Unit)? = null
    
    data class ImportResult(
        val deck: Deck,
        val cards: List<Card>,
        val mediaFiles: List<ImportedMedia> = emptyList(),
        val success: Boolean = true,
        val error: String? = null,
        val warnings: List<String> = emptyList()
    )
    
    data class ImportedMedia(
        val originalName: String,
        val localPath: String,
        val type: MediaType
    )
    
    enum class MediaType {
        IMAGE, AUDIO, VIDEO, OTHER
    }
    
    suspend fun importApkg(uri: Uri, deckId: Long = 0): ImportResult {
        var tempDbFile: File? = null
        var database: SQLiteDatabase? = null
        val warnings = mutableListOf<String>()
        
        try {
            // Extract everything in one pass through the ZIP file
            val extractResult = extractAllFromZip(uri, deckId)
            
            if (extractResult.dbFile == null || !extractResult.dbFile.exists()) {
                return ImportResult(
                    deck = Deck(name = ""),
                    cards = emptyList(),
                    success = false,
                    error = "Failed to extract database from .apkg file"
                )
            }
            
            tempDbFile = extractResult.dbFile
            
            // Open the SQLite database
            database = SQLiteDatabase.openDatabase(
                tempDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            // Parse the deck and cards with media references
            val deckName = extractDeckName(database)
            val cards = extractCards(database, extractResult.mediaFiles)
            
            if (cards.isEmpty()) {
                return ImportResult(
                    deck = Deck(name = deckName),
                    cards = emptyList(),
                    success = false,
                    error = "No cards found in .apkg file"
                )
            }
            
            if (extractResult.mediaFiles.isNotEmpty()) {
                warnings.add("Imported ${extractResult.mediaFiles.size} media files")
            }
            
            return ImportResult(
                deck = Deck(name = deckName),
                cards = cards,
                mediaFiles = extractResult.mediaFiles,
                warnings = warnings
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
    
    private data class ZipExtractResult(
        val dbFile: File?,
        val mediaFiles: List<ImportedMedia>,
        val mediaMap: Map<String, String>
    )
    
    /**
     * Extract all contents from the .apkg ZIP file in a single pass
     */
    private fun extractAllFromZip(uri: Uri, deckId: Long): ZipExtractResult {
        var dbFile: File? = null
        val mediaFiles = mutableListOf<ImportedMedia>()
        var mediaMap = emptyMap<String, String>()
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ZipExtractResult(null, emptyList(), emptyMap())
            val zipInputStream = ZipInputStream(inputStream)
            
            // Create deck-specific media folder
            val mediaDir = File(context.filesDir, "media/deck_$deckId")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                val entryName = entry.name
                
                when {
                    // Extract media map JSON
                    entryName == "media" -> {
                        // Read JSON without closing the stream
                        val jsonStr = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        val jsonObj = JSONObject(jsonStr)
                        val map = mutableMapOf<String, String>()
                        jsonObj.keys().forEach { key ->
                            map[key] = jsonObj.getString(key)
                        }
                        mediaMap = map
                    }
                    
                    // Extract collection database - prefer anki21 over anki2
                    // anki21 is the newer format (Anki 2.1+), anki2 might be a stub
                    entryName == "collection.anki21" || entryName == "collection.anki2" -> {
                        // Only extract if we haven't found anki21 yet, or this IS anki21
                        val isAnki21 = entryName == "collection.anki21"
                        val currentIsAnki21 = dbFile?.name?.contains("anki21") == true
                        
                        if (dbFile == null || (isAnki21 && !currentIsAnki21)) {
                            // Delete old db file if we're replacing it
                            dbFile?.delete()
                            
                            val suffix = if (isAnki21) "_anki21.db" else "_anki2.db"
                            dbFile = File.createTempFile("anki", suffix, context.cacheDir)
                            FileOutputStream(dbFile).use { output ->
                                zipInputStream.copyTo(output)
                            }
                            android.util.Log.d("ApkgImporter", "Extracted database: $entryName")
                        }
                    }
                    
                    // Extract media files (numbered: 0, 1, 2, etc.)
                    entryName.matches(Regex("^\\d+$")) -> {
                        // This is a numbered media file - store temporarily with the entry number
                        // We'll rename it after we have the media map
                        val tempFile = File(context.cacheDir, "media_$entryName.tmp")
                        FileOutputStream(tempFile).use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                }
                
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            
            zipInputStream.close()
            
            // Debug logging
            android.util.Log.d("ApkgImporter", "Media map size: ${mediaMap.size}")
            android.util.Log.d("ApkgImporter", "Database file: ${dbFile?.absolutePath}")
            
            // Now process the media files with the map we extracted
            val tempMediaFiles = context.cacheDir.listFiles()?.filter { it.name.startsWith("media_") } ?: emptyList()
            android.util.Log.d("ApkgImporter", "Found ${tempMediaFiles.size} temp media files")
            
            tempMediaFiles.forEach { tempFile ->
                val number = tempFile.name.removePrefix("media_").removeSuffix(".tmp")
                android.util.Log.d("ApkgImporter", "Processing temp file: ${tempFile.name}, number: $number")
                
                if (mediaMap.containsKey(number)) {
                    val originalName = mediaMap[number]!!
                    val outputFile = File(mediaDir, originalName)
                    
                    android.util.Log.d("ApkgImporter", "Mapping $number -> $originalName")
                    
                    // Move temp file to final location
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                    
                    // Determine media type
                    val mediaType = when (originalName.substringAfterLast('.').lowercase()) {
                        "jpg", "jpeg", "png", "gif", "svg", "webp" -> MediaType.IMAGE
                        "mp3", "wav", "ogg", "flac", "opus", "m4a" -> MediaType.AUDIO
                        "mp4", "webm", "ogv", "mov" -> MediaType.VIDEO
                        else -> MediaType.OTHER
                    }
                    
                    mediaFiles.add(
                        ImportedMedia(
                            originalName = originalName,
                            localPath = outputFile.absolutePath,
                            type = mediaType
                        )
                    )
                } else {
                    android.util.Log.w("ApkgImporter", "No mapping found for $number, deleting temp file")
                    tempFile.delete()
                }
            }
            
            android.util.Log.d("ApkgImporter", "Final media files count: ${mediaFiles.size}")
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return ZipExtractResult(dbFile, mediaFiles, mediaMap)
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
    
    private fun extractCards(database: SQLiteDatabase, mediaFiles: List<ImportedMedia>): List<Card> {
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
            
            val totalCards = cursor.count
            android.util.Log.d("ApkgImporter", "Card cursor count: $totalCards")
            onProgress?.invoke("Processing cards...", "0 / $totalCards")
            
            while (cursor.moveToNext()) {
                // Report progress every 100 cards
                if (cards.size % 100 == 0) {
                    onProgress?.invoke("Processing cards...", "${cards.size} / $totalCards")
                }
                
                // Fields are stored as a single string separated by \x1f character
                val fields = cursor.getString(0)
                val ord = cursor.getInt(1)
                val parts = fields.split("\u001f")
                
                // Debug first few cards
                if (cards.size < 3) {
                    android.util.Log.d("ApkgImporter", "Card ${cards.size}: ${parts.size} fields, ord=$ord")
                    parts.forEachIndexed { i, p -> 
                        android.util.Log.d("ApkgImporter", "  Field $i: ${p.take(50)}...")
                    }
                }
                
                // Find the first two fields that contain actual text content (not just media/numbers)
                val textFieldIndices = findTextFields(parts)
                if (textFieldIndices.size < 2) {
                    // Not enough text fields, skip this card
                    continue
                }
                
                val frontIdx = textFieldIndices[0]
                val backIdx = textFieldIndices[1]
                
                val frontRaw = parts.getOrNull(frontIdx) ?: ""
                val backRaw = parts.getOrNull(backIdx) ?: ""
                
                // Also collect media from other fields
                val allFieldsForMedia = parts.joinToString(" ")
                
                // Parse media from all fields (to capture images/audio from any field)
                val allMediaParsed = parseCardContent(allFieldsForMedia, mediaFiles)
                
                // Parse front and back for display
                val frontParsed = parseCardContent(frontRaw, mediaFiles)
                val backParsed = parseCardContent(backRaw, mediaFiles)
                
                val front = frontParsed.displayText
                val back = backParsed.displayText
                
                // Only add cards with both front and back content
                if (front.isNotBlank() && back.isNotBlank()) {
                    // Use media from all fields, not just front/back
                    val imageUri = allMediaParsed.imageUri ?: frontParsed.imageUri ?: backParsed.imageUri
                    val audioUri = allMediaParsed.audioUri ?: frontParsed.audioUri ?: backParsed.audioUri
                    val allMedia = allMediaParsed.allMediaFiles.distinct()
                    
                    cards.add(
                        Card(
                            deckId = 0, // Will be set when inserting
                            front = front,
                            back = back,
                            imageUri = imageUri,
                            showImageOnFront = frontParsed.imageUri != null,
                            showImageOnBack = backParsed.imageUri != null || (imageUri != null && frontParsed.imageUri == null),
                            audioUri = audioUri,
                            audioOnFront = frontParsed.audioUri != null,
                            audioOnBack = backParsed.audioUri != null || (audioUri != null && frontParsed.audioUri == null),
                            hasMediaFiles = allMedia.isNotEmpty(),
                            mediaFileNames = allMedia.joinToString(","),
                            status = CardStatus.NEW
                        )
                    )
                }
            }
            
            android.util.Log.d("ApkgImporter", "Total cards extracted: ${cards.size}")
            cursor.close()
        } catch (e: Exception) {
            android.util.Log.e("ApkgImporter", "Error extracting cards", e)
            e.printStackTrace()
        }
        
        return cards
    }
    
    /**
     * Find field indices that contain actual text content (not just media, numbers, or empty)
     */
    private fun findTextFields(parts: List<String>): List<Int> {
        val textFieldIndices = mutableListOf<Int>()
        
        for ((index, field) in parts.withIndex()) {
            val stripped = field.stripHtml().trim()
            
            // Skip empty fields
            if (stripped.isBlank()) continue
            
            // Skip fields that are just numbers (sort order, IDs)
            if (stripped.matches(Regex("^\\d+$"))) continue
            
            // This field has actual text content
            textFieldIndices.add(index)
            
            // We only need the first two text fields
            if (textFieldIndices.size >= 2) break
        }
        
        return textFieldIndices
    }
    
    private data class ParsedCardContent(
        val displayText: String,
        val imageUri: String?,
        val audioUri: String?,
        val allMediaFiles: List<String>
    )
    
    private fun parseCardContent(html: String, mediaFiles: List<ImportedMedia>): ParsedCardContent {
        var imageUri: String? = null
        var audioUri: String? = null
        val referencedMedia = mutableListOf<String>()
        
        // Extract image tags: <img src="filename.jpg">
        val imgRegex = """<img[^>]+src=["']([^"']+)["']""".toRegex()
        imgRegex.findAll(html).forEach { match ->
            val filename = match.groupValues[1]
            referencedMedia.add(filename)
            if (imageUri == null) {
                // Find corresponding local path
                imageUri = mediaFiles.find { it.originalName == filename }?.localPath
            }
        }
        
        // Extract sound tags: [sound:filename.mp3]
        val soundRegex = """\[sound:([^\]]+)\]""".toRegex()
        soundRegex.findAll(html).forEach { match ->
            val filename = match.groupValues[1]
            referencedMedia.add(filename)
            if (audioUri == null) {
                // Find corresponding local path
                audioUri = mediaFiles.find { it.originalName == filename }?.localPath
            }
        }
        
        // Strip HTML for display text
        val displayText = html.stripHtml()
        
        return ParsedCardContent(
            displayText = displayText,
            imageUri = imageUri,
            audioUri = audioUri,
            allMediaFiles = referencedMedia
        )
    }
    
    // Simple HTML tag stripper
    private fun String.stripHtml(): String {
        return this
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("""<[^>]*>""".toRegex(), "")
            .replace("""\[sound:[^\]]+\]""".toRegex(), "")  // Remove sound tags
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }
}
