package mx.edu.utez.gibbor.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estado de la protección, producido por [ProtectionService] y observado por la UI. */
data class ProtectionState(
    /** El servicio está corriendo en primer plano y escuchando el botón. */
    val protectionActive: Boolean = false,
    val statusText: String = "Status: Disconnected",
    val isConnecting: Boolean = false,
    /** Se está generando y registrando el incidente. */
    val busy: Boolean = false,
    val isRecording: Boolean = false,
    /** `System.currentTimeMillis()` al empezar a grabar (0 si no graba). */
    val recordingStartedAt: Long = 0L,
    val onChainStatus: String = "",
    val incidentPreview: String = "Ninguno",
    /** Disparos recibidos del botón físico (la UI lo usa para saber el origen). */
    val esp32TriggerCount: Int = 0,
    /** Arrancó sin acceso a cámara/micrófono/ubicación (reinicio del sistema en segundo plano). */
    val limitedMode: Boolean = false,
)

/**
 * Puente entre el servicio (que vive sin la UI) y la Activity (que puede no existir).
 * Es un singleton de proceso: si Android mata el proceso se reinicia vacío.
 */
object ProtectionHub {

    private const val MAX_LOG_CHARS = 60_000

    private val _state = MutableStateFlow(ProtectionState())
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    private val _log = MutableStateFlow("Esperando...\n")
    val log: StateFlow<String> = _log.asStateFlow()

    fun update(transform: (ProtectionState) -> ProtectionState) = _state.update(transform)

    fun log(message: String) {
        // También a logcat (adb logcat -s GIBBOR) para diagnosticar con la app cerrada
        try {
            android.util.Log.i("GIBBOR", message)
        } catch (_: RuntimeException) {
            // Tests JVM sin Android
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _log.update { (it + "[$time] $message\n").takeLast(MAX_LOG_CHARS) }
    }

    fun clearLog() {
        _log.value = ""
    }
}
