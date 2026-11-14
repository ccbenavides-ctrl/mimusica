package app.mimusica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.mimusica.data.remote.PodcastRepository
import app.mimusica.data.remote.PodcastSummary
import app.mimusica.data.remote.RemoteApiException
import app.mimusica.domain.podcast.PodcastFeed
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.ErrorState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import app.mimusica.ui.components.TrackRow
import app.mimusica.ui.components.formatDuration
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Podcasts: suscripciones, búsqueda en el directorio y alta por enlace RSS. */
@Composable
fun PodcastsScreen(onBack: () -> Unit, onOpenPodcast: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val subscriptions by container.podcasts.subscriptions.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastSummary>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.isBlank()) { results = null; error = null; return }
        // Si pegaron un enlace, se abre directamente como feed RSS.
        if (q.startsWith("http://") || q.startsWith("https://")) { onOpenPodcast(q); return }
        loading = true
        error = null
        scope.launch {
            try {
                results = container.podcasts.search(q)
            } catch (e: RemoteApiException) {
                error = e.message
            }
            loading = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Podcasts", onBack) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it; if (it.isBlank()) { results = null; error = null } },
                    placeholder = "Buscar un podcast o pegar su enlace RSS",
                    onSearch = { search() },
                )
            }
            val found = results
            when {
                loading -> item { LoadingState(message = "Buscando podcasts…") }
                error != null -> item { ErrorState(error!!, onRetry = { search() }) }
                found != null -> {
                    if (found.isEmpty()) {
                        item { EmptyState(Icons.Filled.Podcasts, "Sin resultados", "Prueba con otro nombre o pega el enlace RSS del podcast.") }
                    } else {
                        item { SectionHeader("Resultados") }
                        items(found, key = { it.feedUrl }) { p ->
                            ListItem(
                                headlineContent = { Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(p.author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Artwork(p.artworkUrl, 56.dp, icon = Icons.Filled.Podcasts) },
                                modifier = Modifier.clickable { onOpenPodcast(p.feedUrl) },
                            )
                        }
                    }
                }
                subscriptions.isEmpty() -> item {
                    EmptyState(
                        Icons.Filled.Podcasts,
                        "Aún no sigues ningún podcast",
                        "Busca uno por nombre y toca «Seguir» para tenerlo aquí.",
                    )
                }
                else -> {
                    item { SectionHeader("Podcasts que sigues") }
                    items(subscriptions, key = { it.feedUrl }) { p ->
                        ListItem(
                            headlineContent = { Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(p.author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Artwork(p.artworkUrl, 56.dp, icon = Icons.Filled.Podcasts) },
                            modifier = Modifier.clickable { onOpenPodcast(p.feedUrl) },
                        )
                    }
                }
            }
        }
    }
}

/** Episodios de un podcast. */
@Composable
fun PodcastDetailScreen(feedUrl: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feed by remember(feedUrl) { mutableStateOf<PodcastFeed?>(null) }
    var error by remember(feedUrl) { mutableStateOf<String?>(null) }
    var attempt by remember(feedUrl) { mutableIntStateOf(0) }
    val subscribed by remember(feedUrl) { container.podcasts.isSubscribed(feedUrl) }.collectAsStateWithLifecycle(initialValue = 0)
    val progress by container.podcasts.progress.collectAsStateWithLifecycle(initialValue = emptyList())
    val progressByKey = remember(progress) { progress.associateBy { it.trackKey } }

    LaunchedEffect(feedUrl, attempt) {
        error = null
        try {
            feed = container.podcasts.feed(feedUrl, refresh = attempt > 0)
        } catch (e: RemoteApiException) {
            error = e.message
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(feed?.title ?: "Podcast", onBack) },
    ) { padding ->
        val f = feed
        when {
            error != null -> ErrorState(error!!, Modifier.padding(padding), onRetry = { attempt++ })
            f == null -> LoadingState(Modifier.padding(padding), message = "Cargando episodios…")
            else -> {
                val tracks = remember(f) { f.episodes.map { PodcastRepository.episodeToTrack(it, f) } }
                val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(f.imageUrl, 96.dp, icon = Icons.Filled.Podcasts)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (f.author.isNotBlank()) {
                                    Text(f.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Spacer(Modifier.height(8.dp))
                                if (subscribed > 0) {
                                    OutlinedButton(onClick = {
                                        scope.launch { container.podcasts.unsubscribe(feedUrl) }
                                    }) { Text("Dejar de seguir") }
                                } else {
                                    Button(onClick = {
                                        scope.launch {
                                            container.podcasts.subscribe(feedUrl, f)
                                            Toast.makeText(context, "Ahora sigues este podcast", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("Seguir") }
                                }
                            }
                        }
                    }
                    if (f.description.isNotBlank()) {
                        item {
                            Text(
                                f.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    item { SectionHeader("${tracks.size} episodios") }
                    if (tracks.isEmpty()) {
                        item { EmptyState(Icons.Filled.Podcasts, "Sin episodios", "Este podcast no tiene episodios de audio disponibles.") }
                    }
                    itemsIndexed(tracks, key = { _, t -> t.key }) { i, t ->
                        val episode = f.episodes[i]
                        val saved = progressByKey[t.key]
                        val status = when {
                            saved == null -> null
                            saved.durationMs > 0 && saved.positionMs > saved.durationMs - 30_000 -> "Escuchado"
                            saved.positionMs > 15_000 -> "Vas en ${formatDuration(saved.positionMs)}"
                            else -> null
                        }
                        val details = listOfNotNull(
                            episode.publishedAt.takeIf { it > 0 }?.let { dateFormat.format(Date(it)) },
                            t.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
                            status,
                        ).joinToString(" · ")
                        TrackRow(
                            t,
                            // Un episodio se reproduce solo; los siguientes no se encadenan.
                            onClick = { container.trackActions.play(listOf(t)) },
                            subtitle = details.ifBlank { f.title },
                            showArtwork = false,
                        )
                    }
                }
            }
        }
    }
}
