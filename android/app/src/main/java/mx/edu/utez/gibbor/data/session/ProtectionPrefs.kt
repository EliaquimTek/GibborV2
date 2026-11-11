package mx.edu.utez.gibbor.data.session

import android.content.Context
import mx.edu.utez.gibbor.BuildConfig

/**
 * Lo que el servicio de protección necesita aunque la app esté cerrada:
 * la sesión, la URL del backend y si el usuario dejó la protección activa.
 */
class ProtectionPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gibbor_protection", Context.MODE_PRIVATE)

    var email: String
        get() = prefs.getString(KEY_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, null) ?: BuildConfig.BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value).apply()

    /** El usuario conectó el botón y no lo ha desactivado. */
    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    /** Ya se pidió una vez quitar la optimización de batería. */
    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT, value).apply()

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_PROTECTION_ENABLED = "protection_enabled"
        const val KEY_BATTERY_PROMPT = "battery_prompt_shown"
    }
}
