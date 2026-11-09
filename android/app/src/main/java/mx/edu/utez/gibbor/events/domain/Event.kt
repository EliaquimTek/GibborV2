package mx.edu.utez.gibbor.events.domain

/**
 * Un evento donde se presenta GIBBOR (concurso, hackathon, ceremonia, demo…).
 * Sus [resources] se muestran además de los recursos fijos del equipo.
 *
 * @param date texto libre para mostrar, p. ej. "Noviembre 2026".
 */
data class Event(
    val id: String,
    val name: String,
    val date: String = "",
    val place: String = "",
    val description: String = "",
    val resources: List<EventResource> = emptyList(),
)
