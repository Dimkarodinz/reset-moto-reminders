package dev.resetlight.research.general

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeneralResearchDarkColors = darkColorScheme(
    primary = Color(0xFF8DB9B4),
    onPrimary = Color(0xFF09201E),
    primaryContainer = Color(0xFF244743),
    onPrimaryContainer = Color(0xFFC6E7E2),
    secondary = Color(0xFFB3BEC9),
    onSecondary = Color(0xFF1E2932),
    secondaryContainer = Color(0xFF303C47),
    onSecondaryContainer = Color(0xFFD7E2ED),
    tertiary = Color(0xFFD6B36A),
    onTertiary = Color(0xFF392B00),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE2E7EC),
    surface = Color(0xFF121820),
    onSurface = Color(0xFFE2E7EC),
    surfaceVariant = Color(0xFF1C252F),
    onSurfaceVariant = Color(0xFFBCC6D0),
    outline = Color(0xFF87929D),
    outlineVariant = Color(0xFF3D4853),
)

/** Fixed dark presentation shared visually with the main and Triumph research apps. */
@Composable
fun GeneralResearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeneralResearchDarkColors,
        content = content,
    )
}
