package bo.piyo.app.domain.routing

import bo.piyo.app.domain.model.Itinerary
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Leg
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripGuideTest {
    // A straight trip heading north along one meridian: walk 200 m, ride 2 km, walk 150 m.
    // 0.001° of latitude ≈ 111 m.
    private fun p(metersNorth: Double) = LatLng(-17.78 + metersNorth / 111_000.0, -63.18)
    private val boardAt = p(200.0)
    private val alightAt = p(2200.0)
    private val dest = p(2350.0)
    private val path = (0..20).map { p(200.0 + it * 100.0) }
    private val line = Line("10", "10", "Línea 10")
    private val variant = Variant("v", "ida", "B", points = path)
    private val itin = Itinerary(
        id = "t", totalMinutes = 15, walkMeters = 350, transfers = 0, fareBs = "2.50", hint = "",
        legs = listOf(
            Leg.Walk(p(0.0), boardAt, 200, 3, "Esquina"),
            Leg.Ride(boardAt, alightAt, 2000, 7, line, variant, "Esquina", "Mercado", path, "2.50"),
            Leg.Walk(alightAt, dest, 150, 2, "Destino"),
        ),
    )

    @Test
    fun walkingToTheBoardingPoint() {
        val g = TripGuide.guide(itin, p(50.0))
        assertEquals(0, g.legIndex)
        assertEquals(TripGuide.Phase.WALK_TO_BOARD, g.phase)
        assertEquals(150, g.remainingM, 2.0)
        assertFalse(g.offRoute)
    }

    @Test
    fun ridingThenPreparingToAlight() {
        val mid = TripGuide.guide(itin, p(1200.0), minLegIndex = 1)
        assertEquals(1, mid.legIndex)
        assertEquals(TripGuide.Phase.RIDE, mid.phase)
        assertEquals(1000, mid.remainingM, 5.0)
        val near = TripGuide.guide(itin, p(2050.0), minLegIndex = 1)
        assertEquals(TripGuide.Phase.PREPARE_ALIGHT, near.phase)
        assertEquals(150, near.remainingM, 5.0)
    }

    @Test
    fun progressNeverGoesBackwards() {
        // Standing at the boarding corner again while already on the ride leg: stay on the ride.
        val g = TripGuide.guide(itin, boardAt, minLegIndex = 1)
        assertEquals(1, g.legIndex)
    }

    @Test
    fun lastWalkAndArrival() {
        val g = TripGuide.guide(itin, p(2260.0), minLegIndex = 2)
        assertEquals(TripGuide.Phase.WALK_TO_DESTINATION, g.phase)
        assertEquals(90, g.remainingM, 3.0)
        val arrived = TripGuide.guide(itin, p(2320.0))
        assertTrue(arrived.arrived)
        assertEquals(0, arrived.remainingM)
    }

    @Test
    fun offRouteIsFlagged() {
        val far = TripGuide.guide(itin, LatLng(-17.78 + 1000 / 111_000.0, -63.18 + 500 / 105_000.0))
        assertTrue(far.offRoute)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Double) =
        assertTrue("expected $expected ± $tolerance, got $actual", Math.abs(expected - actual) <= tolerance)
}
