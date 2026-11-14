package app.mimusica.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/** Destino donde se escribe una descarga. */
class DownloadTarget(
    val uri: Uri?,
    val file: File?,
    val mimeType: String,
    val title: String,
    val artist: String,
    val album: String,
)

/**
 * Guarda descargas en Music/MiMusica/<Artista>/ :
 *  - API 29+: MediaStore con RELATIVE_PATH (sin permiso de escritura).
 *  - API 26-28: archivo en la carpeta pública Music + MediaScanner (requiere WRITE_EXTERNAL_STORAGE).
 */
object MediaStoreWriter {

    private val invalidChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun safeName(text: String, fallback: String): String =
        invalidChars.replace(text, "_").trim().trim('.').take(80).ifBlank { fallback }

    fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/x-wav"
        else -> "audio/mpeg"
    }

    fun create(context: Context, title: String, artist: String, album: String, extension: String, podcast: Boolean = false): DownloadTarget {
        // Los episodios de podcast van a la carpeta Podcasts para no mezclarse con las canciones.
        val baseDir = if (podcast) Environment.DIRECTORY_PODCASTS else Environment.DIRECTORY_MUSIC
        val artistDir = safeName(artist, "Desconocido")
        val fileName = safeName("$artist - $title", "pista") + "." + extension
        val mime = mimeFor(extension)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "$baseDir/MiMusica/$artistDir")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                if (podcast) put(MediaStore.Audio.Media.IS_PODCAST, 1) else put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en la carpeta Música.")
            DownloadTarget(uri, null, mime, title, artist, album)
        } else {
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(baseDir)
            val dir = File(musicDir, "MiMusica/$artistDir")
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("No se pudo crear la carpeta ${dir.absolutePath}. ¿Diste permiso de almacenamiento?")
            }
            var file = File(dir, fileName)
            var n = 1
            while (file.exists()) {
                file = File(dir, safeName("$artist - $title ($n)", "pista") + "." + extension)
                n++
            }
            DownloadTarget(null, file, mime, title, artist, album)
        }
    }

    fun openOutput(context: Context, target: DownloadTarget): OutputStream =
        target.uri?.let { context.contentResolver.openOutputStream(it, "w") }
            ?: target.file?.let { FileOutputStream(it) }
            ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")

    /** Publica el archivo para que aparezca en la Biblioteca. Devuelve el URI definitivo. */
    suspend fun finish(context: Context, target: DownloadTarget): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && target.uri != null) {
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            context.contentResolver.update(target.uri, done, null, null)
            // Reafirma las etiquetas básicas (el escáner puede sobrescribirlas con las del archivo).
            try {
                val tags = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, target.title)
                    put(MediaStore.Audio.Media.ARTIST, target.artist)
                    put(MediaStore.Audio.Media.ALBUM, target.album)
                }
                context.contentResolver.update(target.uri, tags, null, null)
            } catch (_: Exception) {
                // Algunas versiones de Android no permiten editar estas columnas: no es grave.
            }
            return target.uri.toString()
        }
        val file = target.file ?: throw IllegalStateException("Archivo de descarga inexistente.")
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(target.mimeType)) { _, uri ->
                if (cont.isActive) cont.resume((uri ?: Uri.fromFile(file)).toString())
            }
        }
    }

    /** Borra un archivo a medio descargar. */
    fun discard(context: Context, target: DownloadTarget?) {
        if (target == null) return
        try {
            target.uri?.let { context.contentResolver.delete(it, null, null) }
            target.file?.delete()
        } catch (_: Exception) {
        }
    }
}
