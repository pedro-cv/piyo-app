package bo.piyo.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local data providers (docs/implementation/CONTRIBUTIONS_DESIGN.md §2–3): a named overlay of routes the
 * user created or updated, applied on top of the official dataset. One provider is active at a time;
 * `null` means official data only.
 */
@Serializable
data class ProviderMeta(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String = createdAt,
    @SerialName("base_dataset_version") val baseDatasetVersion: Int = 0,
    @SerialName("route_count") val routeCount: Int = 0,
)

/** Review state of a route after it was sent (mirrors the server statuses). */
@Serializable
data class SentInfo(
    val id: String,
    val at: String,
    val status: String = "pending",   // pending | accepted | rejected
    val note: String = "",
)

/** What the provider knows about one of its routes. `before` keeps the original polylines of an update. */
@Serializable
data class RouteEdit(
    val op: String,                   // "add" | "update"
    @SerialName("based_on_version") val basedOnVersion: Int = 0,
    val before: Map<String, String> = emptyMap(),  // variant_id -> original encoded polyline
    @SerialName("edited_at") val editedAt: String = "",
    val sent: SentInfo? = null,
)

/** A loaded provider: its routes decoded, its own transfer links and the edit log. */
data class LocalProvider(
    val meta: ProviderMeta,
    val lines: List<Line>,
    val transfers: List<TransferEdge>,
    val edits: Map<String, RouteEdit>,
) {
    fun edit(lineId: String): RouteEdit? = edits[lineId]
}

data class ContributionQuota(val limit: Int, val remaining: Int, val resetsAtIso: String?, val blockedUntilIso: String?, val maxRoutes: Int = 10)

sealed class ContributionResult {
    data class Sent(val id: String, val remaining: Int) : ContributionResult()
    data class Blocked(val untilIso: String) : ContributionResult()
    data object QuotaExceeded : ContributionResult()
    data class Invalid(val message: String) : ContributionResult()
    data class Failed(val message: String) : ContributionResult()
}

data class ContributionStatus(val id: String, val status: String, val note: String, val routeIds: List<String>, val createdAtIso: String)
