package com.securecam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Minimalism theme for SecureCam.
 *
 * The camera screen deliberately sits on Ink (near-black) so the viewfinder
 * is the hero; the gallery sits on Paper (off-white) with hairline dividers.
 * Chroma is limited to RecordRed, used only to signal live recording.
 */
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = PaperDim,
    onPrimaryContainer = Ink,
    secondary = Gray,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Gray,
    outline = Divider,
    outlineVariant = Divider,
    error = RecordRed,
)

private val DarkColors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = InkElevated,
    onPrimaryContainer = Paper,
    secondary = GrayLight,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = InkElevated,
    onSurfaceVariant = GrayLight,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF2A2A2A),
    error = RecordRed,
)

/** Camera chrome always uses the dark (ink) scheme regardless of system theme. */
@Composable
fun SecureCamCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MinimalTypography,
        content = content,
    )
}

/** Gallery / general UI follows the system light/dark setting. */
@Composable
fun SecureCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MinimalTypography,
        content = content,
    )
}
