package bo.piyo.app.domain.routing

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.TransferEdge
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Computes transfer links for the routes of a local provider against every other variant, the same
 * contract the backend importer produces (DATA_MODEL.md §8): for a pair (A, B) a link means "ride A to
 * `fromIdx`, walk `walkM` metres, board B at `toIdx`". Both directions are produced for each pair.
 * Pairs whose bounding boxes are further apart than [maxWalkM] are skipped, so a provider with a couple
 * of routes costs well under a second on a phone.
 */
object TransferBuilder {
    private const val K_LAT = 111_320.0

    private class V(val id: String, val pts: List<LatLng>) {
        val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
        val minLng = pts.minOf { it.lng }; val maxLng = pts.maxOf { it.lng }
    }

    fun build(local: List<Line>, others: List<Line>, maxWalkM: Double = 150.0, maxPerPair: Int = 3): List<TransferEdge> {
        val localIds = local.flatMap { l -> l.variants.map { it.variantId } }.toSet()
        val lv = local.flatMap { l -> l.variants.filter { it.points.size >= 2 }.map { V(it.variantId, it.points) } }
        val ov = others.flatMap { l -> l.variants.filter { it.points.size >= 2 && it.variantId !in localIds }.map { V(it.variantId, it.points) } }
        val out = ArrayList<TransferEdge>()
        val pad = maxWalkM / K_LAT * 1.2
        fun near(a: V, b: V) = a.minLat - pad <= b.maxLat && b.minLat - pad <= a.maxLat && a.minLng - pad * 1.1 <= b.maxLng && b.minLng - pad * 1.1 <= a.maxLng
        for (a in lv) {
            for (b in ov + lv) {
                if (a.id == b.id || !near(a, b)) continue
                out += pairLinks(a, b, maxWalkM, maxPerPair)
                if (b.id !in localIds) out += pairLinks(b, a, maxWalkM, maxPerPair)
            }
        }
        return out
    }

    /** Links from A (alight at i) to B (board at j), up to [maxPerPair], spread along A. */
    private fun pairLinks(a: V, b: V, maxWalkM: Double, maxPerPair: Int): List<TransferEdge> {
        val kLng = K_LAT * cos(Math.toRadians(a.pts[0].lat))
        val cands = ArrayList<Triple<Int, Int, Double>>()
        for (i in a.pts.indices) {
            val p = a.pts[i]
            var bestJ = -1
            var bestD2 = Double.MAX_VALUE
            for (j in b.pts.indices) {
                val dy = (b.pts[j].lat - p.lat) * K_LAT
                val dx = (b.pts[j].lng - p.lng) * kLng
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) { bestD2 = d2; bestJ = j }
            }
            val d = sqrt(bestD2)
            if (bestJ >= 0 && d <= maxWalkM) cands += Triple(i, bestJ, d)
        }
        if (cands.isEmpty()) return emptyList()
        cands.sortBy { it.third }
        val picked = ArrayList<Triple<Int, Int, Double>>()
        for (c in cands) {
            if (picked.size >= maxPerPair) break
            if (picked.none { kotlin.math.abs(it.first - c.first) < 15 }) picked += c
        }
        return picked.map { (i, j, d) -> TransferEdge(a.id, i, b.id, j, d.toInt()) }
    }
}
