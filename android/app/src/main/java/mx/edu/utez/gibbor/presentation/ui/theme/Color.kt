package mx.edu.utez.gibbor.presentation.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Paleta neutra (claro / oscuro) ──────────────────────────────────────

internal val LightBackground = Color(0xFFF5F5F7)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFEDEDF0)
internal val LightOutline = Color(0xFFD2D2D7)
internal val LightOnBackground = Color(0xFF1D1D1F)
internal val LightOnSurfaceVariant = Color(0xFF6E6E73)
internal val LightPrimary = Color(0xFF0A63F5)
internal val LightSos = Color(0xFFE5243B)
internal val LightSosContainer = Color(0xFFFDE7EA)
internal val LightSuccess = Color(0xFF1E9E5A)
internal val LightWarning = Color(0xFFC77700)

internal val DarkBackground = Color(0xFF000000)
internal val DarkSurface = Color(0xFF1C1C1E)
internal val DarkSurfaceVariant = Color(0xFF2C2C2E)
internal val DarkOutline = Color(0xFF3A3A3C)
internal val DarkOnBackground = Color(0xFFF5F5F7)
internal val DarkOnSurfaceVariant = Color(0xFF98989D)
internal val DarkPrimary = Color(0xFF4D8DFF)
internal val DarkSos = Color(0xFFFF453A)
internal val DarkSosContainer = Color(0xFF3A1215)
internal val DarkSuccess = Color(0xFF30D158)
internal val DarkWarning = Color(0xFFFF9F0A)

/** Tokens semánticos que Material 3 no cubre. Se leen con `GibborTheme.colors`. */
@Immutable
data class GibborExtendedColors(
    val sos: Color,
    val onSos: Color,
    val sosContainer: Color,
    val success: Color,
    val warning: Color,
)

internal val LightExtendedColors = GibborExtendedColors(
    sos = LightSos,
    onSos = Color.White,
    sosContainer = LightSosContainer,
    success = LightSuccess,
    warning = LightWarning,
)

internal val DarkExtendedColors = GibborExtendedColors(
    sos = DarkSos,
    onSos = Color.White,
    sosContainer = DarkSosContainer,
    success = DarkSuccess,
    warning = DarkWarning,
)

internal val LocalGibborColors = staticCompositionLocalOf { LightExtendedColors }
