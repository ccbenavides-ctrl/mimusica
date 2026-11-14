package app.mimusica.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.ui.components.LocalAppContainer

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Permisos que se piden juntos al inicio. Solo el de audio es imprescindible. */
fun startupPermissions(): Array<String> = buildList {
    add(LocalMusicRepository.audioPermission())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}.toTypedArray()

/** Pantalla de explicación del permiso de audio, con manejo de denegación. */
@Composable
fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    var askedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        val granted = result[LocalMusicRepository.audioPermission()] == true
        if (!granted) {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(LocalMusicRepository.audioPermission())
        }
        container.localMusic.onPermissionChanged()
    }

    // Al volver de Ajustes del sistema, se revisa de nuevo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        container.localMusic.onPermissionChanged()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.LibraryMusic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Tu música, en un solo lugar", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Para mostrar las canciones que tienes en el teléfono, MiMúsica necesita permiso para leer " +
                "tus archivos de audio. Solo se leen en el dispositivo: nada se sube a internet.\n\n" +
                "También te pedirá permiso de notificaciones para mostrar los controles de reproducción " +
                "y el progreso de las descargas (opcional).",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Text(
                "El permiso fue denegado. Actívalo en Ajustes del sistema › Aplicaciones › MiMúsica › Permisos › Música y audio.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Abrir ajustes de la app") }
        } else {
            Button(onClick = { launcher.launch(startupPermissions()) }) {
                Text(if (askedOnce) "Volver a intentar" else "Dar permiso")
            }
        }
        if (askedOnce) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { container.localMusic.onPermissionChanged() }) { Text("Ya lo activé") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Mientras tanto puedes usar Descubrir y Buscar para escuchar música online gratuita.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
