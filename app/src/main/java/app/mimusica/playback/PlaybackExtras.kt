package app.mimusica.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Estado del temporizador de apagado. */
data class SleepState(
    /** Instante (reloj [SystemClock.elapsedRealtime]) en el que se pausa la música; null = sin temporizador. */
    val endAtElapsedMs: Long? = null,
    /** Pausar cuando termine la canción actual. */
    val endOfTrack: Boolean = false,
) {
    val active: Boolean get() = endAtElapsedMs != null || endOfTrack
    fun remainingMs(): Long = endAtElapsedMs?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0) } ?: 0L
}

/** Temporizador de apagado: la UI lo programa y el servicio de reproducción lo cumple. */
class SleepTimer {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = SleepState(endAtElapsedMs = SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun atEndOfTrack() {
        _state.value = SleepState(endOfTrack = true)
    }

    fun cancel() {
        _state.value = SleepState()
    }
}

data class EffectsState(
    val available: Boolean = false,
    val presets: List<String> = emptyList(),
    val bassSupported: Boolean = false,
)

/**
 * Ecualizador, refuerzo de graves y ganancia del sistema, enganchados a la sesión de audio del reproductor.
 * Vive en el mismo proceso que el servicio, así que la pantalla de Sonido lo consulta directamente.
 */
class AudioEffectsController {
    private val _state = MutableStateFlow(EffectsState())
    val state: StateFlow<EffectsState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    private var wantEnabled = false
    private var wantPreset = -1
    private var wantBass = 0
    private var wantGainMb = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        val eq = equalizer
        val presets = if (eq == null) emptyList() else runCatching {
            (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
        }.getOrDefault(emptyList())
        _state.value = EffectsState(
            available = eq != null,
            presets = presets,
            bassSupported = runCatching { bass?.strengthSupported == true }.getOrDefault(false),
        )
        applyNow()
    }

    fun configure(enabled: Boolean, preset: Int, bassStrength: Int) {
        wantEnabled = enabled
        wantPreset = preset
        wantBass = bassStrength
        applyNow()
    }

    /** Ganancia extra en milibelios (para ReplayGain positivo); 0 la desactiva. */
    fun setExtraGainMb(gainMb: Int) {
        wantGainMb = gainMb
        runCatching {
            loudness?.setTargetGain(gainMb)
            loudness?.setEnabled(gainMb > 0)
        }
    }

    private fun applyNow() {
        runCatching {
            val eq = equalizer ?: return@runCatching
            if (wantEnabled) {
                if (wantPreset in 0 until eq.numberOfPresets.toInt()) {
                    eq.usePreset(wantPreset.toShort())
                } else {
                    for (b in 0 until eq.numberOfBands.toInt()) eq.setBandLevel(b.toShort(), 0)
                }
            }
            eq.setEnabled(wantEnabled)
        }
        runCatching {
            val b = bass ?: return@runCatching
            val on = wantEnabled && wantBass > 0
            if (on) b.setStrength(wantBass.coerceIn(0, 1000).toShort())
            b.setEnabled(on)
        }
        setExtraGainMb(wantGainMb)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bass = null
        loudness = null
        sessionId = 0
        _state.value = EffectsState()
    }
}

/** Puente en memoria entre el servicio de reproducción y la interfaz (mismo proceso). */
class PlaybackExtras {
    val sleepTimer = SleepTimer()
    val effects = AudioEffectsController()

    /** Título que anuncia la emisora en vivo (metadatos ICY), si lo envía. */
    val liveTitle = MutableStateFlow<String?>(null)

    /** True si el reproductor se creó con soporte de Chromecast. */
    val castActive = MutableStateFlow(false)
}

/** Caché en disco del streaming: lo ya escuchado no se vuelve a descargar. */
@OptIn(UnstableApi::class)
object PlaybackCache {
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context, maxBytes: Long): SimpleCache {
        cache?.let { return it }
        val dir = File(context.applicationContext.cacheDir, "streaming")
        val created = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(maxBytes), StandaloneDatabaseProvider(context.applicationContext))
        cache = created
        return created
    }

    fun usedBytes(context: Context): Long {
        cache?.let { return runCatching { it.cacheSpace }.getOrDefault(0L) }
        val dir = File(context.applicationContext.cacheDir, "streaming")
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Vacía la caché. Devuelve false si no se pudo. */
    @Synchronized
    fun clear(context: Context): Boolean = runCatching {
        val c = cache
        if (c != null) {
            for (key in c.keys.toList()) c.removeResource(key)
        } else {
            File(context.applicationContext.cacheDir, "streaming").deleteRecursively()
        }
        true
    }.getOrDefault(false)
}
