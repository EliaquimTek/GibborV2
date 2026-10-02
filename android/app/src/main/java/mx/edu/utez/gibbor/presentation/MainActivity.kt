package mx.edu.utez.gibbor.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.crossmint.kotlin.compose.CrossmintSDKProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.edu.utez.gibbor.BuildConfig
import mx.edu.utez.gibbor.core.util.HashUtils
import mx.edu.utez.gibbor.data.bluetooth.Esp32BleClient
import mx.edu.utez.gibbor.data.location.LocationDataSource
import mx.edu.utez.gibbor.data.recording.AudioEvidenceRecorder
import mx.edu.utez.gibbor.data.recording.RecordingService
import mx.edu.utez.gibbor.data.remote.HttpIncidentRepository
import mx.edu.utez.gibbor.domain.model.BackendResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft
import mx.edu.utez.gibbor.domain.repository.IncidentRepository
import mx.edu.utez.gibbor.domain.usecase.BuildIncidentDraftUseCase
import mx.edu.utez.gibbor.presentation.ui.screen.AppScreen
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), MainScreenController {

    companion object {
        private const val STELLAR_CONTRACT_ID =
            "CB36FLNBESA7WJIQTJMVKUAE63IAGZ75Q65XER4MD4MF5VCAAO4RGMLE"
    }

    // ─── Dependencias (capas data / domain) ───────────────────────────────

    private val incidentRepository: IncidentRepository = HttpIncidentRepository()
    private val buildIncidentDraftUseCase = BuildIncidentDraftUseCase()
    private lateinit var locationDataSource: LocationDataSource
    private lateinit var audioRecorder: AudioEvidenceRecorder

    private val bluetoothClient = Esp32BleClient(
        context = this,
        postToMain = { block -> runOnUiThread(block) },
        callbacks = object : Esp32BleClient.Callbacks {
            override fun onLog(message: String) = appendLog(message)
            override fun onStatus(status: String) { statusText = status }
            override fun onTrigger() { triggerCounter++ }
        }
    )

    // ─── Estado de UI ─────────────────────────────────────────────────────

    override var statusText by mutableStateOf("Status: Disconnected")
        private set
    override var logText by mutableStateOf("Esperando...\n")
        private set
    override var triggerCounter by mutableIntStateOf(0)
        private set
    override var demoTriggerCounter by mutableIntStateOf(0)
        private set
    override var lastDemoKey by mutableStateOf("")
        private set
    override var isDemoActive by mutableStateOf(false)
    private var lastDemoTriggerAtMillis = 0L

    // ─── Bluetooth state ──────────────────────────────────────────────────

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pendingConnect = false

    override val isConnecting: Boolean
        get() = bluetoothClient.isConnecting

    // ─── Audio recording state ─────────────────────────────────────────────

    override var isRecording by mutableStateOf(false)
        private set

    // ─── Auth state (separado de UI) ──────────────────────────────────────

    // Respaldado por estado de Compose para que la UI cambie de Onboarding a Inicio al iniciar sesión.
    override var isAuthenticated by mutableStateOf(false)
        private set
    override var authenticatedEmail by mutableStateOf("")
        private set

    // ─── Contexto de sesión accesible desde receivers ─────────────────────

    @Volatile override var lastBackendUrl: String = ""
    @Volatile override var lastIncidentForEvidence: String = ""

    // ─── Video recording broadcast receiver ───────────────────────────────

    private val recordingDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id   = intent?.getStringExtra(RecordingService.EXTRA_INCIDENT_ID) ?: return
            val path = intent.getStringExtra(RecordingService.EXTRA_FILE_PATH) ?: ""
            val hash = intent.getStringExtra(RecordingService.EXTRA_SHA256) ?: ""
            appendLog("VIDEO: Recording finished [$id]")
            appendLog("VIDEO: File: ${File(path).name}")
            appendLog("VIDEO: SHA-256: $hash")

            if (hash.isNotEmpty() && lastBackendUrl.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val result = sendEvidenceToBackend(lastBackendUrl, id, "video", hash)
                    withContext(Dispatchers.Main) {
                        if (result) appendLog("VIDEO: Hash anchored on blockchain")
                        else        appendLog("VIDEO: Error anchoring hash on blockchain")
                    }
                }
            }
        }
    }

    // ─── Permission launchers ─────────────────────────────────────────────

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                appendLog("Bluetooth enabled.")
                if (pendingConnect) {
                    pendingConnect = false
                    connectToEsp32()
                }
            } else {
                pendingConnect = false
                appendLog("User did not enable Bluetooth.")
            }
        }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { granted -> !granted }.keys
            if (denied.isEmpty()) {
                appendLog("Bluetooth permissions granted.")
                if (pendingConnect) {
                    pendingConnect = false
                    connectToEsp32()
                }
            } else {
                pendingConnect = false
                appendLog("Bluetooth permissions denied: ${denied.joinToString()}")
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                appendLog("Location permission granted.")
            } else {
                appendLog("Location permission denied.")
            }
        }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            // Salida: el logo crece 1 → 1.08 mientras el splash se desvanece (300 ms).
            val exitEasing = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            splash.iconView.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(300L)
                .setInterpolator(exitEasing)
                .start()
            splash.view.animate()
                .alpha(0f)
                .setDuration(300L)
                .setInterpolator(exitEasing)
                .withEndAction { splash.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            statusText = "Status: This device does not support Bluetooth"
            appendLog("Bluetooth not supported on this device.")
        }

        locationDataSource = LocationDataSource(this)
        audioRecorder = AudioEvidenceRecorder(this)

        ContextCompat.registerReceiver(
            this,
            recordingDoneReceiver,
            IntentFilter(RecordingService.BROADCAST_DONE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            CrossmintSDKProvider
                .Builder(BuildConfig.CROSSMINT_API_KEY)
                .build {
                    GibborTheme { AppScreen(controller = this@MainActivity) }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioRecording()
        disconnectFromEsp32()
        unregisterReceiver(recordingDoneReceiver)
    }

    // ─── Logging / sesión ─────────────────────────────────────────────────

    override fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logText += "[$time] $message\n"
    }

    override fun clearLog() {
        logText = ""
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val demoKeyCodes = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        if (isDemoActive && event.keyCode in demoKeyCodes) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                lastDemoKey = KeyEvent.keyCodeToString(event.keyCode)
                val now = SystemClock.elapsedRealtime()
                if (lastDemoTriggerAtMillis == 0L || now - lastDemoTriggerAtMillis >= 1500L) {
                    lastDemoTriggerAtMillis = now
                    demoTriggerCounter++
                }
            }
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun startSession(email: String) {
        isAuthenticated = true
        authenticatedEmail = email
    }

    // ─── Bluetooth permissions ────────────────────────────────────────────

    override fun ensureBluetoothEnabled() {
        val adapter = bluetoothAdapter ?: run {
            appendLog("No Bluetooth adapter.")
            return
        }

        if (adapter.isEnabled) {
            appendLog("Bluetooth already enabled.")
            return
        }

        val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableIntent)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = isGranted(Manifest.permission.BLUETOOTH_CONNECT)
            val hasScan = isGranted(Manifest.permission.BLUETOOTH_SCAN)
            val hasLocation = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

            hasConnect && hasScan && hasLocation
        } else {
            true
        }
    }

    override fun requestBluetoothPermissions() {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    // ─── Bluetooth LE connection (ESP32-C3) ────────────────────────────────

    override fun connectToEsp32() {
        // Guard: evitar conexiones simultáneas
        if (bluetoothClient.isConnecting) {
            appendLog("Connection already in progress.")
            return
        }

        val adapter = bluetoothAdapter ?: run {
            appendLog("No Bluetooth adapter available.")
            return
        }

        if (!hasBluetoothPermissions()) {
            pendingConnect = true
            requestBluetoothPermissions()
            return
        }

        if (!adapter.isEnabled) {
            pendingConnect = true
            ensureBluetoothEnabled()
            return
        }

        // Escanea por BLE el ESP32-C3 (no requiere emparejarlo en Ajustes)
        bluetoothClient.connect(adapter)
    }

    /**
     * Desconexión limpia — cierra streams, socket, y espera a que el hilo muera.
     */
    override fun disconnectFromEsp32() {
        bluetoothClient.disconnect(silent = false)
    }

    // ─── Permisos de grabación / ubicación ────────────────────────────────

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasAudioPermission(): Boolean = isGranted(Manifest.permission.RECORD_AUDIO)

    override fun hasCameraPermission(): Boolean = isGranted(Manifest.permission.CAMERA)

    override fun hasLocationPermission(): Boolean = locationDataSource.hasLocationPermission()

    override fun requestLocationPermission() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ─── Audio / video recording ──────────────────────────────────────────

    override fun startAudioRecording(incidentId: String, useFrontCamera: Boolean) {
        if (!hasAudioPermission()) {
            appendLog("AUDIO: Missing RECORD_AUDIO permission")
            return
        }

        // Detener grabación previa si existe
        stopAudioRecording()

        try {
            val audioFile = audioRecorder.start(incidentId)
            isRecording = true
            appendLog("AUDIO: Recording -> ${audioFile.name}")

            // Iniciar grabación de video en paralelo (ForegroundService con CameraX)
            if (hasCameraPermission()) {
                val svcIntent = Intent(this, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra(RecordingService.EXTRA_INCIDENT_ID, incidentId)
                    putExtra(RecordingService.EXTRA_USE_FRONT_CAMERA, useFrontCamera)
                }
                ContextCompat.startForegroundService(this, svcIntent)
                if (useFrontCamera) appendLog("VIDEO: Starting video recording (front camera)...")
                else appendLog("VIDEO: Starting video recording...")
            } else {
                appendLog("VIDEO: No CAMERA permission — audio only")
            }
        } catch (e: Exception) {
            appendLog("AUDIO ERROR: ${e.message}")
            isRecording = false
        }
    }

    override fun stopAudioRecording(): String? {
        if (!audioRecorder.isActive) return null

        val file = audioRecorder.stop(onError = ::appendLog)
        isRecording = false

        // Detener grabación de video
        stopService(Intent(this, RecordingService::class.java))

        if (file != null && file.exists() && file.length() > 0) {
            val hash = HashUtils.sha256File(file)
            appendLog("AUDIO: Recording stopped. File: ${file.name}")
            appendLog("AUDIO: Size: ${file.length()} bytes")
            appendLog("AUDIO: SHA-256: $hash")
            return hash
        }

        appendLog("AUDIO: File empty or not found.")
        return null
    }
    // ─── Incidente (ubicación + backend) ──────────────────────────────────

    override suspend fun getCurrentPhoneLocation(): Location = locationDataSource.getCurrentLocation()

    override fun buildIncidentDraft(location: Location): IncidentDraft =
        buildIncidentDraftUseCase(location.latitude, location.longitude)

    /**
     * Envía el incidente al BACKEND (no a Crossmint directamente).
     */
    override suspend fun sendIncidentToBackend(
        backendUrl: String,
        email: String,
        draft: IncidentDraft
    ): BackendResult = incidentRepository.sendIncident(backendUrl, email, draft)

    /**
     * Polling del estado de TX al backend.
     */
    override suspend fun pollBackendStatus(
        backendUrl: String,
        email: String,
        txId: String
    ): BackendResult = incidentRepository.pollStatus(backendUrl, email, txId)

    /**
     * Ancla el hash del archivo de evidencia en el contrato Soroban.
     * Retorna true si se ancló correctamente.
     */
    override suspend fun sendEvidenceToBackend(
        backendUrl: String,
        incidentId: String,
        mediaType: String,
        mediaHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        val result = incidentRepository.sendEvidence(backendUrl, authenticatedEmail, incidentId, mediaType, mediaHash)
        when {
            result.exceptionMessage != null ->
                appendLog("EVIDENCE [$mediaType] EXCEPTION: ${result.exceptionMessage}")
            result.success ->
                appendLog("EVIDENCE [$mediaType]: TX=${result.txHash}")
            else ->
                appendLog("EVIDENCE [$mediaType] ERROR: ${result.error}")
        }
        result.success
    }
}
