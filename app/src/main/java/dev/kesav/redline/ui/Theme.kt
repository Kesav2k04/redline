package dev.kesav.redline.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF14161A)
private val Paper = Color(0xFFFBFAF7)
private val Red = Color(0xFFC0342B)
private val RedDark = Color(0xFFFF6B5E)

private val Light = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
)

private val Dark = darkColorScheme(
    primary = RedDark,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF1C1F24),
    onSurface = Paper,
)

// Dynamic colour is off on purpose: the demo recording has to look the same on
// any machine that plays it.
@Composable
fun RedlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
