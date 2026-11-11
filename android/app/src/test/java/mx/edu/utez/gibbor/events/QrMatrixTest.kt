package mx.edu.utez.gibbor.events

import mx.edu.utez.gibbor.events.data.EventCatalog
import mx.edu.utez.gibbor.events.ui.qr.displayUrl
import mx.edu.utez.gibbor.events.ui.qr.encodeQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatrixTest {

    @Test
    fun everyCatalogUrlEncodesAsValidQr() {
        val urls = (EventCatalog.fixedResources + EventCatalog.events.flatMap { it.resources }).map { it.url }
        urls.forEach { url ->
            val matrix = encodeQr(url)
            assertEquals("QR no cuadrado para $url", matrix.width, matrix.height)
            // Sin margen, un QR versión v mide 17 + 4v módulos.
            assertTrue("Tamaño inesperado para $url", matrix.width >= 21 && (matrix.width - 17) % 4 == 0)
            // Esquina superior izquierda = patrón localizador (módulo oscuro).
            assertTrue(matrix[0, 0])
        }
    }

    @Test
    fun displayUrlRemovesSchemeAndTrailingSlash() {
        assertEquals("gibbor.vercel.app", displayUrl("https://gibbor.vercel.app/"))
        assertEquals("gibborim.devteam@gmail.com", displayUrl("mailto:gibborim.devteam@gmail.com"))
    }
}
