package com.betteranki.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Bold, professional dark theme with vibrant accents
object AppColors {
    // Deep dark backgrounds - almost black with subtle blue tint
    val DarkBackground = Color(0xFF05080D)
    val DarkSurface = Color(0xFF0D1117)
    val DarkSurfaceVariant = Color(0xFF161B22)
    val DarkElevated = Color(0xFF1C2128)
    
    // Primary accent - Dark professional blue
    val Primary = Color(0xFF2B6CB0)
    val PrimaryDim = Color(0xFF1F4D7A)
    val PrimaryGlow = Color(0xFF2B6CB0)
    
    // Secondary accent - Muted slate-blue (keeps UI calm, still distinct)
    val Secondary = Color(0xFF3A4B63)
    val SecondaryDim = Color(0xFF2B3A4F)
    
    // Card status colors - Crimson to light blue gradient
    val CardNew = Color(0xFFB01030)           // Dark crimson red
    val CardNewDim = Color(0xFF8A0C24)
    val CardHard = Color(0xFF8B4789)          // Purple-red (interpolation)
    val CardHardDim = Color(0xFF6D3569)
    val CardEasy = Color(0xFF4C8FD8)          // Blue-purple (interpolation)
    val CardEasyDim = Color(0xFF3B74B0)
    val CardMastered = Color(0xFF87CEEB)      // Sky blue (lighter than before)
    val CardMasteredDim = Color(0xFF6BA5C8)
    
    // Legacy aliases for backward compatibility (map to new statuses)
    val CardLearning = CardHard
    val CardLearningDim = CardHardDim
    val CardReview = CardEasy
    val CardReviewDim = CardEasyDim
    
    // Feedback colors - Still semantic, but toned down (avoid neon)
    val Success = Color(0xFF2DA44E)
    val SuccessDim = Color(0xFF1F7A39)
    val Warning = Color(0xFFB08800)
    val WarningDim = Color(0xFF7A5F00)
    val Error = Color(0xFFD73A49)
    val ErrorDim = Color(0xFF9E2430)
    
    // Text colors - High contrast
    val TextPrimary = Color(0xFFF0F6FC)
    val TextSecondary = Color(0xFF8B949E)
    val TextTertiary = Color(0xFF484F58)
    val TextAccent = Primary
    
    // Borders and structure
    val Border = Color(0xFF30363D)
    val BorderAccent = PrimaryDim
    val Divider = Color(0xFF21262D)
    
    // Gradients for premium feel
    val GradientPrimary = Brush.linearGradient(
        colors = listOf(Primary, CardNew)
    )
    val GradientSuccess = Brush.linearGradient(
        colors = listOf(Success, Primary)
    )
    val GradientError = Brush.linearGradient(
        colors = listOf(Error, Color(0xFF8A2631))
    )
    val GradientCard = Brush.linearGradient(
        colors = listOf(Color(0xFF161B22), Color(0xFF0D1117))
    )
    
    // Glow effects for cards
    val GlowCyan = Color(0x402B6CB0)
    val GlowGreen = Color(0x402DA44E)
    val GlowPink = Color(0x40D73A49)
    val GlowPurple = Color(0x401F4D7A)
}
