package app.mimusica.domain.importer

/** Una fila importada: solo nombres, nunca audio. */
data class ImportedRow(val title: String, val artist: String, val album: String = "")

data class ParsedImport(val suggestedName: String?, val rows: List<ImportedRow>)

/**
 * Lee playlists exportadas como texto:
 *  - CSV de Exportify (columnas "Track Name", "Artist Name(s)", "Album Name").
 *  - Texto pegado con una canción por línea: "Artista - Canción".
 *
 * Solo se leen nombres; la app nunca accede a archivos de audio de Spotify.
 */
object PlaylistTextParser {

    private val titleHeaders = listOf("track name", "title", "track", "song", "name", "nombre de la canción", "canción", "cancion", "título", "titulo")
    private val artistHeaders = listOf("artist name(s)", "artist name", "artist names", "artists", "artist", "artista", "artistas")
    private val albumHeaders = listOf("album name", "album", "álbum", "nombre del álbum")
    private val leadingNumber = Regex("""^\s*\d{1,4}\s*[\.\)]\s+""")
    private val separators = listOf(" - ", " – ", " — ", " -- ")

    fun parse(content: String, fileName: String? = null): ParsedImport {
        val text = content.removePrefix("﻿")
        val suggested = fileName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ParsedImport(suggested, emptyList())
        return if (looksLikeCsv(firstLine)) {
            ParsedImport(suggested, parseCsv(text))
        } else {
            ParsedImport(suggested, parseLines(text))
        }
    }

    private fun looksLikeCsv(header: String): Boolean {
        val lower = header.lowercase()
        val delimiter = detectDelimiter(header)
        val cells = splitCsvLine(lower, delimiter).map { it.trim() }
        return cells.size >= 2 && cells.any { it in titleHeaders } && cells.any { it in artistHeaders }
    }

    private fun detectDelimiter(header: String): Char {
        val candidates = listOf(',', ';', '\t')
        return candidates.maxByOrNull { c -> header.count { it == c } } ?: ','
    }

    /** CSV RFC 4180: comillas, comillas dobles escapadas y saltos de línea dentro de campos. */
    fun parseCsvRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val field = StringBuilder()
        var record = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> { record.add(field.toString()); field.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        record.add(field.toString()); field.setLength(0)
                        if (record.any { it.isNotBlank() }) records.add(record)
                        record = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            if (record.any { it.isNotBlank() }) records.add(record)
        }
        return records
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> =
        parseCsvRecords(line, delimiter).firstOrNull() ?: emptyList()

    private fun parseCsv(text: String): List<ImportedRow> {
        val headerLine = text.lineSequence().first { it.isNotBlank() }
        val delimiter = detectDelimiter(headerLine)
        val records = parseCsvRecords(text, delimiter)
        if (records.isEmpty()) return emptyList()
        val header = records.first().map { it.trim().lowercase() }
        fun indexOf(options: List<String>): Int = options.firstNotNullOfOrNull { o ->
            header.indexOf(o).takeIf { it >= 0 }
        } ?: -1
        val ti = indexOf(titleHeaders)
        val ai = indexOf(artistHeaders)
        val al = indexOf(albumHeaders)
        if (ti < 0) return emptyList()
        return records.drop(1).mapNotNull { r ->
            val title = r.getOrNull(ti)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            ImportedRow(
                title = title,
                artist = if (ai >= 0) r.getOrNull(ai)?.trim().orEmpty() else "",
                album = if (al >= 0) r.getOrNull(al)?.trim().orEmpty() else "",
            )
        }
    }

    private fun parseLines(text: String): List<ImportedRow> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { leadingNumber.replace(it, "") }
            .mapNotNull { line ->
                val sep = separators.firstOrNull { line.contains(it) }
                if (sep == null) {
                    ImportedRow(title = line, artist = "")
                } else {
                    val artist = line.substringBefore(sep).trim()
                    val title = line.substringAfter(sep).trim()
                    if (title.isBlank()) null else ImportedRow(title = title, artist = artist)
                }
            }
            .toList()
}
