package app.mimusica.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mimusica.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Corrige título, artista, álbum y carátula de una canción del teléfono.
 * Los cambios se guardan dentro de la app; el archivo de audio no se modifica.
 */
@Composable
fun EditTrackDialog(track: Track, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist.takeIf { it != Track.UNKNOWN_ARTIST }.orEmpty()) }
    var album by remember { mutableStateOf(track.album.takeIf { it != Track.UNKNOWN_ALBUM }.orEmpty()) }
    var artwork by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(track.key) {
        artwork = container.library.trackOverride(track.key)?.artworkUri
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "caratulas").apply { mkdirs() }
                    val file = File(dir, track.key.replace(Regex("[^A-Za-z0-9]"), "_") + "_" + System.currentTimeMillis() + ".img")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    Uri.fromFile(file).toString()
                }.getOrNull()
            }
            if (saved != null) artwork = saved
            else Toast.makeText(context, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar datos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Artwork(artwork ?: track.artworkUri, 56.dp)
                    Column {
                        OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("Elegir imagen") }
                        OutlinedButton(
                            enabled = !searching && artist.isNotBlank() && album.isNotBlank(),
                            onClick = {
                                searching = true
                                scope.launch {
                                    val found = container.artwork.find(artist, album)
                                    searching = false
                                    if (found != null) artwork = found
                                    else Toast.makeText(context, "No se encontró carátula para ese álbum", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text(if (searching) "Buscando…" else "Buscar en internet") }
                    }
                }
                Text(
                    "Los cambios se guardan en MiMúsica; el archivo de audio no se modifica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    scope.launch {
                        container.library.saveOverride(track.key, title, artist, album, artwork)
                        onDismiss()
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    scope.launch {
                        container.library.clearOverride(track.key)
                        onDismiss()
                    }
                }) { Text("Restablecer") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
