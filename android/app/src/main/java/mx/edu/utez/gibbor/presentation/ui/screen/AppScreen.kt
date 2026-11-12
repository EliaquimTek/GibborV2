package mx.edu.utez.gibbor.presentation.ui.screen

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.data.recording.CameraPreviewHolder
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.components.ChainState
import mx.edu.utez.gibbor.presentation.ui.components.GibborNavigationBar
import mx.edu.utez.gibbor.presentation.ui.components.GibborTab
import mx.edu.utez.gibbor.presentation.ui.components.chainState
import mx.edu.utez.gibbor.presentation.ui.screen.demo.DemoScreen
import mx.edu.utez.gibbor.presentation.ui.screen.home.EmergencyActiveScreen
import mx.edu.utez.gibbor.presentation.ui.screen.home.HomeScreen
import mx.edu.utez.gibbor.presentation.ui.screen.onboarding.OnboardingScreen
import mx.edu.utez.gibbor.presentation.ui.screen.settings.SettingsSheet
import mx.edu.utez.gibbor.presentation.ui.theme.Motion
import mx.edu.utez.gibbor.presentation.viewmodel.EmergencySmsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(controller: MainScreenController, smsViewModel: EmergencySmsViewModel) {
    var email by remember { mutableStateOf("") }
    var authStatus by remember {
        mutableStateOf(if (controller.isAuthenticated) "Session started" else "No autenticado")
    }
    var lastHandledDemoTrigger by remember { mutableIntStateOf(controller.demoTriggerCounter) }
    var demoExecutionId by remember { mutableIntStateOf(0) }
    var demoStartIncidentPreview by remember { mutableStateOf("Ninguno") }
    var demoStartChainStatus by remember { mutableStateOf("") }
    var demoStartLogLength by remember { mutableIntStateOf(0) }
    var dismissedDemoExecutionId by remember { mutableIntStateOf(0) }

    // El flujo de pánico (ubicación → grabación → SMS → blockchain) corre en
    // ProtectionService para que funcione con la app cerrada; aquí solo se refleja.
    val busy = controller.busy
    val onChainStatus = controller.onChainStatus
    val incidentPreview = controller.incidentPreview

    /**
     * Disparos hechos desde la app (SOS, DEMO, control remoto). Pide los permisos que
     * falten y delega al servicio. El botón físico lo atiende el servicio directamente.
     */
    fun handleHardwareTrigger(source: String, useFrontCamera: Boolean) {
        if (!controller.isAuthenticated) {
            controller.appendLog("$source -> authenticate first")
            return
        }
        if (controller.busy || controller.isRecording) {
            controller.appendLog("$source -> busy, trigger ignored")
            return
        }
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
        controller.triggerPanic(source, useFrontCamera)
    }

    fun beginDemoExecution() {
        demoStartIncidentPreview = controller.incidentPreview
        demoStartChainStatus = controller.onChainStatus
        demoStartLogLength = controller.logText.length
        demoExecutionId++
    }

    LaunchedEffect(controller.demoTriggerCounter) {
        if (controller.demoTriggerCounter == lastHandledDemoTrigger) return@LaunchedEffect
        lastHandledDemoTrigger = controller.demoTriggerCounter
        if (!controller.busy) beginDemoExecution()
        handleHardwareTrigger("REMOTE", useFrontCamera = true)
    }

    var otpCode by remember { mutableStateOf("") }
    var otpRequested by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(GibborTab.GIBBOR) }
    var recordingElapsedSeconds by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(controller.isRecording, controller.recordingStartedAt) {
        if (controller.isRecording) {
            // El inicio lo fija el servicio: al abrir la app a mitad de una grabación el contador es correcto
            val recordingStartedAt = controller.recordingStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            while (true) {
                recordingElapsedSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000
                delay(1000)
            }
        } else {
            recordingElapsedSeconds = 0L
        }
    }

    LaunchedEffect(selectedTab) {
        controller.isDemoActive = selectedTab == GibborTab.DEMO
    }

    val onStopRecording: () -> Unit = { controller.stopRecording() }

    val onManualIncident: () -> Unit = { handleHardwareTrigger("MANUAL", useFrontCamera = false) }

    // ─── Vista previa selfie (BRIEF 2) ─────────────────────────────────────
    // Un único PreviewView registrado en CameraPreviewHolder; ProtectionService lo usa solo con la
    // cámara frontal (DEMO/REMOTE). Se muestra dentro de "Emergencia activa".
    val context = LocalContext.current
    val selfiePreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(selfiePreviewView) {
        val provider = selfiePreviewView.surfaceProvider
        CameraPreviewHolder.surfaceProvider = provider
        onDispose {
            if (CameraPreviewHolder.surfaceProvider === provider) CameraPreviewHolder.surfaceProvider = null
        }
    }

    // ¿El disparo en curso vino de DEMO/REMOTE (cámara frontal)? Solo afecta a la presentación.
    var selfieRun by remember { mutableStateOf(false) }
    LaunchedEffect(demoExecutionId) { if (demoExecutionId > 0) selfieRun = true }
    LaunchedEffect(controller.triggerCounter) { if (controller.triggerCounter > 0) selfieRun = false }

    // Snackbar al terminar la grabación.
    var wasRecording by remember { mutableStateOf(controller.isRecording) }
    LaunchedEffect(controller.isRecording) {
        val stopped = wasRecording && !controller.isRecording
        wasRecording = controller.isRecording
        if (stopped) snackbarHostState.showSnackbar("Evidencia guardada. Anclando en blockchain…")
    }

    // Haptic de éxito / error del registro on-chain.
    val chain = chainState(onChainStatus)
    var lastChain by remember { mutableStateOf(chain) }
    LaunchedEffect(chain) {
        if (chain != lastChain) {
            when (chain) {
                ChainState.Success -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                ChainState.Error -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
            lastChain = chain
        }
    }

    val showMessage: (String) -> Unit = { message ->
        uiScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = controller.isAuthenticated,
            transitionSpec = { Motion.fadeThrough() },
            modifier = Modifier
                .fillMaxSize()
                .then(if (controller.isRecording) Modifier.clearAndSetSemantics { } else Modifier),
            label = "GIBBOR auth",
        ) { authenticated ->
            if (!authenticated) {
                OnboardingScreen(
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
                    onChangeEmail = {
                        otpRequested = false
                        otpCode = ""
                    },
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!controller.isRecording) {
                            GibborNavigationBar(
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                            )
                        }
                    },
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { Motion.fadeThrough() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        label = "GIBBOR tabs",
                    ) { tab ->
                        when (tab) {
                            GibborTab.GIBBOR -> HomeScreen(
                                controller = controller,
                                busy = busy,
                                onChainStatus = onChainStatus,
                                incidentPreview = incidentPreview,
                                onCreateIncident = {
                                    selfieRun = false
                                    onManualIncident()
                                },
                                onOpenSettings = { showSettings = true },
                                onShowMessage = showMessage,
                            )

                            GibborTab.DEMO -> DemoScreen(
                                controller = controller,
                                busy = busy,
                                onChainStatus = onChainStatus,
                                incidentPreview = incidentPreview,
                                demoExecutionId = demoExecutionId,
                                demoStartIncidentPreview = demoStartIncidentPreview,
                                demoStartChainStatus = demoStartChainStatus,
                                demoStartLogLength = demoStartLogLength,
                                dismissedDemoExecutionId = dismissedDemoExecutionId,
                                onResetDemo = { dismissedDemoExecutionId = demoExecutionId },
                                onTrigger = {
                                    if (!controller.busy) beginDemoExecution()
                                    handleHardwareTrigger("DEMO", useFrontCamera = true)
                                },
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controller.isRecording,
            enter = Motion.fadeThroughEnter,
            exit = Motion.fadeThroughExit,
        ) {
            EmergencyActiveScreen(
                elapsedSeconds = recordingElapsedSeconds,
                selfiePreview = if (selfieRun) selfiePreviewView else null,
                onStopRecording = onStopRecording,
            )
        }

        if (showSettings && controller.isAuthenticated && !controller.isRecording) {
            SettingsSheet(
                controller = controller,
                backendUrl = controller.backendUrl,
                onBackendUrlChange = controller::updateBackendUrl,
                emergencyContacts = smsViewModel.contacts,
                validContactCount = smsViewModel.validContactCount,
                onEmergencyContactsChange = smsViewModel::updateContacts,
                smsPermissionGranted = smsViewModel.smsPermissionGranted,
                authStatus = authStatus,
                onDismiss = { showSettings = false },
                onSendTestSms = { smsViewModel.sendTestSms(controller.authenticatedEmail) },
                testSmsStatus = smsViewModel.testSmsStatus,
            )
        }
    }
}
