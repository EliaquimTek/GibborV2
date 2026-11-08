package mx.edu.utez.gibbor.domain.model

/**
 * Resultado de avisar por SMS a los contactos de emergencia.
 */
sealed interface EmergencySmsResult {

    /** No hay ningún número válido configurado. */
    data object NoContacts : EmergencySmsResult

    /** Falta el permiso SEND_SMS. */
    data object MissingPermission : EmergencySmsResult

    /** [errors] contiene un mensaje por cada número que falló. */
    data class Sent(val sent: Int, val total: Int, val errors: List<String>) : EmergencySmsResult
}
