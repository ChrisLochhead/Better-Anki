package com.betteranki.ui.ocr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.model.TranslationState
import com.betteranki.ui.theme.AppColors
import kotlinx.coroutines.launch

data class GeneratedCard(
    val front: String,
    val example: String,
    val back: String = "",
    val frontDescription: String = "",
    val backDescription: String = "",
    val imageUri: String? = null,
    val showImageOnFront: Boolean = true,
    val showImageOnBack: Boolean = true,
    val showExampleOnFront: Boolean = false,
    val showExampleOnBack: Boolean = false,
    val audioUri: String? = null,
    val audioOnFront: Boolean = true,
    val audioOnBack: Boolean = true,
    val isTranslating: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoGenerateCardsDialog(
    fullText: String,
    sourceLanguage: String,
    defaultTargetLanguage: String,
    onDismiss: () -> Unit,
    onGenerate: (List<GeneratedCard>, String, String, ExampleType) -> Unit
) {
    var selectedMode by remember { mutableStateOf(GenerationMode.AUTO) }
    var maxCards by remember { mutableIntStateOf(10) }
    var minWordLength by remember { mutableIntStateOf(4) }
    var exampleType by remember { mutableStateOf(ExampleType.SENTENCE) }
    var selectedSourceLanguage by remember { mutableStateOf(sourceLanguage) }
    var selectedTargetLanguage by remember { mutableStateOf(defaultTargetLanguage) }
    var showSourceLanguageMenu by remember { mutableStateOf(false) }
    var showTargetLanguageMenu by remember { mutableStateOf(false) }
    
    val availableLanguages = mapOf(
        "en" to "English 🇬🇧",
        "es" to "Spanish 🇪🇸",
        "fr" to "French 🇫🇷",
        "de" to "German 🇩🇪",
        "it" to "Italian 🇮🇹",
        "pt" to "Portuguese 🇵🇹",
        "ru" to "Russian 🇷🇺",
        "zh" to "Chinese 🇨🇳",
        "ko" to "Korean 🇰🇷",
        "ja" to "Japanese 🇯🇵",
        "ar" to "Arabic 🇸🇦",
        "hi" to "Hindi 🇮🇳"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AppColors.Secondary
                )
                Text(
                    text = "AUTO-GENERATE CARDS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = AppColors.TextPrimary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Generation mode selection
                    Text(
                        text = "GENERATION MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == GenerationMode.AUTO,
                            onClick = { selectedMode = GenerationMode.AUTO },
                            label = { Text("Auto-Pick") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Secondary,
                                selectedLabelColor = AppColors.TextPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedMode == GenerationMode.MANUAL,
                            onClick = { selectedMode = GenerationMode.MANUAL },
                            label = { Text("Manual") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Secondary,
                                selectedLabelColor = AppColors.TextPrimary
                            )
                        )
                    }
                }
                
                // Auto mode settings
                if (selectedMode == GenerationMode.AUTO) {
                    item {
                        Text(
                            text = "MAX CARDS: $maxCards",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Slider(
                            value = maxCards.toFloat(),
                            onValueChange = { maxCards = it.toInt() },
                            valueRange = 1f..50f,
                            steps = 48,
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.Secondary,
                                activeTrackColor = AppColors.Secondary
                            )
                        )
                    }
                    
                    item {
                        Text(
                            text = "MIN WORD LENGTH: $minWordLength chars",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Slider(
                            value = minWordLength.toFloat(),
                            onValueChange = { minWordLength = it.toInt() },
                            valueRange = 3f..10f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.Secondary,
                                activeTrackColor = AppColors.Secondary
                            )
                        )
                    }
                }
                
                // Example type
                item {
                    Text(
                        text = "EXAMPLE FORMAT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExampleType.values().forEach { type ->
                            FilterChip(
                                selected = exampleType == type,
                                onClick = { exampleType = type },
                                label = { 
                                    Text(
                                        text = when(type) {
                                            ExampleType.SENTENCE -> "Full Sentence (. ! ?)"
                                            ExampleType.PHRASE -> "Phrase (, ; :)"
                                            ExampleType.NONE -> "No Example"
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Secondary,
                                    selectedLabelColor = AppColors.TextPrimary
                                )
                            )
                        }
                    }
                }
                
                // Language selection
                item {
                    Text(
                        text = "TRANSLATE FROM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showSourceLanguageMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.TextPrimary
                            ),
                            border = BorderStroke(1.dp, AppColors.Secondary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = availableLanguages[selectedSourceLanguage] ?: selectedSourceLanguage,
                                fontSize = 14.sp
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSourceLanguageMenu,
                            onDismissRequest = { showSourceLanguageMenu = false },
                            modifier = Modifier.background(AppColors.DarkSurface)
                        ) {
                            availableLanguages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            color = AppColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedSourceLanguage = code
                                        showSourceLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        text = "TRANSLATE TO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showTargetLanguageMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.TextPrimary
                            ),
                            border = BorderStroke(1.dp, AppColors.Secondary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = availableLanguages[selectedTargetLanguage] ?: selectedTargetLanguage,
                                fontSize = 14.sp
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showTargetLanguageMenu,
                            onDismissRequest = { showTargetLanguageMenu = false },
                            modifier = Modifier.background(AppColors.DarkSurface)
                        ) {
                            availableLanguages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            color = AppColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedTargetLanguage = code
                                        showTargetLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cards = if (selectedMode == GenerationMode.AUTO) {
                        extractInterestingWords(fullText, maxCards, minWordLength, exampleType)
                    } else {
                        // Manual mode - will be handled in a different screen
                        emptyList()
                    }
                    onGenerate(cards, selectedSourceLanguage, selectedTargetLanguage, exampleType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.DarkSurfaceVariant,
                    contentColor = AppColors.TextPrimary
                )
            ) {
                Text(
                    text = "GENERATE",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    color = AppColors.TextSecondary
                )
            }
        },
        containerColor = AppColors.DarkBackground
    )
}

enum class GenerationMode {
    AUTO, MANUAL
}

enum class ExampleType {
    SENTENCE, PHRASE, NONE
}

/**
 * Extract interesting words from text based on heuristics:
 * - Length threshold
 * - Exclude common words (articles, prepositions, etc.)
 * - Prefer unique words (no duplicates)
 * - Prefer words with varied characters (not all same letter)
 */
fun extractInterestingWords(
    text: String,
    maxCards: Int,
    minWordLength: Int,
    exampleType: ExampleType
): List<GeneratedCard> {
    // Common words to exclude (basic English - can be expanded)
    val commonWords = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their",
        "what", "so", "up", "out", "if", "about", "who", "get", "which", "go",
        "me", "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could", "them",
        "see", "other", "than", "then", "now", "look", "only", "come", "its", "over",
        "think", "also", "back", "after", "use", "two", "how", "our", "work",
        "first", "well", "way", "even", "new", "want", "because", "any", "these",
        "give", "day", "most", "us", "is", "was", "are", "been", "has", "had",
        "were", "said", "did", "having", "may", "should", "am", "being"
    )
    
    // Split text based on example type
    val segments = when (exampleType) {
        ExampleType.SENTENCE -> text.split(Regex("[.!?]\\s+")).filter { it.isNotBlank() }
        ExampleType.PHRASE -> text.split(Regex("[.,;:]\\s+")).filter { it.isNotBlank() }
        ExampleType.NONE -> listOf(text) // Process entire text but won't use examples
    }
    
    val wordToSegment = mutableMapOf<String, String>()
    val uniqueWords = mutableSetOf<String>()
    
    // Extract words from each segment
    segments.forEach { segment ->
        val words = segment.split(Regex("\\s+"))
        words.forEach { word ->
            val cleanWord = word.replace(Regex("[^\\p{L}'-]"), "").lowercase()
            if (cleanWord.length >= minWordLength && 
                cleanWord !in commonWords && 
                cleanWord !in uniqueWords &&
                cleanWord.any { it.isLetter() } &&
                !cleanWord.all { c -> c == cleanWord.first() } // Not all same character
            ) {
                uniqueWords.add(cleanWord)
                wordToSegment[cleanWord] = segment.trim()
            }
        }
    }
    
    // Sort by length (longer words tend to be more interesting) and take top maxCards
    return uniqueWords
        .sortedByDescending { it.length }
        .take(maxCards)
        .map { word ->
            val example = if (exampleType != ExampleType.NONE) wordToSegment[word] ?: "" else ""
            GeneratedCard(
                front = word,
                example = example,
                showExampleOnFront = example.isNotBlank(),
                showExampleOnBack = example.isNotBlank(),
                back = ""
            )
        }
}
