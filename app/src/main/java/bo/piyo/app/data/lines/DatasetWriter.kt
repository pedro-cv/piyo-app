package bo.piyo.app.data.lines

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.Variant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * Encodes lines back into the dataset package shapes (DATA_MODEL.md §3, format 2) so a local provider
 * file and a contribution payload use exactly what `DatasetParser` and the backend already read.
 */
object DatasetWriter {
    @Serializable
    data class PkgLine(
        @SerialName("line_id") val lineId: String, val display: String, val name: String, val mode: String = "micro",
        val color: String? = null, @SerialName("fare_bs") val fareBs: String? = null,
    )

    @Serializable
    data class PkgVariant(
        @SerialName("variant_id") val variantId: String, @SerialName("line_id") val lineId: String,
        val name: String = "", val branch: String = "", val direction: String = "ida",
        val headsign: String = "", val origin: String = "", val color: String? = null,
        val polyline: String, @SerialName("length_m") val lengthM: Int = 0, val streets: List<String> = emptyList(),
    )

    fun line(l: Line) = PkgLine(l.lineId, l.display, l.name, l.mode, l.color, l.fareBs)

    fun variant(lineId: String, v: Variant) = PkgVariant(
        v.variantId, lineId, v.name, v.branch, v.direction, v.headsign, v.origin, v.color,
        encodePolyline(v.points), v.lengthM, v.streets,
    )

    fun variants(l: Line): List<PkgVariant> = l.variants.map { variant(l.lineId, it) }

    /** Decodes a package line + its variants back into the domain model. */
    fun toLine(l: PkgLine, vs: List<PkgVariant>): Line = Line(
        lineId = l.lineId, display = l.display, name = l.name, mode = l.mode, color = l.color, fareBs = l.fareBs,
        variants = vs.filter { it.lineId == l.lineId }.map { v ->
            Variant(v.variantId, v.direction, v.headsign, v.origin, DatasetParser.decodePolyline(v.polyline), v.streets, emptyList(), v.lengthM, v.name, v.branch, v.color)
        },
    )

    /** Google polyline algorithm, precision 1e-5 (inverse of [DatasetParser.decodePolyline]). */
    fun encodePolyline(points: List<LatLng>): String {
        val sb = StringBuilder(points.size * 6)
        var lastLat = 0L
        var lastLng = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lng = (p.lng * 1e5).roundToLong()
            encodeValue(lat - lastLat, sb)
            encodeValue(lng - lastLng, sb)
            lastLat = lat; lastLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
