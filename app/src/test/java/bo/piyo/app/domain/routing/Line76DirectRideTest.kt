package bo.piyo.app.domain.routing

import bo.piyo.app.data.lines.DatasetParser
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.TripRequest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression for the user's report (2026-10-31): a trip between two points of line 76 must list a direct
 * 76 ride, also after the line was reversed in a local provider (Variant.reversed), and whatever other
 * faster transfer combinations exist around it.
 */
class Line76DirectRideTest {
    private val seed by lazy { DatasetParser.parse(File("src/main/assets/dataset-seed.json").readText()) }

    private fun offset(p: LatLng) = LatLng(p.lat + 0.0008, p.lng + 0.0008) // ~120 m away from the trace

    private fun assertDirect76(lines: List<bo.piyo.app.domain.model.Line>, from: LatLng, to: LatLng) {
        val res = RoutePlanner(lines, seed.fareBs, seed.transfers).plan(TripRequest(from, "A", to, "B"))
        val direct76 = res.filter { it.transfers == 0 && it.lines.map { l -> l.lineId } == listOf("76") }
        assertTrue("no direct 76 in " + res.map { it.lines.joinToString("+") { l -> l.lineId } }, direct76.isNotEmpty())
        // Fewer rides first: every direct ride ranks above every transfer.
        assertTrue(res.zipWithNext().all { (x, y) -> x.transfers <= y.transfers })
    }

    @Test fun officialIdaHasDirectRide() {
        val ida = seed.lines.first { it.lineId == "76" }.variants.first { it.direction == "ida" }
        assertDirect76(seed.lines, offset(ida.points[20]), offset(ida.points[80]))
    }

    @Test fun reversedIdaHasDirectRideTheOtherWay() {
        val l76 = seed.lines.first { it.lineId == "76" }
        val reversed = l76.copy(variants = l76.variants.map { it.reversed() })
        val lines = seed.lines.map { if (it.lineId == "76") reversed else it }
        val ida = reversed.variants.first { it.direction == "ida" }
        // Points 20 -> 80 of the reversed trace = 82 -> 22 of the official one: the trip that had no 76 before.
        assertDirect76(lines, offset(ida.points[20]), offset(ida.points[80]))
    }

    /** The user's real test (2026-10-31): from their location to a stop on the reversed 76 "ida". */
    @Test fun fromUserLocationToStopsOnReversedIdaBothWays() {
        val me = LatLng(-17.783104, -63.185814) // phone location, ~150 m from the 76 corridor (off-vertex)
        val l76 = seed.lines.first { it.lineId == "76" }
        val reversed = l76.copy(variants = l76.variants.map { it.reversed() })
        val lines = seed.lines.map { if (it.lineId == "76") reversed else it }
        val ida = reversed.variants.first { it.direction == "ida" }
        // Downstream on the reversed ida (towards Nueva Jerusalén) and upstream (towards Curtiembre, served
        // by the vuelta trace that runs on the parallel one-way street): both must offer a direct 76.
        assertDirect76(lines, me, ida.points[80])
        assertDirect76(lines, me, ida.points[45])
    }
}
