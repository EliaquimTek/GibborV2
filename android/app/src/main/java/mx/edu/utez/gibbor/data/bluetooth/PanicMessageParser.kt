package mx.edu.utez.gibbor.data.bluetooth

/**
 * Interpreta lo que el ESP32 notifica por la característica TX.
 *
 * El firmware envía `GIBBOR_PANIC_TRIGGER #<n>` (o `… (diferido)` si el botón se
 * presionó sin conexión). Versiones anteriores enviaban `Boton presionado #<n>`.
 * Las respuestas de diagnóstico (PONG, INFO, STATUS) nunca contienen esas palabras.
 */
object PanicMessageParser {

    private val TRIGGER_KEYWORDS = listOf("GIBBOR_PANIC_TRIGGER", "Boton")

    /** Divide una notificación en líneas limpias, como llegan por UART. */
    fun lines(payload: ByteArray): List<String> =
        String(payload, Charsets.UTF_8)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun isTrigger(line: String): Boolean =
        TRIGGER_KEYWORDS.any { line.contains(it, ignoreCase = true) }

    /** `true` si el pánico se presionó mientras el teléfono estaba desconectado. */
    fun isDeferred(line: String): Boolean = isTrigger(line) && line.contains("diferido", ignoreCase = true)
}
