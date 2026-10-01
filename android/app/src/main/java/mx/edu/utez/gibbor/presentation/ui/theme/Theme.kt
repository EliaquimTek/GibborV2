package mx.edu.utez.gibbor.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GibborColorScheme = lightColorScheme(
    primary            = GibborBlue,
    onPrimary          = GibborSurface,
    primaryContainer   = GibborBlueLight,
    onPrimaryContainer = GibborNavy,
    error              = GibborRed,
    onError            = GibborSurface,
    background         = GibborBg,
    onBackground       = GibborCharcoal,
    surface            = GibborSurface,
    onSurface          = GibborCharcoal,
    outline            = GibborBorder,
    outlineVariant     = GibborBorder,
)

@Composable
fun GibborTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GibborColorScheme,
        typography  = Typography,
        content     = content
    )
}
