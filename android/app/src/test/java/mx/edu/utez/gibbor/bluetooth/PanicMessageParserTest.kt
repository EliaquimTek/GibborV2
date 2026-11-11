package mx.edu.utez.gibbor.bluetooth

import mx.edu.utez.gibbor.data.bluetooth.PanicMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los mensajes reales que envía firmware/gibbor_esp32c3 (v1.0.0). */
class PanicMessageParserTest {

    @Test
    fun `pánico del firmware se detecta`() {
        assertTrue(PanicMessageParser.isTrigger("GIBBOR_PANIC_TRIGGER #1"))
    }

    @Test
    fun `pánico diferido se detecta y se marca`() {
        val line = "GIBBOR_PANIC_TRIGGER #3 (diferido)"
        assertTrue(PanicMessageParser.isTrigger(line))
        assertTrue(PanicMessageParser.isDeferred(line))
        assertFalse(PanicMessageParser.isDeferred("GIBBOR_PANIC_TRIGGER #3"))
    }

    @Test
    fun `firmware anterior con Boton sigue siendo compatible`() {
        assertTrue(PanicMessageParser.isTrigger("Boton presionado #7"))
    }

    @Test
    fun `respuestas de diagnóstico no disparan pánico`() {
        listOf(
            "PONG",
            "INFO name=GIBBOR_ESP32 fw=1.0.0 mac=14:63:93:91:8e:86 btn=GPIO9 uptime_s=14",
            "STATUS connected=1 subscribed=1 triggers=0 pending=0 mtu=185",
            "ERR unknown_cmd=X",
        ).forEach { assertFalse(it, PanicMessageParser.isTrigger(it)) }
    }

    @Test
    fun `MTU 23 - el primer trozo de 20 bytes ya es un pánico`() {
        val chunks = "GIBBOR_PANIC_TRIGGER #3\n".toByteArray().toList().chunked(20).map { it.toByteArray() }
        val lines = chunks.flatMap { PanicMessageParser.lines(it) }
        assertEquals(listOf("GIBBOR_PANIC_TRIGGER", "#3"), lines)
        assertEquals(1, lines.count(PanicMessageParser::isTrigger))
    }

    @Test
    fun `varias líneas en una notificación`() {
        val lines = PanicMessageParser.lines("PONG\r\nGIBBOR_PANIC_TRIGGER #2\n\n".toByteArray())
        assertEquals(listOf("PONG", "GIBBOR_PANIC_TRIGGER #2"), lines)
    }
}
