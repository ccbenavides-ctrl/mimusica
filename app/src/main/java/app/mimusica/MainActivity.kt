package app.mimusica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.data.settings.AppSettings
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.components.LocalAutoArtwork
import app.mimusica.ui.navigation.MiMusicaRoot
import app.mimusica.ui.theme.MiMusicaTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as MiMusicaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            MiMusicaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalAutoArtwork provides settings.autoArtwork,
                ) {
                    MiMusicaRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Conecta el MediaController al servicio de reproducción mientras la UI está visible.
        container.player.connect()
        // Si el permiso cambió (por ejemplo, desde Ajustes del sistema), se vuelve a escanear.
        val granted = LocalMusicRepository.hasAudioPermission(this)
        if (granted != container.localMusic.state.value.hasPermission) container.localMusic.onPermissionChanged()
    }

    override fun onStop() {
        // La música sigue sonando: el servicio queda en primer plano mientras reproduce.
        container.player.release()
        super.onStop()
    }
}
