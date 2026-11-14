package app.mimusica

import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.matching.TrackMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchingTest {

    @Test
    fun basicRemovesAccentsCaseAndPunctuation() {
        assertEquals("cancion de amor", TextNormalizer.basic("¡Canción  de AMOR!"))
        assertEquals("nino", TextNormalizer.basic("Niño"))
        assertEquals("rock and roll", TextNormalizer.basic("Rock & Roll"))
    }

    @Test
    fun titleRemovesVersionNoise() {
        assertEquals("bohemian rhapsody", TextNormalizer.title("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("bohemian rhapsody", TextNormalizer.title("Bohemian Rhapsody (Remastered 2011)"))
        assertEquals("despacito", TextNormalizer.title("Despacito (feat. Daddy Yankee)"))
        assertEquals("la bamba", TextNormalizer.title("La Bamba [En Vivo]"))
        assertEquals("hotel california", TextNormalizer.title("Hotel California - Live"))
        assertEquals("song", TextNormalizer.title("Song feat. Someone"))
    }

    @Test
    fun titleKeepsMeaningfulParentheses() {
        assertEquals("i can t get no satisfaction", TextNormalizer.title("(I Can't Get No) Satisfaction"))
        assertEquals("paint it black", TextNormalizer.title("Paint It, Black"))
    }

    @Test
    fun titleNeverReturnsBlankForNoiseOnlyTitles() {
        assertEquals("live", TextNormalizer.title("Live"))
    }

    @Test
    fun artistsSplitsCollaborations() {
        assertEquals(listOf("luis fonsi", "daddy yankee"), TextNormalizer.artists("Luis Fonsi, Daddy Yankee"))
        assertEquals(listOf("beatles"), TextNormalizer.artists("The Beatles"))
        assertEquals(listOf("bad bunny", "jhay cortez"), TextNormalizer.artists("Bad Bunny feat. Jhay Cortez"))
        assertTrue(TextNormalizer.artists("").isEmpty())
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, TrackMatcher.levenshtein("abc", "abc"))
        assertEquals(3, TrackMatcher.levenshtein("", "abc"))
        assertEquals(1, TrackMatcher.levenshtein("gato", "pato"))
        assertEquals(3, TrackMatcher.levenshtein("kitten", "sitting"))
    }

    @Test
    fun matcherFindsExactAfterNormalization() {
        val lib = listOf(
            Fixtures.local(1, "Bohemian Rhapsody", "Queen"),
            Fixtures.local(2, "Despacito", "Luis Fonsi"),
            Fixtures.local(3, "Another One Bites the Dust", "Queen"),
        )
        val m = TrackMatcher(lib)
        assertEquals("local:1", m.findBest("Bohemian Rhapsody - Remastered 2011", "Queen")?.track?.key)
        assertEquals("local:2", m.findBest("Despacito (feat. Daddy Yankee)", "Luis Fonsi,Daddy Yankee")?.track?.key)
    }

    @Test
    fun matcherToleratesSmallTypos() {
        val lib = listOf(Fixtures.local(1, "Corazón Espinado", "Santana"))
        val result = TrackMatcher(lib).findBest("Corazon Espinao", "Santana feat. Maná")
        assertNotNull(result)
        assertEquals("local:1", result!!.track.key)
    }

    @Test
    fun matcherPrefersCorrectArtistWhenTitlesCollide() {
        val lib = listOf(
            Fixtures.local(1, "Hurt", "Nine Inch Nails"),
            Fixtures.local(2, "Hurt", "Johnny Cash"),
        )
        assertEquals("local:2", TrackMatcher(lib).findBest("Hurt", "Johnny Cash")?.track?.key)
    }

    @Test
    fun matcherRejectsDifferentSongs() {
        val lib = listOf(
            Fixtures.local(1, "Yesterday", "The Beatles"),
            Fixtures.local(2, "Let It Be", "The Beatles"),
        )
        val m = TrackMatcher(lib)
        assertNull(m.findBest("Imagine", "John Lennon"))
        assertNull(m.findBest("Yesterday", "Totally Different Band"))
    }

    @Test
    fun matcherWorksWithoutArtist() {
        val lib = listOf(Fixtures.local(7, "Gracias a la Vida", "Mercedes Sosa"))
        assertEquals("local:7", TrackMatcher(lib).findBest("gracias a la vida", "")?.track?.key)
    }
}
