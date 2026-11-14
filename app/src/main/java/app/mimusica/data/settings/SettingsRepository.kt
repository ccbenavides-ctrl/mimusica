package app.mimusica.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
) {
    val hasJamendoKey: Boolean get() = jamendoClientId.isNotBlank()

    companion object {
        const val DEFAULT_MIN_DURATION_SEC = 30
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
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            jamendoClientId = p[Keys.jamendo].orEmpty(),
            wifiOnlyDownloads = p[Keys.wifiOnly] ?: false,
            minTrackDurationSec = p[Keys.minDuration] ?: AppSettings.DEFAULT_MIN_DURATION_SEC,
            themeMode = ThemeMode.fromName(p[Keys.theme]),
            dynamicColor = p[Keys.dynamic] ?: true,
        )
    }

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
}
