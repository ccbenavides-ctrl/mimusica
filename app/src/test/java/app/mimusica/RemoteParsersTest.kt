package app.mimusica

import app.mimusica.data.remote.InternetArchiveParser
import app.mimusica.data.remote.JamendoParser
import app.mimusica.data.remote.RemoteApiException
import app.mimusica.domain.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemoteParsersTest {

    @Test
    fun parsesInternetArchiveSearch() {
        val page = InternetArchiveParser.parseSearch(Fixtures.read("ia_search.json"), page = 1, rows = 3)
        assertEquals(2951, page.numFound)
        assertTrue(page.hasMore)
        assertEquals(3, page.items.size)
        val first = page.items[0]
        assertEquals("freestep-fighters-sjsep-3302", first.identifier)
        assertEquals("FreeStep", first.creator)
        assertEquals("2003-01-01", first.date)
        assertEquals(listOf("sojus-records", "netlabels", "fav-shaker_arquivo_"), first.collections)
        // creator como arreglo
        assertEquals("EugeneKha, Otro Artista", page.items[1].creator)
        // documento sin título ni creador
        assertEquals("sin-titulo-123", page.items[2].title)
        assertEquals("https://archive.org/services/img/bof024", page.items[1].thumbnailUrl)
    }

    @Test
    fun parsesInternetArchiveMetadataChoosingBestFormat() {
        val details = InternetArchiveParser.parseMetadata("bof024", Fixtures.read("ia_metadata.json"))
        assertEquals("In Drone", details.title)
        assertEquals("http://creativecommons.org/licenses/by-nc-nd/3.0/", details.licenseUrl)
        assertEquals(3, details.tracks.size) // 2 temas (mp3 elegido) + 1 flac sin mp3; el privado se omite
        val t1 = details.tracks[0]
        assertEquals("ia:bof024/01_EugeneKha_-_In_Drone_-_Cold_Rain_Remastered_2012.mp3", t1.key)
        assertEquals("Cold Rain (Remastered 2012)", t1.title)
        assertEquals(1, t1.trackNumber)
        assertEquals(1_765_270L, t1.durationMs)
        assertEquals(TrackSource.INTERNET_ARCHIVE, t1.source)
        assertEquals("https://archive.org/download/bof024/01_EugeneKha_-_In_Drone_-_Cold_Rain_Remastered_2012.mp3", t1.uri)
        assertEquals("Moscow Day", details.tracks[1].title)
        val bonus = details.tracks[2]
        assertEquals("https://archive.org/download/bof024/disc%202/03%20Bonus%20Track.flac", bonus.uri)
        assertEquals("03 Bonus Track", bonus.title)
        assertEquals(185_000L, bonus.durationMs)
        assertEquals("EugeneKha", bonus.artist)
        assertEquals("In Drone", bonus.album)
    }

    @Test
    fun internetArchiveMissingItemThrows() {
        try {
            InternetArchiveParser.parseMetadata("no-existe", "{}")
            fail("Debía lanzar excepción")
        } catch (e: RemoteApiException) {
            assertTrue(e.message!!.contains("no-existe"))
        }
    }

    @Test
    fun internetArchiveQueryAlwaysHasLicenseFilter() {
        val q = InternetArchiveParser.buildQuery("salsa: (vieja) \"guardia\"", "etree")
        assertEquals(
            "(salsa vieja guardia) AND collection:etree AND mediatype:(audio OR etree) AND " + InternetArchiveParser.LICENSE_FILTER,
            q
        )
        assertTrue(InternetArchiveParser.buildQuery("", null).startsWith("mediatype:(audio OR etree) AND ("))
    }

    @Test
    fun lengthParsing() {
        assertEquals(0L, InternetArchiveParser.parseLengthMs(null))
        assertEquals(61_500L, InternetArchiveParser.parseLengthMs("61.5"))
        assertEquals(3_723_000L, InternetArchiveParser.parseLengthMs("1:02:03"))
        assertEquals(0L, InternetArchiveParser.parseLengthMs("abc:1"))
    }

    @Test
    fun parsesJamendoTracksOnlyDownloadable() {
        val page = JamendoParser.parseTracks(Fixtures.read("jamendo_tracks.json"), offset = 0, limit = 3)
        assertEquals(2, page.tracks.size)
        assertTrue(page.hasMore)
        val t = page.tracks[0]
        assertEquals("jam:1848357", t.key)
        assertEquals("Cumbia del Sol", t.title)
        assertEquals("Los Libres", t.artist)
        assertEquals("Fiesta Abierta", t.album)
        assertEquals(215_000L, t.durationMs)
        assertEquals("https://prod-1.storage.jamendo.com/download/track/1848357/mp32/", t.downloadUrl)
        assertEquals("http://creativecommons.org/licenses/by-sa/3.0/", t.license)
        assertEquals(TrackSource.JAMENDO, t.source)
        assertTrue(t.canDownload)
        val single = page.tracks[1]
        assertEquals("jam:2000002", single.key)
        assertEquals("Sencillo", single.album)
        assertEquals(200_000L, single.durationMs)
        assertFalse(page.tracks.any { it.key == "jam:2000001" })
    }

    @Test
    fun jamendoErrorIsReported() {
        try {
            JamendoParser.parseTracks(Fixtures.read("jamendo_error.json"), 0, 20)
            fail("Debía lanzar excepción")
        } catch (e: RemoteApiException) {
            assertTrue(e.message!!.contains("client_id"))
        }
    }
}
