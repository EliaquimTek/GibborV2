package mx.edu.utez.gibbor

import mx.edu.utez.gibbor.domain.model.EmergencyContacts
import mx.edu.utez.gibbor.domain.model.EmergencySmsResult
import mx.edu.utez.gibbor.domain.repository.SmsGateway
import mx.edu.utez.gibbor.domain.usecase.BuildEmergencySmsUseCase
import mx.edu.utez.gibbor.domain.usecase.SendEmergencySmsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySmsTest {

    @Test
    fun normalize_tenDigitsAssumesMexico() {
        assertEquals("+527771234567", EmergencyContacts.normalize("777 123-4567"))
        assertEquals("+525512345678", EmergencyContacts.normalize("(55) 1234 5678"))
    }

    @Test
    fun normalize_keepsInternationalNumbers() {
        assertEquals("+14155552671", EmergencyContacts.normalize("+1 415 555 2671"))
    }

    @Test
    fun normalize_rejectsInvalidInput() {
        assertNull(EmergencyContacts.normalize(""))
        assertNull(EmergencyContacts.normalize("12345"))
        assertNull(EmergencyContacts.normalize("777abc4567"))
    }

    @Test
    fun parse_dropsInvalidAndDuplicatesAndCapsAtMax() {
        val raw = "7771234567, 777 123 4567; nope\n5511111111,5522222222,5533333333,5544444444,5555555555"
        val numbers = EmergencyContacts.parse(raw)
        assertEquals(EmergencyContacts.MAX_CONTACTS, numbers.size)
        assertEquals("+527771234567", numbers.first())
    }

    @Test
    fun message_containsSenderAndMapsLinkAndIsGsm7Sized() {
        val message = BuildEmergencySmsUseCase()("ana", 18.8532110, -99.1234560)
        assertTrue(message.contains("ana"))
        assertTrue(message.contains("https://maps.google.com/?q=18.853211,-99.123456"))
        assertTrue(message.all { it.code < 128 })
        assertTrue(message.length <= 160)
    }

    @Test
    fun send_reportsFailuresWithoutStoppingOthers() {
        val sent = mutableListOf<String>()
        val gateway = object : SmsGateway {
            override fun send(number: String, message: String) {
                if (number == "+525500000000") error("no signal")
                sent += number
            }
        }
        val result = SendEmergencySmsUseCase(gateway)("5500000000, 7771234567", "ana", 0.0, 0.0)
        assertEquals(listOf("+527771234567"), sent)
        assertEquals(EmergencySmsResult.Sent(sent = 1, total = 2, errors = listOf("+525500000000: no signal")), result)
    }

    @Test
    fun send_withoutContactsReturnsNoContacts() {
        val gateway = object : SmsGateway {
            override fun send(number: String, message: String) = error("should not send")
        }
        assertEquals(EmergencySmsResult.NoContacts, SendEmergencySmsUseCase(gateway)("  ", "ana", 0.0, 0.0))
    }
}
