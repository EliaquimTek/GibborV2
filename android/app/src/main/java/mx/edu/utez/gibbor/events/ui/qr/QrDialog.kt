package mx.edu.utez.gibbor.events.ui.qr

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import mx.edu.utez.gibbor.events.domain.EventResource
import mx.edu.utez.gibbor.events.ui.icon

/**
 * QR de un recurso a pantalla completa para que el público lo escanee desde el teléfono del
 * equipo. Mientras está abierto sube el brillo al máximo y mantiene la pantalla encendida.
 */
@Composable
fun QrDialog(
    resource: EventResource,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MaxBrightnessEffect()
        val uriHandler = LocalUriHandler.current

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        resource.type.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        resource.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    if (resource.description.isNotBlank()) {
                        Text(
                            resource.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    QrCode(
                        content = resource.url,
                        contentDescription = "Código QR de ${resource.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                    )
                    Text(
                        displayUrl(resource.url),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(8.dp))
                    FilledTonalButton(
                        onClick = { runCatching { uriHandler.openUri(resource.url) } },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Abrir enlace")
                    }
                }
            }
        }
    }
}

/** Texto legible del destino: sin `https://`, `mailto:` ni `/` final. */
internal fun displayUrl(url: String): String =
    url.removePrefix("https://").removePrefix("mailto:").trimEnd('/')

/** Brillo al máximo + pantalla encendida en la ventana del diálogo; se restaura al cerrar. */
@Composable
private fun MaxBrightnessEffect() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val attrs = window.attributes
        val previous = attrs.screenBrightness
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val restored = window.attributes
            restored.screenBrightness = previous
            window.attributes = restored
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
