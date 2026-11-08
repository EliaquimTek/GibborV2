package mx.edu.utez.gibbor.domain.repository

/**
 * Almacenamiento local de los contactos de emergencia (implementado en la capa data).
 */
interface EmergencyContactRepository {

    /** Texto tal como lo capturó el usuario (números separados por coma). */
    fun load(): String

    fun save(raw: String)
}
