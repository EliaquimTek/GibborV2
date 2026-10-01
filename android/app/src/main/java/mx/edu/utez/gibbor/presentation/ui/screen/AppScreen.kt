package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.BuildConfig
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborCard
import mx.edu.utez.gibbor.presentation.ui.components.GibborCollapsible
import mx.edu.utez.gibbor.presentation.ui.components.GibborDangerButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborOutlinedButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborPrimaryButton
import mx.edu.utez.gibbor.presentation.ui.components.GibborSectionLabel
import mx.edu.utez.gibbor.presentation.ui.theme.GibborBg
import mx.edu.utez.gibbor.presentation.ui.theme.GibborCharcoal
import mx.edu.utez.gibbor.presentation.ui.theme.GibborDark
import mx.edu.utez.gibbor.presentation.ui.theme.GibborGreen
import mx.edu.utez.gibbor.presentation.ui.theme.GibborMid
import mx.edu.utez.gibbor.presentation.ui.theme.GibborNavy
import mx.edu.utez.gibbor.presentation.ui.theme.GibborRed
import mx.edu.utez.gibbor.presentation.ui.theme.GibborRedBorder
import mx.edu.utez.gibbor.presentation.ui.theme.GibborRedLight

@Composable
fun AppScreen(controller: MainScreenController) {
    // Valor por defecto desde local.properties (BACKEND_URL)
    // Emulador: 10.0.2.2:3001 | Dispositivo físico: IP de la laptop en WiFi
    // Producción: https://api.tudominio.com
    var backendUrl by remember { mutableStateOf(BuildConfig.BACKEND_URL) }
    var email by remember { mutableStateOf("") }
    var authStatus by remember { mutableStateOf("No autenticado") }
    var busy by remember { mutableStateOf(false) }
    var lastHandledTrigger by remember { mutableIntStateOf(0) }
    var incidentPreview by remember { mutableStateOf("Ninguno") }
    var onChainStatus by remember { mutableStateOf("") }

    /**
     * Flujo completo: ubicación → draft → backend → on-chain
     * authStatus se usa SOLO para display. isAuthenticated controla el flujo.
     */
    suspend fun createAndSendIncident(source: String) {
        if (!controller.hasAudioPermission() || !controller.hasCameraPermission()) {
            controller.appendLog("$source -> Missing recording permissions. Grant them and try again.")
            controller.requestBluetoothPermissions()
            return
        }

        if (!controller.hasLocationPermission()) {
            controller.appendLog("$source -> missing location permission")
            controller.requestLocationPermission()
            return
        }

        // --- Paso 1: Generar incidente local ---
        val location = controller.getCurrentPhoneLocation()
        val draft = controller.buildIncidentDraft(location)

        incidentPreview =
            "id=${draft.incidentId}\n" +
                    "timestamp=${draft.timestamp}\n" +
                    "lat_e7=${draft.latE7}\n" +
                    "lon_e7=${draft.lonE7}\n" +
                    "hash=${draft.initialHash}"

        controller.appendLog("$source -> incident_id=${draft.incidentId}")
        controller.appendLog("$source -> lat_e7=${draft.latE7}, lon_e7=${draft.lonE7}")
        controller.appendLog("$source -> hash=${draft.initialHash}")

        // --- Paso 2: Enviar al backend ---
        authStatus = "Sending to backend..."
        onChainStatus = "Sending..."
        controller.appendLog("$source -> Sending incident to $backendUrl...")

        try {
            val result = controller.sendIncidentToBackend(backendUrl, controller.authenticatedEmail, draft)

            controller.appendLog("$source -> Backend response: success=${result.success}")
            controller.appendLog("$source -> status=${result.status}")
            controller.appendLog("$source -> txId=${result.txId}")
            controller.appendLog("$source -> txHash=${result.txHash}")
            controller.appendLog("$source -> explorer=${result.explorerLink}")

            if (!result.success) {
                onChainStatus = "Error: ${result.error}"
                authStatus = "Backend error"
                controller.appendLog("$source -> BACKEND ERROR: ${result.error}")
                controller.appendLog("$source -> Raw: ${result.rawJson}")
                return
            }

            // --- Paso 3: Polling si está pendiente ---
            var finalResult = result
            if (result.status in listOf("pending", "awaiting-approval")) {
                controller.appendLog("$source -> TX pending, polling backend...")
                onChainStatus = "TX pending, waiting for confirmation..."
                finalResult = controller.pollBackendStatus(backendUrl, controller.authenticatedEmail, result.txId)
                controller.appendLog("$source -> Polling final: status=${finalResult.status}")
            }

            // --- Paso 4: Resultado final ---
            val emoji = when (finalResult.status) {
                "success" -> "✅"
                "failed" -> "❌"
                else -> "⏳"
            }
            onChainStatus =
                "$emoji Status: ${finalResult.status}\n" +
                "TX: ${finalResult.txHash}\n" +
                "Explorer: ${finalResult.explorerLink}"

            authStatus = when (finalResult.status) {
                "success" -> "Incident registered on-chain"
                "failed" -> "TX failed on-chain"
                else -> "TX: ${finalResult.status}"
            }
            // ⚠️ isAuthenticated NO se toca — sigue siendo true

            // --- Paso 5: Iniciar grabación de audio automáticamente ---
            if (finalResult.status == "success") {
                controller.appendLog("$source -> TX confirmed. Starting evidence collection...")
                controller.lastBackendUrl = backendUrl
                controller.lastIncidentForEvidence = draft.incidentId
                controller.startAudioRecording(draft.incidentId)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            onChainStatus = "❌ Error: $errorMsg"
            authStatus = "❌ Error: $errorMsg"
            controller.appendLog("$source -> EXCEPTION: $errorMsg")
            // ⚠️ isAuthenticated NO se toca — sigue siendo true
        }
    }

    // ─── Trigger ESP32 ────────────────────────────────────────────────────

    LaunchedEffect(controller.triggerCounter) {
        if (controller.triggerCounter <= 0 || controller.triggerCounter == lastHandledTrigger) return@LaunchedEffect

        lastHandledTrigger = controller.triggerCounter

        // Usa isAuthenticated (boolean), no authStatus (string de display)
        if (!controller.isAuthenticated) {
            controller.appendLog("ESP32 -> authenticate first")
            return@LaunchedEffect
        }

        busy = true
        try {
            createAndSendIncident("ESP32")
        } catch (e: Exception) {
            controller.appendLog("ESP32 -> error: ${e.message}")
            // isAuthenticated NO se toca
        } finally {
            busy = false
        }
    }

    // ─── UI State ─────────────────────────────────────────────────────────

    var otpCode         by remember { mutableStateOf("") }
    var otpRequested    by remember { mutableStateOf(false) }
    var configExpanded  by remember { mutableStateOf(false) }
    var btExpanded      by remember { mutableStateOf(false) }
    var logExpanded     by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = GibborBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {

            Spacer(Modifier.height(48.dp))

            // ── Header ──────────────────────────────────────────────────

            Text(
                "GIBBOR",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 10.sp,
                color = GibborNavy
            )
            Text(
                "EMERGENCY SYSTEM",
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                color = GibborMid,
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.height(36.dp))

            // ── Autenticacion ────────────────────────────────────────

            if (!controller.isAuthenticated) {

                GibborCard {
                    GibborSectionLabel("Access")
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            otpRequested = false
                            otpCode = ""
                        },
                        label = { Text("Institutional email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(Modifier.height(12.dp))

                    GibborPrimaryButton(
                        text = "Get wallet",
                        onClick = {
                            val code = (100000..999999).random().toString()
                            otpCode = code
                            otpRequested = true
                            controller.appendLog("OTP generated for ${email.trim()}")
                        },
                        enabled = email.isNotBlank() && !otpRequested
                    )

                    if (otpRequested) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {},
                            label = { Text("Verification code (6 digits)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.height(12.dp))
                        GibborPrimaryButton(
                            text = "Sign in",
                            onClick = {
                                val norm = email.trim()
                                    .replace("[^a-zA-Z0-9@._-]".toRegex(), "")
                                    .lowercase()
                                if (norm.isNotBlank()) {
                                    controller.startSession(norm)
                                    authStatus = "Session started"
                                    controller.appendLog("Auth: session started as $norm")
                                }
                            },
                            enabled = otpRequested && email.isNotBlank()
                        )
                    }
                }

            } else {

                // ── Sesion activa ──────────────────────────────────────

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ACTIVE SESSION",
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = GibborMid
                        )
                        Text(
                            controller.authenticatedEmail,
                            fontSize = 13.sp,
                            color = GibborNavy,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GibborGreen)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Accion principal ───────────────────────────────────

                GibborPrimaryButton(
                    text = if (busy) "Processing..." else "CREATE INCIDENT",
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            busy = true
                            try { createAndSendIncident("MANUAL") }
                            catch (e: Exception) { controller.appendLog("MANUAL -> error: ${e.message}") }
                            finally { busy = false }
                        }
                    },
                    enabled = !busy
                )
            }

            // ── Grabacion activa ──────────────────────────────────────

            if (controller.isRecording) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GibborRedLight),
                    border = BorderStroke(1.dp, GibborRedBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(GibborRed)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "RECORDING EVIDENCE",
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                color = GibborRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        GibborDangerButton(
                            text = "STOP RECORDING",
                            onClick = {
                                val hash = controller.stopAudioRecording()
                                if (hash != null && controller.lastBackendUrl.isNotEmpty() && controller.lastIncidentForEvidence.isNotEmpty()) {
                                    controller.appendLog("AUDIO: Anchoring hash on blockchain...")
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val ok = controller.sendEvidenceToBackend(controller.lastBackendUrl, controller.lastIncidentForEvidence, "audio", hash)
                                        if (ok) controller.appendLog("AUDIO: Hash anchored on blockchain")
                                        else    controller.appendLog("AUDIO: Error anchoring hash on blockchain")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ── Estado on-chain ───────────────────────────────────────

            if (onChainStatus.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                GibborCard {
                    GibborSectionLabel("Transaction status")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        onChainStatus
                            .replace("✅", "").replace("❌", "").replace("⏳", "").trim(),
                        fontSize = 12.sp,
                        color = GibborDark,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── Ultimo incidente ──────────────────────────────────────

            if (controller.isAuthenticated && incidentPreview != "Ninguno") {
                Spacer(Modifier.height(16.dp))
                GibborCard {
                    GibborSectionLabel("Last incident")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        incidentPreview,
                        fontSize = 11.sp,
                        color = GibborDark,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Menu: Configuracion ───────────────────────────────────

            GibborCollapsible(
                title = "Configuration",
                expanded = configExpanded,
                onToggle = { configExpanded = !configExpanded }
            ) {
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("Backend URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Menu: Bluetooth ───────────────────────────────────────

            GibborCollapsible(
                title = "Bluetooth",
                expanded = btExpanded,
                onToggle = { btExpanded = !btExpanded }
            ) {
                Text(
                    controller.statusText,
                    fontSize = 12.sp,
                    color = GibborMid
                )
                Spacer(Modifier.height(12.dp))
                GibborOutlinedButton(
                    text = "Enable Bluetooth",
                    onClick = { controller.ensureBluetoothEnabled() }
                )
                Spacer(Modifier.height(8.dp))
                GibborOutlinedButton(
                    text = if (controller.isConnecting) "Connecting..." else "Gibby Button",
                    onClick = { controller.connectToEsp32() },
                    enabled = !controller.isConnecting
                )
                Spacer(Modifier.height(8.dp))
                GibborOutlinedButton(
                    text = "Disconnect",
                    onClick = { controller.disconnectFromEsp32() }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Menu: Registro ────────────────────────────────────────

            GibborCollapsible(
                title = "Activity log",
                expanded = logExpanded,
                onToggle = { logExpanded = !logExpanded }
            ) {
                Text(
                    controller.logText,
                    fontSize = 11.sp,
                    color = GibborCharcoal,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
