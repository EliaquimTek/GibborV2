package mx.edu.utez.gibbor.events.data

import mx.edu.utez.gibbor.events.domain.Event
import mx.edu.utez.gibbor.events.domain.EventResource
import mx.edu.utez.gibbor.events.domain.ResourceType

/**
 * Contenido precargado de la pestaña Evento. Para cambiarlo se edita este archivo y se recompila.
 *
 * - [fixedResources]: aparecen en todos los eventos (web, repo, contacto, redes).
 * - [events]: un evento por entrada, con su propio material.
 *
 * Reglas: cada `id` debe ser único en todo el catálogo y cada `url` debe empezar con
 * `https://` o `mailto:` (lo verifica `EventCatalogTest`).
 */
object EventCatalog {

    val fixedResources: List<EventResource> = listOf(
        EventResource(
            id = "web",
            title = "Página web",
            url = "https://gibbor.vercel.app/",
            type = ResourceType.WEB,
            description = "Conoce GIBBOR",
        ),
        EventResource(
            id = "repo",
            title = "Repositorio",
            url = "https://github.com/EliaquimTek/GibborV2",
            type = ResourceType.REPOSITORIO,
            description = "Código fuente en GitHub",
        ),
        EventResource(
            id = "correo",
            title = "Correo del equipo",
            url = "mailto:gibborim.devteam@gmail.com",
            type = ResourceType.CORREO,
            description = "gibborim.devteam@gmail.com",
        ),
        // TODO: redes sociales del equipo (pendientes).
    )

    val events: List<Event> = emptyList()

    fun findEvent(id: String?): Event? = events.firstOrNull { it.id == id }
}
