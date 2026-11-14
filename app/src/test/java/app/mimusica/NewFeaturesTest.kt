package app.mimusica

import app.mimusica.data.backup.BackupCodec
import app.mimusica.data.backup.BackupData
import app.mimusica.data.db.PodcastEntity
import app.mimusica.data.db.TrackOverrideEntity
import app.mimusica.data.remote.LyricsParser
import app.mimusica.data.remote.MusicBrainzParser
import app.mimusica.data.remote.PodcastSearchParser
import app.mimusica.data.remote.RadioParser
import app.mimusica.domain.lyrics.LrcParser
import app.mimusica.domain.mix.MixBuilder
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackJson
import app.mimusica.domain.model.TrackSource
import app.mimusica.domain.podcast.RssParser
import app.mimusica.domain.stats.PlayEvent
import app.mimusica.domain.stats.StatsCalculator
import app.mimusica.playback.QueueStore
import app.mimusica.playback.ReplayGain
import app.mimusica.playback.SavedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NewFeaturesTest {

    private fun track(
        id: Int,
        title: String = "Canción $id",
        artist: String = "Artista",
        album: String = "Álbum",
        genre: String = "",
        durationMs: Long = 180_000,
        source: TrackSource = TrackSource.LOCAL,
    ) = Track(
        key = if (source == TrackSource.LOCAL) Track.localKey(id.toLong()) else "x:$id",
        title = title, artist = artist, album = album, durationMs = durationMs,
        uri = "content://media/external/audio/media/$id", artworkUri = null, source = source, genre = genre,
    )

    // ---- Letras ----

    @Test
    fun lrc_parsea_tiempos_y_ordena() {
        val lines = LrcParser.parse(
            """
            [ar:Alguien]
            [00:12.50]Segunda
            [00:01.5]Primera
            [01:02.345][02:00]Repetida
            sin marca
            """.trimIndent()
        )
        assertEquals(listOf(1_500L, 12_500L, 62_345L, 120_000L), lines.map { it.timeMs })
        assertEquals("Primera", lines.first().text)
        assertEquals("Repetida", lines.last().text)
    }

    @Test
    fun lrc_linea_activa() {
        val lines = LrcParser.parse("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")
        assertEquals(-1, LrcParser.activeIndex(lines, 5_000))
        assertEquals(0, LrcParser.activeIndex(lines, 10_000))
        assertEquals(1, LrcParser.activeIndex(lines, 29_999))
        assertEquals(2, LrcParser.activeIndex(lines, 99_000))
    }

    @Test
    fun letras_elige_sincronizada_con_duracion_parecida() {
        val body = """[
          {"id":1,"duration":200,"instrumental":false,"plainLyrics":"texto","syncedLyrics":null},
          {"id":2,"duration":181,"instrumental":false,"plainLyrics":"texto","syncedLyrics":"[00:01.00]hola"},
          {"id":3,"duration":300,"instrumental":false,"plainLyrics":"texto","syncedLyrics":"[00:01.00]otra"},
          {"id":4,"duration":180,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}
        ]"""
        val best = LyricsParser.pickBest(LyricsParser.parseMany(body), 180)
        assertEquals("[00:01.00]hola", best?.synced)
        assertNull(LyricsParser.pickBest(emptyList(), 180))
    }

    // ---- Mezclas ----

    @Test
    fun radio_de_cancion_empieza_por_la_semilla_y_prefiere_el_mismo_artista() {
        val seed = track(1, artist = "Juanes", genre = "Pop")
        val sameArtist = (2..6).map { track(it, artist = "Juanes") }
        val others = (7..60).map { track(it, artist = "Otro $it") }
        val queue = MixBuilder.songRadio(seed, listOf(seed) + sameArtist + others, size = 20, random = Random(7))
        assertEquals(seed.key, queue.first().key)
        assertEquals(20, queue.size)
        assertEquals(queue.size, queue.map { it.key }.toSet().size)
        assertTrue(queue.map { it.key }.containsAll(sameArtist.map { it.key }))
    }

    @Test
    fun mezclas_de_inicio() {
        val library = (1..30).map { track(it, artist = if (it <= 12) "Carlos Vives" else "Otro $it", genre = if (it <= 15) "Vallenato" else "") }
        val favorites = library.take(6).map { it.key }.toSet()
        val mixes = MixBuilder.homeMixes(library, favorites, playCounts = mapOf(library[0].key to 3), random = Random(1))
        val ids = mixes.map { it.id }
        assertTrue("fav" in ids)
        assertTrue("artista:Carlos Vives" in ids)
        assertTrue("genero:Vallenato" in ids)
        assertTrue("todo" in ids)
        assertTrue(mixes.all { it.tracks.isNotEmpty() })
        assertTrue(MixBuilder.homeMixes(emptyList(), emptySet(), emptyMap()).isEmpty())
    }

    // ---- Estadísticas ----

    @Test
    fun estadisticas_cuentan_reproducciones_minutos_y_dias() {
        val a = track(1, artist = "A", durationMs = 120_000)
        val b = track(2, artist = "B", durationMs = 60_000)
        val day = 86_400_000L
        val events = listOf(
            PlayEvent(a, 10 * day + 3_600_000L),
            PlayEvent(a, 10 * day + 7_200_000L),
            PlayEvent(b, 11 * day + 3_600_000L),
        )
        val stats = StatsCalculator.compute(events)
        assertEquals(3, stats.totalPlays)
        assertEquals(5L, stats.totalMinutes)
        assertEquals(2, stats.activeDays)
        assertEquals(a.key, stats.topTracks.first().track.key)
        assertEquals(2, stats.topTracks.first().plays)
        assertEquals("A", stats.topArtists.first().artist)
        assertEquals(2, stats.byHour[1])
        assertEquals(1, stats.peakHour)
        assertTrue(StatsCalculator.compute(emptyList()).isEmpty)
    }

    // ---- Podcasts ----

    @Test
    fun rss_lee_episodios() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Mi Podcast</title>
                <itunes:author>La Autora</itunes:author>
                <description><![CDATA[<p>Un <b>podcast</b> de prueba</p>]]></description>
                <itunes:image href="https://example.org/portada.jpg"/>
                <item>
                  <title>Episodio 2</title>
                  <enclosure url="https://example.org/ep2.mp3" type="audio/mpeg" length="123"/>
                  <guid>ep-2</guid>
                  <pubDate>Tue, 03 Nov 2026 10:00:00 +0000</pubDate>
                  <itunes:duration>1:02:03</itunes:duration>
                </item>
                <item>
                  <title>Solo video</title>
                  <enclosure url="https://example.org/v.mp4" type="video/mp4"/>
                </item>
                <item>
                  <title>Episodio 1</title>
                  <enclosure url="https://example.org/ep1.mp3" type="audio/mpeg"/>
                  <itunes:duration>754</itunes:duration>
                </item>
              </channel>
            </rss>"""
        val feed = RssParser.parse(xml.byteInputStream())
        assertEquals("Mi Podcast", feed.title)
        assertEquals("La Autora", feed.author)
        assertEquals("Un podcast de prueba", feed.description)
        assertEquals("https://example.org/portada.jpg", feed.imageUrl)
        assertEquals(listOf("Episodio 2", "Episodio 1"), feed.episodes.map { it.title })
        assertEquals(3_723_000L, feed.episodes[0].durationMs)
        assertEquals(754_000L, feed.episodes[1].durationMs)
        assertTrue(feed.episodes[0].publishedAt > 0)
        assertEquals(20, feed.episodes[0].id.length)
        assertFalse(feed.episodes[0].id == feed.episodes[1].id)
    }

    @Test
    fun busqueda_de_podcasts() {
        val body = """{"resultCount":2,"results":[
            {"collectionName":"Radio Ambulante","artistName":"NPR","feedUrl":"https://feeds.example/ra","artworkUrl600":"https://img/600.jpg"},
            {"collectionName":"Sin feed","artistName":"X"}]}"""
        val list = PodcastSearchParser.parse(body)
        assertEquals(1, list.size)
        assertEquals("Radio Ambulante", list[0].title)
        assertEquals("https://feeds.example/ra", list[0].feedUrl)
    }

    // ---- Radio ----

    @Test
    fun radio_parsea_emisoras_y_descarta_invalidas() {
        val body = """[
          {"stationuuid":"u1","name":" Tropicana ","url":"http://a/pls","url_resolved":"https://stream.a/mp3","favicon":"https://a/i.png","tags":"salsa,tropical,otra","country":"Colombia","homepage":"https://a"},
          {"stationuuid":"u2","name":"Duplicada","url_resolved":"https://stream.a/mp3"},
          {"stationuuid":"u3","name":"Sin url","url":"","url_resolved":""},
          {"stationuuid":"u4","name":"","url_resolved":"https://b/mp3"}
        ]"""
        val stations = RadioParser.parseStations(body)
        assertEquals(1, stations.size)
        val s = stations[0]
        assertEquals("radio:u1", s.key)
        assertEquals("Tropicana", s.title)
        assertEquals("https://stream.a/mp3", s.uri)
        assertEquals("Colombia · salsa · tropical", s.artist)
        assertEquals(TrackSource.RADIO, s.source)
        assertTrue(s.isLive)
        assertFalse(s.canDownload)
    }

    // ---- Carátulas ----

    @Test
    fun musicbrainz_elige_el_album_correcto() {
        val body = """{"release-groups":[
          {"id":"otro","title":"Otro disco","artist-credit":[{"name":"Shakira"}]},
          {"id":"malo","title":"Pies descalzos","artist-credit":[{"name":"Banda Tributo"}]},
          {"id":"bueno","title":"Pies Descalzos (Remastered)","artist-credit":[{"name":"Shakira"}]}
        ]}"""
        assertEquals("bueno", MusicBrainzParser.bestReleaseGroupId(body, "Shakira", "Pies descalzos"))
        assertNull(MusicBrainzParser.bestReleaseGroupId(body, "Shakira", "Un disco que no existe"))
    }

    // ---- Volumen ----

    @Test
    fun replaygain_lee_la_ganancia() {
        assertEquals(-6.5f, ReplayGain.parseTrackGain("TXXX: description=REPLAYGAIN_TRACK_GAIN: values=[-6.50 dB]")!!, 0.001f)
        assertEquals(2.1f, ReplayGain.parseTrackGain("VC: replaygain_track_gain=+2.1 dB")!!, 0.001f)
        assertNull(ReplayGain.parseTrackGain("TIT2: description=null: values=[Una canción]"))
        assertNull(ReplayGain.parseTrackGain("VC: REPLAYGAIN_TRACK_PEAK=0.98"))
    }

    // ---- Cola guardada y copia de seguridad ----

    @Test
    fun pista_ida_y_vuelta_en_json() {
        val t = track(5, genre = "Rock").copy(license = "https://creativecommons.org/licenses/by/4.0/", downloadUrl = "https://x/y.mp3")
        assertEquals(t.copy(dateAddedSec = 0, trackNumber = 0, albumId = 0), TrackJson.fromJson(TrackJson.toJson(t)))
    }

    @Test
    fun cola_guardada_ida_y_vuelta() {
        val items = (1..5).map { track(it) to "content://copia/$it" }
        val saved = SavedQueue(items, index = 3, positionMs = 42_000, shuffle = true, repeatMode = 2)
        val restored = QueueStore.decode(QueueStore.encode(saved))
        assertNotNull(restored)
        assertEquals(saved, restored)
        assertNull(QueueStore.decode("""{"items":[]}"""))
    }

    @Test
    fun cola_enorme_guarda_una_ventana_alrededor_de_la_actual() {
        val items = (1..2500).map { track(it) to "u$it" }
        val restored = QueueStore.decode(QueueStore.encode(SavedQueue(items, index = 2000, positionMs = 0, shuffle = false, repeatMode = 0)))!!
        assertEquals(QueueStore.MAX_ITEMS, restored.items.size)
        assertEquals(items[2000].first.key, restored.items[restored.index].first.key)
    }

    @Test
    fun copia_de_seguridad_ida_y_vuelta() {
        val a = track(1)
        val b = track(2, source = TrackSource.JAMENDO)
        val data = BackupData(
            tracks = mapOf(a.key to a, b.key to b),
            playlists = listOf("Para el carro" to listOf(a.key, b.key, a.key)),
            favorites = listOf(a.key to 111L),
            history = listOf(a.key to 222L, b.key to 333L),
            podcasts = listOf(PodcastEntity("https://feed", "Pod", "Autor", null, 444L)),
            overrides = listOf(TrackOverrideEntity(a.key, "Título bueno", null, null, null)),
        )
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun copia_de_seguridad_rechaza_otros_archivos() {
        BackupCodec.decode("""{"hola":"mundo"}""")
    }
}
