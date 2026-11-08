package mx.edu.utez.gibbor.data.contacts

import android.content.Context
import mx.edu.utez.gibbor.domain.repository.EmergencyContactRepository

/**
 * Persistencia local (prototipo) del texto de contactos de emergencia.
 * Los números nunca salen del teléfono.
 */
class EmergencyContactStore(context: Context) : EmergencyContactRepository {

    private val prefs = context.getSharedPreferences("gibbor_contacts", Context.MODE_PRIVATE)

    override fun load(): String = prefs.getString(KEY_CONTACTS, "").orEmpty()

    override fun save(raw: String) {
        prefs.edit().putString(KEY_CONTACTS, raw).apply()
    }

    private companion object {
        const val KEY_CONTACTS = "contacts"
    }
}
