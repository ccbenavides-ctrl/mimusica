package app.mimusica

import app.mimusica.domain.links.ExternalLinks
import app.mimusica.domain.links.ExternalService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {

    @Test
    fun arma_la_busqueda_con_artista_y_titulo() {
        assertEquals("Carlos Vives La Bicicleta", ExternalLinks.query("La Bicicleta", "Carlos Vives, Shakira"))
        assertEquals("Canción", ExternalLinks.query("Canción", "Artista desconocido"))
        assertEquals("Canción", ExternalLinks.query(" Canción ", null))
    }

    @Test
    fun codifica_tildes_espacios_y_simbolos() {
        val services = ExternalLinks.services("Niño & Bebé #1")
        val spotify = services.first { it.id == "spotify" }
        assertEquals("https://open.spotify.com/search/Ni%C3%B1o%20%26%20Beb%C3%A9%20%231", spotify.url)
        assertTrue(services.none { it.url.contains(" ") || it.url.contains("+") })
    }

    @Test
    fun hay_opciones_para_escuchar_y_para_comprar() {
        val services = ExternalLinks.services("Juanes")
        assertTrue(services.count { it.kind == ExternalService.Kind.LISTEN } >= 3)
        assertTrue(services.count { it.kind == ExternalService.Kind.BUY } >= 2)
        assertTrue(services.all { it.url.startsWith("https://") })
    }
}
