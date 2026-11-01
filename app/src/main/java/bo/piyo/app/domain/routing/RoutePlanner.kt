package bo.piyo.app.domain.routing

import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Leg
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import bo.piyo.app.domain.model.TransferEdge
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.model.Variant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * On-device itinerary search over the dataset (docs/03 §4, docs/implementation/DATA_MODEL.md):
 *  1. snap origin and destination to every variant polyline within [maxWalkM] (nearest point on the line);
 *  2. direct itineraries: one variant serves both, destination index after origin index;
 *  3. one-transfer itineraries: variant A near origin, variant B near destination, using the precomputed
 *     transfer links (from_idx after the boarding index, to_idx before the alighting index);
 *     when the bundle ships no links (legacy data) a brute-force ≤150 m search is used instead;
 *  4. rank by number of rides first (what people ask for), then total minutes; keep up to [maxResults]
 *     distinct line combinations so a direct ride on a slower line is never pushed out by faster transfers.
 * Costs: walking 4.5 km/h, micro 18 km/h, fare = tariff × number of lines. No departure times are invented.
 */
class RoutePlanner(
    private val lines: List<Line>,
    private val fareBs: String,
    transfers: List<TransferEdge> = emptyList(),
    private val maxWalkM: Double = 400.0,
    private val transferM: Double = 150.0,
    private val transferPenaltyMin: Int = 6,
    private val maxWalkOnlyM: Double = 1500.0,
    private val maxResults: Int = 10,
) {
    constructor(bundle: LineBundle) : this(bundle.lines, bundle.fareBs, bundle.transfers)

    private data class Cand(val line: Line, val variant: Variant, val pts: List<LatLng>, val snap: Geo.Snap)

    /** Transfer links grouped by (fromVariant, toVariant). */
    private val links: Map<Pair<String, String>, List<TransferEdge>> = transfers.groupBy { it.fromVariant to it.toVariant }

    fun plan(req: TripRequest): List<Itinerary> {
        val all = lines.flatMap { l -> l.variants.map { v -> Triple(l, v, v.points) } }
        val fromC = all.mapNotNull { (l, v, pts) -> Geo.nearestOnPolyline(req.origin, pts)?.takeIf { it.distanceM <= maxWalkM }?.let { Cand(l, v, pts, it) } }
        val toC = all.mapNotNull { (l, v, pts) -> Geo.nearestOnPolyline(req.destination, pts)?.takeIf { it.distanceM <= maxWalkM }?.let { Cand(l, v, pts, it) } }
        val results = mutableListOf<Itinerary>()

        // Direct
        for (a in fromC) for (b in toC) {
            if (a.variant.variantId != b.variant.variantId) continue
            if (b.snap.pos <= a.snap.pos) continue
            results += build(req, listOf(a to b))
        }
        // One transfer
        for (a in fromC) for (b in toC) {
            if (a.line.lineId == b.line.lineId) continue
            val tx = findTransfer(a, b) ?: continue
            val (ia, ib, walk) = tx
            if (ia <= a.snap.pos || b.snap.pos <= ib) continue
            val leg1 = a to Cand(a.line, a.variant, a.pts, Geo.Snap(ia, a.pts[ia], 0.0))
            val leg2 = Cand(b.line, b.variant, b.pts, Geo.Snap(ib, b.pts[ib], walk)) to b
            results += build(req, listOf(leg1, leg2))
        }
        // Walking all the way is an option when the destination is close: often faster than waiting for a
        // micro, and it keeps a short trip from showing a 3 km detour as the only answer.
        val direct = Geo.distanceM(req.origin, req.destination)
        if (direct <= maxWalkOnlyM) results += walkOnly(req, direct)
        return results
            .sortedWith(compareBy({ it.transfers }, { it.totalMinutes }))
            .distinctBy { it.lines.joinToString("+") { l -> l.lineId } }
            .take(maxResults)
    }

    /** Best transfer (index on A after boarding, index on B before alighting, walk metres). */
    private fun findTransfer(a: Cand, b: Cand): Triple<Int, Int, Double>? {
        val edges = links[a.variant.variantId to b.variant.variantId]
        if (edges != null) {
            var best: TransferEdge? = null
            var bestCost = Double.MAX_VALUE
            for (e in edges) {
                if (e.fromIdx <= a.snap.pos || e.toIdx >= b.snap.pos) continue
                // Prefer the earliest usable transfer with a short walk: ride less on A after it only if the walk is short.
                val cost = e.walkM + (e.fromIdx - a.snap.index) * 0.0
                if (cost < bestCost) { bestCost = cost; best = e }
            }
            return best?.let { Triple(it.fromIdx, it.toIdx, it.walkM.toDouble()) }
        }
        if (links.isNotEmpty()) return null // dataset has links but none for this pair
        // Legacy fallback (small seed bundles only): brute force.
        var bestPair: Triple<Int, Int, Double>? = null
        var bestD = transferM
        val lastJ = if (b.snap.t > 0) b.snap.index else b.snap.index - 1
        for (i in (a.snap.index + 1) until a.pts.size) for (j in 0..lastJ) {
            val d = Geo.distanceM(a.pts[i], b.pts[j])
            if (d < bestD) { bestD = d; bestPair = Triple(i, j, d) }
        }
        return bestPair
    }

    private fun build(req: TripRequest, rides: List<Pair<Cand, Cand>>): Itinerary {
        val legs = mutableListOf<Leg>()
        var cursor = req.origin
        var walk = 0
        var minutes = 0
        rides.forEachIndexed { idx, (board, alight) ->
            val w = Geo.distanceM(cursor, board.snap.point).roundToInt()
            val wm = Geo.walkMinutes(w.toDouble())
            val boardLabel = streetAt(board.variant, board.snap.index)
            legs += Leg.Walk(cursor, board.snap.point, w, wm, boardLabel)
            walk += w; minutes += wm
            val path = ridePath(board.pts, board.snap, alight.snap)
            val rm = Geo.pathLengthM(path)
            val rmin = Geo.rideMinutes(rm)
            legs += Leg.Ride(
                from = board.snap.point, to = alight.snap.point, meters = rm.roundToInt(), minutes = rmin,
                line = board.line, variant = board.variant,
                boardLabel = boardLabel, alightLabel = streetAt(alight.variant, alight.snap.index),
                path = path, fareBs = board.line.fareBs ?: fareBs,
            )
            minutes += rmin
            cursor = alight.snap.point
            if (idx < rides.lastIndex) minutes += transferPenaltyMin
        }
        val wEnd = Geo.distanceM(cursor, req.destination).roundToInt()
        val wEndM = Geo.walkMinutes(wEnd.toDouble())
        legs += Leg.Walk(cursor, req.destination, wEnd, wEndM, req.destinationLabel)
        walk += wEnd; minutes += wEndM
        val fare = legs.filterIsInstance<Leg.Ride>().sumOf { it.fareBs.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val transfers = rides.size - 1
        val hint = when {
            transfers > 0 -> "Transbordo en " + (legs.filterIsInstance<Leg.Ride>().getOrNull(1)?.boardLabel ?: "")
            walk > 350 -> "Más caminata"
            else -> "Subí en " + (legs.filterIsInstance<Leg.Ride>().firstOrNull()?.boardLabel ?: "")
        }
        return Itinerary(
            id = rides.joinToString("|") { it.first.variant.variantId + ":" + fmtPos(it.first.snap.pos) + "-" + fmtPos(it.second.snap.pos) },
            legs = legs, totalMinutes = minutes, walkMeters = walk, transfers = transfers,
            fareBs = formatFare(fare), hint = hint,
        )
    }

    /** The ridden part of the trace: boarding point, the vertices strictly between, alighting point. */
    private fun ridePath(pts: List<LatLng>, board: Geo.Snap, alight: Geo.Snap): List<LatLng> {
        val out = ArrayList<LatLng>()
        out += board.point
        val first = board.index + 1
        val last = if (alight.t > 0) alight.index else alight.index - 1
        for (i in first..last) if (i in pts.indices) out += pts[i]
        out += alight.point
        return out.filterIndexed { i, p -> i == 0 || p != out[i - 1] }
    }

    private fun fmtPos(p: Double) = if (p == p.toLong().toDouble()) p.toLong().toString() else String.format(Locale.ROOT, "%.3f", p)

    private fun walkOnly(req: TripRequest, meters: Double): Itinerary {
        val m = meters.roundToInt()
        val min = Geo.walkMinutes(meters)
        return Itinerary(
            id = "walk:${req.origin.lat},${req.origin.lng}-${req.destination.lat},${req.destination.lng}",
            legs = listOf(Leg.Walk(req.origin, req.destination, m, min, req.destinationLabel)),
            totalMinutes = min, walkMeters = m, transfers = 0, fareBs = "0", hint = "Caminando",
        )
    }

    /** Street label for a point on a variant: streets are spread evenly along the polyline. */
    private fun streetAt(v: Variant, index: Int): String {
        if (v.streets.isEmpty()) return v.headsign
        val pos = (index.toDouble() / v.points.size.coerceAtLeast(1) * v.streets.size).toInt().coerceIn(0, v.streets.lastIndex)
        return v.streets[pos]
    }

    companion object {
        fun formatFare(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.ROOT, "%.2f", v).replace('.', ',')
    }
}
