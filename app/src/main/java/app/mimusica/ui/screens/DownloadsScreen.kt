package app.mimusica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.db.DownloadEntity
import app.mimusica.data.db.DownloadStatus
import app.mimusica.data.settings.AppSettings
import app.mimusica.domain.model.Track
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var toDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    val active = downloads.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Descargas", onBack) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Descargar solo con Wi-Fi") },
                    supportingContent = { Text("Se aplica a las descargas nuevas") },
                    trailingContent = {
                        Switch(checked = settings.wifiOnlyDownloads, onCheckedChange = { v ->
                            scope.launch { container.settings.setWifiOnly(v) }
                        })
                    },
                )
            }
            item {
                Text(
                    "Las canciones se guardan en la carpeta Música/MiMusica/<Artista> y aparecen en tu Biblioteca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (downloads.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.FileDownload,
                        "Sin descargas",
                        "En Descubrir o Buscar, usa el menú ⋮ › «Descargar» en canciones de Internet Archive o Jamendo.",
                    )
                }
            }
            if (active.isNotEmpty()) {
                item { SectionHeader("En curso") }
                items(active, key = { "a-" + it.trackKey }) { d ->
                    DownloadRow(d) {
                        IconButton(onClick = { scope.launch { container.downloads.cancel(d.trackKey) } }) {
                            Icon(Icons.Filled.Close, "Cancelar")
                        }
                    }
                }
            }
            if (failed.isNotEmpty()) {
                item { SectionHeader("Fallidas o canceladas") }
                items(failed, key = { "f-" + it.trackKey }) { d ->
                    DownloadRow(d) {
                        IconButton(onClick = { scope.launch { container.downloads.retry(d.trackKey) } }) {
                            Icon(Icons.Filled.Refresh, "Reintentar")
                        }
                        IconButton(onClick = { scope.launch { container.downloads.remove(d.trackKey, deleteFile = false) } }) {
                            Icon(Icons.Filled.Delete, "Quitar de la lista")
                        }
                    }
                }
            }
            if (completed.isNotEmpty()) {
                item { SectionHeader("Completadas") }
                items(completed, key = { "c-" + it.trackKey }) { d ->
                    DownloadRow(d) {
                        IconButton(onClick = {
                            if (d.localUri == null) return@IconButton
                            // Se usa la referencia online original: el reproductor la cambia por la copia local.
                            val track = Track(
                                key = d.trackKey, title = d.title, artist = d.artist, album = d.album,
                                durationMs = 0, uri = d.sourceUrl, artworkUri = null,
                                source = Track.sourceOfKey(d.trackKey), downloadUrl = d.sourceUrl,
                            )
                            scope.launch {
                                val cached = container.database.cachedTrackDao().get(d.trackKey)?.toTrack()
                                container.trackActions.play(listOf(cached ?: track))
                            }
                        }) { Icon(Icons.Filled.PlayArrow, "Reproducir") }
                        IconButton(onClick = { toDelete = d }) { Icon(Icons.Filled.Delete, "Borrar") }
                    }
                }
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar «${d.title}»?") },
            text = { Text("Puedes borrar también el archivo del teléfono o solo quitarlo de esta lista.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        val ok = container.downloads.remove(d.trackKey, deleteFile = true)
                        if (!ok) Toast.makeText(context, "No se pudo borrar el archivo; bórralo desde tu gestor de archivos.", Toast.LENGTH_LONG).show()
                        container.localMusic.scheduleRefresh()
                    }
                }) { Text("Borrar archivo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { container.downloads.remove(d.trackKey, deleteFile = false) }
                }) { Text("Solo quitar de la lista") }
            },
        )
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(d.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            when (d.status) {
                DownloadStatus.QUEUED -> Text("En espera (de red o Wi-Fi)…", style = MaterialTheme.typography.labelSmall)
                DownloadStatus.RUNNING -> {
                    LinearProgressIndicator(progress = { d.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    Text("${d.progress} %", style = MaterialTheme.typography.labelSmall)
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED ->
                    Text(d.error ?: "Falló", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                else -> Text("Guardada en Música/MiMusica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        actions()
    }
}
