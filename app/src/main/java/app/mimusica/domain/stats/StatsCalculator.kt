package app.mimusica.domain.stats

import app.mimusica.domain.model.Track

/** Una reproducción del historial con su pista. */
data class PlayEvent(val track: Track, val playedAt: Long)

data class TrackStat(val track: Track, val plays: Int)
data class ArtistStat(val artist: String, val plays: Int, val minutes: Long, val artworkUri: String?)

data class ListeningStats(
    val totalPlays: Int,
    val totalMinutes: Long,
    val activeDays: Int,
    val distinctTracks: Int,
    val distinctArtists: Int,
    val topTracks: List<TrackStat>,
    val topArtists: List<ArtistStat>,
    /** Reproducciones por hora del día (0–23). */
    val byHour: List<Int>,
) {
    val isEmpty: Boolean get() = totalPlays == 0
    val peakHour: Int? get() = if (isEmpty) null else byHour.indices.maxByOrNull { byHour[it] }
}

/** Estadísticas de escucha ("tu resumen"). Sin dependencias de Android. */
object StatsCalculator {

    private const val DAY_MS = 86_400_000L

    /**
     * [zoneOffsetMs] es el desfase de la zona horaria local para agrupar por día y hora.
     * El tiempo escuchado es aproximado: cada reproducción cuenta la duración de la canción
     * (las emisoras en vivo no suman minutos porque no tienen duración).
     */
    fun compute(events: List<PlayEvent>, zoneOffsetMs: Long = 0L, top: Int = 10): ListeningStats {
        val byHour = IntArray(24)
        val days = HashSet<Long>()
        var totalMs = 0L
        for (e in events) {
            val local = e.playedAt + zoneOffsetMs
            days.add(Math.floorDiv(local, DAY_MS))
            byHour[(Math.floorMod(local, DAY_MS) / 3_600_000L).toInt().coerceIn(0, 23)]++
            totalMs += e.track.durationMs.coerceAtLeast(0)
        }
        val topTracks = events.groupBy { it.track.key }
            .map { (_, list) -> TrackStat(list.first().track, list.size) }
            .sortedWith(compareByDescending<TrackStat> { it.plays }.thenBy { it.track.title.lowercase() })
        val topArtists = events
            .filter { it.track.artist.isNotBlank() && it.track.artist != Track.UNKNOWN_ARTIST && !it.track.isLive }
            .groupBy { it.track.artist }
            .map { (artist, list) ->
                ArtistStat(
                    artist = artist,
                    plays = list.size,
                    minutes = list.sumOf { it.track.durationMs.coerceAtLeast(0) } / 60_000L,
                    artworkUri = list.firstOrNull { !it.track.artworkUri.isNullOrBlank() }?.track?.artworkUri,
                )
            }
            .sortedWith(compareByDescending<ArtistStat> { it.plays }.thenBy { it.artist.lowercase() })
        return ListeningStats(
            totalPlays = events.size,
            totalMinutes = totalMs / 60_000L,
            activeDays = days.size,
            distinctTracks = topTracks.size,
            distinctArtists = topArtists.size,
            topTracks = topTracks.take(top),
            topArtists = topArtists.take(top),
            byHour = byHour.toList(),
        )
    }
}
