package mx.edu.utez.gibbor.events.domain

/** Tipo de recurso: define el ícono y la etiqueta con que se muestra en la pestaña Evento. */
enum class ResourceType(val label: String) {
    WEB("Página web"),
    REPOSITORIO("Repositorio"),
    CORREO("Correo"),
    RED_SOCIAL("Red social"),
    PAPER("Artículo"),
    DOCUMENTO("Documento"),
    ENCUESTA("Encuesta"),
    VIDEO("Video"),
    OTRO("Enlace"),
}

/**
 * Un recurso que el equipo comparte con el público, como QR y como enlace.
 *
 * @param id identificador único dentro del catálogo (minúsculas, sin espacios).
 * @param url destino del QR: `https://…` o `mailto:…`.
 */
data class EventResource(
    val id: String,
    val title: String,
    val url: String,
    val type: ResourceType,
    val description: String = "",
)
