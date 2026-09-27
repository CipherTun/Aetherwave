package com.ciphertun.aetherwave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AetherwaveColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = NeonPink,
    background = VoidBlack,
    surface = DeepSpace,
    surfaceVariant = SurfaceElevated,
    onPrimary = VoidBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun AetherwaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // app is dark-only by design; param kept for future light mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AetherwaveColorScheme,
        typography = AetherwaveTypography,
        content = content
    )
}
