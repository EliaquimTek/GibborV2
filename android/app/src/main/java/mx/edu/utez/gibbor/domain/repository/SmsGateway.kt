package mx.edu.utez.gibbor.domain.repository

/**
 * Canal de envío de SMS (implementado en la capa data).
 */
interface SmsGateway {

    /** Entrega [message] a la radio para [number]. Lanza excepción si no se pudo enviar. */
    fun send(number: String, message: String)
}
