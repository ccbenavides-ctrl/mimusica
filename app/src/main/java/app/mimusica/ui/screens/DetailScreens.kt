package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import app.mimusica.ui.components.TrackRow

/** Barra superior estándar con flecha de volver. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        actions = { actions() },
    )
}

@Composable
fun ArtistScreen(name: String, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val container = LocalAppContainer.current
    val lib by container.localMusic.state.collectAsStateWithLifecycle()
    val tracks = remember(lib.tracks, name) {
        lib.tracks.filter { it.artist == name }.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    }
    val albums = remember(tracks) { LocalMusicRepository.albums(tracks) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(name, onBack) },
    ) { padding ->
        if (tracks.isEmpty()) {
            EmptyState(Icons.Filled.Person, "Sin canciones", "No hay canciones de este artista en tu teléfono.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PlayShuffleBar(tracks.size, onPlay = { container.trackActions.play(tracks) }, onShuffle = { container.trackActions.shuffle(tracks) })
            }
            if (albums.size > 1) {
                item { SectionHeader("Álbumes") }
                albums.forEach { a ->
                    item(key = "album-${a.key}") {
                        ListItem(
                            headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${a.trackCount} canciones") },
                            leadingContent = { Artwork(a.artworkUri, 48.dp, icon = Icons.Filled.Album) },
                            modifier = Modifier.clickable { onOpenAlbum(a.key) },
                        )
                    }
                }
                item { SectionHeader("Canciones") }
            }
            itemsIndexed(tracks, key = { _, t -> t.key }) { i, t ->
                TrackRow(t, onClick = { container.trackActions.play(tracks, i) }, subtitle = t.album)
            }
        }
    }
}

@Composable
fun AlbumScreen(albumKey: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val lib by container.localMusic.state.collectAsStateWithLifecycle()
    val tracks = remember(lib.tracks, albumKey) { LocalMusicRepository.tracksOfAlbum(lib.tracks, albumKey) }
    val first = tracks.firstOrNull()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(first?.album ?: "Álbum", onBack) },
    ) { padding ->
        if (first == null) {
            EmptyState(Icons.Filled.Album, "Álbum vacío", "No hay canciones de este álbum en tu teléfono.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(first.artworkUri, 112.dp, icon = Icons.Filled.Album)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(first.album, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val artists = tracks.map { it.artist }.distinct()
                        Text(if (artists.size == 1) artists.first() else "Varios artistas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                PlayShuffleBar(tracks.size, onPlay = { container.trackActions.play(tracks) }, onShuffle = { container.trackActions.shuffle(tracks) })
            }
            itemsIndexed(tracks, key = { _, t -> t.key }) { i, t: Track ->
                TrackRow(
                    t,
                    onClick = { container.trackActions.play(tracks, i) },
                    showArtwork = false,
                    leading = {
                        Text(
                            if (t.trackNumber > 0) t.trackNumber.toString() else "•",
                            modifier = Modifier.width(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
