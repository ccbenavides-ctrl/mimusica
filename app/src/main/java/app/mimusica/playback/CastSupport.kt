package app.mimusica.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Chromecast (experimental): envuelve el reproductor local en un [CastPlayer] que pasa la música al
 * dispositivo Cast cuando el usuario lo elige. Solo se activa si está encendido en Ajustes; cualquier
 * fallo (por ejemplo, un teléfono sin Servicios de Google Play) deja el reproductor local intacto.
 */
@OptIn(UnstableApi::class)
object CastSupport {

    /** True cuando el SDK de Cast quedó inicializado y se puede mostrar el botón de transmitir. */
    @Volatile
    var ready: Boolean = false
        private set

    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        if (ready) return true
        ready = try {
            Cast.getSingletonInstance(context.applicationContext).initialize(CastParams.Builder().build())
            true
        } catch (t: Throwable) {
            false
        }
        return ready
    }

    fun wrapIfEnabled(context: Context, local: ExoPlayer, enabled: Boolean): Player {
        if (!enabled) return local
        return try {
            if (!ensureInitialized(context)) return local
            CastPlayer.Builder(context).setLocalPlayer(local).build()
        } catch (t: Throwable) {
            local
        }
    }
}
