package app.mimusica.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mimusica.AppContainer
import app.mimusica.data.remote.RadioRepository
import app.mimusica.data.remote.RemoteApiException
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.ErrorState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Qué lista de emisoras se muestra. */
enum class RadioMode(val label: String) { MINE("Mis emisoras"), COUNTRY("Mi país"), WORLD("Top mundial"), TAG("Género") }

data class RadioUiState(
    val mode: RadioMode = RadioMode.COUNTRY,
    val tag: String? = null,
    val query: String = "",
    val country: String = "CO",
    val stations: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class RadioViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(RadioUiState())
    val ui: StateFlow<RadioUiState> = _ui.asStateFlow()
    private var job: Job? = null

    fun ensureLoaded() {
        if (_ui.value.loaded || _ui.value.loading) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(country = c.settings.settings.first().radioCountry.ifBlank { "CO" })
            load()
        }
    }

    fun setQuery(q: String) { _ui.value = _ui.value.copy(query = q) }

    fun select(mode: RadioMode, tag: String? = null) {
        _ui.value = _ui.value.copy(mode = mode, tag = tag, query = "")
        if (mode != RadioMode.MINE) load()
    }

    fun setCountry(code: String) {
        val clean = code.trim().uppercase().take(2)
        if (clean.length != 2) return
        viewModelScope.launch { c.settings.setRadioCountry(clean) }
        _ui.value = _ui.value.copy(country = clean, mode = RadioMode.COUNTRY, tag = null)
        load()
    }

    fun load() {
        job?.cancel()
        val s = _ui.value
        job = viewModelScope.launch {
            _ui.value = s.copy(loading = true, error = null)
            try {
                val q = s.query.trim().takeIf { it.isNotBlank() }
                val stations = when {
                    // Una búsqueda por nombre busca en todo el mundo.
                    q != null -> {
                        val byName = c.radio.stations(name = q)
                        val byTag = runCatching { c.radio.stations(tag = q.lowercase()) }.getOrDefault(emptyList())
                        (byName + byTag).distinctBy { it.key }
                    }
                    s.mode == RadioMode.WORLD -> c.radio.stations()
                    s.mode == RadioMode.TAG -> c.radio.stations(tag = s.tag)
                    else -> c.radio.stations(countryCode = s.country)
                }
                _ui.value = _ui.value.copy(stations = stations, loading = false, loaded = true)
            } catch (e: RemoteApiException) {
                _ui.value = _ui.value.copy(loading = false, loaded = true, error = e.message)
            }
        }
    }
}

/** Radio en vivo: emisoras de todo el mundo (directorio abierto Radio Browser). */
@Composable
fun RadioScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { RadioViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val favorites by container.library.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteKeys by container.library.favoriteKeys.collectAsStateWithLifecycle()
    val mine = favorites.filter { it.source == TrackSource.RADIO }
    LaunchedEffect(Unit) { vm.ensureLoaded() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Radio en vivo", onBack) },
    ) { padding ->
        val showing = if (ui.mode == RadioMode.MINE && ui.query.isBlank()) mine else ui.stations
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column {
                    SearchField(
                        value = ui.query,
                        onValueChange = vm::setQuery,
                        placeholder = "Emisora o género (salsa, vallenato, rock…)",
                        onSearch = { vm.load() },
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(ui.mode == RadioMode.COUNTRY, { vm.select(RadioMode.COUNTRY) }, { Text("Colombia".takeIf { ui.country == "CO" } ?: "País: ${ui.country}") })
                        FilterChip(ui.mode == RadioMode.MINE, { vm.select(RadioMode.MINE) }, { Text("Mis emisoras (${mine.size})") })
                        FilterChip(ui.mode == RadioMode.WORLD, { vm.select(RadioMode.WORLD) }, { Text("Top mundial") })
                        RadioRepository.TAGS.forEach { t ->
                            FilterChip(ui.mode == RadioMode.TAG && ui.tag == t.tag, { vm.select(RadioMode.TAG, t.tag) }, { Text(t.label) })
                        }
                    }
                }
            }
            when {
                ui.loading -> item { LoadingState(message = "Buscando emisoras…") }
                ui.error != null && !(ui.mode == RadioMode.MINE && ui.query.isBlank()) ->
                    item { ErrorState(ui.error!!, onRetry = { vm.load() }) }
                showing.isEmpty() -> item {
                    if (ui.mode == RadioMode.MINE && ui.query.isBlank()) {
                        EmptyState(Icons.Filled.Radio, "Aún no tienes emisoras guardadas", "Toca el corazón de una emisora para guardarla aquí.")
                    } else {
                        EmptyState(Icons.Filled.Radio, "Sin resultados", "Prueba con otro nombre o género.")
                    }
                }
                else -> {
                    item {
                        Text(
                            "${showing.size} emisoras · toca una para escucharla en vivo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(showing, key = { it.key }) { station ->
                        TrackRow(
                            station,
                            onClick = { container.trackActions.play(listOf(station)) },
                            leading = {
                                val fav = station.key in favoriteKeys
                                IconButton(onClick = { container.trackActions.toggleFavorite(station) }) {
                                    Icon(
                                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = if (fav) "Quitar de mis emisoras" else "Guardar en mis emisoras",
                                        tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
