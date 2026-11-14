package app.mimusica.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.settings.AppSettings
import app.mimusica.data.settings.ThemeMode
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenImportHelp: () -> Unit, onOpenSound: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var clientId by remember { mutableStateOf("") }
    var clientIdLoaded by remember { mutableStateOf(false) }
    var minDuration by remember { mutableFloatStateOf(settings.minTrackDurationSec.toFloat()) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                container.backup.export(uri)
                Toast.makeText(context, "Copia de seguridad guardada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo guardar la copia: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val r = container.backup.restore(uri)
                val missing = if (r.missingTracks > 0) " (${r.missingTracks} canciones no están en este teléfono)" else ""
                Toast.makeText(
                    context,
                    "Restaurado: ${r.playlists} listas, ${r.favorites} favoritos, ${r.podcasts} podcasts$missing",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "No se pudo restaurar la copia", Toast.LENGTH_LONG).show()
            }
        }
    }
    var radioCountry by remember { mutableStateOf("") }
    LaunchedEffect(settings.radioCountry) { radioCountry = settings.radioCountry }

    LaunchedEffect(settings.jamendoClientId, settings.minTrackDurationSec) {
        if (!clientIdLoaded) {
            clientId = settings.jamendoClientId
        }
        minDuration = settings.minTrackDurationSec.toFloat()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Ajustes", onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Jamendo")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Pega aquí tu client_id gratuito de Jamendo. Lo obtienes creando una aplicación en devportal.jamendo.com.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it; clientIdLoaded = true },
                    label = { Text("client_id de Jamendo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        scope.launch {
                            container.settings.setJamendoClientId(clientId)
                            clientIdLoaded = false
                            Toast.makeText(context, if (clientId.isBlank()) "Jamendo desactivado" else "client_id guardado", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Guardar") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://devportal.jamendo.com/")))
                    }) { Text("Abrir devportal.jamendo.com") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Reproducción")
            ListItem(
                headlineContent = { Text("Sonido y reproducción") },
                supportingContent = { Text("Ecualizador, igualar volumen, fundido entre canciones y caché") },
                modifier = Modifier.clickable(onClick = onOpenSound),
            )
            ListItem(
                headlineContent = { Text("Mostrar letras") },
                supportingContent = { Text("Busca la letra de la canción en LRCLIB (gratuito)") },
                trailingContent = {
                    Switch(checked = settings.lyricsEnabled, onCheckedChange = { v -> scope.launch { container.settings.setLyricsEnabled(v) } })
                },
            )
            ListItem(
                headlineContent = { Text("Buscar carátulas que falten") },
                supportingContent = { Text("Las busca en MusicBrainz cuando un álbum no trae carátula") },
                trailingContent = {
                    Switch(checked = settings.autoArtwork, onCheckedChange = { v -> scope.launch { container.settings.setAutoArtwork(v) } })
                },
            )
            ListItem(
                headlineContent = { Text("Chromecast (experimental)") },
                supportingContent = { Text("Agrega el botón de transmitir en «Reproduciendo». Solo música por internet; se aplica al reabrir la app") },
                trailingContent = {
                    Switch(checked = settings.castEnabled, onCheckedChange = { v -> scope.launch { container.settings.setCastEnabled(v) } })
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = radioCountry,
                    onValueChange = { v ->
                        radioCountry = v.uppercase().filter { it.isLetter() }.take(2)
                        if (radioCountry.length == 2) scope.launch { container.settings.setRadioCountry(radioCountry) }
                    },
                    label = { Text("País de la radio en vivo (código de 2 letras)") },
                    supportingText = { Text("CO = Colombia, MX = México, ES = España, US = Estados Unidos…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Copia de seguridad")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Guarda tus listas, favoritos, historial y podcasts en un archivo (no incluye la música).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { exportBackup.launch("mimusica-copia.json") }) { Text("Guardar copia") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restaurar copia") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Descargas")
            ListItem(
                headlineContent = { Text("Descargar solo con Wi-Fi") },
                supportingContent = { Text("Las descargas esperan hasta que haya Wi-Fi") },
                trailingContent = {
                    Switch(checked = settings.wifiOnlyDownloads, onCheckedChange = { v -> scope.launch { container.settings.setWifiOnly(v) } })
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Biblioteca")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Duración mínima de las canciones: ${minDuration.toInt()} s")
                Text(
                    "Oculta audios cortos (notas de voz, tonos). Valor por defecto: ${AppSettings.DEFAULT_MIN_DURATION_SEC} s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = minDuration,
                    onValueChange = { minDuration = it },
                    onValueChangeFinished = { scope.launch { container.settings.setMinDurationSec(minDuration.toInt()) } },
                    valueRange = 0f..120f,
                    steps = 23,
                )
                OutlinedButton(onClick = { scope.launch { container.localMusic.refresh() } }) { Text("Volver a escanear la música") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Apariencia")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = null)
                    Text(mode.label, Modifier.padding(start = 12.dp))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Colores del fondo de pantalla") },
                    supportingContent = { Text("Usa los colores dinámicos de Android (Material You)") },
                    trailingContent = {
                        Switch(checked = settings.dynamicColor, onCheckedChange = { v -> scope.launch { container.settings.setDynamicColor(v) } })
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Acerca de")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "MiMúsica reproduce la música guardada en tu teléfono y música gratuita y legal de " +
                        "Internet Archive y Jamendo (licencias Creative Commons, dominio público o permiso del artista), " +
                        "además de radio en vivo (Radio Browser) y podcasts públicos.\n\n" +
                        "No accede a las descargas de Spotify ni descarga audio de Spotify, YouTube u otras fuentes " +
                        "sin licencia. De Spotify solo se importan los nombres de las canciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenImportHelp) { Text("¿Cómo importar mis playlists de Spotify?") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
