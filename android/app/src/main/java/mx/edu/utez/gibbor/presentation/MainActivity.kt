package mx.edu.utez.gibbor.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.crossmint.kotlin.compose.CrossmintSDKProvider
import kotlinx.coroutines.launch
import mx.edu.utez.gibbor.BuildConfig
import mx.edu.utez.gibbor.data.location.LocationDataSource
import mx.edu.utez.gibbor.data.session.ProtectionPrefs
import mx.edu.utez.gibbor.presentation.ui.screen.AppScreen
import mx.edu.utez.gibbor.presentation.ui.theme.GibborTheme
import mx.edu.utez.gibbor.presentation.viewmodel.EmergencySmsViewModel
import mx.edu.utez.gibbor.service.ProtectionHub
import mx.edu.utez.gibbor.service.ProtectionService
import mx.edu.utez.gibbor.service.ProtectionState

class MainActivity : ComponentActivity(), MainScreenController {

    // ─── Dependencias ─────────────────────────────────────────────────────

    private lateinit var prefs: ProtectionPrefs
    private lateinit var locationDataSource: LocationDataSource
    private val emergencySmsViewModel: EmergencySmsViewModel by viewModels()

    // ─── Estado reflejado del servicio de protección ─────────────────────

    private var protection by mutableStateOf(ProtectionHub.state.value)
    override var logText by mutableStateOf(ProtectionHub.log.value)
        private set

    override val statusText: String get() = protection.statusText
    override val triggerCounter: Int get() = protection.esp32TriggerCount
    override val isRecording: Boolean get() = protection.isRecording
    override val recordingStartedAt: Long get() = protection.recordingStartedAt
    override val isConnecting: Boolean get() = protection.isConnecting
    override val busy: Boolean get() = protection.busy
    override val onChainStatus: String get() = protection.onChainStatus
    override val incidentPreview: String get() = protection.incidentPreview
    override val isProtectionActive: Boolean get() = protection.protectionActive

    // ─── Estado propio de la UI ───────────────────────────────────────────

    override var demoTriggerCounter by mutableIntStateOf(0)
        private set
    override var lastDemoKey by mutableStateOf("")
        private set
    override var isDemoActive by mutableStateOf(false)
    private var lastDemoTriggerAtMillis = 0L

    override var isAuthenticated by mutableStateOf(false)
        private set
    override var authenticatedEmail by mutableStateOf("")
        private set
    override var backendUrl by mutableStateOf(BuildConfig.BACKEND_URL)
        private set

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pendingConnect = false

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
            emergencySmsViewModel.refreshSmsPermission()
            val denied = result.filterValues { granted -> !granted }.keys
            if (denied.isEmpty()) {
                appendLog("Permissions granted.")
            } else {
                appendLog("Permissions denied: ${denied.joinToString()}")
            }
            if (pendingConnect) {
                pendingConnect = false
                if (hasBluetoothPermissions()) connectToEsp32()
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            appendLog(if (granted) "Location permission granted." else "Location permission denied.")
        }

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            emergencySmsViewModel.refreshSmsPermission()
            appendLog(if (granted) "SMS permission granted." else "SMS permission denied.")
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

        prefs = ProtectionPrefs(this)
        locationDataSource = LocationDataSource(this)
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            appendLog("Bluetooth not supported on this device.")
        }

        // Sesión persistida: el servicio la necesita aunque la app esté cerrada
        backendUrl = prefs.backendUrl
        prefs.email.takeIf { it.isNotBlank() }?.let { saved ->
            isAuthenticated = true
            authenticatedEmail = saved
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ProtectionHub.state.collect { protection = it } }
                launch { ProtectionHub.log.collect { logText = it } }
            }
        }

        setContent {
            CrossmintSDKProvider
                .Builder(BuildConfig.CROSSMINT_API_KEY)
                .build {
                    GibborTheme { AppScreen(controller = this@MainActivity, smsViewModel = emergencySmsViewModel) }
                }
        }
    }

    override fun onStart() {
        super.onStart()
        // Si el sistema detuvo el servicio, al abrir la app se reanuda con acceso completo
        // a cámara, micrófono y ubicación (solo se concede arrancando en primer plano).
        resumeProtectionIfNeeded()
    }

    // La Activity ya no desconecta el botón ni detiene la grabación al cerrarse:
    // eso lo gestiona ProtectionService.

    private fun resumeProtectionIfNeeded() {
        if (!isAuthenticated || !prefs.protectionEnabled) return
        if (!hasBluetoothPermissions() || bluetoothAdapter?.isEnabled != true) return
        val s: ProtectionState = ProtectionHub.state.value
        if (!ProtectionService.isRunning || s.limitedMode) ProtectionService.start(this)
    }

    // ─── Logging / sesión ─────────────────────────────────────────────────

    override fun appendLog(message: String) = ProtectionHub.log(message)

    override fun clearLog() = ProtectionHub.clearLog()

    override fun startSession(email: String) {
        prefs.email = email
        isAuthenticated = true
        authenticatedEmail = email
    }

    override fun updateBackendUrl(url: String) {
        backendUrl = url
        prefs.backendUrl = url
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

    // ─── Permisos ─────────────────────────────────────────────────────────

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(Manifest.permission.BLUETOOTH_CONNECT) &&
                isGranted(Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun requestBluetoothPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        bluetoothPermissionLauncher.launch(perms.toTypedArray())
    }

    override fun hasAudioPermission(): Boolean = isGranted(Manifest.permission.RECORD_AUDIO)

    override fun hasCameraPermission(): Boolean = isGranted(Manifest.permission.CAMERA)

    override fun hasLocationPermission(): Boolean = locationDataSource.hasLocationPermission()

    override fun requestLocationPermission() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun requestSmsPermission() {
        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
    }

    // ─── Bluetooth / protección ───────────────────────────────────────────

    override fun ensureBluetoothEnabled() {
        val adapter = bluetoothAdapter ?: run {
            appendLog("No Bluetooth adapter.")
            return
        }
        if (adapter.isEnabled) {
            appendLog("Bluetooth already enabled.")
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun connectToEsp32() {
        if (protection.isConnecting) {
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

        // Se arranca aquí, con la app en primer plano, para que el servicio conserve
        // cámara/micrófono/ubicación cuando la app se cierre.
        ProtectionService.start(this)
        requestBatteryExemptionOnce()
    }

    override fun disconnectFromEsp32() {
        ProtectionService.stopProtection(this)
    }

    /**
     * Pide (una sola vez) que Android no optimice la batería de GIBBOR. Sin esto,
     * algunos fabricantes cierran el servicio en segundo plano aunque sea de primer plano.
     */
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemptionOnce() {
        if (prefs.batteryPromptShown) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.batteryPromptShown = true
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            appendLog("Battery optimization settings not available: ${e.message}")
        }
    }

    // ─── Emergencia ───────────────────────────────────────────────────────

    override fun triggerPanic(source: String, useFrontCamera: Boolean) {
        ProtectionService.trigger(this, source, useFrontCamera)
    }

    override fun stopRecording() {
        ProtectionService.stopRecording(this)
    }
}
