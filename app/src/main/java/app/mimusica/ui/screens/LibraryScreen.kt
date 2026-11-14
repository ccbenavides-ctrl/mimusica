package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mimusica.AppContainer
import app.mimusica.data.local.LibraryState
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.domain.model.AlbumSummary
import app.mimusica.domain.model.ArtistSummary
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.ErrorState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SongSort(val label: String) { TITLE("Título (A–Z)"), ARTIST("Artista (A–Z)"), RECENT("Agregadas recientemente") }

data class LibraryUi(
    val library: LibraryState = LibraryState(),
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val sort: SongSort = SongSort.TITLE,
)

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val sort = MutableStateFlow(SongSort.TITLE)

    val ui: StateFlow<LibraryUi> = combine(c.localMusic.state, sort) { lib, s ->
        val songs = when (s) {
            SongSort.TITLE -> lib.tracks.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> lib.tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
            SongSort.RECENT -> lib.tracks.sortedByDescending { it.dateAddedSec }
        }
        LibraryUi(
            library = lib,
            songs = songs,
            artists = LocalMusicRepository.artists(lib.tracks),
            albums = LocalMusicRepository.albums(lib.tracks),
            sort = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun setSort(s: SongSort) { sort.value = s }
    fun refresh() { viewModelScope.launch { c.localMusic.refresh() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val vm = appViewModel { LibraryViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val container = app.mimusica.ui.components.LocalAppContainer.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                actions = {
                    if (tab == 0 && ui.library.hasPermission) {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SongSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text((if (s == ui.sort) "✓ " else "") + s.label) },
                                    onClick = { vm.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenImport) { Icon(Icons.Filled.ImportExport, "Importar playlist de Spotify") }
                    IconButton(onClick = onOpenDownloads) { Icon(Icons.Filled.FileDownload, "Descargas") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Ajustes") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!ui.library.hasPermission) {
                PermissionScreen()
                return@Column
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Canciones", "Artistas", "Álbumes").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            PullToRefreshBox(
                isRefreshing = ui.library.isScanning,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    ui.library.error != null && ui.songs.isEmpty() ->
                        Box(Modifier.fillMaxSize()) { ErrorState(ui.library.error!!, onRetry = { vm.refresh() }) }
                    !ui.library.loadedOnce ->
                        Box(Modifier.fillMaxSize()) { LoadingState(message = "Buscando música en tu teléfono…") }
                    ui.songs.isEmpty() ->
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                EmptyState(
                                    icon = Icons.Filled.LibraryMusic,
                                    title = "No encontramos música",
                                    message = "No hay archivos de audio (MP3, FLAC, M4A, OGG, WAV) de más de " +
                                        "la duración mínima. Copia música al teléfono o descárgala desde Descubrir. " +
                                        "Desliza hacia abajo para volver a buscar.",
                                )
                            }
                        }
                    tab == 0 -> SongsList(ui.songs, onPlay = { list, i -> container.trackActions.play(list, i) },
                        onShuffle = { container.trackActions.shuffle(ui.songs) })
                    tab == 1 -> ArtistsList(ui.artists, onOpenArtist)
                    else -> AlbumsList(ui.albums, onOpenAlbum)
                }
            }
        }
    }
}

@Composable
fun PlayShuffleBar(count: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count canciones", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        AssistChip(onClick = onPlay, label = { Text("Reproducir") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
        AssistChip(onClick = onShuffle, label = { Text("Aleatorio") }, leadingIcon = { Icon(Icons.Filled.Shuffle, null) })
    }
}

@Composable
private fun SongsList(songs: List<Track>, onPlay: (List<Track>, Int) -> Unit, onShuffle: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PlayShuffleBar(songs.size, onPlay = { onPlay(songs, 0) }, onShuffle = onShuffle) }
        itemsIndexed(songs, key = { _, t -> t.key }) { i, t ->
            TrackRow(t, onClick = { onPlay(songs, i) })
        }
    }
}

@Composable
private fun ArtistsList(artists: List<ArtistSummary>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { a ->
            ListItem(
                headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${a.trackCount} canciones · ${a.albumCount} álbumes") },
                leadingContent = { Artwork(a.artworkUri, 48.dp, icon = Icons.Filled.Person) },
                modifier = Modifier.clickable { onOpen(a.name) },
            )
        }
    }
}

@Composable
private fun AlbumsList(albums: List<AlbumSummary>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.key }) { a ->
            ListItem(
                headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${a.artist} · ${a.trackCount} canciones", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Artwork(a.artworkUri, 56.dp, icon = Icons.Filled.Album) },
                modifier = Modifier.clickable { onOpen(a.key) },
            )
        }
    }
}
