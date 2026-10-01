package mx.edu.utez.gibbor.domain.model

data class IncidentDraft(
    val incidentId: String,
    val timestamp: Long,
    val latE7: Int,
    val lonE7: Int,
    val initialHash: String
)
