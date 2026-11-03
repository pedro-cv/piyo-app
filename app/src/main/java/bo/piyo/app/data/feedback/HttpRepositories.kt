package bo.piyo.app.data.feedback

import bo.piyo.app.data.api.PiyoApi
import bo.piyo.app.data.settings.SettingsRepository
import bo.piyo.app.domain.model.Manifest
import bo.piyo.app.domain.model.NpsDraft
import bo.piyo.app.domain.model.NpsReason
import bo.piyo.app.domain.model.ReportDraft
import bo.piyo.app.domain.model.ReportQuota
import bo.piyo.app.domain.model.ReportResult
import bo.piyo.app.domain.model.ReportType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** Wire shapes shared by the HTTP repositories. */
@Serializable
data class ReportBody(
    val type: String,
    val lat: Double,
    val lng: Double,
    @SerialName("line_id") val lineId: String? = null,
    val comment: String = "",
    @SerialName("app_version") val appVersion: String,
    @SerialName("data_version") val dataVersion: Int,
)

@Serializable
data class NpsBody(
    val score: Int,
    val reasons: List<String>,
    val comment: String = "",
    @SerialName("app_version") val appVersion: String,
    @SerialName("data_version") val dataVersion: Int,
    @SerialName("device_class") val deviceClass: String = "",
)

/** Reports against the Go backend, with an offline outbox persisted in DataStore. */
class HttpReportsRepository(
    private val api: PiyoApi,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val dataVersion: suspend () -> Int,
) : ReportsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun quota(): ReportQuota {
        val r = runCatching { api.get("/v1/reports/quota") }.getOrNull()
        if (r == null || !r.ok) return ReportQuota(limit = 3, remaining = 3, blockedUntilIso = null)
        val o = json.parseToJsonElement(r.body).jsonObject
        return ReportQuota(
            limit = o["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3,
            remaining = o["remaining"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3,
            blockedUntilIso = o["blocked_until"]?.jsonPrimitive?.takeIf { it.isString }?.content,
        )
    }

    override suspend fun send(draft: ReportDraft): ReportResult {
        val body = ReportBody(draft.type.wire, draft.position.lat, draft.position.lng, draft.lineId, draft.comment, appVersion, dataVersion())
        return post(body, queueOnFailure = true)
    }

    private suspend fun post(body: ReportBody, queueOnFailure: Boolean): ReportResult {
        val r = try { api.post("/v1/reports", json.encodeToString(ReportBody.serializer(), body)) } catch (e: IOException) {
            if (queueOnFailure) { enqueue(body); return ReportResult.Queued }
            return ReportResult.Failed(e.message ?: "network")
        }
        val o = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
        return when (r.code) {
            201 -> ReportResult.Sent(
                id = o?.get("id")?.jsonPrimitive?.content ?: "",
                remaining = o?.get("remaining")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
            403 -> ReportResult.Blocked(o?.get("blocked_until")?.jsonPrimitive?.content ?: "")
            429 -> ReportResult.QuotaExceeded
            else -> ReportResult.Failed("HTTP ${r.code}")
        }
    }

    private suspend fun enqueue(body: ReportBody) {
        val cur = pending()
        settings.setPendingReportsJson(json.encodeToString(ListSerializer(ReportBody.serializer()), cur + body))
    }

    private suspend fun pending(): List<ReportBody> =
        settings.pendingReportsJson.first()?.let { runCatching { json.decodeFromString(ListSerializer(ReportBody.serializer()), it) }.getOrNull() } ?: emptyList()

    override suspend fun flushQueue(): Int {
        val cur = pending()
        if (cur.isEmpty()) return 0
        val remaining = mutableListOf<ReportBody>()
        var sent = 0
        for (b in cur) {
            when (post(b, queueOnFailure = false)) {
                is ReportResult.Sent -> sent++
                is ReportResult.Failed -> remaining += b   // network or 5xx: keep
                else -> Unit                               // blocked / quota / 4xx: drop
            }
        }
        settings.setPendingReportsJson(json.encodeToString(ListSerializer(ReportBody.serializer()), remaining))
        return sent
    }
}

class HttpNpsRepository(
    private val api: PiyoApi,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val dataVersion: suspend () -> Int,
    private val deviceClass: String,
) : NpsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun send(draft: NpsDraft): Boolean {
        val body = NpsBody(draft.score, draft.reasons.map(NpsReason::wire), draft.comment, appVersion, dataVersion(), deviceClass)
        return try {
            api.post("/v1/nps", json.encodeToString(NpsBody.serializer(), body)).ok
        } catch (e: IOException) {
            val cur = pending()
            settings.setPendingNpsJson(json.encodeToString(ListSerializer(NpsBody.serializer()), cur + body))
            true // queued counts as accepted for the UI: "Se envía cuando haya red"
        }
    }

    private suspend fun pending(): List<NpsBody> =
        settings.pendingNpsJson.first()?.let { runCatching { json.decodeFromString(ListSerializer(NpsBody.serializer()), it) }.getOrNull() } ?: emptyList()

    override suspend fun flushQueue(): Int {
        val cur = pending()
        if (cur.isEmpty()) return 0
        val remaining = mutableListOf<NpsBody>()
        var sent = 0
        for (b in cur) {
            val ok = try { api.post("/v1/nps", json.encodeToString(NpsBody.serializer(), b)).ok } catch (e: IOException) { remaining += b; continue }
            if (ok) sent++
        }
        settings.setPendingNpsJson(json.encodeToString(ListSerializer(NpsBody.serializer()), remaining))
        return sent
    }
}

class HttpManifestRepository(private val api: PiyoApi) : ManifestRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(currentVersion: Int): ManifestRepository.Check {
        val r = try { api.get("/v1/manifest") } catch (e: IOException) { return ManifestRepository.Check.Failed(e.message ?: "network") }
        if (!r.ok) return ManifestRepository.Check.Failed("HTTP ${r.code}")
        val m = runCatching { json.decodeFromString(Manifest.serializer(), r.body) }.getOrNull()
            ?: return ManifestRepository.Check.Failed("manifest inválido")
        return if (m.dataVersion > currentVersion) ManifestRepository.Check.Available(m) else ManifestRepository.Check.UpToDate
    }

    /**
     * Downloads the package (gzip JSON), verifies its sha256 against the manifest, inflates it and
     * returns the JSON text. Returns null on any failure so the installed data is kept.
     */
    override suspend fun download(manifest: Manifest): String? {
        val url = manifest.packageUrl
        if (url.isBlank()) return null
        val r = try { api.fetchBytes(url) } catch (e: IOException) { return null }
        if (!r.ok || r.bytes.isEmpty()) return null
        val expected = manifest.packageSha256
        if (expected.isNotBlank()) {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(r.bytes)
            val hex = digest.joinToString("") { "%02x".format(it) }
            if (!hex.equals(expected, ignoreCase = true)) return null
        }
        val isGzip = url.endsWith(".gz") || r.contentEncoding == "gzip" || (r.bytes.size > 2 && r.bytes[0] == 0x1f.toByte() && r.bytes[1] == 0x8b.toByte())
        return runCatching {
            if (isGzip) java.util.zip.GZIPInputStream(r.bytes.inputStream()).bufferedReader().use { it.readText() }
            else String(r.bytes, Charsets.UTF_8)
        }.getOrNull()
    }
}

/** Used by both flavors when a type must be shown from its wire value. */
fun reportTypeOf(wire: String): ReportType = ReportType.entries.firstOrNull { it.wire == wire } ?: ReportType.OTHER
