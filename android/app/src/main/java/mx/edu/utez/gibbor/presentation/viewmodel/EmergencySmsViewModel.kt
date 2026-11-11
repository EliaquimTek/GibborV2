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
import mx.edu.utez.gibbor.domain.model.EmergencyContacts
import mx.edu.utez.gibbor.domain.repository.EmergencyContactRepository

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

    private fun checkSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
