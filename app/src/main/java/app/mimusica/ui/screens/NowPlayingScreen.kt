package app.mimusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.playback.PlayerState
import app.mimusica.playback.RepeatSetting
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackMenuButton
import app.mimusica.ui.components.formatDuration
import app.mimusica.ui.components.licenseLabel
import kotlinx.coroutines.delay

/** Mini-reproductor fijo sobre la barra inferior. */
@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val container = LocalAppContainer.current
    val track = state.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying, track.key, state.durationMs) {
        while (true) {
            val d = state.durationMs
            progress = if (d > 0) (container.player.currentPositionMs().toFloat() / d).coerceIn(0f, 1f) else 0f
            if (!state.isPlaying) break
            delay(500)
        }
    }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Artwork(track.artworkUri, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.error ?: track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { container.player.togglePlayPause() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    )
                }
                IconButton(onClick = { container.player.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                }
            }
        }
    }
}

/** Pantalla "Reproduciendo". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val player = container.player
    val state by player.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    val track = state.current

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(
            title = { Text("Reproduciendo") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            },
            actions = {
                if (track != null) TrackMenuButton(track)
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        if (track == null) {
            EmptyState(
                icon = Icons.Filled.MusicNote,
                title = "Nada sonando",
                message = "Elige una canción de tu biblioteca, de Descubrir o de tus listas.",
            )
            return@Column
        }

        // Posición de reproducción (se consulta cada medio segundo mientras suena).
        var positionMs by remember { mutableLongStateOf(0L) }
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(state.isPlaying, track.key) {
            while (true) {
                if (!dragging) positionMs = player.currentPositionMs()
                if (!state.isPlaying) break
                delay(500)
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Artwork(
                track.artworkUri,
                size = 280.dp,
                modifier = Modifier.widthIn(max = 320.dp).aspectRatio(1f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (track.isOnline) {
                Text(
                    listOfNotNull(track.source.label, licenseLabel(track.license)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = { player.clearError(); player.next() }) { Text("Saltar a la siguiente") }
            }

            Spacer(Modifier.height(16.dp))
            val duration = state.durationMs.takeIf { it > 0 } ?: track.durationMs
            Slider(
                value = if (dragging) dragValue else if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    if (duration > 0) {
                        val target = (dragValue * duration).toLong()
                        player.seekTo(target)
                        positionMs = target
                    }
                    dragging = false
                },
                enabled = duration > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(if (dragging) (dragValue * duration).toLong() else positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (state.shuffle) "Aleatorio activado" else "Aleatorio desactivado",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (state.repeat == RepeatSetting.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = state.repeat.label,
                        tint = if (state.repeat != RepeatSetting.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                val fav = track.key in favorites
                IconButton(onClick = { container.trackActions.toggleFavorite(track) }) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (fav) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isBuffering) Text("Cargando…", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                    Text("  Cola (${state.queue.size})")
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(state)
        }
    }
}

/** Cola editable: tocar para saltar, flechas para reordenar, X para quitar. */
@Composable
private fun QueueSheet(state: PlayerState) {
    val player = LocalAppContainer.current.player
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cola de reproducción", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.queue.isNotEmpty()) TextButton(onClick = { player.clearQueue() }) { Text("Vaciar") }
        }
        if (state.queue.isEmpty()) {
            Text("La cola está vacía.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue) { index, t ->
                val isCurrent = index == state.currentIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { player.playQueueItem(index) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(t.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { player.moveQueueItem(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Subir")
                    }
                    IconButton(onClick = { player.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Bajar")
                    }
                    IconButton(onClick = { player.removeQueueItem(index) }) {
                        Icon(Icons.Filled.Close, "Quitar de la cola")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
