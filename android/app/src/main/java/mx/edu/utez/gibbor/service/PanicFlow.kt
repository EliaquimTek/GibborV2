package mx.edu.utez.gibbor.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import mx.edu.utez.gibbor.core.util.HashUtils
import mx.edu.utez.gibbor.data.contacts.EmergencyContactStore
import mx.edu.utez.gibbor.data.location.LocationDataSource
import mx.edu.utez.gibbor.data.recording.AudioEvidenceRecorder
import mx.edu.utez.gibbor.data.recording.VideoEvidenceRecorder
import mx.edu.utez.gibbor.data.remote.HttpIncidentRepository
import mx.edu.utez.gibbor.data.session.ProtectionPrefs
import mx.edu.utez.gibbor.data.sms.SmsSender
import mx.edu.utez.gibbor.domain.model.EmergencySmsResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft
import mx.edu.utez.gibbor.domain.repository.IncidentRepository
import mx.edu.utez.gibbor.domain.usecase.BuildIncidentDraftUseCase
import mx.edu.utez.gibbor.domain.usecase.SendEmergencySmsUseCase
import java.io.File

/**
 * Flujo completo de un pánico, independiente de la UI:
 *
 * 1. Ubicación GPS + hora → borrador del incidente (SHA-256).
 * 2. **Empieza a grabar audio y video de inmediato** (no espera a la blockchain).
 * 3. SMS con la ubicación a los contactos de emergencia.
 * 4. Registro del incidente en Stellar a través del backend (+ polling).
 * 5. Al detener la grabación (usuario, notificación o límite de tiempo) calcula el
 *    SHA-256 de cada archivo y lo ancla on-chain en cuanto el incidente se confirma.
 *
 * Corre en el hilo principal del [ProtectionService]; la red va en Dispatchers.IO
 * dentro del repositorio.
 */
class PanicFlow(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val prefs: ProtectionPrefs,
    private val onSessionChanged: () -> Unit,
    private val incidentRepository: IncidentRepository = HttpIncidentRepository(),
) {

    companion object {
        /** La grabación se detiene sola para no llenar el almacenamiento. */
        const val MAX_RECORDING_MS = 10 * 60_000L
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private const val ANCHOR_ATTEMPTS = 3
        private const val ANCHOR_RETRY_MS = 3_000L
        private const val MAX_ERROR_LOG = 300
    }

    /** Serializa los anclajes de evidencia (ver [anchor]). */
    private val anchorLock = Mutex()

    private val buildDraft = BuildIncidentDraftUseCase()
    private val location = LocationDataSource(context)
    private val contacts = EmergencyContactStore(context)
    private val sendSms = SendEmergencySmsUseCase(SmsSender(context))
    private val audio = AudioEvidenceRecorder(context)
    private val video = VideoEvidenceRecorder(context, lifecycleOwner)

    private var incidentId: String? = null
    private var incidentRegistered: CompletableDeferred<Boolean>? = null
    private var maxDurationJob: Job? = null
    private var pendingAnchors = 0
    private var videoPending = false

    /** No hay incidente en curso, grabación ni hashes por anclar. */
    val isIdle: Boolean
        get() {
            val s = ProtectionHub.state.value
            return !s.busy && !s.isRecording && pendingAnchors == 0 && !videoPending
        }

    private fun log(message: String) = ProtectionHub.log(message)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ─── Disparo ──────────────────────────────────────────────────────────

    fun trigger(source: String, useFrontCamera: Boolean) {
        val s = ProtectionHub.state.value
        if (s.busy || s.isRecording) {
            log("$source -> busy, trigger ignored (emergency already active)")
            return
        }
        val email = prefs.email
        if (email.isBlank()) {
            log("$source -> authenticate first")
            return
        }

        ProtectionHub.update { it.copy(busy = true) }
        onSessionChanged()
        scope.launch {
            try {
                run(source, useFrontCamera, email)
            } catch (e: Exception) {
                log("$source -> error: ${e.message}")
            } finally {
                ProtectionHub.update { it.copy(busy = false) }
                onSessionChanged()
            }
        }
    }

    private suspend fun run(source: String, useFrontCamera: Boolean, email: String) {
        val backendUrl = prefs.backendUrl
        val registered = CompletableDeferred<Boolean>()
        incidentRegistered = registered

        // --- Paso 1: ubicación + borrador ---
        val loc: Location? = try {
            withTimeout(LOCATION_TIMEOUT_MS) { location.getCurrentLocation() }
        } catch (e: Exception) {
            log("$source -> location error: ${e.message}")
            null
        }
        val draft: IncidentDraft? = loc?.let { buildDraft(it.latitude, it.longitude) }
        val id = draft?.incidentId ?: "inc-${System.currentTimeMillis()}"
        incidentId = id

        if (draft != null) {
            ProtectionHub.update {
                it.copy(
                    incidentPreview = "id=${draft.incidentId}\n" +
                        "timestamp=${draft.timestamp}\n" +
                        "lat_e7=${draft.latE7}\n" +
                        "lon_e7=${draft.lonE7}\n" +
                        "hash=${draft.initialHash}",
                    onChainStatus = "Sending...",
                )
            }
            log("$source -> incident_id=${draft.incidentId}")
            log("$source -> lat_e7=${draft.latE7}, lon_e7=${draft.lonE7}")
            log("$source -> hash=${draft.initialHash}")
        }

        // --- Paso 2: grabar ya ---
        startRecording(source, id, useFrontCamera)

        // --- Paso 3: SMS ---
        if (loc != null) {
            sendAlertSms(source, email, loc)
        } else {
            log("$source -> SMS: not sent (no location)")
        }

        // --- Paso 4: blockchain ---
        if (draft == null) {
            ProtectionHub.update { it.copy(onChainStatus = "❌ Error: no location") }
            registered.complete(false)
            return
        }
        registered.complete(registerIncident(source, backendUrl, email, draft))
    }

    private fun sendAlertSms(source: String, email: String, loc: Location) {
        val raw = contacts.load()
        val result = when {
            !granted(Manifest.permission.SEND_SMS) -> EmergencySmsResult.MissingPermission
            else -> sendSms(raw, email.substringBefore('@').ifBlank { "Un usuario" }, loc.latitude, loc.longitude)
        }
        when (result) {
            EmergencySmsResult.NoContacts -> log("$source -> SMS: no emergency contacts configured")
            EmergencySmsResult.MissingPermission -> log("$source -> SMS: missing SEND_SMS permission")
            is EmergencySmsResult.Sent -> {
                result.errors.forEach { log("$source -> SMS ERROR $it") }
                log("$source -> SMS: sent to ${result.sent}/${result.total} contacts")
            }
        }
    }

    /** Devuelve true si el incidente quedó confirmado on-chain. */
    private suspend fun registerIncident(
        source: String,
        backendUrl: String,
        email: String,
        draft: IncidentDraft,
    ): Boolean {
        log("$source -> Sending incident to $backendUrl...")
        return try {
            val result = incidentRepository.sendIncident(backendUrl, email, draft)
            log("$source -> Backend response: success=${result.success}")
            log("$source -> status=${result.status}")
            log("$source -> txId=${result.txId}")
            log("$source -> txHash=${result.txHash}")
            log("$source -> explorer=${result.explorerLink}")

            if (!result.success) {
                ProtectionHub.update { it.copy(onChainStatus = "Error: ${result.error}") }
                log("$source -> BACKEND ERROR: ${result.error}")
                log("$source -> Raw: ${result.rawJson}")
                return false
            }

            var final = result
            if (result.status in listOf("pending", "awaiting-approval")) {
                log("$source -> TX pending, polling backend...")
                ProtectionHub.update { it.copy(onChainStatus = "TX pending, waiting for confirmation...") }
                final = incidentRepository.pollStatus(backendUrl, email, result.txId)
                log("$source -> Polling final: status=${final.status}")
            }

            val emoji = when (final.status) {
                "success" -> "✅"
                "failed" -> "❌"
                else -> "⏳"
            }
            ProtectionHub.update {
                it.copy(
                    onChainStatus = "$emoji Status: ${final.status}\n" +
                        "TX: ${final.txHash}\n" +
                        "Explorer: ${final.explorerLink}",
                )
            }
            if (final.status == "success") log("$source -> TX confirmed. Evidence will be anchored when recording stops.")
            final.status == "success"
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            ProtectionHub.update { it.copy(onChainStatus = "❌ Error: $msg") }
            log("$source -> EXCEPTION: $msg")
            false
        }
    }

    // ─── Grabación ────────────────────────────────────────────────────────

    private fun startRecording(source: String, id: String, useFrontCamera: Boolean) {
        val hasAudio = granted(Manifest.permission.RECORD_AUDIO)
        val hasCamera = granted(Manifest.permission.CAMERA)
        if (!hasAudio && !hasCamera) {
            log("$source -> Missing recording permissions. Open GIBBOR and grant them.")
            return
        }

        var started = false
        if (hasAudio) {
            try {
                val file = audio.start(id)
                log("AUDIO: Recording -> ${file.name}")
                started = true
            } catch (e: Exception) {
                log("AUDIO ERROR: ${e.message}")
            }
        } else {
            log("AUDIO: Missing RECORD_AUDIO permission")
        }

        if (hasCamera) {
            videoPending = true
            log(if (useFrontCamera) "VIDEO: Starting video recording (front camera)..." else "VIDEO: Starting video recording...")
            video.start(id, useFrontCamera, onLog = ::log) { file, hash -> onVideoFinalized(id, file, hash) }
            started = true
        } else {
            log("VIDEO: No CAMERA permission — audio only")
        }

        if (!started) return
        ProtectionHub.update { it.copy(isRecording = true, recordingStartedAt = System.currentTimeMillis()) }
        maxDurationJob?.cancel()
        maxDurationJob = scope.launch {
            delay(MAX_RECORDING_MS)
            log("RECORDING: ${MAX_RECORDING_MS / 60_000} min limit reached, stopping.")
            stopRecording()
        }
        onSessionChanged()
    }

    /** Detiene la grabación activa (botón de la UI, notificación o límite de tiempo). */
    fun stopRecording() {
        if (!ProtectionHub.state.value.isRecording) return
        maxDurationJob?.cancel()
        maxDurationJob = null
        val id = incidentId ?: return

        val audioFile = if (audio.isActive) audio.stop(onError = ::log) else null
        video.stop() // el hash del video llega en onVideoFinalized
        ProtectionHub.update { it.copy(isRecording = false, recordingStartedAt = 0L) }

        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            val hash = HashUtils.sha256File(audioFile)
            log("AUDIO: Recording stopped. File: ${audioFile.name}")
            log("AUDIO: Size: ${audioFile.length()} bytes")
            log("AUDIO: SHA-256: $hash")
            anchor(id, "audio", hash)
        } else if (audioFile != null) {
            log("AUDIO: File empty or not found.")
        }
        onSessionChanged()
    }

    private fun onVideoFinalized(id: String, file: File, hash: String?) {
        videoPending = false
        log("VIDEO: Recording finished [$id]")
        log("VIDEO: File: ${file.name}")
        if (hash != null) {
            log("VIDEO: SHA-256: $hash")
            anchor(id, "video", hash)
        } else {
            log("VIDEO: No usable file to hash.")
        }
        onSessionChanged()
    }

    private fun anchor(id: String, mediaType: String, hash: String) {
        val registered = incidentRegistered
        val email = prefs.email
        val backendUrl = prefs.backendUrl
        pendingAnchors++
        scope.launch {
            try {
                if (registered?.await() != true) {
                    log("${mediaType.uppercase()}: Incident not on-chain; evidence kept on the phone only.")
                    return@launch
                }
                // Un anclaje a la vez: audio y video escriben en la misma entrada del
                // contrato. Si van en paralelo, la segunda TX se simula antes de que la
                // primera aterrice y Soroban la revierte (scecExceededLimit).
                val ok = anchorLock.withLock {
                    var success = false
                    for (attempt in 1..ANCHOR_ATTEMPTS) {
                        log("${mediaType.uppercase()}: Anchoring hash on blockchain (attempt $attempt/$ANCHOR_ATTEMPTS)...")
                        val result = incidentRepository.sendEvidence(backendUrl, email, id, mediaType, hash)
                        when {
                            result.exceptionMessage != null -> log("EVIDENCE [$mediaType] EXCEPTION: ${result.exceptionMessage}")
                            result.success -> log("EVIDENCE [$mediaType]: TX=${result.txHash}")
                            else -> log("EVIDENCE [$mediaType] ERROR: ${result.error.take(MAX_ERROR_LOG)}")
                        }
                        if (result.success) {
                            success = true
                            break
                        }
                        if (attempt < ANCHOR_ATTEMPTS) delay(ANCHOR_RETRY_MS * attempt)
                    }
                    success
                }
                log(if (ok) "${mediaType.uppercase()}: Hash anchored on blockchain" else "${mediaType.uppercase()}: Error anchoring hash on blockchain")
            } finally {
                pendingAnchors--
                onSessionChanged()
            }
        }
    }

    fun release() {
        if (ProtectionHub.state.value.isRecording) stopRecording()
        video.release()
    }
}
