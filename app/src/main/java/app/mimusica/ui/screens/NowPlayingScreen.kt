package app.mimusica.ui.screens

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.cast.MediaRouteButton
import androidx.media3.common.util.UnstableApi
import app.mimusica.data.remote.Lyrics
import app.mimusica.data.remote.RemoteApiException
import app.mimusica.data.settings.AppSettings
import app.mimusica.domain.lyrics.LrcParser
import app.mimusica.domain.model.Track
import app.mimusica.playback.CastSupport
import app.mimusica.playback.PlayerState
import app.mimusica.playback.RepeatSetting
import app.mimusica.playback.SleepState
import app.mimusica.ui.components.DragHandle
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.TrackArtwork
import app.mimusica.ui.components.TrackMenuButton
import app.mimusica.ui.components.formatDuration
import app.mimusica.ui.components.licenseLabel
import app.mimusica.ui.components.rememberArtworkColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Mini-reproductor fijo sobre la barra inferior. Deslizar a los lados cambia de canción; hacia arriba lo abre. */
@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val container = LocalAppContainer.current
    val track = state.current ?: return
    val liveTitle by container.extras.liveTitle.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying, track.key, state.durationMs) {
        while (true) {
            val d = state.durationMs
            progress = if (d > 0 && !track.isLive) (container.player.currentPositionMs().toFloat() / d).coerceIn(0f, 1f) else 0f
            if (!state.isPlaying) break
            delay(500)
        }
    }
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        when {
                            abs(dx) > abs(dy) && dx < -threshold -> container.player.next()
                            abs(dx) > abs(dy) && dx > threshold -> container.player.previous()
                            abs(dy) > abs(dx) && dy < -threshold / 2 -> open()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dx += amount.x
                    dy += amount.y
                }
            }
            .clickable(onClick = onOpen),
    ) {
        Column {
            if (state.isBuffering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                TrackArtwork(track, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.error ?: (if (track.isLive) liveTitle else null) ?: track.artist,
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
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val player = container.player
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val sleep by container.extras.sleepTimer.state.collectAsStateWithLifecycle()
    val liveTitle by container.extras.liveTitle.collectAsStateWithLifecycle()
    val castActive by container.extras.castActive.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val track = state.current

    // Fondo con el color de la carátula.
    val artColor = rememberArtworkColor(track?.artworkUri)
    val background = MaterialTheme.colorScheme.background
    val topColor by animateColorAsState(artColor ?: MaterialTheme.colorScheme.surfaceVariant, tween(600), label = "fondo")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(topColor, background)))) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopAppBar(
                title = { Text(if (track?.isLive == true) "En vivo" else "Reproduciendo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (castActive && CastSupport.ready) CastButton()
                    IconButton(onClick = { showSleep = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Temporizador de apagado",
                            tint = if (sleep.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
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

            // Posición de reproducción (se consulta varias veces por segundo mientras suena).
            var positionMs by remember { mutableLongStateOf(0L) }
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(state.isPlaying, track.key, showLyrics) {
                while (true) {
                    if (!dragging) positionMs = player.currentPositionMs()
                    if (!state.isPlaying) break
                    delay(if (showLyrics) 200 else 500)
                }
            }

            val lyricsAvailable = settings.lyricsEnabled && !track.isLive && !track.isPodcast
            if (showLyrics && lyricsAvailable) {
                LyricsPanel(
                    track = track,
                    positionMs = positionMs,
                    onSeek = { player.seekTo(it); positionMs = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
                        contentKey = { it.key },
                        label = "caratula",
                    ) { shown ->
                        TrackArtwork(shown, size = 280.dp, modifier = Modifier.widthIn(max = 320.dp).aspectRatio(1f))
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (track.isLive) liveTitle ?: track.artist
                else listOf(track.artist, track.album).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

                Spacer(Modifier.height(8.dp))
                if (track.isLive) {
                    if (state.isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 22.dp))
                    else Spacer(Modifier.height(48.dp))
                } else {
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
                        if (state.isBuffering) Text("Cargando…", style = MaterialTheme.typography.labelSmall)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (track.isPodcast) {
                        IconButton(onClick = { player.seekBy(-10_000) }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Retroceder 10 segundos", modifier = Modifier.size(30.dp))
                        }
                    } else {
                        IconButton(onClick = { player.toggleShuffle() }) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = if (state.shuffle) "Aleatorio activado" else "Aleatorio desactivado",
                                tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    if (track.isPodcast) {
                        IconButton(onClick = { player.seekBy(30_000) }) {
                            Icon(Icons.Filled.Forward30, contentDescription = "Avanzar 30 segundos", modifier = Modifier.size(30.dp))
                        }
                    } else {
                        IconButton(onClick = { player.cycleRepeat() }) {
                            Icon(
                                if (state.repeat == RepeatSetting.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = state.repeat.label,
                                tint = if (state.repeat != RepeatSetting.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                ) {
                    val fav = track.key in favorites
                    IconButton(onClick = { container.trackActions.toggleFavorite(track) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (fav) "Quitar de favoritos" else "Agregar a favoritos",
                            tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lyricsAvailable) {
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(
                                Icons.Filled.Lyrics,
                                contentDescription = if (showLyrics) "Ocultar letra" else "Ver letra",
                                tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!track.isLive) {
                        TextButton(onClick = { showSpeed = true }) { Text(speedLabel(state.speed)) }
                    }
                    TextButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                        Text("  Cola (${state.queue.size})")
                    }
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(state)
        }
    }
    if (showSleep) {
        SleepTimerDialog(sleep, onDismiss = { showSleep = false })
    }
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Velocidad") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        ListItem(
                            headlineContent = { Text(if (speed == 1f) "Normal" else speedLabel(speed)) },
                            trailingContent = { if (abs(state.speed - speed) < 0.01f) Text("✓") },
                            modifier = Modifier.clickable {
                                player.setSpeed(speed)
                                scope.launch { container.settings.setPlaybackSpeed(speed) }
                                showSpeed = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Cerrar") } },
        )
    }
}

private fun speedLabel(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "${text}x"
}

@OptIn(UnstableApi::class)
@Composable
private fun CastButton() {
    MediaRouteButton()
}

@Composable
private fun SleepTimerDialog(sleep: SleepState, onDismiss: () -> Unit) {
    val timer = LocalAppContainer.current.extras.sleepTimer
    var remaining by remember { mutableLongStateOf(sleep.remainingMs()) }
    LaunchedEffect(sleep) {
        while (sleep.endAtElapsedMs != null) {
            remaining = sleep.remainingMs()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador de apagado") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    when {
                        sleep.endOfTrack -> "La música se pausará al terminar esta canción."
                        sleep.endAtElapsedMs != null -> "La música se pausará en ${formatDuration(remaining)}."
                        else -> "Pausa la música automáticamente después de un rato."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                listOf(10, 15, 30, 45, 60, 90).forEach { minutes ->
                    ListItem(
                        headlineContent = { Text("$minutes minutos") },
                        modifier = Modifier.clickable { timer.start(minutes); onDismiss() },
                    )
                }
                ListItem(
                    headlineContent = { Text("Al terminar la canción") },
                    modifier = Modifier.clickable { timer.atEndOfTrack(); onDismiss() },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = {
            if (sleep.active) TextButton(onClick = { timer.cancel(); onDismiss() }) { Text("Quitar temporizador") }
        },
    )
}

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object None : LyricsUi
    data class Error(val message: String) : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

/** Letra de la canción: sincronizada (resalta la línea que suena; tocar una línea salta ahí) o en texto. */
@Composable
private fun LyricsPanel(track: Track, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    var ui by remember(track.key) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    var attempt by remember(track.key) { mutableIntStateOf(0) }
    LaunchedEffect(track.key, attempt) {
        ui = LyricsUi.Loading
        ui = try {
            container.lyrics.lyricsFor(track, refresh = attempt > 0)?.let { LyricsUi.Ready(it) } ?: LyricsUi.None
        } catch (e: RemoteApiException) {
            LyricsUi.Error(e.message ?: "No se pudo cargar la letra.")
        }
    }
    Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        when (val current = ui) {
            LyricsUi.Loading -> CircularProgressIndicator()
            LyricsUi.None -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se encontró la letra de esta canción.", textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }) { Text("Buscar de nuevo") }
            }
            is LyricsUi.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Reintentar") }
            }
            is LyricsUi.Ready -> {
                val lines = current.lyrics.synced
                if (lines.isNotEmpty()) {
                    val active = LrcParser.activeIndex(lines, positionMs + 250)
                    val listState = rememberLazyListState()
                    LaunchedEffect(active) {
                        if (active >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem((active - 3).coerceAtLeast(0))
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(lines) { i, line ->
                            Text(
                                line.text.ifBlank { "♪" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == active) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip8()
                                    .clickable { onSeek(line.timeMs) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        current.lyrics.plain.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

private fun Modifier.clip8(): Modifier = this.then(Modifier.background(Color.Transparent, RoundedCornerShape(8.dp)))

/** Cola editable: tocar para saltar, arrastrar el asa para reordenar, X para quitar. */
@Composable
private fun QueueSheet(state: PlayerState) {
    val player = LocalAppContainer.current.player
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    var draggingIndex by remember { mutableIntStateOf(-1) }
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
                        .height(60.dp)
                        .background(if (index == draggingIndex) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { player.playQueueItem(index) }
                        .padding(start = 4.dp, end = 4.dp),
                ) {
                    DragHandle(
                        index = index,
                        count = state.queue.size,
                        onMove = { from, to -> player.moveQueueItem(from, to) },
                        onDragging = { draggingIndex = it },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
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
