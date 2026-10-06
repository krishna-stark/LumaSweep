package com.example.photostorage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF090B0A)
val Pine = Color(0xFF18201C)
val Mint = Color(0xFF74E6A7)
val WarmWhite = Color(0xFFF4F7F5)
val Amber = Color(0xFFD6AA62)
val Violet = Color(0xFF9B8AFB)
val Blue = Color(0xFF73A6FF)
val ErrorRed = Color(0xFFFF5A67)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Forest,
    primaryContainer = Pine,
    onPrimaryContainer = Color.White,
    background = Forest,
    onBackground = WarmWhite,
    surface = Color(0xFF151517),
    onSurface = WarmWhite,
    surfaceVariant = Color(0xFF252528),
    onSurfaceVariant = Color(0xFFC9C9CD),
    error = ErrorRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F7E1),
    onPrimaryContainer = Forest,
    background = Color(0xFFF4F1EB),
    onBackground = Color(0xFF171B18),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF171B18),
)

@Composable
fun PhotoStorageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
