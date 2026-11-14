package app.mimusica.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.mimusica.data.settings.ThemeMode

// Paleta propia (verde azulado + coral), usada cuando no hay color dinámico.
private val Teal = Color(0xFF0E5E6F)
private val TealLight = Color(0xFF7FD3E2)
private val Coral = Color(0xFFFF8A65)
private val CoralDark = Color(0xFFB2452A)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9ECF5),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = CoralDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF5B5D72),
    background = Color(0xFFFAFDFD),
    surface = Color(0xFFFAFDFD),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5A),
    onPrimaryContainer = Color(0xFFB9ECF5),
    secondary = Coral,
    onSecondary = Color(0xFF5E1600),
    secondaryContainer = Color(0xFF7E2C13),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFC3C4DD),
    background = Color(0xFF101415),
    surface = Color(0xFF101415),
)

@Composable
fun MiMusicaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
