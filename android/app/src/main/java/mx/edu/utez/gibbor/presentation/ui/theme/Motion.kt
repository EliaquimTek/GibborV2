package mx.edu.utez.gibbor.presentation.ui.theme

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object Motion {
    const val MICRO = 150
    const val COMPONENT = 300
    const val SCREEN = 450

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Entrada fade-through: fade + scale 0.96 → 1. */
    val fadeThroughEnter: EnterTransition =
        fadeIn(tween(SCREEN - MICRO, delayMillis = MICRO / 2, easing = Emphasized)) +
            scaleIn(tween(SCREEN - MICRO, delayMillis = MICRO / 2, easing = Emphasized), initialScale = 0.96f)

    /** Salida fade-through (emphasized accelerate). */
    val fadeThroughExit: ExitTransition =
        fadeOut(tween(MICRO, easing = EmphasizedAccelerate)) +
            scaleOut(tween(MICRO, easing = EmphasizedAccelerate), targetScale = 0.96f)

    fun fadeThrough(): ContentTransform =
        fadeThroughEnter togetherWith fadeOut(tween(MICRO, easing = EmphasizedAccelerate))
}

/** `true` si el sistema tiene "Quitar animaciones" (escala de animación = 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
