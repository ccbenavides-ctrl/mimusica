package app.mimusica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mimusica.AppContainer
import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.mimusica.domain.model.Track
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }

/** Crea un ViewModel con acceso al [AppContainer] (DI manual). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Nombre corto de una licencia Creative Commons a partir de su URL. */
fun licenseLabel(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val u = url.lowercase()
    return when {
        u.contains("publicdomain") -> "Dominio público"
        u.contains("creativecommons.org/licenses/") -> {
            val parts = u.substringAfter("/licenses/").trim('/').split('/')
            "CC " + parts.getOrNull(0).orEmpty().uppercase() + (parts.getOrNull(1)?.let { " $it" } ?: "")
        }
        else -> "Licencia libre"
    }
}

/** True si se deben buscar en internet las carátulas que falten (ajuste del usuario). */
val LocalAutoArtwork = staticCompositionLocalOf { true }

/**
 * Carátula con ícono de respaldo. Si se pasan [artist] y [album] y la carátula no existe o no carga,
 * se busca una en internet (una sola vez por álbum).
 */
@Composable
fun Artwork(
    model: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.MusicNote,
    artist: String? = null,
    album: String? = null,
) {
    val container = LocalAppContainer.current
    val autoArtwork = LocalAutoArtwork.current
    val canSearch = autoArtwork && !artist.isNullOrBlank() && !album.isNullOrBlank()
    var failed by remember(model) { mutableStateOf(model.isNullOrBlank()) }
    var fallback by remember(model, artist, album) {
        mutableStateOf(if (canSearch) container.artwork.cached(artist!!, album!!) else null)
    }
    if (failed && fallback == null && canSearch) {
        LaunchedEffect(artist, album) { fallback = container.artwork.find(artist!!, album!!) }
    }
    val shown = if (!failed) model else fallback
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 100.dp) 16.dp else 8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2),
        )
        if (!shown.isNullOrBlank()) {
            AsyncImage(
                model = shown,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { if (!failed) failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Carátula de una pista: solo busca en internet las de canciones del teléfono. */
@Composable
fun TrackArtwork(track: Track, size: Dp, modifier: Modifier = Modifier) {
    val local = !track.isOnline
    Artwork(
        model = track.artworkUri,
        size = size,
        modifier = modifier,
        icon = when {
            track.isLive -> Icons.Filled.Radio
            track.isPodcast -> Icons.Filled.Podcasts
            else -> Icons.Filled.MusicNote
        },
        artist = if (local) track.artist else null,
        album = if (local) track.album else null,
    )
}

/**
 * Asa para reordenar arrastrando. Funciona en listas sin `key`: el asa se queda en su fila y lleva la
 * cuenta de a qué posición se ha movido el elemento. [onDragging] informa la posición actual (-1 al soltar).
 */
@Composable
fun DragHandle(
    index: Int,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onDragging: (Int) -> Unit = {},
) {
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(count)
    val move by rememberUpdatedState(onMove)
    val dragging by rememberUpdatedState(onDragging)
    val stepPx = with(LocalDensity.current) { 60.dp.toPx() }
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Arrastrar para reordenar",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .pointerInput(Unit) {
                var position = -1
                var acc = 0f
                detectDragGestures(
                    onDragStart = {
                        position = currentIndex
                        acc = 0f
                        dragging(position)
                    },
                    onDragEnd = { position = -1; dragging(-1) },
                    onDragCancel = { position = -1; dragging(-1) },
                ) { change, amount ->
                    change.consume()
                    acc += amount.y
                    while (acc > stepPx && position >= 0 && position < currentCount - 1) {
                        move(position, position + 1)
                        position++
                        acc -= stepPx
                        dragging(position)
                    }
                    while (acc < -stepPx && position > 0) {
                        move(position, position - 1)
                        position--
                        acc += stepPx
                        dragging(position)
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

/** Color dominante (oscurecido) de una carátula, para el fondo de "Reproduciendo". */
@Composable
fun rememberArtworkColor(model: String?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        color = if (model.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(model).size(48).allowHardware(false).build()
                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap = (result as? SuccessResult)?.image?.toBitmap(48, 48) ?: return@runCatching null
                dominantColor(bitmap)
            }.getOrNull()
        }
    }
    return color
}

private fun dominantColor(bitmap: Bitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    val hsv = FloatArray(3)
    for (p in pixels) {
        android.graphics.Color.colorToHSV(p, hsv)
        // Los píxeles con color pesan más que los grises, blancos y negros.
        val weight = (1 + (hsv[1] * 6).toInt()).toLong()
        r += android.graphics.Color.red(p) * weight
        g += android.graphics.Color.green(p) * weight
        b += android.graphics.Color.blue(p) * weight
        n += weight
    }
    if (n == 0L) return null
    android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
    hsv[1] = hsv[1].coerceIn(0.25f, 0.75f)
    hsv[2] = hsv[2].coerceIn(0.30f, 0.50f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = "Cargando…") {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        icon = Icons.Filled.CloudOff,
        title = "Algo salió mal",
        message = message,
        modifier = modifier,
        actionLabel = if (onRetry != null) "Reintentar" else null,
        onAction = onRetry,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
