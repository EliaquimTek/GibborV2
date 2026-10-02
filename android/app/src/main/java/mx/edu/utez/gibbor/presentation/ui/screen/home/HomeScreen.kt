package mx.edu.utez.gibbor.presentation.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.ChainState
import mx.edu.utez.gibbor.presentation.ui.components.GibborTopBar
import mx.edu.utez.gibbor.presentation.ui.components.ProtectionStatusCard
import mx.edu.utez.gibbor.presentation.ui.components.SosButton
import mx.edu.utez.gibbor.presentation.ui.components.TechnicalDetails
import mx.edu.utez.gibbor.presentation.ui.components.chainState
import mx.edu.utez.gibbor.presentation.ui.components.deviceConnection
import mx.edu.utez.gibbor.presentation.ui.components.incidentField
import mx.edu.utez.gibbor.presentation.ui.components.technicalLines
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pestaña GIBBOR: estado de protección, SOS (mantener 1.2 s) y último incidente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    controller: MainScreenController,
    busy: Boolean,
    onChainStatus: String,
    incidentPreview: String,
    onCreateIncident: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Los permisos se reevalúan cada vez que la app vuelve al frente (p. ej. tras el diálogo).
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val locationGranted = remember(resumeTick) { controller.hasLocationPermission() }
    val recordingGranted = remember(resumeTick) {
        controller.hasAudioPermission() && controller.hasCameraPermission()
    }

    val chain = chainState(onChainStatus)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = { GibborTopBar(onOpenSettings = onOpenSettings, scrollBehavior = scrollBehavior) },
    ) { inner ->
        HomeContent(
            contentPadding = inner,
            email = controller.authenticatedEmail,
            protection = {
                ProtectionStatusCard(
                    device = deviceConnection(controller.statusText, controller.isConnecting),
                    locationGranted = locationGranted,
                    recordingGranted = recordingGranted,
                    chain = chain,
                    onConnect = { controller.connectToEsp32() },
                    onRequestLocation = { controller.requestLocationPermission() },
                    onRequestRecording = { controller.requestBluetoothPermissions() },
                )
            },
            busy = busy,
            chain = chain,
            onChainStatus = onChainStatus,
            incidentPreview = incidentPreview,
            onCreateIncident = onCreateIncident,
            onShowMessage = onShowMessage,
        )
    }
}

@Composable
private fun HomeContent(
    contentPadding: PaddingValues,
    email: String,
    protection: @Composable () -> Unit,
    busy: Boolean,
    chain: ChainState,
    onChainStatus: String,
    incidentPreview: String,
    onCreateIncident: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hola",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(24.dp))
                protection()
            }

            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SosButton(busy = busy, onConfirmed = onCreateIncident)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (busy) "Tu ubicación y video se están protegiendo" else "Mantén presionado para pedir ayuda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Column(Modifier.fillMaxWidth()) {
                val hasIncident = incidentPreview != "Ninguno" || onChainStatus.isNotBlank()
                AnimatedVisibility(visible = hasIncident, enter = fadeIn(), exit = fadeOut()) {
                    LastIncidentCard(
                        chain = chain,
                        busy = busy,
                        onChainStatus = onChainStatus,
                        incidentPreview = incidentPreview,
                        onShowMessage = onShowMessage,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.SettingsRemote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "También puedes usar tu botón GIBBOR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastIncidentCard(
    chain: ChainState,
    busy: Boolean,
    onChainStatus: String,
    incidentPreview: String,
    onShowMessage: (String) -> Unit,
) {
    val time = incidentField(incidentPreview, "timestamp")?.toLongOrNull()?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000L))
    }
    val (title, color) = when (chain) {
        ChainState.Success -> "Registrado en blockchain" to GibborTheme.colors.success
        ChainState.Error -> "No se pudo registrar" to GibborTheme.colors.sos
        ChainState.Pending, ChainState.None ->
            (if (busy) "Enviando alerta…" else "Pendiente") to GibborTheme.colors.warning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "ÚLTIMO INCIDENTE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (chain) {
                    ChainState.Success -> Icon(Icons.Rounded.CheckCircle, null, tint = color)
                    ChainState.Error -> Icon(Icons.Rounded.ErrorOutline, null, tint = color)
                    else -> if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = color, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Schedule, null, tint = color)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (time != null) {
                    Text(
                        time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (chain == ChainState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No hubo respuesta del servidor. Revisa tu conexión y la URL del servidor en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            TechnicalDetails(
                lines = technicalLines(incidentPreview, onChainStatus),
                onCopied = { onShowMessage("Copiado") },
            )
        }
    }
}
