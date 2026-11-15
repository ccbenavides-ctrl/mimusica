package app.mimusica.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mimusica.domain.links.ExternalLinks
import app.mimusica.domain.links.ExternalService

/** Abre la búsqueda en el servicio (su app si está instalada; si no, el navegador). */
fun openExternal(context: Context, service: ExternalService) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(service.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una app para abrir ${service.name}", Toast.LENGTH_SHORT).show()
    }
}

/** Fila compacta "Escúchala en… / Cómprala en…" para el buscador. */
@Composable
fun FindElsewhereRow(query: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val services = remember(query) { ExternalLinks.services(query) }
    Column(modifier.fillMaxWidth()) {
        Text(
            "Escúchala en",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            services.filter { it.kind == ExternalService.Kind.LISTEN }.forEach { s ->
                AssistChip(
                    onClick = { openExternal(context, s) },
                    label = { Text(s.name) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(0.dp)) },
                )
            }
        }
        Text(
            "Cómprala para tenerla sin internet",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            services.filter { it.kind == ExternalService.Kind.BUY }.forEach { s ->
                AssistChip(
                    onClick = { openExternal(context, s) },
                    label = { Text(s.name) },
                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                )
            }
        }
    }
}

/** Hoja con las opciones para una canción concreta (menú ⋮ o canciones que faltan de Spotify). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FindElsewhereSheet(title: String, artist: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val query = remember(title, artist) { ExternalLinks.query(title, artist) }
    val services = remember(query) { ExternalLinks.services(query) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!artist.isNullOrBlank()) {
                Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(16.dp))
            Text("Escúchala en", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.filter { it.kind == ExternalService.Kind.LISTEN }.forEach { s ->
                    AssistChip(onClick = { openExternal(context, s); onDismiss() }, label = { Text(s.name) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Cómprala para tenerla sin internet", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.filter { it.kind == ExternalService.Kind.BUY }.forEach { s ->
                    AssistChip(onClick = { openExternal(context, s); onDismiss() }, label = { Text(s.name) },
                        leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Si la compras como MP3 y la descargas al teléfono, S la muestra sola en tu Biblioteca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
