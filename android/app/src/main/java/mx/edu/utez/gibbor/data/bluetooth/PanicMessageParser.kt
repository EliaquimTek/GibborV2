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

    /** 4 clicks rápidos en el botón: `GIBBOR_STOP_RECORDING #<n>`. */
    private const val STOP_KEYWORD = "GIBBOR_STOP_RECORDING"

    fun isStop(line: String): Boolean = line.contains(STOP_KEYWORD, ignoreCase = true)

    /** Divide una notificación en líneas limpias, como llegan por UART. */
    fun lines(payload: ByteArray): List<String> =
        String(payload, Charsets.UTF_8)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun isTrigger(line: String): Boolean =
        !isStop(line) && TRIGGER_KEYWORDS.any { line.contains(it, ignoreCase = true) }

    /** `true` si el pánico se presionó mientras el teléfono estaba desconectado. */
    fun isDeferred(line: String): Boolean = isTrigger(line) && line.contains("diferido", ignoreCase = true)
}
