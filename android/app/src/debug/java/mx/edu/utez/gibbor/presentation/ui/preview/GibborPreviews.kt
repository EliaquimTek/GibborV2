package mx.edu.utez.gibbor.presentation.ui.preview

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import mx.edu.utez.gibbor.domain.model.BackendResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft
import mx.edu.utez.gibbor.presentation.MainScreenController
import mx.edu.utez.gibbor.presentation.ui.screen.demo.DemoScreen
import mx.edu.utez.gibbor.presentation.ui.screen.home.EmergencyActiveScreen
import mx.edu.utez.gibbor.presentation.ui.screen.home.HomeScreen
import mx.edu.utez.gibbor.presentation.ui.screen.onboarding.OnboardingScreen
import mx.edu.utez.gibbor.presentation.ui.screen.settings.SettingsContent
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme

/** Controller falso solo para @Preview (source set debug: no se empaqueta en release). */
class FakeMainScreenController(
    override val statusText: String = "Status: Connected to GIBBOR",
    override val logText: String = "[12:00:01] Auth: session started as ana@correo.mx\n" +
        "[12:00:09] MANUAL -> incident_id=inc-1727890000000\n",
    override val isRecording: Boolean = false,
    override val isAuthenticated: Boolean = true,
    override val authenticatedEmail: String = "ana@correo.mx",
    override val isConnecting: Boolean = false,
    override val lastDemoKey: String = "",
    private val permissionsGranted: Boolean = true,
) : MainScreenController {
    override val triggerCounter: Int = 0
    override val demoTriggerCounter: Int = 0
    override var isDemoActive: Boolean = false
    override var lastBackendUrl: String = ""
    override var lastIncidentForEvidence: String = ""

    override fun appendLog(message: String) = Unit
    override fun clearLog() = Unit
    override fun startSession(email: String) = Unit
    override fun hasAudioPermission() = permissionsGranted
    override fun hasCameraPermission() = permissionsGranted
    override fun hasLocationPermission() = permissionsGranted
    override fun requestBluetoothPermissions() = Unit
    override fun requestLocationPermission() = Unit
    override suspend fun getCurrentPhoneLocation(): Location = error("preview")
    override fun buildIncidentDraft(location: Location): IncidentDraft = error("preview")
    override suspend fun sendIncidentToBackend(backendUrl: String, email: String, draft: IncidentDraft): BackendResult =
        error("preview")
    override suspend fun pollBackendStatus(backendUrl: String, email: String, txId: String): BackendResult =
        error("preview")
    override suspend fun sendEvidenceToBackend(backendUrl: String, incidentId: String, mediaType: String, mediaHash: String) =
        false
    override fun startAudioRecording(incidentId: String, useFrontCamera: Boolean) = Unit
    override fun stopAudioRecording(): String? = null
    override fun ensureBluetoothEnabled() = Unit
    override fun connectToEsp32() = Unit
    override fun disconnectFromEsp32() = Unit

    companion object {
        const val SAMPLE_INCIDENT = "id=inc-1727890000000\n" +
            "timestamp=1727890000\n" +
            "lat_e7=188532110\n" +
            "lon_e7=-991234560\n" +
            "hash=9f2c4e0b7a1d5c3e8f6a2b4d1c7e9a0b3f5d8c2e4a6b1d9f7c3e5a8b0d2f4c6e"
        const val SAMPLE_CHAIN_STATUS = "✅ Status: success\n" +
            "TX: 4b8e2f0c9d1a7e3b5c6f8a2d4e0b1c9f7a3e5d2b8c6f0a4e1d9b3c7f5a2e8d0b\n" +
            "Explorer: https://stellar.expert/explorer/testnet/tx/4b8e2f0c"
    }
}

@Composable
private fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    GibborTheme(darkTheme = dark) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

// ─── Onboarding ──────────────────────────────────────────────────────────

@Preview(name = "Onboarding · claro", widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingLight() = PreviewSurface(dark = false) {
    OnboardingScreen("", {}, "", false, {}, {}, {})
}

@Preview(name = "Onboarding · oscuro (código)", widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingDark() = PreviewSurface(dark = true) {
    OnboardingScreen("ana@correo.mx", {}, "482913", true, {}, {}, {})
}

// ─── Inicio ──────────────────────────────────────────────────────────────

@Preview(name = "Inicio · claro", widthDp = 360, heightDp = 800)
@Composable
private fun HomeLight() = PreviewSurface(dark = false) {
    HomeScreen(
        controller = FakeMainScreenController(),
        busy = false,
        onChainStatus = FakeMainScreenController.SAMPLE_CHAIN_STATUS,
        incidentPreview = FakeMainScreenController.SAMPLE_INCIDENT,
        onCreateIncident = {},
        onOpenSettings = {},
        onShowMessage = {},
    )
}

@Preview(name = "Inicio · oscuro (revisa protección)", widthDp = 360, heightDp = 800)
@Composable
private fun HomeDark() = PreviewSurface(dark = true) {
    HomeScreen(
        controller = FakeMainScreenController(statusText = "Status: Disconnected", permissionsGranted = false),
        busy = false,
        onChainStatus = "",
        incidentPreview = "Ninguno",
        onCreateIncident = {},
        onOpenSettings = {},
        onShowMessage = {},
    )
}

// ─── Emergencia activa ───────────────────────────────────────────────────

@Preview(name = "Emergencia · claro", widthDp = 360, heightDp = 800)
@Composable
private fun EmergencyLight() = PreviewSurface(dark = false) {
    EmergencyActiveScreen(elapsedSeconds = 83, selfiePreview = null, onStopRecording = {})
}

@Preview(name = "Emergencia · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun EmergencyDark() = PreviewSurface(dark = true) {
    EmergencyActiveScreen(elapsedSeconds = 5, selfiePreview = null, onStopRecording = {})
}

// ─── DEMO ────────────────────────────────────────────────────────────────

@Preview(name = "Demo · claro (en curso)", widthDp = 360, heightDp = 800)
@Composable
private fun DemoLight() = PreviewSurface(dark = false) {
    DemoScreen(
        controller = FakeMainScreenController(lastDemoKey = "KEYCODE_VOLUME_UP"),
        busy = true,
        onChainStatus = "Sending...",
        incidentPreview = FakeMainScreenController.SAMPLE_INCIDENT,
        demoExecutionId = 1,
        demoStartIncidentPreview = "Ninguno",
        demoStartChainStatus = "",
        demoStartLogLength = 0,
        dismissedDemoExecutionId = 0,
        onResetDemo = {},
        onTrigger = {},
    )
}

@Preview(name = "Demo · oscuro (registrada)", widthDp = 360, heightDp = 800)
@Composable
private fun DemoDark() = PreviewSurface(dark = true) {
    DemoScreen(
        controller = FakeMainScreenController(),
        busy = false,
        onChainStatus = FakeMainScreenController.SAMPLE_CHAIN_STATUS,
        incidentPreview = FakeMainScreenController.SAMPLE_INCIDENT,
        demoExecutionId = 1,
        demoStartIncidentPreview = "Ninguno",
        demoStartChainStatus = "",
        demoStartLogLength = 0,
        dismissedDemoExecutionId = 0,
        onResetDemo = {},
        onTrigger = {},
    )
}

// ─── Ajustes ─────────────────────────────────────────────────────────────

@Preview(name = "Ajustes · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SettingsLight() = PreviewSurface(dark = false) {
    SettingsContent(
        controller = FakeMainScreenController(),
        backendUrl = "http://10.0.2.2:3001",
        onBackendUrlChange = {},
        authStatus = "Session started",
    )
}

@Preview(name = "Ajustes · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun SettingsDark() = PreviewSurface(dark = true) {
    SettingsContent(
        controller = FakeMainScreenController(logText = ""),
        backendUrl = "http://10.0.2.2:3001",
        onBackendUrlChange = {},
        authStatus = "Incident registered on-chain",
    )
}
