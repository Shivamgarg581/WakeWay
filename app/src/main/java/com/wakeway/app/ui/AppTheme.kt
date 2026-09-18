package com.wakeway.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WakeWayColors = lightColorScheme(
    primary = Color(0xFF4B5FD7),
    secondary = Color(0xFF4C7B73),
    tertiary = Color(0xFFD28B4C),
    background = Color(0xFFF7F8FA),
    surface = Color.White
)

@Composable
fun WakeWayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WakeWayColors,
        content = content
    )
}
