# S

Reproductor de música personal para Android. Une en una sola app:

- **Tu música local**: todas las canciones guardadas en el teléfono (MP3, FLAC, M4A, OGG, WAV), por canción, artista y álbum.
- **Música nueva gratuita y legal**: streaming y descarga desde **Internet Archive** (sin clave) y **Jamendo** (clave gratuita).
- **Tus playlists de Spotify como lista de nombres**: se importan (CSV o texto) y se cruzan con lo que ya tienes.

### Novedades de la versión 2.1

- La app ahora se llama **S**, con ícono nuevo.
- **Música de cualquier artista**: en Buscar, en el menú ⋮ de cada canción y en las canciones que faltan de tus
  playlists de Spotify aparecen botones para **escucharla** en Spotify, YouTube Music, YouTube, Deezer o Amazon Music
  (se abre su app con la canción ya buscada) o para **comprarla** en Bandcamp, Amazon u otras tiendas.
  Lo que compres como MP3 y descargues al teléfono aparece solo en la Biblioteca.
- La radio en vivo también busca por género (salsa, vallenato, rock…).

S no descarga ni reproduce música de Spotify, YouTube ni sitios de descarga: solo abre esos servicios.

### Novedades de la versión 2.0

**Reproductor**
- Recuerda la cola y el punto exacto donde ibas al cerrar la app.
- Caché de streaming: lo que ya oíste por internet no se vuelve a descargar (Ajustes › Sonido y reproducción).
- Temporizador de apagado (ícono de luna en «Reproduciendo»), velocidad de reproducción y letras sincronizadas (LRCLIB).
- Ecualizador con los preajustes del teléfono, refuerzo de graves, igualar volumen (ReplayGain) y fundido entre canciones.
- Cola y listas se reordenan arrastrando el asa (≡).

**Contenido**
- Pestaña **Inicio**: sigue escuchando, mezclas automáticas, agregado recientemente y lo más oído.
- **Radio en vivo**: miles de emisoras de todo el mundo (directorio abierto Radio Browser). Corazón = guardar en «Mis emisoras».
- **Podcasts**: búsqueda, suscripción, descarga de episodios y reanudación donde quedaste.
- **Radio de esta canción** (menú ⋮): cola de canciones parecidas de tu biblioteca.
- **Tu resumen**: canciones y artistas más escuchados del mes, del año o de siempre.

**Teléfono y carro**
- **Android Auto**: la app aparece como fuente de música. Al ser una app instalada por fuera de Google Play, hay que
  activar en el teléfono *Android Auto › Ajustes › (tocar 10 veces «Versión») › Ajustes de desarrollador › Fuentes desconocidas*.
- **Widget** para la pantalla de inicio (mantén pulsada la pantalla › Widgets › MiMúsica).
- **Editar datos y carátula** de una canción (menú ⋮). Se guarda dentro de la app; el archivo no se modifica.
  Las carátulas que falten se buscan solas en MusicBrainz / Cover Art Archive.
- **Copia de seguridad** de listas, favoritos, historial y podcasts (Ajustes › Copia de seguridad).
- **Chromecast (experimental)**: se activa en Ajustes y se aplica al reabrir la app. Solo transmite música por internet
  (radio, podcasts, Internet Archive, Jamendo), no los archivos guardados en el teléfono.

Límites conocidos: el fundido baja y sube el volumen entre canciones, pero no las superpone; igualar volumen solo actúa
en archivos que traen la etiqueta ReplayGain.

Reproduce en segundo plano con notificación, controles en la pantalla de bloqueo y botones de audífonos Bluetooth.
Tiene listas de reproducción, favoritos, historial, cola editable, aleatorio y repetir.

> **Límites (a propósito).** MiMúsica **no** lee, descifra ni copia las descargas «sin conexión» de Spotify
> (están cifradas con DRM) y **no** descarga audio de Spotify, YouTube ni de ninguna fuente sin licencia.
> Solo usa Internet Archive (colecciones con permiso de redistribución o licencia Creative Commons / dominio público)
> y Jamendo (solo pistas cuya descarga autorizó el artista).

---

## 1. Obtener el APK sin instalar nada (GitHub Actions)

La forma más fácil si no tienes Android Studio:

1. Crea una cuenta en [github.com](https://github.com) (gratis).
2. Crea un repositorio nuevo (puede ser **privado**) y sube **todo el contenido** de esta carpeta
   (incluida la carpeta oculta `.github/`). Desde la web: *Add file › Upload files* y arrastra los archivos
   descomprimidos del zip; o con git:
   ```bash
   git init && git add . && git commit -m "MiMúsica"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/mimusica.git
   git push -u origin main
   ```
3. Entra a la pestaña **Actions** del repositorio. El flujo **«Compilar APK de MiMúsica»** se ejecuta solo
   (o pulsa *Run workflow*). Tarda unos 5–10 minutos.
4. Cuando termine con ✔, abre la ejecución y descarga el artefacto **`MiMusica-debug-apk`** (un .zip con `app-debug.apk`).
5. Pasa `app-debug.apk` al teléfono (cable, Google Drive, correo…) y ábrelo. Android pedirá permitir
   *«Instalar apps desconocidas»* para la app desde la que lo abres (Archivos, Drive o Chrome): actívalo y pulsa **Instalar**.

Si la compilación falla, abre el paso rojo del flujo para ver el error.

## 2. Compilar con Android Studio

1. Instala [Android Studio](https://developer.android.com/studio) (versión reciente, compatible con AGP 9.5).
2. *File › Open* y elige esta carpeta. Espera a que termine el «Gradle sync» (descarga Gradle y librerías).
3. Si lo pide, instala desde el *SDK Manager* la plataforma **Android 17 (API 37)**.
4. Conecta el teléfono con la *depuración USB* activada (Ajustes › Acerca del teléfono › toca 7 veces
   «Número de compilación»; luego Ajustes › Sistema › Opciones de desarrollador › Depuración USB) y pulsa **Run ▶**.

Desde la terminal (con JDK 17 o superior y el SDK de Android configurado en `ANDROID_HOME`):

```bash
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 3. Activar Jamendo (client_id gratuito)

1. Entra a <https://devportal.jamendo.com> y crea una cuenta.
2. Crea una aplicación (nombre libre, por ejemplo «MiMúsica personal»; uso no comercial).
3. Copia el **Client ID** que te muestra.
4. En la app: **Biblioteca › ⚙ Ajustes › Jamendo**, pégalo y pulsa **Guardar**.

Sin clave, la pestaña Jamendo de *Descubrir* muestra estas mismas instrucciones. Internet Archive funciona sin clave.

## 4. Importar playlists de Spotify (solo nombres)

**Opción A — Exportify (CSV):**
1. En el navegador abre <https://exportify.net>, inicia sesión con Spotify y pulsa **Export** en la playlist.
2. En MiMúsica: **Biblioteca ›** ícono de importar (flechas) **› Elegir archivo CSV** y elige el archivo.
   Se leen las columnas «Track Name», «Artist Name(s)» y «Album Name».

**Opción B — Texto pegado:** una canción por línea con el formato `Artista - Canción`.

La app compara cada nombre con tu biblioteca (ignora mayúsculas, tildes, «Remastered», «feat.», etc.) y muestra
**«Ya la tienes»** (reproducible y convertible en lista) o **«No la tienes»** con el botón
**«Buscar en fuentes gratuitas»**. Dentro de la app hay una pantalla de ayuda con estos pasos.

## 5. Dónde queda cada cosa

| Pestaña | Qué hace |
|---|---|
| **Biblioteca** | Canciones / Artistas / Álbumes del teléfono. Desliza hacia abajo para volver a escanear. Íconos arriba: importar de Spotify, Descargas, Ajustes. |
| **Descubrir** | Internet Archive (Live Music Archive, Netlabels, audio de la comunidad con licencia libre) y Jamendo (populares de la semana, géneros, búsqueda). |
| **Listas** | Listas automáticas (Favoritos, Escuchado recientemente, Más escuchadas, Agregado recientemente) y tus listas. Menú ⋮: importar `.m3u`. Dentro de cada lista: renombrar, borrar, exportar `.m3u`, reordenar. |
| **Buscar** | Un solo buscador: «En mi teléfono», Internet Archive y Jamendo. |

Las descargas se guardan en **Música/MiMusica/&lt;Artista&gt;/** y aparecen en la Biblioteca.
En Ajustes puedes elegir «solo con Wi-Fi», la duración mínima de las canciones y el tema (claro/oscuro/sistema).

## 6. Estructura del proyecto

```
app/src/main/java/app/mimusica/
├── AppContainer.kt          DI manual (sin Hilt)
├── MiMusicaApp.kt, MainActivity.kt
├── domain/                  Modelo Track, emparejamiento difuso, parser CSV/texto, códec M3U (Kotlin puro)
├── data/
│   ├── db/                  Room: listas, favoritos, historial, caché de pistas, descargas, importaciones
│   ├── local/               Escaneo MediaStore + ContentObserver
│   ├── remote/              Retrofit/OkHttp + parsers JSON de Internet Archive y Jamendo
│   ├── download/            WorkManager + escritura en MediaStore
│   ├── library/             Repositorio de listas/favoritos/historial/M3U/importaciones
│   └── settings/            DataStore (ajustes)
├── playback/                PlaybackService (Media3 MediaSessionService + ExoPlayer), MediaController
└── ui/                      Compose + Material 3: tema, navegación, componentes y pantallas
app/src/test/                Pruebas JVM (emparejamiento, parsers, M3U) con fixtures JSON/CSV
```

## 7. Versiones

Verificadas como estables en noviembre de 2026: Gradle 9.9.1 · Android Gradle Plugin 9.5.1 (Kotlin integrado) ·
Kotlin 2.4.21 · KSP 2.3.14 · Compose BOM 2026.09.00 · Material 3 1.4.0 · Media3 1.11.1 · Room 2.8.5 ·
Navigation 2.10.2 · Lifecycle 2.11.0 · Activity 1.13.0 · Core 1.19.1 · DataStore 1.3.0 · WorkManager 2.12.0 ·
Retrofit 3.0.0 · OkHttp 5.3.1 · kotlinx.serialization 1.12.0 · Coroutines 1.11.0 · Coil 3.6.3.
compileSdk/targetSdk 37 (Android 17), minSdk 26 (Android 8.0). Todas están en `gradle/libs.versions.toml`.

## 8. Privacidad

No hay cuentas, anuncios ni analíticas. La música local nunca sale del teléfono. Las únicas conexiones son a
`archive.org` y `api.jamendo.com` (y sus servidores de audio/imágenes) cuando usas Descubrir o Buscar.
