package bo.piyo.app.map.maplibre

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Place
import bo.piyo.app.map.SantaCruz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Photon (komoot) geocoder over OpenStreetMap data: free, no key, supports autocomplete.
 * Fair-use public instance: the app debounces input (300 ms), biases to Santa Cruz and sends a unique
 * User-Agent, as its policy asks. No `lang` parameter: the public instance only supports default/de/en/fr
 * (verified 2026-10-28: `lang=es` returns HTTP 400) and "default" returns the local OSM names, which are
 * Spanish anyway. Self-hosting instructions are in MAP_PROVIDERS.md.
 */
class PhotonGeocoder(private val baseUrl: String, appVersion: String) {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "Piyo/$appVersion (Android; transit app for Santa Cruz de la Sierra)"

    suspend fun search(query: String, near: LatLng?): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", q)
            ?.addQueryParameter("limit", "8")
            ?.addQueryParameter("lat", (near ?: SantaCruz.center).lat.toString())
            ?.addQueryParameter("lon", (near ?: SantaCruz.center).lng.toString())
            ?.addQueryParameter("bbox", "${SantaCruz.boundsMin.lng},${SantaCruz.boundsMin.lat},${SantaCruz.boundsMax.lng},${SantaCruz.boundsMax.lat}")
            ?.build() ?: return@withContext emptyList()
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                parse(r.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun reverse(p: LatLng): String? = withContext(Dispatchers.IO) {
        val base = baseUrl.removeSuffix("/").removeSuffix("/api") + "/reverse"
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("lat", p.lat.toString())?.addQueryParameter("lon", p.lng.toString())
            ?.build() ?: return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { r ->
                parse(r.body?.string().orEmpty()).firstOrNull()?.let { "${it.name} · ${it.detail}".trim(' ', '·') }
            }
        }.getOrNull()
    }

    private fun parse(body: String): List<Place> {
        val root = json.parseToJsonElement(body).jsonObject
        return root["features"]?.jsonArray.orEmpty().mapNotNull { f ->
            val o = f.jsonObject
            val props = o["properties"]?.jsonObject ?: return@mapNotNull null
            val coords = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val lng = coords[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val lat = coords[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            fun s(k: String) = props[k]?.jsonPrimitive?.takeIf { it.isString }?.content
            val name = s("name") ?: listOfNotNull(s("street"), s("housenumber")).joinToString(" ").ifBlank { return@mapNotNull null }
            val detail = listOfNotNull(s("street")?.takeIf { it != name }, s("district"), s("city")).distinct().joinToString(", ")
            Place(id = s("osm_id") ?: "$lat,$lng", name = name, detail = detail, position = LatLng(lat, lng))
        } // results outside the metro box (e.g. Montero) are kept: the bbox only biases ranking
    }
}
