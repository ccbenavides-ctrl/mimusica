package app.mimusica.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.db.DownloadStatus
import app.mimusica.domain.model.Track

/** Fila de pista reutilizable con carátula, título, artista y menú ⋮. */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showArtwork: Boolean = true,
    extraMenuItems: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle()
    val downloads by container.downloads.byKey.collectAsStateWithLifecycle()
    val isCurrent = playerState.current?.key == track.key
    val download = downloads[track.key]

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = if (leading != null) 4.dp else 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        if (showArtwork) {
            TrackArtwork(track, 48.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle ?: if (track.isLive) track.artist else listOf(track.artist, formatDuration(track.durationMs)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.isOnline) {
                val license = licenseLabel(track.license)
                Text(
                    listOfNotNull(track.source.label, license).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
        }
        if (isCurrent && playerState.isPlaying) {
            Icon(Icons.Filled.GraphicEq, contentDescription = "Sonando", tint = MaterialTheme.colorScheme.primary)
        }
        if (track.key in favorites) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorito",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
        when (download?.status) {
            DownloadStatus.COMPLETED -> Icon(Icons.Filled.OfflinePin, "Descargada", Modifier.padding(start = 4.dp).size(18.dp))
            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> Icon(Icons.Filled.CloudDownload, "Descargando", Modifier.padding(start = 4.dp).size(18.dp))
            else -> Unit
        }
        TrackMenuButton(track, extraMenuItems)
    }
}

/** Menú ⋮ de pista: cola, listas, favoritos y descarga. */
@Composable
fun TrackMenuButton(
    track: Track,
    extraMenuItems: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val actions = container.trackActions
    val context = LocalContext.current
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle()
    val downloads by container.downloads.byKey.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showElsewhere by remember { mutableStateOf(false) }
    val isFavorite = track.key in favorites
    val downloadStatus = downloads[track.key]?.status

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Reproducir a continuación") },
            leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
            onClick = {
                expanded = false
                actions.playNext(track)
                Toast.makeText(context, "Sonará a continuación", Toast.LENGTH_SHORT).show()
            },
        )
        DropdownMenuItem(
            text = { Text("Agregar a la cola") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
            onClick = {
                expanded = false
                actions.addToQueue(track)
                Toast.makeText(context, "Agregada a la cola", Toast.LENGTH_SHORT).show()
            },
        )
        DropdownMenuItem(
            text = { Text("Agregar a una lista…") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
            onClick = { expanded = false; showPlaylistDialog = true },
        )
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
            leadingIcon = { Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null) },
            onClick = { expanded = false; actions.toggleFavorite(track) },
        )
        if (!track.isLive && !track.isPodcast) {
            DropdownMenuItem(
                text = { Text("Radio de esta canción") },
                leadingIcon = { Icon(Icons.Filled.Radio, null) },
                onClick = {
                    expanded = false
                    actions.songRadio(track)
                    Toast.makeText(context, "Sonando canciones parecidas de tu biblioteca", Toast.LENGTH_SHORT).show()
                },
            )
        }
        if (!track.isLive && !track.isPodcast) {
            DropdownMenuItem(
                text = { Text("Escuchar o comprar en otros servicios…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                onClick = { expanded = false; showElsewhere = true },
            )
        }
        if (!track.isOnline) {
            DropdownMenuItem(
                text = { Text("Editar datos y carátula…") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { expanded = false; showEditDialog = true },
            )
        }
        if (track.canDownload) {
            val label = when (downloadStatus) {
                DownloadStatus.COMPLETED -> "Ya descargada"
                DownloadStatus.RUNNING, DownloadStatus.QUEUED -> "Descargando…"
                else -> "Descargar"
            }
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                enabled = downloadStatus == null || downloadStatus == DownloadStatus.FAILED || downloadStatus == DownloadStatus.CANCELLED,
                onClick = {
                    expanded = false
                    actions.download(track)
                    Toast.makeText(context, "Descarga en cola", Toast.LENGTH_SHORT).show()
                },
            )
        }
        if (extraMenuItems != null) {
            HorizontalDivider()
            extraMenuItems { expanded = false }
        }
    }
    if (showPlaylistDialog) {
        AddToPlaylistDialog(tracks = listOf(track), onDismiss = { showPlaylistDialog = false })
    }
    if (showEditDialog) {
        EditTrackDialog(track = track, onDismiss = { showEditDialog = false })
    }
    if (showElsewhere) {
        FindElsewhereSheet(track.title, track.artist, onDismiss = { showElsewhere = false })
    }
}

/** Diálogo para elegir (o crear) una lista y agregarle pistas. */
@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val playlists by container.library.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nueva lista" else "Agregar a una lista") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre de la lista") },
                    singleLine = true,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("Crear lista nueva") },
                            leadingContent = { Icon(Icons.Filled.Add, null) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.trackCount} canciones") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            modifier = Modifier.clickable {
                                container.trackActions.addToPlaylist(p.id, tracks)
                                Toast.makeText(context, "Agregada a «${p.name}»", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        container.trackActions.createPlaylistWith(newName, tracks)
                        Toast.makeText(context, "Lista creada", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Crear") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
