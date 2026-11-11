package mx.edu.utez.gibbor.events.data

import android.content.Context

/** Recuerda qué evento del catálogo eligió el equipo, para no seleccionarlo en cada apertura. */
class SelectedEventStore(context: Context) {

    private val prefs = context.getSharedPreferences("gibbor_events", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY_SELECTED, null)

    fun save(eventId: String) {
        prefs.edit().putString(KEY_SELECTED, eventId).apply()
    }

    private companion object {
        const val KEY_SELECTED = "selected_event_id"
    }
}
