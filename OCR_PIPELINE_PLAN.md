# OCR-to-Anki Card Generation Pipeline - Implementation Plan

## Overview
Build an ML-powered pipeline to generate Anki flashcards from photos of text, with translation support and context-aware card generation.

---

## Architecture Decision

### Option A: Google ML Kit (RECOMMENDED)
**Pros:**
- Free, on-device text recognition
- Optimized for mobile (small model size ~10-15MB)
- High accuracy for 100+ languages
- Native Android integration
- No API calls needed for OCR
- Latin, Chinese, Japanese, Korean, Devanagari support

**Cons:**
- Requires Google Play Services
- Less control over model

### Option B: TensorFlow Lite + Custom OCR
**Pros:**
- Full control over model
- No Google dependencies

**Cons:**
- Larger model size (50-100MB+)
- More complex integration
- Need to find/train model
- Slower performance

### Option C: Tesseract Mobile
**Pros:**
- Open source
- No external dependencies

**Cons:**
- Very large (30MB+ per language)
- Slower than ML Kit
- Less accurate on phone photos

**DECISION: Use Google ML Kit for OCR + Google Translate API for translation**

---

## Technical Stack

### Core Technologies
1. **ML Kit Text Recognition V2** - On-device OCR
2. **Google Translate API** or **MLKit Translate** - Translation
3. **CameraX** - Camera integration
4. **Coil** - Image loading/caching (already in project)
5. **Jetpack Compose** - UI (already in project)

### New Dependencies Required
```kotlin
// ML Kit Text Recognition
implementation("com.google.mlkit:text-recognition:16.0.0")

// For Asian languages (optional, if needed)
implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
implementation("com.google.mlkit:text-recognition-korean:16.0.0")

// ML Kit Translation (on-device, limited languages)
implementation("com.google.mlkit:translate:17.0.2")

// OR Google Cloud Translation API (more languages, requires API key)
implementation("com.google.cloud:google-cloud-translate:2.3.0")

// CameraX for camera integration
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Image picking
implementation("androidx.activity:activity-compose:1.8.2") // already have this
```

---

## Implementation Phases

### Phase 1: Core OCR Infrastructure (Week 1)
**Goal:** Extract text from images

#### Files to Create:
1. **`data/ml/TextRecognitionManager.kt`**
   - Initialize ML Kit
   - Process images (bitmap/uri)
   - Handle image preprocessing (rotation, scaling)
   - Return structured text with bounding boxes
   
2. **`data/ml/ImagePreprocessor.kt`**
   - Resize images to optimal size (1024x1024 max)
   - Adjust contrast/brightness
   - Handle rotation (EXIF data)
   - Convert to grayscale if needed

3. **`data/model/RecognizedText.kt`**
   ```kotlin
   data class RecognizedText(
       val fullText: String,
       val blocks: List<TextBlock>
   )
   
   data class TextBlock(
       val text: String,
       val boundingBox: Rect?,
       val confidence: Float,
       val language: String?
   )
   ```

#### Tasks:
- [ ] Add ML Kit dependencies
- [ ] Create TextRecognitionManager
- [ ] Implement image preprocessing
- [ ] Add permissions (CAMERA, READ_EXTERNAL_STORAGE)
- [ ] Write unit tests for preprocessing

---

### Phase 2: Camera & Image Selection (Week 1-2)
**Goal:** Let users capture or select images

#### Files to Create:
1. **`ui/ocr/OcrCameraScreen.kt`**
   - CameraX preview
   - Capture button
   - Flash toggle
   - Gallery picker button
   
2. **`ui/ocr/OcrViewModel.kt`**
   - Handle image capture
   - Trigger OCR processing
   - Manage loading states
   - Store results

#### UI Flow:
```
DeckDetailsScreen → [+ from Photo] button
    ↓
OcrCameraScreen (capture or pick)
    ↓
Processing overlay (ML Kit processing)
    ↓
OcrResultScreen (show transcript)
```

#### Tasks:
- [ ] Add camera permission handling
- [ ] Implement CameraX preview
- [ ] Add image picker integration
- [ ] Create loading/progress UI
- [ ] Handle image capture and pass to OCR

---

### Phase 3: Translation Integration (Week 2)
**Goal:** Translate recognized text

#### Option 3A: ML Kit Translate (On-device, FREE)
**Supported:** ~59 languages
**Size:** ~30MB per language model (downloaded on-demand)
**Limitations:** 
- Fewer languages than Cloud API
- Must download models
- Works offline

#### Option 3B: Google Cloud Translation API (Cloud-based, PAID)
**Supported:** 100+ languages
**Cost:** $20 per 1M characters
**Benefits:**
- More languages
- Better quality
- No model downloads

**RECOMMENDATION: Start with ML Kit Translate, add Cloud API as optional premium feature**

#### Files to Create:
1. **`data/ml/TranslationManager.kt`**
   - Download translation models
   - Translate text (sentence or word)
   - Cache translations
   - Handle offline mode

2. **`data/model/TranslationResult.kt`**
   ```kotlin
   data class TranslationResult(
       val originalText: String,
       val translatedText: String,
       val sourceLang: String,
       val targetLang: String,
       val confidence: Float?
   )
   ```

#### Tasks:
- [ ] Implement TranslationManager
- [ ] Add language selection UI
- [ ] Handle model downloads (progress indicator)
- [ ] Add translation caching (Room database)
- [ ] Create settings for target language preference

---

### Phase 4: Text Selection & Context Extraction (Week 3)
**Goal:** Let users select words/phrases and extract surrounding context

#### Files to Create:
1. **`ui/ocr/OcrResultScreen.kt`**
   - Display full transcript
   - Selectable text (AnnotatedString with selection)
   - Highlight selected text
   - "Generate Card" button
   - "Translate All" button
   
2. **`data/ml/ContextExtractor.kt`**
   ```kotlin
   class ContextExtractor {
       fun extractSentence(text: String, selectionStart: Int, selectionEnd: Int): String
       fun extractSurroundingContext(text: String, word: String, contextSize: Int = 50): String
   }
   ```

#### UI Features:
- Long-press to start selection
- Drag handles to adjust
- Selected text highlighted in blue
- Context sentence highlighted in lighter shade
- Show word count / character count

#### Tasks:
- [ ] Implement text selection with Compose
- [ ] Create context extraction logic
- [ ] Add sentence boundary detection
- [ ] Visual feedback for selection
- [ ] Generate card preview

---

### Phase 5: Card Generation (Week 3-4)
**Goal:** Create Anki cards with context and translation

#### Files to Create:
1. **`ui/ocr/CardGenerationDialog.kt`**
   - Show card preview
   - Front: Selected word/phrase
   - Example: Context sentence (with word highlighted)
   - Back: Translation
   - Allow editing before saving
   - Deck selection

2. **`data/repository/OcrCardGenerator.kt`**
   ```kotlin
   class OcrCardGenerator(
       private val ankiRepository: AnkiRepository,
       private val translationManager: TranslationManager
   ) {
       suspend fun generateCard(
           deckId: Long,
           selectedText: String,
           contextSentence: String,
           translation: String?
       ): Long
   }
   ```

#### Card Format:
```
Front: 
  Word/Phrase (large text)
  
Example:
  "...context sentence with [word] highlighted..."
  
Back:
  Translation (if available)
  Source: OCR (timestamp)
```

#### Tasks:
- [ ] Create card generation logic
- [ ] Design card preview UI
- [ ] Add edit functionality
- [ ] Integrate with existing Card model
- [ ] Add "Source: OCR" metadata field

---

### Phase 6: UI/UX Polish (Week 4)
**Goal:** Make the feature discoverable and delightful

#### Enhancements:
1. **DeckDetailsScreen** - Add "+ From Photo" button next to "+ View All Cards"
2. **Onboarding** - Show tutorial first time
3. **Batch Processing** - Process multiple images
4. **History** - Save OCR sessions
5. **Error Handling** - Poor image quality warnings
6. **Loading States** - Shimmer effects during processing

#### Tasks:
- [ ] Add entry point to existing UI
- [ ] Create tutorial/help dialog
- [ ] Add OCR history screen
- [ ] Implement retry mechanism
- [ ] Add image quality feedback

---

## Database Schema Changes

### New Tables:

#### 1. `ocr_sessions`
```kotlin
@Entity(tableName = "ocr_sessions")
data class OcrSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val imageUri: String,
    val recognizedText: String,
    val timestamp: Long,
    val cardsGenerated: Int = 0
)
```

#### 2. `translations` (Cache)
```kotlin
@Entity(tableName = "translations")
data class TranslationCache(
    @PrimaryKey val id: String, // hash of text + source + target
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long
)
```

#### 3. Update `Card` model to add metadata:
```kotlin
// Add to Card data class
val sourceType: String? = null, // "manual", "ocr", "import"
val sourceImageUri: String? = null,
val contextSentence: String? = null
```

---

## Permissions Required

### AndroidManifest.xml additions:
```xml
<!-- Camera for taking photos -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- Storage for selecting images -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" /> <!-- Android 13+ -->

<!-- Internet for cloud translation (optional) -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Image Processing Pipeline

### Step-by-Step Flow:
```
1. User Input
   ├─ Capture photo (CameraX)
   └─ Select from gallery (Photo picker)
         ↓
2. Preprocessing (ImagePreprocessor)
   ├─ Read EXIF, correct rotation
   ├─ Resize if > 1024x1024
   ├─ Enhance contrast (optional)
   └─ Convert to InputImage
         ↓
3. OCR Processing (ML Kit)
   ├─ Text recognition
   ├─ Language detection
   └─ Extract blocks with confidence
         ↓
4. Display Results (OcrResultScreen)
   ├─ Show full transcript
   ├─ Enable text selection
   └─ Show "Translate" button
         ↓
5. Translation (Optional)
   ├─ Detect source language
   ├─ Translate to target (ML Kit or Cloud)
   └─ Cache result
         ↓
6. Card Generation
   ├─ Extract context sentence
   ├─ Create card with translation
   └─ Save to selected deck
```

---

## Performance Considerations

### 1. Image Size Optimization
- **Target:** 1024x1024 max for ML Kit
- **Strategy:** Downscale before processing
- **Benefit:** Faster processing, less memory

### 2. Model Loading
- **ML Kit:** Models downloaded on first use (~15MB)
- **Strategy:** Show download progress, cache models
- **Offline:** Models work offline after download

### 3. Memory Management
- **Issue:** Processing large images can cause OOM
- **Solution:** 
  - Use BitmapFactory.Options.inSampleSize
  - Release bitmaps after processing
  - Use Glide/Coil for image loading

### 4. Threading
- **OCR:** Run on background thread (Dispatchers.IO)
- **UI:** Update on Main thread
- **Translation:** Background with timeout

---

## Testing Strategy

### Unit Tests:
- [ ] ImagePreprocessor rotation/scaling
- [ ] ContextExtractor sentence boundary detection
- [ ] Translation caching logic
- [ ] Card generation with different inputs

### Integration Tests:
- [ ] OCR with sample images
- [ ] Translation API calls
- [ ] Card insertion into database

### UI Tests:
- [ ] Camera permission flow
- [ ] Image selection flow
- [ ] Text selection interaction
- [ ] Card preview and editing

### Test Assets:
- Sample images with clear text
- Sample images with poor quality
- Multi-language text samples
- Edge cases (rotated, skewed, small text)

---

## Cost Analysis

### ML Kit Text Recognition: **FREE** ✓
### ML Kit Translate: **FREE** ✓
- 59 languages supported
- On-device processing
- ~30MB per language model

### Google Cloud Translation API (Optional Premium):
- $20 per 1M characters
- For 1000 cards with 50 chars average = 50K chars = $1
- Could offer as in-app purchase

---

## User Stories

### Story 1: Student studying Japanese
```
As a student learning Japanese,
I want to take a photo of text in a manga,
So that I can quickly create flashcards with translations
```

### Story 2: Language learner from textbook
```
As a language learner,
I want to select specific vocabulary from a textbook photo,
So that I can build my deck with context sentences
```

### Story 3: Quick capture
```
As a user in a hurry,
I want to photograph a sign or menu,
So that I can learn words later when I have time
```

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Poor OCR accuracy on handwritten text | High | Add quality check, suggest re-capture |
| Large model size | Medium | On-demand download, WiFi suggestion |
| Battery drain from camera | Medium | Optimize preview, add battery warning |
| Translation API costs | Low | Use on-device first, cloud as premium |
| Privacy concerns (images) | High | All on-device, option to delete images |

---

## MVP Scope (4 weeks)

### Must Have:
✅ Photo capture & selection
✅ ML Kit OCR for Latin text
✅ Text display with selection
✅ Context sentence extraction
✅ Card generation with context
✅ ML Kit translation (on-device)
✅ Save to deck

### Should Have:
⏸️ Image quality feedback
⏸️ OCR session history
⏸️ Multiple language model support
⏸️ Batch image processing

### Could Have:
❌ Cloud translation (premium)
❌ Handwriting recognition
❌ Auto-detect language pairs
❌ Export/share OCR results

### Won't Have (V1):
❌ Video OCR
❌ Real-time camera OCR
❌ Custom ML models
❌ PDF processing

---

## Implementation Timeline

### Week 1: Foundation
- Day 1-2: Setup ML Kit, add dependencies
- Day 3-4: Implement TextRecognitionManager
- Day 5: Image preprocessing

### Week 2: Camera & Translation
- Day 1-2: CameraX integration
- Day 3-4: Translation manager
- Day 5: Language settings

### Week 3: Selection & Cards
- Day 1-2: Text selection UI
- Day 3-4: Context extraction
- Day 5: Card generation logic

### Week 4: Polish & Testing
- Day 1-2: UI refinements
- Day 3-4: Testing & bug fixes
- Day 5: Documentation

---

## Success Metrics

### Technical Metrics:
- OCR accuracy > 90% (measured on sample dataset)
- Processing time < 3 seconds per image
- Model size < 20MB total
- Memory usage < 200MB during processing

### User Metrics:
- Time to create card: < 30 seconds (photo → saved card)
- User satisfaction: 4+ stars
- Feature adoption: 30%+ of users try it
- Cards per photo: Average 3-5

---

## Future Enhancements (Post-MVP)

1. **Real-time OCR** - Process text as you point camera
2. **Smart suggestions** - Auto-suggest cards based on frequency
3. **Handwriting support** - Use ML Kit Digital Ink
4. **PDF import** - Extract text from PDF textbooks
5. **Audio pronunciation** - TTS for generated cards
6. **Community templates** - Share card templates
7. **Auto-tagging** - ML-based topic detection
8. **Spaced screenshot** - Remind to capture when you see text

---

## Questions to Resolve

1. **Which languages to prioritize?** 
   - Start with: English, Spanish, French, German, Japanese, Chinese
   
2. **Where to store images?**
   - Option A: Keep in app cache (auto-delete after X days)
   - Option B: Let user choose to save to gallery
   - **Recommendation:** Cache only, delete after card creation
   
3. **Translation model download strategy?**
   - Option A: Download on-demand when user selects language
   - Option B: Pre-download common languages (English, Spanish)
   - **Recommendation:** On-demand with progress indicator
   
4. **Card format customization?**
   - Option A: Fixed format (consistent experience)
   - Option B: Allow template selection
   - **Recommendation:** Fixed format for MVP, templates later

5. **Batch processing priority?**
   - Is single-image-at-a-time enough for MVP?
   - **Recommendation:** Single for MVP, batch in v2

---

## Next Steps

1. **Review this plan** - Discuss and refine scope
2. **Choose translation approach** - On-device vs cloud
3. **Design UI mockups** - Sketch key screens
4. **Create sample images** - For testing
5. **Start Phase 1** - Begin implementation

Would you like me to proceed with implementation, or would you like to discuss/modify any part of this plan?
