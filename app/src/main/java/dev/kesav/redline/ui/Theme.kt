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

// Every role left unset falls back to Material's baseline, which is violet-tinted. Six
// roles were set and the rest were inherited, so hairlines and secondary text were
// quietly drawing in lilac on a warm paper background. The greys below are warmed to
// match the paper rather than fighting it.
private val InkMuted = Color(0xFF5A554E)   // 7.2:1 on Paper, so it clears AA at label sizes
private val Hairline = Color(0xFFE4E0D8)
private val PaperSunk = Color(0xFFF2EFE9)

private val PaperMuted = Color(0xFFB0ABA3)
private val HairlineDark = Color(0xFF32363D)
private val InkSunk = Color(0xFF24272D)

private val Light = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperSunk,
    onSurfaceVariant = InkMuted,
    outline = InkMuted,
    outlineVariant = Hairline,
    // The container roles decide what a bottom sheet, a menu and a dialog are painted
    // with. Left unset they resolve to the baseline tonal palette, which is violet, and
    // the first sheet this app ever showed came up lilac on a warm paper screen. Setting
    // the whole ladder closes the class rather than the one component that exposed it.
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = PaperSunk,
    surfaceContainerHigh = PaperSunk,
    surfaceContainerHighest = PaperSunk,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    scrim = Ink,
)

private val Dark = darkColorScheme(
    primary = RedDark,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF1C1F24),
    onSurface = Paper,
    surfaceVariant = InkSunk,
    onSurfaceVariant = PaperMuted,
    outline = PaperMuted,
    outlineVariant = HairlineDark,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF1C1F24),
    surfaceContainer = Color(0xFF212429),
    surfaceContainerHigh = InkSunk,
    surfaceContainerHighest = InkSunk,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    scrim = Color(0xFF000000),
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
