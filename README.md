# Better Anki

A modern Android flashcard application inspired by Anki, built with Kotlin and Jetpack Compose.

## Features

- **Deck Management**: View all your flashcard decks with detailed statistics
- **Smart Study System**: Swipe-based card review similar to dating apps
  - Swipe right if you know the answer
  - Swipe left if you need to review more
  - Tap to flip the card
- **Intelligent Scheduling**: Automatic difficulty calculation based on response time
  - < 1 minute: Very confident (Easy)
  - < 5 minutes: Good
  - < 1 day: Hard
  - > 1 day: Need review (Again)
- **Progress Tracking**: 
  - Color-coded progress bars showing card status
  - Review completion celebrations
  - Progress charts showing learning over time
- **Local Storage**: All data stored locally using Room database

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room
- **Navigation**: Navigation Compose
- **Coroutines**: For asynchronous operations

## Getting Started

1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on an emulator or physical device (Android 8.0+ / API 26+)

The app comes pre-loaded with a sample Spanish vocabulary deck containing 5 cards for testing.

## Project Structure

```
app/src/main/java/com/betteranki/
├── data/
│   ├── dao/              # Database access objects
│   ├── model/            # Data models
│   ├── repository/       # Repository layer
│   ├── AnkiDatabase.kt   # Room database setup
│   └── DataInitializer.kt # Sample data
├── ui/
│   ├── decklist/         # Deck list screen
│   ├── study/            # Card study screen
│   ├── completion/       # Review completion screen
│   └── theme/            # App theming
└── MainActivity.kt       # Main entry point
```

## Spaced Repetition Algorithm

The app uses a modified version of the SM-2 algorithm:
- Cards start as "New"
- Progress to "Learning" after first review
- Move to "Review" once the interval exceeds 1 day
- Become "Mastered" when reviewed with high confidence

Response time is used to automatically determine difficulty:
- Fast responses (< 1 min) indicate mastery
- Moderate responses (< 5 min) indicate good retention
- Slow responses trigger more frequent reviews

## License

This is a sample project for educational purposes.
