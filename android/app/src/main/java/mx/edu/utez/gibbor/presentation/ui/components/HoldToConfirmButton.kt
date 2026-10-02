package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import mx.edu.utez.gibbor.presentation.ui.theme.Motion

/**
 * Mantener presionado para confirmar: el progreso avanza de 0 a 1 en [durationMillis];
 * si se suelta antes, se cancela y regresa a 0. Al completarse llama a [onConfirmed].
 * Haptic al iniciar el hold y al confirmar.
 */
@Composable
fun Modifier.holdToConfirm(
    enabled: Boolean,
    durationMillis: Int,
    progress: Animatable<Float, AnimationVector1D>,
    onConfirmed: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    return pointerInput(enabled, durationMillis) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                var confirmed = false
                val fill = launch {
                    try {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
                        confirmed = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnConfirmed()
                    } catch (_: CancellationException) {
                        // Soltó antes de tiempo: se cancela.
                    }
                }
                waitForUpOrCancellation()
                if (!confirmed) fill.cancel()
                launch { progress.animateTo(0f, tween(Motion.MICRO, easing = Motion.EmphasizedAccelerate)) }
            }
        }
    }
}

/**
 * Botón de contorno que se rellena mientras se mantiene presionado.
 * Se usa para detener la grabación (1.5 s) en Emergencia activa.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1500,
    color: Color = GibborTheme.colors.sos,
    accessibilityLabel: String = text,
) {
    val progress = remember { Animatable(0f) }
    val shape = RoundedCornerShape(50)
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.5.dp, color, shape)
            .drawBehind {
                val p = progress.value
                if (p > 0f) drawRect(color.copy(alpha = 0.22f), size = size.copy(width = size.width * p))
            }
            .holdToConfirm(
                enabled = true,
                durationMillis = durationMillis,
                progress = progress,
                onConfirmed = onConfirmed,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibilityLabel
                onClick(label = accessibilityLabel) {
                    currentOnConfirmed()
                    true
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}
