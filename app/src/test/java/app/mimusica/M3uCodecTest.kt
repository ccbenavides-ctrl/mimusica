package app.mimusica

import app.mimusica.domain.m3u.M3uCodec
import app.mimusica.domain.m3u.M3uEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uCodecTest {

    @Test
    fun encodeThenDecodeRoundTrip() {
        val entries = listOf(
            M3uEntry("content://media/external/audio/media/42", "Mi Canción", "Artista", 215),
            M3uEntry("https://archive.org/download/bof024/02_Moscow%20Day.mp3", "Moscow Day", "EugeneKha", 1989),
            M3uEntry("https://example.com/sin-metadatos.mp3", null, null, -1),
        )
        val text = M3uCodec.encode("Para el bus", entries)
        assertEquals(true, text.startsWith("#EXTM3U\n#PLAYLIST:Para el bus\n#EXTINF:215,Artista - Mi Canción\n"))
        val decoded = M3uCodec.decode(text)
        assertEquals("Para el bus", decoded.name)
        assertEquals(3, decoded.entries.size)
        assertEquals(entries[0], decoded.entries[0])
        assertEquals(entries[1], decoded.entries[1])
        assertEquals(M3uEntry("https://example.com/sin-metadatos.mp3", null, null, -1), decoded.entries[2])
    }

    @Test
    fun decodesPlainM3uWithBomAndWindowsPaths() {
        val text = "\uFEFF/storage/emulated/0/Music/Uno.mp3\r\nC:\\Musica\\Dos.flac\r\n"
        val decoded = M3uCodec.decode(text)
        assertNull(decoded.name)
        assertEquals(listOf("/storage/emulated/0/Music/Uno.mp3", "C:\\Musica\\Dos.flac"), decoded.entries.map { it.location })
        assertEquals("Uno", M3uCodec.baseName(decoded.entries[0].location))
        assertEquals("Dos", M3uCodec.baseName(decoded.entries[1].location))
    }

    @Test
    fun keyFromLocationRecognizesSources() {
        assertEquals("local:42", M3uCodec.keyFromLocation("content://media/external/audio/media/42"))
        assertEquals("local:7", M3uCodec.keyFromLocation("content://media/external_primary/audio/media/7"))
        assertEquals("ia:bof024/02 Moscow Day.mp3", M3uCodec.keyFromLocation("https://archive.org/download/bof024/02%20Moscow%20Day.mp3"))
        assertEquals("ia:gd77/disc 1/t01.mp3", M3uCodec.keyFromLocation("https://archive.org/download/gd77/disc%201/t01.mp3"))
        assertEquals("jam:1848357", M3uCodec.keyFromLocation("https://prod-1.storage.jamendo.com/?trackid=1848357&format=mp31"))
        assertNull(M3uCodec.keyFromLocation("/sdcard/Music/x.mp3"))
    }
}
