package mx.edu.utez.gibbor.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GibborColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    primaryContainer = SurfaceHigh,
    onPrimaryContainer = TextHi,
    secondary = Violet,
    onSecondary = TextHi,
    tertiary = Lime,
    onTertiary = Ink,
    error = Panic,
    onError = TextHi,
    background = Ink,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMid,
    outline = Stroke,
    outlineVariant = Stroke,
)

@Composable
fun GibborTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GibborColorScheme,
        typography = Typography,
        content = content,
    )
}
