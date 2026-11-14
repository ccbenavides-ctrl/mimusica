package app.mimusica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mimusica.domain.stats.ListeningStats
import app.mimusica.ui.components.Artwork
import app.mimusica.ui.components.EmptyState
import app.mimusica.ui.components.LoadingState
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import app.mimusica.ui.components.TrackRow
import java.util.Calendar

private enum class StatsPeriod(val label: String) { MONTH("Este mes"), YEAR("Este año"), ALL("Siempre") }

private fun periodStart(period: StatsPeriod): Long {
    if (period == StatsPeriod.ALL) return 0L
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    if (period == StatsPeriod.YEAR) cal.set(Calendar.MONTH, Calendar.JANUARY)
    return cal.timeInMillis
}

/** "Tu resumen": lo que más has escuchado en el mes, el año o desde siempre. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    LaunchedEffect(period) {
        stats = null
        stats = container.library.stats(periodStart(period))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Tu resumen", onBack) },
    ) { padding ->
        val s = stats
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
            }
            when {
                s == null -> item { LoadingState(message = "Calculando…") }
                s.isEmpty -> item {
                    EmptyState(
                        Icons.Filled.BarChart,
                        "Todavía no hay datos",
                        "Aquí verás tus canciones y artistas más escuchados. Cuenta cada canción que suene más de 10 segundos.",
                    )
                }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Tiempo", formatListening(s.totalMinutes), Modifier.weight(1f))
                            StatCard("Reproducciones", s.totalPlays.toString(), Modifier.weight(1f))
                            StatCard("Días", s.activeDays.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Text(
                            "${s.distinctTracks} canciones distintas de ${s.distinctArtists} artistas" +
                                (s.peakHour?.let { " · sueles escuchar hacia las ${formatHour(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item { SectionHeader("Cuándo escuchas") }
                    item { HourChart(s.byHour) }
                    if (s.topArtists.isNotEmpty()) {
                        item { SectionHeader("Tus artistas") }
                        itemsIndexed(s.topArtists) { i, a ->
                            ListItem(
                                headlineContent = { Text("${i + 1}. ${a.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${a.plays} reproducciones · ${formatListening(a.minutes)}") },
                                leadingContent = { Artwork(a.artworkUri, 48.dp, icon = Icons.Filled.Person) },
                            )
                        }
                    }
                    item { SectionHeader("Tus canciones") }
                    itemsIndexed(s.topTracks) { i, t ->
                        TrackRow(
                            t.track,
                            onClick = { container.trackActions.play(s.topTracks.map { it.track }, i) },
                            subtitle = "${t.track.artist} · ${t.plays} veces",
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun formatListening(minutes: Long): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

private fun formatHour(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12 ${if (hour < 12) "a. m." else "p. m."}"
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Barras con las reproducciones por hora del día (0 a 23). */
@Composable
private fun HourChart(byHour: List<Int>) {
    val max = (byHour.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            byHour.forEach { count ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((count.toFloat() / max).coerceIn(0.03f, 1f))
                        .background(
                            if (count == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12 a. m.", "6 a. m.", "12 p. m.", "6 p. m.", "11 p. m.").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
