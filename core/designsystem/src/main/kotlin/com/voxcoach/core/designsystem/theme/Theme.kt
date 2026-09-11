package com.voxcoach.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = VoxPrimary,
    onPrimary = VoxOnPrimary,
    secondary = VoxSecondary,
    onSecondary = VoxOnPrimary,
    tertiary = VoxAccent,
    background = VoxBackground,
    surface = VoxSurface,
    onBackground = VoxOnBackground,
    onSurface = VoxOnBackground,
    onSurfaceVariant = VoxMuted,
    error = VoxError,
)

private val DarkColors = darkColorScheme(
    primary = VoxPrimary,
    secondary = VoxSecondary,
    tertiary = VoxAccent,
    error = VoxError,
)

@Composable
fun VoxCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VoxTypography,
        content = content,
    )
}
