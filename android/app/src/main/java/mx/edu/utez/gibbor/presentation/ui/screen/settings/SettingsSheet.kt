package mx.edu.utez.gibbor.presentation.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.edu.utez.gibbor.BuildConfig
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborLogo
import mx.edu.utez.gibbor.presentation.ui.components.LogConsole
import mx.edu.utez.gibbor.presentation.ui.components.color
import mx.edu.utez.gibbor.presentation.ui.components.deviceConnection
import mx.edu.utez.gibbor.presentation.ui.components.label
import mx.edu.utez.gibbor.presentation.ui.components.tone

/** Ajustes (ModalBottomSheet desde el engrane de Inicio). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    controller: MainScreenController,
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    authStatus: String,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        SettingsContent(
            controller = controller,
            backendUrl = backendUrl,
            onBackendUrlChange = onBackendUrlChange,
            authStatus = authStatus,
        )
    }
}

/** Contenido de Ajustes (separado para poder previsualizarlo sin el sheet). */
@Composable
fun SettingsContent(
    controller: MainScreenController,
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    authStatus: String,
    modifier: Modifier = Modifier,
) {
    val device = deviceConnection(controller.statusText, controller.isConnecting)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            "Ajustes",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Section("Botón GIBBOR") {
            SettingsItem(
                icon = Icons.Rounded.Bluetooth,
                title = "Estado",
                supporting = controller.statusText.removePrefix("Status: "),
                trailing = device.label(),
                trailingColor = device.tone().color(),
            )
            InsetDivider()
            SettingsItem(
                icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
                title = "Activar Bluetooth",
                onClick = { controller.ensureBluetoothEnabled() },
            )
            InsetDivider()
            SettingsItem(
                icon = Icons.Rounded.BluetoothConnected,
                title = if (controller.isConnecting) "Conectando…" else "Conectar",
                onClick = if (controller.isConnecting) null else ({ controller.connectToEsp32() }),
            )
            InsetDivider()
            SettingsItem(
                icon = Icons.Rounded.BluetoothDisabled,
                title = "Desconectar",
                onClick = { controller.disconnectFromEsp32() },
            )
        }

        Section("Servidor") {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = onBackendUrlChange,
                label = { Text("Backend URL") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        Section("Cuenta") {
            SettingsItem(
                icon = Icons.Rounded.AlternateEmail,
                title = "Correo",
                supporting = controller.authenticatedEmail.ifBlank { "Sin sesión" },
            )
            InsetDivider()
            SettingsItem(
                icon = Icons.Rounded.VerifiedUser,
                title = "Sesión",
                supporting = sessionMessage(authStatus),
            )
        }

        Section("Registro técnico") {
            LogConsole(
                logText = controller.logText,
                onClear = { controller.clearLog() },
                modifier = Modifier.padding(16.dp),
            )
        }

        Section("Acerca de") {
            ListItem(
                leadingContent = { GibborLogo(size = 40.dp) },
                headlineContent = { Text("GIBBOR · Hackatón UTEZ", style = MaterialTheme.typography.bodyLarge) },
                supportingContent = {
                    Text(
                        "Versión ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingContent = {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val actionable = onClick != null && enabled
    ListItem(
        modifier = if (actionable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (onClick != null && actionable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    onClick == null -> MaterialTheme.colorScheme.onSurface
                    actionable -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = supporting?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        trailingContent = trailing?.let {
            { Text(it, style = MaterialTheme.typography.labelLarge, color = trailingColor) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun InsetDivider() = HorizontalDivider(
    modifier = Modifier.padding(start = 56.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline,
)

/** Último estado de la sesión, en lenguaje de persona (authStatus es solo de display). */
private fun sessionMessage(authStatus: String): String = when (authStatus) {
    "Session started" -> "Protección lista"
    "Sending to backend..." -> "Enviando incidente…"
    "Incident registered on-chain" -> "Incidente registrado"
    "TX failed on-chain", "Backend error" -> "Revisa el estado del último envío"
    else -> if (authStatus.startsWith("❌ Error")) "Error al enviar el último incidente" else "Sesión activa"
}
