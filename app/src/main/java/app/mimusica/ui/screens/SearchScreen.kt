package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mimusica.AppContainer
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.data.remote.IaItem
import app.mimusica.data.remote.JamendoKeyMissingException
import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.model.AlbumSummary
import app.mimusica.domain.model.ArtistSummary
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.FindElsewhereRow
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.appViewModel
import app.mimusica.ui.components.licenseLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers

data class LocalResults(
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
)

/** Resultado de una fuente online: cargando, error o datos. */
data class OnlineSection<T>(val loading: Boolean = false, val error: String? = null, val items: List<T> = emptyList(), val disabled: Boolean = false)

data class OnlineResults(
    val ia: OnlineSection<IaItem> = OnlineSection(),
    val jamendo: OnlineSection<Track> = OnlineSection(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val c: AppContainer, initialQuery: String) : ViewModel() {
    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    /** Búsqueda local instantánea. */
    val local: StateFlow<LocalResults> = combine(_query, c.localMusic.state) { q, lib ->
        val nq = TextNormalizer.basic(q)
        if (nq.length < 1) return@combine LocalResults()
        val songs = lib.tracks.filter {
            TextNormalizer.basic(it.title).contains(nq) || TextNormalizer.basic(it.artist).contains(nq) ||
                TextNormalizer.basic(it.album).contains(nq)
        }
        LocalResults(
            songs = songs.take(50),
            artists = LocalMusicRepository.artists(lib.tracks).filter { TextNormalizer.basic(it.name).contains(nq) }.take(10),
            albums = LocalMusicRepository.albums(lib.tracks).filter { TextNormalizer.basic(it.title).contains(nq) }.take(10),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalResults())

    /** Búsqueda online con debounce; una búsqueda nueva cancela la anterior. */
    val online: StateFlow<OnlineResults> = _query
        .map { it.trim() }
        .debounce(600)
        .distinctUntilChanged()
        .transformLatest { q ->
            if (q.length < 2) {
                emit(OnlineResults())
                return@transformLatest
            }
            var current = OnlineResults(ia = OnlineSection(loading = true), jamendo = OnlineSection(loading = true))
            emit(current)
            val ia: OnlineSection<IaItem> = try {
                OnlineSection(items = c.online.searchInternetArchive(q, null, 1, 20).items.distinctBy { it.identifier })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlineSection(error = e.message ?: "Error desconocido")
            }
            current = current.copy(ia = ia)
            emit(current)
            val jam: OnlineSection<Track> = try {
                OnlineSection(items = c.online.jamendoTracks(search = q, limit = 20).tracks.distinctBy { it.key })
            } catch (e: JamendoKeyMissingException) {
                OnlineSection(disabled = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlineSection(error = e.message ?: "Error desconocido")
            }
            emit(current.copy(jamendo = jam))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineResults())

    fun setQuery(q: String) { _query.value = q }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenIaItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel(key = "search-$initialQuery") { SearchViewModel(it, initialQuery) }
    val query by vm.query.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Buscar") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                SearchField(query, vm::setQuery, "Canción, artista o álbum", onSearch = {})
            }
            if (query.isBlank()) {
                item {
                    EmptyState(
                        Icons.Filled.Search,
                        "Busca en todas partes",
                        "Un solo buscador: tu música, la gratuita de Internet Archive y Jamendo, y cualquier canción en Spotify, YouTube Music o tiendas para comprarla.",
                    )
                }
                return@LazyColumn
            }

            // ---- Escucharla o comprarla en otros servicios ----
            item { SectionHeader("Música de cualquier artista") }
            item { FindElsewhereRow(query.trim()) }

            // ---- En mi teléfono ----
            item { SectionHeader("En mi teléfono") }
            if (local.songs.isEmpty() && local.artists.isEmpty() && local.albums.isEmpty()) {
                item { Text("Sin coincidencias en tu teléfono.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(local.artists, key = { "artist-${it.name}" }) { a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text("Artista · ${a.trackCount} canciones") },
                    leadingContent = { Artwork(a.artworkUri, 40.dp, icon = Icons.Filled.Person) },
                    modifier = Modifier.clickable { onOpenArtist(a.name) },
                )
            }
            items(local.albums, key = { "album-${it.key}" }) { a ->
                ListItem(
                    headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Álbum · ${a.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Artwork(a.artworkUri, 40.dp, icon = Icons.Filled.Album) },
                    modifier = Modifier.clickable { onOpenAlbum(a.key) },
                )
            }
            itemsIndexed(local.songs, key = { _, t -> "song-${t.key}" }) { i, t ->
                TrackRow(t, onClick = { container.trackActions.play(local.songs, i) })
            }

            // ---- Internet Archive ----
            item { SectionHeader("Internet Archive") }
            onlineStatus(online.ia, emptyText = "Sin resultados en Internet Archive.")
            items(online.ia.items, key = { "ia-${it.identifier}" }) { item ->
                ListItem(
                    headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(listOfNotNull(item.creator, licenseLabel(item.licenseUrl)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Artwork(item.thumbnailUrl, 40.dp, icon = Icons.Filled.Album) },
                    modifier = Modifier.clickable { onOpenIaItem(item.identifier) },
                )
            }

            // ---- Jamendo ----
            item { SectionHeader("Jamendo") }
            if (online.jamendo.disabled) {
                item {
                    ListItem(
                        headlineContent = { Text("Activa Jamendo para buscar también allí") },
                        supportingContent = { Text("Necesitas un client_id gratuito (Ajustes › Jamendo).") },
                        trailingContent = { TextButton(onClick = onOpenSettings) { Text("Ajustes") } },
                    )
                }
            } else {
                onlineStatus(online.jamendo, emptyText = "Sin resultados en Jamendo.")
                itemsIndexed(online.jamendo.items, key = { _, t -> "jam-${t.key}" }) { i, t ->
                    TrackRow(t, onClick = { container.trackActions.play(online.jamendo.items, i) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun <T> LazyListScope.onlineStatus(section: OnlineSection<T>, emptyText: String) {
    when {
        section.loading -> item { LoadingState(message = "Buscando…") }
        section.error != null -> item {
            Text(section.error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        }
        section.items.isEmpty() -> item {
            Text(emptyText, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
