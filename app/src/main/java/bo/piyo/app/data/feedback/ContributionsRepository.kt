package bo.piyo.app.data.feedback

import bo.piyo.app.data.api.PiyoApi
import bo.piyo.app.data.lines.DatasetWriter
import bo.piyo.app.domain.model.ContributionQuota
import bo.piyo.app.domain.model.ContributionResult
import bo.piyo.app.domain.model.ContributionStatus
import bo.piyo.app.domain.model.LocalProvider
import bo.piyo.app.domain.model.WithdrawResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Route contributions (backend/README.md "contributions"): monthly quota, send selected routes, read review
 * statuses, withdraw a route that is still waiting for review. The server stores one row per route.
 */
interface ContributionsRepository {
    suspend fun quota(): ContributionQuota
    /** Sends only [lineIds] of [provider] (lines, variants, edits with the original geometry). */
    suspend fun send(provider: LocalProvider, lineIds: Collection<String>, comment: String): ContributionResult
    suspend fun mine(): List<ContributionStatus>
    /** Withdraws the sent route [id] so it is not reviewed (only while it is pending). */
    suspend fun withdraw(id: String): WithdrawResult
}

@Serializable
data class ContributionEditBody(val op: String, @SerialName("based_on_version") val basedOnVersion: Int, val before: Map<String, String> = emptyMap())

@Serializable
data class ContributionBody(
    @SerialName("provider_name") val providerName: String,
    @SerialName("base_dataset_version") val baseDatasetVersion: Int,
    @SerialName("app_version") val appVersion: String,
    val comment: String = "",
    val lines: List<DatasetWriter.PkgLine>,
    val variants: List<DatasetWriter.PkgVariant>,
    val edits: Map<String, ContributionEditBody>,
)

class HttpContributionsRepository(private val api: PiyoApi, private val appVersion: String) : ContributionsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun quota(): ContributionQuota {
        val r = runCatching { api.get("/v1/contributions/quota") }.getOrNull()
        if (r == null || !r.ok) return ContributionQuota(3, 3, null, null)
        val o = json.parseToJsonElement(r.body).jsonObject
        return ContributionQuota(
            limit = o["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3,
            remaining = o["remaining"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3,
            resetsAtIso = o["resets_at"]?.jsonPrimitive?.takeIf { it.isString }?.content,
            blockedUntilIso = o["blocked_until"]?.jsonPrimitive?.takeIf { it.isString }?.content,
            maxRoutes = o["max_routes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10,
        )
    }

    override suspend fun send(provider: LocalProvider, lineIds: Collection<String>, comment: String): ContributionResult {
        val lines = provider.lines.filter { it.lineId in lineIds }
        if (lines.isEmpty()) return ContributionResult.Invalid("no routes selected")
        val body = ContributionBody(
            providerName = provider.meta.name, baseDatasetVersion = provider.meta.baseDatasetVersion, appVersion = appVersion, comment = comment,
            lines = lines.map { DatasetWriter.line(it) },
            variants = lines.flatMap { DatasetWriter.variants(it) },
            edits = lines.associate { l -> val e = provider.edit(l.lineId); l.lineId to ContributionEditBody(e?.op ?: "add", e?.basedOnVersion ?: provider.meta.baseDatasetVersion, e?.before ?: emptyMap()) },
        )
        val r = try { api.post("/v1/contributions", json.encodeToString(ContributionBody.serializer(), body)) } catch (e: IOException) {
            return ContributionResult.Failed(e.message ?: "network")
        }
        val o = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
        val err = o?.get("error")?.jsonObject
        return when (r.code) {
            201 -> {
                val first = o?.get("id")?.jsonPrimitive?.content ?: ""
                // One id per route; a server that predates the split answers with a single id for all of them.
                val ids = o?.get("contributions")?.jsonArray?.mapNotNull { e ->
                    val c = e.jsonObject
                    val route = c["route_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val id = c["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    route to id
                }?.toMap() ?: emptyMap()
                ContributionResult.Sent(first, o?.get("remaining")?.jsonPrimitive?.content?.toIntOrNull() ?: 0, lines.associate { it.lineId to (ids[it.lineId] ?: first) })
            }
            400, 413 -> ContributionResult.Invalid(err?.get("message")?.jsonPrimitive?.content ?: "HTTP ${r.code}")
            403 -> ContributionResult.Blocked(err?.get("blocked_until")?.jsonPrimitive?.content ?: "")
            429 -> ContributionResult.QuotaExceeded
            else -> ContributionResult.Failed("HTTP ${r.code}")
        }
    }

    override suspend fun mine(): List<ContributionStatus> {
        val r = runCatching { api.get("/v1/contributions/mine") }.getOrNull() ?: return emptyList()
        if (!r.ok) return emptyList()
        val arr = runCatching { json.parseToJsonElement(r.body).jsonObject["contributions"]?.jsonArray }.getOrNull() ?: return emptyList()
        return arr.map { e ->
            val o = e.jsonObject
            ContributionStatus(
                id = o["id"]?.jsonPrimitive?.content ?: "", status = o["status"]?.jsonPrimitive?.content ?: "pending",
                note = o["review_note"]?.jsonPrimitive?.content ?: "", routeIds = o["route_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                createdAtIso = o["created_at"]?.jsonPrimitive?.content ?: "",
                publishedVersion = o["published_version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        }
    }

    override suspend fun withdraw(id: String): WithdrawResult {
        val r = try { api.delete("/v1/contributions/" + java.net.URLEncoder.encode(id, "UTF-8")) } catch (e: IOException) { return WithdrawResult.FAILED }
        return when (r.code) {
            204, 404 -> WithdrawResult.WITHDRAWN // 404: already gone (withdrawn before, or never stored)
            409 -> WithdrawResult.REVIEWED
            else -> WithdrawResult.FAILED
        }
    }
}
