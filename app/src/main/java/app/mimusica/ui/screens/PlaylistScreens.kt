package app.mimusica.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.library.AutoPlaylist
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import app.mimusica.ui.components.TrackRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lanzador para exportar una lista a .m3u con el selector de archivos del sistema. */
@Composable
private fun rememberM3uExporter(name: String, tracks: () -> List<Track>): () -> Unit {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                container.library.exportM3u(name, tracks(), uri)
                Toast.makeText(context, "Lista exportada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return { launcher.launch("${name.replace(Regex("""[\\/:*?"<>|]"""), "_")}.m3u") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenAuto: (String) -> Unit,
    onOpenImport: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by container.library.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val importM3u = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/')
                val result = container.library.importM3u(uri, name, container.localMusic.state.value.tracks)
                Toast.makeText(
                    context,
                    "«${result.name}»: ${result.added} canciones" + if (result.missing > 0) ", ${result.missing} no encontradas" else "",
                    Toast.LENGTH_LONG,
                ).show()
                onOpenPlaylist(result.playlistId)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo importar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Listas") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Importar lista .m3u") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                menu = false
                                importM3u.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar playlist de Spotify (nombres)") },
                            leadingIcon = { Icon(Icons.Filled.ImportExport, null) },
                            onClick = { menu = false; onOpenImport() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nueva lista") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Listas automáticas") }
            items(AutoPlaylist.entries) { auto ->
                ListItem(
                    headlineContent = { Text(auto.title) },
                    leadingContent = {
                        Icon(
                            when (auto) {
                                AutoPlaylist.FAVORITES -> Icons.Filled.Favorite
                                AutoPlaylist.RECENT -> Icons.Filled.History
                                AutoPlaylist.MOST_PLAYED -> Icons.Filled.Star
                                AutoPlaylist.RECENTLY_ADDED -> Icons.Filled.NewReleases
                            },
                            null,
                        )
                    },
                    modifier = Modifier.clickable { onOpenAuto(auto.id) },
                )
            }
            item { SectionHeader("Mis listas") }
            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "Aún no tienes listas",
                        message = "Crea una con el botón «Nueva lista» o agrega canciones desde el menú ⋮ de cualquier pista.",
                    )
                }
            }
            items(playlists, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${p.trackCount} canciones") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Nueva lista",
            initial = "",
            confirm = "Crear",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                scope.launch {
                    val id = container.library.createPlaylist(name)
                    onOpenPlaylist(id)
                }
            },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nombre") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PlaylistDetailScreen(playlistId: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val playlist by remember(playlistId) { container.library.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val tracks by remember(playlistId) { container.library.playlistTracks(playlistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = playlist?.name ?: "Lista"
    val export = rememberM3uExporter(name) { tracks }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(name, onBack) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opciones de la lista") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Exportar a .m3u") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                        onClick = { menu = false; export() })
                    DropdownMenuItem(text = { Text("Borrar lista") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; confirmDelete = true })
                }
            }
        },
    ) { padding ->
        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Lista vacía",
                message = "Agrega canciones desde el menú ⋮ de cualquier pista (Biblioteca, Descubrir o Buscar).",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PlayShuffleBar(tracks.size, onPlay = { container.trackActions.play(tracks) }, onShuffle = { container.trackActions.shuffle(tracks) })
            }
            itemsIndexed(tracks) { i, t ->
                TrackRow(
                    t,
                    onClick = { container.trackActions.play(tracks, i) },
                    extraMenuItems = { dismiss ->
                        DropdownMenuItem(
                            text = { Text("Subir") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                            enabled = i > 0,
                            onClick = { dismiss(); scope.launch { container.library.movePlaylistItem(playlistId, i, i - 1) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Bajar") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                            enabled = i < tracks.lastIndex,
                            onClick = { dismiss(); scope.launch { container.library.movePlaylistItem(playlistId, i, i + 1) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Quitar de esta lista") },
                            leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null) },
                            onClick = { dismiss(); scope.launch { container.library.removeFromPlaylist(playlistId, i) } },
                        )
                    },
                )
            }
        }
    }

    if (renaming) {
        NameDialog("Renombrar lista", name, "Guardar", onDismiss = { renaming = false }) { newName ->
            renaming = false
            scope.launch { container.library.renamePlaylist(playlistId, newName) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «$name»?") },
            text = { Text("Se borra la lista, no las canciones.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.library.deletePlaylist(playlistId)
                        onBack()
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun AutoPlaylistScreen(autoId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val auto = AutoPlaylist.fromId(autoId) ?: AutoPlaylist.FAVORITES
    val flow: Flow<List<Track>> = remember(auto) {
        when (auto) {
            AutoPlaylist.FAVORITES -> container.library.favorites
            AutoPlaylist.RECENT -> container.library.recent()
            AutoPlaylist.MOST_PLAYED -> container.library.mostPlayed()
            AutoPlaylist.RECENTLY_ADDED -> container.localMusic.state.map { s ->
                s.tracks.sortedByDescending { it.dateAddedSec }.take(100)
            }
        }
    }
    val tracks by remember(flow) { flow }.collectAsStateWithLifecycle(initialValue = emptyList())
    val export = rememberM3uExporter(auto.title) { tracks }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(auto.title, onBack) {
                if (tracks.isNotEmpty()) {
                    IconButton(onClick = export) { Icon(Icons.Filled.FileUpload, "Exportar a .m3u") }
                }
                if (auto == AutoPlaylist.RECENT || auto == AutoPlaylist.MOST_PLAYED) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Borrar historial") }
                }
            }
        },
    ) { padding ->
        if (tracks.isEmpty()) {
            val message = when (auto) {
                AutoPlaylist.FAVORITES -> "Toca el corazón o usa el menú ⋮ › «Agregar a favoritos» en cualquier canción."
                AutoPlaylist.RECENT, AutoPlaylist.MOST_PLAYED -> "Aquí aparecerán las canciones que escuches más de 10 segundos."
                AutoPlaylist.RECENTLY_ADDED -> "No hay canciones en tu teléfono todavía."
            }
            EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Nada por aquí", message, Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PlayShuffleBar(tracks.size, onPlay = { container.trackActions.play(tracks) }, onShuffle = { container.trackActions.shuffle(tracks) })
            }
            itemsIndexed(tracks, key = { _, t -> t.key }) { i, t ->
                TrackRow(t, onClick = { container.trackActions.play(tracks, i) })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar el historial?") },
            text = { Text("Se vacían «Escuchado recientemente» y «Más escuchadas».") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { container.library.clearHistory() } }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
