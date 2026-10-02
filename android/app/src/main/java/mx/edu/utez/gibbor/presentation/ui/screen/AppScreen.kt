package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.BuildConfig
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.GibborBackground
import mx.edu.utez.gibbor.presentation.ui.components.GibborBottomBar
import mx.edu.utez.gibbor.presentation.ui.components.GibborTab

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
    var lastHandledDemoTrigger by remember { mutableIntStateOf(controller.demoTriggerCounter) }
    var demoExecutionId by remember { mutableIntStateOf(0) }
    var demoStartIncidentPreview by remember { mutableStateOf("Ninguno") }
    var demoStartChainStatus by remember { mutableStateOf("") }
    var demoStartLogLength by remember { mutableIntStateOf(0) }
    var dismissedDemoExecutionId by remember { mutableIntStateOf(0) }
    var incidentPreview by remember { mutableStateOf("Ninguno") }
    var onChainStatus by remember { mutableStateOf("") }

    /**
     * Flujo completo: ubicación → draft → backend → on-chain
     * authStatus se usa SOLO para display. isAuthenticated controla el flujo.
     */
    suspend fun createAndSendIncident(source: String, useFrontCamera: Boolean) {
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
                controller.startAudioRecording(draft.incidentId, useFrontCamera)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            onChainStatus = "❌ Error: $errorMsg"
            authStatus = "❌ Error: $errorMsg"
            controller.appendLog("$source -> EXCEPTION: $errorMsg")
            // ⚠️ isAuthenticated NO se toca — sigue siendo true
        }
    }

    suspend fun handleHardwareTrigger(source: String, useFrontCamera: Boolean) {
        if (source != "ESP32" && busy) {
            controller.appendLog("$source -> busy, trigger ignored")
            return
        }
        if (!controller.isAuthenticated) {
            controller.appendLog("$source -> authenticate first")
            return
        }

        busy = true
        try {
            createAndSendIncident(source, useFrontCamera)
        } catch (e: Exception) {
            controller.appendLog("$source -> error: " + e.message)
        } finally {
            busy = false
        }
    }

    fun beginDemoExecution() {
        demoStartIncidentPreview = incidentPreview
        demoStartChainStatus = onChainStatus
        demoStartLogLength = controller.logText.length
        demoExecutionId++
    }

    // ─── Trigger ESP32 ────────────────────────────────────────────────────

    LaunchedEffect(controller.triggerCounter) {
        if (controller.triggerCounter <= 0 || controller.triggerCounter == lastHandledTrigger) return@LaunchedEffect

        lastHandledTrigger = controller.triggerCounter

        handleHardwareTrigger("ESP32", useFrontCamera = false)
    }

    LaunchedEffect(controller.demoTriggerCounter) {
        if (controller.demoTriggerCounter == lastHandledDemoTrigger) return@LaunchedEffect
        lastHandledDemoTrigger = controller.demoTriggerCounter
        if (!busy) beginDemoExecution()
        handleHardwareTrigger("REMOTE", useFrontCamera = true)
    }

    var otpCode by remember { mutableStateOf("") }
    var otpRequested by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(GibborTab.OPERATIONAL) }
    var recordingElapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(controller.isRecording) {
        if (controller.isRecording) {
            val recordingStartedAt = System.currentTimeMillis()
            recordingElapsedSeconds = 0L
            while (true) {
                delay(1000)
                recordingElapsedSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000
            }
        } else {
            recordingElapsedSeconds = 0L
        }
    }

    LaunchedEffect(selectedTab) {
        controller.isDemoActive = selectedTab == GibborTab.DEMO
    }

    val onStopRecording: () -> Unit = {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                GibborBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                )
            },
        ) { innerPadding ->
            GibborBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Crossfade(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    label = "GIBBOR tabs",
                ) { tab ->
                    when (tab) {
                        GibborTab.OPERATIONAL -> OperationalScreen(
                            controller = controller,
                            email = email,
                            onEmailChange = {
                                email = it
                                otpRequested = false
                                otpCode = ""
                            },
                            otpCode = otpCode,
                            otpRequested = otpRequested,
                            onRequestOtp = {
                                val code = (100000..999999).random().toString()
                                otpCode = code
                                otpRequested = true
                                controller.appendLog("OTP generated for ${email.trim()}")
                            },
                            onSignIn = {
                                val norm = email.trim()
                                    .replace("[^a-zA-Z0-9@._-]".toRegex(), "")
                                    .lowercase()
                                if (norm.isNotBlank()) {
                                    controller.startSession(norm)
                                    authStatus = "Session started"
                                    controller.appendLog("Auth: session started as $norm")
                                }
                            },
                            backendUrl = backendUrl,
                            onBackendUrlChange = { backendUrl = it },
                            busy = busy,
                            recordingElapsedSeconds = recordingElapsedSeconds,
                            authStatus = authStatus,
                            onChainStatus = onChainStatus,
                            incidentPreview = incidentPreview,
                            onCreateIncident = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    busy = true
                                    try { createAndSendIncident("MANUAL", useFrontCamera = false) }
                                    catch (e: Exception) { controller.appendLog("MANUAL -> error: ${e.message}") }
                                    finally { busy = false }
                                }
                            },
                            onStopRecording = onStopRecording,
                        )

                        GibborTab.DEMO -> DemoScreen(
                            controller = controller,
                            busy = busy,
                            onChainStatus = onChainStatus,
                            incidentPreview = incidentPreview,
                            recordingElapsedSeconds = recordingElapsedSeconds,
                            demoExecutionId = demoExecutionId,
                            demoStartIncidentPreview = demoStartIncidentPreview,
                            demoStartChainStatus = demoStartChainStatus,
                            demoStartLogLength = demoStartLogLength,
                            dismissedDemoExecutionId = dismissedDemoExecutionId,
                            onResetDemo = { dismissedDemoExecutionId = demoExecutionId },
                            onTrigger = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    if (!busy) beginDemoExecution()
                                    handleHardwareTrigger("DEMO", useFrontCamera = true)
                                }
                            },
                            onStopRecording = onStopRecording,
                        )
                    }
                }
            }
        }

        SplashOverlay(modifier = Modifier.align(Alignment.Center))
    }
}
