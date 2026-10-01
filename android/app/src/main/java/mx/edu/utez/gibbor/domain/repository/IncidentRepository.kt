package mx.edu.utez.gibbor.domain.repository

import mx.edu.utez.gibbor.domain.model.BackendResult
import mx.edu.utez.gibbor.domain.model.EvidenceResult
import mx.edu.utez.gibbor.domain.model.IncidentDraft

/**
 * Contrato de acceso al backend GIBBOR (implementado en la capa data).
 */
interface IncidentRepository {

    /** POST /api/incident — lanza excepción si falla la conexión. */
    suspend fun sendIncident(backendUrl: String, email: String, draft: IncidentDraft): BackendResult

    /** GET /api/incident/:txId — polling hasta "success"/"failed" o agotar intentos. */
    suspend fun pollStatus(
        backendUrl: String,
        email: String,
        txId: String,
        maxAttempts: Int = 10,
        delayMs: Long = 3000L
    ): BackendResult

    /** POST /api/incident/:incidentId/evidence — ancla el hash de un archivo. */
    suspend fun sendEvidence(
        backendUrl: String,
        incidentId: String,
        mediaType: String,
        mediaHash: String
    ): EvidenceResult
}
