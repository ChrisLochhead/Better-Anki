package com.betteranki.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.betteranki.data.model.Card
import com.betteranki.data.model.Deck
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Helper class to export Anki .apkg files
 */
class ApkgExporter(private val context: Context) {
    
    var onProgress: ((String, String) -> Unit)? = null
    
    data class ExportResult(
        val success: Boolean,
        val outputUri: Uri? = null,
        val error: String? = null
    )
    
    suspend fun exportApkg(deck: Deck, cards: List<Card>, outputUri: Uri): ExportResult {
        var tempDbFile: File? = null
        var database: SQLiteDatabase? = null
        
        try {
            onProgress?.invoke("Creating database...", "")
            
            // Create temporary SQLite database
            tempDbFile = File.createTempFile("export", ".db", context.cacheDir)
            database = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null)
            
            // Create Anki schema
            createAnkiSchema(database)
            
            // Insert deck and cards
            onProgress?.invoke("Exporting cards...", "0 / ${cards.size}")
            insertDeckAndCards(database, deck, cards)
            
            database.close()
            database = null
            
            // Create .apkg ZIP file
            onProgress?.invoke("Creating package...", "")
            createApkgZip(tempDbFile, cards, outputUri)
            
            return ExportResult(success = true, outputUri = outputUri)
            
        } catch (e: Exception) {
            android.util.Log.e("ApkgExporter", "Export failed", e)
            return ExportResult(success = false, error = e.message ?: "Export failed")
        } finally {
            database?.close()
            tempDbFile?.delete()
        }
    }
    
    private fun createAnkiSchema(database: SQLiteDatabase) {
        // Create col table (collection metadata)
        database.execSQL("""
            CREATE TABLE col (
                id INTEGER PRIMARY KEY,
                crt INTEGER NOT NULL,
                mod INTEGER NOT NULL,
                scm INTEGER NOT NULL,
                ver INTEGER NOT NULL,
                dty INTEGER NOT NULL,
                usn INTEGER NOT NULL,
                ls INTEGER NOT NULL,
                conf TEXT NOT NULL,
                models TEXT NOT NULL,
                decks TEXT NOT NULL,
                dconf TEXT NOT NULL,
                tags TEXT NOT NULL
            )
        """)
        
        // Create notes table
        database.execSQL("""
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY,
                guid TEXT NOT NULL,
                mid INTEGER NOT NULL,
                mod INTEGER NOT NULL,
                usn INTEGER NOT NULL,
                tags TEXT NOT NULL,
                flds TEXT NOT NULL,
                sfld TEXT NOT NULL,
                csum INTEGER NOT NULL,
                flags INTEGER NOT NULL,
                data TEXT NOT NULL
            )
        """)
        
        // Create cards table
        database.execSQL("""
            CREATE TABLE cards (
                id INTEGER PRIMARY KEY,
                nid INTEGER NOT NULL,
                did INTEGER NOT NULL,
                ord INTEGER NOT NULL,
                mod INTEGER NOT NULL,
                usn INTEGER NOT NULL,
                type INTEGER NOT NULL,
                queue INTEGER NOT NULL,
                due INTEGER NOT NULL,
                ivl INTEGER NOT NULL,
                factor INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                lapses INTEGER NOT NULL,
                left INTEGER NOT NULL,
                odue INTEGER NOT NULL,
                odid INTEGER NOT NULL,
                flags INTEGER NOT NULL,
                data TEXT NOT NULL
            )
        """)
        
        // Create revlog table (review history)
        database.execSQL("""
            CREATE TABLE revlog (
                id INTEGER PRIMARY KEY,
                cid INTEGER NOT NULL,
                usn INTEGER NOT NULL,
                ease INTEGER NOT NULL,
                ivl INTEGER NOT NULL,
                lastIvl INTEGER NOT NULL,
                factor INTEGER NOT NULL,
                time INTEGER NOT NULL,
                type INTEGER NOT NULL
            )
        """)
        
        // Create graves table (deleted cards)
        database.execSQL("""
            CREATE TABLE graves (
                usn INTEGER NOT NULL,
                oid INTEGER NOT NULL,
                type INTEGER NOT NULL
            )
        """)
    }
    
    private fun insertDeckAndCards(database: SQLiteDatabase, deck: Deck, cards: List<Card>) {
        val timestamp = System.currentTimeMillis()
        val deckId = 1L
        val modelId = 1L
        
        // Insert collection metadata
        val decksJson = """{"$deckId":{"id":$deckId,"name":"${deck.name}","desc":"${deck.description}","mod":$timestamp}}"""
        val modelsJson = """{"$modelId":{"id":$modelId,"name":"Basic","flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}],"tmpls":[{"name":"Card 1","qfmt":"{{Front}}","afmt":"{{Back}}","ord":0}]}}"""
        
        database.execSQL("""
            INSERT INTO col VALUES (
                1, $timestamp, $timestamp, $timestamp, 11, 0, 0, 0,
                '{}', '$modelsJson', '$decksJson', '{}', '{}'
            )
        """)
        
        // Insert cards as notes
        cards.forEachIndexed { index, card ->
            if (index % 100 == 0) {
                onProgress?.invoke("Exporting cards...", "$index / ${cards.size}")
            }
            
            val noteId = index + 1L
            val cardId = index + 1L
            
            // Build fields with media references
            val frontField = buildFieldWithMedia(card.front, card.imageUri, card.audioUri, card.showImageOnFront, card.audioOnFront)
            val backField = buildFieldWithMedia(card.back, card.imageUri, card.audioUri, card.showImageOnBack, card.audioOnBack)
            val fields = "$frontField\u001f$backField"
            
            // Insert note
            database.execSQL("""
                INSERT INTO notes VALUES (
                    $noteId, '${generateGuid()}', $modelId, $timestamp, 0, '', 
                    '${fields.replace("'", "''")}', '${card.front.take(20).replace("'", "''")}', 0, 0, ''
                )
            """)
            
            // Insert card
            database.execSQL("""
                INSERT INTO cards VALUES (
                    $cardId, $noteId, $deckId, 0, $timestamp, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ''
                )
            """)
        }
    }
    
    private fun buildFieldWithMedia(text: String, imageUri: String?, audioUri: String?, showImage: Boolean, showAudio: Boolean): String {
        var result = text
        
        if (showImage && imageUri != null) {
            val fileName = File(imageUri).name
            result += "<br><img src=\"$fileName\">"
        }
        
        if (showAudio && audioUri != null) {
            val fileName = File(audioUri).name
            result += "[sound:$fileName]"
        }
        
        return result
    }
    
    private fun createApkgZip(dbFile: File, cards: List<Card>, outputUri: Uri) {
        val outputStream = context.contentResolver.openOutputStream(outputUri)
            ?: throw Exception("Cannot open output stream")
        
        ZipOutputStream(outputStream).use { zip ->
            // Add database as collection.anki21
            zip.putNextEntry(ZipEntry("collection.anki21"))
            FileInputStream(dbFile).use { it.copyTo(zip) }
            zip.closeEntry()
            
            // Collect all media files with their original filenames
            val mediaMap = mutableMapOf<String, String>()  // index -> filename
            var mediaIndex = 0
            
            cards.forEach { card ->
                card.imageUri?.let { uri ->
                    val file = File(uri)
                    if (file.exists()) {
                        val fileName = file.name
                        mediaMap[mediaIndex.toString()] = fileName
                        
                        // Add media file to zip with numbered name
                        zip.putNextEntry(ZipEntry(mediaIndex.toString()))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                        
                        mediaIndex++
                    }
                }
                card.audioUri?.let { uri ->
                    val file = File(uri)
                    if (file.exists()) {
                        val fileName = file.name
                        mediaMap[mediaIndex.toString()] = fileName
                        
                        // Add media file to zip with numbered name
                        zip.putNextEntry(ZipEntry(mediaIndex.toString()))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                        
                        mediaIndex++
                    }
                }
            }
            
            // Add media JSON map
            val mediaJson = JSONObject(mediaMap as Map<String, Any>)
            zip.putNextEntry(ZipEntry("media"))
            zip.write(mediaJson.toString().toByteArray())
            zip.closeEntry()
        }
    }
    
    private fun generateGuid(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "").take(10)
    }
}
