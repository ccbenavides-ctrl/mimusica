package app.mimusica.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

enum class ThemeMode(val label: String) {
    SYSTEM("Según el sistema"),
    LIGHT("Claro"),
    DARK("Oscuro");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class AppSettings(
    val jamendoClientId: String = "",
    val wifiOnlyDownloads: Boolean = false,
    val minTrackDurationSec: Int = DEFAULT_MIN_DURATION_SEC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Fundido de salida/entrada entre canciones, en segundos (0 = desactivado). */
    val fadeSec: Int = 0,
    /** Iguala el volumen usando las etiquetas ReplayGain de los archivos. */
    val normalizeVolume: Boolean = false,
    val playbackSpeed: Float = 1f,
    val eqEnabled: Boolean = false,
    /** Índice del preajuste del ecualizador del sistema (-1 = plano). */
    val eqPreset: Int = -1,
    /** Refuerzo de graves, 0–1000. */
    val bassBoost: Int = 0,
    /** Tamaño máximo de la caché de streaming, en MB. */
    val streamCacheMb: Int = DEFAULT_CACHE_MB,
    val lyricsEnabled: Boolean = true,
    /** Buscar en internet las carátulas que falten. */
    val autoArtwork: Boolean = true,
    /** Transmitir a Chromecast (experimental). */
    val castEnabled: Boolean = false,
    /** Código de país para la radio en vivo. */
    val radioCountry: String = "CO",
) {
    val hasJamendoKey: Boolean get() = jamendoClientId.isNotBlank()

    companion object {
        const val DEFAULT_MIN_DURATION_SEC = 30
        const val DEFAULT_CACHE_MB = 512
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ajustes")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val jamendo = stringPreferencesKey("jamendo_client_id")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val minDuration = intPreferencesKey("min_duration_sec")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fade = intPreferencesKey("fade_sec")
        val normalize = booleanPreferencesKey("normalize_volume")
        val speed = floatPreferencesKey("playback_speed")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = intPreferencesKey("eq_preset")
        val bass = intPreferencesKey("bass_boost")
        val cacheMb = intPreferencesKey("stream_cache_mb")
        val lyrics = booleanPreferencesKey("lyrics_enabled")
        val autoArt = booleanPreferencesKey("auto_artwork")
        val cast = booleanPreferencesKey("cast_enabled")
        val radioCountry = stringPreferencesKey("radio_country")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            jamendoClientId = p[Keys.jamendo].orEmpty(),
            wifiOnlyDownloads = p[Keys.wifiOnly] ?: false,
            minTrackDurationSec = p[Keys.minDuration] ?: AppSettings.DEFAULT_MIN_DURATION_SEC,
            themeMode = ThemeMode.fromName(p[Keys.theme]),
            dynamicColor = p[Keys.dynamic] ?: true,
            fadeSec = p[Keys.fade] ?: 0,
            normalizeVolume = p[Keys.normalize] ?: false,
            playbackSpeed = p[Keys.speed] ?: 1f,
            eqEnabled = p[Keys.eqEnabled] ?: false,
            eqPreset = p[Keys.eqPreset] ?: -1,
            bassBoost = p[Keys.bass] ?: 0,
            streamCacheMb = p[Keys.cacheMb] ?: AppSettings.DEFAULT_CACHE_MB,
            lyricsEnabled = p[Keys.lyrics] ?: true,
            autoArtwork = p[Keys.autoArt] ?: true,
            castEnabled = p[Keys.cast] ?: false,
            radioCountry = p[Keys.radioCountry] ?: "CO",
        )
    }

    /** Lectura síncrona (solo al crear el servicio de reproducción). */
    fun current(): AppSettings = runBlocking { settings.first() }

    suspend fun setJamendoClientId(value: String) {
        context.settingsDataStore.edit { it[Keys.jamendo] = value.trim() }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.wifiOnly] = value }
    }

    suspend fun setMinDurationSec(value: Int) {
        context.settingsDataStore.edit { it[Keys.minDuration] = value.coerceIn(0, 600) }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamic] = value }
    }

    suspend fun setFadeSec(value: Int) {
        context.settingsDataStore.edit { it[Keys.fade] = value.coerceIn(0, 12) }
    }

    suspend fun setNormalizeVolume(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.normalize] = value }
    }

    suspend fun setPlaybackSpeed(value: Float) {
        context.settingsDataStore.edit { it[Keys.speed] = value.coerceIn(0.5f, 2f) }
    }

    suspend fun setEqEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.eqEnabled] = value }
    }

    suspend fun setEqPreset(value: Int) {
        context.settingsDataStore.edit { it[Keys.eqPreset] = value }
    }

    suspend fun setBassBoost(value: Int) {
        context.settingsDataStore.edit { it[Keys.bass] = value.coerceIn(0, 1000) }
    }

    suspend fun setStreamCacheMb(value: Int) {
        context.settingsDataStore.edit { it[Keys.cacheMb] = value.coerceIn(0, 4096) }
    }

    suspend fun setLyricsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.lyrics] = value }
    }

    suspend fun setAutoArtwork(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoArt] = value }
    }

    suspend fun setCastEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.cast] = value }
    }

    suspend fun setRadioCountry(value: String) {
        context.settingsDataStore.edit { it[Keys.radioCountry] = value.trim().uppercase().take(2) }
    }
}
