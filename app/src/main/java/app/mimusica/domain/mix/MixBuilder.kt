package app.mimusica.domain.mix

import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.model.Track
import kotlin.random.Random

/** Una mezcla automática lista para reproducir. */
data class Mix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artworkUri: String? get() = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
}

/** Mezclas automáticas y "radio de una canción" a partir de la biblioteca local. Sin dependencias de Android. */
object MixBuilder {

    /**
     * Cola de canciones parecidas a [seed]: mismo artista, mismo álbum y mismo género primero.
     * La semilla va siempre de primera.
     */
    fun songRadio(
        seed: Track,
        library: List<Track>,
        favorites: Set<String> = emptySet(),
        size: Int = 50,
        random: Random = Random.Default,
    ): List<Track> {
        val seedArtists = TextNormalizer.artists(seed.artist).toSet()
        val seedAlbum = TextNormalizer.basic(seed.album)
        val seedGenre = TextNormalizer.basic(seed.genre)
        val scored = library.asSequence()
            .filter { it.key != seed.key }
            .map { t ->
                var score = 0.0
                val artists = TextNormalizer.artists(t.artist)
                if (seedArtists.isNotEmpty() && artists.any { it in seedArtists }) score += 5.0
                if (seedAlbum.isNotBlank() && TextNormalizer.basic(t.album) == seedAlbum) score += 2.0
                if (seedGenre.isNotBlank() && TextNormalizer.basic(t.genre) == seedGenre) score += 3.0
                if (t.key in favorites) score += 1.0
                score += random.nextDouble() * 2.5
                t to score
            }
            .sortedByDescending { it.second }
            .take((size - 1).coerceAtLeast(0))
            .map { it.first }
            .toList()
        // Se intercalan un poco para que no suenen todas las del mismo artista seguidas.
        val related = scored.take(scored.size / 2).shuffled(random) + scored.drop(scored.size / 2).shuffled(random)
        return listOf(seed) + related
    }

    /** Mezclas para la pantalla de inicio. [playCounts] = reproducciones por clave de pista. */
    fun homeMixes(
        library: List<Track>,
        favorites: Set<String>,
        playCounts: Map<String, Int>,
        maxArtistMixes: Int = 4,
        maxGenreMixes: Int = 3,
        random: Random = Random.Default,
    ): List<Mix> {
        if (library.isEmpty()) return emptyList()
        val mixes = mutableListOf<Mix>()

        val favTracks = library.filter { it.key in favorites }
        if (favTracks.size >= 5) {
            mixes += Mix("fav", "Mezcla de favoritos", "${favTracks.size} canciones que marcaste", favTracks.shuffled(random).take(60))
        }

        val neverPlayed = library.filter { (playCounts[it.key] ?: 0) == 0 }
        if (neverPlayed.size >= 10) {
            mixes += Mix("nuevo", "Redescubre tu música", "Canciones que no has oído aquí", neverPlayed.shuffled(random).take(60))
        }

        // Artistas con más reproducciones (o con más canciones si aún no hay historial).
        val byArtist = library.filter { it.artist != Track.UNKNOWN_ARTIST }.groupBy { it.artist }
        val artistRank = byArtist.entries
            .filter { it.value.size >= 5 }
            .sortedByDescending { (_, list) -> list.sumOf { (playCounts[it.key] ?: 0) * 10 } + list.size }
            .take(maxArtistMixes)
        for ((artist, list) in artistRank) {
            mixes += Mix("artista:$artist", "Mezcla de $artist", "${list.size} canciones", list.shuffled(random).take(60))
        }

        val byGenre = library.filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }
        val genreRank = byGenre.entries.filter { it.value.size >= 10 }.sortedByDescending { it.value.size }.take(maxGenreMixes)
        for ((genre, list) in genreRank) {
            mixes += Mix("genero:$genre", "Mezcla de $genre", "${list.size} canciones", list.shuffled(random).take(60))
        }

        if (library.size >= 10) {
            mixes += Mix("todo", "Todo al azar", "${library.size} canciones", library.shuffled(random).take(100))
        }
        return mixes
    }
}
