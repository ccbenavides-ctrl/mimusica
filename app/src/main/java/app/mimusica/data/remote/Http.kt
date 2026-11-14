package app.mimusica.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** GET sencillo que devuelve código y cuerpo como texto. */
internal suspend fun OkHttpClient.getText(url: HttpUrl): HttpResult = withContext(Dispatchers.IO) {
    newCall(Request.Builder().url(url).build()).execute().use { response ->
        HttpResult(response.code, response.body.string())
    }
}

/** Convierte los errores de red en [RemoteApiException] con un mensaje listo para mostrar. */
internal suspend fun <T> remoteCall(block: suspend () -> T): T = try {
    block()
} catch (e: RemoteApiException) {
    throw e
} catch (e: CancellationException) {
    throw e
} catch (e: UnknownHostException) {
    throw RemoteApiException("Sin conexión a internet. Revisa tu Wi-Fi o datos móviles.")
} catch (e: SocketTimeoutException) {
    throw RemoteApiException("El servidor tardó demasiado en responder. Inténtalo de nuevo.")
} catch (e: IOException) {
    throw RemoteApiException("Error de red: ${e.message ?: "sin conexión"}.")
} catch (e: Exception) {
    throw RemoteApiException("No se pudo leer la respuesta del servidor (${e.javaClass.simpleName}).")
}
