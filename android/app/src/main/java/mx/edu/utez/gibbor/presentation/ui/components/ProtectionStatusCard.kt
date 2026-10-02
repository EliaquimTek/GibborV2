package mx.edu.utez.gibbor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme

/**
 * "Estado de protección": botón GIBBOR, ubicación, cámara/micrófono y servidor,
 * cada uno con ícono + texto + color semántico y su acción inline cuando falta algo.
 */
@Composable
fun ProtectionStatusCard(
    device: DeviceConnection,
    locationGranted: Boolean,
    recordingGranted: Boolean,
    chain: ChainState,
    onConnect: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allOk = device == DeviceConnection.Connected && locationGranted && recordingGranted &&
        chain != ChainState.Error
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            ) {
                Icon(
                    imageVector = if (allOk) Icons.Rounded.Shield else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (allOk) GibborTheme.colors.success else GibborTheme.colors.warning,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (allOk) "Protección activa" else "Revisa tu protección",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = Icons.Rounded.Bluetooth,
                label = "Botón GIBBOR",
                value = device.label(),
                tone = device.tone(),
                loading = device == DeviceConnection.Connecting,
                actionLabel = if (device == DeviceConnection.Disconnected || device == DeviceConnection.Failed) "Conectar" else null,
                onAction = onConnect,
            )
            InsetDivider()
            StatusRow(
                icon = Icons.Rounded.LocationOn,
                label = "Ubicación",
                value = if (locationGranted) "Permitida" else "Sin permiso",
                tone = if (locationGranted) StatusTone.Ok else StatusTone.Pending,
                actionLabel = if (locationGranted) null else "Permitir",
                onAction = onRequestLocation,
            )
            InsetDivider()
            StatusRow(
                icon = Icons.Rounded.Videocam,
                label = "Cámara y micrófono",
                value = if (recordingGranted) "Permitidos" else "Sin permiso",
                tone = if (recordingGranted) StatusTone.Ok else StatusTone.Pending,
                actionLabel = if (recordingGranted) null else "Permitir",
                onAction = onRequestRecording,
            )
            InsetDivider()
            StatusRow(
                icon = Icons.Rounded.CloudDone,
                label = "Servidor",
                value = when (chain) {
                    ChainState.None -> "Sin envíos todavía"
                    ChainState.Pending -> "Pendiente"
                    ChainState.Success -> "En línea"
                    ChainState.Error -> "Error en el último envío"
                },
                tone = chain.tone(),
                loading = chain == ChainState.Pending,
            )
        }
    }
}

@Composable
private fun InsetDivider() = HorizontalDivider(
    modifier = Modifier.padding(start = 40.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline,
)
