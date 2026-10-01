package mx.edu.utez.gibbor.domain.model

/**
 * Resultado de anclar el hash de un archivo de evidencia.
 * [exceptionMessage] != null indica que la petición lanzó una excepción.
 */
data class EvidenceResult(
    val success: Boolean,
    val txHash: String = "",
    val error: String = "",
    val exceptionMessage: String? = null
)
