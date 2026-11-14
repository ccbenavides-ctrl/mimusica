package app.mimusica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import app.mimusica.playback.PlaybackCache
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sonido y reproducción: ecualizador, graves, normalización, fundido y caché de streaming. */
@Composable
fun SoundScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val effects by container.extras.effects.state.collectAsStateWithLifecycle()
    var bass by remember { mutableFloatStateOf(settings.bassBoost.toFloat()) }
    var fade by remember { mutableFloatStateOf(settings.fadeSec.toFloat()) }
    var cacheMb by remember { mutableFloatStateOf(settings.streamCacheMb.toFloat()) }
    var cacheUsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(settings.bassBoost, settings.fadeSec, settings.streamCacheMb) {
        bass = settings.bassBoost.toFloat()
        fade = settings.fadeSec.toFloat()
        cacheMb = settings.streamCacheMb.toFloat()
    }
    LaunchedEffect(Unit) {
        cacheUsed = withContext(Dispatchers.IO) { PlaybackCache.usedBytes(context) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Sonido y reproducción", onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Ecualizador")
            ListItem(
                headlineContent = { Text("Usar ecualizador") },
                supportingContent = {
                    Text(
                        if (effects.available) "Ajusta el sonido con los preajustes de tu teléfono"
                        else "Los preajustes aparecen cuando hay música sonando"
                    )
                },
                trailingContent = {
                    Switch(checked = settings.eqEnabled, onCheckedChange = { v -> scope.launch { container.settings.setEqEnabled(v) } })
                },
            )
            if (settings.eqEnabled) {
                val presets = listOf(-1 to "Plano") + effects.presets.mapIndexed { i, name -> i to name }
                presets.forEach { (index, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { RadioButton(selected = settings.eqPreset == index, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = settings.eqPreset == index,
                            onClick = { scope.launch { container.settings.setEqPreset(index) } },
                            role = Role.RadioButton,
                        ),
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Refuerzo de graves: ${(bass / 10).toInt()} %")
                    Slider(
                        value = bass,
                        onValueChange = { bass = it },
                        onValueChangeFinished = { scope.launch { container.settings.setBassBoost(bass.toInt()) } },
                        valueRange = 0f..1000f,
                        enabled = effects.bassSupported || !effects.available,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Volumen y transiciones")
            ListItem(
                headlineContent = { Text("Igualar el volumen entre canciones") },
                supportingContent = { Text("Usa la etiqueta ReplayGain de cada archivo; las canciones que no la traen suenan sin cambios") },
                trailingContent = {
                    Switch(checked = settings.normalizeVolume, onCheckedChange = { v -> scope.launch { container.settings.setNormalizeVolume(v) } })
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(if (fade.toInt() == 0) "Fundido entre canciones: desactivado" else "Fundido entre canciones: ${fade.toInt()} s")
                Text(
                    "Baja el volumen al final de cada canción y lo sube al empezar la siguiente (las canciones no se superponen).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fade,
                    onValueChange = { fade = it },
                    onValueChangeFinished = { scope.launch { container.settings.setFadeSec(fade.toInt()) } },
                    valueRange = 0f..12f,
                    steps = 11,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionHeader("Caché de streaming")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(if (cacheMb.toInt() == 0) "Caché desactivada" else "Tamaño máximo: ${cacheMb.toInt()} MB")
                Text(
                    "Lo que ya escuchaste por internet se guarda para no gastar datos al repetirlo. " +
                        "En uso: ${cacheUsed / (1024 * 1024)} MB. El cambio de tamaño se aplica al reabrir la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = cacheMb,
                    onValueChange = { cacheMb = it },
                    onValueChangeFinished = { scope.launch { container.settings.setStreamCacheMb(cacheMb.toInt()) } },
                    valueRange = 0f..2048f,
                    steps = 7,
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { PlaybackCache.clear(context) }
                        cacheUsed = withContext(Dispatchers.IO) { PlaybackCache.usedBytes(context) }
                        Toast.makeText(context, if (ok) "Caché vaciada" else "No se pudo vaciar la caché", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Vaciar la caché") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
