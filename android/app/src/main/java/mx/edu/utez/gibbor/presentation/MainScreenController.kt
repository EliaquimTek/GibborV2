package mx.edu.utez.gibbor.presentation

import android.location.Location
import mx.edu.utez.gibbor.domain.model.BackendResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft

/**
 * Estado y acciones que la pantalla principal necesita de la Activity.
 * Las propiedades se leen dentro de la composición igual que antes
 * (cuando AppScreen era un método miembro de MainActivity).
 */
interface MainScreenController {

    // ─── Estado ───────────────────────────────────────────────────────────
    val statusText: String
    val logText: String
    val triggerCounter: Int
    val isRecording: Boolean
    val isAuthenticated: Boolean
    val authenticatedEmail: String
    val isConnecting: Boolean
    var lastBackendUrl: String
    var lastIncidentForEvidence: String

    // ─── Sesión / log ─────────────────────────────────────────────────────
    fun appendLog(message: String)
    fun startSession(email: String)

    // ─── Permisos ─────────────────────────────────────────────────────────
    fun hasAudioPermission(): Boolean
    fun hasCameraPermission(): Boolean
    fun hasLocationPermission(): Boolean
    fun requestBluetoothPermissions()
    fun requestLocationPermission()

    // ─── Incidente ────────────────────────────────────────────────────────
    suspend fun getCurrentPhoneLocation(): Location
    fun buildIncidentDraft(location: Location): IncidentDraft
    suspend fun sendIncidentToBackend(backendUrl: String, email: String, draft: IncidentDraft): BackendResult
    suspend fun pollBackendStatus(backendUrl: String, email: String, txId: String): BackendResult
    suspend fun sendEvidenceToBackend(backendUrl: String, incidentId: String, mediaType: String, mediaHash: String): Boolean

    // ─── Grabación ────────────────────────────────────────────────────────
    fun startAudioRecording(incidentId: String)
    fun stopAudioRecording(): String?

    // ─── Bluetooth ────────────────────────────────────────────────────────
    fun ensureBluetoothEnabled()
    fun connectToEsp32()
    fun disconnectFromEsp32()
}
