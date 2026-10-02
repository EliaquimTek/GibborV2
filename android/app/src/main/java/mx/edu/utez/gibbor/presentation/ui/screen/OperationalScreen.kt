package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.gibbor.R
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborCard
import mx.edu.utez.gibbor.presentation.ui.components.GibborCollapsible
import mx.edu.utez.gibbor.presentation.ui.components.GibborDangerButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborOutlinedButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborPrimaryButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborSectionLabel
import mx.edu.utez.gibbor.presentation.ui.components.LogConsole
import mx.edu.utez.gibbor.presentation.ui.components.PanicButton
import mx.edu.utez.gibbor.presentation.ui.components.RecordingElapsedText
import mx.edu.utez.gibbor.presentation.ui.components.StatusChip
import mx.edu.utez.gibbor.presentation.ui.theme.Amber
import mx.edu.utez.gibbor.presentation.ui.theme.Cyan
import mx.edu.utez.gibbor.presentation.ui.theme.Lime
import mx.edu.utez.gibbor.presentation.ui.theme.Panic
import mx.edu.utez.gibbor.presentation.ui.theme.Stroke
import mx.edu.utez.gibbor.presentation.ui.theme.Surface
import mx.edu.utez.gibbor.presentation.ui.theme.TextHi
import mx.edu.utez.gibbor.presentation.ui.theme.TextLow
import mx.edu.utez.gibbor.presentation.ui.theme.TextMid

@Composable
fun OperationalScreen(
    controller: MainScreenController,
    email: String,
    onEmailChange: (String) -> Unit,
    otpCode: String,
    otpRequested: Boolean,
    onRequestOtp: () -> Unit,
    onSignIn: () -> Unit,
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    busy: Boolean,
    recordingElapsedSeconds: Long,
    authStatus: String,
    onChainStatus: String,
    incidentPreview: String,
    onCreateIncident: () -> Unit,
    onStopRecording: () -> Unit,
) {
    var configExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var btExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var logExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val connectionColor = when {
        controller.isConnecting -> Amber
        (controller.statusText.contains("Connected", ignoreCase = true) ||
            controller.statusText.contains("Conectado", ignoreCase = true)) &&
            !controller.statusText.contains("Disconnected", ignoreCase = true) -> Cyan
        else -> TextLow
    }
    val connectionLabel = when {
        controller.isConnecting -> "Conectando"
        connectionColor == Cyan -> "Conectado"
        else -> "Desconectado"
    }
    val chainColor = when {
        onChainStatus.startsWith("✅") -> Lime
        onChainStatus.contains("pending", ignoreCase = true) ||
            onChainStatus.contains("Sending", ignoreCase = true) -> Amber
        onChainStatus.contains("Error", ignoreCase = true) || onChainStatus.contains("❌") -> Panic
        else -> TextLow
    }
    val chainLabel = when (chainColor) {
        Lime -> "Confirmada"
        Amber -> "Pendiente"
        Panic -> "Error"
        else -> "Sin envío"
    }
    val chainIcon = when {
        onChainStatus.startsWith("✅") -> "✓"
        onChainStatus.contains("pending", ignoreCase = true) ||
            onChainStatus.contains("Sending", ignoreCase = true) -> "…"
        onChainStatus.contains("Error", ignoreCase = true) || onChainStatus.contains("❌") -> "!"
        else -> "•"
    }
    val sessionMessage = when (authStatus) {
        "Session started" -> "Protección lista"
        "Sending to backend..." -> "Enviando incidente…"
        "Incident registered on-chain" -> "Incidente registrado"
        "TX failed on-chain", "Backend error" -> "Revisa el estado del envío"
        else -> if (authStatus.startsWith("❌ Error")) "Error al enviar" else "Sesión activa"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        OperationalHeader(isAuthenticated = controller.isAuthenticated)
        Spacer(Modifier.height(22.dp))

        if (!controller.isAuthenticated) {
            GibborCard {
                GibborSectionLabel("Acceso")
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = darkFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                GibborPrimaryButton(
                    text = "Obtener wallet",
                    onClick = onRequestOtp,
                    enabled = email.isNotBlank() && !otpRequested,
                )
                AnimatedVisibility(visible = otpRequested) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {},
                            label = { Text("Código de verificación (6 dígitos)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = darkFieldColors(),
                        )
                        Spacer(Modifier.height(12.dp))
                        GibborPrimaryButton(
                            text = "Iniciar sesión",
                            onClick = onSignIn,
                            enabled = otpRequested && email.isNotBlank(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            GibborCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        GibborSectionLabel("Sesión activa")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = controller.authenticatedEmail,
                            color = TextHi,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = sessionMessage,
                        color = TextMid,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                label = "Botón",
                value = connectionLabel,
                color = connectionColor,
                modifier = Modifier.weight(1f),
            )
            if (controller.isRecording) {
                StatusChip(
                    label = "Grabando",
                    value = "Evidencia",
                    color = Panic,
                    modifier = Modifier.weight(1f),
                )
            }
            StatusChip(
                label = "Red",
                value = chainLabel,
                color = chainColor,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(22.dp))

        PanicButton(
            authenticated = controller.isAuthenticated,
            busy = busy,
            isRecording = controller.isRecording,
            elapsedSeconds = recordingElapsedSeconds,
            onClick = onCreateIncident,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        if (controller.isRecording) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Panic),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RecordingDot()
                            Spacer(Modifier.size(9.dp))
                            Text(
                                text = "GRABANDO EVIDENCIA",
                                color = Panic,
                                fontSize = 10.sp,
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        RecordingElapsedText(elapsedSeconds = recordingElapsedSeconds, color = TextHi)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Audio y video protegidos", color = TextMid, fontSize = 12.sp)
                        RecordingVisualizer()
                    }
                    Spacer(Modifier.height(16.dp))
                    GibborDangerButton(text = "Detener grabación", onClick = onStopRecording)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (onChainStatus.isNotBlank()) {
            GibborCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chainIcon,
                        color = chainColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(10.dp))
                    GibborSectionLabel("Estado de transacción")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    onChainStatus
                        .replace("✅", "").replace("❌", "").replace("⏳", "").trim(),
                    fontSize = 11.sp,
                    color = TextMid,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (controller.isAuthenticated && incidentPreview != "Ninguno") {
            GibborCard {
                GibborSectionLabel("Último incidente")
                Spacer(Modifier.height(9.dp))
                Text(
                    incidentPreview,
                    fontSize = 11.sp,
                    color = TextMid,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        GibborCollapsible(
            title = "Configuración",
            expanded = configExpanded,
            onToggle = { configExpanded = !configExpanded },
        ) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = onBackendUrlChange,
                label = { Text("URL del backend") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = darkFieldColors(),
            )
        }
        GibborCollapsible(
            title = "Bluetooth",
            expanded = btExpanded,
            onToggle = { btExpanded = !btExpanded },
        ) {
            Text(controller.statusText, fontSize = 12.sp, color = TextMid)
            Spacer(Modifier.height(10.dp))
            GibborOutlinedButton(
                text = "Activar Bluetooth",
                onClick = { controller.ensureBluetoothEnabled() },
            )
            Spacer(Modifier.height(8.dp))
            GibborOutlinedButton(
                text = if (controller.isConnecting) "Conectando…" else "Conectar botón GIBBOR",
                onClick = { controller.connectToEsp32() },
                enabled = !controller.isConnecting,
            )
            Spacer(Modifier.height(8.dp))
            GibborOutlinedButton(
                text = "Desconectar",
                onClick = { controller.disconnectFromEsp32() },
            )
        }
        GibborCollapsible(
            title = "Registro de actividad",
            expanded = logExpanded,
            onToggle = { logExpanded = !logExpanded },
        ) {
            LogConsole(logText = controller.logText, onClear = { controller.clearLog() })
        }
        Spacer(Modifier.height(34.dp))
    }
}

@Composable
private fun OperationalHeader(isAuthenticated: Boolean) {
    val pulse = rememberInfiniteTransition(label = "session status")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "session pulse",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.gibbor_logo),
                contentDescription = "Logo de GIBBOR",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Cyan, CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "GIBBOR",
                    color = TextHi,
                    fontSize = 19.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Sistema de emergencia", color = TextMid, fontSize = 11.sp)
            }
        }
        if (isAuthenticated) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(Lime)
            )
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    disabledTextColor = TextMid,
    focusedContainerColor = mx.edu.utez.gibbor.presentation.ui.theme.SurfaceHigh,
    unfocusedContainerColor = mx.edu.utez.gibbor.presentation.ui.theme.SurfaceHigh,
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = TextMid,
    cursorColor = Cyan,
)

@Composable
private fun RecordingDot() {
    val transition = rememberInfiniteTransition(label = "recording dot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "recording dot alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Panic)
    )
}

@Composable
private fun RecordingVisualizer() {
    val transition = rememberInfiniteTransition(label = "recording visualizer")
    val levels = (0..4).map { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 370 + index * 75, delayMillis = index * 55),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "recording bar $index",
        )
    }
    androidx.compose.foundation.Canvas(Modifier.size(width = 52.dp, height = 25.dp)) {
        val barWidth = size.width / 9
        levels.forEachIndexed { index, level ->
            val barHeight = size.height * level.value
            drawRoundRect(
                color = Panic,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * size.width / 5 + barWidth,
                    y = (size.height - barHeight) / 2,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth),
            )
        }
    }
}
