package bo.piyo.app.data.lines

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import bo.piyo.app.domain.model.Stop
import bo.piyo.app.domain.model.TransferEdge
import bo.piyo.app.domain.model.Variant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the published dataset package (docs/implementation/DATA_MODEL.md §3, produced by
 * backend `piyo-import`) into the domain model, decoding polylines.
 *
 * Package format 2 (dataset v14+) adds per-variant `name`/`branch`/`color`, per-line `color_source`,
 * `attribution`, and stores transfers compactly as `[from_variant, from_idx, to_variant, to_idx, walk_m]`
 * with variant positions instead of ids. Format 1 (objects with ids) and the iteration-1 bundle
 * (`lines[].variants[].polyline` as [[lat,lng]]) are still accepted so an old downloaded file loads.
 */
object DatasetParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PkgLine(
        @SerialName("line_id") val lineId: String, val display: String, val name: String, val mode: String = "micro",
        val color: String? = null, @SerialName("color_source") val colorSource: String? = null,
        @SerialName("fare_bs") val fareBs: String? = null,
        @SerialName("variant_ids") val variantIds: List<String> = emptyList(),
    )

    @Serializable
    private data class PkgStop(@SerialName("stop_id") val stopId: String, val name: String = "", val lat: Double, val lng: Double)

    @Serializable
    private data class PkgVariant(
        @SerialName("variant_id") val variantId: String, @SerialName("line_id") val lineId: String,
        val name: String = "", val branch: String = "",
        val direction: String = "ida", val headsign: String = "", val origin: String = "",
        val color: String? = null,
        val polyline: String, @SerialName("length_m") val lengthM: Int = 0,
        @SerialName("stop_ids") val stopIds: List<String> = emptyList(), val streets: List<String> = emptyList(),
    )

    @Serializable
    private data class PkgTransfer(val from: String, @SerialName("from_idx") val fromIdx: Int, val to: String, @SerialName("to_idx") val toIdx: Int, @SerialName("walk_m") val walkM: Int)

    @Serializable
    private data class Pkg(
        @SerialName("dataset_version") val datasetVersion: Int, @SerialName("package_format") val packageFormat: Int = 1,
        @SerialName("generated_at") val generatedAt: String = "",
        @SerialName("fare_bs") val fareBs: String = "3.50", val license: String = "", val attribution: String = "", val source: String = "",
        val lines: List<PkgLine>, val stops: List<PkgStop>? = null, val variants: List<PkgVariant>,
        /** Format 2: arrays of five ints; format 1: objects. Decoded by [parseTransfers]. Nullable: tolerate `null` for an empty list. */
        val transfers: List<JsonElement>? = null,
    )

    // Legacy (iteration 1) shape
    @Serializable
    private data class OldVariant(@SerialName("variant_id") val variantId: String, val direction: String, val headsign: String, val polyline: List<List<Double>>, val streets: List<String> = emptyList())

    @Serializable
    private data class OldLine(@SerialName("line_id") val lineId: String, val display: String, val name: String, val mode: String = "micro", val color: String? = null, @SerialName("fare_bs") val fareBs: String? = null, val variants: List<OldVariant> = emptyList())

    @Serializable
    private data class OldBundle(@SerialName("data_version") val dataVersion: Int, @SerialName("generated_at") val generatedAt: String = "", @SerialName("fare_bs") val fareBs: String = "3.50", val lines: List<OldLine>)

    fun parse(text: String): LineBundle {
        val root = json.parseToJsonElement(text).jsonObject
        return if ("variants" in root) parsePackage(json.decodeFromString(Pkg.serializer(), text)) else parseLegacy(json.decodeFromString(OldBundle.serializer(), text))
    }

    private fun parsePackage(p: Pkg): LineBundle {
        val variantsByLine = p.variants.groupBy { it.lineId }
        val lines = p.lines.map { l ->
            Line(
                lineId = l.lineId, display = l.display, name = l.name, mode = l.mode, color = l.color, fareBs = l.fareBs,
                colorSource = l.colorSource,
                variants = (variantsByLine[l.lineId] ?: emptyList()).map { v ->
                    Variant(
                        v.variantId, v.direction, v.headsign, v.origin, decodePolyline(v.polyline), v.streets, v.stopIds, v.lengthM,
                        name = v.name, branch = v.branch, color = v.color,
                    )
                },
            )
        }
        return LineBundle(
            dataVersion = p.datasetVersion, generatedAt = p.generatedAt, fareBs = p.fareBs, lines = lines,
            stops = p.stops.orEmpty().map { Stop(it.stopId, it.name, LatLng(it.lat, it.lng)) },
            transfers = parseTransfers(p.transfers.orEmpty(), p.variants.map { it.variantId }),
            license = p.license, attribution = p.attribution, source = p.source, packageFormat = p.packageFormat,
        )
    }

    /** Accepts compact links `[fromVar, fromIdx, toVar, toIdx, walkM]` (format 2) and `{from, from_idx, …}` objects (format 1). */
    private fun parseTransfers(raw: List<JsonElement>, variantIds: List<String>): List<TransferEdge> {
        val out = ArrayList<TransferEdge>(raw.size)
        for (e in raw) {
            when (e) {
                is JsonArray -> {
                    if (e.size < 5) continue
                    val a = e[0].jsonPrimitive.int
                    val b = e[2].jsonPrimitive.int
                    if (a !in variantIds.indices || b !in variantIds.indices) continue
                    out += TransferEdge(variantIds[a], e[1].jsonPrimitive.int, variantIds[b], e[3].jsonPrimitive.int, e[4].jsonPrimitive.int)
                }
                is JsonObject -> {
                    val t = json.decodeFromJsonElement(PkgTransfer.serializer(), e)
                    out += TransferEdge(t.from, t.fromIdx, t.to, t.toIdx, t.walkM)
                }
                else -> continue
            }
        }
        return out
    }

    private fun parseLegacy(b: OldBundle): LineBundle = LineBundle(
        dataVersion = b.dataVersion, generatedAt = b.generatedAt, fareBs = b.fareBs,
        lines = b.lines.map { l ->
            Line(l.lineId, l.display, l.name, l.mode, l.color, l.fareBs, l.variants.map { v ->
                Variant(v.variantId, v.direction, v.headsign, "", v.polyline.map { LatLng(it[0], it[1]) }, v.streets)
            })
        },
    )

    /** Google polyline algorithm, precision 1e-5. */
    fun decodePolyline(s: String): List<LatLng> {
        val out = ArrayList<LatLng>(s.length / 4)
        var i = 0
        var lat = 0L
        var lng = 0L
        while (i < s.length) {
            var result = 0L
            var shift = 0
            var b: Long
            do {
                b = (s[i++].code - 63).toLong()
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && i < s.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            if (i >= s.length) break
            result = 0L; shift = 0
            do {
                b = (s[i++].code - 63).toLong()
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && i < s.length)
            lng += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            out.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return out
    }
}
