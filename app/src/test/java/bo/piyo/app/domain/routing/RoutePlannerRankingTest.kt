package bo.piyo.app.domain.routing

import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.model.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fewer rides rank first and a direct ride is kept even when many combinations exist. */
class RoutePlannerRankingTest {
    // ~111 m per 0.001 deg of latitude; longitude ~ 106 m at -17.8.
    private fun pts(vararg p: Pair<Double, Double>) = p.map { LatLng(it.first, it.second) }
    private fun line(id: String, points: List<LatLng>) = Line(lineId = id, display = id, name = "Línea $id", variants = listOf(Variant("$id-ida", "ida", "fin", "inicio", points)))

    // Straight corridor from A (0,0) to B (0, 0.05) ~ 5.3 km.
    private val a = LatLng(-17.800, -63.200)
    private val b = LatLng(-17.800, -63.150)

    @Test fun directRideBeatsFasterTransferAndIsNotDropped() {
        // Slow direct line: long detour north then back, ~3x the distance.
        val detour = line("76", pts(-17.800 to -63.200, -17.750 to -63.200, -17.750 to -63.150, -17.800 to -63.150))
        // Several fast pairs that need one transfer in the middle (brute-force links, no dataset links).
        val fast = (1..6).flatMap { i ->
            val mid = -63.175 + i * 0.0001
            listOf(
                line("A$i", pts(-17.800 to -63.200, -17.800 to mid)),
                line("B$i", pts(-17.8001 to mid, -17.800 to -63.150)),
            )
        }
        val res = RoutePlanner(fast + detour, "3.50").plan(TripRequest(a, "A", b, "B"))
        assertTrue("got ${res.size}", res.size > 3)
        assertEquals(listOf("76"), res.first().lines.map { it.lineId })
        assertTrue(res.zipWithNext().all { (x, y) -> x.transfers <= y.transfers })
    }
}
