package app.mimusica.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.db.ImportedTrackEntity
import app.mimusica.domain.importer.PlaylistTextParser
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.FindElsewhereSheet
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun displayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Exception) {
    null
}

/** Importar playlists de Spotify como lista de nombres (CSV de Exportify o texto pegado). */
@Composable
fun ImportScreen(onBack: () -> Unit, onOpenResult: (Long) -> Unit, onOpenHelp: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imported by container.library.importedPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    var pasted by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Long?>(null) }

    fun runImport(content: String, fileName: String?) {
        scope.launch {
            working = true
            try {
                val parsed = withContext(Dispatchers.Default) { PlaylistTextParser.parse(content, fileName) }
                if (parsed.rows.isEmpty()) {
                    Toast.makeText(context, "No se encontraron canciones. Revisa el formato (ver Ayuda).", Toast.LENGTH_LONG).show()
                } else {
                    val finalName = name.ifBlank { parsed.suggestedName ?: "Playlist importada" }
                    val id = container.library.saveImport(finalName, parsed.rows, container.localMusic.state.value.tracks)
                    pasted = ""; name = ""
                    onOpenResult(id)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo importar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                working = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (_: Exception) {
                    null
                }
            }
            if (text == null) {
                Toast.makeText(context, "No se pudo leer el archivo.", Toast.LENGTH_LONG).show()
            } else {
                runImport(text, displayName(context, uri))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar("Importar de Spotify", onBack) {
                IconButton(onClick = onOpenHelp) { Icon(Icons.Filled.Info, "Ayuda") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Solo se importan los nombres", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "S lee la lista de canciones (título, artista, álbum) y la cruza con la música de tu " +
                                "teléfono. Las que no tengas las puedes buscar en fuentes gratuitas y legales. " +
                                "Las descargas de Spotify están cifradas y no se pueden usar fuera de Spotify.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onOpenHelp) { Text("¿Cómo exporto mi playlist?") }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de la playlist (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "*/*")) },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Text("  Elegir archivo CSV (Exportify) o .txt")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("…o pega la lista, una canción por línea («Artista - Canción»):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        placeholder = { Text("Soda Stereo - De Música Ligera\nShakira - Ojos Así") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { runImport(pasted, null) },
                        enabled = pasted.isNotBlank() && !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Importar texto pegado") }
                    if (working) LoadingState(message = "Emparejando con tu biblioteca…")
                }
            }
            item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
            item { SectionHeader("Playlists importadas") }
            if (imported.isEmpty()) {
                item {
                    Text(
                        "Todavía no has importado ninguna.",
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(imported, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Ya tienes ${p.matched} de ${p.total}") },
                    leadingContent = { Icon(Icons.Filled.ImportExport, null) },
                    trailingContent = { IconButton(onClick = { toDelete = p.id }) { Icon(Icons.Filled.Delete, "Borrar") } },
                    modifier = Modifier.clickable { onOpenResult(p.id) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar esta importación?") },
            text = { Text("Solo se borra la lista de nombres importada.") },
            confirmButton = { TextButton(onClick = { toDelete = null; scope.launch { container.library.deleteImport(id) } }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

private enum class ImportFilter(val label: String) { ALL("Todas"), HAVE("Ya la tienes"), MISSING("No la tienes") }

/** Resultado del emparejamiento de una playlist importada. */
@Composable
fun ImportResultScreen(importId: Long, onBack: () -> Unit, onSearch: (String) -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val container = LocalAppContainer.current
    var elsewhere by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by remember(importId) { container.library.importedPlaylist(importId) }.collectAsStateWithLifecycle(initialValue = null)
    val rows by remember(importId) { container.library.importedTracks(importId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val lib by container.localMusic.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ImportFilter.ALL) }
    val byKey = remember(lib.tracks) { lib.tracks.associateBy { it.key } }

    fun matchedTrack(r: ImportedTrackEntity): Track? = r.matchedTrackKey?.let { byKey[it] }
    val matchedTracks = rows.mapNotNull { matchedTrack(it) }
    val visible = when (filter) {
        ImportFilter.ALL -> rows
        ImportFilter.HAVE -> rows.filter { matchedTrack(it) != null }
        ImportFilter.MISSING -> rows.filter { matchedTrack(it) == null }
    }
    val name = playlist?.name ?: "Playlist importada"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(name, onBack) {
                IconButton(onClick = {
                    scope.launch {
                        val n = container.library.rematchImport(importId, lib.tracks)
                        Toast.makeText(context, "Emparejadas: $n", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.Refresh, "Volver a emparejar") }
            }
        },
    ) { padding ->
        if (playlist == null && rows.isEmpty()) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ya tienes ${matchedTracks.size} de ${rows.size} canciones en tu teléfono.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { container.trackActions.play(matchedTracks) },
                            enabled = matchedTracks.isNotEmpty(),
                            label = { Text("Reproducir las que tengo") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        )
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    val id = container.library.createPlaylistFromImport(name, matchedTracks)
                                    Toast.makeText(context, "Lista «$name» creada", Toast.LENGTH_SHORT).show()
                                    onOpenPlaylist(id)
                                }
                            },
                            enabled = matchedTracks.isNotEmpty(),
                            label = { Text("Convertir en lista") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImportFilter.entries.forEach { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item { EmptyState(Icons.Filled.CheckCircle, "Nada en este filtro", "Prueba con otro filtro.") }
            }
            items(visible, key = { it.id }) { r ->
                val t = matchedTrack(r)
                ListItem(
                    headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text(listOf(r.artist, r.album).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (t != null) {
                                Text("Ya la tienes: ${t.title} — ${t.artist}", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("No la tienes", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    leadingContent = {
                        if (t != null) Icon(Icons.Filled.CheckCircle, "Ya la tienes", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Filled.Search, "No la tienes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        if (t != null) {
                            IconButton(onClick = { container.trackActions.play(listOf(t)) }) { Icon(Icons.Filled.PlayArrow, "Reproducir") }
                        } else {
                            TextButton(onClick = { elsewhere = r.title to r.artist }) {
                                Text("Escuchar\no comprar")
                            }
                        }
                    },
                    modifier = if (t != null) Modifier.clickable { container.trackActions.play(listOf(t)) }
                    else Modifier.clickable { onSearch(listOf(r.artist.substringBefore(','), r.title).filter { it.isNotBlank() }.joinToString(" ")) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    elsewhere?.let { (title, artist) ->
        FindElsewhereSheet(title, artist, onDismiss = { elsewhere = null })
    }
}

/** Ayuda: cómo exportar una playlist de Spotify y por qué sus descargas no son accesibles. */
@Composable
fun ImportHelpScreen(onBack: () -> Unit) {
    val section = @Composable { title: String, body: String ->
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Ayuda: playlists de Spotify", onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            section(
                "Opción 1: Exportify (archivo CSV)",
                "1. En el navegador, abre exportify.net e inicia sesión con tu cuenta de Spotify.\n" +
                    "2. Pulsa «Export» junto a la playlist que quieras. Se descarga un archivo .csv.\n" +
                    "3. En S: Biblioteca › ícono de importar (o Listas › ⋮ › Importar playlist de Spotify) › " +
                    "«Elegir archivo CSV» y selecciona el archivo descargado.\n" +
                    "Se usan las columnas «Track Name», «Artist Name(s)» y «Album Name».",
            )
            section(
                "Opción 2: Pegar texto",
                "Escribe o pega una canción por línea con el formato «Artista - Canción». Por ejemplo:\n" +
                    "Carlos Vives - La Bicicleta\nAterciopelados - Bolero Falaz\n" +
                    "En la app de Spotify para computador puedes seleccionar las canciones de una playlist (Ctrl+A), " +
                    "copiarlas (Ctrl+C) y pegarlas en un editor de texto para obtener los nombres.",
            )
            section(
                "¿Qué hace S con la lista?",
                "Compara cada nombre con la música de tu teléfono (ignorando mayúsculas, tildes, «Remastered», «feat.», etc.). " +
                    "Las que ya tienes se pueden reproducir o convertir en una lista. Para las que no tienes, el botón " +
                    "En las canciones que no tienes, «Escuchar o comprar» abre Spotify, YouTube Music o una tienda para comprar el MP3; " +
                "tocar la fila busca en Internet Archive y Jamendo, que solo tienen música con licencia libre. " +
                    "Muchas canciones comerciales no estarán allí: es normal.",
            )
            section(
                "¿Por qué no puedo usar las canciones que descargué en Spotify?",
                "Las descargas «sin conexión» de Spotify están cifradas con DRM y solo la app de Spotify puede reproducirlas " +
                    "mientras tengas la suscripción activa. No son archivos MP3 y la ley de derechos de autor no permite " +
                    "descifrarlas ni copiarlas. Por eso S nunca las lee ni intenta descargarlas: solo importa los nombres.",
            )
            section(
                "Música que sí puedes tener en tu teléfono",
                "• Archivos que ya tienes (MP3, FLAC, M4A, OGG, WAV) copiados al teléfono.\n" +
                    "• Música que compres en tiendas que venden archivos sin DRM (por ejemplo, Bandcamp o Qobuz).\n" +
                    "• Música gratuita y legal desde Descubrir (Internet Archive y Jamendo).",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
