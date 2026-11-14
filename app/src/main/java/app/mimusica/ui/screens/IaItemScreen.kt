package app.mimusica.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mimusica.AppContainer
import app.mimusica.data.remote.IaItemDetails
import app.mimusica.ui.components.AddToPlaylistDialog
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.ErrorState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.appViewModel
import app.mimusica.ui.components.licenseLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IaItemUi(val loading: Boolean = true, val details: IaItemDetails? = null, val error: String? = null)

class IaItemViewModel(private val c: AppContainer, private val identifier: String) : ViewModel() {
    private val _ui = MutableStateFlow(IaItemUi())
    val ui: StateFlow<IaItemUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = IaItemUi(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                IaItemUi(loading = false, details = c.online.internetArchiveItem(identifier))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                IaItemUi(loading = false, error = e.message ?: "Error desconocido")
            }
        }
    }
}

@Composable
fun IaItemScreen(identifier: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "ia-$identifier") { IaItemViewModel(it, identifier) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var addAll by remember { mutableStateOf(false) }
    val details = ui.details

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(details?.title ?: "Internet Archive", onBack) {
                if (details != null) {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(details.pageUrl)))
                    }) { Icon(Icons.Filled.Public, "Ver en archive.org") }
                }
            }
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding), "Cargando pistas…")
            ui.error != null -> ErrorState(ui.error!!, Modifier.padding(padding), onRetry = vm::load)
            details == null || details.tracks.isEmpty() -> EmptyState(
                Icons.Filled.Album,
                "Sin audio disponible",
                "Este ítem no tiene archivos de audio descargables (algunos conciertos solo permiten streaming en la web).",
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(details.tracks.first().artworkUri, 96.dp, icon = Icons.Filled.Album)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(details.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(details.creator, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            details.date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                "Fuente: Internet Archive · " + (licenseLabel(details.licenseUrl) ?: "Colección con permiso de redistribución"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                item {
                    PlayShuffleBar(details.tracks.size, onPlay = { container.trackActions.play(details.tracks) },
                        onShuffle = { container.trackActions.shuffle(details.tracks) })
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { addAll = true },
                            label = { Text("Agregar todo a una lista") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        )
                        AssistChip(
                            onClick = {
                                details.tracks.forEach { container.trackActions.download(it) }
                                Toast.makeText(context, "${details.tracks.size} descargas en cola", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Descargar todo") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                        )
                    }
                }
                itemsIndexed(details.tracks, key = { _, t -> t.key }) { i, t ->
                    TrackRow(t, onClick = { container.trackActions.play(details.tracks, i) }, showArtwork = false,
                        leading = {
                            Text(
                                if (t.trackNumber > 0) t.trackNumber.toString() else "${i + 1}",
                                modifier = Modifier.width(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        })
                }
            }
        }
    }
    if (addAll && details != null) {
        AddToPlaylistDialog(details.tracks) { addAll = false }
    }
}
