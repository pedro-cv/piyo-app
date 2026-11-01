package bo.piyo.app.domain.geo

import bo.piyo.app.domain.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geodesic helpers. Distances in metres; good enough at city scale. */
object Geo {
    private const val EARTH_R = 6_371_000.0

    fun distanceM(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_R * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * A point snapped onto a polyline: [point] lies on segment [index] → [index]+1 at fraction [t]
     * (t = 0 means the vertex itself); [pos] orders snaps along the line.
     */
    data class Snap(val index: Int, val point: LatLng, val distanceM: Double, val t: Double = 0.0) {
        val pos: Double get() = index + t
    }

    /**
     * Nearest point of a polyline to [p], projected onto the segments (not just the vertices: the traces
     * have 400–600 m straight segments, so a user standing next to the line could be "400 m away" from
     * every vertex). Planar projection, exact distance to the result.
     */
    fun nearestOnPolyline(p: LatLng, line: List<LatLng>): Snap? {
        val s = nearestOnPolylineFast(p, line) ?: return null
        return s.copy(distanceM = distanceM(p, s.point))
    }

    /** Same as [nearestOnPolyline] with the equirectangular distance (error < 0.1 % at city scale). */
    fun nearestOnPolylineFast(p: LatLng, line: List<LatLng>): Snap? {
        if (line.isEmpty()) return null
        val kLat = 111_320.0
        val kLng = 111_320.0 * cos(Math.toRadians(p.lat))
        var bestI = 0
        var bestT = 0.0
        var bestD2 = Double.MAX_VALUE
        var bx = 0.0
        var by = 0.0
        var ax = (line[0].lng - p.lng) * kLng
        var ay = (line[0].lat - p.lat) * kLat
        if (line.size == 1) return Snap(0, line[0], sqrt(ax * ax + ay * ay))
        for (i in 0 until line.lastIndex) {
            bx = (line[i + 1].lng - p.lng) * kLng
            by = (line[i + 1].lat - p.lat) * kLat
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d2 = px * px + py * py
            if (d2 < bestD2) { bestD2 = d2; bestI = i; bestT = t }
            ax = bx; ay = by
        }
        if (bestT >= 1.0) { bestI += 1; bestT = 0.0 }
        val a = line[bestI]
        val point = if (bestT == 0.0) a else LatLng(a.lat + (line[bestI + 1].lat - a.lat) * bestT, a.lng + (line[bestI + 1].lng - a.lng) * bestT)
        return Snap(bestI, point, sqrt(bestD2), bestT)
    }

    /** Nearest vertex (index) of a polyline to [p]; for the editor, which edits vertices. */
    fun nearestVertex(p: LatLng, line: List<LatLng>): Int? {
        if (line.isEmpty()) return null
        val kLat = 111_320.0
        val kLng = 111_320.0 * cos(Math.toRadians(p.lat))
        var best = 0
        var bestD2 = Double.MAX_VALUE
        for (i in line.indices) {
            val dy = (line[i].lat - p.lat) * kLat
            val dx = (line[i].lng - p.lng) * kLng
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    fun pathLengthM(path: List<LatLng>): Double {
        var total = 0.0
        for (i in 1 until path.size) total += distanceM(path[i - 1], path[i])
        return total
    }

    fun walkMinutes(meters: Double): Int = (meters / 75.0).roundToInt().coerceAtLeast(1) // 4.5 km/h
    fun rideMinutes(meters: Double): Int = (meters / 300.0).roundToInt().coerceAtLeast(1) // 18 km/h

    fun formatDistance(meters: Double): String =
        if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else String.format("%.1f km", meters / 1000)

    fun formatKm(meters: Double): String = String.format("%.1f", meters / 1000).replace('.', ',')
}
