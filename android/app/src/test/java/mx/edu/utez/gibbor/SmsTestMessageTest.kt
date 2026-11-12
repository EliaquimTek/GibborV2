package mx.edu.utez.gibbor

import android.app.Activity
import android.telephony.SmsManager
import mx.edu.utez.gibbor.data.sms.SmsSender
import mx.edu.utez.gibbor.domain.usecase.BuildEmergencySmsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTestMessageTest {

    @Test
    fun `el SMS de prueba deja claro que no es emergencia`() {
        val msg = BuildEmergencySmsUseCase.testMessage("daviux403")
        assertTrue(msg.startsWith("PRUEBA GIBBOR"))
        assertTrue(msg.contains("no es una emergencia"))
        assertFalse(msg.contains("ALERTA"))
    }

    @Test
    fun `el SMS de prueba cabe en una sola parte GSM-7`() {
        val msg = BuildEmergencySmsUseCase.testMessage("un.nombre.de.usuario.largo")
        assertTrue("sin acentos ni emojis", msg.all { it.code < 128 })
        assertTrue("${msg.length} caracteres", msg.length <= 160)
    }

    @Test
    fun `el número se muestra enmascarado`() {
        assertEquals("***6884", SmsSender.mask("+527770076884"))
    }

    @Test
    fun `los códigos de la red se traducen`() {
        assertTrue(SmsSender.sentResultText(Activity.RESULT_OK).startsWith("enviado"))
        assertTrue(SmsSender.sentResultText(SmsManager.RESULT_ERROR_NO_SERVICE).contains("sin servicio"))
        assertTrue(SmsSender.sentResultText(SmsManager.RESULT_ERROR_RADIO_OFF).contains("modo avión"))
        assertTrue(SmsSender.sentResultText(999).contains("999"))
    }
}
