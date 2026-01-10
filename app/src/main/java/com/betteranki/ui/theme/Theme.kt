package com.betteranki.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    secondary = AppColors.Secondary,
    tertiary = AppColors.CardReview,
    background = AppColors.DarkBackground,
    surface = AppColors.DarkSurface,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    onPrimary = AppColors.DarkBackground,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    outlineVariant = AppColors.BorderAccent,
    error = AppColors.Error,
    onError = Color.White,
    primaryContainer = AppColors.DarkElevated,
    onPrimaryContainer = AppColors.Primary,
    secondaryContainer = AppColors.DarkSurfaceVariant,
    onSecondaryContainer = AppColors.Secondary,
    inverseSurface = AppColors.TextPrimary,
    inverseOnSurface = AppColors.DarkBackground,
    inversePrimary = AppColors.PrimaryDim,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF4CAF50),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun BetterAnkiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppColors.DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
