package mx.edu.utez.gibbor.data.bluetooth

/**
 * Espera entre intentos de reconexión con el botón: crece de forma escalonada y
 * se queda en [maxDelayMs]. Android limita a 5 escaneos BLE cada 30 s por app,
 * así que nunca se reintenta en menos de [steps].first().
 *
 * El máximo es de 30 s para que, sumado a la ventana de escaneo, el teléfono vuelva
 * a encontrar el botón antes de que caduque un pánico pendiente en el firmware.
 */
class ReconnectBackoff(
    private val steps: List<Long> = listOf(2_000L, 5_000L, 10_000L, 20_000L),
    private val maxDelayMs: Long = 30_000L,
) {
    var attempt: Int = 0
        private set

    fun nextDelayMs(): Long {
        val delay = steps.getOrElse(attempt) { maxDelayMs }.coerceAtMost(maxDelayMs)
        attempt++
        return delay
    }

    fun reset() {
        attempt = 0
    }
}
