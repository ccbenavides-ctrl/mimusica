package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mimusica.AppContainer
import app.mimusica.data.remote.IaItem
import app.mimusica.data.remote.JamendoKeyMissingException
import app.mimusica.data.remote.OnlineRepository
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.ErrorState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.appViewModel
import app.mimusica.ui.components.licenseLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IaUi(
    val query: String = "",
    val collection: String? = "etree",
    val items: List<IaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
)

data class JamendoUi(
    val mode: String = MODE_POPULAR, // "popular", "tag:<x>" o "search"
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
) {
    companion object {
        const val MODE_POPULAR = "popular"
        const val MODE_SEARCH = "search"
    }
}

class DiscoverViewModel(private val c: AppContainer) : ViewModel() {
    private val _ia = MutableStateFlow(IaUi())
    val ia: StateFlow<IaUi> = _ia.asStateFlow()
    private val _jam = MutableStateFlow(JamendoUi())
    val jamendo: StateFlow<JamendoUi> = _jam.asStateFlow()
    val hasJamendoKey: StateFlow<Boolean?> = c.settings.settings.map { it.hasJamendoKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var iaJob: Job? = null
    private var jamJob: Job? = null

    fun ensureIaLoaded() { if (!_ia.value.loaded && !_ia.value.loading) loadIa(reset = true) }

    fun setIaQuery(q: String) { _ia.value = _ia.value.copy(query = q) }

    fun selectCollection(id: String?) {
        _ia.value = _ia.value.copy(collection = if (_ia.value.collection == id) null else id)
        loadIa(reset = true)
    }

    fun searchIa() = loadIa(reset = true)

    fun loadIa(reset: Boolean) {
        val current = _ia.value
        if (!reset && (!current.hasMore || current.loading)) return
        val page = if (reset) 1 else current.page + 1
        iaJob?.cancel()
        _ia.value = current.copy(loading = true, error = null, items = if (reset) emptyList() else current.items)
        iaJob = viewModelScope.launch {
            try {
                val result = c.online.searchInternetArchive(current.query, current.collection, page)
                val s = _ia.value
                _ia.value = s.copy(
                    items = if (reset) result.items.distinctBy { it.identifier } else (s.items + result.items).distinctBy { it.identifier },
                    loading = false,
                    page = page,
                    hasMore = result.hasMore,
                    loaded = true,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ia.value = _ia.value.copy(loading = false, error = e.message ?: "Error desconocido", loaded = true)
            }
        }
    }

    fun ensureJamendoLoaded() { if (!_jam.value.loaded && !_jam.value.loading) loadJamendo(reset = true) }
    fun setJamendoQuery(q: String) { _jam.value = _jam.value.copy(query = q) }

    fun selectJamendoMode(mode: String) {
        _jam.value = _jam.value.copy(mode = mode)
        loadJamendo(reset = true)
    }

    fun searchJamendo() {
        if (_jam.value.query.isBlank()) return
        _jam.value = _jam.value.copy(mode = JamendoUi.MODE_SEARCH)
        loadJamendo(reset = true)
    }

    fun loadJamendo(reset: Boolean) {
        val current = _jam.value
        if (!reset && (!current.hasMore || current.loading)) return
        val offset = if (reset) 0 else current.offset + LIMIT
        jamJob?.cancel()
        _jam.value = current.copy(loading = true, error = null, tracks = if (reset) emptyList() else current.tracks)
        jamJob = viewModelScope.launch {
            try {
                val page = when {
                    current.mode == JamendoUi.MODE_SEARCH ->
                        c.online.jamendoTracks(search = current.query, offset = offset, limit = LIMIT)
                    current.mode.startsWith("tag:") ->
                        c.online.jamendoTracks(tag = current.mode.removePrefix("tag:"), order = "popularity_month", offset = offset, limit = LIMIT)
                    else -> c.online.jamendoTracks(order = "popularity_week", offset = offset, limit = LIMIT)
                }
                val s = _jam.value
                _jam.value = s.copy(
                    tracks = if (reset) page.tracks.distinctBy { it.key } else (s.tracks + page.tracks).distinctBy { it.key },
                    loading = false,
                    offset = offset,
                    hasMore = page.hasMore,
                    loaded = true,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: JamendoKeyMissingException) {
                _jam.value = _jam.value.copy(loading = false, loaded = false)
            } catch (e: Exception) {
                _jam.value = _jam.value.copy(loading = false, error = e.message ?: "Error desconocido", loaded = true)
            }
        }
    }

    companion object {
        const val LIMIT = 40
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onOpenIaItem: (String) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { DiscoverViewModel(it) }
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Descubrir") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Internet Archive") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Jamendo") })
            }
            if (tab == 0) InternetArchiveTab(vm, onOpenIaItem) else JamendoTab(vm, onOpenSettings)
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, "Borrar") }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
    )
}

@Composable
private fun InternetArchiveTab(vm: DiscoverViewModel, onOpenIaItem: (String) -> Unit) {
    val ui by vm.ia.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.ensureIaLoaded() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Column {
            SearchField(ui.query, vm::setIaQuery, "Buscar conciertos, álbumes, artistas…", onSearch = vm::searchIa)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnlineRepository.IA_COLLECTIONS.forEach { col ->
                    FilterChip(
                        selected = ui.collection == col.id,
                        onClick = { vm.selectCollection(col.id) },
                        label = { Text(col.label) },
                    )
                }
            }
            val desc = OnlineRepository.IA_COLLECTIONS.firstOrNull { it.id == ui.collection }?.description
                ?: "Todo el audio con licencia libre de Internet Archive."
            Text(
                desc + " Solo se muestran ítems con permiso de redistribución o licencia Creative Commons / dominio público.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } }
        if (ui.error != null && ui.items.isEmpty()) {
            item { ErrorState(ui.error!!, onRetry = { vm.loadIa(reset = true) }) }
        } else if (ui.loading && ui.items.isEmpty()) {
            item { LoadingState(message = "Consultando Internet Archive…") }
        } else if (ui.loaded && ui.items.isEmpty()) {
            item { EmptyState(Icons.Filled.Explore, "Sin resultados", "Prueba con otras palabras o quita el filtro de colección.") }
        }
        items(ui.items, key = { it.identifier }) { item ->
            ListItem(
                headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOfNotNull(item.creator, item.date, licenseLabel(item.licenseUrl)).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { Artwork(item.thumbnailUrl, 56.dp, icon = Icons.Filled.Album) },
                modifier = Modifier.clickable { onOpenIaItem(item.identifier) },
            )
        }
        if (ui.items.isNotEmpty()) {
            item {
                LoadMoreFooter(ui.loading, ui.hasMore, ui.error) { vm.loadIa(reset = false) }
            }
        }
    }
}

@Composable
fun LoadMoreFooter(loading: Boolean, hasMore: Boolean, error: String?, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        when {
            loading -> LoadingState()
            hasMore -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun JamendoTab(vm: DiscoverViewModel, onOpenSettings: () -> Unit) {
    val hasKey by vm.hasJamendoKey.collectAsStateWithLifecycle()
    val ui by vm.jamendo.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    when (hasKey) {
        null -> { LoadingState(); return }
        false -> { JamendoKeyHelp(onOpenSettings); return }
        true -> Unit
    }
    LaunchedEffect(Unit) { vm.ensureJamendoLoaded() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Column {
            SearchField(ui.query, vm::setJamendoQuery, "Buscar en Jamendo…", onSearch = vm::searchJamendo)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.mode == JamendoUi.MODE_POPULAR,
                    onClick = { vm.selectJamendoMode(JamendoUi.MODE_POPULAR) },
                    label = { Text("Populares de la semana") },
                )
                OnlineRepository.JAMENDO_GENRES.forEach { g ->
                    FilterChip(
                        selected = ui.mode == "tag:${g.tag}",
                        onClick = { vm.selectJamendoMode("tag:${g.tag}") },
                        label = { Text(g.label) },
                    )
                }
            }
            Text(
                "Música independiente con licencias Creative Commons. Solo se muestran pistas cuya descarga autorizó el artista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } }
        if (ui.error != null && ui.tracks.isEmpty()) {
            item { ErrorState(ui.error!!, onRetry = { vm.loadJamendo(reset = true) }) }
        } else if (ui.loading && ui.tracks.isEmpty()) {
            item { LoadingState(message = "Consultando Jamendo…") }
        } else if (ui.loaded && ui.tracks.isEmpty()) {
            item { EmptyState(Icons.Filled.Explore, "Sin resultados", "Prueba con otra búsqueda o género.") }
        }
        itemsIndexed(ui.tracks, key = { _, t -> t.key }) { i, t ->
            TrackRow(t, onClick = { container.trackActions.play(ui.tracks, i) })
        }
        if (ui.tracks.isNotEmpty()) {
            item { LoadMoreFooter(ui.loading, ui.hasMore, ui.error) { vm.loadJamendo(reset = false) } }
        }
    }
}

@Composable
fun JamendoKeyHelp(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row { Icon(Icons.Filled.VpnKey, null); Text("  Activa Jamendo (gratis)", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Jamendo tiene miles de canciones independientes con licencia Creative Commons. " +
                        "Para usarlo necesitas un «client_id» gratuito:\n\n" +
                        "1. Entra a devportal.jamendo.com y crea una cuenta.\n" +
                        "2. Crea una aplicación (por ejemplo «S personal»).\n" +
                        "3. Copia el «Client ID» que te muestran.\n" +
                        "4. Pégalo en Ajustes › Jamendo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) { Text("Abrir Ajustes") }
            }
        }
    }
}
