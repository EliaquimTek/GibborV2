package mx.edu.utez.gibbor.presentation.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import mx.edu.utez.gibbor.data.contacts.EmergencyContactStore
import mx.edu.utez.gibbor.data.sms.SmsSender
import mx.edu.utez.gibbor.domain.model.EmergencyContacts
import mx.edu.utez.gibbor.domain.repository.EmergencyContactRepository
import mx.edu.utez.gibbor.domain.usecase.BuildEmergencySmsUseCase

/**
 * Estado de los contactos de emergencia y del permiso SEND_SMS para Ajustes.
 * El SMS de alerta lo envía ProtectionService (funciona con la app cerrada).
 * La solicitud del permiso SEND_SMS sigue en la Activity (necesita un launcher).
 */
class EmergencySmsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository: EmergencyContactRepository = EmergencyContactStore(application)

    /** Texto capturado por el usuario (números separados por coma). */
    var contacts by mutableStateOf(contactRepository.load())
        private set

    val validContactCount: Int
        get() = EmergencyContacts.parse(contacts).size

    fun updateContacts(raw: String) {
        contacts = raw
        contactRepository.save(raw)
    }

    var smsPermissionGranted by mutableStateOf(checkSmsPermission())
        private set

    /** Llamar tras una respuesta de permisos para que la UI refleje el estado actual. */
    fun refreshSmsPermission() {
        smsPermissionGranted = checkSmsPermission()
    }

    /** Resultado del último SMS de prueba, una línea por evento (vacío = no se ha probado). */
    var testSmsStatus by mutableStateOf("")
        private set

    /**
     * Manda un SMS de PRUEBA (no de emergencia) a cada contacto válido y va mostrando
     * lo que responde la red: enviado / entregado / error.
     */
    fun sendTestSms(senderEmail: String) {
        refreshSmsPermission()
        val numbers = EmergencyContacts.parse(contacts)
        when {
            !smsPermissionGranted -> {
                testSmsStatus = "Falta el permiso de SMS."
                return
            }
            numbers.isEmpty() -> {
                testSmsStatus = "Agrega al menos un número válido."
                return
            }
        }
        val name = senderEmail.substringBefore('@').ifBlank { "Un usuario" }
        val message = BuildEmergencySmsUseCase.testMessage(name)
        val lines = mutableListOf("Enviando prueba a ${numbers.size} contacto(s)…")
        testSmsStatus = lines.joinToString("\n")
        val sender = SmsSender(getApplication()) { status ->
            lines += status
            testSmsStatus = lines.joinToString("\n")
        }
        numbers.forEach { number ->
            try {
                sender.send(number, message)
            } catch (e: Exception) {
                lines += "SMS ${SmsSender.mask(number)}: NO enviado: ${e.message}"
            }
        }
        testSmsStatus = lines.joinToString("\n")
    }

    private fun checkSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
