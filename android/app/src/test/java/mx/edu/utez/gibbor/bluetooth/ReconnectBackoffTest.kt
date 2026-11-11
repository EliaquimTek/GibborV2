package mx.edu.utez.gibbor.bluetooth

import mx.edu.utez.gibbor.data.bluetooth.ReconnectBackoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun `la espera crece y se queda en el máximo`() {
        val backoff = ReconnectBackoff()
        val delays = List(7) { backoff.nextDelayMs() }
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `reset vuelve a reintentar rápido`() {
        val backoff = ReconnectBackoff()
        repeat(5) { backoff.nextDelayMs() }
        backoff.reset()
        assertEquals(0, backoff.attempt)
        assertEquals(2_000L, backoff.nextDelayMs())
    }

    @Test
    fun `respeta el límite de Android de 5 escaneos cada 30 s`() {
        // Ventana de escaneo (12 s) + espera: nunca más de 5 inicios en 30 s
        val scanWindowMs = 12_000L
        val backoff = ReconnectBackoff()
        var t = 0L
        val starts = mutableListOf(0L)
        repeat(20) {
            t += scanWindowMs + backoff.nextDelayMs()
            starts += t
        }
        starts.forEach { start -> assertTrue(starts.count { it in start until start + 30_000L } <= 5) }
    }

    @Test
    fun `un pánico pendiente en el firmware (5 min) no caduca antes del siguiente intento`() {
        val firmwarePendingTtlMs = 5 * 60_000L
        val worstGapMs = 12_000L + ReconnectBackoff().apply { repeat(10) { nextDelayMs() } }.nextDelayMs()
        assertTrue(worstGapMs < firmwarePendingTtlMs)
    }
}
