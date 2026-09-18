package com.wakeway.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WakeWayLight = lightColorScheme(
    primary = Color(0xFF3E5BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF5A6174),
    secondaryContainer = Color(0xFFE0E2EE),
    tertiary = Color(0xFF7A4D00),
    tertiaryContainer = Color(0xFFFFDEA8),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE4E2EC)
)

private val WakeWayDark = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF172965),
    primaryContainer = Color(0xFF304595),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC3C6D4),
    secondaryContainer = Color(0xFF41434F),
    tertiary = Color(0xFFEFC17B),
    tertiaryContainer = Color(0xFF5C4218),
    background = Color(0xFF121318),
    surface = Color(0xFF17181D),
    surfaceVariant = Color(0xFF45464F)
)

@Composable
fun WakeWayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WakeWayDark else WakeWayLight,
        content = content
    )
}
