package bo.piyo.app.domain.routing

import bo.piyo.app.domain.geo.Geo
import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Leg
import kotlin.math.roundToInt

/**
 * Turn-by-turn state of an active trip from the user's position, with no timetable: which leg the
 * user is on, how far to the end of that leg, and whether the destination was reached. Pure
 * Kotlin so it is unit-tested; the screen turns [Guidance] into text.
 */
object TripGuide {
    /** Within this distance of the destination the trip counts as arrived. */
    const val ARRIVE_M = 60.0
    /** On a ride, below this distance to the alighting point we say "preparate para bajar". */
    const val PREPARE_M = 250.0

    enum class Phase { WALK_TO_BOARD, RIDE, PREPARE_ALIGHT, WALK_TO_DESTINATION, ARRIVED }

    data class Guidance(
        val legIndex: Int,
        val phase: Phase,
        /** Metres left on the current leg (to the boarding point, the alighting point or the destination). */
        val remainingM: Int,
        /** Distance from the user to the itinerary; large values mean the user left the route. */
        val offRouteM: Int,
    ) {
        val arrived: Boolean get() = phase == Phase.ARRIVED
        val offRoute: Boolean get() = offRouteM > OFF_ROUTE_M
    }

    const val OFF_ROUTE_M = 200

    /**
     * [minLegIndex] is the furthest leg already reached (progress never goes backwards: a bus
     * crossing its own earlier path must not send the user back to "caminá hasta…").
     */
    fun guide(itin: Itinerary, pos: LatLng, minLegIndex: Int = 0): Guidance {
        val legs = itin.legs
        val dest = legs.last().to
        val toDest = Geo.distanceM(pos, dest)
        if (toDest <= ARRIVE_M) return Guidance(legs.lastIndex, Phase.ARRIVED, 0, 0)

        // Nearest leg at or after the progress floor.
        var best = minLegIndex.coerceIn(0, legs.lastIndex)
        var bestD = Double.MAX_VALUE
        var bestSnapIdx = 0
        for (i in best..legs.lastIndex) {
            val leg = legs[i]
            val path = if (leg is Leg.Ride) leg.path else listOf(leg.from, leg.to)
            val snap = Geo.nearestOnPolyline(pos, path) ?: continue
            // Prefer later legs on near-ties so a finished walk hands over to the ride.
            if (snap.distanceM < bestD - 15) { bestD = snap.distanceM; best = i; bestSnapIdx = snap.index }
        }
        val leg = legs[best]
        return when (leg) {
            is Leg.Walk -> {
                val remaining = Geo.distanceM(pos, leg.to).roundToInt()
                val phase = if (best == legs.lastIndex) Phase.WALK_TO_DESTINATION else Phase.WALK_TO_BOARD
                Guidance(best, phase, remaining, bestD.roundToInt())
            }
            is Leg.Ride -> {
                // From the user to the next vertex, then along the path: avoids the half-segment error of
                // measuring from the snapped vertex itself.
                val next = (bestSnapIdx + 1).coerceAtMost(leg.path.lastIndex)
                val remaining = (Geo.distanceM(pos, leg.path[next]) + Geo.pathLengthM(leg.path.subList(next, leg.path.size))).roundToInt()
                val phase = if (remaining <= PREPARE_M) Phase.PREPARE_ALIGHT else Phase.RIDE
                Guidance(best, phase, remaining, bestD.roundToInt())
            }
        }
    }
}
