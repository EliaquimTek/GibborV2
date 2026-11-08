package mx.edu.utez.gibbor.presentation.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.edu.utez.gibbor.data.contacts.EmergencyContactStore
import mx.edu.utez.gibbor.data.sms.SmsSender
import mx.edu.utez.gibbor.domain.model.EmergencyContacts
import mx.edu.utez.gibbor.domain.model.EmergencySmsResult
import mx.edu.utez.gibbor.domain.repository.EmergencyContactRepository
import mx.edu.utez.gibbor.domain.usecase.SendEmergencySmsUseCase

/**
 * Estado y acciones de los contactos de emergencia y del SMS de alerta.
 * La solicitud del permiso SEND_SMS sigue en la Activity (necesita un launcher).
 */
class EmergencySmsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository: EmergencyContactRepository = EmergencyContactStore(application)
    private val sendEmergencySms = SendEmergencySmsUseCase(SmsSender(application))

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

    private fun checkSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Envía el SMS con la ubicación a los contactos. Nunca lanza: el resultado
     * indica si faltaban contactos, permiso o qué números fallaron.
     */
    suspend fun sendAlert(senderEmail: String, latitude: Double, longitude: Double): EmergencySmsResult {
        if (EmergencyContacts.parse(contacts).isEmpty()) return EmergencySmsResult.NoContacts
        refreshSmsPermission()
        if (!smsPermissionGranted) return EmergencySmsResult.MissingPermission

        val sender = senderEmail.substringBefore('@').ifBlank { "Un usuario" }
        return withContext(Dispatchers.IO) {
            sendEmergencySms(contacts, sender, latitude, longitude)
        }
    }
}
