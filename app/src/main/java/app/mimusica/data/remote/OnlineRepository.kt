package app.mimusica.data.remote

import app.mimusica.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface InternetArchiveService {
    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") query: String,
        @Query("fl[]") fields: String,
        @Query("sort[]") sort: String?,
        @Query("rows") rows: Int,
        @Query("page") page: Int,
        @Query("output") output: String,
    ): ResponseBody

    @GET("metadata/{identifier}")
    suspend fun metadata(@Path("identifier") identifier: String): ResponseBody
}

interface JamendoService {
    @GET("v3.0/tracks/")
    suspend fun tracks(@QueryMap params: Map<String, String>): ResponseBody
}

/** Colecciones de acceso rápido de Internet Archive (todas con permiso de redistribución). */
data class IaCollection(val id: String, val label: String, val description: String)

/** Géneros de acceso rápido de Jamendo (etiquetas de la API). */
data class JamendoGenre(val tag: String, val label: String)

class JamendoKeyMissingException : Exception(
    "Para usar Jamendo necesitas un client_id gratuito. Pégalo en Ajustes."
)

/**
 * Catálogos online gratuitos y legales: Internet Archive (sin clave) y Jamendo (client_id gratuito).
 */
class OnlineRepository(
    retrofitIa: Retrofit,
    retrofitJamendo: Retrofit,
    private val settings: SettingsRepository,
) {
    private val ia = retrofitIa.create(InternetArchiveService::class.java)
    private val jamendo = retrofitJamendo.create(JamendoService::class.java)

    suspend fun searchInternetArchive(
        text: String,
        collection: String? = null,
        page: Int = 1,
        rows: Int = 30,
    ): IaSearchPage = call {
        val query = InternetArchiveParser.buildQuery(text, collection)
        val sort = if (text.isBlank()) "downloads desc" else null
        val body = ia.search(query, "identifier,title,creator,licenseurl,collection,date", sort, rows, page, "json")
        val raw = withContext(Dispatchers.IO) { body.use { it.string() } }
        withContext(Dispatchers.Default) { InternetArchiveParser.parseSearch(raw, page, rows) }
    }

    suspend fun internetArchiveItem(identifier: String): IaItemDetails = call {
        val body = ia.metadata(identifier)
        val raw = withContext(Dispatchers.IO) { body.use { it.string() } }
        withContext(Dispatchers.Default) { InternetArchiveParser.parseMetadata(identifier, raw) }
    }

    suspend fun hasJamendoKey(): Boolean = settings.settings.first().hasJamendoKey

    /**
     * Pistas de Jamendo. [order] p. ej. "popularity_week"; [tag] un género; [search] texto libre.
     * Solo se devuelven pistas con descarga autorizada (audiodownload_allowed).
     */
    suspend fun jamendoTracks(
        search: String? = null,
        tag: String? = null,
        order: String? = null,
        offset: Int = 0,
        limit: Int = 30,
    ): JamendoPage = call {
        val clientId = settings.settings.first().jamendoClientId
        if (clientId.isBlank()) throw JamendoKeyMissingException()
        val params = buildMap {
            put("client_id", clientId)
            put("format", "json")
            put("limit", limit.toString())
            put("offset", offset.toString())
            put("imagesize", "300")
            put("audioformat", "mp32")
            put("audiodlformat", "mp32")
            put("include", "musicinfo")
            if (!search.isNullOrBlank()) put("search", search.trim())
            if (!tag.isNullOrBlank()) put("tags", tag)
            if (!order.isNullOrBlank()) put("order", order)
        }
        val body = jamendo.tracks(params)
        val raw = withContext(Dispatchers.IO) { body.use { it.string() } }
        withContext(Dispatchers.Default) { JamendoParser.parseTracks(raw, offset, limit) }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: RemoteApiException) {
        throw e
    } catch (e: JamendoKeyMissingException) {
        throw e
    } catch (e: HttpException) {
        throw RemoteApiException("El servidor respondió con un error (código ${e.code()}). Inténtalo más tarde.")
    } catch (e: UnknownHostException) {
        throw RemoteApiException("Sin conexión a internet. Revisa tu Wi-Fi o datos móviles.")
    } catch (e: SocketTimeoutException) {
        throw RemoteApiException("El servidor tardó demasiado en responder. Inténtalo de nuevo.")
    } catch (e: IOException) {
        throw RemoteApiException("Error de red: ${e.message ?: "sin conexión"}.")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw RemoteApiException("No se pudo leer la respuesta del servidor (${e.javaClass.simpleName}).")
    }

    companion object {
        val IA_COLLECTIONS = listOf(
            IaCollection("etree", "Live Music Archive", "Conciertos en vivo de bandas que permiten compartir sus grabaciones."),
            IaCollection("netlabels", "Netlabels", "Sellos independientes que publican con licencias libres."),
            IaCollection("opensource_audio", "Audio de la comunidad", "Audio subido por la comunidad (solo con licencia libre)."),
        )

        val JAMENDO_GENRES = listOf(
            JamendoGenre("rock", "Rock"),
            JamendoGenre("pop", "Pop"),
            JamendoGenre("electronic", "Electrónica"),
            JamendoGenre("hiphop", "Hip hop"),
            JamendoGenre("jazz", "Jazz"),
            JamendoGenre("latin", "Latina"),
            JamendoGenre("reggae", "Reggae"),
            JamendoGenre("classical", "Clásica"),
            JamendoGenre("ambient", "Ambiental"),
            JamendoGenre("folk", "Folk"),
            JamendoGenre("metal", "Metal"),
            JamendoGenre("lounge", "Lounge"),
        )
    }
}
