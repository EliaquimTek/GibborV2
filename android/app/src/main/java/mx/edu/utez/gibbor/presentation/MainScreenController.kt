package mx.edu.utez.gibbor.presentation

/**
 * Estado y acciones que la pantalla principal necesita de la Activity.
 *
 * El flujo de pánico, la grabación y la conexión BLE viven en
 * `ProtectionService` (siguen funcionando con la app cerrada); aquí solo se
 * refleja su estado y se le envían órdenes.
 */
interface MainScreenController {

    // ─── Estado ───────────────────────────────────────────────────────────
    val statusText: String
    val logText: String
    /** Pánicos recibidos del botón físico (para saber el origen en la UI). */
    val triggerCounter: Int
    val demoTriggerCounter: Int
    val lastDemoKey: String
    var isDemoActive: Boolean
    val isRecording: Boolean
    /** Inicio de la grabación en curso (`System.currentTimeMillis()`), 0 si no graba. */
    val recordingStartedAt: Long
    val isAuthenticated: Boolean
    val authenticatedEmail: String
    val isConnecting: Boolean
    /** Se está generando y registrando un incidente. */
    val busy: Boolean
    val onChainStatus: String
    val incidentPreview: String
    val backendUrl: String
    /** El servicio de protección está activo en segundo plano. */
    val isProtectionActive: Boolean

    // ─── Sesión / log ─────────────────────────────────────────────────────
    fun appendLog(message: String)
    fun clearLog()
    fun startSession(email: String)
    fun updateBackendUrl(url: String)

    // ─── Permisos ─────────────────────────────────────────────────────────
    fun hasAudioPermission(): Boolean
    fun hasCameraPermission(): Boolean
    fun hasLocationPermission(): Boolean
    fun requestBluetoothPermissions()
    fun requestLocationPermission()
    fun requestSmsPermission()

    // ─── Emergencia ───────────────────────────────────────────────────────
    /** Ejecuta el flujo completo de pánico en el servicio de protección. */
    fun triggerPanic(source: String, useFrontCamera: Boolean = false)
    fun stopRecording()

    // ─── Bluetooth ────────────────────────────────────────────────────────
    fun ensureBluetoothEnabled()
    /** Activa la protección: conecta el botón y lo mantiene conectado en segundo plano. */
    fun connectToEsp32()
    /** Desactiva la protección y desconecta el botón. */
    fun disconnectFromEsp32()
}
