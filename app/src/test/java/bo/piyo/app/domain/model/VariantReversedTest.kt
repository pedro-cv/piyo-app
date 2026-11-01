package bo.piyo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VariantReversedTest {
    private val v = Variant("x-ida", "ida", headsign = "Curtiembre", origin = "Nueva Jerusalen",
        points = listOf(LatLng(-17.82, -63.26), LatLng(-17.77, -63.21), LatLng(-17.72, -63.17)), streets = listOf("A", "B"), stopIds = listOf("s1", "s2"))

    @Test fun reversesEveryPointAndSwapsEnds() {
        val r = v.reversed()
        assertEquals(v.points.reversed(), r.points)
        assertEquals(listOf("B", "A"), r.streets)
        assertEquals(listOf("s2", "s1"), r.stopIds)
        assertEquals("Nueva Jerusalen", r.headsign)
        assertEquals("Curtiembre", r.origin)
        assertEquals("ida", r.direction)
        assertEquals("x-ida", r.variantId)
        assertEquals(v, r.reversed())
    }

    @Test fun keepsHeadsignWhenNoOrigin() {
        val r = v.copy(origin = "").reversed()
        assertEquals("Curtiembre", r.headsign)
        assertEquals("", r.origin)
    }
}
