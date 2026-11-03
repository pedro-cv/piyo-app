package bo.piyo.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client for the Piyo backend (backend/README.md). Every request carries X-Install-Id.
 * The backend stores only a salted hash of that id (docs/implementation/PRIVACY.md).
 */
class PiyoApi(
    private val baseUrl: String,
    private val installId: suspend () -> String,
    private val appVersion: String,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Response(val code: Int, val body: String) {
        val ok get() = code in 200..299
    }

    data class BytesResponse(val code: Int, val bytes: ByteArray, val contentEncoding: String?) {
        val ok get() = code in 200..299
    }

    suspend fun get(path: String): Response = call(Request.Builder().url(baseUrl.trimEnd('/') + path).get())

    suspend fun post(path: String, jsonBody: String): Response = call(
        Request.Builder().url(baseUrl.trimEnd('/') + path).post(jsonBody.toRequestBody(JSON_TYPE))
    )

    suspend fun put(path: String, jsonBody: String): Response = call(
        Request.Builder().url(baseUrl.trimEnd('/') + path).put(jsonBody.toRequestBody(JSON_TYPE))
    )

    suspend fun delete(path: String): Response = call(Request.Builder().url(baseUrl.trimEnd('/') + path).delete())

    /**
     * Download an arbitrary URL as raw bytes. Accept-Encoding is pinned to identity so OkHttp does
     * not transparently inflate a pre-compressed `.json.gz` package: the manifest's sha256 is over the
     * bytes on disk, and the caller inflates after verifying.
     */
    suspend fun fetchBytes(url: String): BytesResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().header("Accept-Encoding", "identity").header("X-App-Version", appVersion).build()
        client.newCall(req).execute().use { r ->
            BytesResponse(r.code, r.body?.bytes() ?: ByteArray(0), r.header("Content-Encoding"))
        }
    }

    private suspend fun call(b: Request.Builder, withId: Boolean = true): Response = withContext(Dispatchers.IO) {
        if (withId) b.header("X-Install-Id", installId())
        b.header("X-App-Version", appVersion)
        b.header("Accept", "application/json")
        client.newCall(b.build()).execute().use { r -> Response(r.code, r.body?.string().orEmpty()) }
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
