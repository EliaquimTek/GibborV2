package mx.edu.utez.gibbor.domain.usecase

import mx.edu.utez.gibbor.core.util.HashUtils
import mx.edu.utez.gibbor.domain.model.IncidentDraft

/**
 * Genera el borrador local del incidente: id, timestamp, coordenadas E7 y hash inicial.
 */
class BuildIncidentDraftUseCase {

    operator fun invoke(latitude: Double, longitude: Double): IncidentDraft {
        val incidentId = "inc-${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis() / 1000L
        val latE7 = toE7(latitude)
        val lonE7 = toE7(longitude)
        val initialHash = HashUtils.sha256("$incidentId|$timestamp|$latE7|$lonE7")

        return IncidentDraft(
            incidentId = incidentId,
            timestamp = timestamp,
            latE7 = latE7,
            lonE7 = lonE7,
            initialHash = initialHash
        )
    }

    private fun toE7(value: Double): Int {
        return (value * 10_000_000.0).toInt()
    }
}
