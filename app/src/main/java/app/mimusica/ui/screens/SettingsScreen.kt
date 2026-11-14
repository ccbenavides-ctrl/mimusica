package app.mimusica.ui.screens

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
fun SettingsScreen(onBack: () -> Unit, onOpenImportHelp: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var clientId by remember { mutableStateOf("") }
    var clientIdLoaded by remember { mutableStateOf(false) }
    var minDuration by remember { mutableFloatStateOf(settings.minTrackDurationSec.toFloat()) }

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
                        "Internet Archive y Jamendo (licencias Creative Commons, dominio público o permiso del artista).\n\n" +
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
