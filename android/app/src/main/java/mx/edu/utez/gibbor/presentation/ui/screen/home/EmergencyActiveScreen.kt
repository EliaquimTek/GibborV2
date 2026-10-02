package mx.edu.utez.gibbor.presentation.ui.screen.home

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import mx.edu.utez.gibbor.presentation.ui.components.HoldToConfirmButton
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import mx.edu.utez.gibbor.presentation.ui.theme.rememberReduceMotion

/**
 * Overlay a pantalla completa mientras se graba evidencia. Bloquea "atrás" y solo se cierra
 * manteniendo presionado "detener" 1.5 s.
 *
 * @param selfiePreview vista previa en vivo (PreviewView ligado a CameraPreviewHolder) cuando el
 * disparo vino de DEMO/REMOTE; `null` para ESP32/SOS manual (cámara trasera, sin vista previa).
 */
@Composable
fun EmergencyActiveScreen(
    elapsedSeconds: Long,
    selfiePreview: View?,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { /* Evita cerrar por accidente mientras se graba. */ }
    val colors = GibborTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.sosContainer)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Emergencia activa",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        val clock = formatDuration(elapsedSeconds)
        Text(
            clock,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            ),
            color = colors.sos,
            modifier = Modifier.semantics {
                contentDescription = "Tiempo grabando ${elapsedSeconds / 60} minutos ${elapsedSeconds % 60} segundos"
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tu ubicación y video se están protegiendo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (selfiePreview != null) {
            AndroidView(
                factory = {
                    (selfiePreview.parent as? ViewGroup)?.removeView(selfiePreview)
                    selfiePreview
                },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth(0.7f)
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .semantics { contentDescription = "Vista previa de la cámara frontal" },
                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
            )
            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecordingIndicator("Grabando video")
            RecordingIndicator("Grabando audio")
            DoneIndicator("Ubicación registrada")
            DoneIndicator("Incidente en blockchain")
        }

        Spacer(Modifier.height(32.dp))
        HoldToConfirmButton(
            text = "Mantén presionado para detener",
            onConfirmed = onStopRecording,
            durationMillis = 1500,
            accessibilityLabel = "Detener grabación",
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@Composable
private fun RecordingIndicator(label: String) {
    val sos = GibborTheme.colors.sos
    val reduceMotion = rememberReduceMotion()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (reduceMotion) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(sos))
            } else {
                val pulse = rememberInfiniteTransition(label = "rec dot")
                val alpha by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.45f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "rec dot alpha",
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(CircleShape)
                        .background(sos)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun DoneIndicator(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = GibborTheme.colors.success,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

internal fun formatDuration(seconds: Long): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
