package mx.edu.utez.gibbor.events

import mx.edu.utez.gibbor.events.data.EventCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Evita publicar un catálogo con ids repetidos o enlaces que un QR no puede abrir. */
class EventCatalogTest {

    private val allResources =
        EventCatalog.fixedResources + EventCatalog.events.flatMap { it.resources }

    @Test
    fun resourceIdsAreUnique() {
        val ids = allResources.map { it.id }
        assertEquals("Ids repetidos: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}", ids.size, ids.toSet().size)
    }

    @Test
    fun eventIdsAreUnique() {
        val ids = EventCatalog.events.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun urlsUseSupportedSchemes() {
        allResources.forEach { resource ->
            assertTrue(
                "URL no válida en '${resource.id}': ${resource.url}",
                resource.url.startsWith("https://") || resource.url.startsWith("mailto:"),
            )
        }
    }

    @Test
    fun titlesAreNotBlank() {
        allResources.forEach { assertTrue("Título vacío en '${it.id}'", it.title.isNotBlank()) }
        EventCatalog.events.forEach { assertTrue("Nombre vacío en '${it.id}'", it.name.isNotBlank()) }
    }

    @Test
    fun findEventReturnsNullForUnknownId() {
        assertNull(EventCatalog.findEvent("no-existe"))
        assertNull(EventCatalog.findEvent(null))
    }
}
