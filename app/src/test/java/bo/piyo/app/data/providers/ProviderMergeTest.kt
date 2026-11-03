package bo.piyo.app.data.providers

import bo.piyo.app.data.lines.DatasetParser
import bo.piyo.app.data.lines.DatasetWriter
import bo.piyo.app.data.lines.EffectiveLinesRepository
import bo.piyo.app.domain.model.LatLng
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineBundle
import bo.piyo.app.domain.model.LocalEdit
import bo.piyo.app.domain.model.LocalProvider
import bo.piyo.app.domain.model.ProviderMeta
import bo.piyo.app.domain.model.RouteEdit
import bo.piyo.app.domain.model.TransferEdge
import bo.piyo.app.domain.model.TripRequest
import bo.piyo.app.domain.model.Variant
import bo.piyo.app.domain.routing.RoutePlanner
import bo.piyo.app.domain.routing.TransferBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local providers: polyline round trip, merge rules, transfer links and planner use of a local route. */
class ProviderMergeTest {
    private fun line(id: String, pts: List<LatLng>, display: String = id, color: String? = null, variantId: String = "$id:ida") =
        Line(id, display, "Línea $display", "micro", color, null, listOf(Variant(variantId, "ida", "Fin $id", "Inicio $id", pts, lengthM = 1000)))

    // A north-south street and an east-west one crossing it near the 2° anillo.
    private val ns = (0..10).map { LatLng(-17.80 + it * 0.002, -63.18) }
    private val ew = (0..10).map { LatLng(-17.79, -63.19 + it * 0.002) }

    @Test
    fun polylineRoundTrip() {
        val enc = DatasetWriter.encodePolyline(ns)
        val dec = DatasetParser.decodePolyline(enc)
        assertEquals(ns.size, dec.size)
        ns.zip(dec).forEach { (a, b) -> assertEquals(a.lat, b.lat, 1e-5); assertEquals(a.lng, b.lng, 1e-5) }
    }

    @Test
    fun lineRoundTripThroughPackageShapes() {
        val l = line("41", ns, color = "#AA2233")
        val back = DatasetWriter.toLine(DatasetWriter.line(l), DatasetWriter.variants(l))
        assertEquals(l.lineId, back.lineId)
        assertEquals(l.color, back.color)
        assertEquals(1, back.variants.size)
        assertEquals("Fin 41", back.variants[0].headsign)
        assertEquals(ns.size, back.variants[0].points.size)
    }

    @Test
    fun mergeReplacesAndAppendsAndDropsStaleTransfers() {
        val main = LineBundle(15, "", lines = listOf(line("41", ns), line("98", ew)), transfers = listOf(TransferEdge("41:ida", 5, "98:ida", 5, 20), TransferEdge("98:ida", 5, "41:ida", 5, 20)))
        val moved = line("41", ns.map { LatLng(it.lat, it.lng + 0.001) })
        val added = line("u-t1", ew.reversed(), display = "T1", variantId = "u-t1:ida")
        val p = LocalProvider(ProviderMeta("p1", "Mis rutas", "", "", 15, 2), listOf(moved, added), listOf(TransferEdge("u-t1:ida", 3, "98:ida", 7, 30)), emptyMap())
        val eff = EffectiveLinesRepository.merge(main, p)
        assertEquals(3, eff.lines.size)
        assertEquals(LocalEdit.UPDATED, eff.lines.first { it.lineId == "41" }.local)
        assertEquals(LocalEdit.ADDED, eff.lines.first { it.lineId == "u-t1" }.local)
        assertEquals(null, eff.lines.first { it.lineId == "98" }.local)
        assertEquals(-63.179, eff.lines.first { it.lineId == "41" }.variants[0].points[0].lng, 1e-9)
        // The two official links touching the replaced 41 are gone; the provider's link stays.
        assertEquals(listOf(TransferEdge("u-t1:ida", 3, "98:ida", 7, 30)), eff.transfers)
        assertEquals("Mis rutas", eff.providerName)
        // No provider: untouched bundle.
        assertTrue(EffectiveLinesRepository.merge(main, null) === main)
    }

    @Test
    fun transferBuilderLinksCrossingRoutesBothWays() {
        val local = listOf(line("u-t1", ns, variantId = "u-t1:ida"))
        val links = TransferBuilder.build(local, listOf(line("98", ew)))
        val a2b = links.filter { it.fromVariant == "u-t1:ida" && it.toVariant == "98:ida" }
        val b2a = links.filter { it.fromVariant == "98:ida" && it.toVariant == "u-t1:ida" }
        assertTrue("expected links from the local route to 98", a2b.isNotEmpty())
        assertTrue("expected links from 98 to the local route", b2a.isNotEmpty())
        assertTrue(a2b.all { it.walkM <= 150 })
        // The best link is at the crossing: index 5 on both.
        assertEquals(5, a2b.minBy { it.walkM }.fromIdx)
        assertEquals(5, a2b.minBy { it.walkM }.toIdx)
        // Far-apart routes produce nothing.
        val far = listOf(line("far", ns.map { LatLng(it.lat + 0.1, it.lng) }))
        assertTrue(TransferBuilder.build(local, far).isEmpty())
    }

    @Test
    fun plannerUsesLocalRouteWithTransfer() {
        val main = LineBundle(15, "", lines = listOf(line("98", ew)))
        val local = line("u-t1", ns, display = "T1", variantId = "u-t1:ida")
        val p = LocalProvider(ProviderMeta("p1", "Mis rutas", "", "", 15, 1), listOf(local), TransferBuilder.build(listOf(local), main.lines), mapOf("u-t1" to RouteEdit("add", 15)))
        val eff = EffectiveLinesRepository.merge(main, p)
        // Start at the south end of the local route, finish at the east end of 98: ride T1 north, change to 98.
        val its = RoutePlanner(eff).plan(TripRequest(ns.first(), "A", ew.last(), "B"))
        assertTrue(its.isNotEmpty())
        val withLocal = its.first { it.lines.any { l -> l.lineId == "u-t1" } }
        assertEquals(1, withLocal.transfers)
        assertEquals(listOf("u-t1", "98"), withLocal.lines.map { it.lineId })
    }
}
