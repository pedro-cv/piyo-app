package bo.piyo.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A WGS84 coordinate. */
@Serializable
data class LatLng(val lat: Double, val lng: Double)

enum class LineMode { MICRO, TRUFI, OTHER }

/** One direction of a line, already decoded (see data/lines/DatasetParser.kt for the wire formats). */
data class Variant(
    val variantId: String,
    val direction: String,            // "ida" | "vuelta" | "variante"
    val headsign: String,             // where it goes, e.g. "Villa Busch"
    val origin: String = "",
    val points: List<LatLng>,
    val streets: List<String> = emptyList(),
    val stopIds: List<String> = emptyList(),
    val lengthM: Int = 0,
    val name: String = "",            // route name in the source ("linea41 Rojo"); "" for OSM data
    val branch: String = "",          // what tells two routes of one line apart ("Rojo", "UV Doce"); "" = main route
    val color: String? = null,        // hex override for this variant; null = use the line colour
) {
    /**
     * The same trace travelled the other way: every point, street and stop in reverse order, so the old
     * end is the new start. Origin and destination follow; the direction label and the id do not change.
     */
    fun reversed(): Variant = copy(
        points = points.reversed(), streets = streets.reversed(), stopIds = stopIds.reversed(),
        headsign = origin.ifBlank { headsign }, origin = if (origin.isBlank()) "" else headsign,
    )
}

/** How the active local provider touched a line (null = official data). */
enum class LocalEdit { ADDED, UPDATED }

/** A micro or trufi line. `display` is the number or short name shown in the chip. */
data class Line(
    val lineId: String,
    val display: String,
    val name: String,
    val mode: String = "micro",
    val color: String? = null,        // hex; when null a stable hue is derived from the number
    val fareBs: String? = null,
    val variants: List<Variant> = emptyList(),
    val colorSource: String? = null,  // "color-file" | "name-color-word" | "derived" | null (older packages)
    val local: LocalEdit? = null,     // set when the active local data provider added or replaced this line
) {
    val lineMode: LineMode
        get() = when (mode) {
            "trufi", "share_taxi" -> LineMode.TRUFI
            "micro", "bus" -> LineMode.MICRO
            else -> LineMode.OTHER
        }

    /** Distinct branches in variant order; a single "" entry for lines without named routes. */
    val branches: List<String> get() = variants.map { it.branch }.distinct()

    /** The variant to show for the line as a whole: ida of the first branch, whatever the stored order. */
    val primaryVariant: Variant? get() = variants.minByOrNull { when (it.direction) { "ida" -> 0; "vuelta" -> 1; else -> 2 } }
    /** Variants of a branch, ida first (the stored order is not trusted: a swap only relabels). */
    fun variantsOf(branch: String): List<Variant> = variants.filter { it.branch == branch }.sortedBy { when (it.direction) { "ida" -> 0; "vuelta" -> 1; else -> 2 } }
}

data class Stop(val stopId: String, val name: String, val position: LatLng)

/** Precomputed walking link between two variants (docs/implementation/DATA_MODEL.md). */
data class TransferEdge(val fromVariant: String, val fromIdx: Int, val toVariant: String, val toIdx: Int, val walkM: Int)

/** The decoded dataset: what the planner and the screens use. */
data class LineBundle(
    val dataVersion: Int,
    val generatedAt: String,
    val fareBs: String = "3.50",
    val lines: List<Line>,
    val stops: List<Stop> = emptyList(),
    val transfers: List<TransferEdge> = emptyList(),
    val license: String = "",
    val attribution: String = "",     // short credit shown in the UI, taken from the dataset (e.g. "© OpenStreetMap contributors")
    val source: String = "",
    val packageFormat: Int = 1,
    val providerName: String? = null, // name of the local data provider applied on top of the official data; null = official only
)

/** Manifest from GET /v1/manifest (iteration-2 shape, with the legacy mirrors). */
@Serializable
data class ManifestFile(val name: String = "", val bytes: Long = 0, val sha256: String = "", val url: String = "")

@Serializable
data class Manifest(
    @SerialName("dataset_version") val datasetVersion: Int = 0,
    @SerialName("data_version") val dataVersionLegacy: Int = 0,
    @SerialName("package") val pkg: ManifestFile? = null,
    val files: List<ManifestFile> = emptyList(),
    val url: String = "",
    val bytes: Long = 0,
    val sha256: String = "",
    @SerialName("min_app_version") val minAppVersion: String = "1",
    @SerialName("generated_at") val generatedAt: String = "",
    @SerialName("fare_bs") val fareBs: String = "3.50",
    @SerialName("package_format") val packageFormat: Int = 1,
    val attribution: String = "",
    val message: String = "",
) {
    val dataVersion: Int get() = if (datasetVersion > 0) datasetVersion else dataVersionLegacy
    val packageUrl: String get() = pkg?.url?.takeIf { it.isNotBlank() } ?: url
    val packageBytes: Long get() = pkg?.bytes?.takeIf { it > 0 } ?: bytes
    val packageSha256: String get() = pkg?.sha256?.takeIf { it.isNotBlank() } ?: sha256
}

/** A searchable place (POI, street, barrio). */
data class Place(
    val id: String,
    val name: String,
    val detail: String,
    val position: LatLng,
)

enum class ReportType(val wire: String) {
    STOP("stop"), ROUTE("route"), GONE("gone"), MISSING("missing"), OTHER("other")
}

data class ReportDraft(
    val type: ReportType,
    val lineId: String?,
    val position: LatLng,
    val comment: String,
)

sealed class ReportResult {
    data class Sent(val id: String, val remaining: Int) : ReportResult()
    data object Queued : ReportResult()
    data class Blocked(val untilIso: String) : ReportResult()
    data object QuotaExceeded : ReportResult()
    data class Failed(val message: String) : ReportResult()
}

data class ReportQuota(val limit: Int, val remaining: Int, val blockedUntilIso: String?)

enum class NpsReason(val wire: String) {
    MISSING_LINES("faltan_lineas"), WRONG_ROUTES("recorridos_mal"), MAP("el_mapa"),
    SLOW("es_lento"), USEFUL("me_sirvio"), OTHER("otra_cosa")
}

data class NpsDraft(val score: Int, val reasons: Set<NpsReason>, val comment: String)

/** What "Datos y conexión" shows. */
data class DataStatus(
    val dataVersion: Int,
    val lineCount: Int,
    val bytes: Long,
    val generatedAt: String,
    val license: String = "",
    val attribution: String = "",
    val providerName: String? = null,
)

/**
 * A saved place with its own icon and colour. `home` and `work` are the two fixed ids (always present,
 * editable, not removable); any other place is user-created. `icon` is a key from FavoriteIcons.
 */
data class FavoritePlace(
    val id: String,
    val label: String,
    val icon: String = "place",
    val color: String = "#2A6A47",
    val place: Place? = null,
) {
    val fixed: Boolean get() = id == HOME || id == WORK
    companion object { const val HOME = "home"; const val WORK = "work" }
}

data class Favorites(
    val places: List<FavoritePlace> = emptyList(),
    val lineIds: Set<String> = emptySet(),
) {
    val home: Place? get() = places.firstOrNull { it.id == FavoritePlace.HOME }?.place
    val work: Place? get() = places.firstOrNull { it.id == FavoritePlace.WORK }?.place
    fun place(id: String): FavoritePlace? = places.firstOrNull { it.id == id }
}
