package com.wakeway.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF10131A)
private val Slate = Color(0xFF5E6472)
private val Cloud = Color(0xFFF6F8FB)
private val White = Color(0xFFFFFFFF)
private val Indigo = Color(0xFF5B5CE2)
private val IndigoDeep = Color(0xFF3D3FA8)
private val Cyan = Color(0xFF19A8B6)
private val Mint = Color(0xFFDDF7F5)
private val Lavender = Color(0xFFE9E8FF)

private val WakeWayLight = lightColorScheme(
    primary = Indigo,
    onPrimary = White,
    primaryContainer = Lavender,
    onPrimaryContainer = Color(0xFF202258),
    secondary = Cyan,
    onSecondary = White,
    secondaryContainer = Mint,
    onSecondaryContainer = Color(0xFF063F44),
    tertiary = Color(0xFFF18D5C),
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFE8DD),
    onTertiaryContainer = Color(0xFF5A2410),
    background = Cloud,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Slate,
    outline = Color(0xFFCDD3DD),
    outlineVariant = Color(0xFFE2E6ED)
)

private val WakeWayDark = darkColorScheme(
    primary = Color(0xFFC3C4FF),
    onPrimary = Color(0xFF23257A),
    primaryContainer = IndigoDeep,
    onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = Color(0xFF6CD4DE),
    onSecondary = Color(0xFF00373A),
    secondaryContainer = Color(0xFF155258),
    onSecondaryContainer = Color(0xFFC8F5F7),
    tertiary = Color(0xFFFFB691),
    onTertiary = Color(0xFF5B1F08),
    tertiaryContainer = Color(0xFF7A3214),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF0D1016),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF141821),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF262C36),
    onSurfaceVariant = Color(0xFFB6BFCD),
    outline = Color(0xFF434B58),
    outlineVariant = Color(0xFF2F3742)
)

@Composable
fun WakeWayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WakeWayDark else WakeWayLight,
        typography = Typography(),
        content = content
    )
}
