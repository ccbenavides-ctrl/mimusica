package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.library.AutoPlaylist
import app.mimusica.domain.mix.Mix
import app.mimusica.domain.mix.MixBuilder
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackArtwork
import java.util.Calendar

/** Pantalla de inicio: seguir escuchando, mezclas automáticas, lo más oído y accesos rápidos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRadio: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuto: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val lib by container.localMusic.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle()
    val playCounts by container.library.playCounts.collectAsStateWithLifecycle(initialValue = emptyMap())
    val recent by remember { container.library.recent(20) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mostPlayed by remember { container.library.mostPlayed(20) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteTracks by container.library.favorites.collectAsStateWithLifecycle(initialValue = emptyList())

    // Las mezclas se recalculan solo cuando cambia la biblioteca o los favoritos (no en cada reproducción).
    val mixes = remember(lib.tracks, favorites, playCounts.isEmpty()) { MixBuilder.homeMixes(lib.tracks, favorites, playCounts) }
    val recentlyAdded = remember(lib.tracks) { lib.tracks.sortedByDescending { it.dateAddedSec }.take(20) }
    val stations = remember(favoriteTracks) { favoriteTracks.filter { it.source == TrackSource.RADIO } }
    val recentMusic = remember(recent) { recent.filter { !it.isLive } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(greeting()) },
                actions = {
                    IconButton(onClick = onOpenDownloads) { Icon(Icons.Filled.FileDownload, "Descargas") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Ajustes") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickTile("Radio en vivo", Icons.Filled.Radio, onOpenRadio, Modifier.weight(1f))
                    QuickTile("Podcasts", Icons.Filled.Podcasts, onOpenPodcasts, Modifier.weight(1f))
                    QuickTile("Tu resumen", Icons.Filled.BarChart, onOpenStats, Modifier.weight(1f))
                }
            }

            if (lib.tracks.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AssistChip(
                            onClick = { container.trackActions.shuffle(lib.tracks) },
                            label = { Text("Reproducir todo al azar") },
                            leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                        )
                    }
                }
            }

            if (recentMusic.isNotEmpty()) {
                item { HomeHeader("Sigue escuchando") { onOpenAuto(AutoPlaylist.RECENT.id) } }
                item { TrackCarousel(recentMusic) { i -> container.trackActions.play(recentMusic, i) } }
            }

            if (mixes.isNotEmpty()) {
                item { HomeHeader("Mezclas para ti") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(mixes, key = { it.id }) { mix -> MixCard(mix) { container.trackActions.play(mix.tracks) } }
                    }
                }
            }

            if (stations.isNotEmpty()) {
                item { HomeHeader("Tus emisoras", onMore = onOpenRadio) }
                item { TrackCarousel(stations) { i -> container.trackActions.play(listOf(stations[i])) } }
            }

            if (recentlyAdded.isNotEmpty()) {
                item { HomeHeader("Agregado recientemente") { onOpenAuto(AutoPlaylist.RECENTLY_ADDED.id) } }
                item { TrackCarousel(recentlyAdded) { i -> container.trackActions.play(recentlyAdded, i) } }
            }

            if (mostPlayed.isNotEmpty()) {
                item { HomeHeader("Tus más oídas") { onOpenAuto(AutoPlaylist.MOST_PLAYED.id) } }
                item { TrackCarousel(mostPlayed) { i -> container.trackActions.play(mostPlayed, i) } }
            }

            if (lib.loadedOnce && lib.tracks.isEmpty() && recent.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Home,
                        title = "Empieza a escuchar",
                        message = "Aquí aparecerán tus mezclas y lo que más oyes. Mientras tanto, prueba la radio en vivo, " +
                            "los podcasts o la música gratuita de la pestaña Descubrir.",
                    )
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun HomeHeader(title: String, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onMore != null) {
            Text(
                "Ver todo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onMore).padding(8.dp),
            )
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackCarousel(tracks: List<Track>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(tracks) { i, t ->
            Column(Modifier.width(128.dp).clickable { onPlay(i) }) {
                TrackArtwork(t, 128.dp)
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MixCard(mix: Mix, onPlay: () -> Unit) {
    Column(Modifier.width(148.dp).clickable(onClick = onPlay)) {
        val first = mix.tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }
        if (first != null) TrackArtwork(first, 148.dp) else Artwork(null, 148.dp, icon = Icons.Filled.Shuffle)
        Spacer(Modifier.height(6.dp))
        Text(mix.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
